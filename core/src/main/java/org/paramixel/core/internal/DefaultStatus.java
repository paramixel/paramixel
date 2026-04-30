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

import java.util.Optional;
import org.paramixel.core.Status;

/**
 * The default implementation of {@link Status}.
 */
public final class DefaultStatus implements Status {

    private enum Kind {
        STAGED,
        PASS,
        FAILURE,
        SKIP
    }

    private static final DefaultStatus STAGED = new DefaultStatus(Kind.STAGED, null, null);
    private static final DefaultStatus PASS = new DefaultStatus(Kind.PASS, null, null);
    private static final DefaultStatus SKIP = new DefaultStatus(Kind.SKIP, null, null);

    private final Kind kind;
    private final String message;
    private final Throwable throwable;

    private DefaultStatus(Kind kind, String message, Throwable throwable) {
        this.kind = kind;
        this.message = message;
        this.throwable = throwable;
    }

    public static DefaultStatus staged() {
        return STAGED;
    }

    public static DefaultStatus pass() {
        return PASS;
    }

    public static DefaultStatus failure(Throwable t) {
        return new DefaultStatus(Kind.FAILURE, null, t);
    }

    public static DefaultStatus failure(String message) {
        return new DefaultStatus(Kind.FAILURE, message, null);
    }

    public static DefaultStatus failure(Throwable t, String message) {
        return new DefaultStatus(Kind.FAILURE, message, t);
    }

    public static DefaultStatus failure() {
        return new DefaultStatus(Kind.FAILURE, null, null);
    }

    public static DefaultStatus skip() {
        return SKIP;
    }

    public static DefaultStatus skip(String reason) {
        return new DefaultStatus(Kind.SKIP, reason, null);
    }

    @Override
    public boolean isStaged() {
        return kind == Kind.STAGED;
    }

    @Override
    public boolean isPass() {
        return kind == Kind.PASS;
    }

    @Override
    public boolean isFailure() {
        return kind == Kind.FAILURE;
    }

    @Override
    public boolean isSkip() {
        return kind == Kind.SKIP;
    }

    @Override
    public String getDisplayName() {
        return switch (kind) {
            case STAGED -> "STAGED";
            case PASS -> "PASS";
            case FAILURE -> "FAIL";
            case SKIP -> "SKIP";
        };
    }

    @Override
    public Optional<String> getMessage() {
        if (message != null) {
            return Optional.of(message);
        }
        if (throwable != null) {
            return Optional.ofNullable(throwable.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Throwable> getThrowable() {
        return Optional.ofNullable(throwable);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
