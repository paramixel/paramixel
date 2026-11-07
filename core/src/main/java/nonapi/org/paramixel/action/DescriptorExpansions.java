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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.action.Until;

@SuppressWarnings({"deprecation", "removal"})
final class DescriptorExpansions {

    private static final Map<Class<? extends Action>, DescriptorExpansion<? extends Action>> EXPANSIONS = Map.ofEntries(
            expansion(Scope.class, DescriptorExpansions::expandScope),
            expansion(Static.class, DescriptorExpansions::expandStatic),
            expansion(Instance.class, DescriptorExpansions::expandInstance),
            expansion(Parallel.class, (parallel, context) -> addChildren(parallel.children(), context)),
            expansion(Sequence.class, (sequence, context) -> addChildren(sequence.children(), context)),
            expansion(Sequential.class, (sequential, context) -> addChildren(sequential.children(), context)),
            expansion(Loop.class, (loop, context) -> addIterations(loop.body(), loop.maxIterations(), context)),
            expansion(Repeat.class, (repeat, context) -> addIterations(repeat.body(), repeat.iterations(), context)),
            expansion(Until.class, (until, context) -> addIterations(until.body(), until.maxIterations(), context)),
            expansion(Timeout.class, (timeout, context) -> context.addChild(timeout.body())),
            expansion(Isolated.class, (isolated, context) -> context.addChild(isolated.body())),
            expansion(Conditional.class, (conditional, context) -> context.addChild(conditional.body())));

    /**
     * Actions that are supported but expand to nothing: they own no before, body, or after slots.
     */
    private static final Set<Class<? extends Action>> TERMINAL_ACTIONS = Set.of(Step.class, Assert.class, Delay.class);

    private DescriptorExpansions() {}

    static void expand(final Action action, final DescriptorExpansionContext context) {
        Objects.requireNonNull(action, "action is null");
        Objects.requireNonNull(context, "context is null");
        var expansion = EXPANSIONS.get(action.getClass());
        if (expansion != null) {
            expand(expansion, action, context);
        }
    }

    static boolean supports(final Class<? extends Action> type) {
        return EXPANSIONS.containsKey(Objects.requireNonNull(type, "type is null")) || TERMINAL_ACTIONS.contains(type);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Action> void expand(
            final DescriptorExpansion<T> expansion, final Action action, final DescriptorExpansionContext context) {
        expansion.expand((T) action, context);
    }

    private static <T extends Action>
            Map.Entry<Class<? extends Action>, DescriptorExpansion<? extends Action>> expansion(
                    final Class<T> type, final DescriptorExpansion<T> expansion) {
        return Map.entry(type, expansion);
    }

    private static void expandScope(final Scope scope, final DescriptorExpansionContext context) {
        scope.before().ifPresent(context::setBefore);
        if (scope.body() != null) {
            context.addChild(scope.body());
        }
        scope.after().ifPresent(context::setAfter);
    }

    private static void expandStatic(final Static staticAction, final DescriptorExpansionContext context) {
        staticAction.before().ifPresent(context::setBefore);
        if (staticAction.body() != null) {
            context.addChild(staticAction.body());
        }
        staticAction.after().ifPresent(context::setAfter);
    }

    private static void expandInstance(final Instance instance, final DescriptorExpansionContext context) {
        context.setBefore(instance.instantiate());
        if (instance.body() != null) {
            context.addChild(instance.body());
        }
        context.setAfter(instance.destroy());
    }

    private static void addChildren(final List<Action> children, final DescriptorExpansionContext context) {
        children.forEach(context::addChild);
    }

    private static void addIterations(
            final Action body, final int iterations, final DescriptorExpansionContext context) {
        for (var i = 0; i < iterations; i++) {
            context.addChild(body);
        }
    }
}
