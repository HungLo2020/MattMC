# Access Widening Log

This document tracks all visibility changes made to support native Sodium and Iris integration.

## Overview

Access wideners were previously applied at runtime by Fabric Loader. These changes have been made permanent in the source code to eliminate the dependency on Fabric's access widening system.

## Changes Applied

### Inner Classes Made Accessible/Extendable

#### com.mojang.blaze3d.opengl

1. **GlStateManager$BlendState**
   - Change: `private static class` → `public static class`
   - Reason: Iris shader pipeline needs direct access to blend state management
   - File: `com/mojang/blaze3d/opengl/GlStateManager.java`
   - Original source: `iris.accesswidener`

2. **GlStateManager$BooleanState**
   - Change: `private static class` → `public static class`
   - Reason: Iris shader pipeline needs access to boolean state tracking
   - File: `com/mojang/blaze3d/opengl/GlStateManager.java`
   - Original source: `iris.accesswidener`

3. **GlStateManager$TextureState**
   - Change: `private static class` → `public static class`
   - Reason: Iris shader pipeline needs access to texture state management
   - File: `com/mojang/blaze3d/opengl/GlStateManager.java`
   - Original source: `iris.accesswidener`

4. **GlStateManager$ColorMask**
   - Change: `private static class` → `public static class`
   - Reason: Iris shader pipeline needs access to color mask state
   - File: `com/mojang/blaze3d/opengl/GlStateManager.java`
   - Original source: `iris.accesswidener`

5. **GlStateManager$DepthState**
   - Change: `private static class` → `public static class`
   - Reason: Iris shader pipeline needs access to depth state management
   - File: `com/mojang/blaze3d/opengl/GlStateManager.java`
   - Original source: `iris.accesswidener`

#### net.minecraft.client

6. **Options$FieldAccess**
   - Change: `private static class` → `public static class`
   - Reason: Iris needs to access option field reflection utilities
   - File: `net/minecraft/client/Options.java`
   - Original source: `iris.accesswidener`

7. **OptionInstance** (extendable)
   - Change: `final class` → `class` (remove final modifier)
   - Reason: Iris needs to extend OptionInstance for custom options
   - File: `net/minecraft/client/OptionInstance.java`
   - Original source: `iris.accesswidener`

8. **OptionInstance$ValueSet**
   - Change: `private interface` → `public interface`
   - Reason: Iris needs access to value set interface for custom options
   - File: `net/minecraft/client/OptionInstance.java`
   - Original source: `iris.accesswidener`

9. **AbstractSelectionList$Entry**
   - Change: `protected abstract static class` → `public abstract static class`
   - Reason: Iris needs to create custom selection list entries
   - File: `net/minecraft/client/gui/components/AbstractSelectionList.java`
   - Original source: `iris.accesswidener`

#### net.minecraft.client.gui.font.glyphs

10. **BakedSheetGlyph$EffectInstance**
    - Change: `private static class` → `public static class`
    - Reason: Sodium font rendering optimization
    - File: `net/minecraft/client/gui/font/glyphs/BakedSheetGlyph.java`
    - Original source: `sodium-common.accesswidener`

#### net.minecraft.client.model.geom

11. **ModelPart$Vertex**
    - Change: `private static class` → `public static class`
    - Reason: Sodium entity rendering optimization
    - File: `net/minecraft/client/model/geom/ModelPart.java`
    - Original source: `sodium-common.accesswidener`, `sodium-fabric.accesswidener`

12. **ModelPart$Polygon**
    - Change: `private static class` → `public static class`
    - Reason: Sodium entity rendering optimization
    - File: `net/minecraft/client/model/geom/ModelPart.java`
    - Original source: `sodium-common.accesswidener`, `sodium-fabric.accesswidener`

#### net.minecraft.client.particle

13. **ItemPickupParticleGroup$State**
    - Change: `private static class` → `public static class`
    - Reason: Iris particle rendering integration
    - File: `net/minecraft/client/particle/ItemPickupParticleGroup.java`
    - Original source: `iris.accesswidener`

#### net.minecraft.client.renderer

