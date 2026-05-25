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

package org.paramixel.maven.plugin.fixtures;

import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.exception.FailException;

/**
 * Fixture whose action throws a {@link FailException} on execution, used to verify that the
 * Maven mojo translates action failures into {@link org.apache.maven.plugin.MojoFailureException}.
 */
public final class FailingMojoFixture {

    private FailingMojoFixture() {}

    /**
     * Creates an instance action that always throws {@code "boom"} on execution.
     *
     * @return an {@link Instance} action that fails with a runtime exception
     */
    @Paramixel.Factory
    public static Action action() {
        return Instance.<Object>of("mojo-fail", Object::new)
                .child("fail", obj -> {
                    throw new RuntimeException("boom");
                })
                .resolve();
    }
}
