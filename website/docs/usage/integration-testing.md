---
id: integration-testing
title: Integration Testing
description: Testcontainers integration patterns
---

# Integration Testing

Paramixel works well with Testcontainers for integration testing. This page documents common patterns for testing with Docker containers.

## Overview Pattern

The canonical Testcontainers pattern in Paramixel:

1. Iterate over Docker image versions
2. Each version becomes a `Parallel` argument
3. Each argument is a `Lifecycle` with setup/body/teardown
4. **Setup**: Create network, start container, attach using an in-class `record`
5. **Body**: Sequential test actions that retrieve attached resources via `context.parent()`
6. **Teardown**: Use `CleanupRunner` to stop containers, close networks, remove attachment
7. **Graceful skip**: `NetworkFactory.createNetwork()` throws `SkipException` if Docker unavailable

## Kafka Example

### Environment Class

```java
public class KafkaTestEnvironment {
    private final String version;
    private final String name;
    private KafkaContainer kafka;

    public KafkaTestEnvironment(String version) {
        this.version = version;
        this.name = "Kafka " + version;
    }

    public String name() {
        return name;
    }

    public void initialize(Network network) throws Exception {
        kafka = new KafkaContainer(version)
            .withNetwork(network)
            .withNetworkAliases("kafka");
        kafka.start();

        // Wait for broker to be ready
        AdminClient admin = AdminClient.create(Map.of(
            BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
        ));
        admin.describeCluster().clusterId().get(1, TimeUnit.MINUTES);
        admin.close();
    }

    public String getBootstrapServers() {
        return kafka.getBootstrapServers();
    }

    public void destroy() {
        if (kafka != null) {
            kafka.stop();
        }
    }
}
```

### Test Factory

```java
record Attachment(Network network, KafkaTestEnvironment environment) {}

@Paramixel.ActionFactory
public static Action actionFactory() {
    String[] versions = {"apache/kafka:3.7.2", "apache/kafka:3.8.1", "apache/kafka:3.9.2"};
    List<Action> versionActions = new ArrayList<>();

    for (int i = 0; i < versions.length; i++) {
        String version = versions[i];
        KafkaTestEnvironment env = new KafkaTestEnvironment(version);
        Action produceMethod = createProduceAction(env);
        Action consumeMethod = createConsumeAction(env);
        Action methods = Sequential.of("methods", List.of(produceMethod, consumeMethod));

        versionActions.add(
            Lifecycle.of("Kafka " + version,
                context -> {
                    Network network = NetworkFactory.createNetwork();
                    env.initialize(network);
                    context.setAttachment(new Attachment(network, env));
                },
                methods,
                context -> {
                     new CleanupRunner(CleanupRunner.Mode.FORWARD)
                         .add(env::destroy)
                         .add(() -> {
                             context.removeAttachment().ifPresent(att -> {
                                 if (att instanceof Attachment a && a.network() != null) {
                                     a.network().close();
                                 }
                             });
                         })
                         .runAndThrow();
                 })));
    }

    return Parallel.of("KafkaExample", 2, versionActions);
}

private static Action createProduceAction(KafkaTestEnvironment env) {
    return Direct.of("produce", context -> {
        Attachment att = context.attachment(Attachment.class).orElseThrow();
        Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, att.environment().getBootstrapServers());
        props.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("test-topic", "test-key", "test-value"))
                .get(10, TimeUnit.SECONDS);
        }
    });
}

private static Action createConsumeAction(KafkaTestEnvironment env) {
    return Direct.of("consume", context -> {
        Attachment att = context.attachment(Attachment.class).orElseThrow();
        Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, att.environment().getBootstrapServers());
        props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("test-topic"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThan(0);
        }
    });
}
```

## MongoDB Example

```java
record Attachment(Network network, MongoDBContainer mongo) {}

@Paramixel.ActionFactory
public static Action actionFactory() {
    String[] versions = {"mongo:5.0.32", "mongo:6.0.27", "mongo:7.0.30"};
    List<Action> versionActions = new ArrayList<>();

    for (String version : versions) {
        versionActions.add(
            Lifecycle.of(version,
                context -> {
                    Network network = NetworkFactory.createNetwork();

                    MongoDBContainer mongo = new MongoDBContainer(version)
                        .withNetwork(network);
                    mongo.start();

                    context.setAttachment(new Attachment(network, mongo));
                },
                Direct.of("test insert and query", context -> {
                        Attachment att = context.attachment(Attachment.class).orElseThrow();

                        try (MongoClient client = MongoClients.create(att.mongo().getConnectionString())) {
                            MongoDatabase db = client.getDatabase("testdb");
                            MongoCollection<Document> collection = db.getCollection("testcol");

                            collection.insertOne(new Document("key", "value"));
                            Document found = collection.find(new Document("key", "value")).first();

                            assertThat(found).isNotNull();
                            assertThat(found.getString("key")).isEqualTo("value");
                        }
                    }),
                context -> {
                     new CleanupRunner(CleanupRunner.Mode.FORWARD)
                         .add(() -> {
                             context.removeAttachment().ifPresent(att -> {
                                 if (att instanceof Attachment a && a.mongo() != null) {
                                     a.mongo().stop();
                                 }
                             });
                         })
                         .add(() -> {
                             context.removeAttachment().ifPresent(att -> {
                                 if (att instanceof Attachment a && a.network() != null) {
                                     a.network().close();
                                 }
                             });
                         })
                         .runAndThrow();
                 })));
    }

    return Parallel.of("MongoDBExample", 2, versionActions);
}
```

