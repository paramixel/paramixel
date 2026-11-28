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

package nonapi.org.paramixel.compiler;

import java.util.Objects;

/**
 * One validation finding produced by the plan compiler.
 *
 * <p>Diagnostics are collected rather than thrown one at a time, so a single definition review can list every
 * independent problem. An error prevents execution; a warning does not.
 */
public final class Diagnostic {

    /**
     * Diagnostic severity.
     */
    public enum Severity {

        /** The plan is invalid and must not execute. */
        ERROR,

        /** The plan may execute, but the author should review the finding. */
        WARNING
    }

    private final Severity severity;
    private final String code;
    private final String message;
    private final String path;
    private final String hint;

    private Diagnostic(
            final Severity severity, final String code, final String message, final String path, final String hint) {
        this.severity = Objects.requireNonNull(severity, "severity is null");
        this.code = Objects.requireNonNull(code, "code is null");
        this.message = Objects.requireNonNull(message, "message is null");
        this.path = Objects.requireNonNull(path, "path is null");
        this.hint = hint;
    }

    /**
     * Creates an error.
     *
     * @param code the stable rule identifier; must not be {@code null}
     * @param path the task path; must not be {@code null}
     * @param message the description; must not be {@code null}
     * @param hint a concrete repair hint, or {@code null}
     * @return the diagnostic; never {@code null}
     */
    public static Diagnostic error(final String code, final String path, final String message, final String hint) {
        return new Diagnostic(Severity.ERROR, code, message, path, hint);
    }

    /**
     * Creates a warning.
     *
     * @param code the stable rule identifier; must not be {@code null}
     * @param path the task path; must not be {@code null}
     * @param message the description; must not be {@code null}
     * @param hint a concrete review hint, or {@code null}
     * @return the diagnostic; never {@code null}
     */
    public static Diagnostic warning(final String code, final String path, final String message, final String hint) {
        return new Diagnostic(Severity.WARNING, code, message, path, hint);
    }

    /**
     * Returns the severity.
     *
     * @return the severity; never {@code null}
     */
    public Severity severity() {
        return severity;
    }

    /**
     * Returns the stable rule identifier.
     *
     * @return the code; never {@code null}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the description.
     *
     * @return the message; never {@code null}
     */
    public String message() {
        return message;
    }

    /**
     * Returns the task path.
     *
     * @return the path; never {@code null}
     */
    public String path() {
        return path;
    }

    /**
     * Returns the repair hint.
     *
     * @return the hint, or {@code null}
     */
    public String hint() {
        return hint;
    }

    @Override
    public String toString() {
        return severity + " [" + code + "] " + path + ": " + message + (hint == null ? "" : " — " + hint);
    }
}
