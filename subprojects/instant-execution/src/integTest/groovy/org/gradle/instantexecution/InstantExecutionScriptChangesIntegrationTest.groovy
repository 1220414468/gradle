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

package org.gradle.instantexecution

import org.gradle.instantexecution.fixtures.ScriptChangeFixture
import org.junit.Test
import org.gradle.testfixtures.SafeUnroll

class InstantExecutionScriptChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @SafeUnroll
    @Test
    def "invalidates cache upon change to #scriptChangeSpec"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = scriptChangeSpec.fixtureForProjectDir(testDirectory)
        fixture.setup()
        def build = { instantRun(*fixture.buildArguments) }

        when:
        build()

        then:
        outputContains fixture.expectedOutputBeforeChange

        when:
        fixture.applyChange()
        build()

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateStored()

        when:
        build()

        then:
        outputDoesNotContain fixture.expectedOutputBeforeChange
        outputDoesNotContain fixture.expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        scriptChangeSpec << ScriptChangeFixture.specs()
    }
}
