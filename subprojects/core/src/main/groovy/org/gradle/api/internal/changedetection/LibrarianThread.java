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

package org.gradle.api.internal.changedetection;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * by Szczepan Faber, created at: 2/27/13
 */
public class LibrarianThread<K, V> {

    private final static Logger LOG = Logging.getLogger(LibrarianThread.class);
    private Librarian librarian;
    private Thread thread;
    private PersistentCache cache;

    public LibrarianThread() {
        this.librarian = new Librarian();
        thread = new Thread(librarian);
    }

    public void requestStop() {
        librarian.requestStop();
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void start(PersistentCache cache) {
        this.cache = cache;
        thread.start();
    }

    public boolean isStopped() {
        return librarian.stopped;
    }

    public PersistentIndexedCache<K, V> sync(final PersistentIndexedCache delegate) {
        return new PersistentIndexedCache<K, V>() {
            public V get(K key) {
                return librarian.get(key, delegate);
            }

            public void put(K key, V value) {
                librarian.put(key, value, delegate);
            }

            public void remove(K key) {
                librarian.remove(key, delegate);
            }
        };
    }

    private class Librarian implements Runnable {

        private final Lock lock = new ReentrantLock();
        private final Condition accessRequested = lock.newCondition();
        private final Condition answerReady = lock.newCondition();
        private final LinkedList<Factory<V>> readQueue = new LinkedList<Factory<V>>();
        private final Map<Object, Answer<V>> answers = new HashMap<Object, Answer<V>>();
        private final LinkedList<Runnable> writes = new LinkedList<Runnable>();
        private boolean stopRequested;
        private boolean stopped;

        public V get(final K key, final PersistentIndexedCache delegate) {
            lock.lock();
            try {
                return waitFor(new Factory<V>() {
                    public V create() {
                        V out = (V) delegate.get(key);
                        return out;
                    }
                });
            } finally {
                lock.unlock();
            }
        }

        public void put(final K key, final V value, final PersistentIndexedCache delegate) {
            lock.lock();
            try {
                writes.add(new Runnable() {
                    public void run() {
                        delegate.put(key, value);
                    }
                });
                accessRequested.signalAll();
            } finally {
                lock.unlock();
            }
        }

        //when read for given key, check if there is a write/remove pending, if there is, use the value from write
        // instead of getting it from the delegate cache
        //when there are multiple writes for the same key (e.g. check when the write/remove is queued), ignore the earlier one

        public void remove(final K key, final PersistentIndexedCache delegate) {
            lock.lock();
            try {
                writes.add(new Runnable() {
                    public void run() {
                        delegate.remove(key);
                    }
                });
                accessRequested.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private V waitFor(Factory<V> factory) {
            readQueue.add(factory);
            accessRequested.signalAll();
            Answer<V> answer;
            while((answer = answers.get(factory)) == null) {
                try {
                    answerReady.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (stopRequested) {
                    throw new RuntimeException("Stop requested while waiting for cache value");
                }
            }
            return answer.value;
        }

        public void run() {
            try {
                cache.useCache("librarian thread", new Runnable() {
                    public void run() {
                        runNow();
                    }
                });
            } catch (Exception e) {
                LOG.error("Problems running the librarian thread", e);
            }
        }

        /*


         */

        private void runNow() {
            lock.lock();
            try {
                while(true) {
                    if (!readQueue.isEmpty()) {
                        Factory<V> factory = readQueue.removeFirst();
                        V value = factory.create();
                        answers.put(factory, new Answer<V>(value));
                        answerReady.signalAll();
                    } else if (!writes.isEmpty()) {
                        Runnable runnable = writes.removeFirst();
                        runnable.run();
                    } else {
                        if (stopRequested) {
                            break;
                        }
                        try {
                            accessRequested.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
            stopped = true;
        }

        public void requestStop() {
            lock.lock();
            stopRequested = true;
            try {
                accessRequested.signalAll();
                answerReady.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private class Answer<V> {
            V value;

            public Answer(V value) {
                this.value = value;
            }
        }
    }
}