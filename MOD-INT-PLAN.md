# Mod Integration Plan - Building All Mods into a Single JAR

## Executive Summary

This document outlines a comprehensive, robust plan to integrate Sodium, Iris, Distant Horizons mods, AND Fabric Loader directly into a single unified JAR, eliminating all separate mod JARs and drastically simplifying the build system.

**Goal**: Combine Minecraft + Fabric Loader + all mods into **ONE SINGLE JAR** for easier iteration, testing, and maintenance.

**Key Insight**: Once all code is moved to the same source set (`src/main/java`), there are NO circular dependencies - Minecraft can reference mod classes freely, and mods can reference Minecraft classes. Everything compiles together in a single pass.

**Solution Approach**: Two-phase build with complete source consolidation, mixin configuration updates, and internal Fabric Loader mod discovery.

---

## Current Architecture Analysis

### Directory Structure

```
MattMC/
├── modules/                          # External mod source code (compiled separately)
│   ├── fabric-loader-0.18.2/        # Fabric mod loader
│   ├── sodium-1.21.9/               # Rendering optimization mod (97 mixins)
│   ├── Iris-1.21.9/                 # Shader support mod (168 mixins)
│   └── distant-horizons/            # LOD rendering mod (19 mixins)
├── src/main/java/
│   ├── net/minecraft/               # Minecraft source code
│   │   └── hooks/                   # Hook system (32 hook interfaces, partially implemented)
│   ├── net/sodium/                  # Sodium API stubs (empty?)
│   ├── net/iris/                    # Iris API stubs (empty?)
│   └── net/fabricmc/                # Fabric API stubs
└── frnsrc/                          # Reference sources (IGNORED - not used in build)
```

### Build System (Gradle)

**Current Process**:
1. **fabricLoader** source set → `fabric-loader-0.18.2.jar`
2. **main** source set → `minecraft-1.21.10.jar` (depends on fabricLoader)
3. **sodium** source set → `sodium-0.7.2-mc1.21.10.jar` (depends on fabricLoader + main)
4. **iris** source set → `iris-1.9.6-mc1.21.10.jar` (depends on fabricLoader + main + sodium)
5. **distantHorizons** source set → `distanthorizons-2.4.4-b-dev-mc1.21.10.jar` (depends on fabricLoader + main)
6. Runtime: Fabric Loader discovers mod JARs in `run/mods/` directory

**Current Dependencies** (separate compilation):
- Minecraft → Fabric Loader (compile-time)
- Sodium → Fabric Loader + Minecraft (compile-time)
- Iris → Fabric Loader + Minecraft + Sodium (compile-time)
- Distant Horizons → Fabric Loader + Minecraft (compile-time)

**Target Process**:
1. **Single source set** → `MattMC-1.21.10.jar` (ONE JAR with everything)

**Target Dependencies** (unified compilation):
- Everything compiles together in a single pass
- No inter-module dependencies - all code is in one source set
- Minecraft can reference mod classes directly (no circular dependency!)

### Mixin System

**Total**: 284 mixins across all mods
- **Sodium**: 97 mixins (66 mixin classes)
- **Iris**: 168 mixins (169 mixin classes)
- **Distant Horizons**: 19 mixins

**What Mixins Do**:
- Runtime bytecode modification to inject code into Minecraft classes
- Allow mods to modify game behavior without direct source changes
- Currently target classes in separate JARs
- Must be configured via JSON files (e.g., `sodium-common.mixins.json`)

### Hook System (Partially Implemented)

**Purpose**: Replace mixins with direct method calls
- 32 hook interfaces already created (GameHooks, RenderHooks, etc.)
- HookRegistry manages hook registrations
- 26 mixins already converted (~9% of total)
- Mods implement hook interfaces and register at runtime

**Limitation**: Only 9% converted - would take ~29 weeks to complete all conversions

---

## The Challenge

**What needs to be solved to move everything into one JAR?**

1. **Mixin Targeting Issue**:
   - Mixins currently target classes in separate JARs
   - If everything is in one JAR, mixin configs need to be consolidated
   - Mixin refmap (mapping) generation may need adjustments
   - Solution: Consolidate all mixin configs into the main JAR's resources

2. **Fabric Loader Discovery**:
   - Fabric Loader currently scans `run/mods/` directory for mod JARs
   - Each mod has its own `fabric.mod.json` in separate JARs
   - With everything in one JAR, need to tell Fabric Loader about embedded mods
   - Solution: Either use a single mod ID with all entrypoints, OR teach Fabric Loader to discover embedded mod metadata

3. **Source Organization**:
   - Need to move all source code from `modules/` to `src/main/java/`
   - Need to consolidate all resources from `modules/*/resources/` to `src/main/resources/`
   - Need to update build.gradle to compile everything together
   - Solution: Direct source migration with proper package structure

**Important**: There is NO circular dependency problem once everything is in the same source set. The Java compiler can handle all cross-references because it sees all classes at once.

