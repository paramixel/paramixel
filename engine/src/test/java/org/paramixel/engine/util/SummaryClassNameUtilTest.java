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

package org.paramixel.engine.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class SummaryClassNameUtilTest {

    @Test
    public void parseProvidedMaxLength_rejectsBlank() {
        assertThatThrownBy(
                        () -> SummaryClassNameUtil.parseProvidedMaxLength("paramixel.summary.classNameMaxLength", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid paramixel.summary.classNameMaxLength: value must not be blank (raw='')");
    }

    @Test
    public void parseProvidedMaxLength_rejectsLeadingTrailingWhitespace() {
        assertThatThrownBy(() ->
                        SummaryClassNameUtil.parseProvidedMaxLength("paramixel.summary.classNameMaxLength", " 60 "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Invalid paramixel.summary.classNameMaxLength: value must not have leading/trailing whitespace (raw=' 60 ' trimmed='60')");
    }

    @Test
    public void parseProvidedMaxLength_rejectsNonInteger() {
        assertThatThrownBy(() ->
                        SummaryClassNameUtil.parseProvidedMaxLength("paramixel.summary.classNameMaxLength", "abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Invalid paramixel.summary.classNameMaxLength: value must be an integer in range [1, 2147483647] (raw='abc')");
    }

    @Test
    public void parseProvidedMaxLength_acceptsValidInteger() {
        assertThat(SummaryClassNameUtil.parseProvidedMaxLength("paramixel.summary.classNameMaxLength", "60"))
                .isEqualTo(60);
    }

    @Test
    public void abbreviateClassName_returnsFullWhenMaxIsUnlimited() {
        String full = "com.example.deep.pkg.ClassName";
        assertThat(SummaryClassNameUtil.abbreviateClassName(
                        "paramixel.summary.classNameMaxLength", full, Integer.MAX_VALUE))
                .isEqualTo(full);
    }

    @Test
    public void abbreviateClassName_abbreviatesAndExpandsFromRightToLeft() {
        String full = "com.example.deep.pkg.ClassName";

        assertThat(SummaryClassNameUtil.abbreviateClassName("paramixel.summary.classNameMaxLength", full, 19))
                .isEqualTo("c.e.d.pkg.ClassName");
    }

    @Test
    public void abbreviateClassName_preservesSegmentsFromRightToLeft_basedOnMaximum() {
        String full = "foo.bar.Class";

        assertThat(SummaryClassNameUtil.abbreviateClassName("paramixel.summary.classNameMaxLength", full, 11))
                .isEqualTo("f.bar.Class");
        assertThat(SummaryClassNameUtil.abbreviateClassName("paramixel.summary.classNameMaxLength", full, 10))
                .isEqualTo("f.b.Class");
    }

    @Test
    public void abbreviateClassName_doesNotExpandLeftSegmentsWhenARightSegmentCannotExpand() {
        String full = "test.argument.ArgumentsTest";

        assertThat(SummaryClassNameUtil.abbreviateClassName("paramixel.summary.classNameMaxLength", full, 20))
                .isEqualTo("t.a.ArgumentsTest");
    }

    @Test
    public void abbreviateClassName_allowsLastSegmentToExceedMaximum() {
        String full = "a.b.SuperLongLastSegment";

        assertThat(SummaryClassNameUtil.abbreviateClassName("paramixel.summary.classNameMaxLength", full, 5))
                .isEqualTo("a.b.SuperLongLastSegment");
    }
}
