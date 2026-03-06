# Paramixel -- Lifecycle

This spec is the single authoritative source for lifecycle execution order, method
inheritance rules, annotation contracts, and lifecycle pairing behavior.

## Execution Order

```
[Per class]
  @Paramixel.ArgumentsCollector (static -- no instance required)
  TestClass.newInstance()        <-- no-arg constructor (engine may make constructor accessible)
  @Paramixel.Initialize(ClassContext)
  [Per argument]
    @Paramixel.BeforeAll(ArgumentContext)
    [Per test method]
      @Paramixel.BeforeEach(ArgumentContext)
      @Paramixel.Test(ArgumentContext)
      @Paramixel.AfterEach(ArgumentContext)   <-- only if @Paramixel.BeforeEach executed
    [End per test method]
    @Paramixel.AfterAll(ArgumentContext)      <-- only if @Paramixel.BeforeAll executed
    argument.close()              <-- if argument implements AutoCloseable
  [End per argument]
  @Paramixel.Finalize(ClassContext)           <-- only if @Paramixel.Initialize executed
  testInstance.close()            <-- if test instance implements AutoCloseable
[End per class]
```

## Lifecycle Pairing Rules

"After" hooks MUST only execute when their paired "before" hook has executed:

| Before Hook | After Hook | Condition |
|---|---|---|
| `@Paramixel.Initialize` | `@Paramixel.Finalize` | `@Paramixel.Finalize` runs only if `@Paramixel.Initialize` was invoked |
| `@Paramixel.BeforeAll` | `@Paramixel.AfterAll` | `@Paramixel.AfterAll` runs only if `@Paramixel.BeforeAll` was invoked for that argument |
| `@Paramixel.BeforeEach` | `@Paramixel.AfterEach` | `@Paramixel.AfterEach` runs only if `@Paramixel.BeforeEach` was invoked for that test method |

When a paired "before" hook has executed, the corresponding "after" hook MUST execute
regardless of whether intervening steps failed. This guarantees cleanup.

## AutoCloseable Integration

- If an argument value implements `AutoCloseable`, the engine MUST call `argument.close()`
  after `@Paramixel.AfterAll` completes for that argument.
- If the test class instance implements `AutoCloseable`, the engine MUST call
  `testInstance.close()` after `@Paramixel.Finalize` completes.
- If `close()` throws, the exception MUST be recorded and the class MUST be marked FAILED.
  See `08-error-handling.md`.

## Behavior with No Arguments Collector

If no `@Paramixel.ArgumentsCollector` method exists in the class hierarchy, the engine
MUST create a single argument with value `null` and index `0`. The argument display name
MUST be `"argument:0"`. All lifecycle hooks and test methods execute once with this null
argument.

## Behavior with Zero Arguments

If `@Paramixel.ArgumentsCollector` is invoked but registers zero arguments, the test class
MUST be reported as having zero tests. No per-argument lifecycle hooks (`@Paramixel.BeforeAll`,
`@Paramixel.AfterAll`, `@Paramixel.BeforeEach`, `@Paramixel.AfterEach`, `@Paramixel.Test`)
execute. `@Paramixel.Initialize` and `@Paramixel.Finalize` still execute normally. The class
is NOT marked FAILED.

## Test Class Instantiation

- The test class MUST have a no-arg constructor.
- If the class does not declare an explicit constructor, the compiler provides a default
  no-arg constructor whose access may be package-private. The engine MAY use reflection
  to make that constructor accessible.
- If instantiation fails, all test methods MUST be marked FAILED and no lifecycle hooks
  (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, etc.) execute.

## Thread Safety of Test Instances

One test class instance is created and shared across all argument invocations.
Concurrent argument threads MAY access the instance simultaneously. Test authors
MUST ensure their test class fields are thread-safe when using parallel argument execution.

---

## Annotation Contracts

### `@Paramixel.TestClass`

- **Target:** `ElementType.TYPE`
- **Effect:** Marks a class for engine discovery. Required for a class to be recognized.
- **Constraint:** The class MUST have a no-arg constructor (explicit or compiler-generated).

### `@Paramixel.Test`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ArgumentContext context)` -- instance method, NOT static.
- **Effect:** Executed once per argument supplied by `@Paramixel.ArgumentsCollector` (or once
  with a `null` argument if no collector exists).
- **Inheritance:** Included in the flattened method set (see "Method Inheritance and Ordering").
- **Validation errors (`IllegalStateException` at discovery):**
  - Not `public`
  - Non-void return type
  - Parameter count != 1 or parameter type != `ArgumentContext`
  - `static` modifier present

### `@Paramixel.ArgumentsCollector`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public static void methodName(ArgumentsCollector collector)` -- MUST be static.
- **Effect:** Invoked once per test class during discovery to supply arguments.
- **Constraint:** Only ONE method per class hierarchy MAY be annotated. When inheritance is used:
  - The method in the most specific (outermost) subclass is invoked.
  - Methods in parent classes are ignored (not invoked).
