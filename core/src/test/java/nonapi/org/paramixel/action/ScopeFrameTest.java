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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ScopeFrame")
class ScopeFrameTest {

    @Test
    @DisplayName("an empty frame finds nothing")
    void emptyFrameFindsNothing() {
        assertThat(ScopeFrame.root().find(String.class)).isNull();
    }

    @Test
    @DisplayName("find returns a bound value assignable to the requested type")
    void findReturnsAssignableValue() {
        var frame = ScopeFrame.root();
        frame.bind(String.class, "value");

        assertThat(frame.find(String.class)).isEqualTo("value");
        assertThat(frame.find(CharSequence.class)).isEqualTo("value");
        assertThat(frame.find(Integer.class)).isNull();
    }

    @Test
    @DisplayName("within one frame the most recently bound value wins")
    void mostRecentlyBoundWins() {
        var frame = ScopeFrame.root();
        frame.bind(String.class, "first");
        frame.bind(String.class, "second");

        assertThat(frame.find(String.class)).isEqualTo("second");
    }

    @Test
    @DisplayName("an inner frame shadows an outer value of the same type")
    void innerShadowsOuterOfSameType() {
        var outer = ScopeFrame.root();
        outer.bind(String.class, "outer");
        var inner = outer.child();
        inner.bind(String.class, "inner");

        assertThat(inner.find(String.class)).isEqualTo("inner");
        assertThat(outer.find(String.class)).isEqualTo("outer");
    }

    @Test
    @DisplayName("an inner frame still sees an outer value of a different type")
    void innerSeesOuterOfDifferentType() {
        var outer = ScopeFrame.root();
        outer.bind(String.class, "outer");
        var inner = outer.child();
        inner.bind(Integer.class, 42);

        assertThat(inner.find(String.class)).isEqualTo("outer");
        assertThat(inner.find(Integer.class)).isEqualTo(42);
    }

    @Test
    @DisplayName("findLocal ignores ancestor frames")
    void findLocalIgnoresAncestors() {
        var outer = ScopeFrame.root();
        outer.bind(String.class, "outer");
        var inner = outer.child();

        assertThat(inner.findLocal(String.class)).isNull();
        assertThat(inner.find(String.class)).isEqualTo("outer");
    }

    @Test
    @DisplayName("removeLocal removes only a value bound in this frame")
    void removeLocalRemovesOnlyLocalValue() {
        var outer = ScopeFrame.root();
        outer.bind(String.class, "outer");
        var inner = outer.child();
        inner.bind(String.class, "inner");

        assertThat(inner.removeLocal(String.class)).isEqualTo("inner");
        assertThat(inner.removeLocal(String.class)).isNull();
        assertThat(inner.find(String.class)).isEqualTo("outer");
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNullArguments() {
        var frame = ScopeFrame.root();

        assertThatThrownBy(() -> frame.bind(null, "value")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> frame.bind(String.class, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> frame.find(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> frame.findLocal(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> frame.removeLocal(null)).isInstanceOf(NullPointerException.class);
    }
}
