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

package examples.testcontainers.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import examples.support.Logger;
import examples.testcontainers.util.CleanupExecutor;
import examples.testcontainers.util.RandomUtil;
import java.io.IOException;
import org.bson.Document;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.Paramixel;
import org.testcontainers.containers.Network;

@Paramixel.TestClass
/**
 * Demonstrates using Paramixel to run Testcontainers-backed MongoDB tests across environments.
 */
public class MongoDBTest {

    /** Logger for lifecycle output. */
    private static final Logger LOGGER = Logger.createLogger(MongoDBTest.class);

    /** Store key for the shared Testcontainers {@link Network}. */
    private static final String NETWORK = "network";

    /**
     * Supplies {@link MongoDBTestEnvironment} instances as test arguments.
     *
     * @param collector the arguments collector
     * @throws IOException if environment creation fails
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) throws IOException {
        MongoDBTestEnvironment.createTestEnvironments().forEach(collector::addArgument);
    }

    /**
     * Initializes the MongoDB environment for the current argument.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeAll
    public void initializeTestEnvironment(final @NonNull ArgumentContext context) {
        MongoDBTestEnvironment testEnvironment = context.getArgument(MongoDBTestEnvironment.class);
        LOGGER.info("[%s] initialize test environment ...", testEnvironment.getName());

        Network network = Network.newNetwork();
        network.getId();

        context.getClassContext().getStore().put(NETWORK, network);
        testEnvironment.initialize(network);

        assertThat(testEnvironment.isRunning()).isTrue();
    }

    /**
     * Inserts a document and queries it back using the environment's connection string.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void testInsertQuery(final @NonNull ArgumentContext context) {
        MongoDBTestEnvironment testEnvironment = context.getArgument(MongoDBTestEnvironment.class);
        LOGGER.info("[%s] testing testInsertQuery() ...", testEnvironment.getName());

        String name = RandomUtil.getRandomString(16);
        LOGGER.info("[%s] name [%s]", testEnvironment.getName(), name);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(testEnvironment.getConnectionString()))
                .build();

        try (MongoClient mongoClient = MongoClients.create(settings)) {
            MongoDatabase database = mongoClient.getDatabase("test-db");
            MongoCollection<Document> collection = database.getCollection("users");
            Document document = new Document("name", name).append("age", 30);
            collection.insertOne(document);

            Document query = new Document("name", name);
            Document result = collection.find(query).first();
            assertThat(result).isNotNull();
            assertThat(result.get("name")).isEqualTo(name);
            assertThat(result.get("age")).isEqualTo(30);
        }

        LOGGER.info("[%s] name [%s] inserted", testEnvironment.getName(), name);
    }

    /**
     * Destroys the MongoDB environment and closes the shared network.
     *
     * @param context the argument context
     * @throws Throwable if cleanup fails
     */
    @Paramixel.AfterAll
    public void destroyTestEnvironment(final @NonNull ArgumentContext context) throws Throwable {
        MongoDBTestEnvironment testEnvironment = context.getArgument(MongoDBTestEnvironment.class);
        LOGGER.info("[%s] destroy test environment ...", testEnvironment.getName());

        new CleanupExecutor()
                .addTask(testEnvironment::destroy)
                .addTaskIfPresent(
                        () -> context.getClassContext().getStore().remove(NETWORK, Network.class), Network::close)
                .throwIfFailed();
    }
}
