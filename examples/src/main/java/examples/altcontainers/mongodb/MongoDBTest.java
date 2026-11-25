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

package examples.altcontainers.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import examples.support.Logger;
import org.bson.Document;
import org.paramixel.api.Context;
import org.paramixel.api.Parallel;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Parameterized integration test that starts MongoDB containers for each Docker image
 * listed in {@code /docker-images.txt}, inserts a document, queries it back, and
 * asserts the stored value matches.
 */
public class MongoDBTest {

    private static final String ID_SET_UP = "setUp";
    private static final String ID_TEST_INSERT_QUERY = "testInsertAndQuery()";
    private static final String ID_TEAR_DOWN = "tearDown";

    private static final String ENVIRONMENT = "environment";
    private static final String FIXTURE = "fixture";

    private static final Logger LOGGER = Logger.createLogger(MongoDBTest.class);

    private final MongoDBTestEnvironment environment;

    /**
     * Runs the task factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     * @throws Throwable if environment creation fails
     */
    public static void main(final String[] args) throws Throwable {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds a parallel task tree with one instance branch per MongoDB Docker image,
     * each managing a {@code MongoDBTest} instance with setUp, test, and tearDown lifecycle.
     *
     * @return the definition for this test
     * @throws Throwable if environment creation fails
     */
    @Paramixel.TaskFactory
    public static Task factory() throws Throwable {
        final var root = Parallel.builder(MongoDBTest.class, "MongoDBTest", Pool.named("environments"));
        for (final var environment : MongoDBTestEnvironment.createTestEnvironments()) {
            root.step(Sequential.builder(environment.name())
                    .bind(ENVIRONMENT, environment)
                    .before(ID_SET_UP)
                    .step(ID_TEST_INSERT_QUERY)
                    .after(ID_TEAR_DOWN));
        }
        return root.build();
    }

    /**
     * Builds this branch's fixture and runs its setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUpHook(final Context context) throws Throwable {
        final var fixture = new MongoDBTest(context.require(ENVIRONMENT, MongoDBTestEnvironment.class));
        context.put(FIXTURE, fixture);
        fixture.setUp();
    }

    /**
     * Runs the testInsertAndQuery body of this branch.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_INSERT_QUERY)
    public static void testInsertAndQueryHook(final Context context) throws Throwable {
        context.require(FIXTURE, MongoDBTest.class).testInsertAndQuery();
    }

    /**
     * Releases the fixture, if it was created.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDownHook(final Context context) {
        context.find(FIXTURE, MongoDBTest.class).ifPresent(MongoDBTest::tearDown);
    }

    private MongoDBTest(final MongoDBTestEnvironment environment) {
        this.environment = environment;
    }

    public void setUp() throws Throwable {
        LOGGER.info("[%s] initialize test environment ...", environment.name());

        environment.initialize();
        assertThat(environment.isRunning()).isTrue();
    }

    public void testInsertAndQuery() {
        LOGGER.info("[%s] testing insert and query ...", environment.name());

        try (var mongoClient = MongoClients.create(environment.getConnectionString())) {
            var database = mongoClient.getDatabase("testdb");
            var collection = database.getCollection("testcol");
            collection.insertOne(new Document("key", "value"));

            var found = collection.find(new Document("key", "value")).first();

            assertThat(found).isNotNull();
            assertThat(found.getString("key")).isEqualTo("value");
        }
    }

    public void tearDown() {
        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }
}
