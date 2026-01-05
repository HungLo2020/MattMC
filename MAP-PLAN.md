# VoxelMap Integration Plan for MattMC 1.21.10

**Project**: MattMC VoxelMap Integration  
**Date**: 2026-01-05  
**Status**: Planning Phase - Detailed Implementation Strategy

---

## Executive Summary

This document outlines a comprehensive plan to integrate VoxelMap 1.15.9 for Minecraft 1.21.10 directly into the MattMC source tree. VoxelMap is a minimap and world map mod with waypoint management, currently located in `frnsrc/VoxelMap-1.21.10/`. The integration follows the same pattern used for other mods in this project (Iris, Sodium, Distant Horizons, WorldEdit) - **no mixins, no real Fabric API, direct source integration**.

### Key Challenges
1. **Mixin Elimination**: VoxelMap has 7 mixin classes that modify Minecraft rendering
2. **Fabric API Dependencies**: VoxelMap relies on Fabric API events and HUD rendering hooks
3. **Access Wideners**: VoxelMap requires 32 access widener declarations
4. **Multi-Module Structure**: VoxelMap is split between common, fabric, and neoforge modules

### Integration Approach
- Convert all mixins to direct source modifications (following the Iris/Sodium pattern)
- Replace Fabric API event system with direct initialization hooks
- Apply access widener changes directly to Minecraft source classes
- Merge common and fabric modules into unified source in `src/main/java`

---

## Table of Contents

