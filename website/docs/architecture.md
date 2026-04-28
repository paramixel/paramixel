---
id: architecture
title: Architecture
description: Internal design and components
---

# Architecture

Paramixel is organized into modules with clear separation of concerns.

## Module Layout

```
paramixel/
├── core/              # Main library (public API + implementation)
│   ├── src/main/java/org/paramixel/core/
│   │   ├── Action.java
│   │   ├── AbstractAction.java
│   │   ├── Context.java
│   │   ├── Runner.java
│   │   ├── ConsoleRunner.java
│   │   ├── Result.java
│   │   ├── Listener.java
│   │   ├── Configuration.java
│   │   ├── Resolver.java
│   │   ├── Selector.java
│   │   ├── Paramixel.java (annotation holder)
│   │   ├── FailException.java
│   │   ├── SkipException.java
│   │   ├── ConfigurationException.java
│   │   ├── action/
│   │   │   ├── Direct.java
│   │   │   ├── Sequential.java
│   │   │   ├── StrictSequential.java
│   │   │   ├── RandomSequential.java
│   │   │   ├── StrictRandomSequential.java
│   │   │   ├── Parallel.java
│   │   │   ├── Lifecycle.java
│   │   │   ├── Noop.java
│   │   │   └── Executable.java
│   │   ├── listener/
│   │   │   ├── CompositeListener.java
│   │   │   ├── Listeners.java
│   │   │   ├── StatusListener.java
│   │   │   ├── SummaryListener.java
│   │   │   ├── SummaryRenderer.java
│   │   │   ├── TableSummaryRenderer.java
│   │   │   └── TreeSummaryRenderer.java
│   │   └── internal/ (implementation details)
│   └── src/test/java/ (unit tests for core)
├── maven-plugin/      # Maven plugin for test execution
│   └── src/main/java/org/paramixel/maven/plugin/
│       ├── ParamixelMojo.java
│       ├── Configuration.java
│       ├── internal/
│       │   └── summary/ (summary model and renderers)
│       │   └── util/ (utilities)
│   └── src/test/java/ (unit tests for plugin)
├── tests/            # Self-tests using Paramixel
│   └── src/main/java/
│       ├── test/ (test classes)
│       └── test/util/ (test utilities)
├── examples/         # Usage examples
│   └── src/main/java/
│       ├── examples/ (basic examples)
│       └── examples/testcontainers/ (Testcontainers integration)
└── website/          # Docusaurus documentation
```

## Execution Model

### Two-Phase Model

Paramixel uses a two-phase model:

1. **Discovery Phase** — `Resolver` scans the classpath for `@Paramixel.ActionFactory` methods and builds the action tree
2. **Execution Phase** — `Runner` executes the action tree and produces a `Result` tree

### Discovery Phase

```
Resolver
  ├── ClassGraph scan
  ├── Find @Paramixel.ActionFactory methods
  ├── Skip @Paramixel.Disabled methods
  ├── Validate method signatures
  ├── Invoke factory methods
  └── Compose into root Action (Parallel by default)
```

### Execution Phase

```
Runner
  ├── Build Context tree
  ├── Execute Action tree
  ├── Notify Listener before/after each action
  └── Produce Result tree (PASS/FAIL/SKIP)
```

## Component Overview

### Action

The central abstraction representing a named unit of work.

**Key Types:**
- `Direct` — Leaf action with `Executable` callback
- `Sequential` — Composite with ordered children, runs all children regardless of failures
- `StrictSequential` — Composite with ordered children, stops on first failure
- `RandomSequential` — Composite with shuffled children, runs all children regardless of failures
- `StrictRandomSequential` — Composite with shuffled children, stops on first failure
- `Parallel` — Composite with concurrent children, bounded parallelism
- `Lifecycle` — Composite with setup/body/teardown phases
- `Noop` — Leaf action that completes without doing work

**Relationships:**
```java
Action (interface)
  ├── AbstractAction (abstract class)
  │     ├── Direct
  │     ├── Sequential
  │     │     ├── StrictSequential
  │     │     ├── RandomSequential
  │     │     └── StrictRandomSequential
  │     ├── Parallel
  │     ├── Lifecycle
  │     └── Noop
  └── ConsoleRunner (utility class)
```

### Context

Provides runtime state during execution.

**Methods:**
- `parent()` — Navigate up the context hierarchy
- `action()` — The action associated with this context
- `setAttachment(T)` — Set an attachment (fluent)
- `attachment(Class<T>)` — Retrieve attachment as Optional
- `removeAttachment()` — Clear attachment
- `createChild(Action)` — Create a child context
- `execute(Action)` — Execute child synchronously
- `executeAsync(Action)` — Execute child asynchronously
- `beforeAction(Context, Action)` — Notify listener before action
- `afterAction(Context, Action, Result)` — Notify listener after action

**Hierarchy:** Mirrors the action tree. Each context has its own independent attachment.

### ConsoleRunner

Utility for executing resolved actions with default runner.

**Responsibilities:**
- Resolve actions using a `Selector`
- Execute with default `Runner` configuration
- Convert `Result` status to process exit code

**Static Methods:**
- `static Optional<Result> run(Selector selector)` — Run and return result
- `static int runAndReturnExitCode(Selector selector)` — Run and return exit code
- `static void runAndExit(Selector selector)` — Run and terminate JVM

### Runner

Coordinates action execution.

**Responsibilities:**
- Create execution context with store, listener, configuration
- Execute actions (root, existing context, or as child)
- Delegate child execution to actions via `Context.execute()` and `Context.executeAsync()`

