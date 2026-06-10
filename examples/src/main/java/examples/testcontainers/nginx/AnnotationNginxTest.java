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

package examples.testcontainers.nginx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;

import examples.support.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Parameterized integration test that starts Nginx containers using annotation-based
 * method references. Performs an HTTP GET and asserts the default welcome page is
 * served.
 */
public class AnnotationNginxTest {

    private static final Logger LOGGER = Logger.createLogger(AnnotationNginxTest.class);

    private final NginxTestEnvironment environment;

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     * @throws Throwable if environment creation fails
     */
    public static void main(final String[] args) throws Throwable {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds a parallel action tree using annotation-based method references.
     *
     * @return the action tree for this test
     * @throws Throwable if environment creation fails
     */
    @Paramixel.Factory
    public static Action factory() throws Throwable {
        var annotationResolver = AnnotationResolver.create(AnnotationNginxTest.class);

        var parallel = parallel(AnnotationNginxTest.class.getName());
        for (NginxTestEnvironment environment : NginxTestEnvironment.createTestEnvironments()) {
            var lifecycle = scope(environment.name())
                    .before(annotationResolver.byId("setUp"))
                    .body(annotationResolver.byId("testGet"))
                    .after(annotationResolver.byId("tearDown"));

            parallel.child(instance(environment.name(), () -> new AnnotationNginxTest(environment))
                    .body(lifecycle));
        }
        return parallel.build();
    }

    private AnnotationNginxTest(final NginxTestEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Initializes the Nginx test environment.
     */
    @Paramixel.Id("setUp")
    public void setUp() {
        LOGGER.info("[%s] initialize test environment ...", environment.name());

        environment.initialize();
        assertThat(environment.isRunning()).isTrue();
    }

    /**
     * Performs an HTTP GET and asserts the default Nginx welcome page is served.
     *
     * @throws Exception if the HTTP request fails
     */
    @Paramixel.Id("testGet")
    public void testGet() throws Exception {
        LOGGER.info("[%s] testing GET ...", environment.name());

        int port = environment.getNginxContainer().getMappedPort(80);
        String content = doGet("http://localhost:" + port);
        assertThat(content).contains("Welcome to nginx!");
    }

    /**
     * Destroys the Nginx test environment.
     */
    @Paramixel.Id("tearDown")
    public void tearDown() {
        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }

    private static String doGet(final String url) throws Exception {
        var result = new StringBuilder();
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);

        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }
}
