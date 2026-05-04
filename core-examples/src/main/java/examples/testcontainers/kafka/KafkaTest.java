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
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.DependentSequential;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.Cleanup;
import org.testcontainers.containers.Network;

public class KafkaTest {

    private static final Logger LOGGER = Logger.createLogger(KafkaTest.class);
    private static final String TOPIC = "test";
    private static final String GROUP_ID = "test-group-id";
    private static final String EARLIEST = "earliest";
    private static final String NETWORK = "network";
    private static final String ENVIRONMENT = "environment";
    private static final String MESSAGE = "message";

    public static void main(String[] args) throws Throwable {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        return Parallel.of(
                "KafkaExample",
                KafkaTestEnvironment.createTestEnvironments().stream()
                        .map(KafkaTest::createLifecycleAction)
                        .toList());
    }

    private static Action createLifecycleAction(KafkaTestEnvironment environment) {
        Action produceMethodAction = Direct.of("test produce", context -> {
            var lifecycleContext = context.findAncestor(2).orElseThrow();
            KafkaTestEnvironment testEnvironment =
                    lifecycleContext.getStore().get(ENVIRONMENT).orElseThrow().cast(KafkaTestEnvironment.class);

            LOGGER.info("[%s] testing produce() ...", testEnvironment.name());

            String message = RandomUtil.getRandomString(16);
            context.getParent().orElseThrow().getStore().put(MESSAGE, Value.of(message));

            LOGGER.info("[%s] producing message [%s] ...", testEnvironment.name(), message);

            try (KafkaProducer<String, String> producer = createKafkaProducer(testEnvironment)) {
                producer.send(new ProducerRecord<>(TOPIC, message)).get();
            }

            LOGGER.info("[%s] message [%s] produced", testEnvironment.name(), message);
        });

        Action consumeMethodAction = Direct.of("test consume", context -> {
            var lifecycleContext = context.findAncestor(2).orElseThrow();
            KafkaTestEnvironment testEnvironment =
                    lifecycleContext.getStore().get(ENVIRONMENT).orElseThrow().cast(KafkaTestEnvironment.class);

            String message = context.getParent()
                    .orElseThrow()
                    .getStore()
                    .get(MESSAGE)
                    .orElseThrow()
                    .cast(String.class);
            LOGGER.info("[%s] expected message [%s]", testEnvironment.name(), message);

            boolean messageMatched = false;
            try (KafkaConsumer<String, String> consumer = createKafkaConsumer(testEnvironment)) {
                consumer.subscribe(Collections.singletonList(TOPIC));

                var consumerRecords = consumer.poll(Duration.ofMillis(10_000));
                for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                    LOGGER.info("[%s] consumed message [%s]", testEnvironment.name(), consumerRecord.value());
                    assertThat(consumerRecord.value()).isEqualTo(message);
                    messageMatched = true;
                }

                assertThat(messageMatched).isTrue();
                assertThat(consumer.poll(Duration.ofMillis(2_000)).isEmpty()).isTrue();
            }
        });

        Action testMethodsAction = DependentSequential.of("methods", List.of(produceMethodAction, consumeMethodAction));

        return Lifecycle.of(
                environment.name(),
                Direct.of("before", context -> {
                    LOGGER.info("[%s] initialize test environment ...", environment.name());

                    Network network = NetworkFactory.createNetwork();

                    environment.initialize(network);
                    assertThat(environment.isRunning()).isTrue();

                    LOGGER.info("bootstrap servers: %s", environment.getBootstrapServers());
                    environment.createTopic(TOPIC);

                    context.getStore().put(NETWORK, Value.of(network));
                    context.getStore().put(ENVIRONMENT, Value.of(environment));
                }),
                testMethodsAction,
                Direct.of("after", context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    var removedEnvironment = context.getStore().remove(ENVIRONMENT);
                    var removedNetwork = context.getStore().remove(NETWORK);
                    if (removedEnvironment.isPresent() && removedNetwork.isPresent()) {
                        KafkaTestEnvironment testEnvironment =
                                removedEnvironment.orElseThrow().cast(KafkaTestEnvironment.class);
                        Network network = removedNetwork.orElseThrow().cast(Network.class);

                        Cleanup.of(Cleanup.Mode.FORWARD)
                                .addCloseable(testEnvironment)
                                .addCloseable(network)
                                .runAndThrow();
                    }
                }));
    }

    private static KafkaProducer<String, String> createKafkaProducer(final KafkaTestEnvironment environment) {
        var properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, String> createKafkaConsumer(final KafkaTestEnvironment environment) {
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EARLIEST);
        return new KafkaConsumer<>(properties);
    }
}
