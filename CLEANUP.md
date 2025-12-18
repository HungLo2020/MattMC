# MattMC Cleanup Plan: Native Sodium & Iris Integration

## GOAL

Transform MattMC from a Fabric-based modded architecture into a clean, native game engine with Sodium and Iris as first-class built-in features. This means:

1. **Remove Fabric Loader entirely** - eliminate all mod loading infrastructure
2. **Integrate Sodium natively** - advanced chunk rendering as a core engine feature
3. **Integrate Iris natively** - shader pack support as a core engine feature
4. **Simplify the codebase** - replace runtime mixins with direct code, improve maintainability
5. **Zero regressions** - every step maintains a fully functional, buildable project

**Why?** Removing Fabric and mixins improves performance, consistency, maintainability, and testability. Sodium and Iris become native features you can toggle on/off, not separate mods to manage.

**Current State:** Fabric Loader + Mixin system → Sodium JAR + Iris JAR  
**Target State:** Single unified MattMC engine with optional Sodium/Iris rendering paths

---

## Current Architecture Analysis

**Fabric Loader** (`modules/fabric-loader-0.18.2/`)
- 199 Java files providing mod loading infrastructure
- Status: Will be completely removed

**Sodium** (`modules/sodium-1.21.9/`)
- 581 Java files for high-performance chunk rendering
- Depends on: Fabric API stubs, Fabric Loader mixins
- Status: Will be integrated as native feature

**Iris** (`modules/Iris-1.21.9/`)
- 725 Java files for shader pack support
- Depends on: Fabric API stubs, Fabric Loader mixins, Sodium
- Status: Will be integrated as native feature

**Problems to Solve:**
- Runtime mixin transformations (320+ mixin classes)
- Multiple JAR files and source sets
- Fabric Loader startup overhead
- Fragmented configuration files
- Artificial separation between "base game" and "mods"

---

## Integration Steps

Each step is:
- ✅ **Independently testable** - project builds and runs after completion
- ✅ **Zero regression** - no loss of functionality
- ✅ **Incremental** - small, focused changes
- ✅ **Reversible** - can be rolled back if needed

---

## Step 1: Remove Access Widener Files

**Objective:** Eliminate Fabric's runtime access widening system by applying visibility changes directly to source code.

**Why:** Removes first Fabric dependency, enabling later steps.

**Actions:**
1. Identify all fields/classes needing public access in access widener files
2. Change visibility modifiers in source code (private → public)
3. Add JavaDoc comments explaining each change
4. Delete all `.accesswidener` files
5. Verify Sodium and Iris still compile

**Files Modified:** ~10 Minecraft source files  
**Files Deleted:** 3 access widener files

**Success Criteria:**
- ✅ `./gradlew build` succeeds
- ✅ `./gradlew compileSodiumJava` succeeds
- ✅ `./gradlew compileIrisJava` succeeds
- ✅ All access widener files removed

**Estimated Effort:** 2-4 hours

---

## Step 2: Analyze Mixin Usage and Create Integration Plan

**Objective:** Document all mixins in Sodium and Iris to plan systematic integration.

**Why:** Understanding what mixins do is essential before replacing them.

**Actions:**
1. Count and categorize all Sodium mixins by purpose
2. Count and categorize all Iris mixins by purpose
3. Identify critical vs. minor mixins
4. Create priority order for integration
5. Document each mixin's purpose and target class

**Files Created:** `docs/MIXIN-ANALYSIS.md`

**Success Criteria:**
- ✅ Complete list of all mixins documented
- ✅ Priority order established
- ✅ No code changes (analysis only)
- ✅ Project still builds

**Estimated Effort:** 3-5 hours

---

## Step 3: Create Feature Toggle System

**Objective:** Add ability to enable/disable Sodium and Iris at runtime without recompilation.

**Why:** Allows testing both vanilla and optimized rendering paths independently.

**Actions:**
1. Create `SodiumFeatures.java` with enable/disable flags
2. Create `IrisFeatures.java` with enable/disable flags
3. Add configuration loading/saving for feature flags
4. Test toggling features on/off

**Files Created:**
- `net/minecraft/client/renderer/SodiumFeatures.java`
- `net/minecraft/client/renderer/IrisFeatures.java`

**Success Criteria:**
- ✅ Features can be toggled programmatically
- ✅ Flags persist across restarts
- ✅ `./gradlew build` succeeds
- ✅ No functional changes to rendering yet

**Estimated Effort:** 2-3 hours

---

## Step 4: Inline Critical Sodium Rendering Mixins

**Objective:** Replace the 5 most critical Sodium mixins with direct code integration.

