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

package org.paramixel.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.paramixel.core.Action;
import org.paramixel.core.Resolver;

public final class DiscoveryService {

    private DiscoveryService() {}

    public static DiscoveryNode scan() {
        return scan(Thread.currentThread().getContextClassLoader(), packageName -> true);
    }

    public static DiscoveryNode scan(Predicate<String> packagePredicate) {
        return scan(Thread.currentThread().getContextClassLoader(), packagePredicate);
    }

    public static DiscoveryNode scan(ClassLoader classLoader, Predicate<String> packagePredicate) {
        DiscoveryNode rootNode = new DiscoveryNode("plan", DiscoveryNode.Kind.ROOT, "Paramixel", "plan");

        Map<String, DiscoveryNode> packageNodes = new TreeMap<>();
        Map<String, DiscoveryNode> classNodes = new HashMap<>();
        Map<String, DiscoveryNode> actionNodes = new HashMap<>();

        try (io.github.classgraph.ScanResult scanResult = new io.github.classgraph.ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .overrideClassLoaders(classLoader)
                .scan()) {

            for (Class<?> clazz : scanResult
                    .getClassesWithMethodAnnotation(org.paramixel.core.Paramixel.ActionFactory.class.getName())
                    .loadClasses()) {

                if (!packagePredicate.test(clazz.getPackageName())) {
                    continue;
                }

                Optional<Action> optionalAction = Resolver.resolveActionsFromClass(clazz);
                if (optionalAction.isEmpty()) {
                    continue;
                }

                Action rootAction = optionalAction.get();
                createActionNodes(rootAction, clazz, rootNode, packageNodes, classNodes, actionNodes, new int[] {0});
            }

        } catch (Exception e) {
            System.err.println("Failed to scan classpath: " + e.getMessage());
        }

        return rootNode;
    }

    private static void createActionNodes(
            Action action,
            Class<?> sourceClass,
            DiscoveryNode rootNode,
            Map<String, DiscoveryNode> packageNodes,
            Map<String, DiscoveryNode> classNodes,
            Map<String, DiscoveryNode> actionNodes,
            int[] counter) {

        String packagePath = sourceClass != null ? sourceClass.getPackageName() : "default";

        DiscoveryNode packageNode = packageNodes.computeIfAbsent(packagePath, k -> {
            DiscoveryNode node =
                    new DiscoveryNode("pkg-" + counter[0]++, DiscoveryNode.Kind.PACKAGE, packagePath, packagePath);
            node.setParent(rootNode);
            return node;
        });

        String classSimpleName = sourceClass != null ? sourceClass.getSimpleName() : "UnknownClass";
        String qualifiedClassName = sourceClass != null ? sourceClass.getName() : "unknown.UnknownClass";

        DiscoveryNode classNode = classNodes.computeIfAbsent(qualifiedClassName, k -> {
            DiscoveryNode node = new DiscoveryNode(
                    "cls-" + counter[0]++,
                    DiscoveryNode.Kind.CLASS,
                    classSimpleName,
                    qualifiedClassName,
                    sourceClass,
                    null);
            node.setParent(packageNode);
            return node;
        });

        createActionNode(action, classNode, actionNodes, counter);
    }

    private static void createActionNode(
            Action action, DiscoveryNode classNode, Map<String, DiscoveryNode> actionNodes, int[] counter) {

        DiscoveryNode existing = actionNodes.get(action.id());
        if (existing != null) {
            return;
        }

        DiscoveryNode node = new DiscoveryNode(
                "act-" + counter[0]++,
                DiscoveryNode.Kind.ACTION,
                action.name(),
                action.name() + " (" + action.id() + ")",
                null,
                action);
        node.setParent(classNode);
        actionNodes.put(action.id(), node);

        for (Action child : action.children()) {
            createActionNode(child, classNode, actionNodes, counter);
        }
    }
}
