# Paramixel -- Execution Model

This spec describes the internal execution model used by the Paramixel test engine.

## Overview

Paramixel uses a **message-passing actor-based execution model** with separate queues for different task types. This architecture provides clear separation of concerns, predictable execution behavior, and optimal resource utilization.

## Architecture

### Actor System

The execution system consists of four actor types, each with its own message queue:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Actor System                                       │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ClassActor          ArgumentDispatcher       MethodDispatcher   Lifecycle   │
│       │                    │                       │               │         │
│       ▼                    ▼                       ▼               ▼         │
│  ┌──────────┐      ┌──────────────┐      ┌──────────────┐   ┌──────────┐   │
│  │ Class    │      │ Argument     │      │ Method       │   │ Lifecycle│   │
│  │ Queue    │      │ Queue        │      │ Queues       │   │ Queue    │   │
│  └──────────┘      └──────────────┘      └──────────────┘   └──────────┘   │
│       │                    │                       │               │         │
│       └────────────────────┴───────────────────────┴───────────────┘         │
│                              │                                               │
│                              ▼                                               │
│                    ┌─────────────────────┐                                   │
│                    │   TaskDispatcher    │                                   │
│                    │   (Message Router)  │                                   │
│                    └─────────────────────┘                                   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Task Types

All tasks implement the `ExecutionTask` interface:

| Task Type | Description | Created By |
|-----------|-------------|------------|
| **ClassTask** | Executes a test class with all arguments | `TaskDispatcher` |
| **ArgumentTask** | Executes a single argument with all test methods | `ClassActor` |
| **MethodTask** | Executes a single test method with pre/post lifecycle | `ArgumentDispatcher` |
| **LifecycleTask** | Executes lifecycle hooks (Initialize, BeforeAll, etc.) | `ClassActor` / `ArgumentDispatcher` |

### Message Queues

Each actor type has dedicated message queues:

| Queue | Purpose | Size |
|-------|---------|------|
| **Class Queue** | Class-level execution tasks | Unlimited |
| **Argument Queue** | Argument-level execution tasks | Unlimited |
| **Method Queue(s)** | Method-level execution (per argument) | Unlimited |
| **Lifecycle Queue** | Lifecycle hook tasks | Unlimited |

## Concurrency Limits

The system enforces three levels of parallelism using a semaphore-based limiter:

| Limit Type | Count | Formula | Purpose |
|------------|-------|---------|---------|
| **Total slots** | `cores × 2` | Global concurrent work cap | Prevent system overload |
| **Class slots** | `cores` | Max concurrent test classes | Isolate test class execution |
| **Argument slots** | `cores` | Max concurrent arguments | Isolate argument execution |

**Per-Class Parallelism:**

Each test class can specify its own `argumentParallelism` via `ArgumentsCollector.setParallelism()`. The effective parallelism is the minimum of:
- The class's configured value
- The global `cores` limit for argument slots

## Execution Flow

### 1. Class Execution (ClassActor)

```
ClassActor receives ClassTask:
  ├─ Check capacity for class slots
  ├─ Acquire class permit
  ├─ Instantiate test class
  ├─ Run @Initialize hooks (abort on first failure)
  ├─ For each argument:
  │   ├─ Check capacity for argument slots  
  │   ├─ Acquire argument permit
  │   ├─ Create ArgumentContext
  │   ├─ Dispatch ArgumentTask
  │   └─ Release argument permit on completion
  └─ Run @Finalize hooks (always execute)
  └─ Release class permit
```

### 2. Argument Execution (ArgumentDispatcher)

```
ArgumentDispatcher receives ArgumentTask:
  ├─ Increment active argument counter for class
  ├─ Check class-level parallelism limit
  ├─ Run @BeforeAll hooks (abort on first failure)
  ├─ For each test method:
  │   ├─ Create MethodTask with BeforeEach/AfterEach methods
  │   ├─ Dispatch to per-argument method queue
  │   └─ Wait for completion
  ├─ Run @AfterAll hooks (always execute if BeforeAll ran)
  ├─ Close argument if AutoCloseable
  └─ Decrement active argument counter
```

### 3. Method Execution (MethodDispatcher)

```
MethodDispatcher receives MethodTask:
  ├─ Run @BeforeEach hooks (abort on first failure)
  ├─ Invoke @Test method
  ├─ Run @AfterEach hooks (always execute if BeforeEach ran)
  └─ Report completion
```

