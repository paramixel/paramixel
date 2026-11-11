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
import nonapi.org.paramixel.action.TestDescriptors;
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
        var descriptor = new ConcreteDescriptor(Step.step("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);
        return descriptor;
    }

    private static ConcreteDescriptor failedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.step("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.FAILED);
        return descriptor;
    }

    private static ConcreteDescriptor skippedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.step("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.SKIPPED);
        return descriptor;
    }

    private static ConcreteDescriptor abortedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.step("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.ABORTED);
        return descriptor;
    }

    private static ConcreteDescriptor pendingDescriptor() {
        return new ConcreteDescriptor(Step.step("test", v -> {}));
    }

    private static ConcreteDescriptor runningDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.step("test", v -> {}));
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
            assertThat(result.status().isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns SKIPPED when FAIL_IF_NO_TESTS is false")
        void skippedWhenFailIfNoTestsFalse() {
            var result = new ConcreteResult(configuration(Configuration.FAIL_IF_NO_TESTS, "false"));
            assertThat(result.status().isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns FAILED when FAIL_IF_NO_TESTS is true")
        void failedWhenFailIfNoTestsTrue() {
            var result = new ConcreteResult(configuration(Configuration.FAIL_IF_NO_TESTS, "true"));
            assertThat(result.status().isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getStatus() uses root status directly")
    class GetStatusDirect {

        @Test
        @DisplayName("returns PASSED when root has PASSED status")
        void passedRoot() {
            var result = new ConcreteResult(passedDescriptor(), configuration());
            assertThat(result.status().isPassed()).isTrue();
        }

        @Test
        @DisplayName("returns FAILED when root has FAILED status")
        void failedRoot() {
            var result = new ConcreteResult(failedDescriptor(), configuration());
            assertThat(result.status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("returns SKIPPED when root has SKIPPED status and FAILURE_ON_SKIP is absent")
        void skippedRootNoPromotion() {
            var result = new ConcreteResult(skippedDescriptor(), configuration());
            assertThat(result.status().isSkipped()).isTrue();
        }

        @Test
        @DisplayName("returns ABORTED when root has ABORTED status and FAILURE_ON_ABORT is false")
        void abortedRootNoPromotion() {
            var result =
                    new ConcreteResult(abortedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.status().isAborted()).isTrue();
        }

        @Test
        @DisplayName("returns root status regardless of child statuses")
        void rootStatusIndependentOfChildren() {
            var root =
                    TestDescriptors.tree(Step.step("test", v -> {}), List.of(failedDescriptor(), skippedDescriptor()));
            TestDescriptors.completed(root, Status.PASSED);

            var result = new ConcreteResult(root, configuration());
            assertThat(result.status().isPassed()).isTrue();
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
            assertThat(result.status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("SKIPPED is not promoted when FAILURE_ON_SKIP is false")
        void skippedNotPromotedWhenFalse() {
            var result = new ConcreteResult(skippedDescriptor(), configuration(Configuration.FAILURE_ON_SKIP, "false"));
            assertThat(result.status().isSkipped()).isTrue();
        }

        @Test
        @DisplayName("ABORTED is promoted to FAILED when FAILURE_ON_ABORT is true")
        void abortedPromotedToFailed() {
            var result = new ConcreteResult(abortedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "true"));
            assertThat(result.status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("ABORTED is not promoted when FAILURE_ON_ABORT is false")
        void abortedNotPromotedWhenFalse() {
            var result =
                    new ConcreteResult(abortedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "false"));
            assertThat(result.status().isAborted()).isTrue();
        }

        @Test
        @DisplayName("ABORTED is promoted to FAILED by default when FAILURE_ON_ABORT is absent")
        void abortedPromotedByDefault() {
            var result = new ConcreteResult(abortedDescriptor(), configuration());
            assertThat(result.status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("PASSED is not promoted regardless of configuration")
        void passedNotPromoted() {
            var result = new ConcreteResult(passedDescriptor(), configuration(Configuration.FAILURE_ON_SKIP, "true"));
            assertThat(result.status().isPassed()).isTrue();
        }

        @Test
        @DisplayName("FAILED is not promoted regardless of configuration")
        void failedNotPromoted() {
            var result = new ConcreteResult(failedDescriptor(), configuration(Configuration.FAILURE_ON_ABORT, "true"));
            assertThat(result.status().isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("isPassed()")
    class IsPassed {

        @Test
        @DisplayName("returns true when effective status is PASSED")
        void trueWhenPassed() {
            var result = new ConcreteResult(passedDescriptor(), configuration());
            assertThat(result.status().isPassed()).isTrue();
        }

        @Test
        @DisplayName("returns false when effective status is not PASSED")
        void falseWhenNotPassed() {
            var result = new ConcreteResult(failedDescriptor(), configuration());
            assertThat(result.status().isPassed()).isFalse();
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
            var before = TestDescriptors.of(Step.step("before", v -> {}));
            var child = TestDescriptors.of(Step.step("child", v -> {}));
            var after = TestDescriptors.of(Step.step("after", v -> {}));
            var root = TestDescriptors.tree(Step.step("root", v -> {}), before, List.of(child), after);

            TestDescriptors.completed(before, Status.PASSED);
            sleepForTimestampOrdering();
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            TestDescriptors.completed(child, Status.PASSED);
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);
            sleepForTimestampOrdering();
            TestDescriptors.completed(after, Status.PASSED);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isEqualTo(before.startedAt());
            assertThat(result.completedAt()).isEqualTo(after.completedAt());
        }

        @Test
        @DisplayName("derives timing from children-only tree without before or after")
        void derivesTimingFromChildrenOnlyTree() throws InterruptedException {
            var child1 = TestDescriptors.of(Step.step("child-1", v -> {}));
            var child2 = TestDescriptors.of(Step.step("child-2", v -> {}));
            var child3 = TestDescriptors.of(Step.step("child-3", v -> {}));
            var root = TestDescriptors.tree(Step.step("root", v -> {}), List.of(child1, child2, child3));

            TestDescriptors.completed(child1, Status.PASSED);
            sleepForTimestampOrdering();
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            TestDescriptors.completed(child2, Status.PASSED);
            sleepForTimestampOrdering();
            TestDescriptors.completed(child3, Status.PASSED);
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isPresent();
            assertThat(result.completedAt()).isPresent();
            assertThat(result.startedAt().orElseThrow())
                    .isBefore(result.completedAt().orElseThrow());
        }

        @Test
        @DisplayName("derives timing from tree with before and children but no after")
        void derivesTimingFromBeforeAndChildrenTree() throws InterruptedException {
            var before = TestDescriptors.of(Step.step("before", v -> {}));
            var child = TestDescriptors.of(Step.step("child", v -> {}));
            var root = TestDescriptors.tree(Step.step("root", v -> {}), before, List.of(child), null);

            TestDescriptors.completed(before, Status.PASSED);
            sleepForTimestampOrdering();
            root.setStatus(Status.RUNNING);
            sleepForTimestampOrdering();
            TestDescriptors.completed(child, Status.PASSED);
            sleepForTimestampOrdering();
            root.setStatus(Status.PASSED);

            var result = new ConcreteResult(root, configuration());

            assertThat(result.startedAt()).isEqualTo(before.startedAt());
            assertThat(result.completedAt()).isEqualTo(root.completedAt());
        }

        @Test
        @DisplayName("disregards descriptors without timestamps")
        void disregardsDescriptorsWithoutTimestamps() throws InterruptedException {
            var root = new ConcreteDescriptor(Step.step("root", v -> {}));
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
    @DisplayName("status resolution for a Descriptor that is not a MutableDescriptor")
    class StatusResolutionNonMutable {

        @Test
        @DisplayName("uses the descriptor status for every kind")
        void usesDescriptorStatus() {
            assertThat(new ConcreteResult(stubDescriptor(Status.PASSED), configuration())
                            .status()
                            .kind())
                    .isEqualTo(Status.Kind.PASSED);
            assertThat(new ConcreteResult(stubDescriptor(Status.FAILED), configuration())
                            .status()
                            .kind())
                    .isEqualTo(Status.Kind.FAILED);
            assertThat(new ConcreteResult(
                                    stubDescriptor(Status.ABORTED),
                                    configuration(Configuration.FAILURE_ON_ABORT, "false"))
                            .status()
                            .kind())
                    .isEqualTo(Status.Kind.ABORTED);
            assertThat(new ConcreteResult(stubDescriptor(Status.SKIPPED), configuration())
                            .status()
                            .kind())
                    .isEqualTo(Status.Kind.SKIPPED);
            assertThat(new ConcreteResult(stubDescriptor(Status.PENDING), configuration())
                            .status()
                            .kind())
                    .isEqualTo(Status.Kind.PENDING);
        }

        @Test
        @DisplayName("promotes SKIPPED to FAILED when failureOnSkip is true")
        void promotesSkipped() {
            var result = new ConcreteResult(
                    stubDescriptor(Status.skipped("disabled")), configuration(Configuration.FAILURE_ON_SKIP, "true"));

            assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        }

        @Test
        @DisplayName("promotes ABORTED to FAILED by default")
        void promotesAbortedByDefault() {
            var result = new ConcreteResult(stubDescriptor(Status.ABORTED), configuration());

            assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        }

        @Test
        @DisplayName("promotion keeps the message and throwable")
        void promotionKeepsDetail() {
            var cause = new RuntimeException("precondition");
            var result =
                    new ConcreteResult(stubDescriptor(Status.aborted("precondition failed", cause)), configuration());

            assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
            assertThat(result.status().message()).hasValue("precondition failed");
            assertThat(result.status().throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("promotion keeps a skipped message")
        void promotionKeepsSkippedMessage() {
            var result = new ConcreteResult(
                    stubDescriptor(Status.skipped("disabled")), configuration(Configuration.FAILURE_ON_SKIP, "true"));

            assertThat(result.status().message()).hasValue("disabled");
        }

        private Descriptor stubDescriptor(final Status status) {
            return new Descriptor() {
                private final Action action = Step.step("stub", ignored -> {});

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
                    return action;
                }

                @Override
                public String path() {
                    return "stub";
                }

                @Override
                public Status status() {
                    return status;
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
                public List<Descriptor> children() {
                    return List.of();
                }
            };
        }
    }

    @Nested
    @DisplayName("computed once at construction")
    class ComputedOnce {

        @Test
        @DisplayName("status, start and completion are captured when the result is created")
        void capturesStateOnce() {
            var descriptor = passedDescriptor();
            var result = new ConcreteResult(descriptor, configuration());

            var status = result.status();
            var startedAt = result.startedAt();
            var completedAt = result.completedAt();

            assertThat(result.status()).isSameAs(status);
            assertThat(result.startedAt()).isEqualTo(startedAt);
            assertThat(result.completedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("a start and completion instant are reported for a completed tree")
        void reportsTimestamps() {
            var result = new ConcreteResult(passedDescriptor(), configuration());

            assertThat(result.startedAt()).isPresent();
            assertThat(result.completedAt()).isPresent();
        }

        @Test
        @DisplayName("exitCode is 0 for passed and 1 for failed")
        void exitCode() {
            assertThat(new ConcreteResult(passedDescriptor(), configuration()).exitCode())
                    .isZero();
            assertThat(new ConcreteResult(failedDescriptor(), configuration()).exitCode())
                    .isOne();
        }

        @Test
        @DisplayName("exitCode follows the promotions of failureOnSkip and failureOnAbort")
        void exitCodeFollowsPromotions() {
            var skipped = skippedDescriptor();
            var aborted = abortedDescriptor();

            assertThat(new ConcreteResult(skipped, configuration()).exitCode()).isZero();
            assertThat(new ConcreteResult(skipped, configuration(Configuration.FAILURE_ON_SKIP, "true")).exitCode())
                    .isOne();
            assertThat(new ConcreteResult(aborted, configuration()).exitCode()).isOne();
            assertThat(new ConcreteResult(aborted, configuration(Configuration.FAILURE_ON_ABORT, "false")).exitCode())
                    .isZero();
        }

        @Test
        @DisplayName("no discovered descriptor follows failureOnNoTests")
        void noTestsExitCode() {
            assertThat(new ConcreteResult(configuration()).exitCode()).isZero();

            var failIfNoTests =
                    new ConcreteResult(configuration(Configuration.FAIL_IF_NO_TESTS, "true"), Status.PASSED);
            assertThat(failIfNoTests.exitCode()).isZero();

            var noTests = new ConcreteResult(configuration(Configuration.FAIL_IF_NO_TESTS, "true"));
            assertThat(noTests.status().kind()).isEqualTo(Status.Kind.FAILED);
            assertThat(noTests.exitCode()).isOne();
            assertThat(noTests.descriptor()).isEmpty();
        }
    }
}
