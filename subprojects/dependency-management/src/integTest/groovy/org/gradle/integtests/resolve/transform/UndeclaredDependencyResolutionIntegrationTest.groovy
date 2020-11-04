/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

// This tests current behaviour, not desired behaviour
class UndeclaredDependencyResolutionIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {
    def "task can query FileCollection containing the output of transform of project artifacts without declaring this access"() {
        setupBuildWithProjectArtifactTransforms()
        buildFile << """
            task broken {
                doLast {
                    println "result = " + view.files.name
                }
            }
        """

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    def "can query FileCollection containing the output of transform of project artifacts at task graph calculation time"() {
        setupBuildWithProjectArtifactTransforms()
        buildFile << """
            task broken {
                dependsOn {
                    println "result = " + view.files.name
                    []
                }
                doLast { }
            }
        """

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    private void setupBuildWithProjectArtifactTransforms() {
        settingsFile << """
            include 'a', 'b'
        """

        setupBuildWithColorTransformImplementation(true)

        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files
        """
    }

    def "task can query FileCollection containing the output of chained transform of project artifacts without declaring this access"() {
        setupBuildWithChainedProjectArtifactTransforms()
        buildFile << """
            task broken {
                doLast {
                    println "result = " + view.files.name
                }
            }
        """

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.red.green, b.jar.red.green]")
    }

    def "can query FileCollection containing the output of chained transform of project artifacts at task graph calculation time"() {
        setupBuildWithChainedProjectArtifactTransforms()
        buildFile << """
            task broken {
                dependsOn {
                    println "result = " + view.files.name
                    []
                }
                doLast {
                }
            }
        """

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.red.green, b.jar.red.green]")
    }

    private void setupBuildWithChainedProjectArtifactTransforms() {
        settingsFile << """
            include 'a', 'b'
        """

        setupBuildWithChainedColorTransform(true)

        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files
        """
    }
}
