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

import java.util.Objects;
import org.paramixel.api.selector.Selector;

/**
 * Singleton selector that matches all action factories regardless of package, class name, or tag.
 *
 * <p>All three {@code matches*} methods return {@code true}.
 *
 * @see Selector#all()
 */
public final class ConcreteAllSelector implements Selector {

    /**
     * The singleton instance.
     */
    public static final ConcreteAllSelector INSTANCE = new ConcreteAllSelector();

    private ConcreteAllSelector() {
        // Intentionally empty
    }

    @Override
    public boolean matchesPackage(final String packageName) {
        Objects.requireNonNull(packageName, "packageName is null");
        return true;
    }

    @Override
    public boolean matchesClass(final String className) {
        Objects.requireNonNull(className, "className is null");
        return true;
    }

    @Override
    public boolean matchesTag(final String tag) {
        Objects.requireNonNull(tag, "tag is null");
        return true;
    }

    @Override
    public String toString() {
        return "Selector.all()";
    }
}
