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

package org.paramixel.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines annotations used to discover Paramixel actions.
 */
public class Paramixel {

    private Paramixel() {
        // Intentionally empty
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Marks a public static no-argument method as an action factory.
     *
     * <p>Annotated methods must return an {@link Action}.
     */
    public @interface ActionFactory {
        // Intentionally empty
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Excludes an action factory method from discovery.
     */
    public @interface Disabled {

        /**
         * Returns the optional reason this factory is disabled.
         *
         * @return The disabled reason, or an empty string when unspecified.
         */
        String value() default "";
    }
}
