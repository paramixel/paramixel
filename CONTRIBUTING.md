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

# Build and run all tests
./mvnw clean verify
```

### Project Structure

- `core/` - Main library (public API + implementation)
- `maven-plugin/` - Maven plugin for test execution
- `examples/` - Test classes and examples using Paramixel's own framework
- `assets/` - License header template and shared resources
- `website/` - Docusaurus documentation site

## Coding Standards

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
| `polish:` | Minor improvements |

Dependency updates use a scoped prefix: `chore(deps):` or `fix(deps):`.

### Sign-off

All commits must be signed off to satisfy the [Developer Certificate of Origin (DCO)](DCO.md). Use `git commit -s` to add the sign-off line automatically.

### Key Guidelines

- Follow existing code style and patterns
- Write clear, self-documenting code with meaningful variable names
- Add Javadoc for public APIs
- Keep methods focused and reasonably sized
- Write unit tests for new functionality

## Testing

### Running Tests

```bash
# Run all tests
./mvnw test

# Build + SpotBugs + PMD analysis
./mvnw verify

# Build without Paramixel tests
./mvnw clean install -Dparamixel.skipTests
```

### Writing Tests

- Place tests in the `examples/` module under `src/main/java/`
- Use the `@Paramixel.ActionFactory` annotation on static methods that return `Action` trees
- Use descriptive test names and argument names
- Test edge cases and error conditions

## Releasing (Maintainers)

See `RELEASING.md`.

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

Copyright 2026-present Douglas Hoard
