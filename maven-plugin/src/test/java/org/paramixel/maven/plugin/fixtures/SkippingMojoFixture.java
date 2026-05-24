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
import org.paramixel.api.exception.SkipException;

/**
 * Fixture whose action throws a {@link SkipException} on execution, used to verify that the Maven
 * mojo handles skip semantics according to the {@code failureOnSkip} configuration.
 */
public final class SkippingMojoFixture {

    private SkippingMojoFixture() {}

    /**
     * Creates an instance action that always throws a {@link SkipException} on execution.
     *
     * @return an {@link Instance} action that signals a skip
     */
    @Paramixel.Factory
    public static Action action() {
        return Instance.<Object>of("mojo-skip", Object::new)
                .child("skip", obj -> {
                    throw new SkipException("skip requested");
                })
                .resolve();
    }
}
