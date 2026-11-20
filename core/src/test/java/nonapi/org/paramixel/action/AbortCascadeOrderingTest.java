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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Regression coverage for the cancellation ordering defect behind the CI flake in
 * {@code TimeoutSubtreeCancellationTest.timeoutOverSequenceInterruptsLeafAndAbortsRest}.
 *
 * <p>A {@code Timeout} cancels its body by calling {@link MutableDescriptor#abort(Status, Throwable)}
 * on it from the timer thread. That cascade must publish terminal state for the <em>whole</em>
 * subtree before it interrupts the thread executing a leaf. Otherwise the interrupted leaf unwinds
 * while its siblings are still {@link Status#PENDING}, and the resuming coordination frame - for
 * example a dependent {@code Sequential} - can still start work inside a subtree that was already
 * cancelled.
 *
 * <p>These tests observe the exact instant the interrupt is delivered, without any second thread:
 * the leaf's recorded executing thread is a {@link Thread} whose {@link Thread#interrupt()} override
 * runs synchronously on the aborting thread and snapshots the tree. That makes the race
 * deterministic instead of probabilistic.
 */
@DisplayName("Abort cascade ordering")
class AbortCascadeOrderingTest {

    private static final String TIMEOUT_MESSAGE = "timed out after 200 ms";

    /**
     * Returns a never-started thread whose {@link Thread#interrupt()} runs {@code onInterrupt}
     * synchronously on the thread performing the abort.
     *
     * <p>This is the test's clock: it captures tree state at interrupt-delivery time without
     * involving a second thread, so the assertion cannot flake in either direction.
     *
     * @param onInterrupt the observation to run at interrupt time; must not be {@code null}
     * @return the recording thread; never {@code null}
     */
    private static Thread recordingThread(final Runnable onInterrupt) {
        return new Thread(() -> {}, "abort-cascade-recorder") {
            @Override
            public void interrupt() {
                onInterrupt.run();
            }
        };
    }

    private static List<String> incompleteNames(final Descriptor descriptor) {
        var names = new ArrayList<String>();
        collectIncomplete(descriptor, names);
        return names;
    }

    private static void collectIncomplete(final Descriptor descriptor, final List<String> names) {
        if (!descriptor.isCompleted()) {
            names.add(descriptor.action().displayName());
        }
        descriptor.before().ifPresent(before -> collectIncomplete(before, names));
        for (var child : descriptor.children()) {
            collectIncomplete(child, names);
        }
        descriptor.after().ifPresent(after -> collectIncomplete(after, names));
    }

    @Test
    @DisplayName("the next sibling of an interrupted leaf is already terminal")
    void siblingIsTerminalBeforeLeafIsInterrupted() {
        // Mirrors Timeout(dependent Sequential[blocking, never-reached]): the cascade aborts the
        // sequence, which reaches - and interrupts - the blocking leaf before the sibling.
        var sequence = new ConcreteDescriptor(
                Sequential.builder("timed-sequence").dependent().build());
        var blocking = new ConcreteDescriptor(sequence, Step.of("blocking", context -> {}));
        var neverReached = new ConcreteDescriptor(sequence, Step.of("never-reached", context -> {}));
        sequence.addChild(blocking);
        sequence.addChild(neverReached);
        sequence.freeze();

        assertThat(sequence.tryStart()).isTrue();
        assertThat(blocking.tryStart()).isTrue();

        var interrupted = new AtomicBoolean();
        var siblingIncompleteAtInterrupt = new AtomicBoolean(true);
        blocking.setExecutingThread(recordingThread(() -> {
            interrupted.set(true);
            siblingIncompleteAtInterrupt.set(!neverReached.isCompleted());
        }));

        sequence.abort(Status.failed(TIMEOUT_MESSAGE), new TimeoutException(TIMEOUT_MESSAGE));

        assertThat(interrupted.get()).as("blocking leaf was interrupted").isTrue();
        assertThat(siblingIncompleteAtInterrupt.get())
                .as("sibling must be terminal before the leaf is interrupted, otherwise the "
                        + "unwinding sequence can still start it")
                .isFalse();
        assertThat(sequence.isFailed()).isTrue();
        assertThat(blocking.isAborted()).isTrue();
        assertThat(neverReached.isAborted()).isTrue();
    }

    @Test
    @DisplayName("every descriptor of a nested cancelled subtree is terminal before the interrupt")
    void nestedSubtreeIsTerminalBeforeLeafIsInterrupted() {
        var sequential =
                new ConcreteDescriptor(Sequential.builder("outer").dependent().build());
        var nested =
                new ConcreteDescriptor(sequential, Parallel.builder("inner").build());
        var blocking = new ConcreteDescriptor(nested, Step.of("blocking", context -> {}));
        var later = new ConcreteDescriptor(nested, Step.of("later", context -> {}));
        var sibling = new ConcreteDescriptor(sequential, Step.of("sibling", context -> {}));
        sequential.addChild(nested);
        nested.addChild(blocking);
        nested.addChild(later);
        sequential.addChild(sibling);
        sequential.freeze();

        assertThat(sequential.tryStart()).isTrue();
        assertThat(nested.tryStart()).isTrue();
        assertThat(blocking.tryStart()).isTrue();

        var incompleteAtInterrupt = new AtomicReference<List<String>>();
        blocking.setExecutingThread(recordingThread(() -> incompleteAtInterrupt.set(incompleteNames(sequential))));

        sequential.abort(Status.failed(TIMEOUT_MESSAGE), new TimeoutException(TIMEOUT_MESSAGE));

        var observed = incompleteAtInterrupt.get();
        assertThat(observed).as("subtree snapshot read at interrupt time").isNotNull();
        assertThat(observed)
                .as("no descriptor of the cancelled subtree may still be incomplete: %s", observed)
                .isEmpty();
    }

    @Test
    @DisplayName("a leaf that already completed is not interrupted")
    void completedLeafIsNotInterrupted() {
        var root = new ConcreteDescriptor(Parallel.builder("root").build());
        var completed = new ConcreteDescriptor(root, Step.of("completed", context -> {}));
        var pending = new ConcreteDescriptor(root, Step.of("pending", context -> {}));
        root.addChild(completed);
        root.addChild(pending);
        root.freeze();

        assertThat(root.tryStart()).isTrue();
        assertThat(completed.tryStart()).isTrue();
        completed.setStatus(Status.PASSED);

        var interrupted = new AtomicBoolean();
        completed.setExecutingThread(recordingThread(() -> interrupted.set(true)));

        root.abort(Status.failed(TIMEOUT_MESSAGE), new TimeoutException(TIMEOUT_MESSAGE));

        assertThat(interrupted.get()).as("completed leaf interrupted").isFalse();
        assertThat(completed.isPassed()).as("completed leaf status").isTrue();
        assertThat(pending.isAborted()).isTrue();
    }

    @Test
    @DisplayName("aborting an already cancelled subtree does not repeat the interrupt")
    void repeatedAbortDoesNotRepeatInterrupts() {
        var sequence = new ConcreteDescriptor(
                Sequential.builder("timed-sequence").dependent().build());
        var blocking = new ConcreteDescriptor(sequence, Step.of("blocking", context -> {}));
        sequence.addChild(blocking);
        sequence.freeze();

        assertThat(sequence.tryStart()).isTrue();
        assertThat(blocking.tryStart()).isTrue();

        var interrupts = new AtomicInteger();
        blocking.setExecutingThread(recordingThread(interrupts::incrementAndGet));

        sequence.abort(Status.failed(TIMEOUT_MESSAGE), new TimeoutException(TIMEOUT_MESSAGE));
        sequence.abort(Status.failed(TIMEOUT_MESSAGE), new TimeoutException(TIMEOUT_MESSAGE));

        assertThat(interrupts.get()).as("interrupt count").isEqualTo(1);
    }
}
