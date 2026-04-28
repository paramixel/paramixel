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

package org.paramixel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ResolverException}.
 */
@DisplayName("ResolverException")
class ResolverExceptionTest {

    @Test
    @DisplayName("constructor with message sets message")
    void constructorWithMessage() {
        var message = "test message";
        var exception = new ResolverException(message);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("constructor with null message sets null message")
    void constructorWithNullMessage() {
        var exception = new ResolverException(null);
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("constructor with message and cause sets both")
    void constructorWithMessageAndCause() {
        var message = "test message";
        Throwable cause = new IOException("test cause");
        var exception = new ResolverException(message, cause);
        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("constructor with null message and null cause sets both to null")
    void constructorWithNullMessageAndCause() {
        var exception = new ResolverException(null, null);
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("constructor with message and null cause sets message and null cause")
    void constructorWithMessageAndNullCause() {
        var message = "test message";
        var exception = new ResolverException(message, null);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("constructor with null message and cause sets null message and cause")
    void constructorWithNullMessageAndNonNullCause() {
        var cause = new IOException("test cause");
        var exception = new ResolverException(null, cause);
        assertNull(exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
