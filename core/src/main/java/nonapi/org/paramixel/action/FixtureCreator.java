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
import java.util.function.Supplier;
import org.paramixel.api.Context;
import org.paramixel.api.ContextConsumer;

/**
 * The step body that creates, validates and binds one {@code Instance} fixture.
 *
 * <p>This type is how the framework recognizes a fixture scope during compilation: the compile pass inspects the
 * scope's lifecycle slot, finds this consumer, and knows which fixture type the scope's body provides. Users never
 * see it; {@code Instance} creates it.
 *
 * <p>Creation is lazy and node-local: the supplier runs here, on the thread executing the {@code Instance} node,
 * and nowhere else. A node that is skipped, aborted or cancelled before it starts never reaches this step, so its
 * supplier is never called.
 */
public final class FixtureCreator implements ContextConsumer {

    private final Class<?> declaredType;
    private final Supplier<?> factory;

    /**
     * Creates a fixture creator.
     *
     * @param declaredType the type the fixture is declared as; must not be {@code null}
     * @param factory the supplier that creates the fixture; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public FixtureCreator(final Class<?> declaredType, final Supplier<?> factory) {
        this.declaredType = Objects.requireNonNull(declaredType, "declaredType is null");
        this.factory = Objects.requireNonNull(factory, "factory is null");
    }

    /**
     * Returns the declared fixture type.
     *
     * @return the declared type; never {@code null}
     */
    public Class<?> declaredType() {
        return declaredType;
    }

    @Override
    public void accept(final Context context) {
        var fixture = Objects.requireNonNull(factory.get(), "factory returned null");
        ConcreteContext.require(context).frame().bind(declaredType, fixture);
    }
}
