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

package examples.testcontainers.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;

import com.mongodb.client.MongoClients;
import examples.support.Logger;
import org.bson.Document;
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Parameterized integration test that starts MongoDB containers using annotation-based
 * method references. Inserts a document, queries it back, and asserts the stored value
 * matches.
 */
public class AnnotationMongoDBTest {

    private static final Logger LOGGER = Logger.createLogger(AnnotationMongoDBTest.class);

    private final MongoDBTestEnvironment environment;

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
        var annotationResolver = AnnotationResolver.create(AnnotationMongoDBTest.class);

        var parallel = parallel(AnnotationMongoDBTest.class.getName());
        for (MongoDBTestEnvironment environment : MongoDBTestEnvironment.createTestEnvironments()) {
            parallel.child(instance(environment.name(), () -> new AnnotationMongoDBTest(environment))
                    .body(scope(environment.name())
                            .before(annotationResolver.byId("setUp"))
                            .body(annotationResolver.byId("testInsertAndQuery"))
                            .after(annotationResolver.byId("tearDown"))));
        }
        return parallel.build();
    }

    private AnnotationMongoDBTest(final MongoDBTestEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Initializes the MongoDB test environment.
     *
     * @throws Throwable if environment initialization fails
     */
    @Paramixel.Id("setUp")
    public void setUp() throws Throwable {
        LOGGER.info("[%s] initialize test environment ...", environment.name());

        environment.initialize();
        assertThat(environment.isRunning()).isTrue();
    }

    /**
     * Inserts a document and queries it back, asserting the stored value matches.
     */
    @Paramixel.Id("testInsertAndQuery")
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

    /**
     * Destroys the MongoDB test environment.
     */
    @Paramixel.Id("tearDown")
    public void tearDown() {
        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }
}
