# Migration Guide: Temurin JDK to GraalVM

This guide explains how the project has been migrated from Temurin OpenJDK to GraalVM, and what this means for users and developers.

## What Changed?

### Before (Temurin OpenJDK 25)
- Used Temurin OpenJDK 25 (Java 25)
- JAR-based distribution only
- Required JRE/JDK for end users
- Traditional JVM execution with JIT compilation

### After (GraalVM for JDK 21)
- Uses GraalVM for JDK 21
- **Supports both JAR and native executable builds**
- Native executables don't require JDK installation
- Faster startup and lower memory usage with native images

## Why GraalVM?

GraalVM was chosen for several key reasons:

1. **Native Image Support**: Compile Java to standalone executables
2. **Better Performance**: Advanced compiler optimizations
3. **Lower Memory Usage**: More efficient runtime
4. **Future-Proofing**: Industry-standard for native Java compilation

## For End Users

### JAR-Based Distribution (No Change)

If you're using the JAR-based distribution, **nothing changes** for you:

```bash
# Same as before
./run-mattmc.sh          # Linux/macOS
run-mattmc.bat           # Windows
```

### Native Executable Distribution (NEW!)

If you download a native executable distribution:

```bash
# New native launchers
./run-mattmc-native.sh   # Linux/macOS
run-mattmc-native.bat    # Windows
```

**Advantages of native executables:**
- No JDK/JRE required
- Much faster startup (seconds instead of minutes)
- Lower memory usage
- Better overall performance

**Limitations:**
- Platform-specific (Windows .exe only runs on Windows, etc.)
- Slightly larger file size
- Some dynamic features may not work

## For Developers

### Development Workflow

#### Normal Development (JAR-based)

Your normal development workflow **hasn't changed**:

```bash
# Still works exactly as before
./gradlew runClient
./gradlew runServer
./DevUtils/RunDev.sh
```

#### Testing Native Compilation

To test native compilation during development:

```bash
# Test native compilation
./DevUtils/RunDev.sh native

# Or manually:
./gradlew nativeCompile
```

**Note:** Native compilation takes 10-15 minutes on first run!

### Building Distributions

#### JAR-Based Distribution (Same as Before)

```bash
# Build JAR distribution
./gradlew clientDist
./gradlew clientDistZip
```

#### Native Executable Distribution (NEW!)

```bash
# Build native executable
./gradlew nativeCompile

# Build complete native distribution
./gradlew nativeClientDist
./gradlew nativeClientDistZip
```

### JDK Download Changes

The download scripts now fetch GraalVM instead of Temurin:

```bash
# Downloads GraalVM for JDK 21
./libraries/download-jdk.sh          # Linux/macOS
libraries\download-jdk.ps1           # Windows
```

**Important:** While we reference this as "jdk-25" in directory names (for compatibility), it's actually **GraalVM for JDK 21**.

## Build System Changes

### Gradle Configuration

#### New Plugin

Added GraalVM Native Image Gradle plugin:

```groovy
plugins {
    id 'org.graalvm.buildtools.native' version '0.10.3'
}
```

#### New Configuration Section

Added `graalvmNative` configuration block with native image settings.

#### New Tasks

| Task | Description |
|------|-------------|
| `nativeCompile` | Compile client to native executable |
| `nativeServerCompile` | Compile server to native executable |
| `nativeRun` | Compile and run native client |
| `nativeClientDist` | Create native client distribution |
| `nativeClientDistZip` | Create zip of native distribution |

### settings.gradle

Updated plugin management to include GraalVM plugin.

### New Configuration Files

Added native image configuration in:
```
src/main/resources/META-INF/native-image/
├── reflect-config.json
├── jni-config.json
├── resource-config.json
├── proxy-config.json
├── serialization-config.json
└── native-image.properties
```

## Platform Support

### Supported Platforms

| Platform | JAR | Native Executable |
|----------|-----|-------------------|
| Windows x64 | ✅ | ✅ |
| Windows ARM64 | ✅ | ❌ (not yet by GraalVM) |
| Linux x64 | ✅ | ✅ |
| Linux ARM64 | ✅ | ✅ |
| macOS x64 (Intel) | ✅ | ✅ |
| macOS ARM64 (Apple Silicon) | ✅ | ✅ |

### Cross-Compilation

**Important:** Native executables are platform-specific. You **must** build on each platform:

- Build on Windows → Windows .exe
- Build on Linux → Linux executable
- Build on macOS → macOS executable

GraalVM does **not** support cross-compilation.

## Troubleshooting

### "Native executable fails to run"

**Symptoms:** Native executable crashes or gives errors about missing classes/resources.

**Solutions:**
1. Check configuration files in `src/main/resources/META-INF/native-image/`
2. Add missing classes to `reflect-config.json`
3. Add missing resources to `resource-config.json`
4. Use the tracing agent to generate configuration: `./gradlew -Pagent run`

### "Build takes too long / runs out of memory"

**Symptoms:** Native image build takes hours or fails with OutOfMemoryError.

**Solutions:**
1. Increase build memory in `build.gradle`: Change `-J-Xmx16g` to `-J-Xmx24g`
2. Use a machine with more RAM (16GB+ recommended)
3. Close other applications during build
4. Use incremental builds (second build is much faster)

### "Cannot find native-image tool"

**Symptoms:** Error about native-image not being found.

**Solutions:**
1. Re-download GraalVM: `./gradlew clean downloadJdk`
2. Verify installation: `./libraries/jdk-25/bin/native-image --version`
3. If still missing, download GraalVM manually from https://www.graalvm.org/downloads/

### "Why JDK 21 instead of JDK 25?"

GraalVM releases lag behind the latest JDK versions. JDK 21 is:
- An LTS (Long-Term Support) release
- Fully supported by GraalVM Native Image
- Stable and production-ready

When GraalVM for JDK 25 becomes available, we can upgrade.

## Compatibility Notes

### Code Compatibility

The switch from JDK 25 to JDK 21 means:
- You lose some Java 25 features (not many, as 25 is recent)
- All Java 21 features are available
- Most code should work without changes

### Library Compatibility

All dependencies remain the same. Libraries don't care whether you're using Temurin or GraalVM - both are standard JDK implementations.

### Runtime Compatibility

- JAR files built with GraalVM run on any JDK 21+ JRE
- Native executables only run on the platform they were built for

## Rollback Plan

If you need to go back to Temurin JDK 25:

1. Checkout the commit before this migration
2. Or manually edit:
   - `build.gradle`: Remove GraalVM plugin and configuration
   - `settings.gradle`: Remove GraalVM plugin management
   - `libraries/download-jdk.sh`: Change URLs back to Temurin
   - `libraries/download-jdk.ps1`: Change URLs back to Temurin

## Benefits Summary

### For End Users
- ✅ Faster startup with native executables
- ✅ Lower memory usage
- ✅ No JDK installation needed (native only)
- ✅ Better overall performance
- ⚠️ Larger download size (native includes all dependencies)

### For Developers
- ✅ Same development workflow for JAR builds
- ✅ New option for native compilation
- ✅ Better performance tooling
- ✅ Industry-standard native Java
- ⚠️ Longer initial build time for native
- ⚠️ Must build on each platform separately

## Additional Resources

- [GraalVM Native Image Guide](GRAALVM-NATIVE-IMAGE.md)
- [JDK README](../libraries/JDK-README.md)
- [Main README](../README.md)
- [GraalVM Official Docs](https://www.graalvm.org/latest/docs/)

## Questions?

If you have questions about this migration:
1. Check the documentation in the `docs/` folder
2. Review the GitHub Issues for known problems
3. Open a new issue if you find a bug
