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

/**
 * The step body that closes one {@code Instance} fixture and removes it from its frame.
 *
 * <p>It runs in the scope's after slot, so it runs exactly when cleanup is owed: the scope guarantees that
 * {@code after} runs once its {@code before} was attempted. When creation failed, nothing was bound, and this step
 * is a no-op. When creation succeeded, the fixture is removed and, if it is {@link AutoCloseable}, closed once.
 */
public final class FixtureDestroyer implements ContextConsumer {

    private final Class<?> declaredType;

    /**
     * Creates a fixture destroyer.
     *
     * @param declaredType the type the fixture was declared as; must not be {@code null}
     * @throws NullPointerException if {@code declaredType} is {@code null}
     */
    public FixtureDestroyer(final Class<?> declaredType) {
        this.declaredType = Objects.requireNonNull(declaredType, "declaredType is null");
    }

    @Override
    public void accept(final Context context) throws Throwable {
        var fixture = ConcreteContext.require(context).frame().removeLocal(declaredType);
        if (fixture instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
