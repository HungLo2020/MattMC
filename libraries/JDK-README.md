# Bundled JDK Setup

This project uses Temurin OpenJDK 21 bundled with the application to ensure consistent Java runtime across all environments.

## Automatic Download (Linux)

On Linux systems, the JDK is automatically downloaded when needed:

```bash
# Download JDK to libraries/jdk-21
./gradlew downloadJdk

# Copy JDK to run directory
./gradlew copyJdkToRun
```

The JDK is automatically downloaded and copied when you run:
- `./gradlew runClient`
- `./gradlew runServer`
- `./gradlew runServerGui`
- `./gradlew clientDist`
- `./gradlew clientDistZip`

## Manual Download (Windows/macOS/Linux)

If automatic download doesn't work or you're on Windows/macOS, download manually:

1. Go to: https://adoptium.net/temurin/releases/
2. Select:
   - **Version:** 21 LTS
   - **Operating System:** Your OS (Linux, Windows, macOS)
   - **Architecture:** x64 or aarch64 (ARM)
   - **Package Type:** JDK
   - **Archive Type:** .tar.gz (Linux/macOS) or .zip (Windows)

3. Download and extract to `libraries/jdk-21/`

### Linux/macOS:
```bash
cd libraries
tar -xzf /path/to/OpenJDK21U-jdk_*.tar.gz
mv jdk-21.0.5+11 jdk-21
```

### Windows:
```cmd
cd libraries
"C:\Program Files\7-Zip\7z.exe" x C:\path\to\OpenJDK21U-jdk_*.zip
move jdk-21.0.5+11 jdk-21
```

## Directory Structure

```
MattMC/
├── libraries/
│   ├── jdk-21/              # Bundled JDK (not committed to git)
│   │   ├── bin/
│   │   │   └── java         # Java executable
│   │   ├── lib/
│   │   └── ...
│   └── download-jdk.sh      # Automatic download script (Linux)
└── run/
    └── jdk-21/              # JDK copied here at runtime (not committed to git)
        ├── bin/
        └── ...
```

## Why Bundle JDK?

1. **Consistency:** Everyone uses the same Java version
2. **Portability:** No need to install Java separately
3. **Version Control:** Specific Java 21 features and behaviors are guaranteed

## Note

The JDK is **not committed to the git repository** because it's too large (~200MB). It's automatically downloaded when needed or can be manually downloaded and placed in the correct location.

## Distribution

When building distributions (`clientDist` or `clientDistZip`), the bundled JDK is automatically included in the package, ensuring users don't need to install Java separately.

## Technical Details

### Gradle Toolchain Integration

The project uses Gradle's Java toolchain for compilation, but runtime tasks (runClient, runServer, etc.) explicitly use the bundled JDK by:
1. Setting the `executable` property to the bundled JDK path
2. Disabling the toolchain launcher with `javaLauncher.set(provider { null })`

This ensures the bundled JDK is always used at runtime without conflicting with Gradle's toolchain system.
