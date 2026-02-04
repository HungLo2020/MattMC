# Rust Migration Strategy

## Overview

This document describes the incremental migration of MattMC from Java to Rust. The migration is being done gradually, component by component, to maintain stability while gaining the benefits of Rust's performance and memory safety.

## Philosophy

**This is NOT a rewrite.** This is an **incremental, surgical migration** where individual Java classes are replaced with Rust implementations using Java's Foreign Function & Memory (FFM) API. The goal is to:

1. **Improve performance** - Rust's zero-cost abstractions and lack of garbage collection overhead
2. **Enhance safety** - Rust's memory safety guarantees eliminate entire classes of bugs
3. **Maintain compatibility** - Existing Java code continues to work unchanged
4. **Minimize risk** - Migrate one component at a time, with full testing at each step

## Critical Design Decision: No Fallback Policy

### ⚠️ IMPORTANT: Hard Failure is Intentional

**Java code MUST NOT serve as a fallback if the Rust native library fails to load.**

When a component is migrated to Rust:
- If the native library fails to load, the application **MUST fail immediately with a clear error**
- There is **NO Java fallback implementation**
- The failure should be loud and obvious: `ExceptionInInitializerError` with detailed diagnostics

**Why no fallback?**

1. **Prevents silent failures** - If native code fails, we want to know immediately, not discover degraded performance in production
2. **Forces proper deployment** - Ensures native libraries are correctly packaged and deployed
3. **Avoids dual maintenance** - No need to maintain both Java and Rust implementations in sync
4. **Clear contract** - Once migrated, a component is Rust-native; there's no ambiguity

**Example from MathUtil:**
```java
static {
    try {
        // Load native library and initialize FFM handles
        ...
    } catch (Throwable e) {
        // NO FALLBACK - fail hard as intended
        System.err.println("FATAL: Failed to load Rust native library for MathUtil!");
        System.err.println("This is a critical error. The game cannot continue without the native library.");
        e.printStackTrace();
        throw new ExceptionInInitializerError(e);
    }
}
```

## Current Implementation Status

### Migrated Components

#### 1. MathUtil (`com.seibel.distanthorizons.coreapi.util.MathUtil`)

**Status**: ✅ Complete and tested

**What it does**: Provides mathematical utility functions used throughout the codebase

**Migrated functions** (11 total):
- `clamp(int/float/double)` - Value clamping
- `ceilDiv(int, int)` - Ceiling division
- `min/max(byte, byte)` - Byte comparisons
- `fastInvSqrt(float)` - Fast inverse square root (Quake III algorithm)
- `pow2(int/long/float/double)` - Square calculation
- `log2(int)` - Base-2 logarithm

**Why this class first?**
- No external dependencies
- Pure mathematical operations (ideal for Rust)
- Widely used (15+ call sites) - good performance impact
- Simple API - easy to verify correctness

**Implementation approach**:
1. Rust FFI library (`src/main/rust/src/util/math_util.rs`) with C-compatible exports
2. Java FFM interface loads native library from JAR
3. Comprehensive tests verify behavioral equivalence (12 test methods)
4. Platform-specific builds (Linux, macOS x64/ARM, Windows)

#### 2. BitShiftUtil (`com.seibel.distanthorizons.coreapi.util.BitShiftUtil`)

**Status**: ✅ Complete and tested

**What it does**: Helper methods for bit shift operations (written to make code easier to read)

**Migrated functions** (10 total):
- `powerOfTwo(int/long)` - Calculates 2^value via bit shift
- `half(int/long)` - Divides by 2 via right shift
- `divideByPowerOfTwo(int/long, int/long)` - Divides by 2^power via right shift
- `square(int/long)` - Multiplies by 2 via left shift
- `pow(int/long, int/long)` - Multiplies by 2^power via left shift

**Why this class?**
- No external dependencies
- Pure bit manipulation operations (perfect for Rust)
- Frequently used throughout the codebase
- Simple, stateless operations
- Second migration validates the modular structure approach

