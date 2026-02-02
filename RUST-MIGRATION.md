# Rust Migration Guide

## Overview

This document outlines the strategy for incrementally migrating MattMC (a Minecraft 1.21.10 fork) from Java to Rust using JNI (Java Native Interface). The goal is to improve performance in critical code paths while maintaining compatibility with the existing Java codebase.

## Why Rust?

Rust offers several advantages over Java for performance-critical game code:

- **Zero-cost abstractions**: Compile-time optimizations with no runtime overhead
- **Memory safety without garbage collection**: Predictable performance without GC pauses
- **Better CPU utilization**: Fine-grained control over memory layout and CPU cache usage
- **SIMD support**: Easy access to CPU vector instructions for parallel operations
- **Cross-platform**: Single codebase compiles to native code on all platforms

## Migration Strategy

### Phase 1: Setup and Proof of Concept ✓

1. Set up Rust build integration with Gradle in `src/main/rust/`
2. Create JNI bindings infrastructure
3. Migrate existing Mth.java methods to Rust implementations
4. Verify the integration works end-to-end

### Phase 2: Incremental Migration (Recommended Approach)

The key to successful migration is to proceed incrementally, one class or set of methods at a time:

#### Selection Criteria for Code to Migrate

Migrate code in this priority order:

1. **Pure utility functions** (no dependencies, heavily used)
   - Math utilities (floor, ceil, clamp, etc.) ✓ DONE
   - String processing
   - Data structure utilities
   
2. **Performance-critical hot paths** (identified via profiling)
   - Chunk generation algorithms
   - Physics calculations
   - Collision detection
   - Rendering utilities

3. **Self-contained algorithms** (minimal dependencies)
   - Pathfinding
   - Noise generation
   - Data compression/decompression

4. **Data structures** (after utilities are migrated)
   - Custom collections
   - Spatial data structures (octrees, etc.)
   - Cache implementations

#### Migration Process (Per Method or Class)

For each method or class you want to migrate:

1. **Identify the target**
   - Should have no or minimal dependencies on other Java classes
   - Should be called from Java, not call back into Java
   - Should have clear, simple method signatures

2. **Create Rust implementation**
   - Add implementation in `src/main/rust/src/lib.rs`
   - Implement the functionality in pure Rust
   - Add JNI export functions

3. **Add JNI bindings**
   - Create JNI wrapper functions using the `jni` crate
   - Follow naming convention: `Java_<package_path>_<class>_rust<Method>`
   - For overloaded methods, include type signature (e.g., `__I` for int, `__F` for float)

4. **Update existing Java class**
   - Add native method declarations (e.g., `private static native int rustFloor(float value);`)
   - Update existing methods to call Rust when available
   - Implement fallback logic to pure Java if Rust library fails to load
   - Add library loading in a static initializer

5. **Test thoroughly**
   - Run existing tests to verify behavior is unchanged
   - All existing code should continue to work

6. **Performance testing**
   - Run benchmarks to verify performance improvement
   - Profile to identify next migration target

### Build System Integration

The Gradle build is configured to:

1. Compile Rust code BEFORE Java compilation (`compileJava` depends on `copyRustLibrary`)
2. Rust source location: `src/main/rust/src/lib.rs`
3. Copy compiled native libraries to `build/resources/main/natives/`
4. Include native libraries in the JAR file
5. Clean Rust artifacts when running `gradle clean`

### JNI Method Naming Convention

JNI methods must follow this naming pattern:

```
Java_<package_path>_<class>_<method>[__<signature>]
```

For methods with Rust implementations that are wrapped by Java methods, use the `rust` prefix:

```
Java_<package_path>_<class>_rust<Method>[__<signature>]
```

Examples:
- `Java_net_minecraft_util_Mth_rustFloor__F` - rustFloor(float)
- `Java_net_minecraft_util_Mth_rustFloor__D` - rustFloor(double)
- `Java_net_minecraft_util_Mth_rustClamp__III` - rustClamp(int, int, int)

