# MattMC Mod Integration Plan

## Executive Summary

This document provides an **EXTREMELY COMPREHENSIVE** plan for integrating the mods currently compiled separately (Fabric Loader, Sodium, Iris, and Distant Horizons) directly into the main Minecraft 1.21.10 game JAR. The goal is to simplify the build system and have all features in a single JAR while maintaining all functionality.

**Current Architecture**: Mods are compiled into separate JARs and loaded at runtime via Fabric Loader  
**Target Architecture**: All mod code absorbed into Minecraft's package structure, with mixin transformations applied directly to source code

**Critical Design Decision**: To achieve true integration without creating compilation dependency issues, all mod code must be **moved into Minecraft's package hierarchy** (e.g., `net.minecraft.client.renderer.sodium.*` instead of `net.caffeinemc.mods.sodium.*`). This is the ONLY way to avoid the circular dependency problem where Minecraft would need to depend on mods that already depend on Minecraft.

**Why Full Absorption is Necessary**:
- ❌ Minecraft CANNOT depend on mod packages at compile time (creates circular dependency)
- ✅ Solution: Move mod packages INTO Minecraft's package structure
- ✅ Result: Everything becomes part of Minecraft core, no external mod dependencies

**Access Widener Status**: The 53 access widener modifications from Distant Horizons have already been applied directly to the Minecraft source code, so no additional access widening is needed during integration.

**Testing Requirement**: After each integration phase, the game must be tested to ensure it runs and works properly with no regressions before proceeding to the next phase.

---

## Table of Contents