**Implementation approach**:
1. Rust FFI library (`src/main/rust/src/util/bit_shift_util.rs`) with C-compatible exports
2. Java FFM interface loads native library from JAR
3. Comprehensive tests verify behavioral equivalence (10 test methods)
4. Demonstrates successful modular Rust structure

## Technical Architecture

### Current Project Structure

**Migration Complete**: The Rust codebase now uses a **modular structure** as planned, with each Java class having its own Rust module.

**Current Structure**:
```
src/main/rust/src/
├── lib.rs                          # Main entry point, re-exports modules
└── util/
    ├── mod.rs                      # Module declaration
    ├── math_util.rs                # MathUtil FFI functions
    └── bit_shift_util.rs           # BitShiftUtil FFI functions
```

**Design principles**:
- **One Rust file per Java class** - Each migrated Java class has a corresponding Rust module
- **Mirror Java package structure** - Rust module hierarchy reflects Java package hierarchy
- **Single compiled library** - All Rust modules compile into one native library (`.so`/`.dylib`/`.dll`)
- **Single JAR distribution** - The unified native library is packaged into the final JAR

**Future expansion**:
```
src/main/rust/src/
├── lib.rs                          # Main entry point, re-exports modules
├── util/
│   ├── mod.rs                      # Module declaration
│   ├── math_util.rs                # MathUtil FFI functions
│   ├── bit_shift_util.rs           # BitShiftUtil FFI functions
│   └── string_util.rs              # StringUtil FFI functions (future)
│   └── string_util.rs              # StringUtil FFI functions (future)
├── core/
│   ├── mod.rs
│   └── data_processor.rs           # Data processing FFI (future)
└── rendering/
    ├── mod.rs
    └── geometry.rs                 # Geometric calculations FFI (future)
```

This 1:1 mapping strategy ensures:
- **Easy navigation** - Developers can find Rust equivalents of Java classes quickly
- **Clear responsibility** - Each Rust file has a well-defined scope matching its Java counterpart
- **Maintainability** - Changes to Java APIs can be easily tracked to corresponding Rust modules
- **Gradual expansion** - New migrations add new files without modifying existing ones

### FFM (Foreign Function & Memory) API

We use Java's FFM API (available since Java 22) instead of JNI because:

- **Lower overhead** - Direct C ABI calls without JNI marshalling
- **Modern API** - Clean, type-safe interface using `MethodHandle`
- **No code generation** - No need for header files or JNI glue code
- **Memory safety** - Structured memory access with `MemorySegment`

### Build Integration

The Rust native library is built as part of the Gradle build:

```bash
./gradlew build
```

This automatically:
1. Compiles Rust code to a dynamic library (`.so`/`.dylib`/`.dll`)
2. Copies the library to `src/main/resources/native/`
3. Packages it into the JAR file
4. Makes it available at runtime via resource extraction

**Cross-platform support**:
- Linux x86_64 (`.so`)
- macOS x86_64 and ARM64 (`.dylib`)
- Windows x86_64 (`.dll`)

The build system automatically detects the `cargo` executable, even when Gradle daemon doesn't inherit shell PATH (common macOS issue).

### Library Loading

At runtime:
1. `NativeLibraryLoader` extracts the platform-specific library from JAR to a temp directory
2. FFM API loads the library using `SymbolLookup.libraryLookup()`
3. Function handles are created using `Linker.downcallHandle()`
4. Java methods delegate to native functions via `MethodHandle.invokeExact()`

**If loading fails**: Application terminates with `ExceptionInInitializerError` (no fallback).

## Migration Criteria

### Good Candidates for Migration

✅ **Pure utility classes**
- No state, just functions
- Mathematical/algorithmic operations
- No external dependencies

✅ **Performance bottlenecks**
- Identified through profiling
- Called frequently in hot paths
- Heavy computation

✅ **Self-contained components**
- Clear API boundaries
- Minimal coupling to Java ecosystem
- Easy to test in isolation

### Poor Candidates (Avoid)

❌ **UI/rendering code** - Tightly coupled to Java graphics libraries  
❌ **Minecraft integration** - Depends on Minecraft's Java API  
❌ **Complex stateful classes** - Difficult to model in FFI  
❌ **Code with Java-specific features** - Reflection, annotations, etc.

