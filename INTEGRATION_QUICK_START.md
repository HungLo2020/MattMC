# Quick Start: Mod Integration Commands

This is a condensed guide with specific commands to start integrating mods into the main Minecraft build.

## Current Status Check

```bash
# See how code is currently organized
tree -L 3 -d modules/
tree -L 3 -d src/main/java/net/

# Count files in each location
find modules/sodium-1.21.9 -name "*.java" | wc -l     # ~800+ files
find modules/Iris-1.21.9 -name "*.java" | wc -l       # ~700+ files  
find src/main/java/net/sodium -name "*.java" | wc -l  # ~40 files (stubs)
find src/main/java/net/iris -name "*.java" | wc -l    # ~7 files (API stubs)
```

## Phase 1: Sodium Integration (Recommended First Step)

### 1. Backup Current State
```bash
git checkout -b sodium-integration
git add .
git commit -m "Baseline before Sodium integration"
```

### 2. Copy Sodium Source Files

```bash
# Copy all Sodium source files to main source set
# Common source (core sodium code)
cp -r modules/sodium-1.21.9/common/src/main/java/* src/main/java/

# API source  
cp -r modules/sodium-1.21.9/common/src/api/java/* src/main/java/

# Boot source
cp -r modules/sodium-1.21.9/common/src/boot/java/* src/main/java/

# Fabric integration source
cp -r modules/sodium-1.21.9/fabric/src/main/java/* src/main/java/

# Resources (mixins, fabric.mod.json, etc.)
cp -r modules/sodium-1.21.9/common/src/main/resources/* src/main/resources/
cp -r modules/sodium-1.21.9/fabric/src/main/resources/* src/main/resources/
```

### 3. Handle Duplicate Files

You'll have conflicts between:
- `src/main/java/net/sodium/api/` (your stubs - ~40 files)
- `modules/sodium-1.21.9/.../net/caffeinemc/mods/sodium/` (full Sodium - different package!)

**Resolution**: These are different packages! Sodium uses `net.caffeinemc.mods.sodium`, not `net.sodium`.

Your `net/sodium/api/` appears to be custom API classes. You can keep both.

### 4. Test Compilation

```bash
# Clean build to ensure no cached artifacts
./gradlew clean

# Try compiling just the classes
./gradlew compileJava

# If successful, compile everything
./gradlew classes
```

**Common errors you might see**:
1. Missing Fabric API classes → Check `src/main/java/net/fabricmc/fabric/api/`
2. Mixin errors → Ensure mixin configs are in `src/main/resources/`
3. Duplicate class definitions → Resolve package conflicts

### 5. Update build.gradle

**Option A: Keep Sodium source set, just test classpath**

No changes needed yet. Test first with:
```bash
./gradlew jar
java -cp "build/libs/MattMC-1.21.10.jar:$(./gradlew -q printClasspath)" net.minecraft.server.Main --version
```

**Option B: Merge Sodium into main source set**

Edit `build.gradle` around line 385-400, comment out the Sodium source set:
```gradle
sourceSets {
    // Comment out Sodium source set since it's now in main
    /*
    sodium {
        java {
            srcDirs = [
                "${sodiumDir}/common/src/main/java",
                "${sodiumDir}/common/src/api/java",
                "${sodiumDir}/common/src/boot/java",
                "${sodiumDir}/fabric/src/main/java"
            ]
        }
        resources {
            srcDirs = [
                "${sodiumDir}/common/src/main/resources",
                "${sodiumDir}/fabric/src/main/resources"
            ]
        }
    }
    */
}
```

Also comment out Sodium configuration extensions (lines 506-508):
```gradle
/*
sodiumImplementation.extendsFrom implementation
sodiumCompileOnly.extendsFrom compileOnly
sodiumRuntimeOnly.extendsFrom runtimeOnly
*/
```

And Sodium classpath additions (lines 528-531):
```gradle
/*
sourceSets.sodium.compileClasspath += sourceSets.fabricLoader.output
sourceSets.sodium.compileClasspath += sourceSets.main.output
sourceSets.sodium.runtimeClasspath += sourceSets.fabricLoader.output
sourceSets.sodium.runtimeClasspath += sourceSets.main.output
*/
```

### 6. Update JAR Task

Comment out the separate `sodiumJar` task (lines 629-649):
```gradle
/*
tasks.register('sodiumJar', Jar) {
    // ... commented out
}
*/
```

### 7. Update Runtime Configuration

Modify the `runClient` task to not load Sodium from mods directory.

Around line 980, change the mod copying section:
```gradle
// Copy Sodium and Iris mod JARs to mods folder
copy {
    from file("${buildDir}/mods")
    into modsDir
    include '*.jar'
    // Exclude Sodium since it's now integrated
    exclude 'sodium-*.jar'
}
```

And update the dependencies (line 926):
```gradle
// Remove 'sodiumJar' from dependencies since Sodium is now in main JAR
def dependencies = ['fabricLoaderJar', 'gameJar', 'irisJar', 'copyJdkToRun', 'shaderPackZip']
```

### 8. Test Runtime

```bash
# Build everything
./gradlew build

# Run client (Sodium should be internal, Iris still external)
./gradlew runClient
```

**Verify Sodium is working**:
- Launch game
- Video Settings → Should see Sodium's optimized settings
- F3 debug menu → Should mention Sodium

## Phase 2: Update Iris to Use Internal Sodium

