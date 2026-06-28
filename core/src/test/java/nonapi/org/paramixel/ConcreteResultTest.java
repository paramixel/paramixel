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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteResult")
class ConcreteResultTest {

    private static Configuration configuration() {
        return Configuration.of(Map.of());
    }

    private static Configuration configuration(String key, String value) {
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
            var result = new ConcreteResult(null, configuration());
            assertThat(result.descriptor()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDescriptor()")
    class GetDescriptor {

        @Test
        @DisplayName("returns empty optional when root is null")
        void emptyWhenNullRoot() {
            var result = new ConcreteResult(configuration());
            assertThat(result.descriptor()).isEmpty();
        }

        @Test
        @DisplayName("returns present optional when root exists")
        void presentWhenRootExists() {
            var root = passedDescriptor();
            var result = new ConcreteResult(root, configuration());
            assertThat(result.descriptor()).containsSame(root);
        }
    }

    @Nested
    @DisplayName("getStatus() with null root")
    class GetStatusNullRoot {

        @Test
        @DisplayName("returns SKIPPED when FAIL_IF_NO_TESTS is absent")
        void skippedWhenFailIfNoTestsAbsent() {
            var result = new ConcreteResult(configuration());
            assertThat(result.isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns SKIPPED when FAIL_IF_NO_TESTS is false")
        void skippedWhenFailIfNoTestsFalse() {
            var result = new ConcreteResult(configuration(Configuration.FAIL_IF_NO_TESTS, "false"));
            assertThat(result.isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns FAILED when FAIL_IF_NO_TESTS is true")
        void failedWhenFailIfNoTestsTrue() {
            var result = new ConcreteResult(configuration(Configuration.FAIL_IF_NO_TESTS, "true"));
            assertThat(result.isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getStatus() uses root status directly")
    class GetStatusDirect {

        @Test
        @DisplayName("returns PASSED when root has PASSED status")
        void passedRoot() {
            var result = new ConcreteResult(passedDescriptor(), configuration());
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("returns FAILED when root has FAILED status")
        void failedRoot() {
            var result = new ConcreteResult(failedDescriptor(), configuration());
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("returns SKIPPED when root has SKIPPED status and FAILURE_ON_SKIP is absent")
        void skippedRootNoPromotion() {
            var result = new ConcreteResult(skippedDescriptor(), configuration());
            assertThat(result.isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns ABORTED when root has ABORTED status and FAILURE_ON_ABORT is false")
        void abortedRootNoPromotion() {
            var result =
                    new ConcreteResult(abortedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.isAborted()).isTrue();
        }

        @Test
        @DisplayName("returns root status regardless of child statuses")
        void rootStatusIndependentOfChildren() {
            var root = passedDescriptor();
            root.addChild(failedDescriptor());
            root.addChild(skippedDescriptor());

            var result = new ConcreteResult(root, configuration());
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("returns RUNNING when root has RUNNING status")
        void runningRoot() {
            var result = new ConcreteResult(runningDescriptor(), configuration());
            assertThat(result.status()).isEqualTo(Status.RUNNING);
        }
    }

    @Nested
    @DisplayName("promote()")
    class Promote {

        @Test
        @DisplayName("SKIPPED is promoted to FAILED when FAILURE_ON_SKIP is true")
        void skippedPromotedToFailed() {
            var result = new ConcreteResult(skippedDescriptor(), configuration(Configuration.FAILURE_ON_SKIP, "true"));
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("SKIPPED is not promoted when FAILURE_ON_SKIP is false")
        void skippedNotPromotedWhenFalse() {
            var result = new ConcreteResult(skippedDescriptor(), configuration(Configuration.FAILURE_ON_SKIP, "false"));
            assertThat(result.isSkipped()).isTrue();
        }

        @Test
        @DisplayName("ABORTED is promoted to FAILED when FAILURE_ON_ABORT is true")
        void abortedPromotedToFailed() {
            var result = new ConcreteResult(abortedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "true"));
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("ABORTED is not promoted when FAILURE_ON_ABORT is false")
        void abortedNotPromotedWhenFalse() {
            var result =
                    new ConcreteResult(abortedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.isAborted()).isTrue();
        }

        @Test
        @DisplayName("ABORTED is promoted to FAILED by default when FAILURE_ON_ABORT is absent")
        void abortedPromotedByDefault() {
            var result = new ConcreteResult(abortedDescriptor(), configuration());
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("PASSED is not promoted regardless of configuration")
        void passedNotPromoted() {
            var result = new ConcreteResult(passedDescriptor(), configuration(Configuration.FAILURE_ON_SKIP, "true"));
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("FAILED is not promoted regardless of configuration")
        void failedNotPromoted() {
            var result = new ConcreteResult(failedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "true"));
            assertThat(result.isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("isPassed()")
    class IsPassed {

        @Test
        @DisplayName("returns true when effective status is PASSED")
        void trueWhenPassed() {
            var result = new ConcreteResult(passedDescriptor(), configuration());
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("returns false when effective status is not PASSED")
        void falseWhenNotPassed() {
            var result = new ConcreteResult(failedDescriptor(), configuration());
            assertThat(result.isPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("timing")
    class Timing {

        @Test
        @DisplayName("returns empty timestamps when root is null")
        void emptyWhenRootIsNull() {
            var result = new ConcreteResult(configuration());

            assertThat(result.startedAt()).isEmpty();
            assertThat(result.completedAt()).isEmpty();
        }

        @Test
        @DisplayName("derives earliest start and latest completion from whole tree")
        void derivesWholeTreeTiming() throws InterruptedException {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            var before = passedDescriptor();
            sleepForTimestampOrdering();
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            var child = passedDescriptor();
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);
            sleepForTimestampOrdering();
            var after = passedDescriptor();
            root.setBefore(before);
            root.addChild(child);
            root.setAfter(after);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isEqualTo(before.startedAt());
            assertThat(result.completedAt()).isEqualTo(after.completedAt());
        }

        @Test
        @DisplayName("derives timing from children-only tree without before or after")
        void derivesTimingFromChildrenOnlyTree() throws InterruptedException {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            var child1 = passedDescriptor();
            sleepForTimestampOrdering();
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            var child2 = passedDescriptor();
            sleepForTimestampOrdering();
            var child3 = passedDescriptor();
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);
            root.addChild(child1);
            root.addChild(child2);
            root.addChild(child3);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isPresent();
            assertThat(result.completedAt()).isPresent();
            assertThat(result.startedAt().orElseThrow())
                    .isBefore(result.completedAt().orElseThrow());
        }

        @Test
        @DisplayName("derives timing from tree with before and children but no after")
        void derivesTimingFromBeforeAndChildrenTree() throws InterruptedException {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            var before = passedDescriptor();
            sleepForTimestampOrdering();
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            var child = passedDescriptor();
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);
            root.setBefore(before);
            root.addChild(child);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isEqualTo(before.startedAt());
            assertThat(result.completedAt()).isEqualTo(root.completedAt());
        }

        @Test
        @DisplayName("disregards descriptors without timestamps")
        void disregardsDescriptorsWithoutTimestamps() throws InterruptedException {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isEqualTo(root.startedAt());
            assertThat(result.completedAt()).isEqualTo(root.completedAt());
        }

        private void sleepForTimestampOrdering() throws InterruptedException {
            Thread.sleep(2L);
        }
    }

    @Nested
    @DisplayName("status resolution for non-MutableDescriptor")
    class StatusResolutionNonMutable {

        @Test
        @DisplayName("returns PASSED for passed non-mutable descriptor")
        void passedNonMutableDescriptor() {
            var descriptor = descriptorWithStatus(Status.PASSED);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("returns FAILED for failed non-mutable descriptor")
        void failedNonMutableDescriptor() {
            var descriptor = descriptorWithStatus(Status.FAILED);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("returns ABORTED for aborted non-mutable descriptor")
        void abortedNonMutableDescriptor() {
            var descriptor = descriptorWithStatus(Status.ABORTED);
            var result = new ConcreteResult(descriptor, configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.isAborted()).isTrue();
        }

        @Test
        @DisplayName("returns SKIPPED for skipped non-mutable descriptor")
        void skippedNonMutableDescriptor() {
            var descriptor = descriptorWithStatus(Status.SKIPPED);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns RUNNING for pending non-mutable descriptor")
        void runningForPendingNonMutableDescriptor() {
            var descriptor = descriptorWithIncompleteStatus(false, false, false, false);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.status()).isEqualTo(Status.RUNNING);
        }

        @Test
        @DisplayName("promotion from SKIPPED to FAILED for non-mutable descriptor")
        void promotedSkippedNonMutable() {
            var descriptor = descriptorWithStatus(Status.SKIPPED);
            var result = new ConcreteResult(descriptor, configuration(Configuration.FAILURE_ON_SKIP, "true"));
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("default ABORTED promotes to FAILED for non-mutable descriptor")
        void defaultAbortedPromotedNonMutable() {
            var descriptor = descriptorWithStatus(Status.ABORTED);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("preserves message for failed non-mutable descriptor")
        void preservesMessageForFailedNonMutable() {
            var descriptor = descriptorWithStatusAndDetail(Status.FAILED, "timeout", null);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isFailed()).isTrue();
            assertThat(result.status().message()).hasValue("timeout");
            assertThat(result.status().throwable()).isEmpty();
        }

        @Test
        @DisplayName("preserves message and throwable for failed non-mutable descriptor")
        void preservesMessageAndThrowableForFailedNonMutable() {
            var cause = new RuntimeException("boom");
            var descriptor = descriptorWithStatusAndDetail(Status.FAILED, "timeout", cause);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isFailed()).isTrue();
            assertThat(result.status().message()).hasValue("timeout");
            assertThat(result.status().throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("preserves throwable with default message when descriptor has no message")
        void preservesThrowableWithDefaultMessageForFailedNonMutable() {
            var cause = new RuntimeException("boom");
            var descriptor = descriptorWithStatusAndDetail(Status.FAILED, null, cause);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isFailed()).isTrue();
            assertThat(result.status().message()).hasValue("action failed");
            assertThat(result.status().throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("preserves message for aborted non-mutable descriptor")
        void preservesMessageForAbortedNonMutable() {
            var descriptor = descriptorWithStatusAndDetail(Status.ABORTED, "precondition failed", null);
            var result = new ConcreteResult(descriptor, configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.isAborted()).isTrue();
            assertThat(result.status().message()).hasValue("precondition failed");
            assertThat(result.status().throwable()).isEmpty();
        }

        @Test
        @DisplayName("preserves message and throwable for aborted non-mutable descriptor")
        void preservesMessageAndThrowableForAbortedNonMutable() {
            var cause = new RuntimeException("precondition");
            var descriptor = descriptorWithStatusAndDetail(Status.ABORTED, "precondition failed", cause);
            var result = new ConcreteResult(descriptor, configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.isAborted()).isTrue();
            assertThat(result.status().message()).hasValue("precondition failed");
            assertThat(result.status().throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("preserves message for skipped non-mutable descriptor")
        void preservesMessageForSkippedNonMutable() {
            var descriptor = descriptorWithStatusAndDetail(Status.SKIPPED, "disabled", null);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isSkipped()).isTrue();
            assertThat(result.status().message()).hasValue("disabled");
            assertThat(result.status().throwable()).isEmpty();
        }

        @Test
        @DisplayName("returns canonical constant when no message or throwable present")
        void canonicalConstantWhenNoDetailForFailedNonMutable() {
            var descriptor = descriptorWithStatusAndDetail(Status.FAILED, null, null);
            var result = new ConcreteResult(descriptor, configuration());
            assertThat(result.isFailed()).isTrue();
            assertThat(result.status().message()).isEmpty();
            assertThat(result.status().throwable()).isEmpty();
        }

        @Test
        @DisplayName("existing no-detail stubs still resolve correctly after fix")
        void existingNoDetailStubsStillResolveCorrectly() {
            assertThat(new ConcreteResult(descriptorWithStatus(Status.FAILED), configuration()).isFailed())
                    .isTrue();
            assertThat(new ConcreteResult(
                                    descriptorWithStatus(Status.ABORTED),
                                    configuration(Configuration.FAILURE_ON_ABORT, "false"))
                            .isAborted())
                    .isTrue();
            assertThat(new ConcreteResult(descriptorWithStatus(Status.SKIPPED), configuration()).isSkipped())
                    .isTrue();
        }

        private Descriptor descriptorWithStatus(final Status status) {
            boolean isPassed = status.isPassed();
            boolean isFailed = status.isFailed();
            boolean isAborted = status.isAborted();
            boolean isSkipped = status.isSkipped();
            return descriptorWithIncompleteStatus(isPassed, isFailed, isAborted, isSkipped);
        }

        private Descriptor descriptorWithIncompleteStatus(
                final boolean isPassed, final boolean isFailed, final boolean isAborted, final boolean isSkipped) {
            boolean completed = isPassed || isFailed || isAborted || isSkipped;
            return new Descriptor() {
                @Override
                public Optional<Descriptor> parent() {
                    return Optional.empty();
                }

                @Override
                public String id() {
                    return "stub";
                }

                @Override
                public Action action() {
                    return Step.of("stub", v -> {});
                }

                @Override
                public boolean isPassed() {
                    return isPassed;
                }

                @Override
                public boolean isFailed() {
                    return isFailed;
                }

                @Override
                public boolean isSkipped() {
                    return isSkipped;
                }

                @Override
                public boolean isAborted() {
                    return isAborted;
                }

                @Override
                public Optional<Instant> startedAt() {
                    return Optional.empty();
                }

                @Override
                public Optional<Instant> completedAt() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> message() {
                    return Optional.empty();
                }

                @Override
                public Optional<Throwable> throwable() {
                    return Optional.empty();
                }

                @Override
                public boolean isCompleted() {
                    return completed;
                }

                @Override
                public List<Descriptor> children() {
                    return List.of();
                }
            };
        }

        private Descriptor descriptorWithStatusAndDetail(
                final Status status, final String message, final Throwable throwable) {
            boolean isPassed = status.isPassed();
            boolean isFailed = status.isFailed();
            boolean isAborted = status.isAborted();
            boolean isSkipped = status.isSkipped();
            boolean completed = isPassed || isFailed || isAborted || isSkipped;
            return new Descriptor() {
                @Override
                public Optional<Descriptor> parent() {
                    return Optional.empty();
                }

                @Override
                public String id() {
                    return "stub";
                }

                @Override
                public Action action() {
                    return Step.of("stub", v -> {});
                }

                @Override
                public boolean isPassed() {
                    return isPassed;
                }

                @Override
                public boolean isFailed() {
                    return isFailed;
                }

                @Override
                public boolean isSkipped() {
                    return isSkipped;
                }

                @Override
                public boolean isAborted() {
                    return isAborted;
                }

                @Override
                public Optional<Instant> startedAt() {
                    return Optional.empty();
                }

                @Override
                public Optional<Instant> completedAt() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> message() {
                    return Optional.ofNullable(message);
                }

                @Override
                public Optional<Throwable> throwable() {
                    return Optional.ofNullable(throwable);
                }

                @Override
                public boolean isCompleted() {
                    return completed;
                }

                @Override
                public List<Descriptor> children() {
                    return List.of();
                }
            };
        }
    }
}
