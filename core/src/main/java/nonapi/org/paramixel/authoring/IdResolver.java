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

package nonapi.org.paramixel.authoring;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Status;
import org.paramixel.api.TaskCallback;
import org.paramixel.api.exception.ParamixelException;

/**
 * Resolves hook identifiers declared with {@link Paramixel.Id} against a definition's anchor class.
 *
 * <p>Resolution happens while a definition is authored, never during execution: every problem is appended to the
 * caller's diagnostic list so one build reports every bad identifier at once. Nothing here runs user code except the
 * returned callbacks.
 *
 * <p>Only methods <em>declared</em> by the anchor class are considered. Inheritance is deliberately not consulted, so a
 * base-class hook can neither be wired into a subclass nor break one silently.
 */
public final class IdResolver {

    private static final Map<Class<?>, Map<String, List<Method>>> CACHE = new ConcurrentHashMap<>();

    private static final int MAX_CANDIDATES = 5;

    private IdResolver() {
        // Intentionally empty
    }

    /**
     * Resolves a lifecycle or body hook.
     *
     * @param id the identifier; must not be {@code null}
     * @param anchor the anchor class, or {@code null} when the definition names none
     * @param role the role the hook is used in
     * @param problems the diagnostic list to append to
     * @return the callback, or {@code null} when resolution failed and a problem was recorded
     */
    public static TaskCallback resolveHook(
            final String id, final Class<?> anchor, final IdRole role, final List<String> problems) {
        final var method = resolve(id, anchor, role, problems);
        if (method == null) {
            return null;
        }
        return new Hook(id, method);
    }

    /**
     * Resolves a runtime condition hook.
     *
     * @param id the identifier; must not be {@code null}
     * @param anchor the anchor class, or {@code null}
     * @param problems the diagnostic list to append to
     * @return the predicate, or {@code null} when resolution failed
     */
    public static Predicate<Context> resolveCondition(
            final String id, final Class<?> anchor, final List<String> problems) {
        final var method = resolve(id, anchor, IdRole.CONDITION, problems);
        if (method == null) {
            return null;
        }
        return context -> booleanValue(method, context);
    }

    /**
     * Resolves a repeat predicate hook.
     *
     * @param id the identifier; must not be {@code null}
     * @param anchor the anchor class, or {@code null}
     * @param problems the diagnostic list to append to
     * @return the predicate, or {@code null} when resolution failed
     */
    public static BiPredicate<Context, Status> resolveUntil(
            final String id, final Class<?> anchor, final List<String> problems) {
        final var method = resolve(id, anchor, IdRole.UNTIL, problems);
        if (method == null) {
            return null;
        }
        return (context, status) -> booleanValue(method, context, status);
    }

    /**
     * Returns the identifiers declared by a class, mapped to their methods.
     *
     * <p>Exposed for diagnostics and tests; the returned map is the cached index and must not be modified.
     *
     * @param anchor the anchor class; must not be {@code null}
     * @return the identifiers declared by the class, in declaration order
     */
    public static Map<String, List<Method>> declaredIds(final Class<?> anchor) {
        return CACHE.computeIfAbsent(anchor, IdResolver::index);
    }

    private static Method resolve(
            final String id, final Class<?> anchor, final IdRole role, final List<String> problems) {
        if (anchor == null) {
            problems.add("Id \"" + id + "\" used by " + role.label()
                    + "(...) in a definition with no anchor; root the definition at"
                    + " Tasks.sequential(Owner.class, \"name\") so ids can be resolved");
            return null;
        }
        final var index = declaredIds(anchor);
        final var methods = index.get(id);
        if (methods == null || methods.isEmpty()) {
            problems.add("Unknown id \"" + id + "\" used by " + role.label() + "(...) in " + anchor.getName()
                    + candidates(id, index));
            return null;
        }
        if (methods.size() > 1) {
            problems.add("Id \"" + id + "\" is declared " + methods.size() + " times in " + anchor.getName() + ": "
                    + methods.stream().map(IdResolver::signature).collect(Collectors.joining(", "))
                    + "; ids must be unique within the anchor class");
            return null;
        }
        final var method = methods.get(0);
        final var mismatch = mismatch(method, role);
        if (mismatch != null) {
            problems.add("Id \"" + id + "\" resolves to " + signature(method) + " in " + anchor.getName()
                    + "; expected " + mismatch);
            return null;
        }
        return method;
    }

