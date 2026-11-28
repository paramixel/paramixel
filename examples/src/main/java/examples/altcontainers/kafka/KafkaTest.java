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

package examples.altcontainers.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import examples.altcontainers.util.RandomUtil;
import examples.support.Logger;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.paramixel.api.Context;
import org.paramixel.api.Parallel;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.TaskBuilder;

/**
 * Parameterized integration test that starts Kafka containers for each Docker image listed in
 * {@code /docker-images.txt}, produces a message, consumes it, and asserts the round-trip content matches.
 *
 * <p>The test is fully static, so the definition is the only state. The environment already exists when the plan is
 * built and is declared with {@code bind}, which also makes it a readable plan parameter in report files. The message
 * only exists once the test runs, so {@code produce} stores it with {@link Context#put(String, Object)} and
 * {@code consume} reads it back by name.
 */
public final class KafkaTest {

    private static final String ID_SET_UP = "setUp";
    private static final String ID_PRODUCE = "produce";
    private static final String ID_CONSUME = "consume";
    private static final String ID_TEAR_DOWN = "tearDown";

    private static final String ENVIRONMENT = "environment";
    private static final String MESSAGE = "message";

    private static final Logger LOGGER = Logger.createLogger(KafkaTest.class);
    private static final String TOPIC = "test";
    private static final String GROUP_ID = "test-group-id";
    private static final String EARLIEST = "earliest";

    private KafkaTest() {
        // Intentionally empty
    }

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
     * Builds a parallel task tree with one branch per Kafka Docker image, each binding its environment, starting it in
     * the branch's {@code before} hook, running the produce/consume steps, and closing it in {@code after}.
     *
     * @return the definition for this test
     * @throws Throwable if environment creation fails
     */
    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory() throws Throwable {
        final var root = Parallel.builder(KafkaTest.class, "KafkaTest", Pool.named("environments"));
        for (final var environment : KafkaTestEnvironment.createTestEnvironments()) {
            root.step(Sequential.builder(environment.name())
                    .bind(ENVIRONMENT, environment)
                    .before(ID_SET_UP)
                    .step(Sequential.builder("tests").step(ID_PRODUCE).step(ID_CONSUME))
                    .after(ID_TEAR_DOWN));
        }
        return root;
    }

    /**
     * Starts this branch's environment and creates the topic.
     *
     * @param context the execution context
     * @throws Throwable if the environment cannot be started
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUp(final Context context) throws Throwable {
        final var environment = context.require(ENVIRONMENT, KafkaTestEnvironment.class);

        LOGGER.info("[%s] initialize test environment ...", environment.name());

        environment.initialize();
        assertThat(environment.isRunning()).isTrue();

        LOGGER.info("bootstrap servers: %s", environment.getBootstrapServers());
        environment.createTopic(TOPIC);
    }

    /**
     * Produces one random message and stores it for the consume step.
     *
     * @param context the execution context
     * @throws Throwable if producing fails
     */
    @Paramixel.Id(ID_PRODUCE)
    public static void produce(final Context context) throws Throwable {
        final var environment = context.require(ENVIRONMENT, KafkaTestEnvironment.class);

        LOGGER.info("[%s] testing produce() ...", environment.name());

        final var message = RandomUtil.getRandomString(16);

        LOGGER.info("[%s] producing message [%s] ...", environment.name(), message);

        try (var producer = createKafkaProducer(environment)) {
            producer.send(new ProducerRecord<>(TOPIC, message)).get();
            producer.flush();
        }

        context.put(MESSAGE, message);

        LOGGER.info("[%s] message [%s] produced", environment.name(), message);
    }

    /**
     * Consumes the message produced by the previous step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CONSUME)
    public static void consume(final Context context) {
        final var environment = context.require(ENVIRONMENT, KafkaTestEnvironment.class);
        final var message = context.require(MESSAGE, String.class);

        LOGGER.info("[%s] expected message [%s]", environment.name(), message);

        var messageMatched = false;
        var attempts = 0;
        final var maxAttempts = 5;
        final var pollTimeout = Duration.ofSeconds(2);

        try (var consumer = createKafkaConsumer(environment)) {
            consumer.subscribe(List.of(TOPIC));

            while (!messageMatched && attempts < maxAttempts) {
                final var consumerRecords = consumer.poll(pollTimeout);
                for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                    LOGGER.info("[%s] consumed message [%s]", environment.name(), consumerRecord.value());
                    assertThat(consumerRecord.value()).isEqualTo(message);
                    messageMatched = true;
                }
                attempts++;
            }

            assertThat(messageMatched)
                    .withFailMessage(
                            "Expected message not received within %d polls of %ds each",
                            maxAttempts, pollTimeout.toSeconds())
                    .isTrue();

            assertThat(consumer.poll(Duration.ofMillis(500)).isEmpty()).isTrue();
        }
    }

    /**
     * Stops this branch's environment.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDown(final Context context) {
        final var environment = context.require(ENVIRONMENT, KafkaTestEnvironment.class);

        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }

    private static KafkaProducer<String, String> createKafkaProducer(final KafkaTestEnvironment environment) {
        final var properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, String> createKafkaConsumer(final KafkaTestEnvironment environment) {
        final var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EARLIEST);
        return new KafkaConsumer<>(properties);
    }
}
