/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package examples.testcontainers.util;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import org.jspecify.annotations.NonNull;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

/**
 * Waits until a mapped container port is connectable from the host JVM.
 *
 * <p>This is intentionally host-side only and does not exec into the container.
 * That avoids failures/warnings for distroless images that lack /bin/sh.
 */
public final class HostPortSocketWaitStrategy extends AbstractWaitStrategy {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    private static final Duration SLEEP_BETWEEN_ATTEMPTS = Duration.ofMillis(100);

    private final int internalPort;

    public HostPortSocketWaitStrategy(final int internalPort) {
        this.internalPort = internalPort;
    }

    @Override
    protected void waitUntilReady() {
        final Duration timeout = startupTimeout != null ? startupTimeout : Duration.ofSeconds(60);
        final long deadlineNanos = System.nanoTime() + timeout.toNanos();

        Throwable lastFailure = null;

        while (System.nanoTime() < deadlineNanos) {
            try {
                final String host = waitStrategyTarget.getHost();
                final int port = waitStrategyTarget.getMappedPort(internalPort);
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), (int) CONNECT_TIMEOUT.toMillis());
                }

                return;
            } catch (Throwable t) {
                lastFailure = t;
            }

            try {
                Thread.sleep(SLEEP_BETWEEN_ATTEMPTS.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ContainerLaunchException("Interrupted while waiting for container port " + internalPort);
            }
        }

        throw new ContainerLaunchException(buildFailureMessage(timeout, lastFailure));
    }

    private @NonNull String buildFailureMessage(final @NonNull Duration timeout, final Throwable lastFailure) {
        String message =
                "Timed out after " + timeout + " waiting for container port " + internalPort + " to accept connections";
        if (lastFailure == null) {
            return message;
        }
        return message + ": " + lastFailure.getClass().getName() + ": " + lastFailure.getMessage();
    }
}