---

## Proposed Solution: Two-Phase Integration

### Phase 1: Complete Source Consolidation

**Objective**: Move ALL source code (Fabric Loader + Minecraft + all mods) into a single unified source tree.

#### Step 1.1: Create Complete Package Structure

```
src/main/java/
├── net/minecraft/                   # Minecraft core
├── net/fabricmc/                    # Fabric Loader + Fabric API
│   ├── loader/                      # Fabric Loader classes (from modules/fabric-loader-0.18.2)
│   ├── api/                         # Fabric API stubs
│   └── ...
├── net/caffeinemc/mods/sodium/     # Sodium mod
├── net/irisshaders/iris/           # Iris mod
└── com/seibel/distanthorizons/     # Distant Horizons mod
```

**Key Insight**: Everything compiles together in ONE pass - no compilation order needed!

**Implementation**:
```bash
# Copy Fabric Loader source
cp -r modules/fabric-loader-0.18.2/src/main/java/* src/main/java/
cp -r modules/fabric-loader-0.18.2/src/main/legacyJava/* src/main/java/
cp -r modules/fabric-loader-0.18.2/src/java17/java/* src/main/java/
cp -r modules/fabric-loader-0.18.2/minecraft/src/main/java/* src/main/java/
cp -r modules/fabric-loader-0.18.2/minecraft/src/main/legacyJava/* src/main/java/

# Copy Sodium source
cp -r modules/sodium-1.21.9/common/src/main/java/* src/main/java/
cp -r modules/sodium-1.21.9/common/src/api/java/* src/main/java/
cp -r modules/sodium-1.21.9/common/src/boot/java/* src/main/java/
cp -r modules/sodium-1.21.9/fabric/src/main/java/* src/main/java/

# Copy Iris source  
cp -r modules/Iris-1.21.9/common/src/main/java/* src/main/java/
cp -r modules/Iris-1.21.9/common/src/api/java/* src/main/java/
cp -r modules/Iris-1.21.9/fabric/src/main/java/* src/main/java/

# Copy Distant Horizons source
cp -r modules/distant-horizons/coreSubProjects/core/src/main/java/* src/main/java/
cp -r modules/distant-horizons/coreSubProjects/api/src/main/java/* src/main/java/
cp -r modules/distant-horizons/common/src/main/java/* src/main/java/
cp -r modules/distant-horizons/fabric/src/main/java/* src/main/java/
```

#### Step 1.2: Consolidate Resources

```
src/main/resources/
├── META-INF/
│   └── services/                    # Service loader registrations (merged)
├── assets/                          # All mod assets combined
│   ├── sodium/
│   ├── iris/
│   └── distanthorizons/
├── fabric.mod.json                  # Minecraft's mod metadata
├── fabric-mods/                     # NEW: Embedded mod metadata directory
│   ├── sodium.fabric.mod.json       # Sodium mod metadata
│   ├── iris.fabric.mod.json         # Iris mod metadata
│   └── distanthorizons.fabric.mod.json  # DH mod metadata
└── mixins/                          # NEW: All mixin configs in one directory
    ├── sodium-common.mixins.json
    ├── sodium-fabric.mixins.json
    ├── iris.mixins.json
    ├── iris-fabric.mixins.json
    ├── iris-compat-sodium.mixins.json
    ├── iris-compat-dh.mixins.json
    └── distanthorizons.mixins.json
```

**Copy Resources**:
```bash
# Copy Fabric Loader resources
cp -r modules/fabric-loader-0.18.2/src/main/resources/* src/main/resources/
cp -r modules/fabric-loader-0.18.2/minecraft/src/main/resources/* src/main/resources/

# Copy Sodium resources
cp -r modules/sodium-1.21.9/common/src/main/resources/* src/main/resources/
cp -r modules/sodium-1.21.9/fabric/src/main/resources/* src/main/resources/

# Copy Iris resources
cp -r modules/Iris-1.21.9/common/src/main/resources/* src/main/resources/
cp -r modules/Iris-1.21.9/fabric/src/main/resources/* src/main/resources/

# Copy DH resources  
cp -r modules/distant-horizons/fabric/src/main/resources/* src/main/resources/
```

#### Step 1.3: Update Gradle Build Configuration

**Remove ALL Separate Source Sets**:
```gradle
// DELETE all these source sets:
// - sourceSets.fabricLoader
// - sourceSets.sodium
// - sourceSets.iris  
// - sourceSets.distantHorizons

// KEEP only:
sourceSets {
    main {
        java {
            srcDir 'src/main/java'  // ALL source code together
        }
        resources {
            srcDir 'src/main/resources'  // ALL resources together
        }
    }
    test { /* ... */ }
}
```