**Why:** Gets Sodium's core rendering working natively with minimal risk.

**Actions:**
1. Inline `LevelRendererMixin` into `LevelRenderer.java`
2. Inline `ChunkRenderCacheMixin` into relevant class
3. Inline `GameRendererMixin` into `GameRenderer.java`
4. Inline `BlockRenderDispatcherMixin` into `BlockRenderDispatcher.java`
5. Inline `WorldRendererMixin` into world renderer class
6. Test vanilla and Sodium rendering paths work

**Files Modified:** ~5 Minecraft renderer classes

**Success Criteria:**
- ✅ Vanilla rendering works (Sodium disabled)
- ✅ Sodium rendering works (Sodium enabled)
- ✅ `./gradlew build` succeeds
- ✅ Visual parity with mixin-based version
- ✅ FPS unchanged ±5%

**Estimated Effort:** 8-12 hours

---

## Step 5: Inline Remaining Sodium Mixins Incrementally

**Objective:** Replace all remaining Sodium mixins (~115 remaining) with direct integration.

**Why:** Complete Sodium integration without Fabric mixins.

**Actions:**
1. Group mixins by subsystem (particles, entities, terrain, etc.)
2. Inline one subsystem at a time
3. Test after each subsystem
4. Remove mixin classes as they're inlined
5. Update mixin config files to reflect removed mixins

**Files Modified:** ~30-40 Minecraft source files

**Success Criteria:**
- ✅ All Sodium mixins removed
- ✅ Sodium functionality unchanged
- ✅ `./gradlew build` succeeds
- ✅ Game runs with Sodium enabled/disabled

**Estimated Effort:** 20-30 hours

**Note:** Can be done incrementally over multiple commits (10-20 mixins per commit)

---

## Step 6: Inline Critical Iris Shader Mixins

**Objective:** Replace the 5 most critical Iris mixins with direct code integration.

**Why:** Gets shader pipeline working natively with minimal risk.

**Actions:**
1. Inline `GameRendererMixin` (shader passes)
2. Inline `WorldRenderingPipelineMixin`
3. Inline shader program management mixins
4. Inline framebuffer management mixins
5. Test shader rendering with/without shaders enabled

**Files Modified:** ~5 Minecraft renderer classes

**Success Criteria:**
- ✅ Vanilla rendering works (shaders disabled)
- ✅ Shader rendering works (shaders enabled)
- ✅ `./gradlew build` succeeds
- ✅ Shader packs load and render correctly

**Estimated Effort:** 10-15 hours

---

## Step 7: Inline Remaining Iris Mixins Incrementally

**Objective:** Replace all remaining Iris mixins (~195 remaining) with direct integration.

**Why:** Complete Iris integration without Fabric mixins.

**Actions:**
1. Group mixins by subsystem (uniforms, buffers, compatibility, etc.)
2. Inline one subsystem at a time
3. Test shader rendering after each subsystem
4. Remove mixin classes as they're inlined
5. Pay special attention to Sodium compatibility mixins

**Files Modified:** ~40-50 Minecraft and Sodium source files

**Success Criteria:**
- ✅ All Iris mixins removed
- ✅ Shader packs work correctly
- ✅ Sodium + Iris work together
- ✅ `./gradlew build` succeeds

**Estimated Effort:** 25-35 hours

**Note:** Can be done incrementally over multiple commits

---

## Step 8: Consolidate Sodium Into Main Source Tree

**Objective:** Move Sodium source files from separate module into main Minecraft source.

**Why:** Sodium becomes part of the engine, not a separate module.

**Actions:**
1. Choose package structure: `net/minecraft/client/renderer/sodium/`
2. Move Sodium source files to main source tree
3. Update all package declarations and imports
4. Update build.gradle to remove Sodium source set
5. Remove Fabric API references from Sodium code
6. Test compilation and runtime

**Files Moved:** 581 Sodium Java files

**Success Criteria:**
- ✅ All Sodium code in main source tree
- ✅ No separate Sodium JAR produced
- ✅ Package names updated
- ✅ `./gradlew build` succeeds
- ✅ Sodium rendering works

**Estimated Effort:** 6-10 hours

---

## Step 9: Consolidate Iris Into Main Source Tree

**Objective:** Move Iris source files from separate module into main Minecraft source.

**Why:** Iris becomes part of the engine, not a separate module.

**Actions:**
1. Choose package structure: `net/minecraft/client/renderer/iris/`
2. Move Iris source files to main source tree
3. Update all package declarations and imports
4. Update build.gradle to remove Iris source set
5. Remove Fabric API references from Iris code
6. Test shader loading and rendering

