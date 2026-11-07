# Scheduler Model

Status: descriptive — states what this revision of the scheduler guarantees, what it does not,
and where the implementation deviates. The normative rules are **R1–R5**; everything else is
mechanism or consequence.

Scope: `core/src/main/java/nonapi/org/paramixel/Scheduler.java` and the coordination strategies
in `ActionExecutionStrategies.java`. The model covers descriptor *execution* order and
concurrency only; it says nothing about listeners, reporting, or status aggregation.

---

## 1. Terms

| Term | Meaning |
| --- | --- |
| **Descriptor tree** | The immutable tree built from the user's `Action` tree (`DescriptorBuilder`). One descriptor per action occurrence, plus separate `before` / `after` slots. |
| **N** | Global parallelism: `paramixel.parallelism`, defaulting to `Runtime.availableProcessors()`. Resolved once by `ConcreteRunner`, passed to `Scheduler`. |
| **cap** | A `Parallel` node's declared `parallelism(int)` value — the maximum number of **leaf tasks running anywhere in its subtree**. Code-defined: an ordinary Java expression evaluated when the action is built; immutable thereafter. Default `Integer.MAX_VALUE`, which means "no node-level limit". Caps nest: every capped ancestor's limit applies independently, and a leaf runs only when each capped ancestor has room. |
| **overall cap** | `paramixel.scheduler.concurrency.cap` — the maximum number of leaf tasks running across the whole tree. Independent of the thread pool: effective global leaf concurrency is `min(N, overall cap)`. Default unlimited, so N is the only global bound. |
| **Slot** | Capacity to execute one descriptor at a time. There are exactly N slots, implemented as the N threads of the scheduler's `ThreadPoolExecutor`. The caller's thread is not a slot; it runs the root and may execute stolen work while it waits. |
| **Admitted task** | A descriptor that has been enqueued on the scheduler's priority queue and not yet dequeued. Only admitted work can be scheduled; descendants of a node that has not run yet are **not** admitted. This is what "ready" means throughout this document — it is a property of the queue, not of the whole tree. |
| **Compiled order** | The total order the scheduler dequeues by. `DescriptorOrder` walks a finished tree once — ordered postorder: the `before` subtree, the body children in declaration order, the `after` subtree, then the node itself — and gives each descriptor a **rank**, the lowest rank in its own subtree (`subtreeStart`), and the identity of the compilation that produced them (`treeId`). Rank order *is* the depth-first preference below; comparing two ranks is constant time. |
| **Compilation boundary** | The first scheduler operation that can observe or queue a descriptor of a tree — `schedule` or `executeDescriptor`. Crossing it compiles the tree and freezes it; see §2 (R5) and §4. |
| **In flight** | Admitted or executing, but not yet terminal. |

---

## 2. The model

The five rules below are normative. In short: **R1 ceiling, R2 share, R3 preference under
contention, R4 work conservation, R5 compile-then-execute.**

### R1 — Ceiling

At most **N leaf action bodies execute concurrently**, regardless of tree shape, node caps, or how
many nodes want capacity.

Two mechanisms combine to produce this bound:

- the executor has exactly N threads, so at most N descriptors of any kind run on pool threads;
- a semaphore of N **leaf permits** is held for the duration of every leaf action body.

The root's caller thread is **not counted against N**: `ConcreteRunner` executes the root descriptor
on the caller's thread, and that thread also executes stolen work while it waits in `managedJoin`.
So N+1 threads can be executing descriptors — the caller plus N pool threads. The extra thread can
run coordination work (strategies, continuations) and can also run stolen leaf work, which is
exactly why the permit gate is required: it bounds the total number of executing leaf bodies at N
across all N+1 threads.

### R2 — Cap (running leaves in a subtree)

A `Parallel` node's `cap` bounds **leaf tasks running anywhere in its subtree**, so
`parallelism(1)` on a node serializes every leaf below it, not just its direct children. Caps
compose: a leaf is eligible only when every capped ancestor is below its own limit, and the
tightest limit binds. A ready leaf blocked by a cap is skipped and stays ready — it never blocks a
lower-ranked eligible leaf, and it never occupies a thread.

Readiness is separate from eligibility. A `Parallel` node makes **all** its children ready at once
and never withholds one; only dispatch is gated. Effective cap per node =
`max(1, min(declared cap, N))`. (The `max(1, …)` floor is defensive; builders reject non-positive
values.)

### R2b — Overall cap

