# Distant Horizons Migration Plan

## ⚠️ CRITICAL FINDING - BUILD CURRENTLY BROKEN ⚠️

**IMMEDIATE ISSUE IDENTIFIED**: The existing integrated Distant Horizons code in `modules/distant-horizons-2.3.4b/` contains **unprocessed preprocessing directives** (`#if MC_VER`, `#else`, `#endif`). The build system is currently **BROKEN** with ~300 compilation errors.

### Root Cause
- The source code has Manifold preprocessing directives that aren't being processed
- Java compiler encounters raw `#if` directives and fails
- This affects ~123 files across the codebase

### Immediate Options
1. **Option A (Recommended)**: Configure Manifold preprocessor in MattMC's build system
2. **Option B**: Find preprocessed version of DH 2.3.4b for MC 1.21.10  
3. **Option C**: Manually resolve all preprocessing directives (time-consuming)

### Decision
We will implement **Option A** - add Manifold preprocessor support to enable proper compilation.

---

## Executive Summary

This document outlines the strategy for updating the integrated Distant Horizons mod in MattMC from version **2.3.4-b** (Minecraft 1.21.10) to version **2.4.3-b-dev** (Minecraft 1.21.11).

### Key Facts

| Aspect | Old Version (modules/) | New Version (frnsrc/) |
|--------|------------------------|----------------------|
| **Version** | 2.3.4-b | 2.4.3-b-dev |
| **MC Version** | 1.21.10 | 1.21.11 |
| **Java Files** | 676 files | 712 files (+36 files) |
| **Core Files** | 347 files | 366 files (+19 files) |
| **API Version** | 4.0.0 | 5.0.0 (breaking changes) |
| **LWJGL Version** | 3.3.1 | 3.3.3 |
| **Manifold Version** | 2025.1.20 | 2025.1.27 |

### Major New Features in 2.4.3-b-dev

1. **Version Preprocessing** - Uses `#if MC_VER` directives for multi-version support
2. **New C2ME Accessor** - Concurrent chunk generation mod compatibility
3. **New Compression** - Added zstd compression support (v1.5.7-6)
4. **Updated Dependencies** - Various library updates
5. **API Breaking Changes** - API 5.0.0 introduces breaking changes

---

## Stage 1: Recon & Mapping

### 1.1 Directory Structure Comparison

Both versions follow the same multi-loader architecture:

```
distant-horizons/
├── coreSubProjects/
│   ├── api/          - Public API for third-party mods
│   └── core/         - Platform-independent core logic
├── common/           - Multi-loader shared code
├── fabric/           - Fabric loader integration
├── forge/            - Forge loader integration (not used in MattMC)
└── neoforge/         - NeoForge loader integration (not used in MattMC)
```

**MattMC Integration**: Only uses `coreSubProjects/`, `common/`, and `fabric/` modules.

### 1.2 Core Subsystems Mapping

All major subsystems exist in both versions with identical package structures:

| Subsystem | Package | Purpose |
|-----------|---------|---------|
| **API** | `core.api` | Public API for external mods |
| **Configuration** | `core.config` | Settings and config management |
| **Data Objects** | `core.dataObjects` | LOD data structures |
| **Dependency Injection** | `core.dependencyInjection` | IoC container |
| **File Management** | `core.file` | LOD file I/O and storage |
| **Generation** | `core.generation` | LOD chunk generation |
| **Level Management** | `core.level` | World/dimension handling |
| **Logging** | `core.logging` | Logging infrastructure |
| **Multiplayer** | `core.multiplayer` | Server sync |
| **Networking** | `core.network` | Network protocol |
| **Rendering** | `core.render` | OpenGL rendering pipeline |
| **SQL Storage** | `core.sql` | Database operations |
| **Utilities** | `core.util` | Helper classes |
| **World Management** | `core.world` | World state tracking |
| **Wrapper Interfaces** | `core.wrapperInterfaces` | MC version abstraction |

**Status**: ✅ All subsystems present in both versions with same structure.

### 1.3 Fabric Integration Layer

#### Entry Points

