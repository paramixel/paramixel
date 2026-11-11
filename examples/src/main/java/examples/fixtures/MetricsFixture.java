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

package examples.fixtures;

import java.util.ArrayList;
import java.util.List;

/**
 * A typed fixture: one object per environment, created lazily and shared by every step in its subtree.
 *
 * <p>This is the shape a real suite uses: the fixture owns the resources for one environment, exposes the
 * lifecycle methods the tree calls, and closes itself.
 */
public final class MetricsFixture implements AutoCloseable {

    private final Environment environment;
    private final List<String> calls = new ArrayList<>();
    private boolean started;

    /**
     * Creates a fixture for the supplied environment.
     *
     * @param environment the environment this fixture owns; must not be {@code null}
     */
    public MetricsFixture(final Environment environment) {
        this.environment = environment;
    }

    /**
     * Returns the environment this fixture owns.
     *
     * @return the environment; never {@code null}
     */
    public Environment environment() {
        return environment;
    }

    /**
     * Starts the fixture.
     */
    public void setUp() {
        calls.add("setUp");
        started = true;
    }

    /**
     * Records a check against the fixture.
     *
     * @param check the check name
     */
    public void check(final String check) {
        if (!started) {
            throw new IllegalStateException("fixture is not started");
        }
        calls.add(check);
    }

    /**
     * Stops the fixture.
     */
    public void tearDown() {
        calls.add("tearDown");
        started = false;
    }

    /**
     * Returns the recorded calls, in order.
     *
     * @return the recorded calls
     */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public void close() {
        calls.add("close");
    }
}
