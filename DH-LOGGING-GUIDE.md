# Distant Horizons - Comprehensive Logging Guide

This document explains the logging tags added to help diagnose why LODs are not rendering in singleplayer mode.

## Log Tag Reference

### Initialization Phase Tags

#### `[DH-INIT]` - Main Initialization Flow
Tracks the overall initialization process for both client and dedicated server modes.

**Key Log Patterns:**
```
[DH-INIT] ========== CLIENT INITIALIZATION START ==========
[DH-INIT] Thread: <thread-name> (ID: <thread-id>)
[DH-INIT] Creating client proxy and registering events...
[DH-INIT] Client proxy events registered
[DH-INIT] Creating server proxy (integrated=true) and registering events...
[DH-INIT] Server proxy (integrated) events registered
[DH-INIT] Subscribing to client started event...
[DH-INIT] ========== CLIENT INITIALIZATION COMPLETE ==========
```

For dedicated servers:
```
[DH-INIT] ========== DEDICATED SERVER INITIALIZATION START ==========
[DH-INIT] Creating server proxy (dedicated=true) and registering events...
```

**What to Look For:**
- Both client and server proxies should be created during client initialization
- The server proxy with `integrated=true` is crucial for singleplayer

---

#### `[DH-EVENTS]` - Event Registration
Tracks when Fabric events are registered.

**Key Log Patterns:**
```
[DH-EVENTS] ========== REGISTERING FABRIC SERVER EVENTS ==========
[DH-EVENTS] isDedicatedServer: false
[DH-EVENTS] Thread: <thread-name> (ID: <thread-id>)
[DH-EVENTS] Registering SERVER_STARTING event...
[DH-EVENTS] Registering SERVER_STOPPED event...
[DH-EVENTS] Registering ServerWorldEvents.LOAD event...
[DH-EVENTS] ========== FABRIC SERVER EVENTS REGISTERED ==========
```

**What to Look For:**
- `isDedicatedServer: false` for integrated servers
- All events should be registered successfully
- Thread ID helps identify if registration happens on the correct thread

---

#### `[DH-EVENT-SUB]` - Event Subscription Details
Tracks the specific subscription to SERVER_STARTING with phase ordering.

**Key Log Patterns:**
```
[DH-EVENT-SUB] Subscribing to SERVER_STARTING event...
[DH-EVENT-SUB] Thread: <thread-name> (ID: <thread-id>)
[DH-EVENT-SUB] Adding phase ordering: INITIAL_PHASE -> DEFAULT_PHASE
[DH-EVENT-SUB] Registering SERVER_STARTING event handler at INITIAL_PHASE
[DH-EVENT-SUB] SERVER_STARTING event subscription complete
```

**What to Look For:**
- Phase ordering should be added before registration
- Handler should be registered at INITIAL_PHASE

---

### Event Callback Tags

#### `[DH-EVENT-FIRE]` - SERVER_STARTING Event Firing
**CRITICAL**: This should appear when the integrated server starts in singleplayer.

**Key Log Patterns:**
```
[DH-EVENT-FIRE] ========== SERVER_STARTING EVENT FIRED ==========
[DH-EVENT-FIRE] Server: <server-instance>
[DH-EVENT-FIRE] Is Dedicated: false
[DH-EVENT-FIRE] Thread: <thread-name> (ID: <thread-id>)
[DH-EVENT-FIRE] SERVER_STARTING event handler completed
```

**PROBLEM IDENTIFIED**: According to DH-TROUBLESHOOTING.md, this event is **NOT FIRING** for integrated servers in singleplayer mode. If you don't see these logs when joining a singleplayer world, this confirms the root cause.

---

#### `[DH-EVENT-CALLBACK]` - Event Callback Execution
Tracks when various server event callbacks are triggered.