Type signatures:
- `I` = int
- `J` = long
- `F` = float
- `D` = double
- `Z` = boolean
- `L<class>;` = object reference

### Directory Structure

```
MattMC/
├── src/main/rust/              # Rust library root
│   ├── Cargo.toml              # Rust dependencies and build config
│   └── src/
│       └── lib.rs              # Rust implementations with JNI exports
├── src/main/java/
│   └── net/minecraft/util/
│       └── Mth.java            # Migrated class with Rust integration
└── build.gradle                # Gradle build with Rust integration
```

### Error Handling and Fallbacks

Every migrated Java class should:

1. Gracefully handle library loading failures
2. Provide pure Java fallback implementations
3. Not crash if the native library is unavailable
4. Silently fall back to Java (no error messages for normal operation)

This ensures the game can still run even if:
- The Rust library fails to compile
- The native library is missing
- JNI fails for any reason

### Testing Strategy

For each migrated method:

1. **Existing tests should pass** - Verify behavior matches original Java implementation
2. **Integration tests** - Verify JNI bindings work correctly
3. **Performance benchmarks** - Measure actual performance improvement

### Platform Support

The Rust library compiles to native code for each platform:

- **Linux**: `libmattmc_rust.so`
- **macOS**: `libmattmc_rust.dylib`
- **Windows**: `mattmc_rust.dll`

The Gradle build automatically detects the platform and uses the correct library.

### Best Practices

1. **Start small**: Begin with simple, self-contained functions
2. **Maintain API compatibility**: Keep the same method signatures
3. **Preserve semantics**: Ensure Rust behaves exactly like Java
4. **Profile first**: Only migrate code that actually needs optimization
5. **Test thoroughly**: Minecraft has complex edge cases
6. **Document everything**: Future maintainers need to understand the hybrid codebase

### Dependencies

The Rust project uses:

- `jni = "0.21"` - JNI bindings for Rust

Additional dependencies should be added to `src/main/rust/Cargo.toml` as needed.

### Common Pitfalls to Avoid

1. **Don't migrate too much at once** - One method/class at a time
2. **Don't create circular dependencies** - Keep calls one-directional (Java → Rust)
3. **Don't ignore precision differences** - Floating-point math may differ slightly
4. **Don't forget about platforms** - Test on all supported OS/architectures
5. **Don't remove Java fallbacks** - Always keep pure Java implementations

## Example: Migrated Mth.java Methods

Here's how the migration looks for the `Mth` class:

### Rust Implementation (`src/main/rust/src/lib.rs`)

```rust
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_Mth_rustFloor__F(
    _env: JNIEnv,
    _class: JClass,
    value: jfloat,
) -> jint {
    let i = value as jint;
    if value < i as jfloat { i - 1 } else { i }
}
```

### Java Integration (`src/main/java/net/minecraft/util/Mth.java`)

```java
public class Mth {
    private static boolean RUST_AVAILABLE = false;
    
    static {
        try {
            // Load native library
            ...
            RUST_AVAILABLE = true;
        } catch (Exception e) {
            RUST_AVAILABLE = false;
        }
    }
    
    // Native method declaration
    private static native int rustFloor(float value);
    
    // Public method with Rust integration
    public static int floor(float f) {
        if (RUST_AVAILABLE) {
            return rustFloor(f);  // Call Rust
        }
        // Fallback to Java
        int i = (int)f;
        return f < i ? i - 1 : i;
    }
}
```

## Next Steps

After the proof of concept:

1. Profile the game to identify performance bottlenecks
2. Select the next methods to migrate based on:
   - Performance impact
   - Simplicity (few dependencies)
   - Frequency of use
3. Repeat the migration process
4. Measure and document performance improvements

## Resources

- [The Rust Programming Language Book](https://doc.rust-lang.org/book/)
- [JNI Programming Guide](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [rust-jni crate documentation](https://docs.rs/jni/)
- [Gradle Build Tool](https://docs.gradle.org/)
