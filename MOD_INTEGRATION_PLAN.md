# Mod Integration Plan: From Separate JARs to Unified Build

## Current State Analysis

Your MattMC project currently has:

1. **Main Minecraft code** - Compiled into the game JAR
2. **Fabric Loader** - Separate JAR (`fabric-loader-0.18.2.jar`)
3. **Mods as separate JARs**:
   - Sodium (`sodium-0.7.2-mc1.21.10.jar`)
   - Iris (`iris-1.9.6-mc1.21.10.jar`)
   - Distant Horizons (optional, `distanthorizons-2.4.4-b-dev-mc1.21.10.jar`)

### Current Build Configuration

- **Source sets**: Separate source sets for each mod (lines 364-491 in build.gradle)
- **JAR tasks**: Each mod has its own JAR task (sodiumJar, irisJar, distantHorizonsJar)
- **Runtime**: Mods are loaded from `run/mods/` directory by Fabric Loader
- **Partial integration**: You already have placeholder directories in `src/main/java/net/iris` and `src/main/java/net/sodium`

### Key Observation

You've already started moving code! There are ~110 Java files in `src/main/java/net/{iris,sodium,fabricmc}` which appear to be API/utility classes from the mods.

## Integration Goals

Transform the architecture from:
```
Fabric Loader JAR → Loads → [Sodium JAR, Iris JAR, DH JAR] → Loads → Minecraft JAR
```

To:
```
Single Unified JAR (Minecraft + Sodium + Iris + DH)
```

## First Steps for Integration

### Phase 1: Understand the Dependencies

**Step 1.1: Map mod source directories**

Current structure:
```
modules/sodium-1.21.9/
├── common/src/main/java/     (~800+ files)
├── common/src/api/java/       (API classes)
├── common/src/boot/java/      (Bootstrap)
└── fabric/src/main/java/      (Fabric integration)

modules/Iris-1.21.9/
├── common/src/main/java/      (~700+ files)
├── common/src/api/java/       (API classes)
└── fabric/src/main/java/      (Fabric integration)

modules/distant-horizons/
├── coreSubProjects/core/      (Core DH code)
├── coreSubProjects/api/       (DH API)
├── common/                    (Common code)
└── fabric/                    (Fabric integration)
```

**Action**: Create a mapping document showing:
- Which packages from modules map to where in `src/main/java/`
- Which classes are already copied vs. still in modules
- Dependencies between mods (e.g., Iris depends on Sodium)

**Step 1.2: Analyze Fabric Loader dependencies**

The mods currently rely on Fabric Loader's:
- **Mixin system** - Runtime bytecode modification
- **Mod loading** - EntryPoint system, discovery, initialization
- **Metadata** - fabric.mod.json parsing
- **Environment detection** - Client vs Server

**Action**: Decide whether to:
- Option A: Keep Fabric Loader and make it load internal classes instead of JARs
- Option B: Remove Fabric Loader entirely and convert mixins to direct code changes
- Option C: Hybrid - Keep minimal Fabric infrastructure for future extensibility

### Phase 2: Incremental Migration Strategy

**Step 2.1: Start with one mod (Sodium recommended)**