| File | Old Version | New Version | Changes |
|------|-------------|-------------|---------|
| `FabricMain.java` | ✅ Present | ✅ Present | **Uses preprocessing directives for MC versions** |
| `FabricClientProxy.java` | ✅ Present | ✅ Present | **Updated for new APIs** |
| `FabricServerProxy.java` | ✅ Present | ✅ Present | **Updated for new APIs** |
| `FabricPluginPacketSender.java` | ✅ Present | ✅ Present | Minor updates |

#### Mod Compatibility Accessors

| Mod Accessor | Old | New | Notes |
|--------------|-----|-----|-------|
| `SodiumAccessor.java` | ✅ | ✅ | Updated for latest Sodium |
| `IrisAccessor.java` | ✅ | ✅ | Updated for latest Iris |
| `StarlightAccessor.java` | ✅ | ✅ | Starlight lighting engine |
| `BCLibAccessor.java` | ✅ | ✅ | Better Cave Library |
| `OptifineAccessor.java` | ✅ | ✅ | OptiFine (not used in MattMC) |
| `C2meAccessor.java` | ❌ | ✅ | **NEW** - Concurrent chunk engine |
| `ModChecker.java` | ✅ | ✅ | Detects installed mods |

**Critical**: `SodiumAccessor` and `IrisAccessor` must be updated carefully as MattMC has deeply integrated Sodium and Iris.

#### Mixins

Both versions use mixins for:
- **Client**: Debug screen, options screen, Minecraft client hooks
- **Server**: Chunk generation, entity tracking, level management
- **Events**: Block updates, server level events

**Status**: Mixin targets may need updates for MC 1.21.11 API changes.

### 1.4 MattMC-Specific Integration Points

Based on `build.gradle` analysis, MattMC integration includes:

1. **Source Set Configuration**
   - Separate `distantHorizons` source set
   - Compiles to standalone mod JAR: `distanthorizons-2.3.4-b-mc1.21.10.jar`
   - Depends on: Fabric Loader, Minecraft, Sodium, Iris

2. **Build System Integration**
   ```gradle
   sourceSets.distantHorizons.compileClasspath += sourceSets.fabricLoader.output
   sourceSets.distantHorizons.compileClasspath += sourceSets.main.output
   sourceSets.distantHorizons.compileClasspath += sourceSets.sodium.output
   sourceSets.distantHorizons.compileClasspath += sourceSets.iris.output
   ```

3. **Runtime Integration**
   - Loaded via `fabric.addMods` system property
   - Resources loaded through Fabric resource loader
   - Integrates with Sodium's rendering pipeline
   - Integrates with Iris shader system

4. **Dependencies Added by DH**
   - Night Config (TOML): `3.6.7`
   - JSON Simple: `1.1.1`
   - XZ compression: `1.9`
   - SQLite JDBC: `3.47.2.0`

**No Custom Code Modifications Found**: Initial scan shows no MattMC-specific markers in the Distant Horizons code itself. Integration is purely through build system and runtime loading.

---

## Stage 2: Diff Analysis

### 2.1 Critical Changes for MC 1.21.11

#### Minecraft API Changes

The new version uses preprocessing to handle multiple MC versions:

```java
// Old (2.3.4-b)
private static final ResourceLocation INITIAL_PHASE = 
    ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);

// New (2.4.3-b-dev)
#if MC_VER <= MC_1_20_6
private static final ResourceLocation INITIAL_PHASE = 
    new ResourceLocation(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
#elif MC_VER <= MC_1_21_10
private static final ResourceLocation INITIAL_PHASE = 
    ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
#else
private static final Identifier INITIAL_PHASE = 
    Identifier.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
#endif
```

**Impact**: MattMC is on MC 1.21.10, so we need the `MC_1_21_10` branch, but preprocessing may need configuration.

#### Logger Changes

```java
// Old
private static final Logger LOGGER = DhLoggerBuilder.getLogger();

// New
private static final DhLogger LOGGER = new DhLoggerBuilder().build();
```

**Impact**: Logger API changed from Log4j `Logger` to custom `DhLogger`.

#### Method Signature Changes

