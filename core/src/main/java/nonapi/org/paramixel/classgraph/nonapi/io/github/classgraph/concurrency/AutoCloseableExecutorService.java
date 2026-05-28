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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A ThreadPoolExecutor that can be used in a try-with-resources block. */
public class AutoCloseableExecutorService extends ThreadPoolExecutor implements AutoCloseable {
    /** The {@link InterruptionChecker}. */
    public final InterruptionChecker interruptionChecker = new InterruptionChecker();

    /**
     * A ThreadPoolExecutor that can be used in a try-with-resources block.
     *
     * @param numThreads
     *            The number of threads to allocate.
     */
    public AutoCloseableExecutorService(final int numThreads) {
        super(
                numThreads,
                numThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new SimpleThreadFactory("ClassGraph-worker-", true));
    }

    /**
     * Catch exceptions from both submit() and execute(), and call {@link InterruptionChecker#interrupt()} to
     * interrupt all threads.
     *
     * @param runnable
     *            the Runnable
     * @param throwable
     *            the Throwable
     */
    @Override
    public void afterExecute(final Runnable runnable, final Throwable throwable) {
        super.afterExecute(runnable, throwable);
        if (throwable != null) {
            // Wrap the throwable in an ExecutionException (execute() does not do this)
            interruptionChecker.setExecutionException(new ExecutionException("Uncaught exception", throwable));
            // execute() was called and an uncaught exception or error was thrown
            interruptionChecker.interrupt();
        } else if (
        /* throwable == null && */ runnable instanceof Future<?>) {
            // submit() was called, so throwable is not set
            try {
                // This call will not block, since execution has finished
                ((Future<?>) runnable).get();
            } catch (CancellationException | InterruptedException e) {
                // If this thread was cancelled or interrupted, interrupt other threads
                interruptionChecker.interrupt();
            } catch (final ExecutionException e) {
                // Record the exception that was thrown by the thread
                interruptionChecker.setExecutionException(e);
                // Interrupt other threads
                interruptionChecker.interrupt();
            }
        }
    }

    /** Shut down thread pool on close(). */
    @Override
    public void close() {
        try {
            // Prevent new tasks being submitted
            shutdown();
        } catch (final SecurityException e) {
            // Ignore for now (caught again if shutdownNow() fails)
        }
        boolean terminated = false;
        try {
            // Await termination of any running tasks
            terminated = awaitTermination(2500, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            interruptionChecker.interrupt();
        }
        if (!terminated) {
            try {
                // Interrupt all the threads to terminate them, if awaitTermination() timed out
                shutdownNow();
            } catch (final SecurityException e) {
                throw new RuntimeException(
                        "Could not shut down ExecutorService -- need "
                                + "java.lang.RuntimePermission(\"modifyThread\"), "
                                + "or the security manager's checkAccess method denies access",
                        e);
            }
        }
    }
}
