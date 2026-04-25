# Bundled JDK Setup

This project uses Temurin OpenJDK 25 bundled with the application to ensure consistent Java runtime across all environments.

## Supported Platforms

The automatic JDK download script supports:
- **Linux**: x64 (Intel/AMD) and ARM64 (aarch64)
- **macOS**: x64 (Intel) and ARM64 (Apple Silicon)
- **Windows**: x64 (Intel/AMD) and ARM64

## Automatic Download (Windows/Linux/macOS)

The JDK is automatically downloaded when needed on all major platforms:

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

## Manual Download (Alternative Method)

If automatic download doesn't work, download manually:

1. Go to: https://adoptium.net/temurin/releases/
2. Select:
   - **Version:** 25
   - **Operating System:** Your OS (Linux, Windows, macOS)
   - **Architecture:** x64 or aarch64 (ARM)
   - **Package Type:** JDK
   - **Archive Type:** .tar.gz (Linux/macOS) or .zip (Windows)

3. Download and extract to: `libraries/jdk/<platform>/`
    (e.g. `libraries/jdk/win-x64/`, `libraries/jdk/linux-x64/`)

### Linux:
```bash
cd libraries
tar -xzf /path/to/OpenJDK25U-jdk_*.tar.gz
mkdir -p jdk/linux-x64
mv jdk-25.0.1+8/* jdk/linux-x64/
```

### macOS:
```bash
cd libraries
tar -xzf /path/to/OpenJDK25U-jdk_*.tar.gz
# On macOS, the JDK is in Contents/Home subdirectory
mkdir -p jdk/mac-aarch64
mv jdk-25.0.1+8/Contents/Home/* jdk/mac-aarch64/
```

### Windows:
```cmd
cd libraries
"C:\Program Files\7-Zip\7z.exe" x C:\path\to\OpenJDK25U-jdk_*.zip
mkdir jdk\win-x64
xcopy /E /I jdk-25.0.1+8\* jdk\win-x64\
```

## Directory Structure

```
MattMC/
├── libraries/
│   ├── jdk/                 # Multi-platform JDK layout
│   │   ├── win-x64/
│   │   ├── linux-x64/
│   │   └── mac-aarch64/
│   │       ├── bin/
│   │       │   ├── java         # Java executable (Linux/macOS)
│   │       │   └── java.exe     # Java executable (Windows)
│   │       ├── lib/
│   │       └── ...
│   ├── download-jdk.sh      # Automatic download script (Linux/macOS)
│   └── download-jdk.ps1     # Automatic download script (Windows)
└── run/
    └── jdk/
        ├── win-x64/         # Bundled JDK selected by Windows launchers
        ├── linux-x64/       # Bundled JDK selected by Linux launchers
        └── mac-aarch64/     # Bundled JDK selected by macOS launchers
```

## Why Bundle JDK?

1. **Consistency:** Everyone uses the same Java version
2. **Portability:** No need to install Java separately
3. **Version Control:** Specific Java 25 features and behaviors are guaranteed
4. **Performance:** Java 25 includes Compact Object Headers (JEP 519) for improved memory efficiency and performance

## Note

The JDK is **not committed to the git repository** because it's too large (~200MB). It's automatically downloaded when needed or can be manually downloaded and placed in the correct location.

## Distribution

When building distributions (`clientDist` or `clientDistZip`), bundled JDKs are included under platform-labeled paths (`run/jdk/<platform>/`) and launchers select the matching runtime for the host OS/architecture.

## Technical Details

### Gradle Toolchain Integration

The project uses Gradle's Java toolchain for compilation, but runtime tasks (runClient, runServer, etc.) explicitly use the bundled JDK by:
1. Setting the `executable` property to the bundled JDK path
2. Disabling the toolchain launcher with `javaLauncher.set(provider { null })`

This ensures the bundled JDK is always used at runtime without conflicting with Gradle's toolchain system.
