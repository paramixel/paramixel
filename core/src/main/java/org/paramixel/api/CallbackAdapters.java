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

package org.paramixel.api;

/**
 * Adapts typed authoring callbacks to the context-aware {@link TaskCallback} the runtime invokes.
 *
 * <p>Adaptation happens once, when a builder snapshots its definition. The adapter receives the primary instance from
 * the execution context, which the compiler bound lexically; no reflective lookup and no generic type token is
 * involved at execution time.
 */
final class CallbackAdapters {

    private CallbackAdapters() {
        // Intentionally empty
    }

    @SuppressWarnings("unchecked")
    static <T> TaskCallback instanceConsumer(final ThrowingConsumer<? super T> consumer) {
        final ThrowingConsumer<Object> erased = (ThrowingConsumer<Object>) consumer;
        return context -> erased.accept(context.primaryInstance());
    }

    static TaskCallback runnable(final ThrowingRunnable runnable) {
        return context -> runnable.run();
    }

    @SuppressWarnings("unchecked")
    static <T> TaskCallback instanceAndContextConsumer(final ThrowingBiConsumer<? super T, Context> consumer) {
        final ThrowingBiConsumer<Object, Context> erased = (ThrowingBiConsumer<Object, Context>) consumer;
        return context -> erased.accept(context.primaryInstance(), context);
    }

    static TaskCallback contextConsumer(final ThrowingConsumer<Context> consumer) {
        return consumer::accept;
    }
}