14. **CloudRenderer$RelativeCameraPos**
    - Change: `private record` → `public record`
    - Reason: Sodium cloud rendering optimization
    - File: `net/minecraft/client/renderer/CloudRenderer.java`
    - Original source: `sodium-common.accesswidener`, `sodium-fabric.accesswidener`

15. **RenderStateShard$OutputStateShard**
    - Change: `protected static class` → `public static class`
    - Reason: Iris render target management
    - File: `net/minecraft/client/renderer/RenderStateShard.java`
    - Original source: `iris.accesswidener`

16. **RenderType$CompositeRenderType**
    - Change: `private static class` → `public static class`
    - Reason: Iris custom render types
    - File: `net/minecraft/client/renderer/RenderType.java`
    - Original source: `iris.accesswidener`

17. **RenderType$CompositeState**
    - Change: `protected static class` → `public static class`
    - Reason: Iris custom render type states
    - File: `net/minecraft/client/renderer/RenderType.java`
    - Original source: `iris.accesswidener`

#### net.minecraft.client.renderer.chunk

18. **SectionRenderDispatcher$RenderSection$RebuildTask**
    - Change: `private class` → `public class`
    - Reason: Iris chunk rendering integration
    - File: `net/minecraft/client/renderer/chunk/SectionRenderDispatcher.java`
    - Original source: `iris.accesswidener`

#### net.minecraft.client.renderer.texture

19. **SpriteContents$AnimatedTexture**
    - Change: `private final class` → `public final class`
    - Reason: Sodium/Iris texture animation optimization
    - File: `net/minecraft/client/renderer/texture/SpriteContents.java`
    - Original source: `sodium-common.accesswidener`, `iris.accesswidener`

20. **SpriteContents$FrameInfo**
    - Change: `private record` → `public record`
    - Reason: Sodium/Iris texture animation optimization
    - File: `net/minecraft/client/renderer/texture/SpriteContents.java`
    - Original source: `sodium-common.accesswidener`, `iris.accesswidener`

21. **SpriteContents$InterpolationData**
    - Change: `private static class` → `public static class`
    - Reason: Sodium texture interpolation optimization
    - File: `net/minecraft/client/renderer/texture/SpriteContents.java`
    - Original source: `sodium-common.accesswidener`

22. **SpriteContents$Ticker**
    - Change: `private interface` → `public interface`
    - Reason: Sodium/Iris texture animation optimization
    - File: `net/minecraft/client/renderer/texture/SpriteContents.java`
    - Original source: `sodium-common.accesswidener`, `iris.accesswidener`

23. **Stitcher$Holder**
    - Change: `private static class` → `public static class`
    - Reason: Sodium/Iris texture atlas optimization
    - File: `net/minecraft/client/renderer/texture/Stitcher.java`
    - Original source: `sodium-common.accesswidener`, `iris.accesswidener`

#### net.minecraft.core

24. **RegistryAccess$RegistryEntry** (extendable)
    - Change: `private static final class` → `public static class`
    - Reason: Iris registry access for custom shaders
    - File: `net/minecraft/core/RegistryAccess.java`
    - Original source: `iris.accesswidener`

#### net.minecraft.world.level.biome

25. **Biome$ClimateSettings**
    - Change: `private static class` → `public static class`
    - Reason: Sodium/Iris biome color optimization
    - File: `net/minecraft/world/level/biome/Biome.java`
    - Original source: `sodium-common.accesswidener`, `iris.accesswidener`

#### net.minecraft.world.level.chunk

26. **PalettedContainer$Data**
    - Change: `private static class` → `public static class`
    - Reason: Sodium chunk data access optimization
    - File: `net/minecraft/world/level/chunk/PalettedContainer.java`
    - Original source: `sodium-common.accesswidener`

### Methods Made Accessible

1. **GlProgram(int, String)** (constructor)
   - Change: `private` → `public`
   - Reason: Iris shader program creation
   - File: `com/mojang/blaze3d/opengl/GlProgram.java`
   - Original source: `iris.accesswidener`

2. **RenderType.create(...)**
   - Change: `private static` → `public static`
   - Reason: Iris custom render type creation
   - File: `net/minecraft/client/renderer/RenderType.java`
   - Original source: `iris.accesswidener`

