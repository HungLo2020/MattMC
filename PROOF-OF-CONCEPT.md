# Proof of Concept: Java to Rust Migration via JNI

## What Was Done

This PR establishes a working proof of concept for incrementally migrating MattMC from Java to Rust using JNI (Java Native Interface). The implementation demonstrates that we can successfully:

1. Compile Rust code as part of the Gradle build process
2. Create JNI bindings between Java and Rust
3. Load native Rust libraries at runtime
4. Maintain backward compatibility with fallback implementations

## Changes Made

### 1. Rust Project Setup

Created a new Rust library in `rust-core/`:

- **Cargo.toml**: Configured as a `cdylib` (C-compatible dynamic library) with JNI dependencies
- **src/lib.rs**: Rust implementations of basic math functions with JNI exports

The Rust library implements these functions:
- `floor(float)` and `floor(double)` - Fast floor operations
- `ceil(float)` and `ceil(double)` - Fast ceiling operations
- `lfloor(double)` and `ceilLong(double)` - Long variants
- `clamp(int, int, int)` and variants for long, float, double - Value clamping
- `abs(float)` and `abs(int)` - Absolute value
- `square(float)`, `square(double)`, `square(int)`, `square(long)` - Squaring operations

### 2. Gradle Build Integration

Modified `build.gradle` to add Rust compilation:

- **`compileRust` task**: Runs `cargo build --release` before Java compilation
- **`copyRustLibrary` task**: Copies compiled native library to resources
- **`cleanRust` task**: Cleans Rust build artifacts
- **Dependency chain**: `compileJava` → `copyRustLibrary` → `compileRust`

This ensures the Rust library is always compiled and packaged before running the Java code.

### 3. Java JNI Wrapper

Created `src/main/java/net/minecraft/util/MthRust.java`:

- **Native method declarations**: Define the JNI interface to Rust
- **Library loading**: Extracts and loads the native library from resources
- **Fallback implementations**: Pure Java versions if Rust library unavailable
- **Public API**: Clean interface that abstracts whether Rust or Java is used

Key features:
- Automatic platform detection (Linux .so, macOS .dylib, Windows .dll)
- Graceful degradation to Java if native library fails to load
- Zero changes required to existing code - it's a drop-in replacement

### 4. Testing

Created `src/test/java/net/minecraft/util/MthRustTest.java`:

- Comprehensive test coverage for all implemented functions
- Tests verify both Rust and Java fallback implementations
- All 12 tests passing

### 5. Documentation

Created two documentation files:

- **RUST-MIGRATION.md**: Complete guide for future migrations
- **PROOF-OF-CONCEPT.md**: This file

### 6. Configuration

Updated `.gitignore`:
- Added `rust-core/target/` to ignore Rust build artifacts
- Added `rust-core/Cargo.lock` to ignore dependency lock file

## Why This Class?

I chose to create a new `MthRust` class containing basic math utilities from the existing `Mth` class because:

1. **Heavily used**: Math utilities are called throughout the codebase (hundreds of uses)
2. **Very simple**: Pure functions with no dependencies
3. **Performance-critical**: Math operations are in hot paths (rendering, physics, chunk generation)
4. **Easy to verify**: Deterministic outputs make testing straightforward
5. **One-directional**: Java calls Rust, Rust doesn't call back into Java

This is an ideal proof of concept - it demonstrates the full stack without complexity.

## How It Works

### Build Process

1. Developer runs `./gradlew build`
2. Gradle executes `compileRust` task
3. Cargo compiles Rust code to native library (`libmattmc_rust.so` on Linux)
4. `copyRustLibrary` task copies library to `build/resources/main/natives/`
5. Java compilation proceeds normally
6. Native library is packaged in the JAR file

### Runtime Process

1. Java application starts
2. `MthRust` class initializer runs
3. Native library is extracted from JAR to temporary file
4. `System.load()` loads the native library
5. JNI connects Java native methods to Rust functions
6. Application can now call Rust code through Java methods