- **Validation errors (`IllegalStateException` at discovery):**
  - Not `public`
  - Non-void return type
  - Parameter count != 1 or parameter type != `ArgumentsCollector`
  - Not `static`

### `@Paramixel.Initialize`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ClassContext context)` -- instance, NOT static.
- **Execution:** One or more methods MAY be declared. All execute once per class, before
  `@Paramixel.BeforeAll`, ordered per "Method Inheritance and Ordering".
- **Pairing:** Paired with `@Paramixel.Finalize`.
- **Failure:** Causes class execution to abort; no tests run; `@Paramixel.Finalize` still runs.

### `@Paramixel.BeforeAll`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ArgumentContext context)` -- MAY be static or instance.
- **Execution:** One or more methods MAY be declared. All execute once per argument bucket,
  before any `@Paramixel.Test` methods for that argument.
- **Pairing:** Paired with `@Paramixel.AfterAll`.
- **Failure:** Aborts that argument bucket; tests for that argument are skipped; `@Paramixel.AfterAll` still runs.

### `@Paramixel.BeforeEach`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ArgumentContext context)` -- instance, NOT static.
- **Execution:** One or more methods MAY be declared. All execute before every individual
  `@Paramixel.Test` method invocation.
- **Pairing:** Paired with `@Paramixel.AfterEach`.
- **Failure:** Aborts that specific test invocation; `@Paramixel.AfterEach` still runs.

### `@Paramixel.AfterEach`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ArgumentContext context)` -- instance, NOT static.
- **Execution:** One or more methods MAY be declared. All execute after every `@Paramixel.Test`
  method invocation (even on test failure). Only executes if `@Paramixel.BeforeEach` was
  invoked for that test method.
- **Failure:** Remaining test methods for the same argument bucket are aborted. The test
  class is marked FAILED. Does not suppress `@Paramixel.AfterAll` or `@Paramixel.Finalize`.

### `@Paramixel.AfterAll`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ArgumentContext context)` -- MAY be static or instance.
- **Execution:** One or more methods MAY be declared. All execute once per argument bucket,
  after all `@Paramixel.Test` methods. Only executes if `@Paramixel.BeforeAll` was invoked.
- **Failure:** Remaining `@Paramixel.AfterAll` methods still execute; `@Paramixel.Finalize`
  still runs; class is marked FAILED.

### `@Paramixel.Finalize`

- **Target:** `ElementType.METHOD`
- **Required signature:** `public void methodName(ClassContext context)` -- instance, NOT static.
- **Execution:** One or more methods MAY be declared. All execute once per class, after all
  `@Paramixel.AfterAll` hooks. Only executes if `@Paramixel.Initialize` was invoked.
- **Failure:** Remaining `@Paramixel.Finalize` methods still execute; class is marked FAILED.

### `@Paramixel.Disabled`

- **Target:** `ElementType.TYPE`, `ElementType.METHOD`
- **Attribute:** `String value()` -- optional reason string (default `""`).
- **Effect on class:** The class is skipped entirely during discovery (no methods executed).
- **Effect on method:** The method is excluded from the test method list at discovery.

### `@Paramixel.DisplayName`

- **Target:** `ElementType.TYPE`, `ElementType.METHOD`
- **Attribute:** `String value()` -- required, non-empty after trimming.
- **Effect:** Replaces the default display name (class FQN or method name) in reports.

### `@Paramixel.Order`

- **Target:** `ElementType.METHOD` (valid on `@Paramixel.Test` and lifecycle hook methods)
- **Attribute:** `int value()` -- MUST be a positive integer (> 0).
- **Effect:** Establishes deterministic execution order within an annotation type. Lower values
  execute first. Methods without `@Paramixel.Order` sort last (effective value = `Integer.MAX_VALUE`).
- **Side effect:** When ANY test method in a class has `@Paramixel.Order`, ALL test methods
  for that argument bucket MUST be executed sequentially (even if argument parallelism > 1).
  This side effect applies to `@Paramixel.Test` methods only; lifecycle hooks are always
  executed in their declared order regardless.
- **Validation errors (`IllegalStateException` at discovery):**
  - `value()` <= 0
  - Applied to a method type that does not support ordering

### `@Paramixel.Tags`

- **Target:** `ElementType.TYPE`
- **Attribute:** `String[] value()` -- required, MUST contain at least one non-null, non-empty tag.
- **Effect:** Categorizes a test class with metadata tags for filtering.
- **Constraints:**
  - MUST only be applied to classes annotated with `@Paramixel.TestClass`.
  - At most one `@Paramixel.Tags` annotation per class (each class in a hierarchy MAY have
    its own `@Paramixel.Tags`).
  - Each tag value MUST be non-null and non-empty after trimming.
