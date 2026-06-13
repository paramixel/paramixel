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

package examples.testcontainers.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;

import examples.support.Logger;
import examples.testcontainers.util.RandomUtil;
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
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Parameterized integration test that starts Kafka containers using annotation-based
 * method references. Produces a message, consumes it, and asserts the round-trip
 * content matches.
 */
public class AnnotationKafkaTest {

    private static final int PARALLELISM = 2;
    private static final Logger LOGGER = Logger.createLogger(AnnotationKafkaTest.class);
    private static final String TOPIC = "test";
    private static final String GROUP_ID = "test-group-id";
    private static final String EARLIEST = "earliest";

    private final KafkaTestEnvironment environment;
    private String message;

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
        var annotationResolver = AnnotationResolver.create(AnnotationKafkaTest.class);

        var parallel = parallel(AnnotationKafkaTest.class.getName()).parallelism(PARALLELISM);
        for (KafkaTestEnvironment environment : KafkaTestEnvironment.createTestEnvironments()) {
            parallel.child(instance(environment.name(), () -> new AnnotationKafkaTest(environment))
                    .body(scope(environment.name())
                            .before(annotationResolver.byId("setUp"))
                            .body(sequential("tests")
                                    .child(annotationResolver.byId("produce"))
                                    .child(annotationResolver.byId("consume")))
                            .after(annotationResolver.byId("tearDown"))));
        }
        return parallel.build();
    }

    private AnnotationKafkaTest(final KafkaTestEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Initializes the Kafka test environment and creates the test topic.
     *
     * @throws Throwable if environment initialization fails
     */
    @Paramixel.Id("setUp")
    public void setUp() throws Throwable {
        LOGGER.info("[%s] initialize test environment ...", environment.name());

        environment.initialize();
        assertThat(environment.isRunning()).isTrue();

        LOGGER.info("bootstrap servers: %s", environment.getBootstrapServers());
        environment.createTopic(TOPIC);
    }

    /**
     * Produces a random message to the Kafka topic.
     *
     * @throws Throwable if message production fails
     */
    @Paramixel.Id("produce")
    public void produce() throws Throwable {
        LOGGER.info("[%s] testing produce() ...", environment.name());

        message = RandomUtil.getRandomString(16);

        LOGGER.info("[%s] producing message [%s] ...", environment.name(), message);

        try (var producer = createKafkaProducer()) {
            producer.send(new ProducerRecord<>(TOPIC, message)).get();
            producer.flush();
        }

        LOGGER.info("[%s] message [%s] produced", environment.name(), message);
    }

    /**
     * Consumes messages from the Kafka topic and asserts the expected message is found.
     */
    @Paramixel.Id("consume")
    public void consume() {
        LOGGER.info("[%s] expected message [%s]", environment.name(), message);

        boolean messageMatched = false;
        int attempts = 0;
        int maxAttempts = 5;
        var pollTimeout = Duration.ofSeconds(2);

        try (var consumer = createKafkaConsumer()) {
            consumer.subscribe(List.of(TOPIC));

            while (!messageMatched && attempts < maxAttempts) {
                var consumerRecords = consumer.poll(pollTimeout);
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
     * Destroys the Kafka test environment.
     */
    @Paramixel.Id("tearDown")
    public void tearDown() {
        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }

    private KafkaProducer<String, String> createKafkaProducer() {
        var properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private KafkaConsumer<String, String> createKafkaConsumer() {
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EARLIEST);
        return new KafkaConsumer<>(properties);
    }
}