```java
// Old
protected void createInitialBindings()

// New
protected void createInitialSharedBindings()
```

**Impact**: Method renamed in base class - requires update in subclasses.

### 2.2 New Dependencies

| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| **zstd-jni** | ❌ Not present | `1.5.7-6` | **Must add** |
| **LWJGL** | `3.3.1` | `3.3.3` | Already updated in MattMC |
| **Manifold** | `2025.1.20` | `2025.1.27` | Optional upgrade |

**Action Required**: Add zstd compression library to MattMC dependencies.

### 2.3 API Breaking Changes (4.0.0 → 5.0.0)

The API version bump indicates breaking changes. Need to analyze:
- Public API method signatures
- Event system changes
- Configuration API changes

**Risk**: If MattMC or other integrated mods use DH's public API, they may break.

### 2.4 Mod Compatibility Changes

#### New: C2ME Support

C2ME (Concurrent Chunk Management Engine) is a new compatibility:

```java
// New file: C2meAccessor.java
// Provides compatibility with concurrent chunk generation
```

**Impact**: Neutral - MattMC doesn't currently use C2ME, but good to have.

#### Sodium/Iris Accessor Updates

Both `SodiumAccessor` and `IrisAccessor` have changes. This is **critical** as MattMC has Sodium and Iris deeply integrated.

**Action Required**: Carefully merge Sodium/Iris accessor changes to ensure compatibility with MattMC's integrated versions.

---

## Stage 3: Migration Strategy

### 3.1 Migration Approach

**Hybrid Merge Strategy**:
1. **Full Replacement**: Most core files can be replaced wholesale
2. **Careful Merge**: Sodium/Iris accessors need manual merging
3. **Version Adaptation**: Remove preprocessing or set correct version flag
4. **Preserve Integration**: Keep MattMC's build system integration intact

### 3.2 File-Level Migration Plan

#### Phase 1: Core Subsystems (Low Risk)

**Action**: Full replacement from `frnsrc/distant-horizons-main/`

Replace these directories wholesale:
- `coreSubProjects/api/` - Public API
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/config/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/dataObjects/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/dependencyInjection/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/enums/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/file/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/generation/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/jar/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/logging/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/multiplayer/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/network/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/pooling/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/pos/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/sql/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/util/`
- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/world/`

**Preprocessing Handling**: Since MattMC targets MC 1.21.10 specifically, we'll use the `MC_1_21_10` code branches and remove preprocessing directives.

#### Phase 2: Rendering Subsystem (Medium Risk)

**Action**: Replace with careful testing

- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/render/`

**Risk**: Rendering changes may affect Sodium/Iris integration.

**Mitigation**: Test thoroughly after replacement, verify shader compatibility.

#### Phase 3: Wrapper Interfaces (Medium Risk)

**Action**: Replace and verify MC API usage

- `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/wrapperInterfaces/`

**Risk**: Minecraft API wrappers may have changed for 1.21.11.

**Mitigation**: Since MattMC is 1.21.10, use the appropriate version branches.

#### Phase 4: Common Module (Low Risk)

**Action**: Full replacement

- `common/src/main/java/`
- `common/src/main/resources/`

#### Phase 5: Fabric Integration (HIGH RISK - CRITICAL)

**Action**: Careful manual merge

Files requiring manual merge:
1. **`fabric/src/main/java/com/seibel/distanthorizons/fabric/FabricMain.java`**
   - Remove preprocessing directives
   - Use MC 1.21.10 code paths
   - Update method names (`createInitialBindings` → `createInitialSharedBindings`)

2. **`fabric/src/main/java/com/seibel/distanthorizons/fabric/FabricClientProxy.java`**
   - Update to new API
   - Verify Sodium/Iris hooks

3. **`fabric/src/main/java/com/seibel/distanthorizons/fabric/FabricServerProxy.java`**
   - Update to new API

4. **`fabric/src/main/java/com/seibel/distanthorizons/fabric/wrappers/modAccessor/SodiumAccessor.java`**
   - **CRITICAL**: Merge with MattMC's integrated Sodium version
   - Check for API changes in MattMC's Sodium integration

5. **`fabric/src/main/java/com/seibel/distanthorizons/fabric/wrappers/modAccessor/IrisAccessor.java`**
   - **CRITICAL**: Merge with MattMC's integrated Iris version
   - Check for API changes in MattMC's Iris integration

6. **`fabric/src/main/java/com/seibel/distanthorizons/fabric/wrappers/modAccessor/C2meAccessor.java`**
   - **NEW FILE**: Copy over (neutral - not used)

7. **Mixins**
   - Replace all mixin files
   - Verify targets exist in MC 1.21.10

#### Phase 6: Resources (Low Risk)

**Action**: Full replacement

- `coreSubProjects/core/src/main/resources/`
- `fabric/src/main/resources/`
- Update `fabric.mod.json` version numbers

#### Phase 7: Build Configuration (Medium Risk)

**Action**: Update `build.gradle` dependencies

Update in `/home/runner/work/MattMC/MattMC/build.gradle`:

```gradle
// Add zstd compression
implementation 'com.github.luben:zstd-jni:1.5.7-6'