**Key Log Patterns for SERVER_STARTING:**
```
[DH-EVENT-CALLBACK] ========== SERVER_STARTING CALLBACK TRIGGERED ==========
[DH-EVENT-CALLBACK] Server: <server-instance>
[DH-EVENT-CALLBACK] Is Dedicated: false
[DH-EVENT-CALLBACK] Thread: <thread-name> (ID: <thread-id>)
[DH-EVENT-CALLBACK] isValidTime: true
[DH-EVENT-CALLBACK] Calling ServerApi.serverLoadEvent(isDedicated=false)
[DH-EVENT-CALLBACK] ServerApi.serverLoadEvent completed
```

**What to Look For:**
- `Is Dedicated: false` for integrated servers
- `isValidTime: true` - if false, the event is being skipped
- Should call `ServerApi.serverLoadEvent(isDedicated=false)`

**If Missing**: This is the core of the problem - the world creation never happens.

---

#### `[DH-VALIDATION]` - isValidTime() Checks
Tracks the validation that gates whether server events should be processed.

**Key Log Patterns:**
```
[DH-VALIDATION] isValidTime() called
[DH-VALIDATION] isDedicatedServer: false
[DH-VALIDATION] Is on title screen: false
[DH-VALIDATION] isValidTime() returning: true
```

**What to Look For:**
- For integrated servers, should return `true` when not on title screen
- If returning `false`, events will be skipped

---

### World Creation Tags

#### `[DH-WORLD]` - Server World Creation
**CRITICAL**: Tracks the creation of DhClientServerWorld for integrated servers.

**Key Log Patterns:**
```
[DH-WORLD] ========== SERVER LOAD EVENT ==========
[DH-WORLD] isDedicatedEnvironment: false
[DH-WORLD] Thread: <thread-name> (ID: <thread-id>)
[DH-WORLD] Current world before: null
[DH-WORLD] Creating DhClientServerWorld (integrated server)
[DH-WORLD] Created world: DhClientServerWorld@<hashcode>
[DH-WORLD] World class: com.seibel.distanthorizons.core.world.DhClientServerWorld
[DH-WORLD] Calling SharedApi.setDhWorld()
[DH-WORLD] Current world after: DhClientServerWorld@<hashcode>
[DH-WORLD] tryGetDhClientWorld: DhClientServerWorld@<hashcode>
[DH-WORLD] ========== SERVER LOAD EVENT COMPLETE ==========
```

**PROBLEM IDENTIFIED**: If these logs are missing, it means `ServerApi.serverLoadEvent()` was never called, which happens because SERVER_STARTING event didn't fire.

**For Dedicated Servers:**
```
[DH-WORLD] Creating DhServerWorld (dedicated server)
```

**For Client-Only Connections:**
```
[DH-WORLD] Creating DhClientWorld (client-only mode)
```

---

#### `[DH-WORLD-SET]` - World Assignment
Tracks the assignment of the DH world to SharedApi.

**Key Log Patterns:**
```
[DH-WORLD-SET] ========== SETTING DH WORLD ==========
[DH-WORLD-SET] Thread: <thread-name> (ID: <thread-id>)
[DH-WORLD-SET] Old world: null
[DH-WORLD-SET] New world: DhClientServerWorld@<hashcode>
[DH-WORLD-SET] New world class: com.seibel.distanthorizons.core.world.DhClientServerWorld
[DH-WORLD-SET] New world environment: <environment>
[DH-WORLD-SET] Is IDhClientWorld: true
[DH-WORLD-SET] Setting up thread pools...
[DH-WORLD-SET] Firing DhApiWorldLoadEvent...
[DH-WORLD-SET] Final currentWorld: DhClientServerWorld@<hashcode>
[DH-WORLD-SET] Final tryGetDhClientWorld: DhClientServerWorld@<hashcode>
[DH-WORLD-SET] ========== DH WORLD SET COMPLETE ==========
```

**What to Look For:**
- `Is IDhClientWorld: true` is essential for singleplayer rendering
- `Final tryGetDhClientWorld` should NOT be null for singleplayer

---

#### `[DH-WORLD-GET]` - World Retrieval
Tracks attempts to get the DH client world for rendering.

**Key Log Patterns:**
```
[DH-WORLD-GET] tryGetDhClientWorld() - currentWorld: DhClientServerWorld@<hashcode>, is IDhClientWorld: true
```

