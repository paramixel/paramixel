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

package examples.testcontainers.tansu;

import static org.assertj.core.api.Assertions.assertThat;

import examples.support.CleanupRunner;
import examples.support.Logger;
import examples.support.NetworkFactory;
import examples.testcontainers.util.RandomUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;
import org.testcontainers.containers.Network;

public class TansuExample {

    private static final Logger LOGGER = Logger.createLogger(TansuExample.class);
    private static final String TOPIC = "test";
    private static final String GROUP_ID = "test-group-id";
    private static final String EARLIEST = "earliest";

    record Attachment(Network network, TansuTestEnvironment environment, String message) {}

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        var argumentActions = new ArrayList<Action>();

        for (TansuTestEnvironment environment : TansuTestEnvironment.createTestEnvironments()) {
            Action produceMethodAction = Direct.of("produce", context -> {
                var sequentialContext = context.parent().orElseThrow();
                var lifecycleContext = sequentialContext.parent().orElseThrow();
                Attachment attachment =
                        lifecycleContext.attachment(Attachment.class).orElseThrow();
                LOGGER.info(
                        "[%s] testing produce() ...", attachment.environment().name());

                String message = RandomUtil.getRandomString(16);
                lifecycleContext.setAttachment(new Attachment(attachment.network(), attachment.environment(), message));
                LOGGER.info(
                        "[%s] producing message [%s] ...",
                        attachment.environment().name(), message);

                try (KafkaProducer<String, String> producer = createKafkaProducer(attachment.environment())) {
                    producer.send(new ProducerRecord<>(TOPIC, message)).get();
                }

                LOGGER.info(
                        "[%s] message [%s] produced", attachment.environment().name(), message);
            });

            Action consumeMethodAction = Direct.of("consume", context -> {
                var sequentialContext = context.parent().orElseThrow();
                var lifecycleContext = sequentialContext.parent().orElseThrow();
                Attachment attachment =
                        lifecycleContext.attachment(Attachment.class).orElseThrow();
                String message = attachment.message();
                LOGGER.info(
                        "[%s] expected message [%s]", attachment.environment().name(), message);

                boolean matched = false;
                try (var consumer = createKafkaConsumer(attachment.environment())) {
                    consumer.subscribe(Collections.singletonList(TOPIC));
                    var records = consumer.poll(Duration.ofMillis(10_000));
                    for (var record : records) {
                        LOGGER.info(
                                "[%s] consumed message [%s]",
                                attachment.environment().name(), record.value());
                        assertThat(record.value()).isEqualTo(message);
                        matched = true;
                    }
                    assertThat(matched).isTrue();
                    assertThat(consumer.poll(Duration.ofMillis(2_000)).isEmpty())
                            .isTrue();
                }
            });

            Action methodsAction = Sequential.of("methods", List.of(produceMethodAction, consumeMethodAction));

            Action lifecycleAction = Lifecycle.of(
                    environment.name(),
                    context -> {
                        LOGGER.info("[%s] initialize test environment ...", environment.name());

                        Network network = NetworkFactory.createNetwork();

                        environment.initialize(network);
                        assertThat(environment.isRunning()).isTrue();

                        LOGGER.info("bootstrap servers: %s", environment.getBootstrapServers());
                        environment.createTopic(TOPIC);

                        context.setAttachment(new Attachment(network, environment, null));
                    },
                    methodsAction,
                    context -> {
                        LOGGER.info("[%s] destroy test environment ...", environment.name());

                        new CleanupRunner()
                                .addTask(environment::destroy)
                                .addTask(context.removeAttachment(), attachment -> {
                                    if (attachment instanceof Attachment a && a.network() != null) {
                                        a.network().close();
                                    }
                                })
                                .executeAndThrow();
                    });

            argumentActions.add(lifecycleAction);
        }

        return Parallel.of("TansuExample", argumentActions);
    }

    public static void main(String[] args) throws Throwable {
        Result result = Runner.builder().build().run(actionFactory());
        int exitCode = result.status() == Result.Status.PASS ? 0 : 1;
        System.exit(exitCode);
    }

    private static KafkaProducer<String, String> createKafkaProducer(final TansuTestEnvironment environment) {
        var properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, String> createKafkaConsumer(final TansuTestEnvironment environment) {
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EARLIEST);
        return new KafkaConsumer<>(properties);
    }
}