1. [Project Structure Analysis](#1-project-structure-analysis)
2. [Mod Inventory and Statistics](#2-mod-inventory-and-statistics)
3. [Dependency Graph](#3-dependency-graph)
4. [Technical Challenges](#4-technical-challenges)
5. [Integration Strategy](#5-integration-strategy)
6. [Phase-by-Phase Integration Plan](#6-phase-by-phase-integration-plan)
7. [Testing Strategy](#7-testing-strategy)
8. [Rollback and Contingency Plans](#8-rollback-and-contingency-plans)
9. [Long-term Maintenance](#9-long-term-maintenance)

---

## 1. Project Structure Analysis

### 1.1 Current Build System

The project uses a Gradle-based multi-source-set architecture:

```
MattMC/
├── src/main/java/              # Minecraft 1.21.10 source (6,102 files)
│   ├── net/minecraft/          # Core game code
│   ├── net/fabricmc/fabric/    # Fabric API stubs (currently in main source)
│   ├── net/sodium/             # Sodium API stubs
│   ├── net/iris/               # Iris API stubs
│   └── net/distant_horizons/   # DH API stubs
├── modules/                    # Mods compiled from source
│   ├── fabric-loader-0.18.2/   # 182 Java files
│   ├── sodium-1.21.9/          # 480 Java files
│   ├── Iris-1.21.9/            # 699 Java files
│   └── distant-horizons/       # 635 Java files
└── build.gradle                # Multi-source-set configuration
```

### 1.2 Current Compilation Flow

1. **Fabric Loader** → Compiled to `fabric-loader-0.18.2.jar`
2. **Main Minecraft** → Compiled to `minecraft-1.21.10.jar`
3. **Sodium** → Compiled to `sodium-0.7.2-mc1.21.10.jar` (depends on Fabric Loader + Minecraft)
4. **Iris** → Compiled to `iris-1.9.6-mc1.21.10.jar` (depends on Fabric Loader + Minecraft + Sodium)
5. **Distant Horizons** → Compiled to `distanthorizons-2.4.4-b-dev-mc1.21.10.jar` (depends on Fabric Loader + Minecraft)

### 1.3 Runtime Loading Mechanism

Currently, the game starts via **Fabric Loader's Knot launcher**:

```
Main Class: net.fabricmc.loader.impl.launch.knot.KnotClient
  ↓
  Loads fabric-loader-0.18.2.jar
  ↓
  Loads minecraft-1.21.10.jar (game JAR)
  ↓
  Scans run/mods/ directory
  ↓
  Loads mod JARs: sodium, iris, distanthorizons
  ↓
  Applies mixins to Minecraft classes
  ↓
  Calls mod entrypoints
  ↓
  Starts Minecraft: net.minecraft.client.main.Main
```

---

## 2. Mod Inventory and Statistics

### 2.1 Fabric Loader (0.18.2)

**Purpose**: Mod loading framework that enables mixin transformation and mod initialization

**Statistics**:
- **Source Files**: 182 Java files
- **Packages**: `net.fabricmc.loader.*`
- **Key Components**:
  - Knot launcher (game JAR detection and transformation)
  - Mixin integration (bytecode transformation engine)
  - Mod discovery and loading
  - Entrypoint system
  - Access widener support

**Dependencies**:
- ASM 9.9 (bytecode manipulation)
- SpongePowered Mixin 0.16.5+mixin.0.8.7
- Tiny Remapper 0.11.2
- Access Widener 2.1.0
- MixinExtras 0.5.0

### 2.2 Sodium (0.7.2)

**Purpose**: Advanced rendering optimization engine

**Statistics**:
- **Source Files**: 480 Java files
- **Packages**: `net.caffeinemc.mods.sodium.*`
- **Mixins**: 106+ mixin classes across 2 configuration files
- **Reflection Usage**: 0 instances (uses mixins exclusively)
- **Entrypoints**:
  - `net.caffeinemc.mods.sodium.fabric.SodiumFabricMod` (client init)
  - `net.caffeinemc.mods.sodium.fabric.SodiumPreLaunch` (pre-launch)

**Key Features**:
- Chunk rendering optimization
- Custom OpenGL rendering backend
- Memory-efficient mesh building
- Advanced culling algorithms
- GUI options integration

**Fabric API Dependencies**:
- `net.fabricmc.fabric.api.renderer.v1.*` (rendering API)
- `net.fabricmc.fabric.api.blockview.v2.*`
- `net.fabricmc.fabric.api.client.render.fluid.v1.*`

### 2.3 Iris (1.9.6)

**Purpose**: Shader pack support (OptiFine shaders compatibility)

**Statistics**:
- **Source Files**: 699 Java files
- **Packages**: `net.irisshaders.iris.*`
- **Mixins**: 150+ mixin classes across 10 configuration files
- **Reflection Usage**: 10 instances (mostly for compatibility checks)
- **Entrypoints**: Dynamic registration via Fabric Loader

**Key Features**:
- GLSL shader loading and compilation
- Shadow map rendering
- Post-processing effects
- Sodium compatibility layer
- Distant Horizons compatibility layer

**Dependencies**:
- **Hard Dependency**: Sodium (uses Sodium's rendering API extensively)
- **Optional Dependency**: Distant Horizons (via compatibility layer)
- **External Libraries**:
  - JCPP 1.4.14 (C preprocessor for shaders)
  - ANTLR 4.13.1 (shader parsing)
  - GLSL Transformer 3.0.0-pre3

**Fabric API Dependencies**:
- `net.fabricmc.fabric.api.client.keybinding.v1.*`

### 2.4 Distant Horizons (2.4.4-b-dev)

**Purpose**: Level of Detail (LOD) rendering for extended view distances

**Statistics**:
- **Source Files**: 635 Java files
- **Packages**: `com.seibel.distanthorizons.*`
- **Mixins**: Multiple mixin configurations
- **Reflection Usage**: 12 instances (for internal state access)
- **Access Wideners**: 53 access modifications (1_21_10.distanthorizons.accesswidener)
- **Entrypoints**:
  - `com.seibel.distanthorizons.fabric.FabricMain` (client + server)

**Key Features**:
- LOD chunk generation and caching
- Distant terrain rendering
- Data persistence (SQLite database)
- Multi-threaded world generation
- API for shader integration

**Dependencies**:
- **External Libraries**:
  - SQLite JDBC 3.47.2.0 (data storage)
  - Zstd-JNI 1.5.7-6 (compression)
  - LZ4 1.8.0 (compression)
  - XZ 1.9 (compression)
  - NightConfig TOML/JSON 3.6.6 (configuration)
  - LWJGL JAWT 3.3.3 (GUI integration)

**Access Widener Requirements**:
```
# Examples from 1_21_10.distanthorizons.accesswidener
accessible field net/minecraft/world/level/storage/DimensionDataStorage dataFolder
accessible field net/minecraft/client/renderer/LevelRenderer visibleSections
accessible method net/minecraft/client/renderer/GameRenderer getFov
accessible field net/minecraft/world/level/chunk/LevelChunk loaded
# ... 49 more modifications
```

---

## 3. Dependency Graph

### 3.1 Compile-Time Dependencies

```
Minecraft Base (6,102 files)
    ↓
Fabric Loader (182 files) ← Independent, provides APIs
    ↓
    ├─→ Sodium (480 files)
    │   Depends on: Fabric Loader, Minecraft, Fabric Renderer API
    │
    ├─→ Distant Horizons (635 files)
    │   Depends on: Fabric Loader, Minecraft
    │   Access Wideners: 53 modifications
    │
    └─→ Iris (699 files)
        Depends on: Fabric Loader, Minecraft, Sodium (HARD), DH (SOFT)
        Integrates with Sodium's rendering pipeline
        Optionally integrates with DH's LOD rendering
```

### 3.2 Runtime Dependencies

**Mixin Application Order** (critical for proper transformation):
1. Fabric Loader mixins (applied first)
2. Sodium mixins (core rendering transformations)
3. Iris mixins (shader integration, some override Sodium mixins)
4. Distant Horizons mixins (LOD integration)

**Initialization Order** (critical for proper startup):
1. Fabric Loader pre-launch entrypoints
2. Sodium pre-launch setup
3. Mod main entrypoints (order determined by dependencies)
4. Minecraft game initialization

### 3.3 Inter-Mod API Usage

**Iris → Sodium**:
- 20+ direct imports from Sodium packages
- Uses: GL rendering backend, shader system, options GUI integration
- Mixins: Modifies Sodium classes for shader compatibility

**Iris → Distant Horizons**:
- Conditional imports (only when DH is present)
- Uses: DH API for LOD shader integration
- Compatibility layer in `net.irisshaders.iris.compat.dh.*` (6 classes)

## 4. Technical Challenges

### 4.1 Mixin System Challenges

**Challenge**: Mixins are applied at runtime by Fabric Loader during class loading

**Current System**:
- Mixins defined in JSON configuration files (18 total across all mods)
- SpongePowered Mixin library transforms bytecode on-the-fly
- Classes are modified before being loaded by the JVM

**Integration Solution - Full Code Absorption**:

The only way to truly integrate mods without creating compilation dependencies is to **move all mod code into Minecraft's package structure**. This eliminates the mod/Minecraft separation entirely.

**Why Minecraft Cannot Depend on Mod Packages**:
```
Current (Separate Mods):
- Sodium depends on Minecraft ✅
- Iris depends on Minecraft ✅  
- DH depends on Minecraft ✅

If we try to keep mod packages:
- Minecraft would need to import net.caffeinemc.* ❌
- But net.caffeinemc.* already imports net.minecraft.* ❌
- This creates: Minecraft ↔ Mods (bidirectional dependency - impossible!)
```

**The Solution - Absorb Mods into Minecraft**:
```
After Integration:
- No more separate mod packages
- Sodium code moves to: net.minecraft.client.renderer.sodium.*
- Iris code moves to: net.minecraft.client.renderer.shaders.iris.*
- DH code moves to: net.minecraft.client.renderer.lod.*
- Everything is now part of Minecraft's codebase
- No external dependencies!
```

**Mixin Integration Process**:

**Step 1: Relocate Mod Classes**
```
Before:
  net/caffeinemc/mods/sodium/client/SodiumClientMod.java
  
After:
  net/minecraft/client/renderer/sodium/SodiumRenderer.java
```

**Step 2: Update Package Declarations**
```java
// Before (in mod package):
package net.caffeinemc.mods.sodium.client;
import net.minecraft.client.renderer.LevelRenderer;

// After (in Minecraft package):
package net.minecraft.client.renderer.sodium;
import net.minecraft.client.renderer.LevelRenderer;  // Same package tree now!
```

**Step 3: Apply Mixin Transformations**
```java
// Original Mixin:
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevel(CallbackInfo ci) {
        SodiumClientMod.onRenderStart();  // References mod class
    }
}

// After Integration:
// In net/minecraft/client/renderer/LevelRenderer.java
import net.minecraft.client.renderer.sodium.SodiumRenderer;  // Now same package tree!

public void renderLevel(...) {
    // INTEGRATED: Sodium render start
    SodiumRenderer.onRenderStart();  // ✅ Both classes in net.minecraft.*
    
    // Original Minecraft code...
}
```

**Why This Works**:
- ✅ Everything is in `net.minecraft.*` packages
- ✅ No external mod dependencies
- ✅ No compilation dependency issues
- ✅ True integration - mods become part of Minecraft
- ✅ Single cohesive codebase

**Integration Approach by Mixin Type**:

1. **@Inject**: Add method call at injection point
2. **@Accessor**: Add public getter/setter methods
3. **@Overwrite**: Replace method body entirely
4. **@ModifyVariable**: Inline the modification logic
5. **@Redirect**: Inline the redirection logic
6. **Interface @Mixin**: Make target class implement interface directly

**Example Package Reorganization**:
```
Before Integration:
  modules/sodium/src/.../net/caffeinemc/mods/sodium/
  modules/iris/src/.../net/irisshaders/iris/
  modules/distant-horizons/src/.../com/seibel/distanthorizons/

After Integration:
  src/main/java/net/minecraft/client/renderer/sodium/
  src/main/java/net/minecraft/client/renderer/shaders/iris/
  src/main/java/net/minecraft/client/renderer/lod/
```

**Benefits of Full Absorption**:
- ✅ No mod packages = no dependency issues
- ✅ Everything compiles together as one project
- ✅ True integration, not just bundling
- ✅ Simplified maintenance
- ✅ Single JAR with all features built-in

### 4.2 Access Widener Challenges

**STATUS**: Access widener modifications have already been applied to Minecraft source code.

**What Was Done**:
- The 53 access widener entries from Distant Horizons have been manually applied
- Private/protected members changed to public/accessible as needed
- Changes are permanent in the Minecraft source files

**Original Challenge** (now resolved):
- Access wideners defined in `.accesswidener` files
- Fabric Loader would widen access at class load time
- Need to change private/protected members to public/accessible

**Integration Impact**:
- No access widener processing needed during integration
- Distant Horizons code can directly access previously-private Minecraft members
- Access widener files can be removed from resources (no longer needed)

**Example of Applied Access Widener**:
```java
// Before (original Minecraft):
private Path dataFolder;

// After (widened for Distant Horizons):
public Path dataFolder;  // ACCESS WIDENED for DistantHorizons
```

**Maintenance Note**:
- When updating Minecraft versions, access widener changes must be reapplied
- Maintain documentation of all widened members for future reference

### 4.3 Fabric API Challenges

**Challenge**: Mods depend on Fabric API interfaces that provide abstraction over Minecraft internals

**Current Fabric API Usage**:
- Sodium: Renderer API v1 (extensive usage)
- Iris: KeyBinding API v1 (minimal usage)
- All mods: Loader API (mod metadata, entrypoints, config)

**Existing Stubs in Main Source**:
```
src/main/java/net/fabricmc/fabric/api/
├── renderer/v1/          # Used by Sodium extensively
├── client/keybinding/v1/ # Used by Iris
├── blockview/v2/         # Used by Sodium
└── client/render/fluid/v1/ # Used by Sodium
```

**Integration Challenges**:
1. **API Compatibility**: Stubs must match exact behavior expected by mods
2. **Incomplete Implementation**: Some stubs may be partial implementations
3. **Version Mismatch**: Fabric API evolves, stubs may be outdated

### 4.4 Reflection Usage Challenges

**Challenge**: Some mods use reflection for dynamic access

**Reflection Usage Analysis**:
- Sodium: 0 instances (clean!)
- Iris: 10 instances
- Distant Horizons: 12 instances
- Fabric Loader: Extensive (core functionality)

**Integration Challenges**:
1. **Class Name Changes**: Reflection uses string class names, must match exactly
2. **Package Restructuring**: Moving code may break reflection calls
3. **Dynamic Loading**: Some reflection is for optional mod detection

**Example Reflection** (Iris compatibility check):
```java
try {
    Class.forName("com.seibel.distanthorizons.api.DhApi");
    // Distant Horizons is present
} catch (ClassNotFoundException e) {
    // Distant Horizons not present
}
```

### 4.5 Resource Loading Challenges

**Challenge**: Each mod has its own resources (assets, data, mixin configs)

**Current Resources**:
- **Sodium**: Shaders, icons, configuration files
- **Iris**: Shader templates, icons, mixin configs
- **Distant Horizons**: SQL scripts, icons, access wideners, configuration files

**Integration Challenges**:
1. **Resource Path Conflicts**: Multiple mods may have same resource paths
2. **Mod-Specific Resources**: Some resources reference mod IDs
3. **Resource Loading**: Fabric Loader's resource loading must be replaced

### 4.6 Entrypoint System Challenges

**Challenge**: Mods define entrypoints that Fabric Loader calls at specific lifecycle stages

**Current Entrypoints**:
```
Sodium:
  - client: SodiumFabricMod.onInitializeClient()
  - preLaunch: SodiumPreLaunch.onPreLaunch()

Iris:
  - (Dynamic registration via Fabric Loader)

Distant Horizons:
  - client: FabricMain.onInitializeClient()
  - server: FabricMain.onInitializeServer()
```

**Integration Challenges**:
1. **Initialization Order**: Entrypoints must be called in correct dependency order
2. **Lifecycle Hooks**: Need to replicate Fabric Loader's lifecycle stages
3. **Conditional Init**: Some mods only initialize on client or server

### 4.7 Build System Complexity

**Current Build System**:
- 5 separate source sets (fabricLoader, main, sodium, iris, distantHorizons)
- Complex classpath management
- Separate JAR tasks for each mod
- Version substitution in fabric.mod.json files

**Integration Challenges**:
1. **Simplified Build**: Need single source set and single JAR output
2. **Dependency Management**: All dependencies must be compatible
3. **Version Management**: No more separate mod versions
4. **Distribution**: Single JAR vs current multi-JAR approach

---

## 5. Integration Strategy

### 5.1 Core Principles

1. **Incremental Integration**: Integrate one mod at a time, test thoroughly
2. **Preserve Functionality**: All features must work after integration
3. **Minimize Code Changes**: Prefer refactoring over rewriting
4. **Maintain Separation**: Keep mod code organized for future updates
5. **Comprehensive Testing**: Test after each phase

### 5.2 Integration Approach

**RECOMMENDED: Full Absorption Integration**

This approach truly integrates mods by moving their functionality into the Minecraft source tree, eliminating the mod/Minecraft separation entirely.

**Key Principle**: Minecraft CANNOT depend on mod packages at compile time. Therefore, we must move/copy mod functionality INTO Minecraft's package structure.

**Integration Strategy**:

**Phase 1: Move Core Mod Logic into Minecraft Package**
- Identify which mod classes provide core functionality (not just mixin glue)
- Move these classes into appropriate Minecraft packages
- Example: Move `SodiumClientMod` → `net.minecraft.client.renderer.sodium.SodiumRenderer`
- Example: Move Iris shader classes → `net.minecraft.client.renderer.shaders.iris.*`
- Example: Move DH LOD system → `net.minecraft.client.renderer.lod.*`

**Phase 2: Apply Mixin Transformations Directly**
- For each mixin, inline the transformation into the target Minecraft class
- Since mod classes are now IN Minecraft packages, no dependency issues
- Example:

```java
// Original Mixin (in mod):
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevel(CallbackInfo ci) {
        SodiumClientMod.onRenderStart();  // References mod class
    }
}

// After Integration (Minecraft class):
// net/minecraft/client/renderer/LevelRenderer.java
public void renderLevel(...) {
    // INTEGRATED: Sodium render start (moved from SodiumClientMod)
    net.minecraft.client.renderer.sodium.SodiumRenderer.onRenderStart();
    
    // Original Minecraft code...
}
```

**Why This Works**:
- ✅ No mod packages exist anymore - everything is Minecraft
- ✅ No compilation dependencies on external mods
- ✅ True integration - functionality is now part of Minecraft
- ✅ Single JAR with all features built-in

**The Key Difference**:
- ❌ DON'T: Make Minecraft import `net.caffeinemc.mods.sodium.*`
- ✅ DO: Move Sodium classes to `net.minecraft.client.renderer.sodium.*`
- Result: Everything is in Minecraft's package tree

**Package Reorganization**:

```
src/main/java/
├── net/minecraft/
│   ├── client/
│   │   ├── renderer/
│   │   │   ├── sodium/              # Sodium rendering (MOVED from mod)
│   │   │   │   ├── SodiumRenderer.java
│   │   │   │   ├── chunk/
│   │   │   │   └── gl/
│   │   │   ├── shaders/
│   │   │   │   ├── iris/            # Iris shaders (MOVED from mod)
│   │   │   │   │   ├── IrisShaderManager.java
│   │   │   │   │   └── pipeline/
│   │   │   ├── lod/                 # DH LOD system (MOVED from mod)
│   │   │   │   ├── LodRenderer.java
│   │   │   │   └── storage/
│   │   │   └── LevelRenderer.java   # MODIFIED with integrated code
│   │   └── Minecraft.java           # MODIFIED for initialization
│   └── server/
└── com/mojang/                       # Mojang libraries (unchanged)
```

**What Gets Integrated**:
1. **Mods' core functionality**: Moved into Minecraft package structure
2. **Mixin transformations**: Applied directly to Minecraft source
3. **Resources**: Merged into Minecraft resources
4. **Dependencies**: Added to Minecraft's dependencies

**What Gets Removed**:
1. ❌ Mod packages (net.caffeinemc.*, net.irisshaders.*, com.seibel.*)
2. ❌ Mixin JSON configs (no longer needed)
3. ❌ SpongePowered Mixin library (no runtime transformation)
4. ❌ Fabric Loader (no mod loading)

**This is TRUE Integration**:
- All functionality becomes part of Minecraft core
- No separate "mod" concept exists
- Single codebase, single package hierarchy
- Like the features were built into Minecraft from the start

### 5.3 Package Structure After Integration

```
src/main/java/
├── com/mojang/                      # Mojang libraries (existing)
├── net/minecraft/                   # Minecraft core (existing)
│   ├── client/
│   ├── server/
│   └── ... (6,102 files)
├── net/fabricmc/
│   ├── loader/                      # Fabric Loader internals (integrated)
│   └── fabric/api/                  # Fabric API stubs (existing, expanded)
├── net/caffeinemc/mods/sodium/      # Sodium (480 files, integrated)
│   ├── client/
│   ├── mixin/
│   └── fabric/
├── net/irisshaders/iris/            # Iris (699 files, integrated)
│   ├── api/
│   ├── compat/
│   ├── mixin/
│   └── pipeline/
└── com/seibel/distanthorizons/      # Distant Horizons (635 files, integrated)
    ├── api/
    ├── core/
    ├── common/
    └── fabric/
```

### 5.4 Mixin Integration Strategy

**GOAL**: Fully integrate mod functionality into Minecraft by moving mod code into Minecraft's package structure and applying mixin transformations directly.

**Core Principle**: Since Minecraft cannot depend on mod packages at compile time, we must move ALL mod functionality INTO Minecraft's package hierarchy.

**Step-by-Step Integration Process**:

**Phase 1: Analyze and Categorize Mod Code**

For each mod, categorize classes into:

1. **Core Functionality Classes**: Classes that implement actual features
   - Sodium: Rendering engine, chunk builders, GL utilities
   - Iris: Shader manager, pipeline, uniform handling
   - DH: LOD generator, storage, rendering

2. **Mixin Classes**: Classes that inject into Minecraft
   - These will be eliminated - their logic moves into target classes

3. **API/Interface Classes**: Public APIs exposed by mods
   - Will move into Minecraft packages

4. **Utility Classes**: Helpers, data structures, algorithms
   - Will move into appropriate Minecraft util packages

**Phase 2: Relocate Mod Classes into Minecraft Packages**

Move mod classes into Minecraft's package structure:

```
Sodium Classes:
  FROM: net.caffeinemc.mods.sodium.client.render.*
  TO:   net.minecraft.client.renderer.sodium.*

  FROM: net.caffeinemc.mods.sodium.client.gl.*
  TO:   net.minecraft.client.renderer.gl.sodium.*

Iris Classes:
  FROM: net.irisshaders.iris.pipeline.*
  TO:   net.minecraft.client.renderer.shaders.iris.*

  FROM: net.irisshaders.iris.gl.*
  TO:   net.minecraft.client.renderer.gl.iris.*

Distant Horizons Classes:
  FROM: com.seibel.distanthorizons.core.render.*
  TO:   net.minecraft.client.renderer.lod.*

  FROM: com.seibel.distanthorizons.core.world.*
  TO:   net.minecraft.world.level.lod.*
```

**Phase 3: Update Package Declarations and Imports**

After moving files:
1. Change package declarations to new Minecraft packages
2. Update all imports to reflect new locations
3. Resolve any naming conflicts
4. Test compilation

**Phase 4: Apply Mixin Transformations**

For each mixin, manually integrate its logic into the target Minecraft class:

**Example 1: Simple Injection**
```java
// Original Mixin:
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevel(CallbackInfo ci) {
        SodiumClientMod.onRenderStart();
    }
}

// Integrated (after moving SodiumClientMod → SodiumRenderer):
// In net/minecraft/client/renderer/LevelRenderer.java
import net.minecraft.client.renderer.sodium.SodiumRenderer;  // Now in Minecraft package!

public void renderLevel(...) {
    // INTEGRATED: Sodium render start hook
    SodiumRenderer.onRenderStart();
    
    // Original Minecraft code continues...
}
```

**Example 2: Field Accessor**
```java
// Original Mixin Accessor:
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("destructionProgress")
    Map<Integer, BlockDestructionProgress> getDestructionProgress();
}

// Integrated: Just add public getter
// In net/minecraft/client/renderer/LevelRenderer.java
public Map<Integer, BlockDestructionProgress> getDestructionProgress() {
    return this.destructionProgress;
}
```

**Example 3: Method Overwrite**
```java
// Original Mixin:
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Overwrite
    public void renderChunks(...) {
        // Sodium's completely different implementation
        SodiumChunkRenderer.render(...);
    }
}

// Integrated: Replace method body
// In net/minecraft/client/renderer/LevelRenderer.java
import net.minecraft.client.renderer.sodium.SodiumChunkRenderer;

public void renderChunks(...) {
    // INTEGRATED: Using Sodium's chunk rendering
    // (Original method body replaced)
    SodiumChunkRenderer.render(...);
}
```

**Example 4: Interface Implementation**
```java
// Original Mixin:
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements SodiumWorldRenderer {
    // Implements interface methods
}

// Integrated: Make class implement interface directly
// In net/minecraft/client/renderer/LevelRenderer.java
import net.minecraft.client.renderer.sodium.SodiumWorldRenderer;

public class LevelRenderer implements AutoCloseable, SodiumWorldRenderer {
    // Implement required methods
    @Override
    public ChunkRenderManager getChunkRenderManager() {
        return this.sodiumChunkRenderManager;
    }
}
```

**Phase 5: Initialize Integrated Systems**

Add initialization in Minecraft startup:

```java
// In net/minecraft/client/Minecraft.java
public Minecraft(GameConfig gameConfig) {
    // Early in constructor, after basic setup
    
    // INTEGRATED: Initialize Sodium rendering system
    net.minecraft.client.renderer.sodium.SodiumRenderer.initialize();
    
    // INTEGRATED: Initialize Iris shader system  
    net.minecraft.client.renderer.shaders.iris.IrisShaderManager.initialize();
    
    // INTEGRATED: Initialize DH LOD system
    net.minecraft.client.renderer.lod.LodRenderer.initialize();
    
    // Rest of Minecraft initialization...
}
```

**Phase 6: Remove Mixin Infrastructure**

Once all mixins are integrated:
1. Delete all mixin classes
2. Delete mixin JSON configurations
3. Remove SpongePowered Mixin dependency
4. Remove Fabric Loader dependency
5. Test extensively

**Phase 7: Documentation**

Create `INTEGRATION_CHANGES.md` documenting:
- Which Minecraft classes were modified
- What functionality was added
- Which original mod classes were moved where
- Rationale for each major change

### 5.5 Access Widener Application Strategy

**STATUS**: Access widener changes have already been applied directly to the Minecraft source code.

**What Was Done**:
- The 53 access widener entries from Distant Horizons have been manually applied
- Visibility modifiers changed from `private` to `public` or `protected` as needed
- Changes are permanent in the Minecraft source files

**Integration Impact**:
- No additional access widener processing needed during integration
- Distant Horizons code can directly access previously-private Minecraft members
- Access widener files (`.accesswidener`) can be removed from resources
- No runtime access widening required

**Future Maintenance**:
- When updating Minecraft versions, access changes must be reapplied
- Document all access-widened members for tracking
- Consider maintaining a list of modified access levels for version updates

### 5.6 Initialization Strategy

**Create Central Initialization Class**:

```java
package net.minecraft.integration;

public class ModIntegration {
    private static boolean initialized = false;
    
    public static void initializeMixins() {
        // Initialize SpongePowered Mixin engine
        // Register all mixin configurations from integrated mods
        // This replaces Fabric Loader's mixin initialization
    }
    
    public static void initializeClient() {
        if (initialized) return;
        
        // Pre-launch phase
        net.caffeinemc.mods.sodium.fabric.SodiumPreLaunch.onPreLaunch();
        
        // Main initialization phase
        net.caffeinemc.mods.sodium.fabric.SodiumFabricMod.onInitializeClient();
        com.seibel.distanthorizons.fabric.FabricMain.onInitializeClient();
        // Iris init (if has explicit entrypoint)
        
        initialized = true;
    }
    
    public static void initializeServer() {
        if (initialized) return;
        
        com.seibel.distanthorizons.fabric.FabricMain.onInitializeServer();
        
        initialized = true;
    }
}
```

**Call Points**:
- Mixins: Before Minecraft class loading begins (JVM startup)
- Client: `net.minecraft.client.Minecraft.<init>()` (early in constructor)
- Server: `net.minecraft.server.Main.main()` (before server start)


## 6. Phase-by-Phase Integration Plan

### Phase 0: Preparation and Baseline (Week 1)

**Objectives**:
- Document current functionality for regression testing
- Create testing framework
- Establish baseline performance metrics

**Tasks**:
1. ✓ Create `INTEGRATION.md` (this document)
2. Document all current features and test cases
3. Create automated test suite for existing functionality
4. Establish baseline performance benchmarks (FPS, memory, startup time)
5. Document current working state for comparison

**Success Criteria**:
- All documentation complete
- Baseline tests pass 100%
- Performance benchmarks recorded
- **Game runs and works properly with current multi-JAR architecture**

---

### Phase 1: Fabric Loader Integration (Week 2-3)

**Objective**: Remove Fabric Loader's mod loading infrastructure, as it will not be needed once mods are fully absorbed into Minecraft

**Sub-Phase 1.1: Analyze Fabric Loader Dependencies**
1. Identify which Fabric Loader components are essential:
   - Fabric API stubs (used by mods)
   - Any utility classes used by mods
2. Identify which components can be removed:
   - Knot launcher (mod loading system)
   - Mixin initialization system (will be removed entirely)
   - Mod discovery and JAR loading
3. Document findings

**Sub-Phase 1.2: Preserve Essential Fabric APIs**
1. Keep Fabric API stubs that mods use (already in main source at `net/fabricmc/fabric/api/`)
2. Ensure these APIs will still work when mods are absorbed into Minecraft
3. May need to adapt some APIs to work without Fabric Loader

**Sub-Phase 1.3: Remove Fabric Loader Infrastructure**
1. Remove Knot launcher entry point
2. Remove mod discovery code
3. Remove runtime mod loading system
4. Keep minimal Fabric API support if needed by integrated code

**Sub-Phase 1.4: Update Main Entry Point**
1. Ensure Minecraft starts directly (not via Fabric Loader)
2. Update build.gradle to use Minecraft's main class
3. Remove fabricLoader source set from build
4. Test: Minecraft launches without Fabric Loader

**Sub-Phase 1.5: Testing and Validation**
1. Build and verify compilation succeeds
2. Test: Minecraft starts without Fabric Loader launcher
3. Test: Game launches to main menu
4. Verify: No Fabric Loader initialization occurs
5. **CRITICAL: Game must run and work properly (vanilla functionality)**

**Success Criteria**:
- Fabric Loader's mod loading removed
- Game starts directly without Knot launcher
- Essential Fabric APIs preserved for mod integration
- Minecraft runs normally in vanilla mode
- **Game reaches main menu and can load a world**

**Note**: At this phase, no mods are integrated yet - this just removes the mod loading infrastructure in preparation for direct code absorption.

---

### Phase 2: Sodium Integration (Week 4-8)

**Objective**: Integrate Sodium rendering optimizations by moving code into Minecraft's package structure and applying mixin transformations directly

**Sub-Phase 2.1: Relocate Sodium Source Code**
1. Move Sodium source files from `modules/sodium/.../net/caffeinemc/mods/sodium/` to `src/main/java/net/minecraft/client/renderer/sodium/`
2. Update package declarations in all moved files
3. Update imports throughout moved code
4. Copy Sodium resources (shaders, icons, configuration files) to main resources
5. Test compilation

**Sub-Phase 2.2: Analyze Sodium Mixins**
1. Catalog all Sodium mixins (106+ from sodium-common.mixins.json and sodium-fabric.mixins.json)
2. For each mixin, document:
   - Target Minecraft class and method
   - Injection type (@Inject, @Overwrite, @Accessor, etc.)
   - What functionality it adds/modifies
   - Dependencies on Sodium classes
3. Prioritize mixins by importance (core rendering vs optional features)

**Sub-Phase 2.3: Apply Mixin Transformations to Minecraft Source**
1. For simple @Inject mixins: Add method calls at injection points
   ```java
   // In net/minecraft/client/renderer/LevelRenderer.java
   import net.minecraft.client.renderer.sodium.SodiumRenderer;
   
   public void renderLevel(...) {
       SodiumRenderer.beforeRenderLevel(this, ...);  // Added
       // Original code...
   }
   ```

2. For @Accessor mixins: Add public getters/setters
   ```java
   // In net/minecraft/client/renderer/LevelRenderer.java
   public ChunkRenderList getChunkRenderList() {  // Added
       return this.chunkRenderList;
   }
   ```

3. For @Overwrite mixins: Replace method bodies
   ```java
   // In net/minecraft/client/renderer/LevelRenderer.java
   public void setupRender(...) {
       // INTEGRATED: Using Sodium's implementation
       net.minecraft.client.renderer.sodium.SodiumChunkRenderer.setup(this, ...);
   }
   ```

4. For interface @Mixin: Make class implement interface
   ```java
   // In net/minecraft/client/renderer/LevelRenderer.java
   public class LevelRenderer implements AutoCloseable, SodiumWorldRenderer {
       // Implement SodiumWorldRenderer methods
   }
   ```

**Sub-Phase 2.4: Remove Mixin Infrastructure**
1. Delete Sodium mixin classes (no longer needed)
2. Delete mixin JSON configurations
3. Update build.gradle to remove Sodium source set
4. Test compilation

**Sub-Phase 2.5: Initialize Sodium**
1. Add Sodium initialization to Minecraft startup
   ```java
   // In net/minecraft/client/Minecraft.java
   public Minecraft(GameConfig gameConfig) {
       // INTEGRATED: Initialize Sodium
       net.minecraft.client.renderer.sodium.SodiumRenderer.initialize();
       // ...
   }
   ```
2. Ensure Sodium GUI integrates with video settings
3. Test initialization order

**Sub-Phase 2.6: Testing and Validation**
1. Build and verify compilation succeeds
2. Test: Sodium options appear in video settings
3. Test: Rendering optimizations are active
4. Functional tests: Chunk rendering, culling, memory efficiency
5. Performance tests: FPS benchmarks, memory usage, chunk loading speed
6. Visual tests: No rendering artifacts, correct colors, proper lighting
7. **CRITICAL: Game must run and work properly with no regressions**
8. Compare performance to baseline (should be equal or better)

**Success Criteria**:
- Sodium code fully integrated into `net.minecraft.client.renderer.sodium.*`
- All 106+ mixin transformations applied to Minecraft source
- No remaining Sodium mod packages
- Sodium features fully functional
- Rendering performance matches or exceeds modded version
- No rendering artifacts or crashes
- **Game runs normally with all Sodium optimizations active**

---

### Phase 3: Distant Horizons Integration (Week 7-9)

**Objective**: Integrate LOD rendering for extended view distances

**Sub-Phase 3.1: Verify Access Wideners**
1. Confirm that all 53 access widener modifications are already applied to Minecraft source
2. Verify Distant Horizons can access previously-private members
3. No additional access widener processing needed

**Sub-Phase 3.2: Source Integration**
1. Copy Distant Horizons sources to main source tree (`com/seibel/distanthorizons/`)
2. Copy resources (SQL scripts, configuration files, icons)
3. Copy DH mixin JSON configurations to resources
4. Resolve compilation errors
5. Test compilation

**Sub-Phase 3.3: Mixin Registration**
1. Register DH mixin configurations with mixin system
2. Verify mixin discovery and loading
3. Test: DH mixins are applied at runtime
4. Monitor for conflicts with existing mixins

**Sub-Phase 3.4: Initialize Distant Horizons**
1. Add DH initialization to `ModIntegration.initializeClient()`
2. Add DH server initialization to `ModIntegration.initializeServer()`
3. Test: DH initializes without errors

**Sub-Phase 3.5: Testing and Validation**
1. Build and verify compilation succeeds
2. Test: LOD chunks generate correctly
3. Test: LOD rendering displays properly
4. Test: Data persistence (SQLite database)
5. Test: Multi-threading stability
6. Performance tests: FPS with LODs, memory usage, generation speed
7. Functional tests: Extended view distances, LOD quality settings
8. **CRITICAL: Game must run and work properly with no regressions**
9. Test both single-player and server scenarios

**Success Criteria**:
- LOD chunks generate and render correctly
- Extended view distance works as expected
- Data saves and loads correctly between sessions
- Performance acceptable with LODs enabled
- No crashes or threading issues
- **Game runs normally with Distant Horizons active**

---

### Phase 4: Iris Integration (Week 10-13)

**Objective**: Integrate shader pack support with Sodium and DH compatibility

**Sub-Phase 4.1: Source Integration**
1. Copy Iris sources to main source tree (`net/irisshaders/iris/`)
2. Copy resources (shader templates, icons, configuration files)
3. Copy all 10 Iris mixin JSON configurations to resources
4. Resolve compilation errors
5. Test compilation

**Sub-Phase 4.2: Mixin Registration**
1. Register all Iris mixin configurations with mixin system
2. Pay special attention to Sodium compatibility mixins
3. Pay special attention to DH compatibility mixins
4. Verify mixin discovery and loading
5. Test: Iris mixins are applied at runtime
6. Monitor for conflicts (especially with Sodium mixins)

**Sub-Phase 4.3: Sodium Integration**
1. Verify Sodium API usage is compatible
2. Test Iris + Sodium interaction
3. Verify Sodium rendering pipeline modifications work correctly
4. Test: Sodium options still accessible with Iris active

**Sub-Phase 4.4: Distant Horizons Integration**
1. Enable DH compatibility layer
2. Test Iris + DH shader integration
3. Verify LOD rendering works with shaders

**Sub-Phase 4.5: Initialize Iris**
1. Add Iris initialization to `ModIntegration.initializeClient()`
2. Ensure proper initialization order (after Sodium, before/with DH)
3. Test: Iris initializes without errors

**Sub-Phase 4.6: Testing and Validation**
1. Build and verify compilation succeeds
2. Test: Shader packs can be loaded from shaderpacks folder
3. Test: Multiple different shader packs (BSL, Complementary, Sildurs, etc.)
4. Test: Sodium + Iris rendering together
5. Test: DH + Iris rendering together (LOD shaders)
6. Performance tests: FPS with shaders, memory usage, shader compilation time
7. Functional tests: All shader features (shadows, reflections, bloom, etc.)
8. Visual tests: Proper rendering, no artifacts, correct colors
9. **CRITICAL: Game must run and work properly with no regressions**
10. Test shader pack switching and reloading

**Success Criteria**:
- Shader packs load and render correctly
- No conflicts with Sodium optimizations
- DH shader integration works
- Performance acceptable with shaders enabled
- All shader features functional (shadows, bloom, etc.)
- Shader pack switching works smoothly
- **Game runs normally with all three mods (Sodium + Iris + DH) working together**

---

### Phase 5: Build System Simplification (Week 14)

**Objective**: Simplify build system to single source set and single JAR

**Tasks**:
1. Remove all custom source sets from build.gradle
2. Remove separate JAR tasks for mods
3. Simplify run tasks (no more mod loading from run/mods/)
4. Update classpath configuration
5. Clean up dependencies (ensure all mod dependencies included)
6. Update distribution tasks to produce single JAR
7. Test build process end-to-end

**Testing and Validation**:
1. Verify build completes successfully
2. Verify single JAR is produced with all integrated code
3. Test: JAR can be run standalone
4. Test: Game launches from the single JAR
5. Test: All features work from single JAR (no mods in run/mods/)
6. **CRITICAL: Game must run and work properly with no regressions**
7. Verify JAR size is reasonable (not bloated)

**Success Criteria**:
- Build produces single JAR containing all integrated code
- JAR is runnable without external mod files
- Run tasks work without mod loading
- Distribution tasks work correctly
- **Game runs normally from single JAR with all features intact**

---

### Phase 6: Resource Consolidation (Week 15)

**Objective**: Merge all mod resources into main resources

**Tasks**:
1. Merge mod assets (textures, icons, shaders)
2. Merge mod data files (configurations, SQL scripts)
3. Consolidate mixin JSON configurations
4. Remove mod-specific metadata files (fabric.mod.json if not needed)
5. Update resource references in code if needed
6. Resolve any resource path conflicts
7. Test resource loading

**Testing and Validation**:
1. Verify all resources are accessible
2. Test: Sodium GUI icons display correctly
3. Test: Iris shader files load properly
4. Test: DH configuration files are found
5. Test: No missing resource errors in logs
6. Visual tests: All UI elements render correctly
7. **CRITICAL: Game must run and work properly with no regressions**
8. Test resource loading from JAR (not from file system)

**Success Criteria**:
- All mod resources merged into main resources
- No missing assets or resources
- Resource loading works correctly from single JAR
- No resource path conflicts
- **Game runs normally with all resources loading correctly**

---

### Phase 7: Code Cleanup and Optimization (Week 16-17)

**Objective**: Clean up integration code and optimize

**Tasks**:
1. Evaluate if Fabric Loader dependency can be further reduced
2. Remove or archive module directories (keep as reference if needed)
3. Code organization and documentation
4. Add code comments explaining integration points
5. Performance optimization opportunities
6. Code quality improvements
7. Remove dead code or unused imports

**Testing and Validation**:
1. Verify compilation after cleanup
2. Run full regression test suite
3. Performance benchmarks: Compare to baseline and previous phases
4. Memory profiling: Ensure no memory leaks from integration
5. Startup time: Should be faster than multi-JAR version
6. Test all major features comprehensively
7. **CRITICAL: Game must run and work properly with no regressions**
8. Verify no functionality lost during cleanup

**Success Criteria**:
- Clean, well-organized codebase
- Module directories archived or removed
- Performance equal to or better than baseline
- Code quality high and maintainable
- Documentation updated
- **Game runs normally with optimizations applied**

---

### Phase 8: Final Testing and Documentation (Week 18)

**Objective**: Comprehensive testing and documentation

**Tasks**:
1. Functional testing (all features across all mods)
2. Performance testing (comprehensive benchmarks)
3. Compatibility testing (different platforms, configurations)
4. Documentation updates (README, build instructions, etc.)
5. Create integration report and lessons learned
6. Update maintenance documentation

**Comprehensive Testing Checklist**:
1. **Baseline Comparison**:
   - All baseline tests must pass
   - Performance must match or exceed baseline
   - No regressions from original multi-JAR system

2. **Feature Testing**:
   - Sodium: All rendering optimizations, GUI options
   - Iris: Shader pack loading, all shader features
   - Distant Horizons: LOD generation, rendering, persistence
   - Cross-mod: Sodium+Iris, Iris+DH, all three together

3. **Platform Testing**:
   - Windows (x64)
   - Linux (x64, ARM64)
   - macOS (Intel, Apple Silicon)

4. **Performance Testing**:
   - FPS benchmarks (vanilla areas, complex scenes)
   - Memory usage (normal, extended view distances)
   - Startup time (should be faster than multi-JAR)
   - Shader compilation time
   - LOD generation speed

5. **Stability Testing**:
   - Extended play sessions (no memory leaks)
   - World loading/unloading cycles
   - Shader pack switching
   - Settings changes
   - Multiplayer connections (if applicable)

6. **User Acceptance Testing**:
   - New player experience (first launch)
   - World creation and loading
   - Shader pack installation and usage
   - Extended view distance configuration
   - Low-end hardware testing
   - High-end hardware testing

**Documentation Updates**:
1. Update README.md with integration status
2. Document build process for single JAR
3. Create INTEGRATION_REPORT.md with:
   - What was integrated
   - How it was integrated
   - Known issues or limitations
   - Performance comparisons
   - Maintenance notes for future updates
4. Update development documentation

**Success Criteria**:
- **ALL tests pass without regressions**
- Performance meets or exceeds baseline
- All platforms tested and working
- Documentation complete and accurate
- Integration report provides clear guidance for maintenance
- **Game runs flawlessly with all integrated features**
- Ready for production release

---

## 7. Testing Strategy

### 7.1 Unit Testing
- Test each mixin application
- Test mod initialization
- Test API compatibility
- Test resource loading

### 7.2 Integration Testing
- Rendering pipeline tests
- Sodium feature tests
- Iris feature tests
- Distant Horizons feature tests
- Cross-mod interaction tests

### 7.3 Performance Testing
- FPS benchmarks
- Memory benchmarks
- Startup time
- Compare to modded version

### 7.4 Regression Testing
- All baseline tests must pass
- Automated test suite
- Manual visual testing

### 7.5 User Acceptance Testing
- New player experience
- World creation/loading
- Multiplayer testing
- Shader pack usage
- Extended view distances
- Low-end hardware testing

---

## 8. Rollback and Contingency Plans

### 8.1 Version Control Strategy

**Branch Management**:
- Work can be done on feature branches for each phase
- Main branch remains stable
- Integration work merged only after thorough testing

**Recommendations**:
- Commit frequently during each phase
- Tag successful phase completions for reference
- Keep detailed commit messages explaining changes

### 8.2 Contingency Plans

**If Mixin System Integration Fails**:
- Mixin system is essential - integration not viable without it
- The SpongePowered Mixin library must remain as a dependency
- If mixin initialization proves problematic, may need to keep minimal Fabric Loader infrastructure
- Fallback: Keep Fabric Loader's mixin initialization but remove mod loading

**If Performance Degrades**:
- Profile to identify bottleneck
- Optimize initialization order
- Optimize mixin registration
- Investigate threading issues
- Compare mixin application efficiency

**If Dependency Conflicts Occur**:
- Use shading to isolate conflicting libraries
- Fork and modify dependencies if necessary
- Document all modifications for maintenance
- Consider alternative versions of conflicting libraries

**If Cross-Mod Compatibility Issues Arise**:
- Review initialization order
- Check for mixin conflicts (multiple mixins targeting same class)
- Verify API compatibility between mods
- May need to adjust mixin priority or ordering

**If Integration Proves Infeasible**:
- Document why integration is not viable
- Fallback to current multi-JAR system
- Optimize current build process instead
- Consider partial integration (some mods only)

**Testing Failure Response**:
- Do not proceed to next phase if testing fails
- Identify root cause of failures
- Fix issues before continuing
- Re-run all tests after fixes
- Document what went wrong and how it was resolved

---

## 9. Long-term Maintenance

### 9.1 Minecraft Version Updates

**Process**:
1. Update Minecraft source
2. Review all applied mixins
3. Reapply mixins if needed
4. Review access widener changes
5. Test all features
6. Update version

**Mixin Update Tracking**:
- Maintain list of all applied mixins
- Document target class/method
- Document modification type
- Document integration location
- Document test procedure

### 9.2 Mod Feature Updates

**Process**:
1. Review new features in mod repository
2. Determine if features should be integrated
3. Apply new mixins
4. Test new features

### 9.3 Documentation Maintenance

**Required Documentation**:
1. **INTEGRATION.md** - Integration plan and history
2. **APPLIED_MIXINS.md** - List of applied mixins
3. **ACCESS_CHANGES.md** - List of access modifications
4. **INTEGRATION_CHANGELOG.md** - History of changes
5. **MAINTENANCE_GUIDE.md** - Maintenance guide

### 9.4 Testing Maintenance

**Continuous Testing**:
- Maintain automated test suite
- Add tests for new features
- Update tests for Minecraft changes
- Regular performance benchmarking

---

## 10. Quick Reference Summary

### Current State
- **4 Mods**: Fabric Loader, Sodium, Iris, Distant Horizons
- **Total Files**: 1,996 Java files in modules
- **Mixins**: 256+ mixin classes (will be preserved, not inlined)
- **Access Wideners**: 53 modifications (already applied to Minecraft source)
- **Current JAR Output**: 5 separate JARs

### Target State
- **Single JAR**: All code in one JAR
- **Preserved Mixin System**: SpongePowered Mixin library for runtime transformations
- **No Runtime Mod Loading**: Direct integration via ModIntegration class
- **Simplified Build**: Single source set
- **All Features Intact**: 100% functionality preserved

### Integration Complexity
- **Estimated Timeline**: 18 weeks (optimistic) to 30+ weeks (realistic)
- **Major Challenges**:
  1. Mixin system integration (preserving runtime transformation)
  2. Proper initialization order for all mods
  3. Complex inter-mod dependencies
  4. Maintaining performance parity
  5. Testing after each phase to ensure no regressions
- **Major Benefits**:
  1. Single JAR simplicity
  2. Faster startup (no mod discovery/loading)
  3. Better performance potential
  4. Simplified distribution

### Key Decision Points
- **Integration Approach**: Modified Static Integration with Runtime Mixins (recommended)
- **Mixin Strategy**: Preserve mixin system, do NOT inline into Minecraft source
- **Access Wideners**: Already applied to Minecraft source, no additional processing needed
- **Testing**: Comprehensive validation after each phase, game must run and work properly
- **Phase 0**: Skip backup/branching instructions, focus on baseline testing

---

## Appendix A: Mixin Statistics

### Sodium Mixins (106+)
- Location: `sodium-common.mixins.json`, `sodium-fabric.mixins.json`
- Categories:
  - Core rendering: 30+ mixins
  - Chunk optimizations: 20+ mixins
  - Entity rendering: 15+ mixins
  - Texture system: 25+ mixins
  - Features: 16+ mixins

### Iris Mixins (150+)
- Location: 10 configuration files
- Categories:
  - Shader pipeline: 40+ mixins
  - Sodium compatibility: 30+ mixins
  - DH compatibility: 10+ mixins
  - Fixes and workarounds: 20+ mixins
  - Vertex formats: 15+ mixins
  - Other: 35+ mixins

### Distant Horizons Mixins
- Location: `DistantHorizons.fabric.mixins.json`
- Categories:
  - LOD rendering: Multiple mixins
  - Chunk loading: Multiple mixins
  - World generation: Multiple mixins

---

## Appendix B: Access Widener Details

**Total Modifications**: 53 from Distant Horizons

**Categories**:
1. **File System Access** (3 entries)
   - Dimension data storage
   - File paths

2. **Rendering Access** (15 entries)
   - Level renderer fields/methods
   - Camera and FOV access
   - Visible sections access

3. **World Generation Access** (25 entries)
   - Chunk loading state
   - Lighting system
   - Entity management
   - Chunk map access
   - Distance manager

4. **Chunk Storage Access** (10 entries)
   - IO workers
   - Region file storage
   - Cache access

---

## Appendix C: Dependency Libraries

**Required After Integration**:

From Minecraft (existing):
- LWJGL 3.3.3
- Netty, Gson, Guava
- All vanilla dependencies

From Fabric (may remove):
- ASM 9.9
- SpongePowered Mixin (if can eliminate)

From Iris:
- JCPP 1.4.14
- ANTLR 4.13.1
- GLSL Transformer 3.0.0-pre3

From Distant Horizons:
- SQLite JDBC 3.47.2.0
- Zstd-JNI, LZ4, XZ (compression)
- NightConfig TOML/JSON 3.6.6
- LWJGL JAWT 3.3.3

---

## Appendix D: Integration Checklist

### Pre-Integration
- [ ] Document current features and test cases
- [ ] Create baseline test suite
- [ ] Record baseline performance metrics
- [ ] Verify current build works correctly
- [ ] Understand mixin system architecture
- [ ] Verify access wideners already applied

### Phase 1: Fabric Loader
- [ ] Copy Fabric Loader sources to main source tree
- [ ] Remove fabricLoader source set from build.gradle
- [ ] Preserve SpongePowered Mixin library dependency
- [ ] Create ModIntegration class with initializeMixins()
- [ ] Remove Knot launcher and mod loading code
- [ ] Test: Mixin system initializes correctly
- [ ] Test: Minecraft starts without Fabric Loader launcher
- [ ] **CRITICAL: Game runs and works properly**
- [ ] Tag/commit phase completion

### Phase 2: Sodium
- [ ] Copy Sodium sources to `net/caffeinemc/mods/sodium/`
- [ ] Copy Sodium resources and mixin configurations
- [ ] Register Sodium mixin JSONs with mixin system
- [ ] Initialize Sodium via ModIntegration
- [ ] Test: Sodium mixins apply correctly
- [ ] Test: Sodium GUI appears in video settings
- [ ] Test: Rendering optimizations active
- [ ] Performance tests pass
- [ ] **CRITICAL: Game runs and works properly with Sodium**
- [ ] Tag/commit phase completion

### Phase 3: Distant Horizons
- [ ] Verify 53 access wideners already applied
- [ ] Copy DH sources to `com/seibel/distanthorizons/`
- [ ] Copy DH resources and mixin configurations
- [ ] Register DH mixin JSONs with mixin system
- [ ] Initialize DH via ModIntegration
- [ ] Test: DH mixins apply correctly
- [ ] Test: LOD rendering works
- [ ] Test: Data persistence (SQLite)
- [ ] Performance tests pass
- [ ] **CRITICAL: Game runs and works properly with DH**
- [ ] Tag/commit phase completion

### Phase 4: Iris
- [ ] Copy Iris sources to `net/irisshaders/iris/`
- [ ] Copy Iris resources and all 10 mixin configurations
- [ ] Register Iris mixin JSONs with mixin system
- [ ] Initialize Iris via ModIntegration
- [ ] Test: Iris mixins apply correctly
- [ ] Test: Shader packs load correctly
- [ ] Test: Sodium + Iris compatibility
- [ ] Test: DH + Iris compatibility
- [ ] Performance tests with shaders pass
- [ ] **CRITICAL: Game runs and works properly with all mods**
- [ ] Tag/commit phase completion

### Phase 5-8: Finalization
- [ ] Simplify build system to single source set
- [ ] Consolidate all resources
- [ ] Remove module directories
- [ ] Code cleanup and optimization
- [ ] Comprehensive testing (all features)
- [ ] Platform compatibility testing
- [ ] Performance benchmarking
- [ ] Documentation updates
- [ ] Integration report complete
- [ ] **CRITICAL: Final build runs flawlessly**
- [ ] Tag integration complete

---

## Conclusion

This comprehensive plan provides a detailed roadmap for integrating all mods into the main Minecraft JAR. The integration preserves the mixin system for runtime bytecode transformation while achieving a single JAR output.

**Revised Integration Complexity**:
- **Estimated Timeline**: 18-30 weeks
- **Key Approach Changes**:
  1. Mixins are NOT inlined - they remain as separate classes to avoid circular dependencies
  2. SpongePowered Mixin library is preserved for runtime transformations
  3. Access wideners are already applied to Minecraft source - no additional work needed
  4. Testing is mandatory after each phase before proceeding

**Critical Success Factors**:
1. **Preserve Mixin Architecture**: Keep mixin system for bytecode transformation
2. **Avoid Circular Dependencies**: Keep mod code in mod packages, not in Minecraft
3. **Incremental Testing**: Game must run and work properly after each phase
4. **Comprehensive Documentation**: Document all integration points for maintenance
5. **Performance Monitoring**: Ensure no performance regressions throughout

**Key Technical Decisions**:
1. **Mixin System**: Preserved, not inlined (avoids circular dependency issues)
2. **Access Wideners**: Already applied to Minecraft source code
3. **Build System**: Simplified to single source set producing single JAR
4. **Testing**: Mandatory validation after each integration phase
5. **Initialization**: Central ModIntegration class coordinates mod startup

**Next Steps**:
1. Review this revised plan
2. Begin Phase 0 (baseline testing and documentation)
3. Proceed incrementally through phases, testing thoroughly after each
4. Do not proceed to next phase if testing reveals regressions
5. Document all issues and resolutions for future reference

**Important Notes**:
- This is a complex integration requiring careful execution
- Testing is not optional - it's critical to success
- Circular dependency avoidance is fundamental to the design
- Performance must match or exceed current multi-JAR system
- All 256+ mixins must work correctly for full functionality

---

**Document Version**: 2.0  
**Created**: 2025-12-24  
**Revised**: 2025-12-24  
**Status**: Updated - Ready for Implementation with Corrected Approach

---

**END OF DOCUMENT**
