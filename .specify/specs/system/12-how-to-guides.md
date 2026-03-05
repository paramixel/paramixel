# Paramixel -- How-To Guides

Step-by-step guides for common development tasks. Each guide assumes familiarity with
the relevant specs (referenced inline).

---

## Add a New API Interface

1. Create `org.paramixel.api.MyNewInterface.java` in `api/src/main/java/org/paramixel/api/`.
2. Add Apache license header from `assets/license-header.txt`.
3. Add full Javadoc with `@author` and `@since` (see `09-conventions.md` section 6).
4. Add `@NonNull` to all non-null reference-type parameters.
5. Create a concrete implementation in
   `engine/src/main/java/org/paramixel/engine/api/ConcreteMyNew.java`.
6. Write a unit test in
   `engine/src/test/java/org/paramixel/engine/api/ConcreteMyNewTest.java`.
7. Update `03-domain-model.md` with the new interface definition.
8. Update `04-lifecycle.md` if the interface participates in lifecycle.
9. Run `./mvnw verify -pl api,engine` and fix all errors.

---

## Add a New Annotation

1. Add nested `@interface` inside `org.paramixel.api.Paramixel` following existing patterns.
2. Set `@Retention(RetentionPolicy.RUNTIME)`, `@Documented`, and appropriate `@Target`.
3. Add full Javadoc including signature requirements and lifecycle ordering. Include `@author`
   and `@since`.
4. If the annotation applies to methods, add validation logic to
   `MethodValidator.validateTestClass()`.
5. Add corresponding lifecycle hook in `ParamixelClassRunner` or `ParamixelInvocationRunner`.
6. Add a functional test in `paramixel-tests/src/test/java/test/`.
7. Update `03-domain-model.md` annotation table and `04-lifecycle.md`.
8. Run `./mvnw verify` and fix all errors.

---

## Add a New Engine Execution Feature

1. Identify the correct sub-package (`execution`, `invoker`, `listener`, etc.).
2. Create the class with `final` modifier if not designed for extension.
3. Use constructor injection for all dependencies.
4. Declare a JUL logger: `Logger.getLogger(MyClass.class.getName())`.
5. Annotate all public reference-type parameters with `@NonNull`; validate in constructor.
6. Write a unit test in `engine/src/test/java/org/paramixel/engine/<package>/MyClassTest.java`.
7. Wire the new class in `ParamixelTestEngine.execute()` if it joins the main flow.
8. Run `./mvnw verify -pl engine` and fix all errors.

---

## Add a New `@Paramixel.TestClass` Test

Target: `paramixel-tests` or `paramixel-examples`

1. Create a new Java class in the appropriate package under `src/test/java/`.
2. Annotate with `@Paramixel.TestClass`.
3. Add `@Paramixel.ArgumentsCollector` if parameterized; otherwise the engine provides a
   single null argument.
4. Add `@Paramixel.Test` methods with signature `public void name(ArgumentContext context)`.
5. Add lifecycle methods as needed with correct signatures per `04-lifecycle.md`.
6. Do NOT use JUnit Jupiter annotations.
7. Run `./mvnw test -pl tests` (or `examples`) and verify the test passes.

---

## Add a New Maven Module

1. Create `<moduleName>/pom.xml` with `<parent>` pointing to `paramixel-parent`.
2. Add `<module>moduleName</module>` to the root `pom.xml`.
3. Add Spotless plugin configuration with the license header file path.
4. Add `maven-enforcer-plugin` if the module needs enforcer rules.
5. Declare only dependencies that the module directly uses.
6. Update `01-overview.md` module inventory table.
7. Update `11-modules.md` with a new module section including constraints.
8. Run `./mvnw verify` from root and fix all errors.
