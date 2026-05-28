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

@DisplayName("ConcreteAllSelector")
class ConcreteAllSelectorTest {

    @Test
    @DisplayName("INSTANCE is singleton")
    void instanceIsSingleton() {
        assertThat(ConcreteAllSelector.INSTANCE).isSameAs(ConcreteAllSelector.INSTANCE);
    }

    @Test
    @DisplayName("matchesPackage returns true")
    void matchesPackageReturnsTrue() {
        assertThat(ConcreteAllSelector.INSTANCE.matchesPackage("org.paramixel")).isTrue();
    }

    @Test
    @DisplayName("matchesPackage rejects null")
    void matchesPackageRejectsNull() {
        assertThatThrownBy(() -> ConcreteAllSelector.INSTANCE.matchesPackage(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("packageName is null");
    }

    @Test
    @DisplayName("matchesClass returns true")
    void matchesClassReturnsTrue() {
        assertThat(ConcreteAllSelector.INSTANCE.matchesClass("com.example.Test"))
                .isTrue();
    }

    @Test
    @DisplayName("matchesClass rejects null")
    void matchesClassRejectsNull() {
        assertThatThrownBy(() -> ConcreteAllSelector.INSTANCE.matchesClass(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("className is null");
    }

    @Test
    @DisplayName("matchesTag returns true")
    void matchesTagReturnsTrue() {
        assertThat(ConcreteAllSelector.INSTANCE.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("matchesTag rejects null")
    void matchesTagRejectsNull() {
        assertThatThrownBy(() -> ConcreteAllSelector.INSTANCE.matchesTag(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tag is null");
    }

    @Test
    @DisplayName("toString returns expected value")
    void toStringReturnsExpectedValue() {
        assertThat(ConcreteAllSelector.INSTANCE.toString()).isEqualTo("Selector.all()");
    }
}