`paramixel.scheduler.concurrency.cap` bounds leaf tasks running across the whole tree,
independently of the thread pool size: effective global leaf concurrency is `min(N, overall cap)`.
Whichever is tighter binds, and a leaf blocked by a node cap does not consume an overall slot.

### R3 — Preference (depth-first)

Among **admitted** tasks, dequeue order is by compiled rank, then by submission sequence. Ranks
are assigned by an ordered postorder walk, so a node's descendants always precede the node and an
earlier sibling's entire subtree precedes a later sibling's:

```
root                rank 5
├─ child-0          rank 2     ← its subtree served before child-1's
│   ├─ grandchild-0 rank 0
│   └─ grandchild-1 rank 1
└─ child-1          rank 4
    └─ grandchild-0 rank 3
```

Order: ranks 0…5 — `grandchild-0`, `grandchild-1`, `child-0`, `child-1/grandchild-0`,
`child-1`, root. Within the admitted set, every descendant of an earlier sibling precedes the
later sibling, and a coordination node's own continuation (its own rank) ranks below its own
admitted descendants. (That last ordering is what keeps a node from competing with its own
children for slots.)

Slot composition (`DescriptorOrder`): a `before` subtree is visited first, body children next in
declaration order, and `after` after them — so `before` outranks the body and `after` ranks last.
(`after` is only scheduled once the body completes, so its position never creates an ordering
hazard.)

### R4 — Work conservation

A free slot is given to the highest-priority **admitted** task. No slot is left idle while an
admitted task exists, and a running task is never preempted.

Because only admitted work competes, R3 is a preference **under contention**, not a barrier. If
an earlier sibling has no admitted work at the instant a slot frees — because the node that would
admit its descendants has not run yet — the slot goes to the next admitted task, which may belong
to a later sibling. This is intentional: the scheduler must not idle capacity waiting for work
that does not yet exist.

This is the only place where informal statements of the model can diverge. "An earlier sibling's
ready descendants beat a later sibling's" holds when both are *admitted*. It does not mean the
scheduler reserves capacity for descendants that an in-flight node has not admitted yet; doing so
would be a barrier, and it would contradict R4.

### R5 — Order is compiled once, then executed

The descriptor tree is **accepted as built, compiled to ranks, then executed from the ranks**. The
first `schedule` or `executeDescriptor` on any descriptor of a tree compiles the whole tree — from
its root, so ranks are tree-global — and freezes it. After that boundary the structure is
immutable: adding a child or a `before`/`after` slot throws `IllegalStateException`. Nothing
observable allows a tree to be reshaped between two dequeues, so R3 cannot be invalidated
mid-run.

---

## 3. Consequences

- **C1** An earlier subtree dominates the bound only while its *admitted* set can fill the free
  slots. When it can, later siblings get no slot.
- **C2** When an earlier node's `cap` limits it below `N`, later siblings receive the remaining
  slots.
- **C3** A later sibling may start before an earlier sibling's deeper work exists. There is no
  "finish the earlier subtree first" barrier, and `Parallel(cap=2)` genuinely means "two of my
  children may run concurrently".
- **C4** A subtree that keeps at least N admitted tasks indefinitely can starve every later
  sibling. There is no aging or fairness rule.
- **C5** Dequeue *order* is deterministic for a given admission history (ranks are a fixed
  property of the tree); execution *interleaving* is not, for `N > 1`. With `N = 1`,
  `cap = min(declared, 1) = 1` at every node, so admission is also serialized and every **leaf body**
  runs in rank order. Descriptor *start* order is not rank order, and cannot be: a node is ranked
  after its own subtree, so `A` (rank 8) starts before `a1` (rank 0) because `A` is what admits
  `a1`. Only leaf bodies, and coordinators competing with work that is already admitted, follow
  rank.
- **C6** The root descriptor is executed by the caller's thread (`ConcreteRunner.run`), not
  taken from the queue, and is not counted against N. That thread participates in execution —
  `managedJoin` steals queued work while it waits — so N+1 threads may be active, with leaf
  bodies still capped at N by the permit gate (R1).

---

## 4. Mechanism

**Priority queue.** `ThreadPoolExecutor(N, N, …, new PriorityBlockingQueue<>(queueCapacity,
PRIORITY_COMPARATOR))`. The comparator orders `(treeId, rank, sequence)`: ranks are tree-local, so
the compilation id must match before two ranks may be compared. `sequence` is a global
`AtomicLong` incremented per submission, so equal ranks — multiple continuations of the same node
— are ordered by submission.

