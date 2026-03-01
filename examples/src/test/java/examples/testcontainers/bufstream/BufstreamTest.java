/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

import examples.support.CleanupExecutor;
import examples.support.Logger;
import examples.testcontainers.util.RandomUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;
import org.testcontainers.containers.Network;

@SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
@Paramixel.TestClass
public class BufstreamTest {

    private static final Logger LOGGER = Logger.createLogger(BufstreamTest.class);

    private static final String TOPIC = "test";
    private static final String GROUP_ID = "test-group-id";
    private static final String EARLIEST = "earliest";
    private static final String MESSAGE = "message";
    private static final String NETWORK = "network";

    @Paramixel.ArgumentSupplier(parallelism = Integer.MAX_VALUE)
    public static Stream<BufstreamTestEnvironment> arguments() throws IOException {
        return BufstreamTestEnvironment.createTestEnvironments();
    }

    @Paramixel.BeforeAll
    public void initializeTestEnvironment(final @NonNull ArgumentContext argumentContext) throws IOException {
        BufstreamTestEnvironment bufstreamTestEnvironment = argumentContext.getArgument(BufstreamTestEnvironment.class);
        LOGGER.info("[%s] initialize test environment ...", bufstreamTestEnvironment.getName());

        Network network = Network.newNetwork();
        network.getId();

        bufstreamTestEnvironment.initialize(network);
        argumentContext.getStore().put(NETWORK, network);

        assertThat(bufstreamTestEnvironment.isRunning()).isTrue();

        LOGGER.info("bootstrap servers: %s", bufstreamTestEnvironment.getBootstrapServers());
    }

    @Paramixel.Test
    @Paramixel.Order(1)
    public void testProduce(final @NonNull ArgumentContext argumentContext)
            throws ExecutionException, InterruptedException {
        BufstreamTestEnvironment bufstreamTestEnvironment = argumentContext.getArgument(BufstreamTestEnvironment.class);
        LOGGER.info("[%s] testing testProduce() ...", bufstreamTestEnvironment.getName());

        String message = RandomUtil.getRandomString(16);
        argumentContext.getStore().put(MESSAGE, message);

        LOGGER.info("[%s] producing message [%s] ...", bufstreamTestEnvironment.getName(), message);

        try (KafkaProducer<String, String> producer = createKafkaProducer(bufstreamTestEnvironment)) {
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(TOPIC, message);
            producer.send(producerRecord).get();
        }

        LOGGER.info("[%s] message [%s] produced", bufstreamTestEnvironment.getName(), message);
    }

    @Paramixel.Test
    @Paramixel.Order(2)
    public void testConsume(final @NonNull ArgumentContext argumentContext) {
        BufstreamTestEnvironment bufstreamTestEnvironment = argumentContext.getArgument(BufstreamTestEnvironment.class);
        LOGGER.info("[%s] testing testConsume() ...", bufstreamTestEnvironment.getName());

        String message = argumentContext.getStore().get(MESSAGE, String.class);
        LOGGER.info("[%s] expected message [%s]", bufstreamTestEnvironment.getName(), message);
        boolean messageMatched = false;

        try (KafkaConsumer<String, String> consumer = createKafkaConsumer(bufstreamTestEnvironment)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(10_000));
            for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                LOGGER.info("[%s] consumed message [%s]", bufstreamTestEnvironment.getName(), consumerRecord.value());
                assertThat(consumerRecord.value()).isEqualTo(message);
                messageMatched = true;
            }

            assertThat(messageMatched).isTrue();

            ConsumerRecords<String, String> consumerRecordsAfter = consumer.poll(Duration.ofMillis(2_000));
            assertThat(consumerRecordsAfter.isEmpty()).isTrue();
        }
    }

    @Paramixel.AfterAll
    public void destroyTestEnvironment(final @NonNull ArgumentContext argumentContext) throws Throwable {
        BufstreamTestEnvironment bufstreamTestEnvironment = argumentContext.getArgument(BufstreamTestEnvironment.class);
        LOGGER.info("[%s] destroy test environment ...", bufstreamTestEnvironment.getName());

        new CleanupExecutor()
                .addTask(bufstreamTestEnvironment::destroy)
                .addTaskIfPresent(() -> (Network) argumentContext.getStore().remove(NETWORK), Network::close)
                .throwIfFailed();
    }

    /**
     * Method to create a KafkaProducer
     *
     * @param bufstreamTestEnvironment bufstreamTestEnvironment
     * @return a KafkaProducer
     */
    private static KafkaProducer<String, String> createKafkaProducer(
            final @NonNull BufstreamTestEnvironment bufstreamTestEnvironment) {
        Properties properties = new Properties();

        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bufstreamTestEnvironment.getBootstrapServers());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        return new KafkaProducer<>(properties);
    }

    /**
     * Method to create a KafkaConsumer
     *
     * @param bufstreamTestEnvironment bufstreamTestEnvironment
     * @return a KafkaConsumer
     */
    private static KafkaConsumer<String, String> createKafkaConsumer(
            final @NonNull BufstreamTestEnvironment bufstreamTestEnvironment) {
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bufstreamTestEnvironment.getBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EARLIEST);

        return new KafkaConsumer<>(properties);
    }
}
