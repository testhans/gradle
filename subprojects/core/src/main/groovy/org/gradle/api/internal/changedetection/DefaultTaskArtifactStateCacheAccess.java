/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection;

import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultTaskArtifactStateCacheAccess implements TaskArtifactStateCacheAccess {
    private final Gradle gradle;
    private final CacheRepository cacheRepository;
    private PersistentCache cache;

    public DefaultTaskArtifactStateCacheAccess(Gradle gradle, CacheRepository cacheRepository) {
        this.gradle = gradle;
        this.cacheRepository = cacheRepository;
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(final String cacheName, final Class<K> keyType, final Class<V> valueType) {
        BTreePersistentIndexedCache btree = new BTreePersistentIndexedCache(new File(cacheName), new DefaultSerializer<K>(keyType.getClassLoader()), new DefaultSerializer<V>(valueType.getClassLoader()));
        return new ThreadSafeCache(btree);
//        Factory<PersistentIndexedCache> factory = new Factory<PersistentIndexedCache>() {
//            public PersistentIndexedCache create() {
//                return getCache().createCache(cacheFile(cacheName), keyType, valueType);
//            }
//        };
//        return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(final String cacheName, final Class<K> keyType, final Class<V> valueType, final Serializer<V> valueSerializer) {
        BTreePersistentIndexedCache btree = new BTreePersistentIndexedCache(new File(cacheName), new DefaultSerializer<K>(keyType.getClassLoader()), new DefaultSerializer<V>(valueType.getClassLoader()));
        return new ThreadSafeCache(btree);
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return action.create();
    }

    public void useCache(String operationDisplayName, Runnable action) {
        action.run();
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        action.run();
    }

    private class ThreadSafeCache implements PersistentIndexedCache {
        private final BTreePersistentIndexedCache delegate;
        private final Lock lock = new ReentrantLock();

        public ThreadSafeCache(BTreePersistentIndexedCache delegate) {
            this.delegate = delegate;
        }

        public Object get(Object key) {
            lock.lock();
            try {
                return delegate.get(key);
            } finally {
                lock.unlock();
            }
        }

        public void put(Object key, Object value) {
            lock.lock();
            try {
                delegate.put(key, value);
            } finally {
                lock.unlock();
            }
        }

        public void remove(Object key) {
            lock.lock();
            try {
                delegate.remove(key);
            } finally {
                lock.unlock();
            }
        }
    }
}