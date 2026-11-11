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

import java.util.Objects;
import org.paramixel.api.Context;
import org.paramixel.api.ContextConsumer;
import org.paramixel.api.InstanceConsumer;

/**
 * The step body of a typed step: it receives the enclosing fixture of the declared type.
 *
 * <p>This type is how the compile pass recognizes a typed step. It carries the declared type, so the compiler can
 * reject a run whose tree does not provide a compatible fixture before any user code runs.
 *
 * @param <T> the fixture type
 */
public final class FixtureConsumer<T> implements ContextConsumer {

    private final Class<T> type;
    private final InstanceConsumer<? super T> consumer;

    /**
     * Creates a typed step body.
     *
     * @param type the declared fixture type; must not be {@code null}
     * @param consumer the consumer that receives the fixture; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public FixtureConsumer(final Class<T> type, final InstanceConsumer<? super T> consumer) {
        this.type = Objects.requireNonNull(type, "type is null");
        this.consumer = Objects.requireNonNull(consumer, "consumer is null");
    }

    /**
     * Returns the declared fixture type.
     *
     * @return the declared type; never {@code null}
     */
    public Class<T> type() {
        return type;
    }

    @Override
    public void accept(final Context context) throws Throwable {
        consumer.accept(context.requireInstance(type));
    }
}
