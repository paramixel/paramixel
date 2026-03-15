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
 * Simple factory for creating class executors.
 *
 * <p>This factory creates {@link SimpleClassExecutor} instances with default configuration.
 *
 * <p><b>Thread safety</b>
 * <p>This type is stateless and thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class SimpleExecutorFactory implements ExecutorFactory {

    /**
     * Creates a new factory instance.
     */
    public SimpleExecutorFactory() {
        // INTENTIONALLY EMPTY - stateless utility
    }

    @Override
    public SimpleClassExecutor createExecutor(
            final @NonNull ConcreteEngineContext engineContext, final @NonNull EngineExecutionListener listener) {
        return new SimpleClassExecutor(engineContext, listener);
    }
}
