/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.testcontainers.containers.output.OutputFrame;

/**
 * Testcontainers log consumer that prefixes container output.
 */
public class ContainerLogConsumer implements Consumer<OutputFrame> {

    /**
     * Prefix to prepend to log output.
     */
    private final String prefix;

    /**
     * Docker image name to include in log output.
     */
    private final String dockerImageName;

    /**
     * Creates a new log consumer.
     *
     * @param prefix the log prefix
     * @param dockerImageName the container image name
     */
    public ContainerLogConsumer(final @NonNull String prefix, final @NonNull String dockerImageName) {
        this.prefix = prefix;
        this.dockerImageName = dockerImageName;
    }

    @Override
    public void accept(final @NonNull OutputFrame outputFrame) {
        String message = outputFrame.getUtf8String();
        if (message != null) {
            message = message.trim();

            if (!message.isEmpty()) {
                System.out.printf("%s | [%s] %s%n", prefix, dockerImageName, message);
            }
        }
    }
}
