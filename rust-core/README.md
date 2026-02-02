# MattMC Rust Core

This directory contains Rust implementations of performance-critical MattMC functions, accessible from Java via JNI (Java Native Interface).

## Building

The Rust library is automatically compiled as part of the Gradle build process. You don't need to manually build it.

However, if you want to build it manually:

```bash
cd rust-core
cargo build --release
```

The compiled library will be in `target/release/`:
- Linux: `libmattmc_rust.so`
- macOS: `libmattmc_rust.dylib`
- Windows: `mattmc_rust.dll`

## Testing

Run Rust tests:

```bash
cd rust-core
cargo test
```

## Adding New Functions

To add a new Rust function callable from Java:

1. **Add the Rust implementation** in `src/lib.rs`:

```rust
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MyClass_myFunction(
    _env: JNIEnv,
    _class: JClass,
    param: jint,
) -> jint {
    // Your Rust implementation
    param * 2
}
```

2. **Declare it in Java** (e.g., in `src/main/java/net/minecraft/util/MyClass.java`):

```java
public class MyClass {
    static {
        // Load library
        System.loadLibrary("mattmc_rust");
    }
    
    private static native int myFunction(int param);
    
    public static int publicMyFunction(int param) {
        return myFunction(param);
    }
}
```

3. **Follow JNI naming conventions**:
   - Function name: `Java_<package>_<class>_<method>`
   - Package separators: underscores (`_`)
   - For overloaded methods, add type signature (e.g., `__I` for int, `__F` for float)

4. **Rebuild**:

```bash
./gradlew clean build
```

## JNI Type Mappings

| Java Type | JNI Type | Rust Type |
|-----------|----------|-----------|
| `int` | `jint` | `i32` |
| `long` | `jlong` | `i64` |
| `float` | `jfloat` | `f32` |
| `double` | `jdouble` | `f64` |
| `boolean` | `jboolean` | `u8` |

## Dependencies

Current dependencies (in `Cargo.toml`):
- `jni = "0.21"` - JNI bindings for Rust

## Performance Tips

1. **Avoid allocations**: Use stack-allocated data when possible
2. **Minimize JNI crossings**: Batch operations to reduce overhead
3. **Use SIMD**: Consider `std::simd` for parallel operations
4. **Profile first**: Only optimize what matters

## Troubleshooting

**Issue**: `UnsatisfiedLinkError` at runtime

**Solutions**:
- Verify the library compiled: check `rust-core/target/release/`
- Check Gradle built successfully: `./gradlew compileRust`
- Ensure library is in resources: `build/resources/main/natives/`

**Issue**: Function not found

**Solution**:
- Verify function name matches JNI convention exactly
- Check type signatures for overloaded methods
- Use `nm` (Linux/Mac) or `dumpbin` (Windows) to list exported symbols:
  ```bash
  nm -D target/release/libmattmc_rust.so | grep Java
  ```

## Resources

- [Rust JNI Crate Docs](https://docs.rs/jni/)
- [JNI Specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [Rust Book](https://doc.rust-lang.org/book/)
