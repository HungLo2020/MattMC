# Distant Horizons LOD Rendering Troubleshooting

This document chronicles the investigation and attempted fixes for LOD rendering issues in singleplayer mode with Distant Horizons 2.4.4-b-dev, Sodium 0.7.2, Iris 1.9.6, and Minecraft 1.21.10.

## Problem Statement

LODs (Level of Detail chunks) are not rendering in singleplayer mode, even though all other aspects of Distant Horizons appear to initialize correctly.

## Investigation Timeline

### 1. Build Configuration Issues

**Problem**: Build failed when `distanthorizons=false` in gradle.properties.

**Root Cause**: Duplicate DH API headers were added to Iris source set, causing:
- Duplicate classes in both Iris and DH JARs at runtime
- Build failures when DH was disabled (references to non-existent classes)

**Solution**:
- Removed duplicate header files from `modules/Iris-1.21.9/common/src/headers/java/com/seibel/`
- Reverted `build.gradle` to use direct compilation against DH module
- **Status**: ✅ Fixed (commits f09fce5, 6ab62e2)

### 2. Mixin Priority Issues with Sodium

**Problem**: DH's rendering mixins weren't being applied when Sodium was present.

**Root Cause**: Sodium 0.7.2 uses `@Overwrite` on `prepareChunkRenders()`, completely replacing the vanilla method. DH's `@Inject` mixin was trying to inject into the vanilla version, which no longer existed after Sodium's overwrite.

**Solution**: Set DH mixin priority to 1100 in `DistantHorizons.fabric.mixins.json` (Sodium uses default 1000). This makes DH mixins apply AFTER Sodium's overwrites, allowing injections to target the final overwritten method.

**Status**: ✅ Fixed (commit 9a00566)

### 3. Rendering Pipeline Investigation

**Symptoms**:
- DH initializes successfully
- Pipeline created for overworld
- OpenGL proxy created
- BUT: No "DH-RenderLevel" profiler messages or actual rendering

**Investigation Steps**:
1. Added logging to verify mixin class loading
2. Added logging to verify injection points firing
3. Added logging to verify `renderLods()` being called
4. Added logging around profiler operations
5. Added logging for render thread tasks
6. Added logging for validation checks

**Findings**: All mixins were working correctly up until validation.

**Status**: ✅ Diagnostics complete (commits 50247bf, fe3ab1b, 9f956aa)

### 4. Validation Failure: "No DH Client World Loaded"

**Root Cause Found**: The validation error occurs because `SharedApi.tryGetDhClientWorld()` returns `null`. This method checks if `SharedApi.currentWorld` is an instance of `IDhClientWorld`.

