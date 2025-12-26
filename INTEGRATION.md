# MattMC Mod Integration Plan - Hook-Based Integration

## Goal

**Remove the need for mixins entirely** while keeping mods in separate modules.

## The Problem

- Mods currently use **mixins** (runtime bytecode modification) to inject functionality into Minecraft
- Mixins are complex, fragile, and make the codebase harder to understand and maintain
- We want to eliminate mixins but keep mods as separate modules
- **Constraint**: Minecraft cannot depend on mod packages at compile time (would create circular dependencies)

## The Solution: Interface-Based Hook System

Instead of using mixins to inject code at runtime, we add **hook interfaces** directly into Minecraft source code. Mods implement these interfaces and register themselves at runtime.

### How It Works

1. **Add hook interfaces in Minecraft** - Define interfaces like `RenderHooks`, `GameHooks`, etc. in Minecraft packages
2. **Add hook calls in Minecraft** - At points where mixins would inject, call the registered hooks instead
3. **Mods implement interfaces** - Each mod implements the hook interfaces in their own separate modules
4. **Runtime registration** - Mods register their hook implementations when the game starts

### Why This Works

- ✅ **No mixins needed** - Direct method calls instead of bytecode modification
- ✅ **Mods stay separate** - No need to move mod code into Minecraft packages
- ✅ **No compile-time dependencies** - Minecraft only knows about its own interfaces
- ✅ **Clean and maintainable** - Easy to understand what hooks exist and where they're called
- ✅ **Type-safe** - Compiler checks interface implementations

## Example: Replacing a Mixin with Hooks

### Before (Using Mixins)

```java
// In Sodium mod (net.caffeinemc.mods.sodium.mixin.MinecraftMixin)
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "buildInitialScreens", at = @At("TAIL"))
    private void postInit(CallbackInfoReturnable<Runnable> cir) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);
    }
}
```

### After (Using Hooks)

```java
// In Minecraft (net.minecraft.hooks.GameHooks.java)
public interface GameHooks {
    void onGameInitialized(Minecraft minecraft);
}

// In Minecraft (net.minecraft.client.Minecraft.java)
public class Minecraft {
    private Runnable buildInitialScreens() {
        // ... original code ...
        
        // Call registered hooks
        for (GameHooks hook : HookRegistry.getGameHooks()) {
            hook.onGameInitialized(this);
        }
        
        return screenTask;
    }
}

// In Sodium mod (still in separate module!)
public class SodiumGameHook implements GameHooks {
    @Override
    public void onGameInitialized(Minecraft minecraft) {
        ResourcePackScanner.checkIfCoreShaderLoaded(minecraft.getResourceManager());
    }
}

// Sodium registers itself at startup
HookRegistry.registerGameHook(new SodiumGameHook());
```

## Implementation Strategy

### Phase 1: Infrastructure Setup
1. Create hook registry system in Minecraft (`net.minecraft.hooks.HookRegistry`)
2. Create initial hook interfaces (`GameHooks`, `RenderHooks`, etc.)
3. Test the registration system works

### Phase 2: Convert Mixins to Hooks (Incremental)
For each mixin:
1. Analyze what it does and where it injects
2. Create or use existing hook interface method
3. Add hook call in Minecraft at injection point
4. Implement hook in mod
5. Remove mixin class and config entry
6. Test that functionality still works

### Phase 3: Remove Mixin System
Once all mixins are converted:
1. Remove SpongePowered Mixin dependency
2. Remove Fabric Loader's mixin initialization
3. Simplify build system

## Benefits

- **Simpler codebase** - No runtime bytecode modification
- **Better IDE support** - Can navigate from hook calls to implementations
- **Easier debugging** - Stack traces show actual method calls, not mixin magic
- **Better performance** - No runtime bytecode transformation overhead
- **Maintainable** - Clear contracts defined by interfaces

## Proof of Concept

A working proof of concept has been implemented for Sodium's resource pack checking functionality:
- Hook interface: `net.minecraft.hooks.GameHooks`
- Hook registry: `net.minecraft.hooks.HookRegistry`
- Minecraft integration: Added hook call in `Minecraft.buildInitialScreens()`
- Sodium implementation: `SodiumGameHook` implements the interface
- Original mixin removed from `MinecraftMixin.java`

