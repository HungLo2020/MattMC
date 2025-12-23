# Architectural Analysis: Mod Integration Approaches

## Executive Summary

Your MattMC project is in a unique position:
- ✅ You compile everything from source (no prebuilt JARs)
- ✅ You already have mod placeholders in `src/main/java/`
- ✅ All dependencies are already in build.gradle
- ⚠️ But mods are still built as separate JARs and loaded at runtime

This document analyzes different approaches to achieve tighter integration.

## The Fabric Loader Question

This is the **key architectural decision** that determines everything else.

### Current Architecture
```
JVM starts
  ↓
Fabric Loader (KnotClient)
  ↓ discovers mods in run/mods/
Loads: sodium-*.jar, iris-*.jar, distanthorizons-*.jar
  ↓ reads fabric.mod.json from each
Initializes mixins from each mod
  ↓
Starts Minecraft (from game JAR)
  ↓
Calls mod entrypoints (@Entrypoint annotations)
  ↓
Game runs with mod code injected via mixins
```

### Integration Decision Tree

```
Do you want to keep Fabric Loader?
│
├─ YES → Option A: Keep Loader, Simplify Discovery
│   │
│   ├─ Keep mixin infrastructure (easiest)
│   ├─ Remove JAR scanning (faster)
│   └─ Hardcode mod registration (simpler)
│
└─ NO → Option B: Remove Loader, Direct Integration
    │
    ├─ Convert all mixins to direct code (most work)
    ├─ Call mod init directly from Minecraft startup
    └─ No runtime overhead (fastest)
```

## Option A: Keep Fabric Loader (Recommended First)

### Architecture
```
JVM starts
  ↓
Fabric Loader (KnotClient) - still present
  ↓ NO JAR scanning
Hardcoded mod list: [Sodium, Iris, DH] - from classpath
  ↓ reads mixin configs from resources
Initializes mixins (still uses Mixin library)
  ↓
Starts Minecraft (same JAR now)
  ↓
Calls mod entrypoints (hardcoded)
  ↓
Game runs with mod code via mixins
```

### Changes Required

#### 1. Source Code Organization
```
src/main/java/
├── net/minecraft/              (Minecraft core)
├── net/fabricmc/loader/        (Fabric Loader - from modules/fabric-loader-0.18.2)
├── net/caffeinemc/mods/sodium/ (Sodium - from modules/sodium-1.21.9)
├── net/irisshaders/iris/       (Iris - from modules/Iris-1.21.9)
└── com/seibel/distanthorizons/ (DH - from modules/distant-horizons)
```

#### 2. Gradle Changes (build.gradle)

**Remove separate source sets** (lines 385-491):
```gradle
// DELETE or comment out:
// sourceSets.sodium { ... }
// sourceSets.iris { ... }
// sourceSets.distantHorizons { ... }
```

**Simplify main source set** (keep only main):
```gradle
sourceSets {
    fabricLoader {
        // Keep this - Fabric Loader still separate for Knot architecture
        java {
            srcDirs = ["${fabricLoaderDir}/src/main/java", ...]
        }
    }
    
    main {
        java {
            srcDir '.'  // Includes all: Minecraft + Sodium + Iris + DH
            
            exclude 'gradle/**'
            exclude 'build/**'
            exclude 'frnsrc/**'
            exclude 'modules/**'  // Don't compile modules twice
        }
        
        compileClasspath += sourceSets.fabricLoader.output
        runtimeClasspath += sourceSets.fabricLoader.output
    }
}
```

**Remove separate JAR tasks** (lines 629-740):
```gradle
// DELETE:
// tasks.register('sodiumJar', Jar) { ... }
// tasks.register('irisJar', Jar) { ... }
// tasks.register('distantHorizonsJar', Jar) { ... }
```