### 4. Lifecycle Execution (LifecycleActor)

```
LifecycleActor receives LifecycleTask:
  ├─ Execute all lifecycle methods in order
  ├─ For @BeforeEach/@BeforeAll/@Initialize: abort on first failure
  ├─ For @AfterEach/@AfterAll/@Finalize: continue despite failures
  └─ Record first failure via classContext.recordFailure()
```

## State Management

### Thread-Safe State

The execution model uses thread-safe state for shared data:

| State | Type | Usage |
|-------|------|-------|
| **Invocation counters** | `AtomicInteger` | Test count tracking |
| **Failure tracking** | `AtomicReference<Throwable>` | First failure storage |
| **Context caches** | `ConcurrentHashMap` | Argument context lookup |
| **Method caches** | `ConcurrentHashMap` | Lifecycle method cache |

### Context Hierarchy

```
EngineContext (immutable, thread-safe Store)
  └─ ClassContext (Immutable wrapper)
       └─ InternalClassContext (Mutable execution state)
            └─ ArgumentContext (Immutable wrapper)
                 └─ InternalArgumentContext (Mutable execution state)
```

**Important:** While the API `ClassContext` and `ArgumentContext` interfaces are immutable, the internal implementations maintain mutable execution state (counters, failures) that is thread-safe via atomic operations.

## Lifecycle Hook Ordering

Within each lifecycle phase, methods are ordered:

1. **@Paramixel.Order** values (lower = earlier)
2. **Method name** (lexicographic ascending)
3. **Methods without @Paramixel.Order** (effective value: `Integer.MAX_VALUE`)

## Error Handling

| Error Type | Policy | Effect on Hooks |
|------------|--------|-----------------|
| **@Initialize** failure | Log + abort | Only @Finalize runs |
| **@BeforeAll** failure | Log + abort | @AfterAll still runs |
| **@BeforeEach** failure | Log + abort | @AfterEach runs |
| **@Test** failure | Count as failure | @AfterEach still runs |
| **@AfterEach** failure | Log + continue | @AfterAll runs |
| **@AfterAll** failure | Log + continue | @Finalize runs |
| **@Finalize** failure | Log + continue | None (final phase) |

**"After" hook guarantee:** All "after" hooks execute even if their paired "before" hook failed or threw an exception.

## Resource Cleanup

### AutoCloseable Arguments

Arguments implementing `AutoCloseable` are closed in this order:

1. After `@AfterAll` completes for that argument
2. In reverse order of creation
3. Exceptions are logged and the class is marked FAILED

### AutoCloseable Test Instance

Test instance is closed after `@Finalize` completes:

1. If `AutoCloseable`, call `close()`
2. Exception is logged and class is marked FAILED

## Thread Name Management

Tasks are executed with descriptive thread names:

| Task Type | Thread Name Format |
|-----------|-------------------|
| Class execution | `{class-thread}` |
| Argument execution | `{class-thread}/{argument-id}` |
| Method execution | `{argument-thread}/{method-id}` |

This enables easy debugging and profiling with thread dump analysis.

## Performance Characteristics

### Virtual Threads (Java 21+)

* **Thread-per-task** execution model
* Minimal memory overhead per thread (~128KB vs ~1MB for platform threads)
* Automatic switching on I/O operations

### Platform Threads (Java 17-20)

* Fixed thread pool with size = `cores`
* Round-robin task assignment
* Lower overhead for compute-bound tasks

## Testing Guidelines

### Unit Tests

Each actor should be tested with mock messages to verify:

1. **Message processing**: Correct state transitions
2. **Queue submission**: Proper task routing
3. **Error handling**: Graceful failure recovery
4. **Resource cleanup**: Permit release on all paths

### Integration Tests

Use the functional tests in `tests/` module to verify:

1. **End-to-end execution**: All lifecycle hooks run
2. **Concurrency limits**: Respected under load
3. **Failure propagation**: First failure reported correctly
4. **Resource cleanup**: AutoCloseable objects closed

## Future Enhancements

Potential future improvements:

1. **Task prioritization**: Higher priority for class tasks over method tasks
2. **Batch processing**: Group similar tasks for efficiency
3. **Backpressure**: Signal overloaded to prevent queue overflow
4. **Per-class queues**: Dedicated queues for each test class
