# Building and Running MattMC

## Quick Start

To run the game with all mods (Fabric, Sodium, Iris, Distant Horizons):

```bash
./gradlew runClient
```

This single command will:
1. Build all mod JARs (Fabric Loader, Sodium, Iris, Distant Horizons)
2. Copy them to `run/mods/`
3. Launch Minecraft with Fabric Loader

## Important Notes

### Distant Horizons Requires Rebuild

**CRITICAL**: Distant Horizons is built as a separate mod JAR. After making any changes to files in `modules/distant-horizons-2.3.4b/`, you MUST rebuild before running:

```bash
./gradlew distantHorizonsJar runClient
```

Or use the provided script:
```bash
./RunDev.sh
```

### Why LODs Don't Render

If LODs are not rendering (VBO count 0/0 in `/dh debug`), the most common causes are:

1. **DH JAR not built**: Run `./gradlew distantHorizonsJar` first
2. **DH JAR not in run/mods**: The `runClient` task copies JARs - use it instead of running manually
3. **Mixins not applying**: DH's mixins must apply to `LevelRenderer` for rendering to work

### Verifying DH is Working

1. Commands should work: `/dh info`, `/dh debug`, `/dh config`
2. Check `/dh debug` output:
   - `rendering: yes` - DH thinks it should render
   - `VBO Render Count: [X/Y]` - Should be non-zero if LODs are uploading
   - `Generic Obj #: X/Y, Cube #: X/Y` - LOD data being generated

3. Check that DH JAR exists in run/mods:
```bash
ls -l run/mods/distanthorizons-*.jar
```

### Building Individual Components

```bash
# Build Fabric Loader
./gradlew fabricLoaderJar

# Build game JAR (decompiled Minecraft)
./gradlew gameJar

# Build Sodium
./gradlew sodiumJar

# Build Iris
./gradlew irisJar

# Build Distant Horizons
./gradlew distantHorizonsJar

# Build everything and run
./gradlew runClient
```

### Clean Build

If you encounter issues, try a clean build:

```bash
./gradlew clean
./gradlew runClient
```

This will rebuild everything from scratch.
