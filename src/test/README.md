# MattMC Test Directory

This directory contains all tests for the MattMC project.

## Structure

- **`misc/`** - Unit tests and integration tests (pass/fail tests)
- **`performance/`** - Performance benchmarks using JMH
- **`resources/`** - Test-specific resources (test data, fixtures, configs)

## Quick Start

### Running Tests

```bash
# Run all unit/integration tests (excludes performance tests)
./gradlew test

# Run performance benchmarks
./gradlew performanceTest

# Run all tests
./gradlew testAll
```

## Documentation

For complete documentation on the testing infrastructure, see:
**[docs/HOWTO-TESTING.md](../docs/HOWTO-TESTING.md)**

This includes:
- How to write tests
- Testing frameworks and libraries used
- Best practices
- IDE integration
- Troubleshooting

## Example Tests

- **Unit Test Example**: `misc/net/minecraft/util/MthTest.java`
- **Performance Test Example**: `performance/net/minecraft/world/BlockPosBenchmark.java`

## Test Isolation

Tests are completely isolated from production builds:
- Test source files are excluded from main source set
- Test dependencies are scoped to `testImplementation`
- Production JARs and distributions never include test code or dependencies
