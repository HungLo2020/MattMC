# Contributing to MattMC

Thank you for your interest in contributing to MattMC!

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally: `git clone https://github.com/YOUR_USERNAME/MattMC.git`
3. **Set up the development environment** (see below)
4. **Create a branch** for your changes: `git checkout -b feature/my-feature`

## Development Environment Setup

### Prerequisites
- Java 21 or higher (Java 21 LTS recommended)
- Git
- 8GB+ RAM recommended

### Initial Setup

```bash
# Clone the repository
git clone https://github.com/HungLo2020/MattMC.git
cd MattMC

# Download the bundled JDK (optional but recommended)
./libraries/download_jdk.sh

# Build the project
./gradlew build

# Run the client
./gradlew runClient

# Or use the dev script
./DevUtils/RunDev.sh
```

## Building the Project

```bash
# Clean build
./gradlew clean build

# Build without tests
./gradlew build -x test

# Run tests only
./gradlew test
```

## Coding Standards

- Follow existing code style and conventions in the codebase
- Write clear commit messages describing what changed and why
- Add comments for complex logic
- Ensure your code compiles without errors or warnings
- Run tests before submitting a pull request

## Testing

- Run the full test suite: `./gradlew test`
- Run specific tests: `./gradlew test --tests "ClassName.methodName"`
- See [Testing Documentation](testing/HOWTO-TESTING.md) for details

## Submitting Changes

1. **Commit your changes** with clear, descriptive commit messages
2. **Push to your fork**: `git push origin feature/my-feature`
3. **Create a Pull Request** on GitHub
4. Wait for review and address any feedback

## Pull Request Guidelines

- Provide a clear description of the changes
- Reference any related issues
- Ensure all tests pass
- Keep changes focused and minimal
- Update documentation if needed

## Code Review Process

All contributions go through code review. Reviewers will check for:
- Code quality and style
- Correctness and completeness
- Test coverage
- Documentation updates
- Performance implications

## Questions?

If you have questions about contributing, please:
- Check existing documentation in the `docs/` directory
- Review closed issues and pull requests
- Open a new issue with your question

## License

By contributing to MattMC, you agree that your contributions will be licensed under the same license as the project.
