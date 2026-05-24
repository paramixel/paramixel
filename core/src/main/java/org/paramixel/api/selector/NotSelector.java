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

/**
 * A {@link Selector} that negates another selector.
 *
 * <p>A candidate matches a {@code NotSelector} when it does <em>not</em> match the
 * negated selector. All three {@code matches*} methods are strictly negated.
 *
 * @see Selector#not(Selector)
 */
public interface NotSelector extends Selector {

    /**
     * Returns the selector negated by this NOT selector.
     *
     * @return the negated selector; never {@code null}
     */
    Selector selector();
}
