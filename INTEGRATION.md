# MattMC Mod Integration Plan

## Executive Summary

This document provides an **EXTREMELY COMPREHENSIVE** plan for integrating the mods currently compiled separately (Fabric Loader, Sodium, Iris, and Distant Horizons) directly into the main Minecraft 1.21.10 game JAR. The goal is to simplify the build system and have all features in a single JAR while maintaining all functionality.

**Current Architecture**: Mods are compiled into separate JARs and loaded at runtime via Fabric Loader  
**Target Architecture**: All mod code integrated directly into the main game JAR with initialization at game startup

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

**Integration Challenges**:
1. **Static Mixins**: Can potentially be applied at compile-time or via manual source code integration
2. **Dynamic Mixins**: Some mixins use conditions or plugins (e.g., `SodiumMixinPlugin`, `IrisMixinPlugin`)
3. **Mixin Conflicts**: Multiple mods may mixin to the same class (need careful ordering)
4. **Interface Injections**: Some mixins inject interfaces into Minecraft classes

**Example Mixin Configuration** (Sodium):
```json
{
  "package": "net.caffeinemc.mods.sodium.mixin",
  "plugin": "net.caffeinemc.mods.sodium.mixin.SodiumMixinPlugin",
  "mixins": [
    "core.MinecraftMixin",
    "core.render.world.LevelRendererMixin",
    "features.render.entity.CubeMixin"
    // ... 100+ more
  ]
}
```

### 4.2 Access Widener Challenges

**Challenge**: Distant Horizons requires 53 access modifications to Minecraft classes

**Current System**:
- Access wideners defined in `.accesswidener` files
- Fabric Loader widens access at class load time
- Changes private/protected members to public/accessible

**Integration Challenges**:
1. **Manual Access Widening**: Need to modify Minecraft source directly (change `private` → `public`)
2. **Maintenance Burden**: Changes must be tracked and reapplied on Minecraft updates
3. **Potential Conflicts**: Multiple mods may widen same members

**Example Access Widener**:
```
accessible field net/minecraft/world/level/storage/DimensionDataStorage dataFolder Ljava/nio/file/Path;
accessible method net/minecraft/client/renderer/GameRenderer getFov (Lnet/minecraft/client/Camera;FZ)F
```

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

**Option A: Static Integration (RECOMMENDED)**
- Copy mod source into main source tree
- Manually apply mixins as source code changes
- Create initialization hooks in Minecraft startup
- Remove Fabric Loader runtime dependency

**Option B: Hybrid Integration**
- Keep Fabric Loader for mixin system
- Integrate mod sources into main JAR
- Use embedded Fabric Loader at runtime
- Simpler but keeps Fabric dependency

**Option C: Pure Compilation**
- Integrate sources into single source set
- Keep separate JAR tasks
- Bundle all JARs into single distribution
- Minimal change but doesn't meet goal of single JAR

**Recommendation**: **Option A (Static Integration)** for maximum simplification and single JAR goal

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

### 5.4 Mixin Application Strategy

**Three-Phase Approach**:

**Phase 1: Mixin Analysis**
- Parse all mixin JSON configurations
- Identify all target classes and methods
- Map all @Inject, @Redirect, @ModifyVariable, @Overwrite annotations
- Document all interface injections

**Phase 2: Manual Application**
- For each mixin, manually apply the transformation to Minecraft source
- Create backup of original methods (commented out or in separate files)
- Add integration points for mod functionality
- Test each mixin application individually

**Phase 3: Verification**
- Run diff between original and modified Minecraft classes
- Document all changes for future Minecraft version updates
- Create automated tests for mixin functionality

**Example Mixin Transformation**:

Original Mixin (Sodium):
```java
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevel(CallbackInfo ci) {
        SodiumClientMod.onRenderStart();
    }
}
```