**Order compilation.** `DescriptorOrder.compile` is the single gate, called from
`prepareScheduledTask` (admission) and from `executeDescriptorWithCompletion` (a root that never
passes admission). It returns immediately if the descriptor already has a rank; otherwise it
resolves the tree root, freezes it, and performs one iterative postorder walk writing
`(treeId, subtreeStart, rank)` on each descriptor. `rank` is published last and every compiled
rank is non-negative, so `rank == -1` is one volatile read meaning "not compiled". Compilation
runs under the root's monitor, so two threads scheduling the same tree compile once.

Ancestry (`isSameOrDescendant`, used by isolation deferral and inline-continuation nesting) is the
constant-time interval test `subtreeStart <= rank <= ancestor.rank` **only** when both ends share a
compilation id; otherwise it walks the parent chain, so a defaulted rank is never read as real.

**Task kinds.** `PrioritizedTask` (a scheduled descriptor) captures its descriptor's
`(treeId, rank)` at submission. `ContinuationTask` and `AdmissionWakeupTask` capture the same pair
from their node, i.e. the node's own rank.

**Admission.** Each coordination strategy owns a cursor over its children and admits them one at
a time, in child order, while `runningChildren < cap`. `Parallel` re-admits on every child
completion until its window is full. Admission requires a `queuePermits` reservation
(`queueCapacity`, default 1024). If no reservation is available, an internal admission returns
`DEFERRED`, the parent rolls its cursor back, and it retries when capacity is signalled — this
path **preserves** order because the parent resumes from its own cursor.

**Continuations.** The strategy of a coordination node does not block on its children: it is
continuation-driven, and it holds no thread between advances. The *descriptor's* executing thread
is a different matter — once its action returns `RUNNING` it parks in `managedJoin(nodeCompletion)`
until the node is finalized, so that `onAfterExecution` fires on the same thread as
`onBeforeExecution`. That park is work-stealing (`stealEligibleReadyTask`), so it costs the pool no
capacity; it is not a release of the thread. Expect roughly one parked joiner per simultaneously
live coordination node. Each child completion advances the parent: when the completing thread is a
scheduler worker, the continuation runs **inline** on that thread, bounded by a nesting depth of
16; otherwise (delay thread, caller
thread, shutdown) it is enqueued as a `ContinuationTask`. Inline advancement saves a queue
submission and a hand-off per child and does not affect ordering, because a continuation only
admits work and admitted work still competes by rank. This is why "deeper first" matters: it
keeps a node from competing with its own children for slots.

**Inline leaf execution.** When a sequential, lifecycle, or iteration strategy advances to a
**leaf** child in `RUN` mode, the child is executed on the calling thread instead of being
submitted, provided nothing already queued outranks it (the ready queue is a min-heap, so peeking
the head answers that exactly) and inline nesting is within the depth limit. Outranking is decided
by rank within one compilation; when the head belongs to a different compilation, or the child has
no rank yet, the child is queued instead. Parallel windows and `Timeout` bodies are excluded
deliberately: inlining a fan-out burst would serialize it, and inlining a timeout body would run it
before its deadline is armed. This removes the thread hand-off for the common "chain of leaves"
shape; measured cost is roughly 2.6 µs per trivial step with it in place.

**Waiting.** `Isolated` and `Conditional` are the two synchronous wrappers: they block a worker in
`managedJoin`, which work-steals queued tasks while it waits, parking with multiplicative backoff
(1 µs floor, 1 s ceiling; 10 ms ceiling when the thread holds isolation frames) so a blocked
worker still makes progress without spinning.

A parked thread registers itself as a waiter; enqueueing work or completing a node unparks every
registered waiter. The backoff therefore only bounds genuinely idle periods, not hand-off latency:
without the wakeup a waiter would sleep through the arrival of the work it is waiting for, for up
to the backoff ceiling.

**Leaf permits.** `leafPermits` is a second gate of size N, acquired before a leaf action body runs
and released when it returns. It is **load-bearing, not redundant with the thread count**: the
caller thread that runs the root also executes stolen work while parked in `managedJoin`
(`stealEligibleReadyTask` → `runQueuedTask`), so N+1 threads can attempt leaf execution. The
semaphore is what keeps user action bodies at N. It also gates `runningLeafCounter`
(observability) and has permits released during `close()`.