    private static Map<String, List<Method>> index(final Class<?> anchor) {
        final var map = new LinkedHashMap<String, List<Method>>();
        for (final Method method : anchor.getDeclaredMethods()) {
            final var id = method.getAnnotation(Paramixel.Id.class);
            if (id == null || id.value().isBlank()) {
                continue;
            }
            map.computeIfAbsent(id.value(), key -> new ArrayList<>()).add(method);
        }
        return map;
    }

    private static String mismatch(final Method method, final IdRole role) {
        final var modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            return expected(role) + " and must be public static";
        }
        final var parameters = method.getParameterTypes();
        if (role == IdRole.UNTIL) {
            if (parameters.length != 2 || parameters[0] != Context.class || parameters[1] != Status.class) {
                return expected(role);
            }
        } else if (parameters.length != 1 || parameters[0] != Context.class) {
            return expected(role);
        }
        if (role.isPredicate()) {
            if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
                return expected(role);
            }
            for (final Class<?> declared : method.getExceptionTypes()) {
                if (!RuntimeException.class.isAssignableFrom(declared) && !Error.class.isAssignableFrom(declared)) {
                    return expected(role) + " and must not declare checked exceptions";
                }
            }
        } else if (method.getReturnType() != void.class) {
            return expected(role);
        }
        return null;
    }

    private static String expected(final IdRole role) {
        return switch (role) {
            case BEFORE, STEP, BODY, AFTER -> "void m(Context)";
            case CONDITION -> "boolean m(Context)";
            case UNTIL -> "boolean m(Context, Status)";
        };
    }

    private static String signature(final Method method) {
        final var parameters = new StringBuilder();
        for (final Class<?> parameter : method.getParameterTypes()) {
            if (!parameters.isEmpty()) {
                parameters.append(", ");
            }
            parameters.append(parameter.getSimpleName());
        }
        return method.getReturnType().getSimpleName() + " " + method.getName() + "(" + parameters + ")";
    }

    private static String candidates(final String id, final Map<String, List<Method>> index) {
        final var limit = Math.max(2, id.length() / 3);
        final var suggestions = index.keySet().stream()
                .filter(candidate -> distance(id, candidate) <= limit)
                .sorted()
                .limit(MAX_CANDIDATES)
                .toList();
        if (suggestions.isEmpty()) {
            return index.isEmpty() ? "; the class declares no @Paramixel.Id methods" : "";
        }
        return "; candidates: " + String.join(", ", suggestions);
    }

    /**
     * Levenshtein distance, used only to suggest near misses.
     */
    private static int distance(final String left, final String right) {
        final var previous = new int[right.length() + 1];
        final var current = new int[right.length() + 1];
        for (var j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (var i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (var j = 1; j <= right.length(); j++) {
                final var cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            System.arraycopy(current, 0, previous, 0, current.length);
        }
        return previous[right.length()];
    }

    /**
     * A resolved hook: an executable callback that remembers the identifier it came from, so the engine can label the
     * phase and any failure with {@code before[setUp]} instead of {@code before[0]}.
     */
    private static final class Hook implements TaskCallback, IdentifiedHook {

        private final String id;
        private final Method method;

        Hook(final String id, final Method method) {
            this.id = id;
            this.method = method;
        }

        @Override
        public void execute(final Context context) throws Throwable {
            HookInvoker.call(method, context);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String toString() {
            return "hook[" + id + "]";
        }
    }

    private static boolean booleanValue(final Method method, final Object... arguments) {
        try {
            return Boolean.TRUE.equals(HookInvoker.call(method, arguments));
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            // Unreachable: predicates are validated to declare no checked exceptions. Kept so a future change cannot
            // silently swallow a hook failure.
            throw new ParamixelException("Predicate hook " + method + " threw " + t, t);
        }
    }
}
