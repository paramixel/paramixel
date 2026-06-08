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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Builder;
import org.paramixel.api.selector.Selector;

/**
 * Namespace class holding core annotations consumed by Paramixel discovery and execution; not
 * instantiable.
 *
 * <p>This class groups the annotations used by the framework for action discovery, tagging,
 * ordering, and disabling.
 */
public final class Paramixel {

    private Paramixel() {
        // Intentionally empty
    }

    /**
     * Marks a method as a factory discovered by classpath scanning.
     *
     * <p>Factory methods must produce {@link Action} or {@link Builder} instances, or
     * {@code null} to indicate that the factory should be skipped. When a {@link Builder}
     * is returned, it is built immediately to produce an {@link Action}. The framework
     * invokes these methods during discovery to build the root action tree.
     *
     * @see Runner
     * @see Action
     * @see Builder
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Factory {}

    /**
     * Marks a method that executes once before any discovered test actions
     * within a runner invocation.
     *
     * <p>The method must be {@code public static}, take zero parameters, and
     * return {@link Action} or {@link Builder}. Multiple {@code @BeforeAll}
     * methods are ordered by {@link Priority @Priority} on the declaring
     * class, then by package, class, and method name.
     *
     * <p>A failing or skipped {@code @BeforeAll} method causes the body of
     * the run (all discovered factory actions) to be skipped. Any
     * {@code @AfterAll} methods still execute.
     *
     * @see AfterAll
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BeforeAll {}

    /**
     * Marks a method that executes once after all discovered test actions
     * within a runner invocation.
     *
     * <p>The method must be {@code public static}, take zero parameters, and
     * return {@link Action} or {@link Builder}. Multiple {@code @AfterAll}
     * methods are ordered in reverse of {@code @BeforeAll} ordering. Every
     * {@code @AfterAll} method executes regardless of whether preceding
     * hooks or test actions failed.
     *
     * @see BeforeAll
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AfterAll {}

    /**
     * Declares discovery ordering priority for a discovered action factory class.
     *
     * <p>Higher priority classes are ordered earlier when the framework builds the discovered
     * root action. Priority affects scheduling admission order only; concurrent execution does not
     * imply completion order.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Priority {

        /**
         * Returns the discovery priority value. The neutral default is {@code 0}.
         *
         * @return the discovery priority
         */
        int value() default 0;
    }

    /**
     * Marks a factory method as disabled, excluding it from discovery entirely.
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
     * Container annotation for repeated {@link Tag} declarations on a factory method.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tags {

        /**
         * Returns the tags declared for the annotated factory method.
         *
         * @return the declared tags
         */
        Tag[] value();
    }

    /**
     * Declares a logical tag for a factory method, consumed by {@link Selector} for
     * tag-based discovery filtering.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Tags.class)
    public @interface Tag {

        /**
         * Returns the tag value declared for the annotated factory method.
         *
         * @return the tag value
         */
        String value();
    }

    /**
     * Assigns a stable identifier to a method that may be registered as an action.
     *
     * <p>The identifier is used by {@link AnnotationResolver#byId(String)} and
     * {@link AnnotationResolver#staticById(String)} to resolve annotated methods into named
     * step actions. The annotated method must satisfy the same invocation signature rules as
     * the corresponding method-reference API: it must be a public method that accepts no
     * arguments and returns {@code void}. Instance methods are resolved by
     * {@link AnnotationResolver#byId(String)}; static methods are resolved by
     * {@link AnnotationResolver#staticById(String)}.
     *
     * <p>IDs are resolved against the concrete type supplied to
     * {@link AnnotationResolver#create(Class)} and must be unique across the class hierarchy
     * visible through that type. Instance and static methods are resolved independently; the
     * same ID may appear on both a static and an instance method within the same class.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Id {

        /**
         * Returns the stable method identifier used for action lookup.
         *
         * @return the method identifier
         */
        String value();
    }
}