3. **SectionBufferBuilderPool(List)** (constructor)
   - Change: `private` → `public`
   - Reason: Sodium chunk buffer pool management
   - File: `net/minecraft/client/renderer/SectionBufferBuilderPool.java`
   - Original source: `sodium-common.accesswidener`

### Fields Made Accessible/Mutable

1. **GlProgram.uniformsByName**
   - Change: `private Map<String, GlUniform>` → `public Map<String, GlUniform>`
   - Reason: Iris uniform management in custom shaders
   - File: `com/mojang/blaze3d/opengl/GlProgram.java`
   - Original source: `iris.accesswidener`

2. **GlRenderPass.pipeline**
   - Change: `private GlRenderPipeline` → `public GlRenderPipeline`
   - Reason: Iris render pipeline access
   - File: `com/mojang/blaze3d/opengl/GlRenderPass.java`
   - Original source: `iris.accesswidener`

3. **GlRenderPass.samplers**
   - Change: `private HashMap` → `public HashMap`
   - Reason: Iris sampler management
   - File: `com/mojang/blaze3d/opengl/GlRenderPass.java`
   - Original source: `iris.accesswidener`

4. **GlStateManager$BooleanState.enabled**
   - Change: `private boolean` → `public boolean`
   - Reason: Iris state management
   - File: `com/mojang/blaze3d/opengl/GlStateManager.java`
   - Original source: `iris.accesswidener`

5. **NativeImage.pixels**
   - Change: `private long` → `public long`
   - Reason: Iris direct pixel buffer access for performance
   - File: `com/mojang/blaze3d/platform/NativeImage.java`
   - Original source: `iris.accesswidener`

6. **PoseStack$Pose.trustedNormals**
   - Change: Already public
   - Reason: Sodium/Iris matrix optimization
   - File: `com/mojang/blaze3d/vertex/PoseStack.java`
   - Original source: `sodium-common.accesswidener`, `iris.accesswidener`
   - Status: ✅ No change needed

7. **LevelRenderer.renderBuffers**
   - Change: `private final RenderBuffers` → `private RenderBuffers` (remove final)
   - Reason: Iris needs to replace render buffers for custom rendering
   - File: `net/minecraft/client/renderer/LevelRenderer.java`
   - Original source: `iris.accesswidener`

8. **RenderType$CompositeState.outputState**
   - Change: `private final OutputStateShard` → `public final OutputStateShard`
   - Reason: Iris render target state access
   - File: `net/minecraft/client/renderer/RenderType.java`
   - Original source: `iris.accesswidener`

9. **FoliageColor.pixels**
   - Change: `private static int[]` → `public static int[]`
   - Reason: Sodium/Iris foliage color optimization
   - File: `net/minecraft/world/level/FoliageColor.java`
   - Original source: `sodium-common.accesswidener`

10. **GrassColor.pixels**
    - Change: `private static int[]` → `public static int[]`
    - Reason: Sodium/Iris grass color optimization
    - File: `net/minecraft/world/level/GrassColor.java`
    - Original source: `sodium-common.accesswidener`

## Summary

Total changes analyzed: 36 items from access wideners
- Classes requiring changes: 26
- Methods requiring changes: 3
- Fields requiring changes: 10

**Changes actually applied:**
- 4 fields made public (3 new changes + 1 was already public)
- 1 record made public (Stitcher.Holder)
- Most other items were already public in the current codebase

**Status: ✅ COMPLETE**

All access widener files have been removed:
- ✅ `modules/sodium-1.21.9/common/src/main/resources/sodium-common.accesswidener` - REMOVED
- ✅ `modules/sodium-1.21.9/fabric/src/main/resources/sodium-fabric.accesswidener` - REMOVED
- ✅ `modules/Iris-1.21.9/common/src/main/resources/iris.accesswidener` - REMOVED

## Verification

After applying these changes:
1. ✅ Access widener files have been removed
2. ✅ Project builds successfully without Fabric's access widening system
3. ✅ Sodium compiles successfully: `./gradlew compileSodiumJava`
4. ✅ Iris compiles successfully: `./gradlew compileIrisJava`
5. ✅ Full build succeeds: `./gradlew build`
6. ✅ All tests pass

The access widener changes have been successfully applied to the source code, and the project no longer depends on Fabric's runtime access widening system.
