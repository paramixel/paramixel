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

package examples.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;

public class ArgumentNestedParallelismTest {

    private static final List<String> INVOCATIONS = new CopyOnWriteArrayList<>();
    private static final int OUTER_PARALLEL_COUNT = 4;
    private static final int INNER_PARALLEL_COUNT = 3;

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        INVOCATIONS.clear();

        Action outerParallel = outerParallel();
        Action verify = verify();

        var suiteBuilder = Container.builder("ArgumentNestedParallelismTest");
        suiteBuilder.child(outerParallel);
        suiteBuilder.child(verify);
        return suiteBuilder.build();
    }

    private static Action outerParallel() {
        var outerBuilder = Parallel.builder("outer");
        for (int i = 0; i < OUTER_PARALLEL_COUNT; i++) {
            Action innerParallel = innerParallel(i);
            outerBuilder.child(innerParallel);
        }
        return outerBuilder.build();
    }

    private static Action innerParallel(int outerIndex) {
        var innerBuilder = Parallel.builder("inner " + outerIndex);
        for (int j = 0; j < INNER_PARALLEL_COUNT; j++) {
            Action leaf = leaf(outerIndex, j);
            innerBuilder.child(leaf);
        }
        return innerBuilder.build();
    }

    private static Action leaf(int outerIndex, int innerIndex) {
        return Direct.builder("leaf " + outerIndex + "-" + innerIndex)
                .runnable(context -> INVOCATIONS.add("leaf " + outerIndex + "-" + innerIndex))
                .build();
    }

    private static Action verify() {
        return Direct.builder("verify")
                .runnable(context -> assertThat(INVOCATIONS).hasSize(OUTER_PARALLEL_COUNT * INNER_PARALLEL_COUNT))
                .build();
    }
}