**Files Moved:** 725 Iris Java files

**Success Criteria:**
- ✅ All Iris code in main source tree
- ✅ No separate Iris JAR produced
- ✅ Package names updated
- ✅ `./gradlew build` succeeds
- ✅ Shaders work correctly

**Estimated Effort:** 8-12 hours

---

## Step 10: Remove Fabric Loader Infrastructure

**Objective:** Delete Fabric Loader completely from the project.

**Why:** No longer needed after mixins are inlined and modules consolidated.

**Actions:**
1. Verify no Fabric API imports remain in codebase
2. Remove Fabric Loader module directory
3. Remove Fabric Loader source set from build.gradle
4. Remove Fabric dependencies from build.gradle
5. Change main class back to vanilla `net.minecraft.client.main.Main`
6. Remove mods directory logic
7. Test clean build and launch

**Files Deleted:** Entire `modules/fabric-loader-0.18.2/` directory

**Success Criteria:**
- ✅ No Fabric code remains
- ✅ Launches with vanilla Main class
- ✅ `./gradlew build` succeeds
- ✅ `./gradlew runClient` works
- ✅ Both Sodium and Iris functional

**Estimated Effort:** 4-6 hours

---

## Step 11: Unify Configuration System

**Objective:** Merge Sodium and Iris configs into Minecraft's native options.txt.

**Why:** Single configuration file, better UX, native integration.

**Actions:**
1. Add Sodium options to `Options.java`
2. Add Iris options to `Options.java`
3. Create migration logic for old config files
4. Update options parsing/saving
5. Test config persistence

**Files Modified:**
- `net/minecraft/client/Options.java`
- `options.txt` format

**Success Criteria:**
- ✅ All settings in single options.txt
- ✅ Old configs auto-migrate
- ✅ Settings persist correctly
- ✅ `./gradlew build` succeeds

**Estimated Effort:** 4-6 hours

---

## Step 12: Create Unified Video Settings UI

**Objective:** Add Sodium and Iris options to Minecraft's Video Settings screen.

**Why:** Cohesive user experience, no separate mod menus.

**Actions:**
1. Add Sodium options to Video Settings
2. Create "Shaders" button and screen
3. Implement shader pack selection UI
4. Add tooltips for new options
5. Test all UI interactions

**Files Modified:**
- `net/minecraft/client/gui/screens/VideoSettingsScreen.java`

**Files Created:**
- `net/minecraft/client/gui/screens/ShaderSettingsScreen.java`

**Success Criteria:**
- ✅ All options accessible from Video Settings
- ✅ Shader pack selection works
- ✅ `./gradlew build` succeeds
- ✅ UI is intuitive and polished

**Estimated Effort:** 6-10 hours

---

## Step 13: Simplify Build System

**Objective:** Clean up build.gradle to reflect new unified architecture.

**Why:** Faster builds, simpler maintenance, clearer structure.

**Actions:**
1. Remove all Fabric-related tasks
2. Remove module-specific JAR tasks
3. Simplify to single main source set
4. Update run tasks to use vanilla launcher
5. Archive old modules directory
6. Test build performance

**Files Modified:** `build.gradle`

**Success Criteria:**
- ✅ Single source set: main
- ✅ Single JAR output
- ✅ Faster build times
- ✅ `./gradlew build` succeeds
- ✅ Clean, maintainable build script

**Estimated Effort:** 3-5 hours

---

## Step 14: Update Documentation

**Objective:** Document the new native architecture.

**Why:** Clear documentation for developers and users.

**Actions:**
1. Update README.md
2. Update INTEGRATION.md
3. Create architecture diagrams
4. Document feature toggle system
5. Add user guide for Sodium/Iris features

**Files Modified/Created:**
- `README.md`
- `INTEGRATION.md`
- `docs/ARCHITECTURE.md`

**Success Criteria:**
- ✅ Clear, accurate documentation
- ✅ Architecture well-documented
- ✅ User-facing features explained

**Estimated Effort:** 4-6 hours

---

## Summary

**Total Steps:** 14 incremental steps  
**Total Estimated Effort:** 110-175 hours (3-4 weeks full-time)

**Key Principles:**
1. Every step leaves project in buildable, runnable state
2. No breaking changes - vanilla and enhanced rendering always work
3. Incremental commits - test after each change
4. Can pause between steps - no "partially done" states

**End Result:**
- Zero Fabric dependencies
- Native Sodium and Iris integration
- Cleaner, more maintainable codebase
- Better performance and consistency
- Single unified JAR distribution