Because a permit is acquired *after* the task is dequeued, permits are **not** rank ordered: a
later-rank leaf can hold a permit while an earlier-rank leaf waits. `leafPermits` (and
`queuePermits`) are **non-fair** semaphores, so there is no arrival-order guarantee either — a
thread that dequeues while a permit is being released may barge ahead of an already-parked waiter.
In practice the skew is small, because a thread can only dequeue a leaf once nothing better is
queued: while an earlier subtree still has admitted work, the barger is executing that subtree's
own work. R3 is a **dequeue-order** guarantee, not a permit-grant guarantee. Making the semaphores
fair would tighten this to arrival order, at the cost of a lock hand-off on the hottest path (every
leaf body acquires and releases a permit); that trade has not been taken, and no benchmark here
quantifies it.

**Delays.** `Delay` and inter-iteration loop delays are scheduled on a single-thread
`ScheduledExecutorService`; when they fire they re-enter the priority queue with the node's rank,
so ordering is preserved across the wait.

---

## 5. Ordering outside the ready queue

Every queue that can hold undispatched work uses the same total order as the ready queue —
`TASK_ORDER`: compiled rank (within one compilation), then submission sequence:

- `IsolationState.deferredTasks` and `IsolationState.ownerTasks` — same-lock work deferred behind
  the current lock owner;
- `parkedReadyTasks` — tasks relinquished by a lock owner to make room for its own subtree.

Consequently **R3 holds inside and around `Isolated`**, not only on the ready queue. When the owner
releases the lock, the lowest-rank deferred task is dispatched next: the owner's own descendants
(deeper ranks) are considered before unrelated work, and among unrelated waiters the earlier
sibling wins regardless of arrival order. This is pinned by
`SchedulerModelConformanceTest.isolationDefersInKeyOrderNotArrivalOrder`, which forces the
later sibling to reach the lock first and still expects the earlier sibling to run first.

The one remaining exception is shutdown: once `close()` begins, continuations and admission
retries move to FIFO `shutdownDrain` / `admissionDrain` queues and are processed in arrival order.
The window is bounded by shutdown, and no new work is admitted during it.

---

## 6. Configuration

| Key / API | Default | Effect |
| --- | --- | --- |
| `paramixel.parallelism` | `availableProcessors()` | N — the global ceiling |
| `paramixel.scheduler.queue.capacity` | `1024` | Maximum admitted tasks holding a reservation (admission backpressure); also the initial size of the priority queue. Does **not** bound concurrency — see R2. |
| `paramixel.scheduler.concurrency.cap` | unlimited | Overall cap on concurrently running leaf tasks, independent of the thread pool |
| `Parallel.Builder.parallelism(int)` | `Integer.MAX_VALUE` | Per-node cap on running leaf tasks in that subtree (nested caps compose); no cap when unset |
| `Each.parallel(...)` | unset | Inherits N |

Invalid values (non-positive) are rejected with `ConfigurationException`.

---

## 7. What is verified

`core/src/test/java/nonapi/org/paramixel/SchedulerPriorityContractTest.java` (deterministic —
latch-driven, no wall-clock races):

| Test | Pins |
| --- | --- |
| `earlierSubtreeWinsWhenGlobalBoundIsContended` | R3 with depth — the later sibling's descendants are submitted first and still lose the slots to the earlier sibling's; also distinguishes depth-first from level-order |
| `dequeueOrderFollowsStructuralKeyNotSubmissionOrder` | R3 against arrival order on a single worker |
| `cappedSiblingLeavesSlotsForLaterSiblings` | R2 + R4 — a capped earlier sibling yields the remaining slot |
| `singleWorkerExecutesDepthFirst` | R3 under N = 1, exact order across nested subtrees |

Existing coverage in `SchedulerAlgorithmTest`: global bound (R1), per-node cap (R2), backfill by
later siblings (`lowerPriorityWorkUsesSlotsLeftByHigherPriorityLimits`), and top-level throttle.

`core/src/test/java/nonapi/org/paramixel/SchedulerModelConformanceTest.java` verifies the rules
whose implementation is non-obvious, together with the ordering guarantees of §5:

| Test | Pins |
| --- | --- |
| `leafBodiesStayAtParallelismWithCallerThreadPresent` | R1 with the caller thread participating — leaf bodies stay at N |
| `declaredCapAboveParallelismIsClamped` | R2 — a declared cap above N is clamped to N |
| `queueCapacityDoesNotReduceEffectiveCap` | R2 — the same tree reaches concurrency 4 with queue capacity 2 and 32 |
| `isolationDefersInKeyOrderNotArrivalOrder` | R3 — deferred work is dispatched in rank order even when it arrived in reverse order |

