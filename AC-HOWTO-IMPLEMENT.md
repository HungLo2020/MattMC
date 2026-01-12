# Alex's Caves Mob Implementation Guide

Quick reference for implementing Alex's Caves mobs into MattMC's direct Minecraft source.

## Prerequisites
- Source code available in `frnsrc/` directory
- Target mob identified (e.g., Subteranadon)

## Implementation Steps

### 1. Copy Source Files
- Copy all Java files for the mob from `frnsrc/` to `src/main/java/`
- Copy required dependency files (base classes, AI goals, interfaces, utilities)
- Copy Citadel model system files (BasicEntityModel, AdvancedEntityModel, AdvancedModelBox, BasicModelPart, etc.)
- Copy Citadel pathfinding system if needed (raycoms package)
- Copy Tabula model container files (8 files: JsonUtils, TabulaModelContainer, etc.)

### 2. Copy Asset Files
- **CRITICAL**: Copy textures to `src/main/resources/assets/minecraft/textures/entity/` (NOT alexscaves/)
- Copy model JSON if applicable to `assets/minecraft/models/`
- Asset namespace MUST be `minecraft` for direct source integration

### 3. Remove/Replace All Custom Registries
- Delete ACEntityRegistry, ACItemRegistry, ACBlockRegistry, etc.
- Delete AlexsCaves main class
- Replace all registry references with vanilla Minecraft equivalents:
  - `ACEntityRegistry.ENTITY` → `EntityType.ENTITY_NAME`
  - `ACItemRegistry.ITEM` → `Items.ITEM_NAME`
  - `ACSoundRegistry.SOUND` → `SoundEvents.SOUND_NAME`
  - `ACBlockRegistry.BLOCK` → `Blocks.BLOCK_NAME`

### 4. Register Entity in Vanilla Minecraft
- **EntityType.java**: Add entity type registration (e.g., line ~855)
- **Items.java**: Add spawn egg item registration (e.g., line ~1727)
- **DefaultAttributes.java**: Add entity attributes using correct method name (`createAttributes()`, not `createLivingAttributes()`)
- **EntityRenderers.java**: Register renderer in static block
- **CreativeModeTabs.java**: Add spawn egg to SPAWN_EGGS tab (alphabetically)

### 5. Fix Minecraft 1.21 API Changes
- `MobSpawnType` → `EntitySpawnReason`
- `CompoundTag` → `ValueOutput`/`ValueInput` with `getBooleanOr()`/`getIntOr()` methods
- `Level.isClientSide` field → `isClientSide()` method
- `moveTo()` → `setPos()`
- `startRiding()` now takes 3 parameters (entity, force, silent)
- `WalkAnimationState.update()` takes 3 parameters
- `addParticle()` requires 2 boolean parameters
- `EntityType.create()` requires EntitySpawnReason parameter

### 6. Update Model System for 1.21 Render State Architecture
- Change `EntityModel<T extends Entity>` → `EntityModel<T extends EntityRenderState>`
- Create custom RenderState class (e.g., SubterranodonRenderState) with animation data fields
- Update renderer's `extractRenderState()` to populate render state from entity
- Update model's `setupAnim(EntityRenderState)` to extract data from render state
- Remove `renderToBuffer()` override or use 4-parameter version (not 5-parameter which is final)

### 7. Fix Model Rendering Integration
- Implement `renderToBuffer()` in BasicEntityModel to render BasicModelPart children
- Create RenderingProxyModelPart extending ModelPart to bridge vanilla/custom model systems
- **CRITICAL**: Synchronize texture dimensions - set both `texWidth`/`texHeight` AND `textureWidth`/`textureHeight` BEFORE creating any model boxes
- Call `setTextureSize()` in AdvancedModelBox constructors to sync parent dimensions

### 8. Fix Resource Loading
- Change `ResourceLocation.fromNamespaceAndPath("alexscaves", ...)` → `ResourceLocation.withDefaultNamespace(...)` 
- All resources MUST use "minecraft" namespace for direct source integration
- Verify texture paths: `minecraft:textures/entity/mobname.png`

### 9. Inline Forge/NeoForge APIs
- Replace Forge Event system with simple data holder classes
- Remove `NeoForge.EVENT_BUS.post()` calls - inline event processing directly
- Remove `PacketDistributor` network sync (not critical for single-player)
- Replace Forge-specific APIs with vanilla equivalents:
  - `BlockState.isLadder()` → `BlockState.is(BlockTags.CLIMBABLE)`
  - Use vanilla damage sources instead of custom DamageTypes

### 10. Modify Vanilla Minecraft Source (NO Access Wideners)
- Make private fields public if needed (e.g., NodeEvaluator.entityWidth/Height/Depth)
- Make private methods public if needed (e.g., ChunkMap.getVisibleChunkIfPresent())
- Remove `final` modifiers if needed (e.g., ModelPart class)
- **Document all vanilla modifications**

### 11. Fix Advanced Pathfinding (if applicable)
- Update ChunkCache constructor signature (add DimensionType parameter)
- Fix Direction.getNearest() calls (now requires 4th null parameter)
- Use WalkNodeEvaluator.getPathTypeStatic() with Mob parameter
- Use Logger.error() instead of Logger.catching()
- Cast Level to LevelHeightAccessor for getMinY()/getMaxY()

### 12. Add Missing Attributes
- Check all AI goals for required attributes (e.g., TemptGoal needs TEMPT_RANGE)
- Add attributes to entity's createAttributes() method

### 13. Create Stub Classes for Missing Dependencies
- Create minimal implementations for any missing utility classes
- Stub out non-critical functionality to reduce implementation scope

## Common Pitfalls

1. **Black Textures**: Usually namespace issue - verify using `minecraft:` not `alexscaves:`
2. **Invisible Models**: Missing renderToBuffer() implementation or renderer not registered
3. **Compilation Errors**: Check for 1.21 API changes, especially render state architecture
4. **Texture Dimension Mismatch**: Ensure textureWidth/textureHeight synchronized across model hierarchy
5. **Missing in Creative Tab**: Must manually add to CreativeModeTabs
6. **Summon Command Fails**: Check DefaultAttributes method name and registration

## Validation

1. Compile: `./gradlew build` must succeed with 0 errors
2. Run: `./gradlew runClient` must launch without crashes
3. Summon: `/summon minecraft:mobname` must work
4. Render: Mob must be visible with correct textures
5. Creative: Spawn egg must appear in creative mode tabs

## Files Modified Summary
- Vanilla Minecraft source files: 3-5 files (EntityType, Items, DefaultAttributes, EntityRenderers, CreativeModeTabs)
- Vanilla modifications for access: 2-3 files (e.g., NodeEvaluator, ChunkMap, ModelPart)
- New mod files: 70-80 Java files + 3-4 asset files
