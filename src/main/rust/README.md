# Rust Native Library Integration

This directory contains the Rust implementation of performance-critical components using Java's Foreign Function & Memory (FFM) API.

## Overview

The Rust code is compiled to native libraries and loaded by Java using the FFM API (Java 22+). This provides:
- **Better performance** for mathematical operations
- **Memory safety** through Rust's guarantees
- **No JNI overhead** - direct C ABI calls via FFM

## Current Implementation

### MathUtil (com.seibel.distanthorizons.coreapi.util.MathUtil)

The `MathUtil` class has been migrated from pure Java to Rust FFI. It provides:
- `clamp()` - Clamp values (int, float, double)
- `ceilDiv()` - Ceiling division
- `min/max()` - Byte min/max
- `fastInvSqrt()` - Fast inverse square root (Quake III algorithm)
- `pow2()` - Square function (int, long, float, double)
- `log2()` - Base-2 logarithm

**IMPORTANT**: This implementation has NO Java fallback. If the native library fails to load, the game will fail with an `ExceptionInInitializerError`.

### BitShiftUtil (com.seibel.distanthorizons.coreapi.util.BitShiftUtil)

The `BitShiftUtil` class has been migrated from pure Java to Rust FFI. It provides:
- `powerOfTwo()` - Calculate 2^value (int, long)
- `half()` - Divide by 2 via right shift (int, long)
- `divideByPowerOfTwo()` - Divide by 2^power (int, long)
- `square()` - Multiply by 2 via left shift (int, long)
- `pow()` - Multiply by 2^power (int, long)

**IMPORTANT**: This implementation has NO Java fallback. If the native library fails to load, the game will fail with an `ExceptionInInitializerError`.

## Building

The Rust library is automatically built when you run:

```bash
./gradlew build
```

Or build just the native library:

```bash
./gradlew buildRustNative
```

### Cross-compilation

To build for multiple platforms, you need to install the appropriate Rust targets:

```bash
# Linux x86_64
rustup target add x86_64-unknown-linux-gnu

# macOS x86_64
rustup target add x86_64-apple-darwin

# macOS ARM64 (Apple Silicon)
rustup target add aarch64-apple-darwin

# Windows x86_64
rustup target add x86_64-pc-windows-gnu
```

Then build for specific platforms:

```bash
./gradlew buildRustLinux
./gradlew buildRustMacOSx64
./gradlew buildRustMacOSARM
./gradlew buildRustWindows
```

## Project Structure

```
src/main/rust/
├── Cargo.toml          # Rust project configuration
├── lib.rs              # Main entry point, re-exports modules
└── util/
    ├── mod.rs              # Module declarations
    ├── math_util.rs        # MathUtil FFI functions
    └── bit_shift_util.rs   # BitShiftUtil FFI functions
```

**Modular Design**: Each migrated Java class has its own Rust module file. The `lib.rs` file re-exports all modules to make their functions available via the C ABI.

## Native Library Packaging

The compiled native libraries are:
1. Built to `src/main/rust/target/release/`
2. Copied to `src/main/resources/native/`
3. Packaged into the JAR at `/native/`
4. Extracted and loaded at runtime

Platform-specific library names:
- **Linux**: `libmattmc_native.so`
- **macOS**: `libmattmc_native.dylib`
- **Windows**: `mattmc_native.dll`

## Running with FFM

The JVM must be started with native access enabled:

```bash
--enable-native-access=ALL-UNNAMED
```

This flag is automatically added to:
- Test tasks
- Run tasks (client and server)

## Testing

Run the Rust integration tests:

```bash
./gradlew test --tests "MathUtilTest"
./gradlew test --tests "BitShiftUtilTest"
```

All 22 test methods (12 for MathUtil, 10 for BitShiftUtil) verify that the Rust implementation matches the original Java behavior.

## Development Workflow

1. Modify Rust code in `src/main/rust/util/` (e.g., `math_util.rs`, `bit_shift_util.rs`)
2. If adding a new module, update `util/mod.rs` to declare it and `lib.rs` to re-export it
3. Build: `./gradlew buildRustNative`
4. Copy to resources: `./gradlew copyNativeLibs`
5. Test: `./gradlew test --tests "MathUtilTest" --tests "BitShiftUtilTest"`

Or just run `./gradlew build` to do all steps.

## Adding New Native Functions

### To an existing module (e.g., MathUtil):

1. **Add Rust function** in `util/math_util.rs`:
   ```rust
   #[no_mangle]
   pub extern "C" fn mathutil_my_function(arg: i32) -> i32 {
       // implementation
   }
   ```

2. **Add Java method** in the FFM class:
   ```java
   private static final MethodHandle myFunction;
   
   static {
       myFunction = LINKER.downcallHandle(
           findFunction("mathutil_my_function"),
           FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
       );
   }
   
   public static int myFunction(int arg) {
       try {
           return (int) myFunction.invokeExact(arg);
       } catch (Throwable e) {
           throw new RuntimeException("Failed to call native myFunction", e);
       }
   }
   ```

3. **Rebuild and test**

### To add a new module:

1. Create a new `.rs` file in `util/` (e.g., `string_util.rs`)
2. Add `pub mod string_util;` to `util/mod.rs`
3. Add `pub use util::string_util::*;` to `lib.rs`
4. Follow the same FFI function pattern as above
5. Create corresponding Java FFM class and tests

## Performance Considerations

- Rust code is compiled with optimizations: `-O3`, LTO, single codegen unit
- Strip symbols in release builds for smaller binaries
- FFM calls have very low overhead (near-native performance)

## Dependencies

- **Rust**: 1.93.0 or later
- **Java**: 22+ (FFM API is available since Java 22, project uses Java 25)
- **Cargo**: 1.93.0 or later

### Installing Rust

If you haven't installed Rust yet:

```bash
# Install via rustup (recommended)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# After installation, ensure cargo is in your PATH
source "$HOME/.cargo/env"
```

### macOS Build Issues

On macOS, Gradle may not find `cargo` even if it's installed. The build script automatically checks these locations:
- `~/.cargo/bin/cargo` (rustup default)
- `/opt/homebrew/bin/cargo` (Homebrew on Apple Silicon)
- `/usr/local/bin/cargo` (Homebrew on Intel)

If you still get "cargo not found" errors:
1. Verify cargo is installed: `which cargo`
2. Add cargo to your PATH in `~/.zshrc` or `~/.bash_profile`:
   ```bash
   export PATH="$HOME/.cargo/bin:$PATH"
   ```
3. Restart your terminal or run: `source ~/.zshrc`

## Troubleshooting

### Library not found

If you get "Native library not found in JAR", ensure:
1. The library was built: `./gradlew buildRustNative`
2. The library was copied: `./gradlew copyNativeLibs`
3. The JAR includes it: `unzip -l build/libs/*.jar | grep native`

### Native access warnings

If you see FFM warnings, ensure JVM flag is set:
```
--enable-native-access=ALL-UNNAMED
```

### Cross-compilation failures

Some targets require additional tools:
- **Windows**: MinGW-w64 toolchain
- **macOS**: Xcode command-line tools (on macOS only)

## Future Migrations

This proof-of-concept demonstrates the process for migrating Java code to Rust. Future candidates:
- Mathematical heavy operations
- Data processing pipelines  
- Performance-critical algorithms

Always measure before migrating - not all code benefits from Rust!
