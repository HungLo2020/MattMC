# GraalVM Native Image Compilation Guide

This document explains how to build and use native executables for MattMC using GraalVM Native Image.

## What is GraalVM Native Image?

GraalVM Native Image allows you to compile Java applications ahead-of-time into standalone native executables. These executables:

- Start up **much faster** than traditional JVM applications
- Use **less memory** at runtime
- Don't require a JVM to be installed
- Are **platform-specific** (you need to build separately for Windows, Linux, and macOS)

## Prerequisites

### Automatic Installation (Recommended)

The project is configured to automatically download GraalVM when you run build tasks:

```bash
# Downloads GraalVM automatically
./gradlew downloadJdk
```

### Manual Installation

If you prefer to install GraalVM manually:

1. Download GraalVM from: https://www.graalvm.org/downloads/
2. Install it to `libraries/jdk-25/`
3. Verify that `native-image` tool is available:
   ```bash
   ./libraries/jdk-25/bin/native-image --version
   ```

## Building Native Executables

### Build Client Executable

To build a native executable for the Minecraft client:

```bash
# Build the native executable
./gradlew nativeCompile

# Or build and create a distribution package
./gradlew nativeClientDist
```

The native executable will be created at:
- Linux/macOS: `build/native/nativeCompile/MattMC`
- Windows: `build/native/nativeCompile/MattMC.exe`

### Build Server Executable

To build a native executable for the dedicated server:

```bash
./gradlew nativeServerCompile
```

### Build Distribution Packages

To create a complete distribution package with native executables:

```bash
# Client distribution
./gradlew nativeClientDistZip

# This creates: build/distributions/MattMC-Native-Client.zip
```

## Running Native Executables

### Development Mode

During development, you can test native compilation using:

```bash
# Run with native compilation
./DevUtils/RunDev.sh native

# Run normally (JAR-based)
./DevUtils/RunDev.sh
```

### Production Mode

In production distributions, use the native launcher scripts:

```bash
# Linux/macOS Client
./run-mattmc-native.sh

# Windows Client
run-mattmc-native.bat

# Linux/macOS Server
./server/run-server-native.sh

# Windows Server
server\run-server-native.bat
```

## Important Notes

### First Build Time

The first native compilation can take **10-15 minutes** or longer, depending on your system. Subsequent builds are faster due to caching.

### Memory Requirements

Native image compilation requires significant memory:
- Minimum: 8GB RAM
- Recommended: 16GB RAM

The build process is configured with `-J-Xmx16g` to allocate enough memory.

### Platform-Specific Builds

Native executables are **platform-specific**. You must build on each platform:

- **Windows**: Build on Windows for Windows executables
- **Linux**: Build on Linux for Linux executables  
- **macOS**: Build on macOS for macOS executables

Cross-compilation is **not supported** by GraalVM Native Image.

### Configuration Files

Native image configuration is located in:
```
src/main/resources/META-INF/native-image/
├── reflect-config.json       # Reflection configuration
├── jni-config.json          # JNI configuration
├── resource-config.json     # Resources to include
├── proxy-config.json        # Dynamic proxy configuration
├── serialization-config.json # Serialization configuration
└── native-image.properties  # Build arguments
```

These files tell GraalVM which classes need reflection, which resources to include, etc.

### Updating Configuration

If you add new code that uses reflection or loads resources dynamically, you may need to update the configuration files. You can generate these automatically using the GraalVM agent:

```bash
# Run with the tracing agent to generate configuration
./gradlew -Pagent run

# This will update the configuration files in src/main/resources/META-INF/native-image/
```

## Troubleshooting

### Build Fails with OutOfMemoryError

Increase the memory allocated to the build process in `build.gradle`:

```groovy
buildArgs.addAll(
    '-J-Xmx24g',  // Increase from 16g to 24g
    // ... other args
)
```

### Missing Classes at Runtime

If you get `ClassNotFoundException` at runtime, add the class to `reflect-config.json`:

```json
{
  "name": "com.example.MissingClass",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
```

### Missing Resources

If resources are not found, add them to `resource-config.json`:

```json
{
  "pattern": "path/to/resource/.*"
}
```

### LWJGL/OpenGL Issues

Native executables with LWJGL may have issues on some platforms. The configuration includes:

- JNI configuration for LWJGL native libraries
- Resource inclusion for all assets
- Platform-specific build arguments

If you encounter issues, check the GraalVM documentation for LWJGL native image support.

## Comparison: JAR vs Native

### JAR-based (Traditional)

**Pros:**
- Cross-platform (same JAR runs everywhere)
- Faster development iteration
- Standard JVM features available

**Cons:**
- Requires JDK/JRE installation
- Slower startup time
- Higher memory usage
- JIT compilation overhead

### Native Executable (GraalVM)

**Pros:**
- Much faster startup
- Lower memory usage
- No JDK/JRE required
- Better performance for CPU-intensive tasks

**Cons:**
- Platform-specific builds required
- Longer build time
- Some JVM features not available
- Larger executable size

## Build Task Reference

| Task | Description |
|------|-------------|
| `downloadJdk` | Downloads GraalVM JDK |
| `nativeCompile` | Compiles client to native executable |
| `nativeServerCompile` | Compiles server to native executable |
| `nativeClientDist` | Creates native client distribution |
| `nativeClientDistZip` | Creates zip of native client distribution |

## Additional Resources

- GraalVM Documentation: https://www.graalvm.org/latest/docs/
- Native Image Guide: https://www.graalvm.org/latest/reference-manual/native-image/
- Build Configuration: https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildConfiguration/

## Support

For issues specific to this project's GraalVM configuration, please check:
1. The configuration files in `src/main/resources/META-INF/native-image/`
2. The GraalVM configuration in `build.gradle`
3. The GitHub Issues page for known problems