**Keep fabricLoaderJar and gameJar** (they're still needed):
```gradle
tasks.register('fabricLoaderJar', Jar) {
    archiveBaseName = 'fabric-loader'
    from sourceSets.fabricLoader.output
    // ... keep as-is
}

tasks.register('gameJar', Jar) {
    archiveBaseName = 'minecraft'
    from sourceSets.main.output  // Now includes Minecraft + all mods!
    // ... keep as-is
}
```

#### 3. Fabric Loader Modification

Modify Fabric Loader to recognize internal mods instead of scanning JARs.

**File**: `modules/fabric-loader-0.18.2/src/main/java/net/fabricmc/loader/impl/discovery/ModDiscoverer.java`

Add hardcoded mod discovery:
```java
public class ModDiscoverer {
    public void discoverMods() {
        // OLD: scanModsDirectory("run/mods")
        
        // NEW: Register internal mods
        registerInternalMod("sodium", "0.7.2", 
            "net.caffeinemc.mods.sodium.fabric.SodiumFabricMod",
            "sodium-common.mixins.json", "sodium-fabric.mixins.json");
            
        registerInternalMod("iris", "1.9.6",
            "net.irisshaders.iris.fabric.IrisFabricMod", 
            "mixins.iris.json", "mixins.iris.fabric.json", ...);
            
        if (isDistantHorizonsEnabled()) {
            registerInternalMod("distanthorizons", "2.4.4-b-dev",
                "com.seibel.distanthorizons.fabric.DhFabricMod",
                "mixins.distanthorizons.json");
        }
    }
}
```

#### 4. Resource Organization

```
src/main/resources/
├── fabric.mod.json                    (Minecraft metadata)
├── sodium-common.mixins.json          (Sodium mixins)
├── sodium-fabric.mixins.json
├── mixins.iris.json                   (Iris mixins)
├── mixins.iris.fabric.json
├── mixins.iris.compat.sodium.json
├── mixins.distanthorizons.json        (DH mixins)
├── assets/
│   ├── minecraft/                     (Minecraft assets)
│   ├── sodium/                        (Sodium assets)
│   └── iris/                          (Iris assets)
└── ...
```

### Pros & Cons

**Pros**:
- ✅ Minimal code changes
- ✅ Keep mixin infrastructure (easier to maintain)
- ✅ Still extensible for future mods
- ✅ Faster than current JAR-based loading
- ✅ All code in one JAR

**Cons**:
- ⚠️ Still have Fabric Loader overhead
- ⚠️ Mixins have runtime cost
- ⚠️ More complex than direct integration
- ⚠️ Fabric Loader still needs to be understood/maintained

### Estimated Effort
- **Time**: 1-2 weeks
- **Complexity**: Medium
- **Risk**: Low (can rollback easily)

---

## Option B: Remove Fabric Loader (Advanced)

### Architecture
```
JVM starts
  ↓
Minecraft Main class directly
  ↓
Initialize Sodium (direct method call)
  ↓
Initialize Iris (direct method call)
  ↓
Initialize DH (direct method call)
  ↓
Game runs with mod code directly integrated
```

### Changes Required

#### 1. Convert ALL Mixins to Direct Code

**Example: Sodium's ChunkRenderingMixin**

Before (mixin):
```java
@Mixin(ChunkRenderer.class)
public class ChunkRendererMixin {
    @Inject(method = "renderChunk", at = @At("HEAD"), cancellable = true)
    private void onRenderChunk(CallbackInfo ci) {
        // Sodium's optimized chunk rendering
        SodiumChunkRenderer.render(...);
        ci.cancel();
    }
}
```

After (direct integration):
```java
// In Minecraft's ChunkRenderer.java:
public class ChunkRenderer {
    public void renderChunk(...) {
        // OLD CODE (delete or comment):
        // vanillaRenderingCode();
        
        // NEW CODE (directly call Sodium):
        SodiumChunkRenderer.render(...);
    }
}
```

**Challenge**: 
- Sodium has ~50+ mixins
- Iris has ~60+ mixins
- Each needs manual conversion
- Must understand what each mixin does

#### 2. Direct Initialization

**File**: `net/minecraft/client/Minecraft.java`

```java
public class Minecraft {
    public Minecraft(GameConfig config) {
        // ... existing initialization ...
        
        // Initialize Sodium
        SodiumClientMod.initialize();
        
        // Initialize Iris (depends on Sodium)
        IrisApi.initialize();
        
        // Initialize Distant Horizons (if enabled)
        if (isDistantHorizonsEnabled()) {
            DistantHorizons.initialize();
        }
    }
}
```

#### 3. Remove All Fabric Dependencies

**build.gradle changes**:
```gradle
// DELETE Fabric Loader source set entirely
// DELETE fabricLoaderJar task
// DELETE all Mixin dependencies (lines 236-300)
// DELETE Fabric Loader dependencies

// Keep only essential dependencies:
dependencies {
    // Minecraft core dependencies
    implementation 'com.mojang:brigadier:1.3.10'
    implementation 'com.mojang:datafixerupper:8.0.16'
    // ... etc
    
    // Mod-specific dependencies (keep these)
    implementation 'org.anarres:jcpp:1.4.14'           // Iris
    implementation 'org.antlr:antlr4-runtime:4.13.1'   // Iris
    implementation 'com.electronwill.night-config:toml:3.6.6'  // DH
    
    // REMOVE:
    // implementation 'org.ow2.asm:asm:9.9'  
    // implementation 'net.fabricmc:sponge-mixin:...'
    // etc.
}
```

#### 4. Single JAR Task

```gradle
jar {
    from sourceSets.main.output
    
    manifest {
        attributes(
            'Main-Class': 'net.minecraft.client.main.Main'  // Direct Minecraft launch
        )
    }
}
```

### Pros & Cons

**Pros**:
- ✅ No Fabric Loader overhead
- ✅ No mixin runtime cost
- ✅ Cleaner architecture
- ✅ Easier to debug (no bytecode modification)
- ✅ Faster startup and runtime

**Cons**:
- ❌ Massive amount of work (100+ mixins to convert)
- ❌ Hard to update mods (can't just copy new versions)
- ❌ Merge conflicts when Minecraft updates
- ❌ Lose extensibility (can't easily add new mods)
- ❌ Risk of breaking mod functionality

### Estimated Effort
- **Time**: 2-3 months
- **Complexity**: Very High
- **Risk**: High (many places to introduce bugs)

---

## Option C: Hybrid Approach (Middle Ground)

### Architecture
```
JVM starts
  ↓
Minimal Fabric Loader (just mixin engine)
  ↓
Hardcoded mod list (no discovery)
  ↓
Minecraft + Mods (same JAR)
  ↓
Game runs with mixins
```

### Key Idea
Keep only the mixin engine from Fabric Loader, remove everything else:
- Remove mod discovery
- Remove JAR scanning
- Remove entrypoint system (use direct calls)
- Keep mixin application

### Changes

1. **Strip down Fabric Loader** to just mixin initialization
2. **Hardcode mixin configs** in launcher
3. **Direct call mod init** instead of entrypoint system

### Pros & Cons

**Pros**:
- ✅ Keep mixins (easier than converting)
- ✅ Remove some Fabric overhead
- ✅ Simpler than full Fabric

**Cons**:
- ⚠️ Still have mixin runtime cost
- ⚠️ More complex than Option A
- ⚠️ Less clean than Option B

### Estimated Effort
- **Time**: 3-4 weeks
- **Complexity**: High
- **Risk**: Medium

---

## Recommendation: Phased Approach

### Phase 1: Option A (Keep Fabric, Integrate Source)
**Do this first** (1-2 weeks):
1. Copy all mod source to `src/main/java/`
2. Remove separate source sets
3. Build single game JAR with all mods
4. Keep Fabric Loader as-is
5. Modify loader to recognize internal mods

**Benefits**:
- Quick win - see integrated JAR working
- Low risk - can rollback easily
- Learn the integration challenges

### Phase 2: Optimize Loader (Optional)
**After Phase 1 works** (1-2 weeks):
1. Strip unnecessary Fabric Loader features
2. Optimize mod discovery
3. Remove JAR scanning entirely

**Benefits**:
- Faster startup
- Cleaner code
- Better understanding of Fabric internals

### Phase 3: Consider Full Integration (Long-term)
**Only if you want** (2-3 months):
1. Convert mixins to direct code
2. Remove Fabric Loader entirely
3. Pure Minecraft + Mods codebase

**Benefits**:
- Ultimate performance
- Cleanest architecture
- Complete control

---

## Technical Deep Dive: Mixin Challenges

### What Mixins Do

Mixins let mods modify Minecraft code without editing source:

```java
// Minecraft source (you can't edit in a mod):
public class ChunkRenderer {
    public void render() {
        // vanilla rendering
    }
}

// Sodium's mixin (in separate JAR):
@Mixin(ChunkRenderer.class)
public class ChunkRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void optimizedRender(CallbackInfo ci) {
        doSodiumRendering();
        ci.cancel();  // Don't run vanilla code
    }
}
```

At runtime, Mixin library rewrites bytecode:
```java
// ChunkRenderer.class bytecode becomes:
public void render() {
    ChunkRendererMixin.optimizedRender(this, ci);
    if (ci.isCancelled()) return;
    // original vanilla code
}
```

### Why Mixins Are Convenient

In your case, you **have the Minecraft source**, so you **could** just edit it:

```java
// Direct edit to ChunkRenderer.java:
public class ChunkRenderer {
    public void render() {
        // Call Sodium directly
        SodiumChunkRenderer.render(this);
        // Skip vanilla code
    }
}
```

But this has downsides:
- **Updates are hard**: When Minecraft updates, you must re-apply edits
- **Mod updates are hard**: When Sodium updates, you must re-merge
- **Conflicts**: Multiple mods editing same method

### Keeping Mixins: The Trade-off

**Pros of keeping mixins**:
- Mods stay "separate" logically
- Easier to update mods (just copy new mixins)
- Less merge conflicts
- Proven approach (Fabric ecosystem)

**Cons of keeping mixins**:
- Runtime overhead (bytecode rewriting)
- Startup overhead (mixin processing)
- Complexity (need to understand mixin system)
- Debugging harder (code doesn't match source)

---

## Build System Comparison

### Current Build (Separate JARs)
```
./gradlew build produces:
├── build/libs/
│   ├── fabric-loader-0.18.2.jar       (~2 MB)
│   └── minecraft-1.21.10.jar          (~50 MB)
└── build/mods/
    ├── sodium-0.7.2-mc1.21.10.jar     (~800 KB)
    ├── iris-1.9.6-mc1.21.10.jar       (~2 MB)
    └── distanthorizons-2.4.4.jar      (~5 MB)

Total: ~60 MB in 5 files
```

### Option A Build (Unified JAR, Keep Loader)
```
./gradlew build produces:
├── build/libs/
│   ├── fabric-loader-0.18.2.jar       (~2 MB)
│   └── minecraft-1.21.10.jar          (~58 MB - includes all mods)

Total: ~60 MB in 2 files
```

### Option B Build (Fully Integrated)
```
./gradlew build produces:
└── build/libs/
    └── minecraft-1.21.10.jar          (~56 MB - no Fabric overhead)

Total: ~56 MB in 1 file
```

---

## Performance Comparison

### Startup Time Estimates

**Current (Separate JARs)**:
- JVM start: ~1 sec
- Fabric Loader init: ~2 sec
- Scan mods directory: ~0.5 sec
- Load 3 mod JARs: ~1 sec
- Parse fabric.mod.json: ~0.2 sec
- Initialize mixins: ~3 sec
- Start Minecraft: ~5 sec
- **Total: ~12.7 seconds**

**Option A (Unified JAR, Keep Loader)**:
- JVM start: ~1 sec
- Fabric Loader init: ~2 sec
- Hardcoded mod list: ~0.1 sec (no scanning)
- Initialize mixins: ~3 sec (same)
- Start Minecraft: ~5 sec
- **Total: ~11.1 seconds (13% faster)**

**Option B (No Fabric, Direct Integration)**:
- JVM start: ~1 sec
- Direct mod init: ~0.5 sec
- Start Minecraft: ~5 sec
- **Total: ~6.5 seconds (49% faster)**

### Runtime Performance

**Option A vs Current**: Similar (mixins still used)
**Option B vs Current**: ~5-10% faster (no mixin overhead, better JIT optimization)

---

## Migration Strategy: Step-by-Step

### Week 1: Preparation
- [ ] Read this document thoroughly
- [ ] Create backup branch
- [ ] Test current build works
- [ ] Document current behavior (screenshots, logs)

### Week 2-3: Sodium Integration (Option A)
- [ ] Copy Sodium source to src/main/java
- [ ] Update build.gradle (remove Sodium source set)
- [ ] Test compilation
- [ ] Fix any errors
- [ ] Test game launch
- [ ] Verify Sodium features work

### Week 4-5: Iris Integration (Option A)
- [ ] Copy Iris source to src/main/java
- [ ] Update build.gradle (remove Iris source set)
- [ ] Test compilation
- [ ] Fix any errors
- [ ] Test shader loading
- [ ] Verify Iris features work

### Week 6: Distant Horizons (Optional)
- [ ] Copy DH source if wanted
- [ ] Same process as Sodium/Iris

### Week 7+: Optimization (Optional)
- [ ] Profile startup time
- [ ] Identify bottlenecks
- [ ] Optimize Fabric Loader
- [ ] Consider Phase 2/3

---

## Conclusion

**Start with Option A** (Keep Fabric Loader, integrate source):
- Least risky
- Quickest results
- Provides foundation for future optimization

**Only consider Option B** if:
- You're willing to invest 2-3 months
- You want ultimate performance
- You're comfortable with complex refactoring
- You don't need to update mods frequently

The documents `MOD_INTEGRATION_PLAN.md` and `INTEGRATION_QUICK_START.md` provide detailed steps for Option A.

Good luck with your integration!
