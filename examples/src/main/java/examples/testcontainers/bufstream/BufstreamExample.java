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

package examples.testcontainers.bufstream;

import static org.assertj.core.api.Assertions.assertThat;

import examples.support.Logger;
import examples.support.NetworkFactory;
import examples.testcontainers.util.RandomUtil;
import java.time.Duration;
import java.util.Collections;
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
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.StrictSequential;
import org.paramixel.core.support.Cleanup;
import org.testcontainers.containers.Network;

public class BufstreamExample {

    private static class TestAttachment {
        public Network network;
        public BufstreamTestEnvironment environment;
        public String message;
    }

    private static final Logger LOGGER = Logger.createLogger(BufstreamExample.class);
    private static final String TOPIC = "test";
    private static final String GROUP_ID = "test-group-id";
    private static final String EARLIEST = "earliest";

    public static void main(String[] args) throws Throwable {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        return Parallel.of(
                "BufstreamExample",
                BufstreamTestEnvironment.createTestEnvironments().stream()
                        .map(BufstreamExample::createLifecycleAction)
                        .toList());
    }

    private static Action createLifecycleAction(BufstreamTestEnvironment environment) {
        Action produceMethodAction = Direct.of("test produce", context -> {
            var lifecycleContext = context.findContext(2).orElseThrow();
            TestAttachment testAttachment = lifecycleContext
                    .getAttachment()
                    .flatMap(a -> a.to(TestAttachment.class))
                    .orElseThrow();

            LOGGER.info("[%s] testing produce() ...", testAttachment.environment.name());

            String message = RandomUtil.getRandomString(16);
            testAttachment.message = message;

            LOGGER.info("[%s] producing message [%s] ...", testAttachment.environment.name(), message);

            try (KafkaProducer<String, String> producer = createKafkaProducer(testAttachment.environment)) {
                producer.send(new ProducerRecord<>(TOPIC, message)).get();
            }

            LOGGER.info("[%s] message [%s] produced", testAttachment.environment.name(), message);
        });

        Action consumeMethodAction = Direct.of("test consume", context -> {
            var lifecycleContext = context.findContext(2).orElseThrow();
            TestAttachment testAttachment = lifecycleContext
                    .getAttachment()
                    .flatMap(a -> a.to(TestAttachment.class))
                    .orElseThrow();

            String message = testAttachment.message;
            LOGGER.info("[%s] expected message [%s]", testAttachment.environment.name(), message);

            boolean messageMatched = false;
            try (KafkaConsumer<String, String> consumer = createKafkaConsumer(testAttachment.environment)) {
                consumer.subscribe(Collections.singletonList(TOPIC));

                var consumerRecords = consumer.poll(Duration.ofMillis(10_000));
                for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                    LOGGER.info(
                            "[%s] consumed message [%s]", testAttachment.environment.name(), consumerRecord.value());
                    assertThat(consumerRecord.value()).isEqualTo(message);
                    messageMatched = true;
                }

                assertThat(messageMatched).isTrue();
                assertThat(consumer.poll(Duration.ofMillis(2_000)).isEmpty()).isTrue();
            }
        });

        Action testMethodsAction = StrictSequential.of("methods", List.of(produceMethodAction, consumeMethodAction));

        return Lifecycle.of(
                environment.name(),
                Direct.of("before", context -> {
                    LOGGER.info("[%s] initialize test environment ...", environment.name());

                    Network network = NetworkFactory.createNetwork();

                    environment.initialize(network);
                    assertThat(environment.isRunning()).isTrue();

                    LOGGER.info("bootstrap servers: %s", environment.getBootstrapServers());
                    environment.createTopic(TOPIC);

                    TestAttachment testAttachment = new TestAttachment();
                    testAttachment.network = network;
                    testAttachment.environment = environment;

                    context.setAttachment(testAttachment);
                }),
                testMethodsAction,
                Direct.of("after", context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    TestAttachment testAttachment = context.removeAttachment()
                            .flatMap(a -> a.to(TestAttachment.class))
                            .orElse(null);

                    if (testAttachment != null) {
                        new Cleanup(Cleanup.Mode.FORWARD)
                                .addCloseable(testAttachment.environment)
                                .addCloseable(testAttachment.network)
                                .runAndThrow();
                    }
                }));
    }

    private static KafkaProducer<String, String> createKafkaProducer(final BufstreamTestEnvironment environment) {
        var properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, String> createKafkaConsumer(final BufstreamTestEnvironment environment) {
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EARLIEST);
        return new KafkaConsumer<>(properties);
    }
}