## Developer Workflow

### Migrating a New Class

1. **Identify candidate** - Use profiling data and dependency analysis
2. **Create Rust module** - Add a new `.rs` file mirroring the Java class structure
   - For `com.example.util.MyUtil.java` → create `src/main/rust/src/util/my_util.rs`
   - Update `util/mod.rs` to add `pub mod my_util;`
   - Update `lib.rs` to re-export with `pub use util::my_util::*;`
3. **Implement Rust** - Create FFI-compatible functions with `#[no_mangle]` and `extern "C"`
4. **Create FFM interface** - Replace Java implementation with native calls
5. **Write tests** - Verify behavioral equivalence with original
6. **Update build** - Ensure library is built and packaged (usually automatic)
7. **Document** - Update this file and add migration notes

**Current modular structure**: The codebase now uses the module-per-class structure as designed.

### Testing Requirements

Every migrated component MUST have:
- Unit tests comparing Rust behavior to original Java
- Integration tests in realistic usage scenarios
- Cross-platform verification (Linux, macOS, Windows)
- Performance benchmarks (optional but recommended)

### Code Review Checklist

- [ ] Rust code follows safety best practices
- [ ] FFI exports use `#[no_mangle]` and `extern "C"`
- [ ] Java interface has NO fallback implementation
- [ ] Throws `ExceptionInInitializerError` on load failure
- [ ] All tests pass on all platforms
- [ ] Documentation is updated

## Performance Expectations

While performance varies by use case, typical improvements include:

- **Math operations**: 1.5-3x faster (no boxing, direct CPU instructions)
- **Memory allocation**: Reduced GC pressure (stack allocation in Rust)
- **Predictable latency**: No GC pauses for migrated code paths

**Note**: Not all code will see dramatic improvements. Measure before and after.

## Future Migration Candidates

Based on profiling and architecture analysis, potential candidates:

1. **StringUtil** - String manipulation utilities (pure functions, no dependencies)
2. **Data processing pipelines** - Heavy computation in LOD generation
3. **Geometric calculations** - Vector math, collision detection
4. **Compression/decompression** - CPU-intensive, well-defined APIs

**Do NOT migrate** (keep in Java):
- Minecraft mod integration code
- UI components using Java Swing/AWT
- Reflection-heavy systems
- Fabric loader integration

## Security and Safety

### Memory Safety

Rust's ownership system prevents:
- Buffer overflows
- Use-after-free
- Data races
- Null pointer dereferences

These guarantees extend to the native library, even when called from Java.

### Validation

All Rust code undergoes:
1. **CodeQL security scanning** - Automated vulnerability detection
2. **Cargo audit** - Dependency vulnerability checking (when deps added)
3. **Code review** - Manual inspection of FFI boundaries
4. **Fuzz testing** - For parsing/processing components (future)

## Dependencies and Prerequisites

### Required

- **Rust**: 1.93.0 or later (via rustup)
- **Cargo**: 1.93.0 or later
- **Java**: 22+ (for FFM API)
- **Gradle**: 9.1.0+

### Build System

Gradle tasks:
- `buildRustNative` - Build for current platform
- `buildRustLinux` - Cross-compile for Linux
- `buildRustMacOSx64` - Cross-compile for macOS Intel
- `buildRustMacOSARM` - Cross-compile for macOS ARM
- `buildRustWindows` - Cross-compile for Windows
- `copyNativeLibs` - Package into JAR resources

See `src/main/rust/README.md` for detailed build instructions.

## FAQ

### Why incremental migration instead of a full rewrite?

Full rewrites are risky and often fail. Incremental migration:
- Maintains working software at all times
- Allows validation at each step
- Enables gradual learning of Rust idioms
- Minimizes disruption to development

### Why Rust specifically?

- **Performance**: Comparable to C/C++ with modern ergonomics
- **Safety**: Memory safety without garbage collection
- **Tooling**: Excellent package manager, testing framework, and documentation
- **Community**: Large ecosystem of high-quality libraries
- **Future-proof**: Growing adoption in systems programming

### What about JNI?