If any step fails, the fallback Java implementations are used instead.

## Performance Expectations

While this proof of concept focuses on correctness over optimization, Rust implementations should provide:

- **Reduced GC pressure**: Math operations don't allocate on the heap
- **Better CPU utilization**: Compiler optimizations and CPU-native code
- **Predictable latency**: No garbage collection pauses
- **SIMD potential**: Future implementations can use vector instructions

Actual performance gains depend on workload characteristics and should be measured via profiling.

## Testing Results

All tests pass successfully:

```
MthRustTest > testClampFloat() PASSED
MthRustTest > testClampDouble() PASSED
MthRustTest > testAbs() PASSED
MthRustTest > testFloor() PASSED
MthRustTest > testCeil() PASSED
MthRustTest > testCeilLong() PASSED
MthRustTest > testClampLong() PASSED
MthRustTest > testNegativeSquare() PASSED
MthRustTest > testLibraryLoading() PASSED
MthRustTest > testClampInt() PASSED
MthRustTest > testLfloor() PASSED
MthRustTest > testSquare() PASSED

Test Results: SUCCESS
Tests run: 12, Passed: 12, Failed: 0, Skipped: 0
```

## Platform Support

The build system automatically handles platform differences:

- **Linux**: Generates `libmattmc_rust.so`
- **macOS**: Generates `libmattmc_rust.dylib`
- **Windows**: Generates `mattmc_rust.dll`

The Java code detects the OS at runtime and loads the appropriate library.

## Next Steps

Now that the infrastructure is in place, future migrations can follow this pattern:

1. **Identify hot paths**: Profile the game to find performance bottlenecks
2. **Select target class**: Choose a simple, self-contained class with high impact
3. **Implement in Rust**: Create Rust implementation with unit tests
4. **Add JNI bindings**: Connect Java to Rust
5. **Test thoroughly**: Verify behavior matches Java exactly
6. **Measure performance**: Benchmark to quantify improvements
7. **Update existing code**: Replace calls to old class with new Rust-backed version

See `RUST-MIGRATION.md` for detailed migration guidelines.

## Lessons Learned

1. **JNI naming is precise**: Function names must exactly match the Java package/class/method structure
2. **Type signatures matter**: Overloaded methods require type signature suffixes (e.g., `__F` for float)
3. **Fallbacks are essential**: The system should never crash just because native code is unavailable
4. **Build order matters**: Rust must compile before Java to ensure libraries are available
5. **Resource extraction works**: Extracting libraries from JAR resources is a reliable pattern

## Potential Issues and Solutions

### Issue: Library Not Found

**Symptom**: `UnsatisfiedLinkError` at runtime

**Solutions**:
- Verify Rust library compiled successfully
- Check library was copied to resources
- Confirm platform detection is correct
- Fallback to Java implementation (automatic)

### Issue: JNI Method Not Found

**Symptom**: `UnsatisfiedLinkError` for specific method

**Solutions**:
- Verify function name matches exactly (case-sensitive)
- Check type signature for overloaded methods
- Use `javah` or similar tool to verify naming
- Recompile Rust library

### Issue: Build Performance

**Symptom**: Slow builds due to Rust compilation

**Solutions**:
- Gradle caches Rust builds (only rebuilds when source changes)
- Use `--release` flag only for production builds
- Consider incremental compilation settings in Cargo.toml

## Conclusion

This proof of concept successfully demonstrates that:

✅ Rust can be integrated into the MattMC build process  
✅ JNI bindings work correctly  
✅ Performance-critical code can be written in Rust  
✅ The system gracefully handles library loading failures  
✅ Tests verify correctness of the Rust implementations  
✅ The migration path is clear and repeatable  

The infrastructure is now in place for incremental migration of performance-critical code from Java to Rust, one class at a time, without breaking existing functionality.
