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

package org.paramixel.engine.execution;

import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineExecutionListener;
import org.paramixel.engine.api.ConcreteEngineContext;

/**
 * Factory for creating class executors.
 *
 * <p>This interface abstracts the creation of {@link SimpleClassExecutor} instances,
 * allowing for dependency injection and easier testing.
 *
 * <p><b>Thread safety</b>
 * <p>Implementations should be thread-safe or confined to single-threaded use.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public interface ExecutorFactory {

    /**
     * Creates a new class executor instance.
     *
     * @param engineContext the engine context; never {@code null}
     * @param listener the execution listener; never {@code null}
     * @return a new class executor instance; never {@code null}
     */
    SimpleClassExecutor createExecutor(
            final @NonNull ConcreteEngineContext engineContext, final @NonNull EngineExecutionListener listener);
}
