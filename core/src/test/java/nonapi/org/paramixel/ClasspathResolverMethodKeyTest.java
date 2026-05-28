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

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClasspathResolver MethodKey branch coverage")
class ClasspathResolverMethodKeyTest {

    @Test
    @DisplayName("same name and parameter types are equal")
    void sameNameAndParameterTypesAreEqual() throws Exception {
        Object key1 = createMethodKey("test", new Class<?>[] {String.class, int.class});
        Object key2 = createMethodKey("test", new Class<?>[] {String.class, int.class});

        assertThat(key1.equals(key2)).isTrue();
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("different names are not equal")
    void differentNamesAreNotEqual() throws Exception {
        Object key1 = createMethodKey("foo", new Class<?>[] {String.class});
        Object key2 = createMethodKey("bar", new Class<?>[] {String.class});

        assertThat(key1.equals(key2)).isFalse();
    }

    @Test
    @DisplayName("different parameter types are not equal")
    void differentParameterTypesAreNotEqual() throws Exception {
        Object key1 = createMethodKey("test", new Class<?>[] {String.class});
        Object key2 = createMethodKey("test", new Class<?>[] {int.class});

        assertThat(key1.equals(key2)).isFalse();
    }

    @Test
    @DisplayName("equals null returns false")
    void equalsNullReturnsFalse() throws Exception {
        Object key = createMethodKey("test", new Class<?>[] {String.class});

        assertThat(key.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals different type returns false")
    void equalsDifferentTypeReturnsFalse() throws Exception {
        Object key = createMethodKey("test", new Class<?>[] {String.class});

        assertThat(key.equals("not a MethodKey")).isFalse();
    }

    @Test
    @DisplayName("equals self returns true")
    void equalsSelfReturnsTrue() throws Exception {
        Object key = createMethodKey("test", new Class<?>[] {String.class});

        assertThat(key.equals(key)).isTrue();
    }

    @Test
    @DisplayName("hashCode is consistent across calls")
    void hashCodeIsConsistentAcrossCalls() throws Exception {
        Object key = createMethodKey("test", new Class<?>[] {String.class, int.class});

        int h1 = key.hashCode();
        int h2 = key.hashCode();

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("empty parameter types equals another empty parameter types")
    void emptyParameterTypesEqualsAnother() throws Exception {
        Object key1 = createMethodKey("test", new Class<?>[] {});
        Object key2 = createMethodKey("test", new Class<?>[] {});

        assertThat(key1.equals(key2)).isTrue();
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("empty vs non-empty parameter types are not equal")
    void emptyVsNonEmptyParameterTypesAreNotEqual() throws Exception {
        Object key1 = createMethodKey("test", new Class<?>[] {});
        Object key2 = createMethodKey("test", new Class<?>[] {String.class});

        assertThat(key1.equals(key2)).isFalse();
    }

    private static Object createMethodKey(final String name, final Class<?>[] parameterTypes) throws Exception {
        Class<?> methodKeyClass = Class.forName("nonapi.org.paramixel.ClasspathResolver$MethodKey");
        Constructor<?> constructor = methodKeyClass.getDeclaredConstructor(String.class, Class[].class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, parameterTypes);
    }
}
