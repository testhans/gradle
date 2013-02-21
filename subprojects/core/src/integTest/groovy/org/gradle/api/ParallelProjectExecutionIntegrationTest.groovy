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
        run 'build', '--parallel-threads', '2', '-i'
    }

    //sanity test
    def "java compilation"() {
        settingsFile << "include 'a', 'b', 'c', 'd'"

        file("a/src/main/java/A.java") << "public class A { static B b = new B(); }"
        file("c/src/main/java/C.java") << "public class C { static B b = new B(); }"
        file("b/src/main/java/B.java") << "public class B {}"
        file("d/src/main/java/D.java") << "public class D {}"

        buildFile << """
            subprojects {
                apply plugin: 'java'
            }
            project(":a") {
                dependencies { compile project(":b") }
            }
            project(":c") {
                dependencies { compile project(":b") }
            }
        """

        expect:
        run 'build', '--parallel-threads', '2', '-i'
    }

    def "java compilation 2"() {
        settingsFile << "include 'a', 'b', 'c', 'd'"

        file("a/src/main/java/A.java") << "public class A { static B b = new B(); }"
        file("c/src/main/java/C.java") << "public class C { static B b = new B(); }"
        file("b/src/main/java/B.java") << "public class B {}"
        file("d/src/main/java/D.java") << "public class D {}"

        buildFile << """
            subprojects {
                apply plugin: 'java'
            }
            project(":b") {
                task aslowTask << {
                    Thread.sleep(5000)
                }
                build.dependsOn aslowTask
            }
            project(":a") {
                dependencies { compile project(":b") }
            }
            project(":c") {
                dependencies { compile project(":b") }
            }
        """

        expect:
        run 'build', '--parallel-threads', '2', '-i'
    }
}
