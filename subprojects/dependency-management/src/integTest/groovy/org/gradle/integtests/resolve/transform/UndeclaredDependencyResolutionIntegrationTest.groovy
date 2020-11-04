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
            task broken {
                doLast {
                    println "result = " + view.files.name
                }
            }
        """

        when:
        run("broken")

        then:
        output.count("processing [a.jar]") == 1
        output.count("processing [b.jar]") == 1
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        run("broken")

        then:
        output.count("processing") == 0
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    def "task can query FileCollection containing the output of chained transform of project artifacts without declaring this access"() {
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
            task broken {
                doLast {
                    println "result = " + view.files.name
                }
            }
        """

        when:
        run("broken")

        then:
        output.count("processing [a.jar]") == 1
        output.count("processing [a.jar.red]") == 1
        output.count("processing [b.jar]") == 1
        output.count("processing [b.jar.red]") == 1
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        run("broken")

        then:
        output.count("processing") == 0
        output.contains("result = [a.jar.red.green, b.jar.red.green]")
    }
}