**Compilation**: Simple single-pass compilation - no special configuration needed!
```gradle
// Everything compiles together automatically
// No circular dependencies because everything is in one source set
tasks.named('compileJava') {
    // Standard compilation - nothing special needed
    options.encoding = 'UTF-8'
    options.release = 21
}
```

#### Step 1.4: Remove ALL Separate JAR Tasks

```gradle
// DELETE all these tasks:
// - tasks.register('fabricLoaderJar', Jar)
// - tasks.register('gameJar', Jar)
// - tasks.register('sodiumJar', Jar)
// - tasks.register('irisJar', Jar)
// - tasks.register('distantHorizonsJar', Jar)

// KEEP only the main jar task:
jar {
    // Single JAR with EVERYTHING
    from sourceSets.main.output
    
    manifest {
        attributes(
            'Main-Class': 'net.fabricmc.loader.impl.launch.knot.KnotClient'
        )
    }
}
```

---

### Phase 2: Mixin System Reconfiguration

**Objective**: Make mixins work when targeting classes in the same JAR.

#### Step 2.1: Understand Mixin Mechanics

**Current Flow**:
1. Fabric Loader loads mod JARs
2. Mixin library reads mixin config JSON from each mod JAR
3. Mixin applies transformations to Minecraft classes at runtime
4. Refmap provides obfuscation mappings (dev → prod names)

**Challenge with Single JAR**:
- Mixin configs reference classes in same JAR
- Refmap generation assumes separate JARs
- Target class resolution changes

#### Step 2.2: Consolidate Mixin Configurations

**Create Master Mixin Config** (`src/main/resources/mattmc-all.mixins.json`):
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "net.caffeinemc.mods.sodium.mixin",
  "compatibilityLevel": "JAVA_21",
  "plugin": "net.caffeinemc.mods.sodium.mixin.SodiumMixinPlugin",
  "injectors": {
    "defaultRequire": 1
  },
  "mixins": [],
  "client": [
    // All Sodium mixins
    "core.render.frustum.FrustumMixin",
    "core.render.world.GameRendererMixin",
    "core.render.world.LevelRendererMixin",
    // ... (all 97 Sodium mixins)
    
    // All Iris mixins (with package prefix)
    "net.irisshaders.iris.mixin.GameRendererMixin",
    "net.irisshaders.iris.mixin.LevelRendererMixin",
    // ... (all 168 Iris mixins)
    
    // All DH mixins (with package prefix)
    "com.seibel.distanthorizons.fabric.mixins.client.MixinClientLevel",
    // ... (all 19 DH mixins)
  ],
  "server": [
    // DH server mixins
    "com.seibel.distanthorizons.fabric.mixins.server.MixinChunkGenerator"
    // ... (all DH server mixins)
  ]
}
```

**Alternative**: Keep separate configs but update package declarations:
```json
// sodium.mixins.json
{
  "package": "net.caffeinemc.mods.sodium.mixin",
  "required": true,
  "client": [ /* Sodium mixins */ ]
}

// iris.mixins.json  
{
  "package": "net.irisshaders.iris.mixin",
  "required": true,
  "client": [ /* Iris mixins */ ]
}
```

#### Step 2.3: Update Fabric Mod Metadata

**Option A: Single Mod with All Features**

`src/main/resources/fabric.mod.json`:
```json
{
  "schemaVersion": 1,
  "id": "mattmc",
  "version": "1.21.10",
  "name": "MattMC",
  "description": "Minecraft with integrated Sodium, Iris, and Distant Horizons",
  "license": "Mixed",
  "environment": "*",
  "entrypoints": {
    "client": [
      "net.caffeinemc.mods.sodium.fabric.SodiumFabricMod",
      "net.irisshaders.iris.fabric.IrisFabricMod",
      "com.seibel.distanthorizons.fabric.FabricMain"
    ],
    "preLaunch": [
      "net.caffeinemc.mods.sodium.fabric.SodiumPreLaunch"
    ],
    "server": [
      "com.seibel.distanthorizons.fabric.FabricMain"
    ]
  },
  "mixins": [
    "sodium-common.mixins.json",
    "sodium-fabric.mixins.json",
    "iris.mixins.json",
    "iris-fabric.mixins.json",
    "iris-compat-sodium.mixins.json",
    "iris-compat-dh.mixins.json",
    "distanthorizons.mixins.json"
  ],
  "depends": {
    "minecraft": "1.21.10"
  }
}
```

**Option B: Virtual Mods (Multiple Mod IDs in Same JAR)**

Keep separate `fabric.mod.json` files in `META-INF/mods/`:
```
META-INF/
└── mods/
    ├── sodium.fabric.mod.json
    ├── iris.fabric.mod.json
    └── distanthorizons.fabric.mod.json