Once Sodium is integrated, Iris needs to reference it from classpath instead of separate JAR.

### Update Iris Source Set (if keeping separate initially)

Around line 534-539 in build.gradle:
```gradle
// Iris depends on Fabric Loader, Minecraft, and Sodium
sourceSets.iris.compileClasspath += sourceSets.fabricLoader.output
sourceSets.iris.compileClasspath += sourceSets.main.output
// Remove this line if Sodium is in main:
// sourceSets.iris.compileClasspath += sourceSets.sodium.output
sourceSets.iris.runtimeClasspath += sourceSets.fabricLoader.output
sourceSets.iris.runtimeClasspath += sourceSets.main.output
// sourceSets.iris.runtimeClasspath += sourceSets.sodium.output
```

Since Sodium is now in main output, Iris automatically gets it through `sourceSets.main.output`.

## Phase 3: Full Iris Integration (Optional)

Repeat the same process as Sodium:

```bash
# Copy Iris source
cp -r modules/Iris-1.21.9/common/src/main/java/* src/main/java/
cp -r modules/Iris-1.21.9/common/src/api/java/* src/main/java/
cp -r modules/Iris-1.21.9/fabric/src/main/java/* src/main/java/

# Copy Iris resources (shaders, mixins, etc.)
cp -r modules/Iris-1.21.9/common/src/main/resources/* src/main/resources/
cp -r modules/Iris-1.21.9/fabric/src/main/resources/* src/main/resources/
```

Then follow the same build.gradle update process.

## Testing Checklist

After each integration step:

- [ ] `./gradlew clean build` succeeds
- [ ] `./gradlew runClient` launches without errors
- [ ] Game menu appears
- [ ] Video settings show mod features (Sodium options, Iris shaders)
- [ ] Can load a world
- [ ] Rendering works correctly
- [ ] F3 debug shows mod information
- [ ] No class loading errors in logs

## Rollback if Needed

```bash
# If integration doesn't work, rollback
git checkout build.gradle
git clean -fd src/main/java
git clean -fd src/main/resources
git checkout src/

# Restore to working state
./gradlew clean build
```

## Package Structure Reference

### Before Integration
```
modules/
├── sodium-1.21.9/
│   └── (separate JAR built)
├── Iris-1.21.9/
│   └── (separate JAR built)
└── distant-horizons/
    └── (separate JAR built)

src/main/java/
└── net/
    ├── minecraft/     (main Minecraft code)
    ├── sodium/api/    (your custom stubs ~40 files)
    └── iris/api/      (your custom stubs ~7 files)
```

### After Sodium Integration
```
modules/
├── sodium-1.21.9/     (kept as reference only)
├── Iris-1.21.9/       (still builds separate JAR)
└── distant-horizons/  (still builds separate JAR)

src/main/java/
└── net/
    ├── minecraft/                  (main Minecraft code)
    ├── caffeinemc/mods/sodium/     (full Sodium ~800+ files) ← NEW
    ├── sodium/api/                 (your custom stubs ~40 files)
    └── iris/api/                   (your custom stubs ~7 files)
```

### After Full Integration
```
modules/
├── sodium-1.21.9/     (reference only)
├── Iris-1.21.9/       (reference only)
└── distant-horizons/  (reference only)

src/main/java/
└── net/
    ├── minecraft/                  (main Minecraft code)
    ├── caffeinemc/mods/sodium/     (full Sodium ~800+ files)
    ├── irisshaders/iris/           (full Iris ~700+ files) ← NEW
    ├── sodium/api/                 (your custom stubs)
    └── iris/api/                   (your custom stubs)
```

## Notes

1. **Package names are important**: 
   - Sodium: `net.caffeinemc.mods.sodium`
   - Iris: `net.irisshaders.iris`  
   - Your stubs: `net.sodium.api`, `net.iris.api` (different packages!)

2. **Mixins are preserved**: 
   - Mixin JSON files go in `src/main/resources/`
   - Mixin classes are in the normal Java source tree
   - Fabric Loader's mixin engine still processes them

3. **Fabric Loader stays** (initially):
   - Still needed for mixin processing
   - Still needed for mod initialization
   - Can be removed later if desired (Phase 4)

4. **Dependencies already present**:
   - JCPP, ANTLR (for Iris shaders) - already in build.gradle
   - ASM, Mixin libraries - already in build.gradle
   - No new dependencies needed!

## Expected Benefits

Once integrated:
- **Faster startup**: No mod JAR scanning
- **Simpler distribution**: One JAR instead of many
- **Easier debugging**: All code in one codebase
- **Better IDE support**: Navigate between Minecraft and mod code seamlessly

## Potential Issues

1. **Initialization order**: Mods might initialize in wrong order
   - Solution: Manually call entrypoints in correct order

2. **Resource conflicts**: Multiple mods might have same resource paths
   - Solution: Check for duplicate files after copying

3. **Mixin conflicts**: Mixins might target same methods
   - Solution: Review mixin configs, resolve conflicts

4. **Fabric API expectations**: Mods expect certain Fabric APIs
   - Solution: Ensure stubs in `src/main/java/net/fabricmc/` are complete

## Next Steps After Sodium Works

1. Integrate Iris (depends on Sodium being available)
2. Integrate Distant Horizons (optional)
3. Consider removing Fabric Loader (advanced)
4. Update documentation
5. Create unified distribution

Good luck with the integration! Start with Sodium and iterate from there.