**Problem Pattern:**
```
[DH-WORLD-GET] tryGetDhClientWorld() - currentWorld: null, is IDhClientWorld: false
```

---

### Client Connection Tags

#### `[DH-CLIENT-CONNECT]` - Client Connection Events
Tracks when the client connects to servers (but NOT for integrated/singleplayer).

**Key Log Patterns for Dedicated Server:**
```
[DH-CLIENT-CONNECT] ========== CLIENT ONLY CONNECTED ==========
[DH-CLIENT-CONNECT] Thread: <thread-name> (ID: <thread-id>)
[DH-CLIENT-CONNECT] Connected to dedicated server: true
[DH-CLIENT-CONNECT] Connected to replay: false
[DH-CLIENT-CONNECT] Creating DhClientWorld (client-only mode)
```

**Key Log Patterns for Singleplayer:**
```
[DH-CLIENT-CONNECT] ========== CLIENT ONLY CONNECTED ==========
[DH-CLIENT-CONNECT] Connected to dedicated server: false
[DH-CLIENT-CONNECT] Connected to replay: false
[DH-CLIENT-CONNECT] Not connected to dedicated server or replay - skipping DhClientWorld creation
[DH-CLIENT-CONNECT] This is expected for integrated/singleplayer - world should be created by ServerApi
```

**What to Look For:**
- In singleplayer, DhClientWorld should NOT be created here
- The world should instead be created by ServerApi as DhClientServerWorld

---

### Level Loading Tags

#### `[DH-LEVEL]` - Server Level Loading
Tracks when server levels are loaded into the DH world.

**Key Log Patterns:**
```
[DH-LEVEL] ========== SERVER LEVEL LOAD EVENT ==========
[DH-LEVEL] Level: <level-wrapper>
[DH-LEVEL] Level identifier: <identifier>
[DH-LEVEL] Thread: <thread-name> (ID: <thread-id>)
[DH-LEVEL] Current DH world: DhClientServerWorld@<hashcode>
[DH-LEVEL] Loading level into DH world...
[DH-LEVEL] Level loaded, firing DhApiLevelLoadEvent
[DH-LEVEL] ========== SERVER LEVEL LOAD EVENT COMPLETE ==========
```

**Problem Pattern:**
```
[DH-LEVEL] Current DH world: null
[DH-LEVEL] Cannot load level - DH world is null!
```

---

#### `[DH-CLIENT-LEVEL]` - Client Level Loading
Tracks when client levels are loaded.

**Key Log Patterns:**
```
[DH-CLIENT-LEVEL] ========== CLIENT LEVEL LOAD EVENT ==========
[DH-CLIENT-LEVEL] Level: <level-wrapper>
[DH-CLIENT-LEVEL] Level identifier: <identifier>
[DH-CLIENT-LEVEL] Current DH world: DhClientServerWorld@<hashcode>
[DH-CLIENT-LEVEL] Loading level into DH world...
[DH-CLIENT-LEVEL] Level loaded, firing DhApiLevelLoadEvent
[DH-CLIENT-LEVEL] Loading waiting chunks for level...
[DH-CLIENT-LEVEL] ========== CLIENT LEVEL LOAD EVENT COMPLETE ==========
```

**Problem Pattern:**
```
[DH-CLIENT-LEVEL] DH world is null - adding to waiting levels
```

---

### Rendering Pipeline Tags

#### `[DH-RENDER-MIXIN]` - Mixin Injection Point
Tracks when the rendering mixin is called.

**Key Log Patterns:**
```
[DH-RENDER-MIXIN] prepareChunkRenders() called
[DH-RENDER-MIXIN] Thread: <thread-name> (ID: <thread-id>)
[DH-RENDER-MIXIN] Setting render state matrices and level wrapper
[DH-RENDER-MIXIN] clientLevelWrapper: <wrapper>
[DH-RENDER-MIXIN] Calling ClientApi.INSTANCE.renderLods()
[DH-RENDER-MIXIN] ClientApi.INSTANCE.renderLods() completed
```

**What to Look For:**
- This should be called every frame during rendering
- If missing, the mixin isn't being applied