```

Modify Fabric Loader to scan `META-INF/mods/` in addition to root.

#### Step 2.4: Update Mixin Plugin Classes

Mixin plugins (e.g., `SodiumMixinPlugin`) may need updates:

```java
// net/caffeinemc/mods/sodium/mixin/SodiumMixinPlugin.java
public class SodiumMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        // Update to handle same-JAR targeting
        MixinEnvironment.getDefaultEnvironment()
            .addConfiguration("sodium-common.mixins.json");
    }
    
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Same logic, but aware we're in same JAR
        return true;
    }
}
```

---

### Phase 2: Fabric Loader Internal Mod Discovery

**Objective**: Make Fabric Loader discover and load mods that are embedded in the same JAR (no external mod directory).

#### Step 2.1: Understand Fabric Loader Mod Discovery

**Current Discovery Process**:
1. `DirectoryModCandidateFinder` - Scans `run/mods/` directory
2. `ClasspathModCandidateFinder` - Scans classpath for `fabric.mod.json`
3. `ArgumentModCandidateFinder` - Scans paths from command-line args

**Key Classes**:
- `net.fabricmc.loader.impl.discovery.ModCandidateFinder`
- `net.fabricmc.loader.impl.discovery.ModCandidateImpl`
- `net.fabricmc.loader.impl.discovery.ClasspathModCandidateFinder`

#### Step 2.2: Modify Fabric Loader for Internal Mods

**Option A: Enhanced Classpath Scanner**

Update `ClasspathModCandidateFinder.java`:
```java
public class ClasspathModCandidateFinder implements ModCandidateFinder {
    @Override
    public void findCandidates(/*...*/) {
        // Existing: Find fabric.mod.json in root
        URL rootModJson = ClassLoader.getSystemResource("fabric.mod.json");
        if (rootModJson != null) {
            addCandidate(rootModJson, /*...*/);
        }
        
        // NEW: Find fabric.mod.json in META-INF/mods/
        Enumeration<URL> embeddedMods = 
            ClassLoader.getSystemResources("META-INF/mods/*.fabric.mod.json");
        while (embeddedMods.hasMoreElements()) {
            URL modJson = embeddedMods.nextElement();
            addCandidate(modJson, /*...*/);
        }
        
        // NEW: Find fabric.mod.json in fabric-mods/
        Enumeration<URL> fabricMods = 
            ClassLoader.getSystemResources("fabric-mods/*.fabric.mod.json");
        while (fabricMods.hasMoreElements()) {
            URL modJson = fabricMods.nextElement();
            addCandidate(modJson, /*...*/);
        }
    }
}
```

**Option B: Single Mod with Multiple Entrypoints**

No Fabric Loader changes needed - just use single `fabric.mod.json` with multiple entrypoints (see Phase 2.3 Option A).

#### Step 2.3: Update Mod Loading

**Ensure Mixin Configs Load**:

In `net.fabricmc.loader.impl.launch.knot.Knot`:
```java
public class Knot {
    private void loadMods() {
        // Load all discovered mods
        for (ModCandidate candidate : modCandidates) {
            // Load mixin configs
            for (String mixinConfig : candidate.getMetadata().getMixinConfigs()) {
                // Existing: Load from mod JAR
                // NEW: Also check classpath for same-JAR mods
                URL mixinConfigUrl = ClassLoader.getSystemResource(mixinConfig);
                if (mixinConfigUrl != null) {
                    Mixins.addConfiguration(mixinConfig);
                }
            }
        }
    }
}
```

#### Step 2.4: Handle Mod Dependencies

**Problem**: Iris depends on Sodium, but both are in same JAR.

**Solution**:
- If using separate mod metadata files, update dependency resolution to recognize same-JAR mods
- If using single mod metadata, dependencies are implicit (all loaded together)

**Update Dependency Resolver** (if using separate metadata):
```java
// net/fabricmc/loader/impl/discovery/ModResolver.java
public class ModResolver {
    private void resolveDependencies() {
        // NEW: Check if dependency is in same JAR
        for (ModCandidate mod : candidates) {
            for (String depId : mod.getMetadata().getDependencies().keySet()) {
                ModCandidate dep = findModById(depId);
                if (dep == null) {
                    // Check if it's an embedded mod in same JAR
                    dep = findEmbeddedMod(depId, mod.getOriginUrl());
                }
                // ... resolve dependency
            }
        }
    }
    
