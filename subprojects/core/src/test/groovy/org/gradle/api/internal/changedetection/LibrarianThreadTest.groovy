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

package org.gradle.api.internal.changedetection

import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentIndexedCache
import org.gradle.util.ConcurrentSpecification
import spock.lang.Ignore

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

/**
 * by Szczepan Faber, created at: 2/27/13
 */
class LibrarianThreadTest extends ConcurrentSpecification {

    LibrarianThread thread
    PersistentIndexedCache cache

    def cacheAccess = Mock(PersistentCache)
    def data = new HashMap()
    def mapCache = new MapCache(data: data)

    def setup() {
        cacheAccess.useCache(_, _) >> { args ->
            args[1].run()
        }
        thread = new LibrarianThread()
        thread.start(cacheAccess)
        cache = thread.sync(mapCache)
    }

    def cleanup() {
        thread.requestStop()
    }

    def "can be stopped"() {
        expect:
        thread.requestStop()
        poll { assert thread.stopped }
    }

    def "can be stopped from multiple threads"() {
        when:
        start { thread.requestStop() }
        start { thread.requestStop() }
        start { thread.requestStop() }

        then:
        poll { assert thread.stopped }
    }

    def "can write and read"() {
        when:
        cache.put(1, "foo")
        cache.put(2, "bar")

        then:
        cache.get(1) == "foo"
        cache.get(1) == "foo"
        cache.get(2) == "bar"
        cache.get(2) == "bar"

        cleanup:
        thread.requestStop()
    }

    def "recognizes different caches"() {
        def data2 = new HashMap()
        def cache2 = thread.sync(new MapCache(data: data2))

        when:
        start { cache.put(1, "foo") }
        start { cache2.put(1, "foox") }
        start { cache.put(2, "bar") }
        start { cache2.put(2, "barx") }

        then:
        poll { assert data  == [1: 'foo', 2: 'bar'] }
        poll { assert data2 == [1: 'foox', 2: 'barx'] }

        when:
        start { assert cache.get(1) == "foo" }
        start { assert cache.get(2) == "bar" }
        start { assert cache2.get(1) == "foox" }
        start { assert cache2.get(2) == "barx" }

        then:
        finished()

        cleanup:
        thread.requestStop()
    }

    def "can remove from cache"() {
        when:
        cache.put(1, "foo")
        cache.put(2, "bar")
        cache.remove(1)

        then:
        poll { assert data == [2: "bar"] }

        when:
        cache.remove(2)

        then:
        poll { assert data.isEmpty() }

        cleanup:
        thread.requestStop()
    }

    def "can write from separate threads"() {
        when:
        start { cache.put(1, "foo") }
        start { cache.put(2, "bar") }
        start { cache.put(3, "baz") }

        then:
        poll { assert data == [1: "foo", 2: "bar", 3: "baz"] }

        cleanup:
        thread.requestStop()
    }

    def "can remove from separate threads"() {
        data.putAll(1: "foo", 2: "bar", 3: "baz")

        when:
        start { cache.remove(1) }
        start { cache.remove(2) }
        start { cache.remove(3) }

        then:
        poll { assert data.isEmpty() }

        cleanup:
        thread.requestStop()
    }

    def "can read from separate threads"() {
        data.putAll(1: "foo", 2: "bar", 3: "baz")

        expect:
        start { assert cache.get(1) == "foo" }
        start { assert cache.get(2) == "bar" }
        start { assert cache.get(3) == "baz" }

        finished()

        cleanup:
        thread.requestStop()
    }

    def "null cache value is supported"() {
        data.putAll(1: "foo", 2: null)

        expect:
        start { assert cache.get(1) == "foo" }
        start { assert cache.get(2) == null }

        finished()

        cleanup:
        thread.requestStop()
    }

    @Ignore //not sure if we need this... e.g. support the case where we're waiting for value that will be written in future
    def "can read from separate threads values that will be written in future"() {
        def values = [:]
        start { values[1] = cache.get(1) }
        start { values[2] = cache.get(2) }
        start { values[3] = cache.get(3) }

        when:
        cache.put(1, "foo")
        cache.put(2, "bar")
        cache.put(3, "baz")

        then:
        poll { assert values == [1: "foo", 2: "bar", 3: "baz"] }

        cleanup:
        thread.requestStop()
    }

    static class MapCache implements PersistentIndexedCache {
        Map data = new HashMap()
        Object get(Object key) {
            data.get(key)
        }

        void put(Object key, Object value) {
            data.put(key, value)
        }

        void remove(Object key) {
            data.remove(key)
        }
    }
}
