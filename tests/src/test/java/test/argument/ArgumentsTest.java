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

package test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that Paramixel can surface arbitrary argument payload types.
 *
 * <p>This test supplies {@link NamedValue} arguments wrapping a variety of Java types and asserts
 * that each invocation receives a named, non-null payload matching the supplied value.
 */
public class ArgumentsTest {

    /**
     * Supplies a heterogeneous set of {@link NamedValue} arguments.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument(NamedValue.of("bigDecimal:fromBigDecimal", new BigDecimal("1.0")));
        collector.addArgument(NamedValue.of("bigDecimal:fromString", new BigDecimal("1.0")));
        collector.addArgument(NamedValue.of("bigInteger:fromBigInteger", new BigInteger("1")));
        collector.addArgument(NamedValue.of("bigInteger:fromString", new BigInteger("1")));
        collector.addArgument(NamedValue.of("boolean", true));
        collector.addArgument(NamedValue.of("byte", (byte) 1));
        collector.addArgument(NamedValue.of("char", 'a'));
        collector.addArgument(NamedValue.of("double", 1d));
        collector.addArgument(NamedValue.of("float", 1.0f));
        collector.addArgument(NamedValue.of("int", 1));
        collector.addArgument(NamedValue.of("long", 1L));
        collector.addArgument(NamedValue.of("short", (short) 1));
        collector.addArgument(NamedValue.of("string", "a"));
    }

    /**
     * Asserts that the argument is a {@link NamedValue} and that its payload matches expectations.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();

        Object raw = context.getArgument();
        assertThat(raw).isInstanceOf(NamedValue.class);

        @SuppressWarnings("unchecked")
        NamedValue<Object> argument = (NamedValue<Object>) raw;

        assertThat(argument.getName()).isNotBlank();
        assertThat(argument.getValue()).isNotNull();

        Object payload = argument.getValue();
        assertThat(payload)
                .isInstanceOfAny(
                        BigDecimal.class,
                        BigInteger.class,
                        Boolean.class,
                        Byte.class,
                        Character.class,
                        Double.class,
                        Float.class,
                        Integer.class,
                        Long.class,
                        Short.class,
                        String.class);

        if (payload instanceof BigDecimal bigDecimal) {
            assertThat(bigDecimal.compareTo(new BigDecimal("1.0"))).isZero();
        } else if (payload instanceof BigInteger bigInteger) {
            assertThat(bigInteger).isEqualTo(new BigInteger("1"));
        } else if (payload instanceof Boolean b) {
            assertThat(b).isTrue();
        } else if (payload instanceof Byte b) {
            assertThat(b).isEqualTo((byte) 1);
        } else if (payload instanceof Character c) {
            assertThat(c).isEqualTo('a');
        } else if (payload instanceof Double d) {
            assertThat(d).isEqualTo(1d);
        } else if (payload instanceof Float f) {
            assertThat(f).isEqualTo(1.0f);
        } else if (payload instanceof Integer i) {
            assertThat(i).isEqualTo(1);
        } else if (payload instanceof Long l) {
            assertThat(l).isEqualTo(1L);
        } else if (payload instanceof Short s) {
            assertThat(s).isEqualTo((short) 1);
        } else if (payload instanceof String s) {
            assertThat(s).isEqualTo("a");
        }
    }
}
