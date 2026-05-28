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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Arguments collection validation")
class ArgumentsCollectionValidationTest {

    @Test
    @DisplayName("requireNonEmpty(Collection, String) returns non-empty collection")
    void requireNonEmptyCollectionReturnsNonEmptyCollection() {
        List<String> list = List.of("a");
        assertThat(Arguments.requireNonEmpty(list, "is empty")).isSameAs(list);
    }

    @Test
    @DisplayName("requireNonEmpty(Collection, String) rejects null collection")
    void requireNonEmptyCollectionRejectsNullCollection() {
        assertThatThrownBy(() -> Arguments.requireNonEmpty(null, "is empty")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireNonEmpty(Collection, String) rejects empty collection")
    void requireNonEmptyCollectionRejectsEmptyCollection() {
        assertThatThrownBy(() -> Arguments.requireNonEmpty(Collections.emptyList(), "is empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("is empty");
    }

    @Test
    @DisplayName("requireNoNullElements(Collection, String) returns collection with no null elements")
    void requireNoNullElementsCollectionReturnsCollectionWithNoNullElements() {
        List<String> list = List.of("a", "b");
        assertThat(Arguments.requireNoNullElements(list, "no nulls")).isSameAs(list);
    }

    @Test
    @DisplayName("requireNoNullElements(Collection, String) rejects null collection")
    void requireNoNullElementsCollectionRejectsNullCollection() {
        assertThatThrownBy(() -> Arguments.requireNoNullElements((Collection<String>) null, "no nulls"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireNoNullElements(Collection, String) rejects collection with null element")
    void requireNoNullElementsCollectionRejectsCollectionWithNullElement() {
        ArrayList<String> list = new ArrayList<>();
        list.add(null);
        assertThatThrownBy(() -> Arguments.requireNoNullElements(list, "no nulls"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("no nulls");
    }

    @Test
    @DisplayName("requireNoNullElements(T[], String) returns array with no null elements")
    void requireNoNullElementsArrayReturnsArrayWithNoNullElements() {
        String[] array = {"a", "b"};
        assertThat(Arguments.requireNoNullElements(array, "no nulls")).isSameAs(array);
    }

    @Test
    @DisplayName("requireNoNullElements(T[], String) rejects array with null element")
    void requireNoNullElementsArrayRejectsArrayWithNullElement() {
        String[] array = {"a", null, "b"};
        assertThatThrownBy(() -> Arguments.requireNoNullElements(array, "no nulls"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("no nulls");
    }

    @Test
    @DisplayName("requireNoNullElements(T[], String) accepts empty array")
    void requireNoNullElementsArrayAcceptsEmptyArray() {
        String[] array = {};
        assertThat(Arguments.requireNoNullElements(array, "no nulls")).isSameAs(array);
    }

    @Test
    @DisplayName("requireNoNullElements(T[], String) rejects null array")
    void requireNoNullElementsArrayRejectsNullArray() {
        assertThatThrownBy(() -> Arguments.requireNoNullElements((String[]) null, "no nulls"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("array is null");
    }
}
