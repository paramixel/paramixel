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

package org.paramixel.core.spi.listener;

import org.paramixel.core.support.AnsiColor;

/**
 * Shared console output constants for built-in listener implementations.
 */
public class Constants {

    /**
     * Colored prefix used for built-in Paramixel console output lines.
     */
    public static final String PARAMIXEL = "[" + AnsiColor.BOLD_BLUE_TEXT.format("PARAMIXEL") + "] ";

    /**
     * Plain-text prefix used for non-ANSI Paramixel output lines.
     */
    public static final String PARAMIXEL_PLAIN = "[PARAMIXEL] ";

    private Constants() {}
}
