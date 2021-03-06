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

package org.gradle.buildsetup.plugins.internal

import org.gradle.buildsetup.plugins.internal.maven.MavenProjectXmlWriter
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/21/13
 */
class MavenProjectXmlWriterTest extends Specification {

    def writer = new MavenProjectXmlWriter()

    def "removes xml element"() {
        expect:
        writer.prepareXml('<?xml encoding="UTF-8"?><project/>') == "<project/>"
        writer.prepareXml('<?xml version="1.0" encoding="UTF-8"?><project/>') == "<project/>"
        writer.prepareXml('<?xml  version="1.0"  encoding="UTF-8"  ?><project/>') == "<project/>"
    }
}