- **Inheritance:** Tags are inherited from parent classes and combined with the current
  class's tags. When filtering, a class matches if ANY of its tags (inherited or declared)
  match the filter pattern.
- **Validation errors (`IllegalStateException` at discovery):**
  - Applied to a class not annotated with `@Paramixel.TestClass`
  - Multiple `@Paramixel.Tags` on the same class
  - Empty tags array, or array containing null/blank elements

---

## Method Inheritance and Ordering

### Flattened Inheritance

For `@Paramixel.Initialize`, `@Paramixel.BeforeAll`, `@Paramixel.BeforeEach`,
`@Paramixel.Test`, `@Paramixel.AfterEach`, `@Paramixel.AfterAll`, and `@Paramixel.Finalize`:

1. Methods are discovered across the full class hierarchy and treated as a single flattened
   set per `@Paramixel.TestClass`. Base/subclass location is irrelevant.
2. If the same method signature (name + parameter types) appears in multiple classes in the
   hierarchy for the same annotation, only the most specific (subclass) declaration is used.
3. Results are cached in a `ConcurrentHashMap<LifecycleCacheKey, List<Method>>`.

### Deterministic Ordering

For each annotation type, the flattened method list is ordered:

1. **Primary:** `@Paramixel.Order` value ascending.
2. **Secondary:** Method name ascending (lexicographic).
3. **Unordered last:** Methods without `@Paramixel.Order` have effective value `Integer.MAX_VALUE`.

Ordering is applied **per annotation type** independently:
- All `@Paramixel.BeforeEach` methods are ordered among themselves.
- All `@Paramixel.Test` methods are ordered among themselves.
- All `@Paramixel.AfterEach` methods are ordered among themselves.
- The ordering of one annotation type does not affect another.

### `@Paramixel.ArgumentsCollector` Inheritance

Only ONE `@Paramixel.ArgumentsCollector` method is allowed per class hierarchy. If multiple
exist, the method in the most specific subclass is invoked and parent methods are ignored.

---

## Execution Timing

The engine measures execution duration for each test descriptor independently.

### Timing Measurement Points

Timing starts at `executionStarted` and ends at `executionFinished` for each descriptor:

| Descriptor Level | Start Event | End Event | Stored In |
|---|---|---|---|
| Test Class | `ParamixelTestClassDescriptor` started | `ParamixelTestClassDescriptor` finished | `ExecutionSummary.classDurations` |
| Argument | `ParamixelTestArgumentDescriptor` started | `ParamixelTestArgumentDescriptor` finished | `ExecutionSummary.argumentDurations` |
| Test Method | `ParamixelTestMethodDescriptor` started | `ParamixelTestMethodDescriptor` finished | `ExecutionSummary.methodDurations` |

### Duration Semantics

- **Class duration**: Total time from class execution start to finish, including all
  lifecycle hooks (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, etc.) and all
  argument/test method executions.
- **Argument duration**: Time for one argument bucket execution, including
  `@Paramixel.BeforeAll`, all test method invocations for that argument, and
  `@Paramixel.AfterAll`.
- **Method duration**: Time for a single test method invocation, including
  `@Paramixel.BeforeEach`, the test method itself, and `@Paramixel.AfterEach`.

### Thread Safety

Duration tracking uses thread-safe concurrent maps keyed by descriptor unique IDs.
All timing operations are atomic and safe under parallel execution.

---

## Execution Timing

The engine measures execution duration for each test descriptor independently.

### Timing Measurement Points

Timing starts at `executionStarted` and ends at `executionFinished` for each descriptor:

| Descriptor Level | Start Event | End Event | Stored In |
|---|---|---|---|
| Test Class | `ParamixelTestClassDescriptor` started | `ParamixelTestClassDescriptor` finished | `ExecutionSummary.classDurations` |
| Argument | `ParamixelTestArgumentDescriptor` started | `ParamixelTestArgumentDescriptor` finished | `ExecutionSummary.argumentDurations` |
| Test Method | `ParamixelTestMethodDescriptor` started | `ParamixelTestMethodDescriptor` finished | `ExecutionSummary.methodDurations` |

### Duration Semantics

- **Class duration**: Total time from class execution start to finish, including all
  lifecycle hooks (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, etc.) and all
  argument/test method executions.
- **Argument duration**: Time for one argument bucket execution, including
  `@Paramixel.BeforeAll`, all test method invocations for that argument, and
  `@Paramixel.AfterAll`.
- **Method duration**: Time for a single test method invocation, including
  `@Paramixel.BeforeEach`, the test method itself, and `@Paramixel.AfterEach`.

### Thread Safety

Duration tracking uses thread-safe concurrent maps keyed by descriptor unique IDs.
All timing operations are atomic and safe under parallel execution.
