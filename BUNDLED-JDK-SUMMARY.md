# Bundled JDK Implementation Summary

## Overview

The MattMC project now includes **Temurin OpenJDK 21** bundled with the application. This ensures consistent Java runtime across all environments without requiring users to install Java separately.

## Problem Solved

The JDK (~200MB) is too large to commit to GitHub, so the solution automatically downloads it when needed on Linux and provides clear instructions for manual setup on other platforms.

## How It Works

### 1. Automatic Download (Linux)

When you run any Gradle task that needs the JDK:
```bash
./gradlew runClient
./gradlew runServer
./gradlew clientDist
```

The system automatically:
1. Checks if JDK exists in `libraries/jdk-21/`
2. Downloads it if missing (Linux only)
3. Copies it to `run/jdk-21/` when needed
4. Uses the bundled JDK instead of system Java

### 2. Manual Setup (Windows/macOS/Linux)

If automatic download doesn't work or you're on Windows/macOS:
1. Download from: https://adoptium.net/temurin/releases/
2. Extract to `libraries/jdk-21/`
3. See detailed instructions in `libraries/JDK-README.md`

### 3. Distribution Builds

When building distributions:
```bash
./gradlew clientDistZip
```

The bundled JDK is automatically included in the package. Users don't need to install Java!

## Files Added/Modified

### New Files
- `libraries/download-jdk.sh` - Automatic JDK download script (Linux)
- `libraries/verify-jdk-setup.sh` - Verification script
- `libraries/JDK-README.md` - Comprehensive JDK setup documentation
- `BUNDLED-JDK-SUMMARY.md` - This file

### Modified Files
- `build.gradle` - Added `downloadJdk` and `copyJdkToRun` tasks
- `libraries/run-mattmc.sh` - Updated to use bundled JDK
- `libraries/run-mattmc.bat` - Updated to use bundled JDK
- `.gitignore` - Added `libraries/jdk-21/` exclusion
- `README.md` - Added bundled JDK documentation

## Directory Structure

```
MattMC/
├── libraries/
│   ├── jdk-21/              # Bundled JDK (not in git, ~200MB)
│   │   ├── bin/
│   │   │   └── java         # Java executable
│   │   ├── lib/
│   │   └── ...
│   ├── download-jdk.sh      # Auto-download script
│   ├── verify-jdk-setup.sh  # Verification script
│   └── JDK-README.md        # Setup documentation
└── run/
    └── jdk-21/              # JDK copied here at runtime (not in git)
        ├── bin/
        └── ...
```

## Quick Start

### For Development

```bash
# Verify setup
bash libraries/verify-jdk-setup.sh

# Run client (auto-downloads JDK if needed)
./gradlew runClient

# Run server
./gradlew runServer
```

### For Distribution

```bash
# Build distribution with bundled JDK
./gradlew clientDistZip

# Output: build/distributions/MattMC-Client-1.21.10.zip
# This includes the JDK - no Java installation needed!
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `downloadJdk` | Downloads Temurin OpenJDK 21 (Linux only) |
| `copyJdkToRun` | Copies JDK from libraries/ to run/ |
| `runClient` | Runs client with bundled JDK |
| `runServer` | Runs server with bundled JDK |
| `runServerGui` | Runs server GUI with bundled JDK |
| `clientDist` | Creates distribution with bundled JDK |
| `clientDistZip` | Creates zip distribution with bundled JDK |

## Benefits

1. **Consistency** - Everyone uses the exact same Java version
2. **Portability** - No need to install Java separately
3. **Version Control** - Specific Java 21 features guaranteed
4. **User-Friendly** - Distributions work out-of-the-box
5. **Developer-Friendly** - Automatic setup on Linux

## Technical Details

- **JDK Version:** Temurin OpenJDK 21.0.5+11 LTS
- **Download Source:** https://adoptium.net/
- **Architectures Supported:** x64, aarch64 (ARM)
- **Platforms:** Linux (auto), Windows (manual), macOS (manual)
- **Size:** ~200MB (not committed to git)

## Troubleshooting

### JDK Not Found

```bash
# Verify setup
bash libraries/verify-jdk-setup.sh

# Manual download (if auto fails)
# See: libraries/JDK-README.md
```

### Windows/macOS Users

Automatic download is Linux-only. For manual setup:
1. See `libraries/JDK-README.md` for step-by-step instructions
2. Or run: `./gradlew downloadJdk` (will show instructions)

### Verification

```bash
# Check JDK in libraries
ls -lh libraries/jdk-21/bin/java
libraries/jdk-21/bin/java -version

# Check JDK in run directory
ls -lh run/jdk-21/bin/java
run/jdk-21/bin/java -version
```

## Future Updates

To update the JDK version:
1. Edit `libraries/download-jdk.sh`
2. Update `JDK_VERSION` and `JDK_BUILD` variables
3. Run: `rm -rf libraries/jdk-21 && ./gradlew downloadJdk`

## Questions?

See:
- `libraries/JDK-README.md` - Detailed setup instructions
- `README.md` - Main project documentation
- Run: `bash libraries/verify-jdk-setup.sh` - Verify your setup