Why Sodium first?
- Smaller dependency footprint (doesn't depend on other mods)
- Iris depends on Sodium, so get the dependency right first
- Client-side only, simpler than mixed client/server mods

**Action**: 
1. Copy Sodium source files from `modules/sodium-1.21.9/` to `src/main/java/`
2. Map package structure:
   ```
   modules/sodium-1.21.9/common/src/main/java/net/caffeinemc/mods/sodium/
   →
   src/main/java/net/caffeinemc/mods/sodium/
   ```

**Step 2.2: Update build.gradle source sets**

Currently, Sodium is in a separate source set. To integrate:

```gradle
// BEFORE (lines 385-400):
sourceSets {
    sodium {
        java {
            srcDirs = [
                "${sodiumDir}/common/src/main/java",
                ...
            ]
        }
    }
}

// AFTER:
sourceSets {
    main {
        java {
            // Sodium is now part of main source set
            srcDir 'src/main/java'  // Already includes net/caffeinemc/mods/sodium/
        }
    }
}
```

**Step 2.3: Handle resources (mixins, fabric.mod.json, assets)**

Mods have resources that need special handling:

1. **Mixin configurations** (`.mixins.json` files):
   - Keep in `src/main/resources/` 
   - Update Fabric Loader to find them in classpath instead of separate JARs

2. **fabric.mod.json**:
   - Option A: Keep them and merge into a unified mod metadata
   - Option B: Remove them entirely if eliminating Fabric Loader

3. **Assets** (icons, shader files):
   - Move to `src/main/resources/assets/sodium/` etc.

### Phase 3: Update Build Configuration

**Step 3.1: Modify JAR task**

Currently:
```gradle
tasks.register('sodiumJar', Jar) {
    from sourceSets.sodium.output
    destinationDirectory = file("${buildDir}/mods")
}
```

After integration:
```gradle
jar {
    // Sodium is now included in main JAR
    from sourceSets.main.output
    // No separate sodium JAR needed
}
```

**Step 3.2: Remove mod loading mechanism**

If going with unified JAR approach:

1. **Remove JAR copying** in runClient task (lines 980-984):
   ```gradle
   // DELETE THIS SECTION:
   copy {
       from file("${buildDir}/mods")
       into modsDir
       include '*.jar'
   }
   ```

2. **Initialize mods directly** instead of via Fabric discovery:
   - Call mod entrypoints directly from Minecraft initialization
   - Replace `@Environment` annotations with runtime checks if needed

**Step 3.3: Update dependencies**

Some dependencies are mod-specific:
- Iris needs: JCPP, ANTLR, GLSL Transformer (lines 306-312)
- Distant Horizons needs: zstd-jni, sqlite-jdbc, night-config (lines 316-331)

These should remain in main dependencies block - they're already there!

### Phase 4: Handle Fabric Loader Integration

**Decision Point**: What to do with Fabric Loader?

**Option A: Keep Fabric Loader, modify loading mechanism**
- Pros: Maintains mixin infrastructure, easier to add future mods
- Cons: Still has loader overhead, complexity
- Implementation:
  1. Keep Fabric Loader source set and JAR
  2. Modify Knot launcher to recognize internal mods (no JAR scanning)
  3. Register mod entrypoints programmatically

**Option B: Remove Fabric Loader, convert to direct integration**
- Pros: True unified build, no loader overhead, simpler runtime
- Cons: Need to convert all mixins to direct code changes
- Implementation:
  1. Convert each mixin to direct code modification
  2. Remove Fabric entrypoint system
  3. Call mod initialization from Minecraft's startup

**Option C: Hybrid - Internal mod loading**
- Pros: Keep mixin infrastructure, remove JAR complexity
- Cons: Middle ground, may not satisfy either goal fully
- Implementation:
  1. Keep Fabric's mixin system and ASM dependencies
  2. Remove mod discovery/JAR loading
  3. Hardcode mod initialization in Minecraft startup

**Recommendation**: Start with Option A, migrate to Option B incrementally

### Phase 5: Handle Mixins

Mixins are the biggest technical challenge. Each mod uses them extensively:

**Sodium mixins** (~50+ mixin classes):
- Example: `sodium-common.mixins.json`, `sodium-fabric.mixins.json`
- Target: Minecraft rendering, chunk building, etc.

**Iris mixins** (~60+ mixin classes):
- Example: `mixins.iris.json`, `mixins.iris.compat.sodium.json`
- Target: Shader integration, rendering pipeline

**Approaches**:

1. **Keep Mixin system** (Easier):
   - Mixin library stays in dependencies (already there - line 243)
   - Mixin configs in `src/main/resources/`
   - Fabric Loader's mixin initialization still works

2. **Convert mixins to direct code** (Harder but cleaner):
   - For each mixin class, manually apply the changes to Minecraft source
   - Remove Mixin dependency
   - Pros: No runtime bytecode modification, easier to debug
   - Cons: Labor intensive, harder to maintain mod updates

## Concrete First Steps (Recommended Order)

### Step 1: Copy Sodium Source (Non-breaking)
```bash
# Create target directory structure
mkdir -p src/main/java/net/caffeinemc/mods/sodium

# Copy Sodium common source
cp -r modules/sodium-1.21.9/common/src/main/java/* src/main/java/
cp -r modules/sodium-1.21.9/common/src/api/java/* src/main/java/
cp -r modules/sodium-1.21.9/common/src/boot/java/* src/main/java/

# Copy Sodium fabric integration
cp -r modules/sodium-1.21.9/fabric/src/main/java/* src/main/java/

# Copy Sodium resources
cp -r modules/sodium-1.21.9/common/src/main/resources/* src/main/resources/
cp -r modules/sodium-1.21.9/fabric/src/main/resources/* src/main/resources/
```

### Step 2: Update build.gradle (Testing integration)

Add to main source set (line ~430):
```gradle
main {
    java {
        srcDir '.'
        srcDir 'src/main/java'  // Explicitly add - already includes Sodium now
        
        // ... existing excludes ...
    }
}
```

Comment out Sodium source set temporarily to test compilation.

### Step 3: Test Compilation
```bash
./gradlew compileJava
```

This will reveal:
- Missing dependencies
- Package conflicts
- Circular dependencies

### Step 4: Handle Compilation Errors

Common issues you'll encounter:

1. **Duplicate classes**: You have placeholders in `src/main/java/net/sodium/` but full code in modules
   - Resolution: Decide if placeholders stay or get replaced

2. **Missing Fabric API**: Sodium uses Fabric API interfaces
   - You have stubs in `src/main/java/net/fabricmc/fabric/api/`
   - Ensure these match what Sodium expects

3. **Mixin references**: Code references mixin classes that don't exist yet
   - Resolution: Ensure mixin configs are in classpath

### Step 5: Update Runtime Configuration

Modify `runClient` task to not load Sodium from mods directory:

```gradle
runClient {
    doFirst {
        // Create mods directory but don't copy Sodium
        def modsDir = file('run/mods')
        modsDir.mkdirs()
        
        // Only copy Iris and DH (if Sodium integration successful)
        copy {
            from file("${buildDir}/mods")
            into modsDir
            include 'iris-*.jar'
            if (enableDistantHorizons) {
                include 'distanthorizons-*.jar'
            }
            // Exclude Sodium - it's now internal
            exclude 'sodium-*.jar'
        }
    }
}
```

## Long-term Integration Roadmap

### Phase 1 (Weeks 1-2): Sodium Integration
- [x] Analyze current structure
- [ ] Copy Sodium source to main source set
- [ ] Update build configuration
- [ ] Test compilation
- [ ] Test runtime with Sodium internal, Iris/DH external
- [ ] Fix any initialization issues

### Phase 2 (Weeks 3-4): Iris Integration  
- [ ] Copy Iris source to main source set
- [ ] Handle Iris-Sodium dependencies
- [ ] Update shader loading paths
- [ ] Test runtime with Sodium+Iris internal, DH external

### Phase 3 (Weeks 5-6): Distant Horizons Integration
- [ ] Copy DH source to main source set
- [ ] Handle DH-Iris compatibility layer
- [ ] Test full integration

### Phase 4 (Weeks 7-8): Fabric Loader Decision
- [ ] Evaluate if Fabric Loader still needed
- [ ] Option A: Keep loader, simplify mod discovery
- [ ] Option B: Remove loader, convert mixins
- [ ] Implement chosen approach

### Phase 5 (Ongoing): Maintenance
- [ ] Document new build process
- [ ] Update README
- [ ] Create upgrade guides for mod updates
- [ ] Consider contribution to upstream mods

## Key Considerations

### 1. Maintaining Upstream Changes

Once integrated, updating mods is harder:
- Can't just `git pull` in modules anymore
- Need to manually merge changes

**Mitigation**: Keep modules directory as reference, use git subtree or patches

### 2. Debugging Complexity

With unified JAR:
- Can't easily disable individual mods
- Harder to isolate which mod causes issues

**Mitigation**: Use feature flags in code to enable/disable mod functionality

### 3. Build Performance

Pros:
- Single compilation step
- No separate JAR tasks

Cons:  
- Larger codebase (~2000 more files)
- Longer initial compilation

**Mitigation**: Gradle incremental compilation helps; use build cache

### 4. Licensing

Each mod has different licenses:
- Sodium: Polyform-Shield-1.0.0
- Iris: LGPL-3.0-only  
- Distant Horizons: (Check their license)

**Action**: Ensure compliance when distributing unified builds

## Alternative Approaches

### Alternative 1: Classpath-based Loading (Minimal Change)

Keep separate JARs but don't use mod loading:
```gradle
runClient {
    classpath = files(
        "${buildDir}/libs/minecraft-${version}.jar",
        "${buildDir}/libs/sodium-${sodiumVersion}.jar",
        "${buildDir}/libs/iris-${irisVersion}.jar"
    ) + configurations.runtimeClasspath
}
```

Pros: Keep mod separation, simpler build
Cons: Not truly "integrated"

### Alternative 2: Gradle Composite Build

Use Gradle's composite build feature:
```gradle
// settings.gradle
includeBuild('modules/sodium-1.21.9')
includeBuild('modules/Iris-1.21.9')
```

Pros: Maintains mod boundaries, easier updates
Cons: Still separate compilation, not truly unified

## Recommended First Action

**Start here** (safest, non-breaking):

1. **Create a new Gradle task to test integration**:
   ```gradle
   tasks.register('integratedJar', Jar) {
       group = 'experimental'
       description = 'Test unified JAR with Sodium integrated'
       
       // Include main + sodium in one JAR
       from sourceSets.main.output
       from sourceSets.sodium.output
       
       manifest {
           attributes(
               'Main-Class': 'net.fabricmc.loader.impl.launch.knot.KnotClient'
           )
       }
   }
   ```

2. **Test this integrated JAR** without modifying existing build
3. **Iterate** on what works

This lets you experiment without breaking the current working build!

## Questions to Answer Before Proceeding

1. **Do you want to keep Fabric Loader?**
   - If yes → Easier, keep mixin system
   - If no → More work, but cleaner

2. **Are you planning to add more mods later?**
   - If yes → Keep some loading infrastructure
   - If no → Full integration makes sense

3. **How important is keeping up with mod updates?**
   - Very important → Keep modules separate, improve integration
   - Not important → Full integration okay

4. **Performance goal - what's the target?**
   - Faster startup → Remove loader overhead
   - Faster runtime → Unified JAR helps with JIT
   - Smaller distribution → One JAR is smaller than many

## Summary

**Immediate first steps**:
1. ✅ Understand current architecture (DONE - this document)
2. Copy Sodium source to `src/main/java/net/caffeinemc/`
3. Update main source set to include it
4. Test compilation
5. Fix any package conflicts with existing `net/sodium/api/` stubs
6. Test runtime with Sodium internal

**This gives you**:
- Hands-on experience with integration challenges
- A working baseline to iterate from
- Clear next steps for Iris and DH integration

Once Sodium integration works, the pattern repeats for other mods!