    private ModCandidate findEmbeddedMod(String modId, URL jarUrl) {
        // Search META-INF/mods/ or fabric-mods/ in same JAR
        // ...
    }
}
```

---

## Detailed Implementation Steps

### Step-by-Step Execution Plan

#### Week 1-2: Preparation & Analysis

1. **Create Development Branch**
   ```bash
   git checkout -b feature/single-jar-integration
   ```

2. **Backup Current Working State**
   ```bash
   ./gradlew build
   cp -r build/ build-backup/
   git tag pre-integration-backup
   ```

3. **Analyze Mixin Usage**
   - Create inventory of all 284 mixins
   - Categorize by complexity (simple injection vs. complex overwrite)
   - Identify hook conversion candidates (low-hanging fruit)

4. **Test Current Build**
   ```bash
   ./gradlew clean build
   ./gradlew runClient  # Verify mods load correctly
   ```

#### Week 3-4: Source Reorganization (Phase 1)

5. **Create New Package Structure**
   ```bash
   mkdir -p src/main/java/net/caffeinemc/mods/sodium
   mkdir -p src/main/java/net/irisshaders/iris
   mkdir -p src/main/java/com/seibel/distanthorizons
   ```

6. **Copy Sodium Source**
   ```bash
   rsync -av modules/sodium-1.21.9/common/src/main/java/ src/main/java/
   rsync -av modules/sodium-1.21.9/fabric/src/main/java/ src/main/java/
   rsync -av modules/sodium-1.21.9/common/src/api/java/ src/main/java/
   rsync -av modules/sodium-1.21.9/common/src/boot/java/ src/main/java/
   ```

7. **Copy Iris Source**
   ```bash
   rsync -av modules/Iris-1.21.9/common/src/main/java/ src/main/java/
   rsync -av modules/Iris-1.21.9/fabric/src/main/java/ src/main/java/
   rsync -av modules/Iris-1.21.9/common/src/api/java/ src/main/java/
   ```

8. **Copy Distant Horizons Source** (if enabled)
   ```bash
   rsync -av modules/distant-horizons/coreSubProjects/core/src/main/java/ src/main/java/
   rsync -av modules/distant-horizons/coreSubProjects/api/src/main/java/ src/main/java/
   rsync -av modules/distant-horizons/common/src/main/java/ src/main/java/
   rsync -av modules/distant-horizons/fabric/src/main/java/ src/main/java/
   ```

9. **Consolidate Resources**
   ```bash
   # Create new resource directories
   mkdir -p src/main/resources/fabric-mods
   mkdir -p src/main/resources/mixins
   
   # Copy Sodium resources
   cp modules/sodium-1.21.9/common/src/main/resources/*.json src/main/resources/mixins/
   cp modules/sodium-1.21.9/fabric/src/main/resources/*.json src/main/resources/mixins/
   cp modules/sodium-1.21.9/fabric/src/main/resources/fabric.mod.json \
      src/main/resources/fabric-mods/sodium.fabric.mod.json
   
   # Copy Iris resources
   cp modules/Iris-1.21.9/common/src/main/resources/*.json src/main/resources/mixins/
   cp modules/Iris-1.21.9/common/src/main/resources/fabric.mod.json \
      src/main/resources/fabric-mods/iris.fabric.mod.json
   
   # Copy DH resources
   cp modules/distant-horizons/fabric/src/main/resources/*.json src/main/resources/mixins/
   ```

10. **Update build.gradle**
    - Remove `sourceSets.sodium`, `sourceSets.iris`, `sourceSets.distantHorizons`
    - Remove `sodiumJar`, `irisJar`, `distantHorizonsJar` tasks
    - Update `jar` task to include all sources
    - Update dependencies (all mods now in main source set)

11. **Test Compilation**
    ```bash
    ./gradlew clean compileJava
    # Fix any compilation errors (missing imports, package conflicts)
    ```

#### Week 5-6: Mixin System Reconfiguration (part of Phase 1)

12. **Create Unified Mixin Configuration**
    - Option 1: Create `mattmc-all.mixins.json` combining all
    - Option 2: Keep separate configs, ensure proper package declarations

13. **Update Mixin Plugin Classes**
    - Verify `SodiumMixinPlugin` works with same-JAR targeting
    - Update any hardcoded JAR path assumptions

14. **Configure Fabric Mod Metadata**
    - Decide: Single mod ID or virtual mods?
    - Update `fabric.mod.json` accordingly
    - Consolidate entrypoints

15. **Test Mixin Loading**
    ```bash
    ./gradlew build
    # Add debug logging to verify mixin configs load:
    # -Dmixin.debug.verbose=true -Dmixin.debug.export=true
    ```

#### Week 7-8: Fabric Loader Modification (Phase 2)

16. **Modify Fabric Loader Mod Discovery**
    - Update `ClasspathModCandidateFinder` to scan `META-INF/mods/` or `fabric-mods/`
    - Ensure same-JAR mods are discovered

17. **Update Mod Loading Pipeline**
    - Verify mixin configs load from classpath
    - Update dependency resolution for same-JAR mods

18. **Rebuild Fabric Loader**
    ```bash
    ./gradlew fabricLoaderJar
    ```

19. **Integration Testing**
    ```bash
    ./gradlew build
    ./gradlew runClient
    # Test all mod features:
    # - Sodium rendering optimizations
    # - Iris shader support
    # - DH LOD rendering
    ```

#### Week 9-10: Testing & Refinement

20. **Comprehensive Testing**
    - Video settings (Sodium features)
    - Shader pack loading (Iris features)
    - LOD rendering (DH features)
    - Performance benchmarks
    - Multiplayer compatibility

21. **Fix Any Issues**
    - Mixin application failures
    - Missing resources
    - Classpath issues
    - Entrypoint loading errors

22. **Optimize Build System**
    - Remove unnecessary tasks
    - Simplify distribution tasks
    - Update documentation

23. **Update Documentation**
    - Update README.md
    - Update build instructions
    - Document new architecture

24. **Final Validation**
    ```bash
    ./gradlew clean
    ./gradlew build
    ./gradlew test
    ./gradlew runClient
    ./gradlew runServer
    ```

---

## Build System Changes

### Simplified build.gradle

**Before** (Current):
```gradle
// 5 separate source sets
sourceSets {
    fabricLoader { /* ... */ }
    sodium { /* ... */ }
    iris { /* ... */ }
    distantHorizons { /* ... */ }
    main { /* ... */ }
}

