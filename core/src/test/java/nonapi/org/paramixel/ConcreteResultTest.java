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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteResult")
class ConcreteResultTest {

    private static Configuration config() {
        return Configuration.of(Map.of());
    }

    private static Configuration config(String key, String value) {
        return Configuration.of(Map.of(key, value));
    }

    private static ConcreteDescriptor passedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);
        return descriptor;
    }

    private static ConcreteDescriptor failedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.FAILED);
        return descriptor;
    }

    private static ConcreteDescriptor skippedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.SKIPPED);
        return descriptor;
    }

    private static ConcreteDescriptor abortedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.ABORTED);
        return descriptor;
    }

    private static ConcreteDescriptor pendingDescriptor() {
        return new ConcreteDescriptor(Step.of("test", v -> {}));
    }

    private static ConcreteDescriptor runningDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        return descriptor;
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("throws NullPointerException when configuration is null")
        void throwsForNullConfiguration() {
            assertThatThrownBy(() -> new ConcreteResult(passedDescriptor(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("configuration is null");
        }

        @Test
        @DisplayName("single-arg constructor throws NullPointerException when configuration is null")
        void singleArgThrowsForNullConfiguration() {
            assertThatThrownBy(() -> new ConcreteResult(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("configuration is null");
        }

        @Test
        @DisplayName("accepts null root descriptor")
        void acceptsNullRoot() {
            var result = new ConcreteResult(null, config());
            assertThat(result.descriptor()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDescriptor()")
    class GetDescriptor {

        @Test
        @DisplayName("returns empty optional when root is null")
        void emptyWhenNullRoot() {
            var result = new ConcreteResult(config());
            assertThat(result.descriptor()).isEmpty();
        }

        @Test
        @DisplayName("returns present optional when root exists")
        void presentWhenRootExists() {
            var root = passedDescriptor();
            var result = new ConcreteResult(root, config());
            assertThat(result.descriptor()).containsSame(root);
        }
    }

    @Nested
    @DisplayName("getStatus() with null root")
    class GetStatusNullRoot {

        @Test
        @DisplayName("returns SKIPPED when FAIL_IF_NO_TESTS is absent")
        void skippedWhenFailIfNoTestsAbsent() {
            var result = new ConcreteResult(config());
            assertThat(result.status()).isEqualTo(Status.SKIPPED);
        }

        @Test
        @DisplayName("returns SKIPPED when FAIL_IF_NO_TESTS is false")
        void skippedWhenFailIfNoTestsFalse() {
            var result = new ConcreteResult(config(Configuration.FAIL_IF_NO_TESTS, "false"));
            assertThat(result.status()).isEqualTo(Status.SKIPPED);
        }

        @Test
        @DisplayName("returns FAILED when FAIL_IF_NO_TESTS is true")
        void failedWhenFailIfNoTestsTrue() {
            var result = new ConcreteResult(config(Configuration.FAIL_IF_NO_TESTS, "true"));
            assertThat(result.status()).isEqualTo(Status.FAILED);
        }
    }

    @Nested
    @DisplayName("getStatus() uses root status directly")
    class GetStatusDirect {

        @Test
        @DisplayName("returns PASSED when root has PASSED status")
        void passedRoot() {
            var result = new ConcreteResult(passedDescriptor(), config());
            assertThat(result.status()).isEqualTo(Status.PASSED);
        }

        @Test
        @DisplayName("returns FAILED when root has FAILED status")
        void failedRoot() {
            var result = new ConcreteResult(failedDescriptor(), config());
            assertThat(result.status()).isEqualTo(Status.FAILED);
        }

        @Test
        @DisplayName("returns SKIPPED when root has SKIPPED status and FAILURE_ON_SKIP is absent")
        void skippedRootNoPromotion() {
            var result = new ConcreteResult(skippedDescriptor(), config());
            assertThat(result.status()).isEqualTo(Status.SKIPPED);
        }

        @Test
        @DisplayName("returns ABORTED when root has ABORTED status and FAILURE_ON_ABORT is false")
        void abortedRootNoPromotion() {
            var result = new ConcreteResult(abortedDescriptor(), config(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.status()).isEqualTo(Status.ABORTED);
        }

        @Test
        @DisplayName("returns root status regardless of child statuses")
        void rootStatusIndependentOfChildren() {
            var root = passedDescriptor();
            root.addChild(failedDescriptor());
            root.addChild(skippedDescriptor());

            var result = new ConcreteResult(root, config());
            assertThat(result.status()).isEqualTo(Status.PASSED);
        }

        @Test
        @DisplayName("returns RUNNING when root has RUNNING status")
        void runningRoot() {
            var result = new ConcreteResult(runningDescriptor(), config());
            assertThat(result.status()).isEqualTo(Status.RUNNING);
        }
    }

    @Nested
    @DisplayName("promote()")
    class Promote {

        @Test
        @DisplayName("SKIPPED is promoted to FAILED when FAILURE_ON_SKIP is true")
        void skippedPromotedToFailed() {
            var result = new ConcreteResult(skippedDescriptor(), config(Configuration.FAILURE_ON_SKIP, "true"));
            assertThat(result.status()).isEqualTo(Status.FAILED);
        }

        @Test
        @DisplayName("SKIPPED is not promoted when FAILURE_ON_SKIP is false")
        void skippedNotPromotedWhenFalse() {
            var result = new ConcreteResult(skippedDescriptor(), config(Configuration.FAILURE_ON_SKIP, "false"));
            assertThat(result.status()).isEqualTo(Status.SKIPPED);
        }

        @Test
        @DisplayName("ABORTED is promoted to FAILED when FAILURE_ON_ABORT is true")
        void abortedPromotedToFailed() {
            var result = new ConcreteResult(abortedDescriptor(), config(Configuration.FAILURE_ON_ABORT, "true"));
            assertThat(result.status()).isEqualTo(Status.FAILED);
        }

        @Test
        @DisplayName("ABORTED is not promoted when FAILURE_ON_ABORT is false")
        void abortedNotPromotedWhenFalse() {
            var result = new ConcreteResult(abortedDescriptor(), config(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.status()).isEqualTo(Status.ABORTED);
        }

        @Test
        @DisplayName("ABORTED is promoted to FAILED by default when FAILURE_ON_ABORT is absent")
        void abortedPromotedByDefault() {
            var result = new ConcreteResult(abortedDescriptor(), config());
            assertThat(result.status()).isEqualTo(Status.FAILED);
        }

        @Test
        @DisplayName("PASSED is not promoted regardless of configuration")
        void passedNotPromoted() {
            var result = new ConcreteResult(passedDescriptor(), config(Configuration.FAILURE_ON_SKIP, "true"));
            assertThat(result.status()).isEqualTo(Status.PASSED);
        }

        @Test
        @DisplayName("FAILED is not promoted regardless of configuration")
        void failedNotPromoted() {
            var result = new ConcreteResult(failedDescriptor(), config(Configuration.FAILURE_ON_ABORT, "true"));
            assertThat(result.status()).isEqualTo(Status.FAILED);
        }
    }
}
