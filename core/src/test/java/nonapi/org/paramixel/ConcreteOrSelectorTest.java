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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.selector.Selector;

@DisplayName("ConcreteOrSelector")
class ConcreteOrSelectorTest {

    private static final Selector SELECTOR_A = Selector.tagRegex("smoke");
    private static final Selector SELECTOR_B = Selector.tagRegex("integration");

    @Test
    @DisplayName("constructor rejects null")
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new ConcreteOrSelector(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getSelectors returns the list")
    void getSelectorsReturnsList() {
        var selector = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector.selectors()).containsExactly(SELECTOR_A, SELECTOR_B);
    }

    @Test
    @DisplayName("equals returns true for same reference")
    void equalsReturnsTrueForSameReference() {
        var selector = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector.equals(selector)).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same selectors")
    void equalsReturnsTrueForSameSelectors() {
        var selector1 = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));
        var selector2 = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different selectors")
    void equalsReturnsFalseForDifferentSelectors() {
        var selector1 = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));
        var selector2 = new ConcreteOrSelector(List.of(SELECTOR_A));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsReturnsFalseForNull() {
        var selector = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsReturnsFalseForDifferentType() {
        var selector = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector.equals("OrSelector")).isFalse();
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
        var selector1 = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));
        var selector2 = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("toString contains selectors")
    void toStringContainsSelectors() {
        var selector = new ConcreteOrSelector(List.of(SELECTOR_A, SELECTOR_B));

        assertThat(selector.toString()).startsWith("OrSelector").contains("smoke");
    }
}
