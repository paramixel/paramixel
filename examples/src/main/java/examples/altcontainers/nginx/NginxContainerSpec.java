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

package examples.altcontainers.nginx;

import java.time.Duration;
import java.util.function.Consumer;
import org.altcontainers.api.ContainerSpec;
import org.altcontainers.api.GenericContainerSpec;
import org.altcontainers.api.Network;
import org.altcontainers.api.OutputFrame;

/**
 * A {@link ContainerSpec} pre-configured for an {@code nginx} image.
 *
 * <p>Altcontainers has no Nginx module, so the image configuration is stated here
 * explicitly: exposed port 80, the foreground command
 * {@code nginx -g "daemon off;"}, an HTTP readiness check on {@code /}, a
 * 30-second startup timeout, and 3 startup attempts.
 *
 * <p>Use {@link #builder(String)} to create an {@code NginxContainerSpec}:
 *
 * <pre>{@code
 * NginxContainerSpec spec = NginxContainerSpec.builder("nginx:1.29.5")
 *         .network(network, "nginx")
 *         .outputConsumer(new ContainerLogConsumer("Nginx", "nginx:1.29.5"))
 *         .build();
 * Container container = Container.create(spec);
 * }</pre>
 */
public final class NginxContainerSpec extends GenericContainerSpec {

    /**
     * The Nginx HTTP port exposed to the host.
     */
    public static final int NGINX_PORT = 80;

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

    private static final int STARTUP_ATTEMPTS = 3;

    private NginxContainerSpec(final GenericContainerSpec genericContainerSpec) {
        super(genericContainerSpec);
    }

    /**
     * Creates a new builder for an Nginx container spec.
     *
     * @param image the Docker image name (e.g. {@code "nginx:1.29.5"})
     * @return a mutable builder pre-configured for Nginx
     */
    public static Builder builder(final String image) {
        return new Builder(image);
    }

    /**
     * Mutable builder for configuring an {@link NginxContainerSpec}.
     *
     * <p>The builder pre-fills Nginx defaults: port 80, foreground command, HTTP
     * readiness check on {@code /}, 30-second startup timeout, 3 startup attempts.
     * Callers specify only image, network, and output consumer.
     *
     * <p>Instances are mutable and not thread-safe.
     */
    public static final class Builder {

        private final GenericContainerSpec.Builder delegate;

        private Builder(final String image) {
            this.delegate = ContainerSpec.builder(image)
                    .command("nginx", "-g", "daemon off;")
                    .exposePorts(NGINX_PORT)
                    .startupTimeout(STARTUP_TIMEOUT)
                    .startupAttempts(STARTUP_ATTEMPTS)
                    .waitForHttpResponse(NGINX_PORT);
        }

        /**
         * Joins the container to a Docker network with the given DNS alias.
         *
         * @param network the network to join
         * @param alias DNS alias within the network
         * @return this builder
         */
        public Builder network(final Network network, final String alias) {
            delegate.network(network, alias);
            return this;
        }

        /**
         * Sets the output consumer for container output.
         *
         * @param consumer the output consumer
         * @return this builder
         */
        public Builder outputConsumer(final Consumer<OutputFrame> consumer) {
            delegate.outputListener(consumer);
            return this;
        }

        /**
         * Builds an immutable {@link NginxContainerSpec}.
         *
         * @return a new Nginx container spec
         */
        public NginxContainerSpec build() {
            return new NginxContainerSpec(delegate.build());
        }
    }
}
