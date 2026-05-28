/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.concurrency;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple implementation of a thread factory.
 *
 * @author Johno Crawford (johno@sulake.com)
 */
public class SimpleThreadFactory implements java.util.concurrent.ThreadFactory {
    /** The thread name prefix. */
    private final String threadNamePrefix;

    /** The thread index counter, used for assigning unique thread ids. */
    private static final AtomicInteger threadIdx = new AtomicInteger();

    /** Whether to set daemon mode. */
    private final boolean daemon;

    /**
     * Constructor.
     *
     * @param threadNamePrefix
     *            prefix for created threads.
     * @param daemon
     *            create daemon threads?
     */
    SimpleThreadFactory(final String threadNamePrefix, final boolean daemon) {
        this.threadNamePrefix = threadNamePrefix;
        this.daemon = daemon;
    }

    /**
     * New thread.
     *
     * @param runnable
     *            the runnable
     * @return the thread
     */
    @Override
    public Thread newThread(final Runnable runnable) {
        // Call System.getSecurityManager().getThreadGroup() via reflection, since it is deprecated in JDK 17
        ThreadGroup securityManagerThreadGroup = null;
        try {
            final Method getSecurityManager = System.class.getDeclaredMethod("getSecurityManager");
            final Object securityManager = getSecurityManager.invoke(null);
            if (securityManager != null) {
                final Method getThreadGroup = securityManager.getClass().getDeclaredMethod("getThreadGroup");
                securityManagerThreadGroup = (ThreadGroup) getThreadGroup.invoke(securityManager);
            }
        } catch (final Throwable t) {
            // Fall through
        }
        final Thread thread = new Thread(
                securityManagerThreadGroup != null
                        ? securityManagerThreadGroup
                        : new ThreadGroup("ClassGraph-thread-group"),
                runnable,
                threadNamePrefix + threadIdx.getAndIncrement());
        thread.setDaemon(daemon);
        return thread;
    }
}