`core/src/test/java/nonapi/org/paramixel/action/DescriptorOrderTest.java` pins the compilation
itself against an independent oracle:

| Test | Pins |
| --- | --- |
| `compiledRankOrderMatchesStructuralPathOrder` | rank order equals a path-comparison walk re-implemented in the test, over shapes that vary `before`, children, and `after` |
| `everyRankInATreeIsUnique` | no two descriptors share a rank |
| `subtreeStartIsLowestRankInSubtree` | `subtreeStart` is the lowest rank in its own subtree, which is what makes the interval test exact |
| `ancestryAgreesWithParentWalkWithinAndAcrossCompiledTrees` | `isSameOrDescendant` agrees with an exact parent walk for every ordered pair, including pairs from two separately compiled trees |
| `uncompiledDescriptorIsAnsweredByParentWalk` | an uncompiled or `null` descriptor falls back to the walk |
| `compilingTwiceIsIdempotentAndKeepsRanks` | recompiling changes no rank |
| `deepChainCompilesWithoutAddingRecursionDepth` | the ranking walk is iterative — a 2 000-deep chain compiles; the depth bound comes from the pre-existing recursive `freeze`, not from ranking |
| `compileRejectsNullDescriptor` | `null` is rejected |

`core/src/test/java/nonapi/org/paramixel/SchedulerOrderCompilationTest.java` pins R5:

| Test | Pins |
| --- | --- |
| `schedulingMidTreeDescriptorCompilesAndFreezesWholeTree` | scheduling a descriptor in the middle of a tree compiles the whole tree, not just the scheduled subtree |
| `executingUnscheduledDescriptorCompilesTree` | `executeDescriptor` compiles too, so a root that bypasses admission still has ranks |
| `structuralMutationAfterCompilationBoundaryIsRejected` | `addChild` / `setAfter` throw `IllegalStateException("Descriptor is frozen: …")` after the boundary |

`SchedulerInlineContinuationTest` and `SchedulerWaiterWakeupTest` cover the hand-off mechanisms
described in §4: inline continuation and inline leaf execution are proven to be taken (via
internal counters), to preserve sequential ordering and skip propagation over 1 000 children, and
to leave parallel windows unserialized; waiter wakeups are proven to be broadcast when
coordination nodes complete.

The `SchedulerBenchmark` shapes `runSequentialChain` and `runNestedTree` drive the scheduler
directly (`DescriptorBuilder` + `Scheduler` + `ConcreteContext`) rather than through
`Runner.run(action)`, because a runner invocation performs a classpath scan for hooks before every
run — milliseconds of work that would swamp scheduling cost. The other benchmarks in that class
still go through `Runner`, so their scores are discovery-dominated and should not be compared with
these.

**Not covered by tests:** starvation behaviour (C4) and the absence of preemption (R4). The
shutdown exception in §5 is also untested. The failure path of `scheduleChild` — a child whose
scheduling throws, which terminalizes the child and advances the parent
(`ActionExecutionStrategies.terminalizeUnscheduledChild`) — is unreachable through the supported
API, because rejections are returned as `ChildAdmissionResult` values rather than thrown, so it is
reviewed but not exercised. No benchmark compares the compiled order against the
previous per-comparison path walk: the performance claim for R5 is that dequeue comparison became
constant time, not that end-to-end runtime improved measurably.

---

## 8. Open decisions

1. **Strict precedence vs work conservation.** The model is work-conserving (R4). A strict
   "finish the earlier subtree before starting a later sibling" rule is a different contract and
   would require gating admission on subtree completion, sacrificing parallelism. Current
   behaviour is the work-conserving reading.
2. **Fairness.** C4 allows indefinite starvation of later siblings. No aging rule exists; adding
   one would make dequeue order depend on elapsed time and weaken C5.
3. **User-facing priority.** Scheduling priority is purely structural (declaration order).
   `@Paramixel.Priority` affects discovery ordering only and does not influence the scheduler.
4. **Resolved:** isolation deques and relinquished-task parking are rank-ordered, and
   `queueCapacity` no longer participates in the effective-cap expression.
5. **Resolved:** the tree is accepted, compiled to scalar ranks, then executed (R5). There is no
   compatibility path for structural mutation after scheduling: `SchedulerPriorityKey` and the
   per-comparison path walk are gone, and a post-boundary mutation is an error rather than a
   silently mis-ordered run.