// 5 separate JAR tasks
tasks.register('fabricLoaderJar', Jar) { /* ... */ }
tasks.register('sodiumJar', Jar) { /* ... */ }
tasks.register('irisJar', Jar) { /* ... */ }
tasks.register('distantHorizonsJar', Jar) { /* ... */ }
tasks.register('gameJar', Jar) { /* ... */ }

// Complex runClient task with mod copying
tasks.register('runClient', JavaExec) {
    dependsOn 'fabricLoaderJar', 'gameJar', 'sodiumJar', 'irisJar', 'distantHorizonsJar'
    // Copy mod JARs to run/mods/
    doFirst {
        copy {
            from file("${buildDir}/mods")
            into file('run/mods')
        }
    }
}
```

**After** (Simplified to ONE JAR):
```gradle
// SINGLE source set with EVERYTHING
sourceSets {
    main {
        java {
            srcDir 'src/main/java'  // Fabric Loader + Minecraft + ALL mods
        }
        resources {
            srcDir 'src/main/resources'  // All resources
        }
    }
}

// SINGLE JAR task
jar {
    // One JAR with EVERYTHING - Fabric Loader + Minecraft + all mods
    from sourceSets.main.output
    
    manifest {
        attributes(
            'Main-Class': 'net.fabricmc.loader.impl.launch.knot.KnotClient'
        )
    }
}

