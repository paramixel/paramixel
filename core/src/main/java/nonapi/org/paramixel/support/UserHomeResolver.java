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

package nonapi.org.paramixel.support;

import java.io.IOException;

/**
 * Resolves another user's home directory for tilde-path expansion.
 *
 * @see TildePathExpander
 */
@FunctionalInterface
interface UserHomeResolver {

    /**
     * Looks up the home directory for the supplied user.
     *
     * @param user the username to look up
     * @return the home directory path, or {@code null} if the user does not exist
     * @throws IOException if the lookup fails
     */
    String lookupHome(String user) throws IOException;
}
