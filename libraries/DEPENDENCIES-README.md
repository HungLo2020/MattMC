# Bundled Dependencies for Offline Builds

This directory contains tooling to enable offline builds of MattMC in network-restricted environments.

## Problem

MattMC requires dependencies from several Maven repositories:
- `libraries.minecraft.net` - Mojang's official library server
- `maven.fabricmc.net` - Fabric Maven repository
- `maven.minecraftforge.net` - MinecraftForge mirrors
- `jitpack.io` - JitPack repository

In sandboxed or air-gapped environments, these repositories may be blocked, preventing builds.

## Solution: Bundled Dependencies

The `download-dependencies.sh` script downloads all required dependencies from blocked repositories and saves them to `libraries/deps/`. The build system automatically detects and uses these bundled dependencies.

## Usage

### Step 1: Download Dependencies (requires unrestricted internet)

Run this script on a machine with full internet access:

```bash
cd libraries
./download-dependencies.sh
```

This downloads all required dependencies (~30+ JARs total):

**Mojang Libraries:**
- brigadier-1.3.10.jar
- datafixerupper-8.0.16.jar
- authlib-6.0.55.jar
- logging-1.2.7.jar
- jtracy-1.0.29.jar
- blocklist-1.0.10.jar
- patchy-2.2.10.jar
- text2speech-1.17.9.jar
- launchwrapper-1.12.jar

**Fabric Loader Dependencies (for integrated Fabric source):**
- sponge-mixin-0.16.5+mixin.0.8.7.jar (org.spongepowered.asm.* - bytecode transformation)
- tiny-remapper-0.11.2.jar (net.fabricmc.tinyremapper.* - class remapping)
- class-tweaker-0.2.jar (net.fabricmc.classtweaker.* - access modification)
- mapping-io-0.7.1.jar (net.fabricmc.mappingio.* - mapping I/O)
- mixinextras-fabric-0.5.0.jar (com.llamalad7.mixinextras.* - mixin extensions)
- access-widener-2.1.0.jar (access widening)
- tiny-mappings-parser-0.3.0+build.17.jar (legacy mapping parser)

**ASM Libraries (bytecode manipulation, required by Mixin):**
- asm-9.9.jar
- asm-analysis-9.9.jar
- asm-commons-9.9.jar
- asm-tree-9.9.jar
- asm-util-9.9.jar

### Step 2: Transfer to Restricted Environment (if needed)

If you're working in a restricted environment, transfer the entire `libraries/deps/` directory:

```bash
# Package dependencies
tar -czf mattmc-deps.tar.gz libraries/deps/

# Transfer mattmc-deps.tar.gz to restricted environment

# Extract in MattMC directory
tar -xzf mattmc-deps.tar.gz
```

### Step 3: Build Offline

Once `libraries/deps/` exists, the build automatically uses bundled dependencies:

```bash
./gradlew build
```

No `--offline` flag needed! The build.gradle detects the deps directory and uses it automatically.

## How It Works

The `build.gradle` file checks for `libraries/deps/` at configuration time:

1. **If `libraries/deps/` exists:** Uses bundled JARs via `files()` dependencies
2. **If `libraries/deps/` doesn't exist:** Uses remote Maven repositories (normal mode)

This provides seamless fallback:
- **Development machines** can use remote repos normally
- **Restricted environments** use bundled dependencies after running the script

## Integrated Fabric Loader

MattMC includes the Fabric Loader source code integrated directly into the project for:
- Editable mod loading functionality
- Direct integration with Minecraft classes
- No external fabric-loader.jar dependency at runtime

The Fabric Loader source is located at `fabric-loader-0.18.2/` and is compiled as part of the main project.

## Updating Dependencies

If dependency versions change in `build.gradle`:

1. Update the URLs in `download-dependencies.sh`
2. Re-run the script to download new versions
3. Update the JAR filenames in `build.gradle` dependencies section

## File Structure

```
libraries/
├── download-dependencies.sh    # Downloads all dependencies
├── DEPENDENCIES-README.md      # This file
└── deps/                        # Downloaded JARs (gitignored)
    ├── brigadier-1.3.10.jar
    ├── datafixerupper-8.0.16.jar
    ├── authlib-6.0.55.jar
    ├── sponge-mixin-0.16.5+mixin.0.8.7.jar
    ├── tiny-remapper-0.11.2.jar
    ├── class-tweaker-0.2.jar
    ├── mapping-io-0.7.1.jar
    └── ... (more JARs)
```

## Troubleshooting

### "Cannot reach libraries.minecraft.net" or "Cannot reach maven.fabricmc.net"

The script requires unrestricted internet access. Run it on a different machine and transfer the `deps/` directory.

### Build still tries to download from remote repos

Make sure `libraries/deps/` exists and contains JAR files. Check with:

```bash
ls -la libraries/deps/*.jar
```

### Missing transitive dependencies

If you get compilation errors about missing classes, a transitive dependency may be missing. Check the error message for the missing JAR and add it to `download-dependencies.sh`.

### Fabric Loader compilation errors

If Fabric Loader source fails to compile, ensure all Fabric dependencies are downloaded:
- sponge-mixin (provides org.spongepowered.asm.*)
- tiny-remapper (provides net.fabricmc.tinyremapper.*)
- class-tweaker (provides net.fabricmc.classtweaker.*)
- mapping-io (provides net.fabricmc.mappingio.*)
- launchwrapper (provides net.minecraft.launchwrapper.*)

## Comparison with Other Solutions

| Solution | Setup | Repo Size | Updates | Air-gap Ready |
|----------|-------|-----------|---------|---------------|
| **Bundled Deps (This)** | Low | Small (deps gitignored) | Easy | Yes |
| Offline Gradle Cache | Low | Small | Medium | Yes |
| Vendor All Deps | High | Large (+100MB) | Hard | Yes |
| Local Maven Mirror | High | Small | Easy | Yes |

This solution provides the best balance of simplicity and effectiveness for MattMC's use case.

## Notes

- The `deps/` directory is gitignored (too large for version control)
- The download script is versioned and can be committed
- All dependencies from Maven Central are still fetched normally (they're accessible in most environments)
- Only blocked repositories' artifacts are bundled
- The Fabric Loader source at `fabric-loader-0.18.2/` is editable and compiled with the project
