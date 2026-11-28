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
import org.paramixel.api.selector.Selector;

/**
 * Namespace class holding core annotations consumed by Paramixel discovery and execution; not
 * instantiable.
 *
 * <p>This class groups the annotations used by the framework for factory discovery, tagging, ordering, and disabling.
 * The discovered definition model is the immutable {@link Task}; every discovered method returns a {@link Task} or a
 * {@link TaskBuilder}.
 */
public final class Paramixel {

    private Paramixel() {
        // Intentionally empty
    }

    /**
     * Marks a method as a task factory discovered by classpath scanning.
     *
     * <p>Task factory methods must be {@code public static} and take no parameters or exactly one
     * {@link Configuration} parameter, returning a {@link Task} or a {@link TaskBuilder}. A returned builder is built by
     * the framework.
     *
     * <p>Declaring the {@link Configuration} parameter hands the factory the run's effective configuration, so a factory
     * can decide the <em>shape</em> of its definition — which environments to author, which branches to include — rather
     * than only what its tasks do at execution time. The injected configuration is the same object
     * {@link Context#configuration()} exposes and has already been validated. The parameter is optional: a no-parameter
     * factory is unchanged, and the return type is independent of whether the parameter is declared.
     *
     * <p>Returning {@code null} is an explicit skip with a recorded reason: the factory contributes no definition and
     * discovery continues. That makes a configuration-driven factory able to opt out of contributing a definition at
     * all.
     *
     * <p>Discovery validates every matched factory before any test work runs, so an invalid shape fails the run with
     * {@link org.paramixel.api.exception.ResolverException} rather than executing a partial suite.
     *
     * @see Configuration
     * @see Runner
     * @see Task
     * @see TaskBuilder
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface TaskFactory {}

    /**
     * Binds a stable identifier to a hook method.
     *
     * <p>The annotated method must be {@code public static}. Its signature depends on the builder method:
     * <ul>
     *   <li>{@code before}, {@code step}, {@code body}, and {@code after}: {@code void m(Context)}, with checked
     *       exceptions allowed.</li>
     *   <li>{@code onlyIf}: {@code boolean m(Context)} or {@code Boolean m(Context)}.</li>
     *   <li>{@code until} on {@link RepeatBuilder}: {@code boolean m(Context, Status)} or
     *       {@code Boolean m(Context, Status)}.</li>
     * </ul>
     * Predicate methods may only declare unchecked exceptions. A boxed {@code null} predicate result is treated as
     * {@code false}.
     *
     * <p>For an anchor in a named module, make a public anchor accessible by exporting its package to
     * {@code org.paramixel.api}, or open the package to that module. A non-public anchor requires an open package.
     * Inaccessible methods are reported when the definition is built.
     *
     * <p>The identifier resolves only against methods declared by the definition's <strong>anchor class</strong> —
     * the class named once at the definition root, as in
     * {@code Sequential.builder(MyTest.class, "MyTest")}. Inheritance is not consulted, and an identifier that is
     * declared in a class that is not the anchor of a definition is never resolved.
     *
     * <p>Identifiers must be nonblank, at most 64 UTF-16 code units, and unique within the anchor class.
     * The identifier is also the label reported for the hook ({@code before[setUp]}, {@code body[work]}).
     * {@link Selector} filters discovered factories by class, package, or factory tags.
     *
     * @see Sequential#builder(Class, String)
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Id {

        /**
         * Returns the stable identifier of the hook.
         *
         * @return the identifier; must be nonblank and at most 64 UTF-16 code units
         */
        String value();
    }

    /**
     * Marks a method that executes once before any discovered test tasks
     * within a runner invocation.
     *
     * <p>The method must be {@code public static}, take zero parameters, and return a {@link Task} or
     * {@link TaskBuilder}. Multiple {@code @BeforeAll} methods are ordered by {@link Priority @Priority} on the
     * declaring class, then by package, class, and method name.
     *
     * <p>A failing or skipped {@code @BeforeAll} method causes the discovered factory group to be skipped. Any
     * {@code @AfterAll} method still executes.
     *
     * @see AfterAll
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BeforeAll {}

    /**
     * Marks a method that executes once after all discovered test tasks
     * within a runner invocation.
     *
     * <p>The method must be {@code public static}, take zero parameters, and return a {@link Task} or
     * {@link TaskBuilder}. Multiple {@code @AfterAll} methods are ordered in reverse of {@code @BeforeAll} ordering.
     * Every {@code @AfterAll} method executes regardless of whether preceding hooks or discovered factories failed,
     * subject to cooperative quiescence.
     *
     * @see BeforeAll
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AfterAll {}

    /**
     * Declares discovery ordering priority for a discovered factory class.
     *
     * <p>Higher priority classes are ordered earlier when the framework builds the discovered
     * root task. Priority affects scheduling admission order only; concurrent execution does not
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
         * Returns an optional explanation describing why the task is disabled.
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
     * Declares a factory tag for discovery filtering by {@link Selector}. On the discovered root occurrence, the
     * framework appends missing annotation tags after builder tags in both the context and the result.
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
}