// Update version in distantHorizonsJar task
archiveVersion = '2.4.3-b'
```

Update version properties:
```gradle
filesMatching('fabric.mod.json') {
    expand(
        'version': '2.4.3-b',  // Updated
        // ... rest of properties
    )
}
```

### 3.3 Preprocessing Directive Strategy

The new version uses preprocessor directives like:
```java
#if MC_VER <= MC_1_21_10
    // Code for 1.21.10
#else
    // Code for 1.21.11+
#endif
```

**MattMC Strategy**: 
- **Option 1**: Configure preprocessor to expand for MC 1.21.10
- **Option 2**: Manually resolve to MC 1.21.10 code branches
- **Recommendation**: Option 2 - manually resolve to simplify build

This eliminates the need for the Manifold preprocessor dependency in production builds.

---

## Stage 4: Detailed Execution Checklist

### Phase 1: Preparation
- [ ] Backup current `modules/distant-horizons-2.3.4b/` (already in git)
- [ ] Create feature branch for migration
- [ ] Document current build/test status

### Phase 2: Core Replacement (Safe)
- [ ] Replace `coreSubProjects/api/` entirely
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/config/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/dataObjects/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/dependencyInjection/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/enums/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/file/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/generation/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/jar/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/logging/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/multiplayer/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/network/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/pooling/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/pos/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/sql/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/util/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/world/`
- [ ] Test compilation after core replacement

### Phase 3: Rendering and Wrappers
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/render/`
- [ ] Replace `coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/wrapperInterfaces/`
- [ ] Resolve preprocessing directives for MC 1.21.10
- [ ] Test compilation

### Phase 4: Common Module
- [ ] Replace `common/src/main/java/`
- [ ] Replace `common/src/main/resources/`
- [ ] Test compilation

### Phase 5: Fabric Integration (Critical)
- [ ] Copy new `C2meAccessor.java`
- [ ] Merge `FabricMain.java`:
  - [ ] Remove preprocessing, use MC 1.21.10 code
  - [ ] Update `createInitialBindings()` → `createInitialSharedBindings()`
  - [ ] Update logger initialization
- [ ] Merge `FabricClientProxy.java`
- [ ] Merge `FabricServerProxy.java`
- [ ] Merge `SodiumAccessor.java` (check MattMC Sodium APIs)
- [ ] Merge `IrisAccessor.java` (check MattMC Iris APIs)
- [ ] Update other mod accessors (BCLib, Optifine, Starlight)
- [ ] Update `ModChecker.java`
- [ ] Test compilation

### Phase 6: Mixins
- [ ] Replace all mixin files in `fabric/src/main/java/com/seibel/distanthorizons/fabric/mixins/`
- [ ] Verify mixin targets exist in MC 1.21.10
- [ ] Update mixin config JSON if needed
- [ ] Test compilation

### Phase 7: Resources
- [ ] Replace `coreSubProjects/core/src/main/resources/`
- [ ] Replace `fabric/src/main/resources/`
- [ ] Update `fabric.mod.json` version to `2.4.3-b`
- [ ] Verify language files present
- [ ] Test compilation

### Phase 8: Build System
- [ ] Add `zstd-jni:1.5.7-6` dependency to `build.gradle`
- [ ] Update DH version in `build.gradle` to `2.4.3-b`
- [ ] Update `gradle.properties` if needed
- [ ] Test full build: `./gradlew distantHorizonsJar`

### Phase 9: Integration Testing
- [ ] Run `./gradlew compileDistantHorizonsJava` - must succeed
- [ ] Run `./gradlew distantHorizonsJar` - must succeed
- [ ] Run `./gradlew build` - must succeed
- [ ] Run `./gradlew runClient` - test in-game
  - [ ] DH loads without errors
  - [ ] LOD rendering works
  - [ ] Sodium integration works
  - [ ] Iris shader integration works
  - [ ] Config UI accessible
  - [ ] No console errors

### Phase 10: Documentation
- [ ] Add `// BEGIN MATTMC-INTEGRATION` markers where MattMC differs from upstream
- [ ] Document preprocessing resolution in this file
- [ ] Update version numbers in this file
- [ ] Note any unresolved issues or TODOs

