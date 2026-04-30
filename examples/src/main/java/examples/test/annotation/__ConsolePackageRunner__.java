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

package examples.test.annotation;

import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.discovery.Selector;

public class __ConsolePackageRunner__ {

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(selector());
    }

    private static Selector selector() {
        return Selector.byPackageName(__ConsolePackageRunner__.class);
    }
}