## Mixin Inventory - Total Count

After thoroughly analyzing all mixin configuration files across the entire codebase, here is the complete breakdown of mixins that need to be replaced:

### Sodium: 97 mixins
- `sodium-common.mixins.json`: 93 mixins (core rendering, chunk optimizations, entity rendering, texture system, features, workarounds)
- `sodium-fabric.mixins.json`: 4 mixins (fabric-specific integrations)

### Iris: 168 mixins
- `mixins.iris.json`: 123 mixins (shader pipeline, rendering, entity rendering, sky, texture handling, state tracking)
- `mixins.iris.fabric.json`: 3 mixins (fabric-specific integrations)
- `mixins.iris.compat.sodium.json`: 19 mixins (Sodium compatibility layer)
- `mixins.iris.vertexformat.json`: 8 mixins (vertex format modifications)
- `mixins.iris.fantastic.json`: 7 mixins (particle and feature renderer modifications)
- `mixins.iris.compat.dh.json`: 4 mixins (Distant Horizons compatibility)
- `mixins.iris.fixes.maxfpscrash.json`: 1 mixin (max FPS crash fix)
- `mixins.iris.devenvironment.json`: 2 mixins (development environment)
- `mixins.iris.integrationtest.json`: 1 mixin (integration testing)
- `mixins.iris.bettermipmaps.json`: 0 mixins (empty/disabled)

### Distant Horizons: 19 mixins
- `DistantHorizons.fabric.mixins.json`: 19 mixins
  - Server mixins (9): Chunk generation, chunk map, entity tracking, player management, threading, lifecycle
  - Client mixins (10): Level rendering, fog, debug overlay, lighting, chunk sections, texture utilities

### **TOTAL: 284 mixins across all mods**

### Breakdown by Category

**Rendering & Graphics**: ~180 mixins
- Core rendering pipeline modifications
- Shader integration and management
- Entity and particle rendering
- Texture handling and animations
- Vertex format customizations
- Sky, clouds, weather rendering

**World & Chunk Management**: ~50 mixins
- Chunk loading and rendering
- LOD (Level of Detail) system
- World generation hooks
- Chunk section management

**Game Lifecycle & Integration**: ~30 mixins
- Initialization hooks
- Resource loading and management
- Configuration screens
- Debug overlays

**Compatibility & Fixes**: ~24 mixins
- Mod-to-mod compatibility (Sodium ↔ Iris, Iris ↔ DH)
- Bug fixes and workarounds
- Platform-specific adjustments

### Implementation Progress

- ✅ **Completed**: 26 mixins converted to hooks (9.2% of 284 total)
  1. Sodium `MinecraftMixin.postInit()` → GameHooks.onGameInitialized()
  2. Sodium `MinecraftMixin.preRender()` → GameHooks.beforeRunTick()
  3. Sodium `MinecraftMixin.postRender()` → GameHooks.afterRunTick()
  4. Sodium `MinecraftMixin.postResourceReload()` → GameHooks.afterResourceReload()
  5. All MinecraftMixin functionality fully replaced, file deleted
  6. Sodium `RenderSystemMixin` (event loop) → RenderHooks.shouldSkipFirstPollEvents()
  7. Sodium `GuiMixin` (vignette) → GraphicsConfigHooks.shouldEnableVignette()
  8-9. Sodium `WeatherLevelRendererMixin` (2 methods) → GraphicsConfigHooks.getWeatherQuality()
  10. Sodium `GameRendererMixin` (console overlay) → GuiRenderHooks.onGuiRender()
  11. Sodium `DebugEntryMemoryMixin` (off-heap memory) → DebugScreenHooks.addDebugInfo()
  12. Sodium `OptionsScreenMixin` (video settings) → ScreenFactoryHooks.overrideScreenFactory()
  13. Sodium `LeavesBlockMixin` → BlockRenderHooks.shouldSkipRendering()
  14-15. Sodium `ItemBlockRenderTypesMixin` (2 methods) → RenderTypeHooks.shouldUseCutoutRendering() + onSetFancyGraphics()
  16. Sodium `LevelLoadTrackerMixin` → PlayerPositionHooks.getPlayerBlockPositionForChunkLoading()
  17. Sodium `FogRendererMixin` → FogRenderHooks.onFogParametersCalculated()
  18. Sodium `ShadowFeatureRendererMixin` → EntityRenderHooks.onRenderEntityShadows()
  19. Sodium `BlockColorsMixin` → BlockColorHooks.onBlockColorRegistered()
  20. Sodium `ClientLevelMixin` (biome) → ClientLevelHooks.onClientLevelInit()
  21. Sodium `ClientLevelMixin` (map) → ClientLevelHooks.onChunkUnload()
  22. Sodium `VertexFormatMixin` → VertexFormatHooks.onVertexFormatInit()
  23. Sodium `ClientChunkCacheMixin` → ClientLevelHooks.onChunkLoaded() + onChunkDropped()
  24. Sodium `BlockEntityTypeMixin` → BlockEntityTypeHooks.onBlockEntityTypeInit()
  25. Sodium `SpriteContentsMixin` → SpriteContentsHooks.onSpriteContentsInit()
  26. Sodium `WindowMixin` → NativeWindowHandle + window hints (hook-based)
