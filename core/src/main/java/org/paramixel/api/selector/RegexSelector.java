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

package org.paramixel.api.selector;

import java.util.regex.Pattern;

/**
 * A {@link Selector} that matches discovery candidates using a compiled regular-expression {@link Pattern}.
 *
 * <p>Regex selectors use {@link Pattern#matcher(CharSequence)} {@code .find()} semantics. For exact matches,
 * callers should provide anchored regular expressions such as {@code ^smoke$}.
 *
 * @see PackageRegexSelector
 * @see ClassRegexSelector
 * @see TagRegexSelector
 */
public interface RegexSelector extends Selector {

    /**
     * Returns the compiled pattern used by this selector.
     *
     * @return the regex pattern; never {@code null}
     */
    Pattern pattern();
}