FFM is superior to JNI for new code:
- Simpler API (no header generation)
- Better performance (no marshalling overhead)
- Type-safe (checked at compile time)
- Modern (actively developed, JNI is legacy)

### Can we roll back a migration?

Yes, but only before release:
1. Restore original Java implementation from git history
2. Remove Rust FFI code
3. Update build to skip native library
4. Re-run tests

Once released, rolling back requires a new release with the old code.

## Monitoring and Metrics

### Success Criteria

- ✅ Zero native library load failures in production
- ✅ Performance improvement measured by benchmarks
- ✅ No memory safety violations (validated by Rust compiler)
- ✅ All tests pass on all platforms

### Failure Modes

If you see these errors, the native library failed to load:

```
FATAL: Failed to load Rust native library for [ClassName]!
This is a critical error. The game cannot continue without the native library.
```

**Do not add a Java fallback.** Instead, fix the root cause:
- Library not packaged in JAR
- Platform mismatch (wrong `.so`/`.dylib`/`.dll`)
- Missing dependencies (rare with static linking)
- File extraction failed (permissions, disk space)

## Contributing

When contributing to Rust migration efforts:

1. **Discuss first** - Propose migration candidates in issues/PRs
2. **Start small** - Don't migrate large components initially
3. **Measure impact** - Profile before and after
4. **Test thoroughly** - All platforms, edge cases, performance
5. **Document clearly** - Update this file and code comments
6. **No fallbacks** - Follow the hard-failure policy

## Resources

- **FFM API**: [JEP 454](https://openjdk.org/jeps/454)
- **Rust Book**: https://doc.rust-lang.org/book/
- **Rust FFI Guide**: https://doc.rust-lang.org/nomicon/ffi.html
- **Project Rust README**: `src/main/rust/README.md`
- **macOS Build Fix**: `docs/MACOS_BUILD_FIX.md`

## Project Evolution

### Current State (v0.2)

- **Modular Rust structure** - Organized into `util/` module with separate files per class
- **Two migrated classes** (`MathUtil` and `BitShiftUtil`)
- **Production-ready** FFM integration with comprehensive testing
- **22 passing tests** verifying behavioral equivalence

### Near-Term Goals (v0.3-0.5)

- **Migrate 3-5 more utility classes** - Focus on math/data processing (e.g., StringUtil)
- **Establish patterns** - Standard practices for FFI boundary design
- **Performance benchmarks** - Quantify improvements
- **Cross-platform CI** - Automated testing on Linux, macOS, and Windows

### Long-Term Vision (v1.0+)

- **Hundreds of Rust modules** - Comprehensive migration of performance-critical paths
- **Clear Java/Rust boundary** - Well-defined FFI interfaces
- **Minimal Java overhead** - Most computation in Rust, Java for integration only
- **Production-ready** - Battle-tested across all platforms

## Changelog

### 2024-02-04: Modular Structure Migration (v0.2)
- ✅ Migrated `BitShiftUtil` to Rust (10 functions)
- ✅ Refactored Rust codebase to modular structure
- ✅ Created `util/` module with separate files per class
- ✅ Moved MathUtil functions to `util/math_util.rs`
- ✅ Implemented BitShiftUtil in `util/bit_shift_util.rs`
- ✅ Updated `lib.rs` to re-export modules
- ✅ Added comprehensive testing for BitShiftUtil (10 tests)
- ✅ All 22 tests passing (12 MathUtil + 10 BitShiftUtil)
- ✅ Validated modular build and packaging
- 📝 **Architecture**: Now using one Rust file per Java class as designed

### 2024-02-04: Initial Migration (v0.1)
- ✅ Migrated `MathUtil` to Rust
- ✅ Set up FFM integration infrastructure
- ✅ Configured cross-platform builds
- ✅ Established no-fallback policy
- ✅ Added comprehensive testing (12 tests, all passing)
- ✅ Fixed macOS cargo detection issue
- ✅ Documented migration strategy
- 📝 **Note**: All Rust code initially in single `lib.rs` file

---

**Remember**: The goal is not to rewrite everything in Rust. The goal is to strategically migrate performance-critical components while maintaining a stable, working Java application. Quality over quantity.