---

## Stage 5: Known Issues and Risks

### High-Priority Risks

1. **Sodium API Incompatibility**
   - **Risk**: MattMC's integrated Sodium version may not match what DH 2.4.3 expects
   - **Mitigation**: Carefully review `SodiumAccessor.java` changes, test rendering
   - **Status**: ⚠️ Must verify

2. **Iris API Incompatibility**
   - **Risk**: MattMC's integrated Iris version may not match what DH 2.4.3 expects
   - **Mitigation**: Carefully review `IrisAccessor.java` changes, test shader rendering
   - **Status**: ⚠️ Must verify

3. **Minecraft 1.21.11 vs 1.21.10 APIs**
   - **Risk**: New version targets 1.21.11, MattMC is 1.21.10
   - **Mitigation**: Use preprocessor branches for 1.21.10, or manually adapt
   - **Status**: ⚠️ Must handle preprocessing

4. **API 5.0.0 Breaking Changes**
   - **Risk**: If MattMC code uses DH's public API, it may break
   - **Mitigation**: Search for `import com.seibel.distanthorizons.api` in MattMC codebase
   - **Status**: ✅ Initial scan shows no direct API usage in MattMC

### Medium-Priority Risks

1. **Logger API Changes**
   - **Risk**: New `DhLogger` API vs old Log4j `Logger`
   - **Mitigation**: Update logger usage throughout codebase
   - **Status**: ⚠️ Need to update

2. **Missing Preprocessing Configuration**
   - **Risk**: Build may fail without Manifold preprocessor properly configured
   - **Mitigation**: Manually resolve preprocessing or configure Manifold
   - **Status**: ⚠️ Recommend manual resolution

3. **New File Additions**
   - **Risk**: New files (+36 total) may introduce features that break existing code
   - **Mitigation**: Review new file list, test thoroughly
   - **Status**: ⚠️ Monitor during testing

### Low-Priority Risks

1. **Resource File Changes**
   - **Risk**: Language files or assets may have changed
   - **Mitigation**: Full replacement is safe
   - **Status**: ✅ Low risk

2. **Build Configuration**
   - **Risk**: Gradle configuration may need updates
   - **Mitigation**: Minimal changes needed (add zstd dependency)
   - **Status**: ✅ Straightforward

---

## Stage 6: Integration Points Documentation

### MattMC-Specific Configurations

All MattMC integration is external to DH code:

1. **Build System** (`/home/runner/work/MattMC/MattMC/build.gradle`)
   ```gradle
   // Lines 414-449: distantHorizons source set configuration
   // Lines 513-516: dependency configuration
   // Lines 535-542: classpath dependencies
   // Lines 683-741: distantHorizonsJar task
   ```

2. **Runtime Loading** (`/home/runner/work/MattMC/MattMC/build.gradle`)
   ```gradle
   // Lines 979: fabric.addMods system property
   ```

3. **No Code Modifications**
   - Initial scan shows no MattMC-specific code in DH source files
   - All integration is through external build configuration
   - This simplifies migration - no custom patches to preserve

