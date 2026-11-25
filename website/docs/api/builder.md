---
title: TaskBuilder
description: The single-slot authoring surface for before, step, and after.
---

# TaskBuilder

```java
public abstract class TaskBuilder<S extends TaskBuilder<S>> {
    public S before(String id);
    public S before(Task task);
    public S before(TaskBuilder<?> builder);
    public S step(String id);
    public S step(Task task);
    public S step(TaskBuilder<?> builder);
    public S after(String id);
    public S after(Task task);
    public S after(TaskBuilder<?> builder);
    public S body(String id);
    public S onlyIf(String id);

    public S bind(String name, Object value);

    public S shuffle();
    public S shuffle(long seed);
    public S tags(String... values);
    public S timeout(Duration timeout);
    public S retry(RetryPolicy policy);
    public S isolated(String lockName);
}
```

`build()` is declared by each concrete builder and returns that builder's node type: `SequentialBuilder.build()` returns
`Sequential`, `ParallelBuilder.build()` returns `Parallel`, and `RepeatBuilder.build()` returns `Repeat`.

Every hook is an ordinary `public static` method named by a stable identifier, and the tree refers to it by that
identifier. There is no functional-interface overload and no generic instance type.

## The node grammar

Every node is exactly:

```
bind*  →  before?  →  step+  →  after?
```

- `bind` declares a value that **already exists when the plan is built**. It may be called any number of times, never
  accepts a supplier or lambda, and a duplicate name on one node throws `IllegalArgumentException`.
- `before` and `after` are **single slots**. Each takes an annotation id, a `Task`, or a `TaskBuilder`; a second call
  throws `IllegalStateException` at the call site.
- `step` adds children in declaration order and takes the same three argument kinds.

```java
Sequential.builder(ScenarioTest.class, "ScenarioTest")
        .bind(ENVIRONMENT, environment)
        .before(ID_START)
        .step(Sequential.builder("checks").step(ID_HEALTH))
        .after(ID_STOP);
```

The class literal appears **once per definition root**; everything attached below it inherits it. Identifiers resolve
only against methods declared by that class — inheritance is not consulted — and they are also the labels reported for
the hooks (`before[start]`, `body[health]`).

Hook IDs have a maximum of 64 UTF-16 code units across all named builder methods. An overlong reference throws
`IllegalArgumentException` at the builder call. The display-name limit for tasks is 512 UTF-16 code units.

| Builder method | Public static hook signature |
| --- | --- |
| `before(id)`, `step(id)`, `body(id)`, `after(id)` | `void hook(Context)`, optionally with checked exceptions |
| `onlyIf(id)` | `boolean hook(Context)` or `Boolean hook(Context)` |
| `RepeatBuilder.until(id)` | `boolean hook(Context, Status)` or `Boolean hook(Context, Status)` |

Predicate hooks can declare unchecked exceptions. A boxed `null` result is treated as `false`. In a named Java module,
export the package of a public anchor to `org.paramixel.api` or open it to that module. A non-public anchor requires
an open package. The builder checks access when it resolves the hook.

## Composing several setup or cleanup phases

Because each slot holds one thing, several phases are composed into a subtree rather than repeated:

```java
// two setup phases, in order
.before(Sequential.builder("setup").step(ID_START_DB).step(ID_START_APP))

// two cleanup phases, keeping reverse order and run-all behavior
.after(Sequential.builder("cleanup").independent().step(ID_STOP_APP).step(ID_STOP_DB))
```

A `Sequential` cleanup subtree is `dependent()` by default, so a failing cleanup step skips the later ones;
`independent()` is how a cleanup that must always finish declares itself.

## Bound values versus runtime state

`bind` and `put` are deliberately different:

| | `bind(name, value)` | `put(name, value)` |
| --- | --- | --- |
| When the value exists | Before the plan is built | While the test runs |
| Where it is declared | On the node, visible to its subtree | In the scope of the occurrence that writes it |
| Reported | Yes, as a plan parameter | Never |
| Accepts a lambda or supplier | No | Not applicable: the value is passed in already created |

A value that only exists while the test runs — a started container, a client, a temporary directory — is created in the
`before` slot and stored with `put`:

```java
Sequential.builder(MyTest.class, "scenario")
        .bind(ENVIRONMENT, environment)
        .before(ID_SET_UP)
        .step(ID_CHECK)
        .after(ID_TEAR_DOWN);

@Paramixel.Id(ID_SET_UP)
public static void setUp(final Context context) throws Exception {
    final var client = Client.start(context.require(ENVIRONMENT, Environment.class));
    context.put(CLIENT, client);
}
```

## Builder rules

- Identifiers are resolved when the definition is built, before any hook runs; every problem in one definition is
  reported together as a `CompilationException`.
- A builder passed to `before`, `step`, or `after` is **held**, not built: it is materialized when the parent is built,
  so a change made to it after it was passed in still takes effect.
- Passing the same builder instance more than once anywhere in one tree is an authoring error reported at build time.
  Pass a built `Task` twice when the same subtree must appear in two places; each placement compiles to its own
  occurrence.
- A cycle through a step, before slot, or after slot is reported with its builder path in a `CompilationException`.
- `bind` places a value in the node's scope before its `before` slot runs, so the slot, the steps, the `after` slot, and
  every descendant see it. A descendant that binds the same name shadows it inside its own subtree only.
- `build()` returns a deep snapshot, may be called repeatedly, and performs no user work.

## Compile-time guidance

- `.independent()` exists only on `SequentialBuilder`.
- `Parallel.builder(name, Pool.named("x"))` requires a `Pool`; a second `String` does not compile.
- `child(...)` and `children(...)` do not exist: children are added with `step(...)`.
- A lambda body does not compile as a `before`, `step`, or `after` argument: the authoring surface takes an id, a
  `Task`, or a `TaskBuilder`.
- An identifier used in a definition with no anchor is rejected when the definition is built.

## Next

- [Named builders](../api/named-builders) — `Sequential`, `Parallel`, and repetition views.
- [Result](../api/result) — what a run produces.
