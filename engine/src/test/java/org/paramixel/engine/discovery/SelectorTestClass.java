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

package org.paramixel.engine.discovery;

import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

/**
 * Test class used for selector discovery coverage.
 */
@Paramixel.TestClass
public class SelectorTestClass {

    /**
     * Sample test method used by method selectors.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void testMethod(final ArgumentContext context) {}
}
