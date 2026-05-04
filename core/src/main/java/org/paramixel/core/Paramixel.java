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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines core annotations used by Paramixel discovery and execution.
 *
 * <p>This class is a namespace for annotations consumed by the framework.
 */
public final class Paramixel {

    private Paramixel() {}

    /**
     * Marks a method as an action factory.
     *
     * <p>Action factory methods are expected to produce {@link Action} instances that can be discovered and executed by
     * Paramixel.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ActionFactory {}

    /**
     * Marks an action factory method as disabled.
     *
     * <p>Disabled actions remain discoverable for reporting purposes but are not executed.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Disabled {

        /**
         * Returns an optional explanation describing why the action is disabled.
         *
         * @return the disable reason, or an empty string when no reason is supplied
         */
        String value() default "";
    }

    /**
     * Container annotation for repeated {@link Tag} declarations on an action factory method.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tags {

        /**
         * Returns the tags declared for the annotated action factory method.
         *
         * @return the declared tags
         */
        Tag[] value();
    }

    /**
     * Declares a logical tag for an action factory method.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Tags.class)
    public @interface Tag {

        /**
         * Returns the tag value declared for the annotated action factory method.
         *
         * @return the tag value
         */
        String value();
    }
}
