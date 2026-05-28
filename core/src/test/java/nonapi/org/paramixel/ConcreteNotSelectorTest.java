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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.selector.Selector;

@DisplayName("ConcreteNotSelector")
class ConcreteNotSelectorTest {

    private static final Selector INNER = Selector.tagRegex("smoke");

    @Test
    @DisplayName("constructor rejects null")
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new ConcreteNotSelector(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getSelector returns the inner selector")
    void getSelectorReturnsInner() {
        var selector = new ConcreteNotSelector(INNER);

        assertThat(selector.selector()).isSameAs(INNER);
    }

    @Test
    @DisplayName("equals returns true for same reference")
    void equalsReturnsTrueForSameReference() {
        var selector = new ConcreteNotSelector(INNER);

        assertThat(selector.equals(selector)).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same inner selector")
    void equalsReturnsTrueForSameInnerSelector() {
        var selector1 = new ConcreteNotSelector(INNER);
        var selector2 = new ConcreteNotSelector(INNER);

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different inner selector")
    void equalsReturnsFalseForDifferentInnerSelector() {
        var selector1 = new ConcreteNotSelector(Selector.tagRegex("smoke"));
        var selector2 = new ConcreteNotSelector(Selector.tagRegex("integration"));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsReturnsFalseForNull() {
        var selector = new ConcreteNotSelector(INNER);

        assertThat(selector.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsReturnsFalseForDifferentType() {
        var selector = new ConcreteNotSelector(INNER);

        assertThat(selector.equals("NotSelector")).isFalse();
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
        var selector1 = new ConcreteNotSelector(INNER);
        var selector2 = new ConcreteNotSelector(INNER);

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("toString contains inner selector")
    void toStringContainsInnerSelector() {
        var selector = new ConcreteNotSelector(INNER);

        assertThat(selector.toString())
                .startsWith("NotSelector(")
                .contains("smoke")
                .endsWith(")");
    }
}
