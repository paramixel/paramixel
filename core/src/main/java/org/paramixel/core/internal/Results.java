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

package org.paramixel.core.internal;

import java.time.Duration;
import org.paramixel.core.Result;
import org.paramixel.core.Status;

/**
 * Internal result factory helpers.
 */
public final class Results {

    private Results() {}

    public static Result of(Status status, Duration timing) {
        return DefaultResult.of(status, timing);
    }

    public static Result staged() {
        return DefaultResult.staged();
    }

    public static Result pass(Duration timing) {
        return DefaultResult.pass(timing);
    }

    public static Result fail(Duration timing, Throwable failure) {
        return DefaultResult.fail(timing, failure);
    }

    public static Result fail(Duration timing, String failureMessage) {
        return DefaultResult.fail(timing, failureMessage);
    }

    public static Result skip(Duration timing) {
        return DefaultResult.skip(timing);
    }

    public static Result skip(Duration timing, String reason) {
        return DefaultResult.skip(timing, reason);
    }
}