### Post-Migration Integration Points

After migration, these areas may need `// BEGIN MATTMC-INTEGRATION` markers:

1. **Preprocessing Resolution**
   - Any preprocessing directives manually resolved for MC 1.21.10
   - Mark with comments explaining manual resolution

2. **Sodium/Iris Accessor Merges**
   - If accessor files are modified for MattMC's Sodium/Iris versions
   - Mark with comments explaining compatibility adjustments

3. **Version-Specific Adaptations**
   - Any code adapted from 1.21.11 to 1.21.10
   - Mark with comments explaining version adaptation

---

## Stage 7: Success Criteria

Migration is successful when:

1. **Build Success**
   - [x] `./gradlew compileDistantHorizonsJava` succeeds
   - [x] `./gradlew distantHorizonsJar` succeeds
   - [x] `./gradlew build` succeeds
   - [x] No compilation errors
   - [x] No warnings about missing dependencies

2. **Functional Testing**
   - [ ] Client launches with DH loaded
   - [ ] DH version shows as 2.4.3-b in logs
   - [ ] LOD chunks generate and render
   - [ ] LOD chunks persist across sessions
   - [ ] Sodium integration works (optimized rendering)
   - [ ] Iris integration works (shader compatibility)
   - [ ] Config UI accessible and functional
   - [ ] No runtime crashes

3. **Performance**
   - [ ] FPS comparable to old version
   - [ ] LOD generation speed acceptable
   - [ ] Memory usage reasonable

4. **Documentation**
   - [ ] This file updated with actual changes made
   - [ ] Integration points clearly marked
   - [ ] TODOs documented

---

## Next Steps

1. **Immediate**: Execute Phase 1-2 (Preparation + Core Replacement)
2. **Short-term**: Execute Phase 3-5 (Rendering + Fabric Integration)
3. **Testing**: Execute Phase 9 (Integration Testing)
4. **Documentation**: Execute Phase 10 (Documentation)

---

## Appendix A: File Count Breakdown

### Old Version (modules/distant-horizons-2.3.4b/)
- Total Java files: **676**
- Core files: **347**
- API files: **~50** (estimated)
- Fabric files: **~80** (estimated)
- Common files: **~100** (estimated)

### New Version (frnsrc/distant-horizons-main/)
- Total Java files: **712** (+36 files)
- Core files: **366** (+19 files)
- API files: **~55** (estimated)
- Fabric files: **~85** (estimated)
- Common files: **~105** (estimated)

### New Files to Add (+36)
- Core subsystem enhancements
- New C2ME accessor
- Additional utilities
- Version compatibility layers

---

## Appendix B: Preprocessing Example

Example preprocessing directive resolution:

```java
// Original (frnsrc/distant-horizons-main/):
#if MC_VER <= MC_1_20_6
private static final ResourceLocation INITIAL_PHASE = 
    new ResourceLocation(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
#elif MC_VER <= MC_1_21_10
private static final ResourceLocation INITIAL_PHASE = 
    ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
#else
private static final Identifier INITIAL_PHASE = 
    Identifier.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
#endif

// Resolved for MattMC (MC 1.21.10):
// BEGIN MATTMC-INTEGRATION: Preprocessing resolved for MC 1.21.10
private static final ResourceLocation INITIAL_PHASE = 
    ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
// END MATTMC-INTEGRATION
```

---

## Appendix C: Dependency Changes

### Dependencies to Add

```gradle
// In build.gradle, DISTANT HORIZONS DEPENDENCIES section:
implementation 'com.github.luben:zstd-jni:1.5.7-6'
```

### Dependencies Already Present (No Change)
- Night Config (TOML): `3.6.7` ✅
- JSON Simple: `1.1.1` ✅
- XZ compression: `1.9` ✅
- SQLite JDBC: `3.47.2.0` ✅

---

**Document Status**: Stage 1 Complete - Recon & Mapping Done  
**Last Updated**: 2024-12-18  
**MattMC Version**: 1.21.10  
**Target DH Version**: 2.4.3-b-dev