**Investigation**:
- `clientLevelWrapper` was being set correctly in mixins ✅
- Minecraft's `ClientLevel` exists and is valid ✅
- BUT: `dhClientWorld` (DH's internal world object) was `null` ❌

**Status**: ✅ Root cause identified (commit a7d5492)

### 5. Client-Side World Creation Investigation

**Architecture Discovery**:
- **Dedicated servers**: Use `DhClientWorld` (created by `ClientApi.onClientOnlyConnected()`)
- **Singleplayer/Integrated servers**: Should use `DhClientServerWorld` (created by `ServerApi.serverLoadEvent()`)

**Attempt #1 - Add Singleplayer to Client World Creation**:
- Modified `ClientApi.onClientOnlyConnected()` to create `DhClientWorld` for singleplayer
- **Result**: ❌ Network packet encoding errors - singleplayer can't use network-based `DhClientWorld`
- **Status**: Reverted (commits 0559a0a, 1fc4adc, 6b254a3)

**Conclusion**: Singleplayer was intentionally excluded from client-side world creation. Must use server-side approach.

### 6. Server-Side World Creation Investigation

**Expected Behavior**: `ServerApi.serverLoadEvent()` should create `DhClientServerWorld` for integrated servers.

**Investigation Findings**:
- `ServerApi.serverLoadEvent()` method exists ✅
- It creates `DhClientServerWorld` for integrated servers ✅
- BUT: Method was NEVER being called ❌

**Status**: ✅ Issue identified (commit d0cc6d6)

### 7. Server Event Registration Investigation

**Problem**: `ServerApi.serverLoadEvent()` should be triggered by `SERVER_STARTING` event, but wasn't.

**Investigation Chain**:

#### Attempt #1 - `isValidTime()` Check
- **Issue**: `isValidTime()` in `FabricServerProxy.java` returned `false` for integrated servers
- **Fix**: Modified to always return `true` for integrated servers
- **Result**: ❌ Still didn't fire events
- **Status**: Applied but insufficient (commit b0d05f0)

#### Attempt #2 - Event Registration Timing
- **Issue**: `SERVER_STARTING` event callback was registered but never fired
- **Investigation**: Added logging to trace event registration and callbacks
- **Finding**: `registerEvents()` was called, but event never fired
- **Status**: ✅ Issue identified (commit e1c4e82)

#### Attempt #3 - Phase-Based Event System
- **Issue**: `SERVER_STARTED` and non-phased `SERVER_STARTING` don't fire reliably for integrated servers
- **Fix**: Changed to `SERVER_STARTING.register(Event.DEFAULT_PHASE, ...)`
- **Result**: ❌ Still didn't fire
- **Status**: Applied but insufficient (commit 0d3ba46)

#### Attempt #4 - Phase Ordering Setup
- **Issue**: Phase ordering (`INITIAL_PHASE` → `DEFAULT_PHASE`) was not set up for integrated servers
- **Root Cause**: Phase ordering is normally set up in `FabricMain.subscribeServerStartingEvent()`, which is only called from `AbstractModInitializer.onInitializeServer()`, which NEVER runs for integrated servers
- **Fix**: Added phase ordering setup directly in `FabricServerProxy.registerEvents()`
- **Result**: ❌ Still investigating
- **Status**: Applied (commit 21420f1a)

#### Attempt #5 - Fabric API Entry Points
- **Issue**: `FabricMain` implements `ClientModInitializer` and `DedicatedServerModInitializer` but never implemented their required methods
- **Fix**: Added `onInitializeClient()` and `onInitializeServer()` method implementations
- **Result**: ❌ Calling `onInitializeServer()` from client init caused crashes
- **Status**: Reverted (commits 2dce543d, cf777498, 787de92f)

### 8. Current State

**What's Working**:
- ✅ Build succeeds with `distanthorizons=true`
- ✅ Build succeeds with `distanthorizons=false`
- ✅ Mixin priority allows DH mixins to run after Sodium
- ✅ All mixins load and inject correctly
- ✅ Rendering pipeline executes up to validation

**What's NOT Working**:
- ❌ `SERVER_STARTING` event does not fire for integrated servers
- ❌ `ServerApi.serverLoadEvent()` never gets called
- ❌ `DhClientServerWorld` is never created
- ❌ Validation fails: "No DH Client World Loaded"
- ❌ LODs do not render in singleplayer

**Current Hypothesis**:
The Fabric event system for integrated servers may work differently than expected, or there may be a fundamental incompatibility between how DH expects server initialization to work and how this modpack's environment actually works.

## Key Files Modified

### Successful Fixes
1. `build.gradle` - Removed duplicate DH API headers
2. `DistantHorizons.fabric.mixins.json` - Set mixin priority to 1100
3. `FabricServerProxy.java` - Modified `isValidTime()`, added phase-based event registration, added phase ordering setup
4. `FabricMain.java` - Made `INITIAL_PHASE` public, added `onInitializeClient()` override

### Debug Instrumentation Added
1. `ClientApi.java` - Logging for rendering validation and world state
2. `ServerApi.java` - Logging for server-side world creation
3. `FabricServerProxy.java` - Logging for event registration and callbacks
4. `AbstractModInitializer.java` - Logging for initialization flow
5. `FabricMain.java` - Logging for Fabric API entry points
6. `MixinLevelRenderer.java` - Extensive rendering pipeline logging
7. `MixinClientPacketListener.java` - Client connection event logging
8. `FabricMixinPlugin.java` - Mixin loading pipeline logging

## Versions

- Minecraft: 1.21.10
- Sodium: 0.7.2
- Iris: 1.9.6
- Distant Horizons: 2.4.4-b-dev
- Fabric API: (included in modpack)

## Next Steps / Recommendations

1. **Check Reference Implementation**: Compare with a working vanilla Fabric installation that has DH 2.4.3b, Iris 1.9.6, and Sodium 0.7.2 to see how server initialization differs

2. **Alternative Event Hooks**: Investigate if there's a different Fabric lifecycle event that reliably fires for integrated servers

3. **Manual Server World Creation**: Consider manually creating `DhClientServerWorld` from client initialization when singleplayer is detected, similar to how dedicated servers work

4. **Version Compatibility**: Consider using DH 2.4.3b (the working reference version) instead of 2.4.4-b-dev

5. **Fabric Loader Investigation**: Verify Fabric Loader version and check if there are known issues with server lifecycle events in integrated server environments

6. **Server Thread Analysis**: The logs show server starting on a different thread - may need to investigate thread timing or synchronization issues

## Debug Log Patterns to Look For

When troubleshooting, check ERROR-LOG.txt for:
- `FabricServerProxy.registerEvents() CALLED` - Should appear during client init
- `SERVER_STARTING event fired` - Should appear when integrated server starts (currently MISSING)
- `ServerApi.serverLoadEvent() CALLED!` - Should appear after event fires (currently MISSING)
- `Created world: DhClientServerWorld@...` - Should appear after serverLoadEvent (currently MISSING)
- `VALIDATION FAILED: No DH Client World Loaded` - Currently appears during rendering

## Conclusion

The investigation has narrowed down the issue to server-side initialization not occurring for integrated servers. The `SERVER_STARTING` event is not firing despite being registered with proper phase ordering. This prevents `DhClientServerWorld` from being created, which causes the validation failure during rendering.

The root cause appears to be a fundamental difference in how Fabric handles server lifecycle events for integrated servers versus dedicated servers, or a timing/threading issue specific to this environment.