**Builder:**
```java
Runner executor = Runner.builder()
    .runner(customRunner)
    .listener(customListener)
    .configuration(config)
    .build();
```

**Note:** Parallelism is configured per `Parallel` action or via configuration properties, not on the executor.

### Result

Describes the outcome of executing an action.

**Properties:**
- `action()` — The action that produced this result
- `status()` — PASS, FAIL, or SKIP
- `timing()` — Execution duration
- `failure()` — Optional failure cause
- `parent()` — Parent result in the tree
- `children()` — Results from child actions

**Hierarchy:** Mirrors the action tree.

### Listener

Receives notifications during execution.

**Methods:**
- `beforeAction(Context, Action)` — Called before an action starts
- `afterAction(Context, Action, Result)` — Called after an action finishes

**Implementations:**
- `CompositeListener` — Composes multiple listeners
- `StatusListener` — Simple console status logging
- `SummaryListener` — Summary reporting with configurable renderer
- `TableSummaryRenderer` — ASCII table output
- `TreeSummaryRenderer` — Hierarchical tree output

### Resolver

Discovers `@Paramixel.ActionFactory` methods via ClassGraph scanning.

**Responsibilities:**
- Scan classpath for annotated methods
- Validate method signatures
- Skip `@Paramixel.Disabled` methods
- Invoke factory methods
- Compose actions (SEQUENTIAL or PARALLEL)

### Selector

Describes a classpath selection as a regular expression.

**Factory Methods:**
- `byPackageName(String)` — Match package and subpackages
- `byClassName(String)` — Match specific class

### Configuration

Loads configuration from classpath and system properties.

**Precedence:**
1. `paramixel.properties` (classpath)
2. Maven plugin `<properties>` (POM)
3. System properties (`-D`)

## Data Flow

### Discovery Flow

```
ClassLoader → ClassGraph → @Paramixel.ActionFactory methods
     ↓
Validate (public static, no args, returns Action)
     ↓
Skip @Paramixel.Disabled
     ↓
Invoke factory methods → Action trees
     ↓
Compose (Sequential or Parallel) → root Action
```

### Execution Flow

```
root Action
     ↓
Runner.execute(root)
     ↓
For each action:
  1. Context.beforeAction(context, action)
  2. Action.execute(context) → Action.doExecute(context, start)
  3. Result produced (via Result.pass/fail/skip/of)
  4. Context.afterAction(context, action, result)
     ↓
Result tree (mirrors action tree)
```

**Key Points:**
- `Action.execute(Context)` is a `final` template method that handles before/after callbacks, timing, and error catching
- Subclasses implement `doExecute(Context, Instant)` to define execution logic
- Child actions are executed via `Context.execute(Action)` or `Context.executeAsync(Action)`

### Attachment Access Flow

```
Context 1 ──> Attachment A (independent)
Context 2 ──> Attachment B (independent)
Context 3 ──> Attachment C (independent)
```

Each context has its own independent attachment. To share data across contexts, navigate the parent chain.

## Parallel Execution Model

### Permit-Based Parallelism

The executor uses a permit pool to limit concurrent actions:

```
Parallelism = 4
Permits: [●, ●, ●, ●]  (● = available permit)

Action 1 starts: takes a permit → [○, ●, ●, ●]
Action 2 starts: takes a permit → [○, ○, ●, ●]
Action 3 starts: takes a permit → [○, ○, ○, ●]
Action 4 starts: takes a permit → [○, ○, ○, ○]
Action 5 waits: no permits available
Action 1 finishes: returns permit → [●, ○, ○, ○]
Action 5 starts: takes a permit → [○, ○, ○, ○]
```

### Thread Pool

Actions execute in a thread pool managed by the executor.

## Maven Plugin Architecture

### Plugin Mojos

`ParamixelMojo` — Sole mojo with goal `test`

**Parameters:**
- `skipTests` — Skip execution
- `failIfNoTests` — Fail if no factories found
- `properties` — Configuration key-value pairs

### Plugin Execution Flow

```
ParamixelMojo.execute()
  ↓
Build configuration (classpath → POM → system properties)
  ↓
Build test classloader
  ↓
Resolver.resolveActions(classloader)
  ↓
Runner.builder().configuration(config).build().run(action)
  ↓
Result status determines build success/failure
  ↓
Fail build if root status is FAIL
```

### Summary Model

Results are rendered by listeners in the `SummaryListener` class:

```
TableSummaryRenderer: ASCII table output
TreeSummaryRenderer: Hierarchical tree output
```

Both renderers display action results with status, timing, and failure information.

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17+ |
| Build Tool | Maven 3.9+ |
| Classpath Scanning | ClassGraph |
| Maven Plugin | Maven Plugin API |
| Code Formatting | Spotless (Palantir Java Format) |
| Static Analysis | SpotBugs, PMD |
| Documentation | Docusaurus |

## Public API

All public types are in `org.paramixel.core` and `org.paramixel.core.action`:

**org.paramixel.core:**
- `Action`, `AbstractAction`, `Context`, `Runner`, `Result`, `Listener`, `ConsoleRunner`
- `Configuration`, `Resolver`, `Selector`
- `Paramixel` (annotation holder)
- `FailException`, `SkipException`, `ConfigurationException`

**org.paramixel.core.action:**
- `Direct`, `Sequential`, `StrictSequential`, `RandomSequential`, `StrictRandomSequential`
- `Parallel`, `Lifecycle`, `Noop`
- `Executable`

Internal types are in `org.paramixel.core.internal`.

## See Also

- [API Reference](api/intro) - Complete public API inventory
- [Maven Plugin](usage/maven-plugin) - Plugin architecture
