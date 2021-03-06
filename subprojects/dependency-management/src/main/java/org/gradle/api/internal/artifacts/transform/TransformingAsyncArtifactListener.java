/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class TransformingAsyncArtifactListener implements ResolvedArtifactSet.Visitor {
    private final List<BoundTransformationStep> transformationSteps;
    private final ImmutableAttributes target;
    private final ImmutableList.Builder<ResolvedArtifactSet.Artifacts> result;

    public TransformingAsyncArtifactListener(
        List<BoundTransformationStep> transformationSteps,
        ImmutableAttributes target,
        ImmutableList.Builder<ResolvedArtifactSet.Artifacts> result) {
        this.transformationSteps = transformationSteps;
        this.target = target;
        this.result = result;
    }

    @Override
    public void visitArtifacts(ResolvedArtifactSet.Artifacts artifacts) {
        artifacts.visit(new ArtifactVisitor() {
            @Override
            public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
                TransformedArtifact transformedArtifact = new TransformedArtifact(variantName, target, artifact, transformationSteps);
                result.add(transformedArtifact);
            }

            @Override
            public boolean requireArtifactFiles() {
                return false;
            }

            @Override
            public void visitFailure(Throwable failure) {
                result.add(new BrokenArtifacts(failure));
            }
        });
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        // Visit everything
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    public static class TransformedArtifact implements ResolvedArtifactSet.Artifacts, RunnableBuildOperation {
        private final DisplayName variantName;
        private final ResolvableArtifact artifact;
        private final ImmutableAttributes target;
        private final List<BoundTransformationStep> transformationSteps;
        private Try<TransformationSubject> transformedSubject;
        private CacheableInvocation<TransformationSubject> invocation;

        public TransformedArtifact(DisplayName variantName, ImmutableAttributes target, ResolvableArtifact artifact, List<BoundTransformationStep> transformationSteps) {
            this.variantName = variantName;
            this.artifact = artifact;
            this.target = target;
            this.transformationSteps = transformationSteps;
        }

        public DisplayName getVariantName() {
            return variantName;
        }

        public ResolvableArtifact getArtifact() {
            return artifact;
        }

        public ImmutableAttributes getTarget() {
            return target;
        }

        public List<BoundTransformationStep> getTransformationSteps() {
            return transformationSteps;
        }

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
            synchronized (this) {
                prepareInvocation();
                if (transformedSubject == null && (invocation == null || !invocation.getCachedResult().isPresent())) {
                    actions.add(this);
                }
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Execute transform");
        }

        @Override
        public void run(@Nullable BuildOperationContext context) {
            synchronized (this) {
                finalizeValue();
            }
        }

        @Override
        public void finalizeNow(boolean requireFiles) {
            synchronized (this) {
                finalizeValue();
            }
        }

        private void prepareInvocation() {
            if (!artifact.getFileSource().isFinalized()) {
                return;
            }
            if (!artifact.getFileSource().getValue().isSuccessful()) {
                transformedSubject = Try.failure(artifact.getFileSource().getValue().getFailure().get());
                return;
            }

            TransformationSubject initialSubject = TransformationSubject.initial(artifact);
            this.invocation = createInvocation(initialSubject);
        }

        private void finalizeValue() {
            if (transformedSubject != null) {
                return;
            }
            if (invocation == null) {
                artifact.getFileSource().finalizeIfNotAlready();
                prepareInvocation();
            }
            if (transformedSubject != null) {
                return;
            }
            if (invocation.getCachedResult().isPresent()) {
                // Already calculated
                transformedSubject = invocation.getCachedResult().get();
                return;
            }

            transformedSubject = invocation.invoke();
        }

        private CacheableInvocation<TransformationSubject> createInvocation(TransformationSubject initialSubject) {
            BoundTransformationStep initialStep = transformationSteps.get(0);
            CacheableInvocation<TransformationSubject> invocation = initialStep.getTransformation().createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null);
            for (int i = 1; i < transformationSteps.size(); i++) {
                BoundTransformationStep nextStep = transformationSteps.get(i);
                invocation = invocation.flatMap(intermediate -> nextStep.getTransformation().createInvocation(intermediate, nextStep.getUpstreamDependencies(), null));
            }
            return invocation;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            synchronized (this) {
                finalizeValue();
                transformedSubject.ifSuccessfulOrElse(
                    transformedSubject -> {
                        for (File output : transformedSubject.getFiles()) {
                            ResolvableArtifact resolvedArtifact = artifact.transformedTo(output);
                            visitor.visitArtifact(variantName, target, resolvedArtifact);
                        }
                    },
                    failure -> visitor.visitFailure(
                        new TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.getId(), target), failure))
                );
            }
        }
    }
}
