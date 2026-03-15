/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.maven.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.paramixel.maven.plugin.ParamixelMojo.Property;

public class ParamixelMojoSkipTest {

    @Test
    public void execute_skipsWhenSkipTestsTrue() {
        final ParamixelMojo mojo = new ParamixelMojo();

        assertDoesNotThrow(() -> {
            setField(mojo, "skipTests", true);
            mojo.execute();
        });
    }

    @Test
    public void execute_failsFastForInvalidSummaryClassNameMaxLength() throws Exception {
        final ParamixelMojo mojo = new ParamixelMojo();

        Property prop = new Property();
        prop.setKey("paramixel.summary.classNameMaxLength");
        prop.setValue("0");
        setField(mojo, "properties", List.of(prop));

        final MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);
        assertEquals(
                "Invalid configuration: paramixel.summary.classNameMaxLength: must be an integer in range [1, 2147483647] (source=maven-plugin raw='0' normalized='0')",
                ex.getMessage());
    }

    private static void setField(final Object target, final String fieldName, final Object value)
            throws NoSuchFieldException, IllegalAccessException {
        final var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