1. [VoxelMap Architecture Analysis](#1-voxelmap-architecture-analysis)
2. [MattMC Integration Context](#2-mattmc-integration-context)
3. [Mixin Elimination Strategy](#3-mixin-elimination-strategy)
4. [Fabric API Replacement Strategy](#4-fabric-api-replacement-strategy)
5. [Access Widener Integration](#5-access-widener-integration)
6. [Source Code Migration Plan](#6-source-code-migration-plan)
7. [Resource and Asset Migration](#7-resource-and-asset-migration)
8. [Initialization and Lifecycle](#8-initialization-and-lifecycle)
9. [Testing and Validation Strategy](#9-testing-and-validation-strategy)
10. [Implementation Phases](#10-implementation-phases)

---

## 1. VoxelMap Architecture Analysis

### 1.1 Module Structure

**Common Module** (`common/src/main/java`)
- 120 Java files
- Core VoxelMap logic, rendering, and data management
- Location: `com.mamiyaotaru.voxelmap.*`
- **Independent of Fabric/Forge** - uses abstraction layer

**Fabric Module** (`fabric/src/main/java`)
- 7 Java files
- Fabric-specific initialization and event handling
- Location: `com.mamiyaotaru.voxelmap.fabric.*`
- **Fabric API dependent** - needs replacement

**Key Packages**:
```
com.mamiyaotaru.voxelmap/
├── VoxelMap.java              (Core singleton)
├── VoxelConstants.java        (Static utilities, abstraction layer)
├── Map.java                   (Minimap renderer)
├── Radar.java                 (Entity radar)
├── WaypointManager.java       (Waypoint management)
├── ColorManager.java          (Block color management)
├── MapSettingsManager.java    (Configuration)
├── RadarSettingsManager.java  (Radar configuration)
├── gui/                       (17 GUI classes)
├── persistent/                (11 persistent map classes)
├── util/                      (42 utility classes)
├── mixins/                    (7 mixin classes - TO BE ELIMINATED)
├── interfaces/                (5 interface definitions)
├── packets/                   (4 packet handlers)
├── entityrender/              (6 entity rendering classes)
└── textures/                  (6 texture management classes)
```

### 1.2 Mixin Analysis

VoxelMap uses 7 mixins to inject into Minecraft's rendering pipeline:

| Mixin Class | Target Class | Purpose | Integration Strategy |
|-------------|--------------|---------|---------------------|
| **MixinInGameHud** | `net.minecraft.client.gui.Gui` | Move scoreboard for minimap | Direct modification |
| **MixinWorldRenderer** | `net.minecraft.client.renderer.LevelRenderer` | Render waypoint beacons in world | Direct hook addition |
| **MixinChatHud** | `net.minecraft.client.gui.components.ChatComponent` | Adjust chat position for minimap | Direct modification |
| **APIMixinMinecraftClient** | `net.minecraft.client.Minecraft` | Track dimension changes | Direct field/method addition |
| **APIMixinNetHandlerPlayClient** | `net.minecraft.client.multiplayer.ClientPacketListener` | Intercept network packets | Direct hook addition |
| **APIMixinChatListenerHud** | `net.minecraft.client.gui.components.ChatListener` | Filter chat messages | Direct modification |
| **AccessorEnderDragonRenderer** | `net.minecraft.client.renderer.entity.EnderDragonRenderer` | Access private fields | Make fields accessible |

**Fabric-specific Mixin**:
- **MixinRenderPipelines** (`fabric` module only): Injects into render pipeline creation - can be replaced with direct modification

### 1.3 Fabric API Dependencies

VoxelMap uses these Fabric API features:

```java
// Event Registration (FabricEvents.java)
ClientLifecycleEvents.CLIENT_STOPPING
ClientPlayConnectionEvents.DISCONNECT
ClientPlayConnectionEvents.INIT
ClientPlayConnectionEvents.JOIN
ClientConfigurationConnectionEvents.INIT

// HUD Rendering (FabricEvents.java)
HudElementRegistry.attachElementAfter(...)
HudElement.render(GuiGraphics, DeltaTracker)

// Network Packets (FabricPacketBridge.java, VoxelmapSettingsChannelHandler.java)
ClientPlayNetworking.registerGlobalReceiver(...)
ClientConfigurationNetworking.registerGlobalReceiver(...)
```

**Replacement Strategy**: All Fabric API events can be replaced with direct method calls at appropriate lifecycle points in Minecraft's client code.

### 1.4 Access Widener Requirements

VoxelMap requires 32 access widener declarations from `voxelmap.accesswidener`:

**Categories**:
- **Biome Access**: 2 declarations (climate settings access)
- **GUI Components**: 2 declarations (selection list, scissors stack)
- **Entity Models**: 14 declarations (head/body parts for various mobs)
- **Client Rendering**: 9 declarations (render types, pipelines, fog renderer)
- **Input/Options**: 1 declaration (key mappings array)
- **World/Level**: 1 declaration (biome zoom seed)

**Integration Strategy**: Apply these access changes directly to the Minecraft source files by changing visibility modifiers.

### 1.5 Resource Files

**Assets** (`common/src/main/resources/assets/voxelmap/`):
- Images: 40+ PNG files (waypoint icons, radar icons, UI elements)
- Configuration: `biomecolors.txt` (biome color mappings)
- Icon: `icon.png` (mod icon)

**Configuration Files**:
- `mixin.voxelmap.json` - Common mixin config (DELETE after integration)
- `mixin.voxelmap.fabric.json` - Fabric mixin config (DELETE after integration)
- `voxelmap.accesswidener` - Access widener (APPLY to source, then delete)
- `fabric.mod.json` - Fabric mod metadata (DELETE after integration)

---

## 2. MattMC Integration Context

### 2.1 Project Architecture

**MattMC is a unified source build** with all mods integrated into a single compilation unit:

```
MattMC/
├── src/main/java/
│   ├── net/minecraft/        (Minecraft 1.21.10 source)
│   ├── com/mojang/           (Mojang libraries)
│   ├── net/fabricmc/         (Fabric Loader + minimal API stubs)
│   ├── net/irisshaders/      (Iris shader mod - integrated)
│   ├── net/caffeinemc/       (Sodium renderer - integrated)
│   ├── com/seibel/           (Distant Horizons - integrated)
│   └── [TO ADD] com/mamiyaotaru/voxelmap/
├── src/main/resources/
│   └── assets/
│       └── [TO ADD] voxelmap/
└── frnsrc/                   (Reference source - NOT compiled)
    ├── VoxelMap-1.21.10/
    ├── WorldEdit-version-7.3.x/
    ├── fabric-1.21.10/
    └── dwarfhollow-v0.2-by-kanokarob/
```

### 2.2 No Mixin System

**Key Fact**: MattMC does NOT use mixins at runtime. All mod integrations are done through:
1. **Direct source code modifications** (e.g., Iris adds hooks directly into shader compilation)
2. **Direct field/method additions** to Minecraft classes
3. **Interface implementations** on existing classes

**Evidence**:
- `FabricMixinBootstrap.java` exists but mixin application is bypassed
- Comments in source: `"// Removed unused mixinextras import - mixin system bypassed"`
- Iris uses `mixinterface` pattern for shader customization points

**Pattern to Follow**:
```java
// Instead of @Mixin + @Inject
// Add direct method calls in Minecraft source:

// In net.minecraft.client.gui.Gui.java:
public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
    // ... existing code ...
    
    // VoxelMap minimap rendering
    com.mamiyaotaru.voxelmap.VoxelConstants.renderOverlay(graphics);
    
    // ... rest of code ...
}
```

### 2.3 Limited Fabric API

MattMC has **minimal Fabric API stubs** in `src/main/java/net/fabricmc/api/`:
- `ClientModInitializer.java` - Interface for client mod initialization
- `Environment.java` / `EnvType.java` - Environment annotations
- `ModInitializer.java` - Interface for mod initialization

**What's Missing** (that VoxelMap needs):
- `net.fabricmc.fabric.api.client.event.lifecycle.v1.*` - Lifecycle events
- `net.fabricmc.fabric.api.client.rendering.v1.hud.*` - HUD element registration
- `net.fabricmc.fabric.api.client.networking.v1.*` - Network packet handling

**Solution**: Create minimal stub interfaces OR replace with direct initialization calls.

### 2.4 Successful Integration Examples

**Iris Shaders Integration**:
- Uses `mixinterface` pattern: interfaces like `CustomPass`, `ShaderInstanceInterface`
- Direct modifications to `GlProgram.java`, `GlRenderPass.java`, etc.
- NO mixin files in `src/main/java`

**Sodium Integration**:
- Comment in source: `"// Removed unused mixinextras import - mixin system bypassed"`
- Direct integration into Minecraft's chunk rendering

**Distant Horizons Integration**:
- Uses `IMixinServerPlayer` interface but applied directly to source
- No actual mixin application at runtime

**WorldEdit Integration** (from IMPLEMENTATION_STATUS.md):
- 42 Java classes integrated directly
- Commands registered via direct server hooks
- Zero mixin usage

---

## 3. Mixin Elimination Strategy

### 3.1 General Approach

For each mixin, we will:
1. **Identify the target method** in Minecraft source
2. **Add direct method calls** at injection points
3. **Make fields/methods accessible** as needed
4. **Delete the mixin class** after integration

### 3.2 Mixin-by-Mixin Conversion Plan

#### 3.2.1 MixinInGameHud

**Original Mixin**:
```java
@Mixin(Gui.class)
public class MixinInGameHud {
    @ModifyVariable(method = "displayScoreboardSidebar(...)")
    private int injected(int bottomX, @Local int entriesHeight) {
        return VoxelConstants.moveScoreboard(bottomX, entriesHeight);
    }
}
```

**Target File**: `src/main/java/net/minecraft/client/gui/Gui.java`

**Integration Steps**:
1. Open `Gui.java`, locate method `displayScoreboardSidebar(GuiGraphics, Objective)`
2. Find the line: `int o = guiGraphics.guiHeight() / 2 + n / 3;` (where `n` is entries height)
3. Replace with:
   ```java
   int o = com.mamiyaotaru.voxelmap.VoxelConstants.moveScoreboard(
       guiGraphics.guiHeight() / 2 + n / 3, 
       n
   );
   ```
4. Delete `MixinInGameHud.java`

**Impact**: Adjusts scoreboard Y position to avoid overlap with minimap

---

#### 3.2.2 MixinWorldRenderer

**Purpose**: Render waypoint beacons in the 3D world

**Target File**: `src/main/java/net/minecraft/client/renderer/LevelRenderer.java`

**Integration Steps**:
1. Locate the method `renderLevel(...)` or similar world rendering method
2. After world geometry rendering, add:
   ```java
   // VoxelMap: Render waypoint beacons
   com.mamiyaotaru.voxelmap.VoxelConstants.renderWorldWaypoints(
       poseStack, 
       bufferSource, 
       camera, 
       partialTick
   );
   ```
3. Delete `MixinWorldRenderer.java`

**Impact**: Renders waypoint beams/markers visible through terrain

---

#### 3.2.3 MixinChatHud

**Purpose**: Adjust chat component position to avoid minimap overlap

**Target File**: `src/main/java/net/minecraft/client/gui/components/ChatComponent.java`

**Integration Steps**:
1. Locate chat rendering method (likely in `render()` or position calculation)
2. Add hook to adjust Y position based on minimap configuration
3. May need to add field for position offset
4. Delete `MixinChatHud.java`

**Impact**: Moves chat up/down based on minimap position settings

---

#### 3.2.4 APIMixinMinecraftClient

**Purpose**: Track world/dimension changes for VoxelMap

**Target File**: `src/main/java/net/minecraft/client/Minecraft.java`

**Integration Steps**:
1. Locate world change/join methods (`setLevel()`, etc.)
2. Add direct calls:
   ```java
   if (this.level != newLevel) {
       com.mamiyaotaru.voxelmap.VoxelConstants.onWorldChange(newLevel);
   }
   ```
3. Consider adding interface `IVoxelMapMinecraft` for cleaner separation
4. Delete `APIMixinMinecraftClient.java`

**Impact**: Notifies VoxelMap when player changes dimensions (Overworld ↔ Nether ↔ End)

---

#### 3.2.5 APIMixinNetHandlerPlayClient

**Purpose**: Intercept network packets for server→client communication

**Target File**: `src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java`

**Integration Steps**:
1. Locate packet handling methods
2. Add hooks for custom VoxelMap packets:
   ```java
   if (packet instanceof CustomPayloadPacket customPayload) {
       if (com.mamiyaotaru.voxelmap.VoxelConstants.handleCustomPacket(customPayload)) {
           return; // Packet handled by VoxelMap
       }
   }
   ```
3. Delete `APIMixinNetHandlerPlayClient.java`

**Impact**: Allows server to send waypoint data, world IDs to client

---

#### 3.2.6 APIMixinChatListenerHud

**Purpose**: Filter/process chat messages for commands

**Target File**: `src/main/java/net/minecraft/client/gui/components/ChatListener.java`

**Integration Steps**:
1. Locate chat message processing
2. Add VoxelMap message handler hook before/after vanilla processing
3. Delete `APIMixinChatListenerHud.java`

**Impact**: Allows VoxelMap to process commands like `/newWaypoint`

---

#### 3.2.7 AccessorEnderDragonRenderer

**Purpose**: Access private fields in ender dragon renderer

**Target File**: `src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java`

**Integration Steps**:
1. Make accessed fields public or package-private
2. This is effectively an access widener, just apply visibility change
3. Delete `AccessorEnderDragonRenderer.java`

**Impact**: Allows VoxelMap to render dragon head correctly on radar

---

#### 3.2.8 MixinRenderPipelines (Fabric-specific)

**Purpose**: Inject custom render pipeline for VoxelMap UI

**Target File**: `src/main/java/net/minecraft/client/renderer/RenderPipelines.java`

**Integration Steps**:
1. Locate pipeline initialization
2. Add VoxelMap pipeline registration:
   ```java
   com.mamiyaotaru.voxelmap.util.VoxelMapPipelines.register();
   ```
3. Delete `MixinRenderPipelines.java` from fabric module

**Impact**: Registers custom shaders/render states for map rendering

---

### 3.3 Mixin Deletion Checklist

After all integrations:
- [ ] Delete `frnsrc/VoxelMap-1.21.10/common/src/main/java/com/mamiyaotaru/voxelmap/mixins/` (entire folder)
- [ ] Delete `frnsrc/VoxelMap-1.21.10/fabric/src/main/java/com/mamiyaotaru/voxelmap/fabric/mixins/` (entire folder)
- [ ] Delete `common/src/main/resources/mixin.voxelmap.json`
- [ ] Delete `fabric/src/main/resources/mixin.voxelmap.fabric.json`
- [ ] Remove mixin references from VoxelMap code (if any)

---

## 4. Fabric API Replacement Strategy

### 4.1 Event System Replacement

VoxelMap uses Fabric lifecycle events in `FabricEvents.java`:

**Original Fabric Events**:
```java
ClientLifecycleEvents.CLIENT_STOPPING.register(client -> map.onClientStopping());
ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> map.onDisconnect());
ClientConfigurationConnectionEvents.INIT.register((handler, client) -> map.onConfigurationInit());
ClientPlayConnectionEvents.INIT.register((handler, client) -> map.onPlayInit());
ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> map.onJoinServer());
```

**Replacement Strategy**:

Create `VoxelMapClientHooks.java` in Minecraft client code:

```java
// In src/main/java/net/minecraft/client/VoxelMapClientHooks.java
package net.minecraft.client;

import com.mamiyaotaru.voxelmap.VoxelConstants;

public class VoxelMapClientHooks {
    public static void onClientStopping() {
        VoxelConstants.getVoxelMapInstance().onClientStopping();
    }
    
    public static void onDisconnect() {
        VoxelConstants.getVoxelMapInstance().onDisconnect();
    }
    
    public static void onConfigurationInit() {
        VoxelConstants.getVoxelMapInstance().onConfigurationInit();
    }
    
    public static void onPlayInit() {
        VoxelConstants.getVoxelMapInstance().onPlayInit();
    }
    
    public static void onJoinServer() {
        VoxelConstants.getVoxelMapInstance().onJoinServer();
    }
}
```

**Integration Points**:

| Event | Target File | Method | Hook Location |
|-------|-------------|--------|---------------|
| CLIENT_STOPPING | `Minecraft.java` | `stop()` | End of method |
| DISCONNECT | `ClientPacketListener.java` | `onDisconnect()` | In disconnect handler |
| CONFIGURATION_INIT | `ClientPacketListener.java` | Constructor or init | After configuration setup |
| PLAY_INIT | `ClientPacketListener.java` | Constructor or init | After play setup |
| JOIN | `ClientPacketListener.java` | `handleLogin()` | After successful join |

### 4.2 HUD Rendering Replacement

**Original Fabric HUD System**:
```java
ResourceLocation voxelMapMinimapLayer = ResourceLocation.parse("voxelmap:minimap");
HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, voxelMapMinimapLayer, new HudElement() {
    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        VoxelConstants.renderOverlay(context);
    }
});
```

**Replacement**: Direct call in `Gui.java` (already has example from other mods)

```java
// In src/main/java/net/minecraft/client/gui/Gui.java
public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
    // ... existing HUD rendering ...
    
    // VoxelMap minimap overlay (rendered after boss bar, before chat)
    com.mamiyaotaru.voxelmap.VoxelConstants.renderOverlay(guiGraphics);
    
    // ... rest of HUD ...
}
```

**Ordering**: Insert after boss bar rendering, before debug screen/chat.

### 4.3 Network Packet Replacement

**Original Fabric Networking**:
```java
// VoxelmapSettingsChannelHandler.java
ClientConfigurationNetworking.registerGlobalReceiver(
    CHANNEL, 
    (client, handler, buf, responseSender) -> { ... }
);

// VoxelmapWorldIdChannelHandler.java
ClientPlayNetworking.registerGlobalReceiver(
    CHANNEL, 
    (client, handler, buf, responseSender) -> { ... }
);
```

**Replacement Strategy**:

1. **Create packet classes** in `com.mamiyaotaru.voxelmap.packets/` (already exist)
2. **Register with Minecraft's packet system** directly in `ClientPacketListener.java`
3. **Add handler in packet processing** pipeline

Example:
```java
// In ClientPacketListener.java, packet handling method:
if (packet instanceof ClientboundCustomPayloadPacket customPayload) {
    ResourceLocation channel = customPayload.type().id();
    
    if (channel.equals(ResourceLocation.parse("voxelmap:settings"))) {
        com.mamiyaotaru.voxelmap.VoxelConstants.handleSettingsPacket(customPayload);
        return;
    }
    
    if (channel.equals(ResourceLocation.parse("voxelmap:worldid"))) {
        com.mamiyaotaru.voxelmap.VoxelConstants.handleWorldIdPacket(customPayload);
        return;
    }
}
```

### 4.4 Fabric API Stub Creation (Alternative)

**If direct replacement is complex**, create minimal stubs:

```java
// src/main/java/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientLifecycleEvents.java
package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;

public class ClientLifecycleEvents {
    public interface ClientStopping {
        void onClientStopping(Minecraft client);
    }
    
    public static final Event<ClientStopping> CLIENT_STOPPING = new Event<>();
    
    // Simple event implementation
    public static class Event<T> {
        private final List<T> listeners = new ArrayList<>();
        
        public void register(T listener) {
            listeners.add(listener);
        }
        
        public void invoker() {
            return (T) listeners; // Simplified
        }
    }
}
```

Then call event invokers from integration points.

**Recommendation**: Use **direct replacement** (4.1-4.3) for cleaner code, stubs only if needed.

---

## 5. Access Widener Integration

### 5.1 Access Widener File Analysis

From `voxelmap.accesswidener`:

```
accessWidener   v2  named

# Biome access (2)
accessible class net/minecraft/world/level/biome/Biome$ClimateSettings
accessible field net/minecraft/world/level/biome/Biome climateSettings

# GUI (2)
accessible class net/minecraft/client/gui/components/AbstractSelectionList$Entry
accessible class net/minecraft/client/gui/GuiGraphics$ScissorStack
accessible field net/minecraft/client/gui/GuiGraphics scissorStack

# Entity models (14)
accessible field net/minecraft/client/model/WolfModel head
accessible field net/minecraft/client/model/QuadrupedModel head
accessible field net/minecraft/client/model/LavaSlimeModel bodyCubes
# ... (11 more model fields)

# Rendering (9)
accessible field net/minecraft/client/Options keyMappings
accessible field net/minecraft/client/renderer/RenderPipelines GUI_TEXTURED_SNIPPET
accessible method net/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder setTextureState(...)
# ... (6 more rendering access wideners)

# World (1)
accessible field net/minecraft/world/level/biome/BiomeManager biomeZoomSeed
```

### 5.2 Application Strategy

**For each access widener**:
1. Locate the class/field/method in `src/main/java`
2. Change visibility:
   - `private` → `public` (or `protected` if appropriate)
   - `package-private` → `public`
3. Document change with comment: `// VoxelMap: Made accessible`

**Example Changes**:

```java
// In src/main/java/net/minecraft/world/level/biome/Biome.java
public static class ClimateSettings {  // Changed from package-private
    // VoxelMap: Made accessible
    // ...
}

// In src/main/java/net/minecraft/world/level/biome/Biome.java
public final ClimateSettings climateSettings;  // Changed from private
// VoxelMap: Made accessible
```

### 5.3 Access Widener Application Checklist

**Biome Package**:
- [ ] Make `Biome$ClimateSettings` class public
- [ ] Make `Biome.climateSettings` field public

**GUI Package**:
- [ ] Make `AbstractSelectionList$Entry` class public
- [ ] Make `GuiGraphics$ScissorStack` class public
- [ ] Make `GuiGraphics.scissorStack` field public

**Model Package** (14 changes):
- [ ] Make `WolfModel.head` field public
- [ ] Make `QuadrupedModel.head` field public
- [ ] Make `LavaSlimeModel.bodyCubes` field public
- [ ] Make `FelineModel.head` field public
- [ ] Make `HoglinModel.head` field public
- [ ] Make `ChickenModel.head` field public
- [ ] Make `BeeModel.bone` field public
- [ ] Make `AxolotlModel.head` field public
- [ ] Make `ModelPart.children` field public
- [ ] Make `SkullModel.head` field public
- [ ] Make `ShulkerModel.head` field public
- [ ] Make `AbstractEquineModel.headParts` field public
- [ ] Make `SlimeOuterLayer.model` field public
- [ ] Make `LivingEntityRenderer.layers` field public

**Renderer Package** (9 changes):
- [ ] Make `Options.keyMappings` field mutable (already might be)
- [ ] Make `RenderPipelines.GUI_TEXTURED_SNIPPET` field accessible
- [ ] Make `RenderType$CompositeState$CompositeStateBuilder.setTextureState()` accessible
- [ ] Make `RenderType$CompositeState$CompositeStateBuilder.createCompositeState()` accessible
- [ ] Make `GameRenderer.fogRenderer` field accessible
- [ ] Make `GuiGraphics.guiRenderState` field accessible
- [ ] Make `FogRenderer.computeFogColor()` method accessible

**World Package**:
- [ ] Make `BiomeManager.biomeZoomSeed` field public

### 5.4 Verification

After applying all access wideners:
- [ ] Run `./gradlew compileJava` to verify no compilation errors
- [ ] Delete `voxelmap.accesswidener` file
- [ ] VoxelMap code should compile without access errors

---

## 6. Source Code Migration Plan

### 6.1 Directory Structure

**Source Modules to Merge**:
```
frnsrc/VoxelMap-1.21.10/
├── common/src/main/java/com/mamiyaotaru/voxelmap/  → MOVE to src/main/java
└── fabric/src/main/java/com/mamiyaotaru/voxelmap/fabric/  → MERGE into src/main/java
```

**Destination**:
```
src/main/java/com/mamiyaotaru/voxelmap/
├── VoxelMap.java
├── VoxelConstants.java
├── Map.java
├── Radar.java
├── [... all 120 common files ...]
├── [... 7 fabric files merged/adapted ...]
└── [NO mixin files]
```

### 6.2 File-by-File Migration Strategy

#### 6.2.1 Common Module Files (120 files)

**Package**: `com.mamiyaotaru.voxelmap.*`

**Migration Steps**:
1. Copy all files from `frnsrc/VoxelMap-1.21.10/common/src/main/java/com/mamiyaotaru/voxelmap/` to `src/main/java/com/mamiyaotaru/voxelmap/`
2. **EXCLUDE** the `mixins/` folder (7 files) - these will be deleted
3. Review imports for Fabric dependencies - most should be fine as VoxelMap common is platform-agnostic

**Files to Copy** (excluding mixins):
```
✓ VoxelMap.java
✓ VoxelConstants.java
✓ Map.java
✓ Radar.java
✓ RadarSimple.java
✓ WaypointManager.java
✓ MapSettingsManager.java
✓ RadarSettingsManager.java
✓ ColorManager.java
✓ Events.java (interface)
✓ ModApiBridge.java (interface)
✓ PacketBridge.java (interface)
✓ SettingsAndLightingChangeNotifier.java
✓ DebugRenderState.java

✓ gui/ (17 files - all GUI screens)
✓ persistent/ (11 files - map storage)
✓ util/ (42 files - utilities)
✓ interfaces/ (5 files)
✓ packets/ (4 files)
✓ entityrender/ (6 files)
✓ textures/ (6 files)
```

**Total**: 113 files to copy directly

#### 6.2.2 Fabric Module Files (7 files)

**Package**: `com.mamiyaotaru.voxelmap.fabric.*`

These files implement Fabric-specific functionality and need adaptation:

| File | Purpose | Migration Strategy |
|------|---------|-------------------|
| **VoxelmapFabricMod.java** | Fabric mod initializer | Replace with direct initialization in `Minecraft.java` |
| **FabricEvents.java** | Event registration | Delete - use direct hooks (Section 4.1) |
| **FabricPacketBridge.java** | Packet handling | Adapt to Minecraft's packet system |
| **FabricModApiBridge.java** | Mod API bridge | Review and possibly stub out |
| **VoxelmapSettingsChannelHandler.java** | Settings packet handler | Adapt packet registration |
| **VoxelmapWorldIdChannelHandler.java** | World ID packet handler | Adapt packet registration |
| **MixinRenderPipelines.java** | Render pipeline mixin | Convert to direct registration |

**Adaptation Strategy**:

1. **VoxelmapFabricMod.java** → Delete, move initialization to:
   ```java
   // In Minecraft.java constructor or init method:
   public Minecraft(...) {
       // ... existing code ...
       
       // Initialize VoxelMap
       com.mamiyaotaru.voxelmap.VoxelMapInitializer.initialize();
   }
   ```

2. **Create VoxelMapInitializer.java**:
   ```java
   package com.mamiyaotaru.voxelmap;
   
   public class VoxelMapInitializer {
       private static boolean initialized = false;
       
       public static void initialize() {
           if (initialized) return;
           initialized = true;
           
           // Original FabricMod.onInitializeClient() code:
           new VoxelmapSettingsChannelHandler();  // Adapt this
           new VoxelmapWorldIdChannelHandler();   // Adapt this
           VoxelConstants.setEvents(new DirectEvents());  // New implementation
           VoxelConstants.setPacketBridge(new DirectPacketBridge());  // New implementation
           VoxelConstants.setModApiBridge(new DirectModApiBridge());  // New implementation
       }
   }
   ```

3. **FabricEvents.java** → Create `DirectEvents.java`:
   ```java
   package com.mamiyaotaru.voxelmap;
   
   public class DirectEvents implements Events {
       @Override
       public void initEvents(VoxelMap map) {
           // Events are handled via direct hooks in Minecraft classes
           // This is just a no-op implementation
       }
   }
   ```

4. **FabricPacketBridge.java** → Create `DirectPacketBridge.java`:
   - Implement packet sending using Minecraft's native packet system
   - Register custom packet types

5. **Packet Handlers** → Integrate into `ClientPacketListener.java`:
   - Move channel registration to Minecraft's packet init
   - Keep packet processing logic

#### 6.2.3 Files to Delete

After migration:
- [ ] `frnsrc/VoxelMap-1.21.10/common/src/main/java/com/mamiyaotaru/voxelmap/mixins/` (entire folder)
- [ ] `frnsrc/VoxelMap-1.21.10/fabric/src/main/java/com/mamiyaotaru/voxelmap/fabric/mixins/` (entire folder)
- [ ] `frnsrc/VoxelMap-1.21.10/fabric/src/main/java/com/mamiyaotaru/voxelmap/fabric/FabricEvents.java` (replaced)
- [ ] `frnsrc/VoxelMap-1.21.10/fabric/src/main/java/com/mamiyaotaru/voxelmap/fabric/VoxelmapFabricMod.java` (replaced)

### 6.3 Import Resolution

**Common Import Issues**:

1. **Mixin imports**: Delete any remaining `import org.spongepowered.asm.mixin.*`
2. **Fabric API imports**: Replace with stubs or direct Minecraft calls
3. **Access widener references**: Should work after applying access wideners

**Verification**:
```bash
# Check for problematic imports
grep -r "import org.spongepowered.asm.mixin" src/main/java/com/mamiyaotaru/voxelmap/
# Should return nothing

grep -r "import net.fabricmc.fabric.api" src/main/java/com/mamiyaotaru/voxelmap/
# Should only find minimal stub references
```

### 6.4 Package Verification Checklist

After migration, verify:
- [ ] All 113 common files copied to `src/main/java/com/mamiyaotaru/voxelmap/`
- [ ] No `mixins/` folder in `src/main/java/com/mamiyaotaru/voxelmap/`
- [ ] Fabric-specific files adapted and placed appropriately
- [ ] New initialization files created (`VoxelMapInitializer.java`, `DirectEvents.java`, etc.)
- [ ] No compilation errors: `./gradlew compileJava`

---

## 7. Resource and Asset Migration

### 7.1 Asset Files

**Source**: `frnsrc/VoxelMap-1.21.10/common/src/main/resources/assets/voxelmap/`

**Destination**: `src/main/resources/assets/voxelmap/`

**Contents**:
```
assets/voxelmap/
├── conf/
│   └── biomecolors.txt          (Biome color configuration)
├── images/
│   ├── circle.png               (UI elements)
│   ├── colorpicker.png
│   ├── square.png
│   ├── roundmap.png
│   ├── squaremap.png
│   ├── radar/                   (7 radar icon PNGs)
│   │   ├── contact.png
│   │   ├── contact_facing.png
│   │   ├── glow.png
│   │   ├── hostile.png
│   │   ├── neutral.png
│   │   ├── solid.png
│   │   └── tame.png
│   └── waypoints/               (30+ waypoint icon PNGs)
│       ├── waypointaxe.png
│       ├── waypointskull.png
│       ├── target.png
│       └── ... (more icons)
└── icon.png                     (Mod icon - not needed in integration)
```

**Migration Steps**:
1. Copy entire `assets/voxelmap/` folder to `src/main/resources/assets/voxelmap/`
2. **EXCLUDE**: `icon.png` (only used for mod listing)
3. Verify file count: ~40 files total

**Resource Loading**:
VoxelMap uses `ResourceLocation.parse("voxelmap:images/...")` which will work automatically after assets are in place.

### 7.2 Configuration Files to Delete

**DO NOT COPY** these files (Fabric/mixin metadata):
- `fabric.mod.json`
- `pack.mcmeta`
- `mixin.voxelmap.json`
- `mixin.voxelmap.fabric.json`
- `voxelmap.accesswidener`

These are only needed for standalone mod loading and are not relevant for integrated builds.

### 7.3 Verification

After migration:
```bash
# Verify assets are in place
ls -R src/main/resources/assets/voxelmap/

# Expected output:
# conf/biomecolors.txt
# images/circle.png, colorpicker.png, square.png, roundmap.png, squaremap.png
# images/radar/[7 files]
# images/waypoints/[30+ files]
```

---

## 8. Initialization and Lifecycle

### 8.1 Initialization Flow

**Original Flow** (Fabric mod):
```
1. Fabric Loader calls VoxelmapFabricMod.onInitializeClient()
2. VoxelmapFabricMod registers event handlers
3. FabricEvents registers HUD overlay
4. ClientLifecycleEvents.CLIENT_STARTING triggers VoxelMap.lateInit()
```

**New Flow** (Direct integration):
```
1. Minecraft.java constructor/init calls VoxelMapInitializer.initialize()
2. VoxelMapInitializer sets up VoxelConstants abstraction layer
3. VoxelMap.lateInit() called after client resources loaded
4. Direct hooks in Minecraft classes call VoxelMap methods
```

### 8.2 Initialization Code

**Create**: `src/main/java/com/mamiyaotaru/voxelmap/VoxelMapInitializer.java`

```java
package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.BiomeRepository;
import net.minecraft.client.Minecraft;

public class VoxelMapInitializer {
    private static boolean initialized = false;
    private static VoxelMap voxelMapInstance;
    
    public static void initialize() {
        if (initialized) {
            VoxelConstants.getLogger().warn("VoxelMap already initialized!");
            return;
        }
        
        VoxelConstants.getLogger().info("Initializing VoxelMap...");
        
        // Set up abstraction layer
        VoxelConstants.setEvents(new DirectEvents());
        VoxelConstants.setPacketBridge(new DirectPacketBridge());
        VoxelConstants.setModApiBridge(new DirectModApiBridge());
        
        // Register packet handlers
        VoxelmapPacketRegistry.registerClientPackets();
        
        initialized = true;
        VoxelConstants.getLogger().info("VoxelMap initialization complete");
    }
    
    public static void lateInit() {
        if (voxelMapInstance != null) return;
        
        VoxelConstants.getLogger().info("VoxelMap late initialization...");
        
        // Create VoxelMap instance and initialize
        voxelMapInstance = VoxelConstants.getVoxelMapInstance();
        voxelMapInstance.lateInit(
            false,  // showUnderMenus - can be configured
            false   // isFair - can be configured
        );
        
        VoxelConstants.getLogger().info("VoxelMap fully initialized");
    }
    
    public static VoxelMap getVoxelMapInstance() {
        return voxelMapInstance;
    }
}
```

### 8.3 Integration Points in Minecraft

**In `net.minecraft.client.Minecraft.java`**:

```java
public Minecraft(...) {
    // ... existing constructor code ...
    
    // Initialize VoxelMap early
    com.mamiyaotaru.voxelmap.VoxelMapInitializer.initialize();
    
    // ... rest of constructor ...
}

// After resources loaded:
private void finishInitialization() {
    // ... existing code ...
    
    // Late initialization for VoxelMap
    com.mamiyaotaru.voxelmap.VoxelMapInitializer.lateInit();
}

public void stop() {
    // ... existing shutdown code ...
    
    // VoxelMap cleanup
    com.mamiyaotaru.voxelmap.VoxelMapClientHooks.onClientStopping();
    
    // ... rest of shutdown ...
}
```

### 8.4 Lifecycle Hook Summary

| Lifecycle Event | Minecraft Hook Location | VoxelMap Handler |
|----------------|------------------------|------------------|
| **Mod Init** | `Minecraft.java` constructor | `VoxelMapInitializer.initialize()` |
| **Late Init** | `Minecraft.finishInitialization()` | `VoxelMapInitializer.lateInit()` |
| **World Join** | `ClientPacketListener.handleLogin()` | `VoxelMapClientHooks.onJoinServer()` |
| **World Change** | `Minecraft.setLevel()` | `VoxelMapClientHooks.onWorldChange()` |
| **Disconnect** | `ClientPacketListener.onDisconnect()` | `VoxelMapClientHooks.onDisconnect()` |
| **Client Stop** | `Minecraft.stop()` | `VoxelMapClientHooks.onClientStopping()` |
| **HUD Render** | `Gui.render()` | `VoxelConstants.renderOverlay()` |
| **World Render** | `LevelRenderer.renderLevel()` | `VoxelConstants.renderWorldWaypoints()` |

---

## 9. Testing and Validation Strategy

### 9.1 Compilation Testing

**Step 1**: Verify compilation after each phase
```bash
./gradlew compileJava
```

**Expected Issues During Migration**:
- Missing access wideners → Apply access widener changes
- Mixin references → Remove or convert to direct calls
- Fabric API references → Replace with stubs or direct calls

### 9.2 Runtime Testing

**Minimal Test** (after each phase):
1. Run client: `./gradlew runClient`
2. Verify VoxelMap initialization logs appear
3. Check for crash/errors in console

**Full Feature Tests**:

| Feature | Test Procedure | Success Criteria |
|---------|----------------|------------------|
| **Minimap Display** | Launch game, create world | Minimap visible in corner |
| **Waypoint Creation** | Press waypoint key, create waypoint | Waypoint appears on map |
| **World Map** | Press M (or configured key) | Full-screen map opens |
| **Radar** | Enable radar, spawn mobs | Entities appear on radar |
| **Map Persistence** | Create waypoints, quit, rejoin | Waypoints persist |
| **Multi-dimension** | Go to Nether/End | Separate maps for each dimension |
| **Zoom** | Mouse wheel on map | Zoom in/out works |
| **Scoreboard Position** | Open scoreboard (tab) | Doesn't overlap minimap |
| **Chat Position** | Send chat message | Chat doesn't overlap minimap |

### 9.3 Regression Testing

**Verify No Breaks**:
- [ ] Minecraft client launches
- [ ] Existing mods still work (Iris, Sodium, Distant Horizons, WorldEdit)
- [ ] Vanilla features unaffected (GUI, rendering, etc.)
- [ ] Performance is acceptable (no major FPS drops)

### 9.4 Debug Mode

VoxelMap has debug mode in `VoxelConstants.DEBUG`. 

**Enable for testing**:
```java
// In VoxelConstants.java
public static final boolean DEBUG = true;  // Change from false
```

This enables verbose logging for troubleshooting.

---

## 10. Implementation Phases

### Phase 1: Preparation and Planning ✅
**Status**: Complete (this document)

- [x] Research VoxelMap source code structure
- [x] Analyze mixin usage and conversion strategy
- [x] Document Fabric API dependencies
- [x] Create comprehensive migration plan

**Deliverable**: This MAP-PLAN.md document

---

### Phase 2: Access Widener Application
**Estimated Time**: 1-2 hours

**Steps**:
1. [ ] Apply all 32 access widener changes to Minecraft source files
2. [ ] Document each change with `// VoxelMap: Made accessible` comments
3. [ ] Compile and verify no errors: `./gradlew compileJava`
4. [ ] Delete `voxelmap.accesswidener` file

**Success Criteria**: All access widener targets are now accessible, compilation succeeds

---

### Phase 3: Source Code Migration (No Mixins)
**Estimated Time**: 30 minutes

**Steps**:
1. [ ] Copy all 113 files from `common/src/main/java/com/mamiyaotaru/voxelmap/` to `src/main/java/com/mamiyaotaru/voxelmap/`
2. [ ] **EXCLUDE** the `mixins/` folder
3. [ ] Create new abstraction implementations:
   - [ ] `DirectEvents.java`
   - [ ] `DirectPacketBridge.java`
   - [ ] `DirectModApiBridge.java`
   - [ ] `VoxelMapInitializer.java`
   - [ ] `VoxelMapClientHooks.java`
4. [ ] Verify compilation (will have errors due to missing mixins - that's expected)

**Success Criteria**: All non-mixin VoxelMap code is in `src/main/java/`, new abstraction files created

---

### Phase 4: Mixin Elimination - Part 1 (HUD/GUI)
**Estimated Time**: 2-3 hours

**Steps**:
1. [ ] **MixinInGameHud**: Modify `Gui.java` scoreboard positioning
2. [ ] **MixinChatHud**: Modify `ChatComponent.java` chat positioning
3. [ ] Add HUD rendering hook in `Gui.render()` for minimap overlay
4. [ ] Test: Run client, verify minimap renders, scoreboard/chat don't overlap

**Success Criteria**: Minimap displays correctly, scoreboard and chat positioning work

---

### Phase 5: Mixin Elimination - Part 2 (World Rendering)
**Estimated Time**: 2-3 hours

**Steps**:
1. [ ] **MixinWorldRenderer**: Add waypoint beacon rendering in `LevelRenderer.java`
2. [ ] **MixinRenderPipelines**: Add VoxelMap pipeline registration in `RenderPipelines.java`
3. [ ] Test: Create waypoints, verify beacons render in 3D world

**Success Criteria**: Waypoint beacons visible through terrain

---

### Phase 6: Mixin Elimination - Part 3 (Lifecycle & Network)
**Estimated Time**: 3-4 hours

**Steps**:
1. [ ] **APIMixinMinecraftClient**: Add world change hooks in `Minecraft.java`
2. [ ] **APIMixinNetHandlerPlayClient**: Add packet handling in `ClientPacketListener.java`
3. [ ] **APIMixinChatListenerHud**: Add chat message filtering in `ChatListener.java`
4. [ ] **AccessorEnderDragonRenderer**: Already covered by access wideners (verify)
5. [ ] Add lifecycle hooks (join, disconnect, stop) in appropriate locations
6. [ ] Test: Join/leave worlds, verify dimension changes work

**Success Criteria**: All lifecycle events trigger correctly, packets handled, dimension switching works

---

### Phase 7: Initialization Integration
**Estimated Time**: 1-2 hours

**Steps**:
1. [ ] Add `VoxelMapInitializer.initialize()` call in `Minecraft.java` constructor
2. [ ] Add `VoxelMapInitializer.lateInit()` call after resource loading
3. [ ] Add `VoxelMapClientHooks.onClientStopping()` in `Minecraft.stop()`
4. [ ] Test: Launch client, check logs for VoxelMap initialization messages

**Success Criteria**: VoxelMap initializes properly, no errors in logs

---

### Phase 8: Resource Migration
**Estimated Time**: 30 minutes

**Steps**:
1. [ ] Copy `assets/voxelmap/` folder to `src/main/resources/assets/voxelmap/`
2. [ ] Exclude `icon.png`, `fabric.mod.json`, `pack.mcmeta`, mixin configs
3. [ ] Verify ~40 asset files copied (images, biomecolors.txt)
4. [ ] Test: Check that waypoint icons and UI elements load correctly

**Success Criteria**: All VoxelMap textures and configs load from resources

---

### Phase 9: Cleanup and Deletion
**Estimated Time**: 15 minutes

**Steps**:
1. [ ] Delete all mixin files from source tree
2. [ ] Delete mixin config JSON files
3. [ ] Delete `voxelmap.accesswidener`
4. [ ] Delete Fabric-specific files (`fabric.mod.json`, etc.)
5. [ ] Remove any remaining Fabric API import statements
6. [ ] Final compilation check: `./gradlew clean build`

**Success Criteria**: No mixin files remain, clean build succeeds

---

### Phase 10: Testing and Validation
**Estimated Time**: 2-3 hours

**Steps**:
1. [ ] Run all feature tests (Section 9.2)
2. [ ] Test minimap rendering
3. [ ] Test waypoint creation/deletion
4. [ ] Test world map (full-screen)
5. [ ] Test radar functionality
6. [ ] Test dimension switching (Overworld/Nether/End)
7. [ ] Test multiplayer compatibility
8. [ ] Test persistence (quit and rejoin)
9. [ ] Performance testing (FPS impact)
10. [ ] Regression testing (verify other mods still work)

**Success Criteria**: All features work as expected, no regressions

---

### Phase 11: Documentation and Polish
**Estimated Time**: 1 hour

**Steps**:
1. [ ] Update README.md to mention VoxelMap integration
2. [ ] Document keybindings
3. [ ] Create VOXELMAP.md integration summary (similar to WORLDEDIT.md)
4. [ ] Add VoxelMap to IMPLEMENTATION_STATUS.md
5. [ ] Document any known issues or limitations

**Success Criteria**: Documentation complete and accurate

---

## Total Estimated Time: 15-20 hours

---

## Risk Assessment and Mitigation

### High Risk Items

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Mixin conversion breaks rendering** | High | Incremental testing after each mixin, debug mode enabled |
| **Packet handling incompatibility** | Medium | Use Minecraft's native packet system, test multiplayer |
| **Access widener incomplete** | Medium | Careful review of all 32 wideners, compilation checks |
| **Resource loading fails** | Low | Verify asset paths match, test early |

### Dependencies

**VoxelMap depends on**:
- OpenGL rendering (already available)
- Minecraft's chunk system (already available)
- Biome data (already available)
- Entity rendering (already available)

**No external dependencies needed** - VoxelMap is self-contained.

---

## Success Criteria Summary

**The integration is successful when**:

1. ✅ VoxelMap compiles cleanly with Minecraft source
2. ✅ No mixin files remain in source tree
3. ✅ Minimap displays correctly in-game
4. ✅ World map opens and functions (zoom, pan, waypoints)
5. ✅ Waypoints can be created, edited, deleted
6. ✅ Waypoint beacons render in 3D world
7. ✅ Radar shows nearby entities
8. ✅ Multi-dimension support works (separate maps)
9. ✅ Map data persists across sessions
10. ✅ No regressions in existing mods (Iris, Sodium, DH, WorldEdit)
11. ✅ Performance is acceptable (<5% FPS impact)
12. ✅ Scoreboard and chat don't overlap minimap

---

## Notes and Considerations

### 1. Code Ownership
- VoxelMap code remains in `com.mamiyaotaru.voxelmap` package
- Clear separation from Minecraft and other mods
- Original authorship preserved in source files

### 2. Maintainability
- Direct integration makes debugging easier (no mixin indirection)
- Changes to Minecraft source are clearly marked with comments
- VoxelMap updates can be merged by comparing against frnsrc/

### 3. Performance
- Direct calls are faster than mixin injections
- No runtime bytecode manipulation overhead
- VoxelMap's own performance is well-optimized

### 4. Compatibility
- VoxelMap should not interfere with other integrated mods
- Rendering hooks are added at appropriate pipeline stages
- Packet system uses custom channel IDs to avoid conflicts

### 5. Future Updates
- When Minecraft updates, affected hooks need review
- VoxelMap updates can be merged from upstream
- Access wideners may need updates for new Minecraft versions

---

## Appendix A: File Checklist

### Files to Copy (113 from common)
- [x] All `.java` files in `com.mamiyaotaru.voxelmap/` (excluding `mixins/`)
- [x] All subdirectories: `gui/`, `persistent/`, `util/`, `interfaces/`, `packets/`, `entityrender/`, `textures/`

### Files to Create (7 new)
- [ ] `VoxelMapInitializer.java`
- [ ] `VoxelMapClientHooks.java`
- [ ] `DirectEvents.java`
- [ ] `DirectPacketBridge.java`
- [ ] `DirectModApiBridge.java`
- [ ] `VoxelmapPacketRegistry.java`
- [ ] Any needed Fabric API stubs

### Files to Delete (after integration)
- [ ] All mixin files (7 from common, 1 from fabric)
- [ ] `mixin.voxelmap.json`
- [ ] `mixin.voxelmap.fabric.json`
- [ ] `voxelmap.accesswidener`
- [ ] `fabric.mod.json`
- [ ] `pack.mcmeta`

### Files to Modify (Minecraft source)
- [ ] `net.minecraft.client.Minecraft` (initialization, lifecycle)
- [ ] `net.minecraft.client.gui.Gui` (HUD rendering, scoreboard position)
- [ ] `net.minecraft.client.renderer.LevelRenderer` (waypoint beacons)
- [ ] `net.minecraft.client.multiplayer.ClientPacketListener` (packets, events)
- [ ] `net.minecraft.client.gui.components.ChatComponent` (chat position)
- [ ] `net.minecraft.client.gui.components.ChatListener` (chat filtering)
- [ ] `net.minecraft.client.renderer.RenderPipelines` (pipeline registration)
- [ ] 32 files for access widener changes

---

## Appendix B: Key VoxelMap Classes

### Core Classes
- **VoxelMap**: Main singleton, manages all sub-systems
- **VoxelConstants**: Static utilities and abstraction layer
- **Map**: Minimap rendering logic
- **Radar**: Entity radar system
- **WaypointManager**: Waypoint CRUD operations
- **ColorManager**: Block color calculation

### Abstraction Interfaces
- **Events**: Lifecycle event interface (FabricEvents implements this)
- **PacketBridge**: Packet sending interface (FabricPacketBridge implements this)
- **ModApiBridge**: Mod API integration interface

### Persistent Storage
- **PersistentMap**: World map storage and rendering
- **CachedRegion**: Region-based map tile cache
- **ThreadManager**: Background thread management

### GUI Classes
- **GuiAddWaypoint**: Waypoint creation screen
- **GuiWaypoints**: Waypoint list screen
- **GuiMinimapOptions**: Minimap settings
- **GuiPersistentMap**: Full-screen world map

---

## Appendix C: Configuration Files

VoxelMap stores configuration in `run/config/voxelmap/`:
- `voxelmap.properties` - Main settings
- `waypointSubworldsAuto.txt` - Auto-generated world IDs
- Per-world folders with waypoints and cache data

**No changes needed** - VoxelMap's file I/O works independently.

---

## End of Plan

This plan provides a comprehensive, step-by-step approach to integrating VoxelMap into MattMC. Each phase is independent and testable, allowing for incremental progress and early problem detection.

**Next Steps**: Begin Phase 2 (Access Widener Application) after approval of this plan.