Integrated Code:
```java
// In net/minecraft/client/renderer/LevelRenderer.java
public void renderLevel(...) {
    // SODIUM INTEGRATION: Call Sodium render start
    net.caffeinemc.mods.sodium.client.SodiumClientMod.onRenderStart();
    
    // Original method body continues...
}
```

### 5.5 Access Widener Application Strategy

**Automated Approach**:
1. Parse all `.accesswidener` files
2. Generate list of Minecraft classes to modify
3. Use AST manipulation (JavaParser or similar) to change visibility modifiers
4. Document all modifications for version updates

**Manual Approach** (if automation fails):
1. Systematically go through each access widener entry
2. Locate the field/method/class in Minecraft source
3. Change `private` → `protected` or `public` as specified
4. Mark with comment: `// ACCESS WIDENED for DistantHorizons`

### 5.6 Initialization Strategy

**Create Central Initialization Class**:

```java
package net.minecraft.integration;

public class ModIntegration {
    private static boolean initialized = false;
    
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
- Client: `net.minecraft.client.Minecraft.<init>()` (early in constructor)
- Server: `net.minecraft.server.Main.main()` (before server start)


## 6. Phase-by-Phase Integration Plan

### Phase 0: Preparation and Baseline (Week 1)

**Objectives**:
- Create comprehensive backup of current working state
- Document current functionality for regression testing
- Set up integration branch
- Create testing framework

**Tasks**:
1. ✓ Create `INTEGRATION.md` (this document)
2. Create integration branch: `git checkout -b integration/all-mods`
3. Tag current state: `git tag baseline-before-integration`
4. Document all current features and test cases
5. Create automated test suite for existing functionality
6. Set up build comparison tools (JAR diff, class comparison)

**Success Criteria**:
- All documentation complete
- Baseline tests pass 100%
- Build produces identical JARs to current state

---

### Phase 1: Fabric Loader Integration (Week 2-3)

**Objective**: Integrate Fabric Loader source and remove runtime mod loading

**Sub-Phase 1.1: Source Integration**
1. Copy Fabric Loader sources to main source tree
2. Update build.gradle: Remove fabricLoader source set
3. Resolve any compilation errors
4. Test compilation

**Sub-Phase 1.2: Remove Knot Launcher**
1. Identify Knot launcher entry point code
2. Extract essential initialization logic
3. Create `ModIntegration.initializeClient()` in Minecraft client startup
4. Remove mod discovery and JAR loading code
5. Test: Minecraft should start without Fabric Loader launcher

**Sub-Phase 1.3: Preserve Essential Fabric APIs**
1. Audit which Fabric Loader APIs are used by mods
2. Create stub implementations or direct replacements
3. Test: Mods can compile against new API stubs

**Success Criteria**:
- Minecraft compiles with Fabric Loader source integrated
- Game starts without Knot launcher
- No runtime mod loading occurs

---

### Phase 2: Sodium Integration (Week 4-6)

**Objective**: Integrate Sodium rendering optimizations directly into Minecraft

**Sub-Phase 2.1: Source Integration**
1. Copy Sodium sources to main source tree
2. Copy Sodium resources
3. Resolve compilation errors
4. Test compilation

**Sub-Phase 2.2: Mixin Analysis and Application**
1. Parse `sodium-common.mixins.json` (106+ mixins)
2. Categorize mixins by criticality
3. Create mixin application order
4. Apply mixins systematically
5. Test after each major mixin category

**Sub-Phase 2.3: Initialize Sodium**
1. Identify Sodium initialization code
2. Call from `ModIntegration.initializeClient()`
3. Test: Sodium GUI should appear in video settings

**Sub-Phase 2.4: Testing**
1. Functional tests: Rendering, GUI, options
2. Performance tests: FPS, memory, chunk loading

**Success Criteria**:
- Sodium features fully functional
- Rendering performance matches modded version
- No rendering artifacts or crashes

---

### Phase 3: Distant Horizons Integration (Week 7-9)

**Objective**: Integrate LOD rendering for extended view distances

**Sub-Phase 3.1: Access Widener Application**
1. Parse `1_21_10.distanthorizons.accesswidener` (53 entries)
2. Apply access changes (automated or manual)
3. Test compilation

**Sub-Phase 3.2: Source Integration**
1. Copy Distant Horizons sources
2. Copy resources (SQL scripts, configuration)
3. Resolve compilation errors
4. Test compilation

**Sub-Phase 3.3: Mixin Application**
1. Parse DH mixin configurations
2. Apply mixins to Minecraft classes
3. Test mixin integration

**Sub-Phase 3.4: Initialize Distant Horizons**
1. Add DH initialization to ModIntegration
2. Test: LOD rendering should activate

**Sub-Phase 3.5: Testing**
1. LOD generation, rendering, persistence
2. Multi-threading stability
3. Performance

**Success Criteria**:
- LOD chunks generate and render
- Extended view distance works
- Data saves and loads correctly
- Performance acceptable

---

### Phase 4: Iris Integration (Week 10-13)

**Objective**: Integrate shader pack support with Sodium and DH compatibility

**Sub-Phase 4.1: Source Integration**
1. Copy Iris sources
2. Copy resources
3. Resolve compilation errors
4. Test compilation

**Sub-Phase 4.2: Mixin Application**
1. Parse 10 Iris mixin configurations (150+ mixins)
2. Categorize mixins
3. Apply mixins carefully (may conflict with Sodium)
4. Test each mixin category

**Sub-Phase 4.3: Sodium Integration**
1. Verify Sodium API usage
2. Test Iris + Sodium together

**Sub-Phase 4.4: Distant Horizons Integration**
1. Enable DH compatibility layer
2. Test Iris + DH together

**Sub-Phase 4.5: Initialize Iris**
1. Add Iris initialization to ModIntegration
2. Test: Shader packs should be loadable

**Sub-Phase 4.6: Testing**
1. Shader pack loading
2. Various shader packs
3. Sodium + Iris rendering
4. DH + Iris rendering
5. Performance with shaders

**Success Criteria**:
- Shader packs load and render correctly
- No conflicts with Sodium
- DH shaders work
- Performance acceptable
- All shader features functional

---

### Phase 5: Build System Simplification (Week 14)

**Objective**: Simplify build system to single source set and single JAR

**Tasks**:
1. Remove all custom source sets
2. Remove separate JAR tasks
3. Simplify run tasks
4. Update classpath
5. Clean up dependencies
6. Update distribution tasks

**Success Criteria**:
- Build produces single JAR
- JAR contains all integrated code
- Run tasks work without mod loading
- Distributions work correctly

---

### Phase 6: Resource Consolidation (Week 15)

**Objective**: Merge all mod resources into main resources

**Tasks**:
1. Merge mod assets
2. Merge mod data files
3. Remove mod metadata
4. Update resource references
5. Test resource loading

**Success Criteria**:
- All mod resources accessible
- No missing assets
- Resource loading works correctly

---

### Phase 7: Code Cleanup and Optimization (Week 16-17)

**Objective**: Clean up integration code and optimize

**Tasks**:
1. Remove Fabric Loader dependency (if possible)
2. Remove module directories
3. Code organization and documentation
4. Performance optimization
5. Code quality improvements

**Success Criteria**:
- Clean codebase
- No module directories
- Performance equal or better
- Code quality high

---

### Phase 8: Final Testing and Documentation (Week 18)

**Objective**: Comprehensive testing and documentation

**Tasks**:
1. Functional testing (all features)
2. Performance testing (benchmarks)
3. Compatibility testing (platforms, configurations)
4. Documentation updates
5. Create integration report

**Success Criteria**:
- All tests pass
- Performance acceptable
- Documentation complete
- Ready for release

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

### 8.1 Git Strategy

**Branch Structure**:
```
main
  ↓
