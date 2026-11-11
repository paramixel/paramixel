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

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;

@DisplayName("Pools")
class PoolsTest {

    private static final int CORES = Runtime.getRuntime().availableProcessors();

    private static Pools pools(final String key, final String value) {
        return new Pools(Configuration.of(Map.of(key, value)));
    }

    private static int size(final String value) {
        return pools("paramixel.parallelism.p", value).pool("p").size();
    }

    @Test
    @DisplayName("a named pool is sized by paramixel.parallelism.<pool>")
    void namedPoolIsSizedByItsKey() {
        assertThat(size("7")).isEqualTo(7);
        assertThat(size(" 3 ")).isEqualTo(3);
    }

    @Test
    @DisplayName("@cores resolves to the number of processors the JVM reports")
    void coresResolvesToProcessors() {
        assertThat(size("@cores")).isEqualTo(CORES);
    }

    @Test
    @DisplayName("@cores*N multiplies and @cores/N divides")
    void coresArithmetic() {
        assertThat(size("@cores*3")).isEqualTo(CORES * 3);
        assertThat(size("@cores/2")).isEqualTo(Math.max(1, CORES / 2));
        assertThat(size("@cores/1")).isEqualTo(CORES);
    }

    @Test
    @DisplayName("a result below one is raised to one")
    void minimumIsOne() {
        assertThat(size("@cores/" + (CORES + 1))).isEqualTo(1);
        assertThat(size("@cores/1000000")).isEqualTo(1);
    }

    @Test
    @DisplayName("a very large product is clamped to Integer.MAX_VALUE")
    void largeProductIsClamped() {
        assertThat(size("@cores*99999999999")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("invalid sizes are rejected with the key and value")
    void invalidSizesAreRejected() {
        for (var value : new String[] {
            "0",
            "-1",
            "abc",
            "",
            "@cor",
            "@core",
            "@cores*0",
            "@cores/0",
            "@cores*",
            "@cores+2",
            "@cores*-1",
            "@cores*1.5",
            "@CORES"
        }) {
            assertThatThrownBy(() -> size(value))
                    .as("value '%s'", value)
                    .isInstanceOf(org.paramixel.api.exception.ConfigurationException.class)
                    .hasMessageContaining("paramixel.parallelism.p")
                    .hasMessageContaining("@cores");
        }
    }

    @Test
    @DisplayName("an undefined named pool names the key to add")
    void undefinedPoolNamesTheKey() {
        assertThatThrownBy(() -> new Pools(Configuration.of(Map.of())).pool("docker"))
                .isInstanceOf(org.paramixel.api.exception.ConfigurationException.class)
                .hasMessageContaining("paramixel.parallelism.docker");
    }

    @Test
    @DisplayName("the root pool defaults to the number of processors")
    void rootDefaultsToProcessors() {
        assertThat(new Pools(Configuration.of(Map.of()))
                        .pool(Pools.ROOT_POOL_NAME)
                        .size())
                .isEqualTo(CORES);
    }

    @Test
    @DisplayName("the root pool is sized by the bare paramixel.parallelism key, including @cores forms")
    void rootIsSizedByTheBareKey() {
        assertThat(pools(Configuration.RUNNER_PARALLELISM, "3")
                        .pool(Pools.ROOT_POOL_NAME)
                        .size())
                .isEqualTo(3);
        assertThat(pools(Configuration.RUNNER_PARALLELISM, "@cores*2")
                        .pool(Pools.ROOT_POOL_NAME)
                        .size())
                .isEqualTo(CORES * 2);
    }

    @Test
    @DisplayName("a user pool named root is sized by paramixel.parallelism.root")
    void userPoolNamedRootIsSizedByItsOwnKey() {
        assertThat(pools("paramixel.parallelism.root", "3").pool("root").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("the root pool and a user pool named root are independent budgets")
    void rootPoolAndUserRootAreIndependent() {
        var pools = pools("paramixel.parallelism.root", "2");

        assertThat(pools.pool(Pools.ROOT_POOL_NAME)).isNotSameAs(pools.pool("root"));
        assertThat(pools.pool(Pools.ROOT_POOL_NAME).size()).isEqualTo(CORES);
        assertThat(pools.pool("root").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("a missing key for a user pool named root does not fall back to the root pool")
    void missingUserRootKeyDoesNotFallBack() {
        assertThatThrownBy(() -> new Pools(Configuration.of(Map.of())).pool("root"))
                .isInstanceOf(org.paramixel.api.exception.ConfigurationException.class)
                .hasMessageContaining("paramixel.parallelism.root");
    }

    @Test
    @DisplayName("a pool is created once and shared")
    void poolIsShared() {
        var pools = pools("paramixel.parallelism.p", "2");
        assertThat(pools.pool("p")).isSameAs(pools.pool("p"));
    }
}