// Simplified runClient task - ONE JAR!
tasks.register('runClient', JavaExec) {
    dependsOn 'jar'
    
    classpath = files(
        file("${buildDir}/libs/MattMC-${version}.jar")
    ) + configurations.runtimeClasspath
    
    mainClass = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
    
    // No mod copying - everything is already in the ONE JAR!
}
```

### Dependency Changes

**Remove ALL separate source set configurations**:
- fabricLoader configurations (`fabricLoaderImplementation`, etc.)
- Sodium configurations (`sodiumImplementation`, `sodiumCompileOnly`, etc.)
- Iris configurations (`irisImplementation`, `irisCompileOnly`, etc.)
- DH configurations (`distantHorizonsImplementation`, etc.)

**Keep**:
- Single `implementation` block with ALL dependencies (Fabric, Minecraft, Sodium, Iris, DH)

---

## Risk Analysis & Mitigation

### High Risk

#### Risk: Mixin Refmap Generation Breaks
**Impact**: Mixins fail to apply, mods don't work
**Mitigation**:
- Test refmap generation early in Phase 2
- Use Mixin's `--refmap-in` and `--refmap-out` options
- Consider disabling refmap if dev environment only
- Gradual migration: keep old JARs working while testing

#### Risk: Compilation Issues
**Impact**: Compilation fails due to missing dependencies or package conflicts
**Mitigation**:
- Ensure all dependencies are in the main configuration
- Check for package name conflicts between mods
- Test compilation early and often
- Note: NO circular dependency risk - everything compiles in one pass!

#### Risk: Fabric Loader Doesn't Discover Same-JAR Mods
**Impact**: Mods don't initialize, features missing
**Mitigation**:
- Extensive testing of mod discovery changes
- Fallback to single mod ID approach if virtual mods fail
- Debug logging to trace discovery process

### Medium Risk

#### Risk: Mixin Conflicts Between Mods
**Impact**: Some mixins fail to apply
**Mitigation**:
- Audit all mixin targets for conflicts
- Use mixin priorities to control application order
- Leverage existing Iris-Sodium compatibility layer

#### Risk: Resource Conflicts (Assets, Configs)
**Impact**: Wrong textures/configs loaded
**Mitigation**:
- Namespace all assets properly (already done)
- Use `DuplicatesStrategy.WARN` to detect conflicts
- Manual merge of conflicting `META-INF/services` files

### Low Risk

#### Risk: Build Time Increases Significantly
**Impact**: Slower development iteration
**Mitigation**:
- Enable Gradle build cache
- Use incremental compilation
- Parallel compilation where possible

#### Risk: IDE Performance Degrades
**Impact**: Slower code navigation, autocomplete
**Mitigation**:
- Increase IDE heap size
- Use separate source roots for organization
- Exclude generated sources from indexing

---

## Testing Strategy

### Unit Tests
- Verify all existing tests pass
- Add tests for:
  - Mixin application
  - Mod discovery from same JAR
  - Resource loading

### Integration Tests
- Sodium features:
  - Advanced video settings load
  - Chunk rendering optimizations apply
  - Memory usage improvements
- Iris features:
  - Shader packs load from shaderpacks/
  - Shader compilation works
  - Render pipeline integration
- DH features:
  - LOD chunks generate
  - Distant terrain renders
  - Performance acceptable

### Performance Tests
- Compare before/after benchmarks:
  - FPS in different scenarios
  - Memory usage
  - Startup time
  - Chunk loading speed

### Compatibility Tests
- Single-player world loading
- Multiplayer server connection
- Resource pack loading
- Different graphics settings

---

## Rollback Plan

If integration fails catastrophically:

1. **Revert to Tagged Backup**
   ```bash
   git reset --hard pre-integration-backup
   ./gradlew clean build
   ```

2. **Restore Build Artifacts**
   ```bash
   rm -rf build/
   cp -r build-backup/ build/
   ```

3. **Document Lessons Learned**
   - What went wrong?
   - What worked?
   - How to improve next attempt?

---

## Success Criteria

### Build System
- ✅ Single `./gradlew build` command produces ONE working JAR
- ✅ No separate mod JAR copying needed
- ✅ No separate Fabric Loader JAR needed
- ✅ Build time ≤ 120% of current time
- ✅ Build configuration < 500 lines (vs. current 1500+) - massive simplification!

### Runtime
- ✅ All mods initialize successfully
- ✅ All mixin transformations apply
- ✅ No missing textures or resources
- ✅ All mod features work as before

### Performance
- ✅ FPS within 5% of current performance
- ✅ Memory usage unchanged or better
- ✅ Startup time ≤ current time

### Maintenance
- ✅ Easier to add new features (no multi-module changes)
- ✅ Simpler debugging (single JAR)
- ✅ Faster iteration (no mod copying)

---

## Alternative Approaches Considered

### Alternative 1: Complete Hook Conversion (26 weeks)
**Approach**: Finish converting all 284 mixins to hooks
**Pros**: Clean architecture, no mixins at all
**Cons**: Extremely time-consuming (~29 weeks full-time), some mixins may be impossible to convert
**Verdict**: Not practical for immediate goals

### Alternative 2: Keep Separate JARs, Simplify Loading
**Approach**: Keep mod JARs separate but improve build/load process
**Pros**: Less risky, smaller changes
**Cons**: Doesn't achieve goal of single JAR, still complex build
**Verdict**: Doesn't meet requirements

### Alternative 3: Uber JAR with Shade Plugin
**Approach**: Use Gradle Shadow plugin to merge all JARs
**Pros**: Simpler implementation
**Cons**: Mixin refmap issues, classpath conflicts, loses mod identity
**Verdict**: Insufficient for Fabric mod architecture

### Alternative 4: Custom Mod Container Format
**Approach**: Create custom container format for embedded mods
**Pros**: Full control over mod discovery
**Cons**: Major Fabric Loader rewrite, compatibility issues
**Verdict**: Over-engineered for this use case

---

## Future Enhancements

After successful integration:

### Short-Term (1-3 months)
- Convert more mixins to hooks (target: 50% conversion)
- Optimize build caching
- Add automated testing for mod features

### Medium-Term (3-6 months)
- Complete hook conversion (if feasible)
- Remove mixin system entirely (if 100% hooks)
- Simplify Fabric Loader (if mixins gone)

### Long-Term (6-12 months)
- Custom renderer pipeline (replace Sodium/Iris with native implementation)
- Native LOD system (replace DH with integrated solution)
- Full codebase unification (no mod boundaries)

---

## Conclusion

**Is this plan feasible?** YES, absolutely!

**Key Success Factors**:
1. No circular dependency issues - everything compiles together in one pass
2. Simpler than originally thought - just consolidate sources and update Fabric Loader discovery
3. Extensive testing at each phase
4. Rollback plan if issues arise

**Estimated Timeline**:
- **Phase 1 (Complete Source Consolidation + Mixin Updates)**: 3-5 weeks
- **Phase 2 (Fabric Loader Internal Discovery)**: 2-3 weeks
- **Testing & Refinement**: 2-3 weeks
- **Total**: 7-11 weeks (2-3 months)

**Compared to Hook Conversion**:
- Hook conversion: ~29 weeks
- This plan: ~9 weeks average
- **3.2x faster** to achieve ONE JAR goal

**Recommendation**: Proceed with complete migration - move ALL sources (including Fabric Loader) to src/main/java in one step. This is simpler than incremental migration because there are no circular dependencies to worry about.

---

## Appendix A: Mixin Inventory

### Sodium (97 mixins)

**Core Rendering** (25 mixins):
- FrustumMixin, GameRendererMixin, LevelRendererMixin
- BufferBuilderMixin, EntityOutlineGeneratorMixin
- ChunkSectionsToRenderMixin, EntityRendererAccessor
- (18 more...)

**World & Chunk** (15 mixins):
- PalettedContainerMixin, SimpleBitStorageMixin, ZeroBitStorageMixin
- (12 more...)

**Features** (42 mixins):
- Entity rendering, GUI, particle effects, texture handling
- (39 more...)

**Workarounds** (15 mixins):
- Platform-specific fixes, compatibility patches

### Iris (168 mixins)

**Shader Pipeline** (60 mixins):
- Core shader system, shader parsing, uniform handling

**Rendering** (50 mixins):
- World rendering, entity rendering, sky rendering

**Compatibility** (30 mixins):
- Sodium compat (19), DH compat (4), other (7)

**Vertex Format** (8 mixins):
- Custom vertex attributes for shaders

**Other** (20 mixins):
- Texture handling, state tracking, fixes

### Distant Horizons (19 mixins)

**Server** (9 mixins):
- Chunk generation, entity tracking, threading

**Client** (10 mixins):
- Level rendering, fog, debug overlay, lighting

---

## Appendix B: File Structure After Integration

```
MattMC/
├── build.gradle                     # MASSIVELY SIMPLIFIED (1 source set, 1 JAR)
├── modules/                         # CAN BE DELETED (or kept as reference)
│   ├── fabric-loader-0.18.2/       # SOURCE MOVED to src/main/java
│   ├── sodium-1.21.9/              # SOURCE MOVED to src/main/java
│   ├── Iris-1.21.9/                # SOURCE MOVED to src/main/java
│   └── distant-horizons/           # SOURCE MOVED to src/main/java
├── src/main/java/
│   ├── net/minecraft/              # Minecraft source
│   ├── net/fabricmc/               # Fabric Loader + Fabric API (MOVED FROM modules/fabric-loader-0.18.2)
│   │   ├── loader/                 # Fabric Loader classes
│   │   ├── api/                    # Fabric API
│   │   └── ...
│   ├── net/caffeinemc/mods/sodium/ # MOVED FROM modules/sodium-1.21.9
│   ├── net/irisshaders/iris/       # MOVED FROM modules/Iris-1.21.9
│   └── com/seibel/distanthorizons/ # MOVED FROM modules/distant-horizons
├── src/main/resources/
│   ├── fabric.mod.json             # UPDATED: Combined or main metadata
│   ├── fabric-mods/                # NEW: Embedded mod metadata (optional)
│   │   ├── sodium.fabric.mod.json
│   │   ├── iris.fabric.mod.json
│   │   └── distanthorizons.fabric.mod.json
│   ├── mixins/                     # CONSOLIDATED: All mixin configs
│   │   ├── sodium-common.mixins.json
│   │   ├── sodium-fabric.mixins.json
│   │   ├── iris.mixins.json
│   │   ├── iris-fabric.mixins.json
│   │   ├── iris-compat-sodium.mixins.json
│   │   ├── iris-compat-dh.mixins.json
│   │   └── distanthorizons.mixins.json
│   └── assets/                     # MERGED: All mod assets
│       ├── sodium/
│       ├── iris/
│       └── distanthorizons/
└── build/libs/
    └── MattMC-1.21.10.jar          # ONE SINGLE JAR: Fabric Loader + Minecraft + all mods!
