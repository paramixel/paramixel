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

package org.paramixel.core.action.internal;

import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;

/**
 * Stub action in a subpackage of {@code org.paramixel.core.action} used to verify that
 * {@code startsWith} package matching includes subpackages.
 */
public final class SubpackageAction implements Action {

    private final String id;
    private final String name;

    public SubpackageAction(String name) {
        this.id = "subpkg-" + name;
        this.name = name;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ContextMode getContextMode() {
        return ContextMode.ISOLATED;
    }

    @Override
    public Result execute(Context context) {
        var result = new DefaultResult(this);
        result.complete(DefaultStatus.PASS, java.time.Duration.ZERO);
        return result;
    }

    @Override
    public Result skip(Context context) {
        var result = new DefaultResult(this);
        result.complete(DefaultStatus.SKIP, java.time.Duration.ZERO);
        return result;
    }
}
