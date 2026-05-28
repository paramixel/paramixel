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

package nonapi.org.paramixel.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Listeners.parseExcludes")
class ListenersParseExcludesTest {

    @Test
    @DisplayName("returns empty set for null input")
    void nullInput() {
        var result = Listeners.parseExcludes(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty set for blank input")
    void blankInput() {
        assertThat(Listeners.parseExcludes("")).isEmpty();
        assertThat(Listeners.parseExcludes("   ")).isEmpty();
        assertThat(Listeners.parseExcludes("\t")).isEmpty();
    }

    @Test
    @DisplayName("returns empty set for unknown token")
    void unknownToken() {
        var result = Listeners.parseExcludes("unknown");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty set for multiple unknown tokens")
    void multipleUnknownTokens() {
        var result = Listeners.parseExcludes("foo,bar,baz");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("status shorthand expands to status.header and status.footer")
    void statusShorthand() {
        var result = Listeners.parseExcludes("status");
        assertThat(result).containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER);
    }

    @Test
    @DisplayName("parses status.header token")
    void statusHeaderToken() {
        var result = Listeners.parseExcludes("status.header");
        assertThat(result).containsExactly(ExcludeTarget.STATUS_HEADER);
    }

    @Test
    @DisplayName("parses status.footer token")
    void statusFooterToken() {
        var result = Listeners.parseExcludes("status.footer");
        assertThat(result).containsExactly(ExcludeTarget.STATUS_FOOTER);
    }

    @Test
    @DisplayName("parses summary.header token")
    void summaryHeaderToken() {
        var result = Listeners.parseExcludes("summary.header");
        assertThat(result).containsExactly(ExcludeTarget.SUMMARY_HEADER);
    }

    @Test
    @DisplayName("parses summary.tree token")
    void summaryTreeToken() {
        var result = Listeners.parseExcludes("summary.tree");
        assertThat(result).containsExactly(ExcludeTarget.SUMMARY_TREE);
    }

    @Test
    @DisplayName("parses summary.footer token")
    void summaryFooterToken() {
        var result = Listeners.parseExcludes("summary.footer");
        assertThat(result).containsExactly(ExcludeTarget.SUMMARY_FOOTER);
    }

    @Test
    @DisplayName("is case insensitive")
    void caseInsensitive() {
        assertThat(Listeners.parseExcludes("STATUS"))
                .containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER);
        assertThat(Listeners.parseExcludes("Status"))
                .containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER);
        assertThat(Listeners.parseExcludes("STATUS.HEADER")).containsExactly(ExcludeTarget.STATUS_HEADER);
        assertThat(Listeners.parseExcludes("SUMMARY.TREE")).containsExactly(ExcludeTarget.SUMMARY_TREE);
    }

    @Test
    @DisplayName("handles leading and trailing whitespace")
    void whitespaceHandling() {
        var result = Listeners.parseExcludes(" status , summary.tree ");
        assertThat(result)
                .containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER, ExcludeTarget.SUMMARY_TREE);
    }

    @Test
    @DisplayName("handles duplicate tokens")
    void duplicates() {
        var result = Listeners.parseExcludes("status,status");
        assertThat(result).containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER);
    }

    @Test
    @DisplayName("parses multiple tokens")
    void multipleTokens() {
        var result = Listeners.parseExcludes("status,summary.header");
        assertThat(result)
                .containsExactly(
                        ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER, ExcludeTarget.SUMMARY_HEADER);
    }

    @Test
    @DisplayName("parses all individual tokens together")
    void allIndividualTokens() {
        var result = Listeners.parseExcludes("status.header,status.footer,summary.header,summary.tree,summary.footer");
        assertThat(result)
                .containsExactly(
                        ExcludeTarget.STATUS_HEADER,
                        ExcludeTarget.STATUS_FOOTER,
                        ExcludeTarget.SUMMARY_HEADER,
                        ExcludeTarget.SUMMARY_TREE,
                        ExcludeTarget.SUMMARY_FOOTER);
    }

    @Test
    @DisplayName("quiet shorthand expands to status.header, status.footer, and summary.tree")
    void quietShorthand() {
        var result = Listeners.parseExcludes("quiet");
        assertThat(result)
                .containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER, ExcludeTarget.SUMMARY_TREE);
    }

    @Test
    @DisplayName("all shorthand expands to all targets")
    void allShorthand() {
        var result = Listeners.parseExcludes("all");
        assertThat(result)
                .containsExactly(
                        ExcludeTarget.STATUS_HEADER,
                        ExcludeTarget.STATUS_FOOTER,
                        ExcludeTarget.SUMMARY_HEADER,
                        ExcludeTarget.SUMMARY_TREE,
                        ExcludeTarget.SUMMARY_FOOTER);
    }

    @Test
    @DisplayName("ignores unknown tokens alongside known tokens")
    void unknownAlongsideKnown() {
        var result = Listeners.parseExcludes("status,unknown,summary.tree");
        assertThat(result)
                .containsExactly(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER, ExcludeTarget.SUMMARY_TREE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"s tatus", "summary. tree", "summary .tree"})
    @DisplayName("returns empty set for malformed tokens")
    void malformedTokens(String input) {
        var result = Listeners.parseExcludes(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns unmodifiable enum set")
    void returnsEnumSet() {
        var result = Listeners.parseExcludes("status.header");
        assertThat(result).isInstanceOf(EnumSet.class);
    }

    @ParameterizedTest
    @MethodSource("allFiveTokenCombinations")
    @DisplayName("correctly handles all 2^5 = 32 token combinations")
    void allFiveTokenCombinations(String input, EnumSet<ExcludeTarget> expected) {
        var result = Listeners.parseExcludes(input);
        assertThat(result).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> allFiveTokenCombinations() {
        var tokens = List.of("status.header", "status.footer", "summary.header", "summary.tree", "summary.footer");
        var targets = List.of(
                ExcludeTarget.STATUS_HEADER,
                ExcludeTarget.STATUS_FOOTER,
                ExcludeTarget.SUMMARY_HEADER,
                ExcludeTarget.SUMMARY_TREE,
                ExcludeTarget.SUMMARY_FOOTER);

        return IntStream.range(0, 1 << 5).mapToObj(i -> {
            var input = new StringBuilder();
            var expected = EnumSet.noneOf(ExcludeTarget.class);
            for (int j = 0; j < 5; j++) {
                if ((i & (1 << j)) != 0) {
                    if (input.length() > 0) {
                        input.append(",");
                    }
                    input.append(tokens.get(j));
                    expected.add(targets.get(j));
                }
            }
            return Arguments.of(input.toString(), expected);
        });
    }
}