---

#### `[DH-RENDER]` - Render Entry Point
Tracks entry to the LOD rendering system.

**Key Log Patterns:**
```
[DH-RENDER] renderLods() called
[DH-RENDER] renderLods() completed
```

---

#### `[DH-RENDER-LAYER]` - Render Layer Processing
Tracks the detailed rendering layer process.

**Key Log Patterns:**
```
[DH-RENDER-LAYER] ========== RENDER LOD LAYER START ==========
[DH-RENDER-LAYER] renderingDeferredLayer: false
[DH-RENDER-LAYER] Thread: <thread-name> (ID: <thread-id>)
[DH-RENDER-LAYER] Setting up render parameters...
[DH-RENDER-LAYER] Render pass: OPAQUE_AND_TRANSPARENT
[DH-RENDER-LAYER] clientLevelWrapper: <wrapper>
[DH-RENDER-LAYER] RenderParams created
[DH-RENDER-LAYER] Validating render parameters...
[DH-RENDER-LAYER] Validation passed!
[DH-RENDER-LAYER] Starting rendering...
[DH-RENDER-LAYER] Calling LodRenderer.INSTANCE.render()
[DH-RENDER-LAYER] LodRenderer.INSTANCE.render() completed
[DH-RENDER-LAYER] ========== RENDER LOD LAYER COMPLETE ==========
```

**Problem Pattern (Validation Failure):**
```
[DH-RENDER-LAYER] Validating render parameters...
[DH-RENDER-VALIDATION] ========== VALIDATION FAILED ==========
[DH-RENDER-VALIDATION] Reason: No DH Client World Loaded
```

---

#### `[DH-RENDER-PARAMS]` - Render Parameter Creation
Tracks the creation and initialization of render parameters.

**Key Log Patterns:**
```
[DH-RENDER-PARAMS] ========== CREATING RENDER PARAMS ==========
[DH-RENDER-PARAMS] renderPass: OPAQUE_AND_TRANSPARENT
[DH-RENDER-PARAMS] clientLevelWrapper: <wrapper>
[DH-RENDER-PARAMS] dhClientWorld from SharedApi: DhClientServerWorld@<hashcode>
[DH-RENDER-PARAMS] dhClientLevel: <level>
[DH-RENDER-PARAMS] renderBufferHandler: <handler>
[DH-RENDER-PARAMS] genericRenderer: <renderer>
[DH-RENDER-PARAMS] ========== RENDER PARAMS CREATED ==========
```

**Problem Pattern:**
```
[DH-RENDER-PARAMS] dhClientWorld from SharedApi: null
[DH-RENDER-PARAMS] dhClientWorld is null!
```

---

#### `[DH-RENDER-VALIDATION]` - Render Validation
**MOST IMPORTANT FOR DIAGNOSIS**: Shows exactly why rendering is failing.

**Success Pattern:**
```
[DH-RENDER-VALIDATION] Running validation checks...
[DH-RENDER-VALIDATION] All validation checks passed!
```

**Failure Pattern (The Current Problem):**
```
[DH-RENDER-VALIDATION] Running validation checks...
[DH-RENDER-VALIDATION] Failed: No DH Client World Loaded
[DH-RENDER-VALIDATION] Current abstract world: null
[DH-RENDER-VALIDATION] ========== VALIDATION FAILED ==========
[DH-RENDER-VALIDATION] Reason: No DH Client World Loaded
[DH-RENDER-VALIDATION] Current DH world: null
[DH-RENDER-VALIDATION] tryGetDhClientWorld: null
[DH-RENDER-VALIDATION] dhClientWorld from params: null
[DH-RENDER-VALIDATION] dhClientLevel from params: null
[DH-RENDER-VALIDATION] clientLevelWrapper from params: <wrapper>
[DH-RENDER-VALIDATION] ========================================
```

**Other Possible Failures:**
- `Failed: No Player Exists`
- `Failed: No DH Client Level Loaded`
- `Failed: No Client Level Wrapper Loaded`
- `Failed: No Lightmap Loaded`
- `Failed: No RenderBufferHandler Present`
- `Failed: No Generic Renderer Present`
- `Failed: No MVM or Proj Matrix Given`
- `Failed: Optifine Target Frame Buffer not set`

