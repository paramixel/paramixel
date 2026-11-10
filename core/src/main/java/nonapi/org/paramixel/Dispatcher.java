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

package nonapi.org.paramixel;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Hands out the engine's worker permits and remembers which {@code Parallel} frames are waiting
 * for one.
 *
 * <p>A permit represents one live worker stack. A frame that has unstarted children but could not
 * obtain a permit is registered as <em>hungry</em>; the next worker to run out of work serves the
 * oldest hungry frame instead of releasing its permit. Registration and hand-off are atomic with
 * respect to each other, so a permit can never be released while a hungry frame is queued
 * (no lost wake-up), and nothing ever blocks waiting for a permit.
 *
 * <p>Serving a hungry frame happens only on an otherwise empty worker stack, so the worker carries
 * no reservations from another subtree. This is deliberately not work stealing.
 */
final class Dispatcher {

    private final Set<ParallelFrame> hungry = new LinkedHashSet<>();
    private int permits;
    private boolean closed;

    Dispatcher(final int permits) {
        this.permits = permits;
    }

    /**
     * Takes a permit if one is free.
     *
     * @return {@code true} if a permit was taken; the caller must {@link #release()} it
     */
    synchronized boolean tryAcquire() {
        if (closed || permits == 0) {
            return false;
        }
        permits--;
        return true;
    }

    /**
     * Takes a permit for the frame if one is free; otherwise registers the frame as hungry.
     *
     * @param frame the frame that wants another worker; must not be {@code null}
     * @return {@code true} if a permit was taken, {@code false} if the frame was queued instead
     */
    synchronized boolean acquireOrQueue(final ParallelFrame frame) {
        if (tryAcquire()) {
            return true;
        }
        if (!closed) {
            hungry.add(frame);
        }
        return false;
    }

    /**
     * Returns a permit that was taken but will not be used.
     */
    synchronized void release() {
        if (!closed) {
            permits++;
        }
    }

    /**
     * Called by a worker that holds a permit and has no work left. Either returns the oldest hungry
     * frame that the worker has joined, keeping the permit, or releases the permit and returns
     * {@code null}.
     *
     * @return a frame the caller must now run a lane for, or {@code null} if the permit was released
     */
    ParallelFrame handOffOrRelease() {
        while (true) {
            ParallelFrame frame;
            synchronized (this) {
                var iterator = hungry.iterator();
                frame = next(iterator);
                if (frame == null) {
                    release();
                    return null;
                }
            }
            if (frame.tryJoin()) {
                return frame;
            }
        }
    }

    private static ParallelFrame next(final Iterator<ParallelFrame> iterator) {
        if (!iterator.hasNext()) {
            return null;
        }
        var frame = iterator.next();
        iterator.remove();
        return frame;
    }

    /**
     * Forgets a frame that has finished.
     *
     * @param frame the finished frame; must not be {@code null}
     */
    synchronized void forget(final ParallelFrame frame) {
        hungry.remove(frame);
    }

    synchronized int availablePermits() {
        return permits;
    }

    synchronized void close() {
        closed = true;
        permits = 0;
        hungry.clear();
    }
}
