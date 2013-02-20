/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

public class ParallelProjectExecutionIntegrationTest extends AbstractIntegrationSpec {

    /*
    2 threads
    4 projects a,b,c,d

    a->b (a,b - w1)
    c->b (c,d - w2)
    d
     */

    def "workers run a different project if no available tasks"() {
        settingsFile << "include 'a', 'b', 'c', 'd'"

        buildFile << """
            subprojects {
                task build << {
                    println "Sleeping " + path
                    Thread.sleep(2000)
                }
            }
            tasks.getByPath(":a:build").dependsOn(":b:build")
            tasks.getByPath(":c:build").dependsOn(":b:build")
        """

        expect:
        run 'build', '--parallel-threads', '2'
    }
}