---

## Diagnostic Flow for Singleplayer LOD Issue

Based on the troubleshooting document, follow this diagnostic flow:

### 1. Check Initialization
Look for:
```
[DH-INIT] ========== CLIENT INITIALIZATION START ==========
...
[DH-INIT] Creating server proxy (integrated=true) and registering events...
[DH-INIT] Server proxy (integrated) events registered
```

**Expected**: Both should appear during client startup.

### 2. Check Event Registration
Look for:
```
[DH-EVENTS] ========== REGISTERING FABRIC SERVER EVENTS ==========
[DH-EVENTS] isDedicatedServer: false
[DH-EVENTS] Registering SERVER_STARTING event...
```

**Expected**: Server events should be registered with `isDedicatedServer: false`.

### 3. **CRITICAL**: Check SERVER_STARTING Event
When you join a singleplayer world, look for:
```
[DH-EVENT-FIRE] ========== SERVER_STARTING EVENT FIRED ==========
```
OR
```
[DH-EVENT-CALLBACK] ========== SERVER_STARTING CALLBACK TRIGGERED ==========
```

**PROBLEM**: According to DH-TROUBLESHOOTING.md, these logs will be **MISSING** for integrated servers. This is the root cause.

### 4. Check World Creation
If SERVER_STARTING fired, look for:
```
[DH-WORLD] ========== SERVER LOAD EVENT ==========
[DH-WORLD] Creating DhClientServerWorld (integrated server)
```

**Expected**: Should appear immediately after SERVER_STARTING callback.
**Problem**: Will be missing if SERVER_STARTING didn't fire.

### 5. Check Rendering Validation
During gameplay, look for:
```
[DH-RENDER-VALIDATION] ========== VALIDATION FAILED ==========
[DH-RENDER-VALIDATION] Reason: No DH Client World Loaded
```

**Problem**: This will appear every frame because DhClientServerWorld was never created.

---

## Summary of Expected Log Flow (Singleplayer)

### Client Startup:
1. `[DH-INIT] CLIENT INITIALIZATION START`
2. `[DH-INIT] Creating server proxy (integrated=true)`
3. `[DH-EVENTS] REGISTERING FABRIC SERVER EVENTS` with `isDedicatedServer: false`
4. `[DH-EVENT-SUB] Subscribing to SERVER_STARTING event`

### World Join (Where Problem Occurs):
5. **MISSING**: `[DH-EVENT-FIRE] SERVER_STARTING EVENT FIRED`
6. **MISSING**: `[DH-EVENT-CALLBACK] SERVER_STARTING CALLBACK TRIGGERED`
7. **MISSING**: `[DH-WORLD] Creating DhClientServerWorld`
8. **APPEARS INSTEAD**: `[DH-CLIENT-CONNECT] Not connected to dedicated server - skipping DhClientWorld creation`

### Every Frame During Rendering:
9. `[DH-RENDER-MIXIN] prepareChunkRenders() called`
10. `[DH-RENDER-LAYER] RENDER LOD LAYER START`
11. `[DH-RENDER-PARAMS] dhClientWorld from SharedApi: null`
12. `[DH-RENDER-VALIDATION] VALIDATION FAILED - No DH Client World Loaded`

---

## Next Steps for Investigation

Based on these logs, you can determine:

1. **Is the event being registered?** Check for `[DH-EVENTS]` logs
2. **Is the event firing?** Check for `[DH-EVENT-FIRE]` or `[DH-EVENT-CALLBACK]` logs
3. **Is isValidTime() blocking it?** Check for `[DH-VALIDATION] isValidTime() returning: false`
4. **Is the world being created?** Check for `[DH-WORLD] Creating DhClientServerWorld`
5. **Why is rendering failing?** Check for `[DH-RENDER-VALIDATION]` logs

The comprehensive logging will show exactly where in the initialization and rendering pipeline things are breaking down.
