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

package examples.support;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Builder for multi-line text content.
 */
public class TextBlock {

    /**
     * Collected lines for the text block.
     */
    private final List<String> lines;

    /**
     * Creates an empty text block.
     */
    public TextBlock() {
        this.lines = new ArrayList<>();
    }

    /**
     * Adds a line of text.
     *
     * @param line the line to append
     * @return this instance for chaining
     */
    public TextBlock line(final @NonNull String line) {
        this.lines.add(line);
        return this;
    }

    /**
     * Adds an empty line.
     *
     * @return this instance for chaining
     */
    public TextBlock line() {
        return line("");
    }

    /**
     * Returns the text block content.
     *
     * @return the assembled text content
     */
    public String toString() {
        return String.join(System.lineSeparator(), lines);
    }
}