```

**Key Changes**: 
- `run/mods/` directory **NO LONGER NEEDED** - everything is in ONE JAR!
- **NO separate Fabric Loader JAR** - it's included in the main JAR!

---

## Appendix C: Commands Reference

### Build Commands
```bash
# Clean build
./gradlew clean build

# Build without tests
./gradlew build -x test

# Build with parallel execution
./gradlew build --parallel

# Build with verbose output
./gradlew build --info

# Build with debug
./gradlew build --debug
```

### Run Commands
```bash
# Run client (simplified - no mod copying)
./gradlew runClient

# Run with debug logging
./gradlew runClient -Dmixin.debug.verbose=true

# Run with profiling
./gradlew runClient -Xprof

# Run server
./gradlew runServer
```

### Testing Commands
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "ClassName.testMethod"

# Run tests with debugging
./gradlew test --debug-jvm
```

### Debug Commands
```bash
# Export mixin output for inspection
./gradlew runClient -Dmixin.debug.export=true

# Verify classpath
./gradlew dependencies --configuration runtimeClasspath

# Check for duplicate classes
./gradlew buildEnvironment
```

---

**Document Version**: 1.0  
**Created**: 2025-12-29  
**Author**: Copilot AI Assistant  
**Status**: READY FOR REVIEW & IMPLEMENTATION