integration/all-mods
  ↓
  ├─ integration/phase-1-fabric-loader
  ├─ integration/phase-2-sodium
  ├─ integration/phase-3-distant-horizons
  └─ integration/phase-4-iris
```

**Tags**:
- `baseline-before-integration`
- `phase-1-complete`
- `phase-2-complete`
- `phase-3-complete`
- `phase-4-complete`
- `integration-complete`

### 8.2 Contingency Plans

**If Mixin Integration Fails**:
- Keep Fabric Loader's mixin system at runtime
- Integrate sources but use runtime mixins
- Still achieves single JAR (embed mixin library)

**If Performance Degrades**:
- Profile to identify bottleneck
- Optimize initialization
- Optimize threading

**If Dependency Conflicts Occur**:
- Use shading to isolate libraries
- Fork/modify dependencies
- Document modifications

**If Integration Proves Infeasible**:
- Fallback to current multi-JAR system
- Optimize build process
- Document why integration not viable

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
- **Mixins**: 256+ mixin classes
- **Access Wideners**: 53 modifications
- **Current JAR Output**: 5 separate JARs

### Target State
- **Single JAR**: All code in one JAR
- **No Runtime Mod Loading**: Direct integration
- **Simplified Build**: Single source set
- **All Features Intact**: 100% functionality preserved

### Integration Complexity
- **Estimated Timeline**: 18 weeks (optimistic) to 30+ weeks (realistic)
- **Major Challenges**:
  1. 256+ mixins to manually apply
  2. 53 access widener modifications
  3. Complex inter-mod dependencies
  4. Maintaining performance parity
- **Major Benefits**:
  1. Single JAR simplicity
  2. Faster startup
  3. Better performance potential
  4. Simplified distribution

### Key Decision Points
- **Integration Approach**: Static integration (Option A) recommended
- **Mixin Strategy**: Manual application with documentation
- **Access Wideners**: Automated parsing + manual application
- **Testing**: Comprehensive at each phase
- **Rollback**: Git branches and tags for each phase

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
- [ ] Backup current codebase
- [ ] Tag baseline
- [ ] Create integration branch
- [ ] Document features
- [ ] Create test suite
- [ ] Verify build works

### Phase 1: Fabric Loader
- [ ] Copy sources
- [ ] Remove source set
- [ ] Create ModIntegration class
- [ ] Remove Knot launcher
- [ ] Test startup
- [ ] Tag phase complete

### Phase 2: Sodium
- [ ] Copy sources/resources
- [ ] Apply 106+ mixins
- [ ] Initialize Sodium
- [ ] Test rendering
- [ ] Test GUI
- [ ] Tag phase complete

### Phase 3: Distant Horizons
- [ ] Apply 53 access wideners
- [ ] Copy sources/resources
- [ ] Apply mixins
- [ ] Initialize DH
- [ ] Test LOD rendering
- [ ] Tag phase complete

### Phase 4: Iris
- [ ] Copy sources/resources
- [ ] Apply 150+ mixins
- [ ] Verify Sodium integration
- [ ] Test shader loading
- [ ] Test DH integration
- [ ] Tag phase complete

### Phase 5-8: Finalization
- [ ] Simplify build system
- [ ] Consolidate resources
- [ ] Code cleanup
- [ ] Final testing
- [ ] Update documentation
- [ ] Tag integration complete

---

## Conclusion

This comprehensive plan provides a detailed roadmap for integrating all mods into the main Minecraft JAR. The integration is complex with 256+ mixins, 53 access widener modifications, and complex dependencies, but is achievable through careful incremental execution over 18-30 weeks.

**Key Success Factors**:
1. Incremental approach with testing at each phase
2. Comprehensive documentation of all changes
3. Git branches/tags for rollback capability
4. Performance monitoring throughout
5. Thorough testing after each phase

**Next Steps**:
1. Review this plan
2. Begin Phase 0 (preparation and baseline)
3. Proceed incrementally through phases
4. Document everything for maintenance

---

**Document Version**: 1.0  
**Created**: 2025-12-24  
**Status**: Complete - Ready for Implementation

---

**END OF DOCUMENT**