## Nginx Example

```java
record Attachment(Network network, NginxContainer nginx) {}

@Paramixel.ActionFactory
public static Action actionFactory() {
    String[] versions = {"nginx:1.27.5", "nginx:1.28.2", "nginx:1.29.5"};
    List<Action> versionActions = new ArrayList<>();

    for (String version : versions) {
        versionActions.add(
            Lifecycle.of(version,
                context -> {
                    Network network = NetworkFactory.createNetwork();

                    NginxContainer nginx = new NginxContainer(version)
                        .withNetwork(network);
                    nginx.start();

                    context.setAttachment(new Attachment(network, nginx));
                },
                Direct.of("test HTTP GET", context -> {
                        Attachment att = context.attachment(Attachment.class).orElseThrow();

                        int port = att.nginx().getMappedPort(80);
                        String content = doGet("http://localhost:" + port);

                        assertThat(content).contains("Welcome to nginx!");
                    }),
                context -> {
                     new CleanupRunner(CleanupRunner.Mode.FORWARD)
                         .add(() -> {
                             context.removeAttachment().ifPresent(att -> {
                                 if (att instanceof Attachment a && a.nginx() != null) {
                                     a.nginx().stop();
                                 }
                             });
                         })
                         .add(() -> {
                             context.removeAttachment().ifPresent(att -> {
                                 if (att instanceof Attachment a && a.network() != null) {
                                     a.network().close();
                                 }
                             });
                         })
                         .runAndThrow();
                 })));
    }

    return Parallel.of("NginxExample", 2, versionActions);
}
```

## Common Patterns

> **Note:** `CleanupRunner` is a utility class provided in `org.paramixel.core.support`. `NetworkFactory` is an example utility in the `examples/support/` package. These serve as useful patterns for Testcontainers integration.

### NetworkFactory

```java
public static Network createNetwork() {
    try {
        Network network = Network.newNetwork();
        network.getId(); // Trigger exception if network creation fails
        return network;
    } catch (IllegalStateException e) {
        throw new SkipException("Docker not available", e);
    }
}
```

### CleanupRunner

`CleanupRunner` is provided in `org.paramixel.core.support` and executes cleanup tasks in the order specified by the `Mode`:

```java
public class CleanupRunner {
    public enum Mode {
        FORWARD,  // Execute in registration order
        REVERSE   // Execute in reverse registration order
    }

    public CleanupRunner(Mode mode) { ... }

    public CleanupRunner add(Executable executable) { ... }

    public CleanupRunner add(Executable... executables) { ... }

    public CleanupRunner add(List<Executable> executables) { ... }

    public CleanupRunner addWhen(Supplier<Boolean> condition, Executable executable) { ... }

    public CleanupRunner addWhen(boolean condition, Executable executable) { ... }

    public CleanupRunner run() { ... }

    public void runAndThrow() throws Throwable { ... }

    public interface Executable {
        void run() throws Throwable;
    }
}
```

Usage:

```java
new CleanupRunner(CleanupRunner.Mode.FORWARD)
    .add(container::stop)
    .add(() -> {
        context.removeAttachment().ifPresent(att -> {
            if (att instanceof Attachment a && a.network() != null) {
                a.network().close();
            }
        });
    })
    .runAndThrow();
```

### Version Lists from Resources

Load Docker image versions from `src/main/resources/docker-images.txt`:

```java
public static List<String> loadVersions() throws IOException {
    List<String> lines = Resources.readAllLines(
        Paths.get("docker-images.txt"),
        StandardCharsets.UTF_8
    );
    return lines.stream()
        .filter(line -> !line.isBlank() && !line.startsWith("#"))
        .toList();
}
```

## Graceful Docker Unavailability

If Docker is not available, tests skip instead of fail:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("IntegrationTest",
        context -> {
            Network network = NetworkFactory.createNetwork();
            // If Docker is unavailable, NetworkFactory throws SkipException
            // This marks the action as SKIP, not FAIL
        },
        Direct.of("test", context -> {
        })),
        context -> {
            // Teardown still runs even on skip
        }));
}
```

## Parallelism Considerations

- **Parallel arguments**: Test multiple versions concurrently
- **Sequential methods**: Test steps within a version run sequentially (e.g., produce before consume)
- **Parallelism level**: Limit concurrent containers to avoid resource exhaustion

```java
// 3 Kafka versions, but only 2 running at a time
Parallel.of("KafkaExample", 2, versionActions);
```

## See Also

- [Lifecycle](../actions/lifecycle) - Setup and teardown guarantees
- [Context](context) - Attachment pattern for resource sharing
- [Error Handling](error-handling) - SkipException for graceful skips
