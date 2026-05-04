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

package examples.testcontainers.util;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

public class HostPortSocketWaitStrategy extends AbstractWaitStrategy {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);

    private static final Duration SLEEP_BETWEEN_ATTEMPTS = Duration.ofMillis(100);

    private final int internalPort;

    public HostPortSocketWaitStrategy(final int internalPort) {
        this.internalPort = internalPort;
    }

    @Override
    protected void waitUntilReady() {
        final Duration timeout = startupTimeout;
        final long deadlineNanos = System.nanoTime() + timeout.toNanos();

        Throwable lastFailure = null;

        while (System.nanoTime() < deadlineNanos) {
            try {
                final String host = waitStrategyTarget.getHost();
                final int port = waitStrategyTarget.getMappedPort(internalPort);
                try (var socket = new Socket()) {
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

        if (lastFailure != null) {
            throw new ContainerLaunchException(buildFailureMessage(timeout, lastFailure));
        }
    }

    private String buildFailureMessage(final Duration timeout, final Throwable lastFailure) {
        var message =
                "Timed out after " + timeout + " waiting for container port " + internalPort + " to accept connections";
        return message + ": " + lastFailure.getClass().getName() + ": " + lastFailure.getMessage();
    }
}