- ⏳ **Remaining**: 259 mixins to convert to hooks (72 Sodium + 168 Iris + 19 DH)
- ⚠️ **Cannot Convert** (Iris compatibility):
  - `core.render.world.GameRendererMixin` (FogStorage) - Iris casts GameRenderer to FogStorage
  - `core.render.frustum.FrustumMixin` (ViewportProvider) - Iris shadow frustums extend Frustum and implement ViewportProvider

**Recent Sessions**:
- Session 1: MinecraftMixin (4 methods, GPU sync + resource reload) - 5 mixins
- Session 2: RenderSystemMixin (event loop workaround) - 1 mixin
- Session 3: GuiMixin + WeatherLevelRendererMixin (graphics config overrides) - 3 mixins
- Session 4: GameRendererMixin, DebugEntryMemoryMixin, OptionsScreenMixin (complex patterns) - 3 mixins
- Session 5: LeavesBlockMixin, ItemBlockRenderTypesMixin, LevelLoadTrackerMixin, FogRendererMixin, ShadowFeatureRendererMixin - 5 mixins
- Session 6: BlockColorsMixin - 1 mixin
- Session 7: ClientLevelMixin (2 files), VertexFormatMixin - 3 mixins
- Session 8: ClientChunkCacheMixin, BlockEntityTypeMixin, SpriteContentsMixin - 3 mixins
- Session 9: WindowMixin - 1 mixin (FrustumMixin and GameRendererMixin reverted for Iris compatibility)
- Build verified successful after each session

**Hook Infrastructure Created**:
- GameHooks (4 methods) - Game lifecycle events
- RenderHooks (1 method) - Rendering system lifecycle
- GraphicsConfigHooks (4 methods) - Graphics quality configuration
- GuiRenderHooks (1 method) - GUI rendering with full context
- DebugScreenHooks (1 method) - F3 debug screen custom information
- ScreenFactoryHooks (1 method) - Screen creation/replacement
- BlockRenderHooks (1 method) - Block rendering customization
- RenderTypeHooks (2 methods) - Render type overrides
- PlayerPositionHooks (1 method) - Player position calculations
- FogRenderHooks (1 method) - Fog parameter interception
- EntityRenderHooks (1 method) - Entity shadow rendering optimization
- BlockColorHooks (1 method) - Block color provider tracking
- ClientLevelHooks (4 methods) - Client level initialization and chunk lifecycle
- VertexFormatHooks (1 method) - Vertex format initialization
- BlockEntityTypeHooks (1 method) - Block entity type initialization
- SpriteContentsHooks (1 method) - Sprite contents initialization

### Estimated Effort

Converting all 284 mixins to hooks is a substantial undertaking. Based on the proof of concept:

- **Simple injections** (~40% of mixins): 1-2 hours each → ~110 mixins × 1.5 hours = 165 hours
- **Moderate complexity** (~40% of mixins): 3-5 hours each → ~110 mixins × 4 hours = 440 hours  
- **Complex transformations** (~20% of mixins): 6-12 hours each → ~60 mixins × 9 hours = 540 hours

**Total estimated effort**: 1,145 hours or approximately **29 weeks** of full-time work

This can be parallelized or done incrementally, with mods remaining functional throughout the conversion process as long as unconverted mixins continue to use the current mixin system.
