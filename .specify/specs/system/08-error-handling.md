# Paramixel -- Error Handling

## Principles

1. **Console Visibility:** All exceptions MUST be printed to console output.
2. **Lifecycle Pairing:** "After" hooks MUST only run if their paired "before" hook executed.
   See `04-lifecycle.md` for pairing rules.
3. **Guaranteed Cleanup:** Paired "after" hooks MUST always execute despite failures in
   preceding steps.
4. **First Failure Wins:** The first exception recorded via `classContext.recordFailure(t)`
   determines the reported cause. Subsequent failures MUST be logged but MUST NOT replace
   the recorded cause.
5. **Fail Fast (before hooks):** `@Paramixel.Initialize`, `@Paramixel.BeforeAll`, and
   `@Paramixel.BeforeEach` abort the current scope on first failure.
6. **Continue (after hooks):** `@Paramixel.AfterEach`, `@Paramixel.AfterAll`, and
   `@Paramixel.Finalize` failures are recorded but remaining hooks in the same phase
   continue executing.
7. **Exception Unwrapping:** All exceptions from reflective invocations MUST be unwrapped
   from `InvocationTargetException` by `ParamixelReflectionInvoker`
   (`e.getCause() != null ? e.getCause() : e`).
8. **Qualified Annotation Names:** All error messages that mention a Paramixel annotation
   MUST use the qualified form (`@Paramixel.BeforeAll`, not `@BeforeAll`).

## Discovery-Time Errors

Discovery errors are fail-fast. The engine MUST fail immediately on the first validation
error.

## Configuration Errors

Configuration errors are fail-fast.

| Condition | Result |
|---|---|
| `paramixel.properties` exists but cannot be loaded | `ConfigurationException`; test execution aborts |
| Provided configuration value is invalid after normalization | `ConfigurationException`; test execution aborts |

**Standardized message:** All configuration failures MUST use a single-line standardized error
message starting with `Invalid configuration:` and MUST include the key name.

**Normalization:** Values MUST be normalized as defined in `06-engine-internals.md` (trim, then
Unicode unescape, no re-trim).

| Condition | Result |
|---|---|
| Method fails signature validation | `IllegalStateException`; test execution aborts |
| `@Paramixel.Order(value <= 0)` | `IllegalStateException`; test execution aborts |
| `@Paramixel.Order` on unsupported method type | `IllegalStateException`; test execution aborts |
| `@Paramixel.ArgumentsCollector` invocation throws | Exception printed; test execution aborts |
| Class cannot be loaded (`ClassNotFoundException`) | Exception printed; test execution aborts |
| Invalid tag filter regex pattern | `ConfigurationException`; test execution fails before discovery |

**Error Message Format:** All validation errors MUST include the class name, validation
failure details, and qualified annotation names.

## Execution-Time Errors

| Condition | Result |
|---|---|
| Test class instantiation fails | All test methods marked FAILED; no lifecycle hooks execute; exception printed |
| `@Paramixel.Initialize` throws | Class execution aborts; `@Paramixel.Finalize` runs; class marked FAILED |
| `@Paramixel.BeforeAll` throws | Argument bucket skipped; `@Paramixel.AfterAll` runs; failure recorded |
| `@Paramixel.BeforeEach` throws | Test invocation marked FAILED; `@Paramixel.AfterEach` runs |
| `@Paramixel.Test` throws | Test invocation marked FAILED; `@Paramixel.AfterEach` runs |
| `@Paramixel.AfterEach` throws | Remaining test methods for same argument bucket aborted; `@Paramixel.AfterAll` runs; class marked FAILED |
| `@Paramixel.AfterAll` throws | Remaining `@Paramixel.AfterAll` methods still execute; `@Paramixel.Finalize` runs; class marked FAILED |
| `@Paramixel.Finalize` throws | Remaining `@Paramixel.Finalize` methods still execute; class marked FAILED |
| `AutoCloseable.close()` throws | Exception printed; class marked FAILED |

## Exception Handling by Scope

| Scope | Policy |
|---|---|
| `@Paramixel.Initialize` / `@Paramixel.BeforeAll` / `@Paramixel.BeforeEach` failure | Log + abort current scope; record via `classContext.recordFailure(t)` |
| `@Paramixel.Test` failure | Log + count as test failure; `@Paramixel.AfterEach` still executes |
| `@Paramixel.AfterEach` / `@Paramixel.AfterAll` / `@Paramixel.Finalize` failure | Log + record; execution of remaining hooks continues |
| `AutoCloseable.close()` failure | Log + record; execution continues |
| Reflection (`InvocationTargetException`) | Always unwrap via `ParamixelReflectionInvoker` |
| Discovery validation failure | Throws `IllegalStateException` |

## Maven Plugin Errors

See `07-maven-plugin.md` for the complete Maven plugin error contract.
