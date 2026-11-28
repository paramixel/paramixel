# Contributing to Paramixel

Thank you for your interest in contributing to Paramixel! This document provides guidelines for contributing to the project.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## How to Contribute

### Reporting Bugs

If you find a bug, please create an issue with:
- A clear title describing the problem
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- Java version, OS, and other relevant environment details

### Suggesting Features

We welcome feature suggestions! Please create an issue with:
- A clear title describing the feature
- A detailed description of the proposed feature
- Use cases and benefits
- Any potential drawbacks or alternatives considered

### Submitting Pull Requests

1. Fork the repository
2. Create a feature branch from `main`:
   ```bash
   git checkout -b "feature/your-feature-name"
   ```
3. Make your changes
4. Run tests to ensure all tests pass:
    ```bash
    ./mvnw clean install
    ```
5. Commit your changes with a clear commit message
6. Push to your fork and submit a pull request

## Development Setup

### Prerequisites

- Java 17 or higher
- Maven 3.9+

### Building the Project

```bash
# Clone the repository
git clone https://github.com/paramixel/paramixel.git
cd paramixel

# Build and install
./mvnw clean install
```

The build runs the examples against real containers, so Docker must be running. Expect two kinds of output that look
worse than they are:

- **`[PARAMIXEL] FAIL` lines** come from examples and plugin fixtures that deliberately exercise failure paths (a
  retry policy's failed attempts, a factory that throws). The build result is reported by the `[PARAMIXEL] result:`
  line and the Maven reactor summary, not by individual attempts.
- **`WARNING:` lines from the JDK** originate in build tools, not in this code: Maven's Jansi library on JDK 24+ is
  handled by `.mvn/jvm.config`, while Spotless' use of `sun.misc.Unsafe` and PMD's "Adding current platform … to
  auxClasspath" notice remain. They are silenced with
  `MAVEN_OPTS="--sun-misc-unsafe-memory-access=allow"` (JDK 24+) and `PMD_JAVA_DISABLE_AUX_CLASSPATH_WARNINGS=true`
  respectively.

### Project Structure

- `core/` - Main library (public API + implementation)
- `maven-plugin/` - Maven plugin for test execution
- `examples/` - Test classes and examples using Paramixel's own framework
- `benchmarks/` - JMH benchmarks for compilation and scheduling (see [BENCHMARKS.md](BENCHMARKS.md))
- `assets/` - License header template and shared resources
- `scripts/` - Release and documentation automation
- `website/` - Docusaurus documentation site

### Documentation

The Docusaurus site lives in `website/`. Building it requires Node.js 20 or newer, `npm`, `jq`, and `awk`.

```bash
./scripts/build-documentation.sh    # validate sidebars, frontmatter, and links, then build
./scripts/serve-documentation.sh    # build and serve the site locally
```

Edit active documentation in `website/docs/`. `website/versioned_docs/`, `website/versioned_sidebars/`, and
`website/versions.json` are release snapshots: do not edit them when updating the unreleased documentation.

## Coding Standards

### Javadoc

Public API members carry Javadoc with `@param`, `@return`, and `@throws` where they apply. The Maven build enforces this:
`maven-javadoc-plugin` runs with `doclint=all` and `-Werror`, so a missing or malformed tag fails
`./mvnw clean verify`.

### Code Formatting

All code must be formatted before committing. The project uses Spotless with Palantir Java Format:

```bash
# Format code (required before committing)
./mvnw spotless:apply

# Check formatting
./mvnw spotless:check
```

### Commit Messages

This project uses conventional commit prefixes:

| Prefix | Usage |
|--------|-------|
| `feature:` | New features |
| `fix:` | Bug fixes |
| `refactor:` | Code refactoring |
| `chore:` | Maintenance tasks |
| `performance:` | Performance improvements |
| `polish:` | Minor improvements |

Dependency updates use a scoped prefix: `chore(deps):` or `fix(deps):`.

### Sign-off

All commits must be signed off to satisfy the [Developer Certificate of Origin (DCO)](DCO.md). Use `git commit -s` to add the sign-off line automatically.

### Key Guidelines

- Follow existing code style and patterns
- Write clear, self-documenting code with meaningful variable names
- Add Javadoc for public APIs; the build fails on a Javadoc warning
- Keep methods focused and reasonably sized
- Write unit tests for new functionality

## Testing

### Running Tests

```bash
# Run all tests (requires Docker for the examples module)
./mvnw test

# Build, tests, Javadoc, formatting, and PMD checks
./mvnw verify

# Build without running Paramixel tests
./mvnw clean install -Dparamixel.skipTests
```

### Writing Tests

- Place tests in the `examples/` module under `src/main/java/`
- Use the `@Paramixel.TaskFactory` annotation on static methods that return a `Task` or a `TaskBuilder`
- Use descriptive test names and argument names
- Test edge cases and error conditions

## Releasing (Maintainers)

See [RELEASING.md](RELEASING.md).

## Review Process

Pull requests are reviewed by maintainers. To speed up the review process:

1. Ensure all CI checks pass
2. Write clear descriptions of changes
3. Reference related issues
4. Keep changes focused and atomic
5. Be responsive to feedback

## License

By contributing to Paramixel, you agree that your contributions will be licensed under the Apache License 2.0.

## Questions?

Feel free to open an issue for questions about contributing or the codebase.

---

Copyright (c) 2026-present Douglas Hoard
