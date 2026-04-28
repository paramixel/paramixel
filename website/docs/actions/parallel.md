---
id: parallel
title: Parallel
description: Execute child actions concurrently with bounded parallelism
---

# Parallel

`Parallel` runs child actions concurrently with bounded parallelism, enabling test speedup and stress testing.

## Creating Parallel Actions

### Unbounded Parallelism

```java
import org.paramixel.core.action.Parallel;

Action action = Parallel.of("my parallel", List.of(
    Direct.of("task 1", context -> {
    }),
    Direct.of("task 2", context -> {
    }),
    Direct.of("task 3", context -> {
    })
));
```

Unbounded parallelism uses `Integer.MAX_VALUE` as the parallelism limit.

### Bounded Parallelism

```java
Action action = Parallel.of("my parallel", 4, List.of(
    Direct.of("task 1", context -> {
    }),
    Direct.of("task 2", context -> {
    }),
    Direct.of("task 3", context -> {
    }),
    Direct.of("task 4", context -> {
    }),
    Direct.of("task 5", context -> {
    })
));
```

Bounded parallelism limits concurrent execution to the specified number.

## Key Behavior

- Executes children concurrently up to the specified parallelism
- Waits for all children to complete
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip
- Children are not ordered — execution order is nondeterministic

## Parallelism Configuration

### System Property

Configure parallelism via a system property:

```bash
./mvnw test -Dparamixel.core.runner.parallelism=8
```

### paramixel.properties

Create `src/test/resources/paramixel.properties`:

```properties
paramixel.core.runner.parallelism=4
```

### Maven Plugin Properties

Configure in `pom.xml`:

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <configuration>
        <properties>
            <property>
                <key>paramixel.core.runner.parallelism</key>
                <value>4</value>
            </property>
        </properties>
    </configuration>
</plugin>
```

### Default Parallelism

If not configured, parallelism defaults to `Runtime.getRuntime().availableProcessors()`.

## Examples

### Parallel Argument Matrix

Run test variations in parallel:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    String[] arguments = {"A", "B", "C", "D", "E"};
    List<Action> argumentActions = new ArrayList<>();
    for (String arg : arguments) {
        argumentActions.add(Direct.of(arg, context -> testWithArgument(arg)));
    }
    return Parallel.of("MyTest", 3, argumentActions);
}
```

### Parallel vs Sequential

**Sequential:**

```java
Sequential.of("tests", List.of(
    Direct.of("test 1", context -> {
    }),
    Direct.of("test 2", context -> {
    }),
    Direct.of("test 3", context -> {
    })
));
```

- Runs in order: test 1 → test 2 → test 3
- Total time = sum of all test times

**Parallel:**

```java
Parallel.of("tests", 3, List.of(
    Direct.of("test 1", context -> {
    }),
    Direct.of("test 2", context -> {
    }),
    Direct.of("test 3", context -> {
    })
));
```

- Runs concurrently: all tests start together (up to parallelism limit)
- Total time ≈ max(test time 1, test time 2, test time 3)

### Multi-Level Parallelism

Different parallelism at different tree levels:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    List<Action> groupActions = new ArrayList<>();
    for (int group = 1; group <= 3; group++) {
        List<Action> childActions = new ArrayList<>();
        for (int child = 1; child <= 4; child++) {
            childActions.add(
                Direct.of("child " + child, context -> {
                }));
        }
        groupActions.add(
            Parallel.of("children", 2, childActions));
    }
    return Parallel.of("MyTest", 2, groupActions);
}
```

Execution model:
- 2 groups run concurrently at the top level
- Each group runs 2 children concurrently
- Total: 4 children running at any given time

## Thread Safety

Each context has its own independent attachment. Since `Parallel` creates separate contexts for concurrent children, each child has its own attachment. To share state across concurrent actions, use parent navigation and thread-safe types:

```java
record Attachment(AtomicInteger counter) {}

@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("ConcurrentTest",
        context -> {
            context.setAttachment(new Attachment(new AtomicInteger(0)));
        },
        Parallel.of("concurrent tasks", 4, List.of(
            Direct.of("task 1", context -> {
                Attachment att = context.parent()
                    .orElseThrow()
                    .attachment(Attachment.class)
                    .orElseThrow();
                int count = att.counter().incrementAndGet();
            }),
            Direct.of("task 2", context -> {
                Attachment att = context.parent()
                    .orElseThrow()
                    .attachment(Attachment.class)
                    .orElseThrow();
                int count = att.counter().incrementAndGet();
            }),
            Direct.of("task 3", context -> {
                Attachment att = context.parent()
                    .orElseThrow()
                    .attachment(Attachment.class)
                    .orElseThrow();
                int count = att.counter().incrementAndGet();
            }),
            Direct.of("task 4", context -> {
                Attachment att = context.parent()
                    .orElseThrow()
                    .attachment(Attachment.class)
                    .orElseThrow();
                int count = att.counter().incrementAndGet();
            })
        )),
        context -> {
            Attachment att = context.attachment(Attachment.class).orElseThrow();
            int finalCount = att.counter().get();
            assertThat(finalCount).isEqualTo(4);
        });
}
```

User code is responsible for its own thread safety when sharing state across concurrent actions. Use thread-safe types like `AtomicInteger`, `ConcurrentHashMap`, etc.

## Failures in Parallel Actions

- All children complete regardless of failures
- Status is `FAIL` if any child fails
- Failures are collected from all failed children

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Parallel.of("tests", List.of(
        Direct.of("passing test", context -> {
        }),
        Direct.of("failing test", context -> {
            throw new AssertionError("This fails");
        }),
        Direct.of("another passing test", context -> {
        })
    ));
}
```

All three tests run. Two pass, one fails. The overall status is `FAIL`.

## Stress Testing

Use parallel execution to stress test thread-safety:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    List<Action> concurrentWrites = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
        concurrentWrites.add(
            Direct.of("writer " + i, context -> {
                sharedResource.write("data " + i);
            }));
    }
    return Parallel.of("StressTest", 20, concurrentWrites);
}
```

## See Also

- [Action Composition](../usage/action-composition) - Building action trees with Parallel
- [Context](../usage/context) - Attachment pattern for state sharing
- [Configuration](../configuration) - All configuration options
- [Error Handling](../usage/error-handling) - How failures propagate
