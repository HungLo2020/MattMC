# Bundled JDK Setup

This project uses **GraalVM JDK 21** bundled with the application to ensure consistent Java runtime across all environments and enable native image compilation.

## Why GraalVM?

GraalVM is a high-performance JDK distribution that includes:
- **Native Image**: Compile Java applications to native executables
- **Faster Performance**: Advanced optimizations and compiler
- **Lower Memory**: More efficient memory usage
- **Polyglot Support**: Run multiple languages in one runtime

This project uses GraalVM to support **native compilation**, allowing you to build standalone executables without requiring a JVM.

## Supported Platforms

The automatic JDK download script supports:
- **Linux**: x64 (Intel/AMD) and ARM64 (aarch64)
- **macOS**: x64 (Intel) and ARM64 (Apple Silicon)
- **Windows**: x64 (Intel/AMD) only (ARM64 not officially supported by GraalVM yet)

## Automatic Download (Windows/Linux/macOS)

The GraalVM JDK is automatically downloaded when needed on all major platforms:

### Windows
```cmd
gradlew downloadJdk
gradlew copyJdkToRun
```

### Linux/macOS
```bash
./gradlew downloadJdk
./gradlew copyJdkToRun
```

The JDK is automatically downloaded and copied when you run:
- `./gradlew runClient` (or `gradlew runClient` on Windows)
- `./gradlew runServer`
- `./gradlew runServerGui`
- `./gradlew clientDist`
- `./gradlew clientDistZip`
- `./gradlew nativeCompile` (for native executable compilation)
- `./gradlew nativeClientDist`

## Native Image Compilation

GraalVM includes the `native-image` tool for compiling Java applications to native executables. This is automatically available after downloading the JDK.

To verify native-image is available:

### Linux/macOS
```bash
./libraries/jdk-25/bin/native-image --version
```

### Windows
```cmd
libraries\jdk-25\bin\native-image.cmd --version
```

For more information on native compilation, see [GraalVM Native Image Guide](../docs/GRAALVM-NATIVE-IMAGE.md).

## Manual Download (Alternative Method)

If automatic download doesn't work, download manually:

1. Go to: https://www.graalvm.org/downloads/
2. Select:
   - **Version:** GraalVM for JDK 21
   - **Operating System:** Your OS (Linux, Windows, macOS)
   - **Architecture:** x64 or aarch64 (ARM)

3. Download and extract to `libraries/jdk-25/`

### Linux:
```bash
cd libraries
tar -xzf /path/to/graalvm-jdk-21_*.tar.gz
mv graalvm-jdk-21.0.5+9.1 jdk-25
```

### macOS:
```bash
cd libraries
tar -xzf /path/to/graalvm-jdk-21_*.tar.gz
# On macOS, the JDK is in Contents/Home subdirectory
mv graalvm-jdk-21.0.5+9.1/Contents/Home jdk-25
```

### Windows:
```cmd
cd libraries
"C:\Program Files\7-Zip\7z.exe" x C:\path\to\graalvm-jdk-21_*.zip
move graalvm-jdk-21.0.5+9.1 jdk-25
```

## Directory Structure

```
MattMC/
├── libraries/
│   ├── jdk-25/              # Bundled GraalVM JDK (not committed to git)
│   │   ├── bin/
│   │   │   ├── java         # Java executable (Linux/macOS)
│   │   │   ├── java.exe     # Java executable (Windows)
│   │   │   ├── native-image # Native Image compiler (Linux/macOS)
│   │   │   └── native-image.cmd # Native Image compiler (Windows)
│   │   ├── lib/
│   │   └── ...
│   ├── download-jdk.sh      # Automatic GraalVM download script (Linux/macOS)
│   └── download-jdk.ps1     # Automatic GraalVM download script (Windows)
└── run/
    └── jdk-25/              # JDK copied here at runtime (not committed to git)
        ├── bin/
        └── ...
```

## Why Bundle GraalVM?

1. **Consistency:** Everyone uses the same Java version
2. **Portability:** No need to install Java separately
3. **Native Compilation:** Build standalone executables without JVM
4. **Performance:** Advanced JIT compiler and optimizations
5. **Lower Memory:** More efficient memory usage than standard JDK

## Note

The JDK is **not committed to the git repository** because it's too large (~200MB). It's automatically downloaded when needed or can be manually downloaded and placed in the correct location.

## Distribution

When building distributions (`clientDist` or `clientDistZip`), the bundled GraalVM JDK can be included in the package, ensuring users don't need to install Java separately.

For native executable distributions (`nativeClientDist`), no JDK is needed at all - the application is compiled to a standalone executable.

## Version Note

This project uses **GraalVM for JDK 21** instead of JDK 25 because:
- GraalVM typically lags behind the latest JDK releases
- JDK 21 is an LTS (Long-Term Support) release with excellent stability
- Native Image support is mature and well-tested on JDK 21

## Technical Details

### Gradle Toolchain Integration

The project uses Gradle's Java toolchain for compilation, but runtime tasks (runClient, runServer, etc.) explicitly use the bundled JDK by:
1. Setting the `executable` property to the bundled JDK path
2. Disabling the toolchain launcher with `javaLauncher.set(provider { null })`

This ensures the bundled JDK is always used at runtime without conflicting with Gradle's toolchain system.

### GraalVM Native Image

The GraalVM Native Image plugin is configured in `build.gradle` and uses configuration files in:
```
src/main/resources/META-INF/native-image/
```

These files tell the native image compiler which classes need reflection, which resources to include, and other build-time configuration.
