/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.util.HashSet;
import java.util.Map;

import static org.gradle.internal.fingerprint.impl.EmptyDirectorySensitivity.FINGERPRINT;
import static org.gradle.internal.fingerprint.impl.EmptyDirectorySensitivity.IGNORE;

/**
 * Fingerprint files normalizing the path to the file name.
 *
 * File names for root directories are ignored.
 */
public class NameOnlyFingerprintingStrategy extends AbstractFingerprintingStrategy {

    public static final NameOnlyFingerprintingStrategy FINGERPRINT_DIRECTORIES = new NameOnlyFingerprintingStrategy(FINGERPRINT);
    public static final NameOnlyFingerprintingStrategy IGNORE_DIRECTORIES = new NameOnlyFingerprintingStrategy(IGNORE);
    public static final String IDENTIFIER = "NAME_ONLY";
    private final EmptyDirectorySensitivity emptyDirectorySensitivity;

    private NameOnlyFingerprintingStrategy(EmptyDirectorySensitivity emptyDirectorySensitivity) {
        super(IDENTIFIER);
        this.emptyDirectorySensitivity = emptyDirectorySensitivity;
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        return snapshot.getName();
    }

    private boolean shouldFingerprint(CompleteDirectorySnapshot directorySnapshot) {
        return !(directorySnapshot.getChildren().isEmpty() && emptyDirectorySensitivity == IGNORE);
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(Iterable<? extends FileSystemSnapshot> roots) {
        final ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            root.accept(new FileSystemSnapshotVisitor() {
                private boolean root = true;

                @Override
                public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    String absolutePath = directorySnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath) && shouldFingerprint(directorySnapshot)) {
                        FileSystemLocationFingerprint fingerprint = isRoot() ? IgnoredPathFileSystemLocationFingerprint.DIRECTORY : new DefaultFileSystemLocationFingerprint(directorySnapshot.getName(), directorySnapshot);
                        builder.put(absolutePath, fingerprint);
                    }
                    root = false;
                    return true;
                }

                @Override
                public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                    String absolutePath = fileSnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        builder.put(
                            absolutePath,
                            new DefaultFileSystemLocationFingerprint(fileSnapshot.getName(), fileSnapshot));
                    }
                }

                private boolean isRoot() {
                    return root;
                }

                @Override
                public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                }
            });
        }
        return builder.build();
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
