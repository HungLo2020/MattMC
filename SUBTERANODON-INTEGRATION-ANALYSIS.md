# Subteranodon Integration Analysis

## Task Overview
Integrate the Subteranodon mob from AlexsCaves mod (Forge 1.21.1) directly into MattMC (Minecraft 1.21.10 fork).

## Scope Assessment

### Required Core Files to Port

#### From AlexsCaves (Server-side):
1. `SubterranodonEntity.java` (676 lines) - Main entity class
2. `DinosaurEntity.java` (384 lines) - Parent class
3. `SubterranodonFleeGoal.java` (44 lines) - AI Goal
4. `SubterranodonFlightGoal.java` (~200 lines estimated) - AI Goal
5. `SubterranodonFollowOwnerGoal.java` (~100 lines estimated) - AI Goal
6. `AnimalJoinPackGoal.java` - AI Goal (shared with other mobs)
7. `AnimalBreedEggsGoal.java` - AI Goal (shared with other mobs)
8. `AnimalLayEggGoal.java` - AI Goal (shared with other mobs)
9. `FlightMoveHelper` (integrated in SubterranodonEntity, ~30 lines)
10. `AdvancedPathNavigateNoTeleport.java` - Custom pathfinding

#### From AlexsCaves (Client-side):
1. `SubterranodonModel.java` (~300 lines estimated) - Entity model
2. `SubterranodonRenderer.java` (26 lines) - Entity renderer
3. `SubterranodonRiderLayer.java` (~100 lines estimated) - Render layer

#### From Citadel Library:
1. `AdvancedEntityModel.java` - Base model class
2. `AdvancedModelBox.java` - Model part system
3. `BasicModelPart.java` - Model part interface
4. `AdvancedPathNavigate.java` - Advanced pathfinding system
5. `IAdvancedPathingMob.java` - Interface
6. `ICustomCollisions.java` - Collision interface
7. `IDancesToJukebox.java` - Interface
8. `ColorUtil.java` - Rendering utilities
9. `ACMath.java` - Math utilities

#### Custom Interfaces/Utilities Needed:
1. `PackAnimal.java` - Pack behavior interface
2. `FlyingMount.java` - Mount interface
3. `KeybindUsingMount.java` - Input handling
4. `LaysEggs.java` - Egg laying interface
5. `RidingMeterMount.java` - Stamina meter interface

### Assets to Copy

#### Textures:
- `textures/entity/subterranodon.png`
- `textures/entity/subterranodon_retro.png`
- `textures/entity/subterranodon_tectonic.png`
- `textures/item/subterranodon_egg.png`
- `textures/block/egg/subterranodon_egg.png` (+ cracked variants)
- `textures/block/cave_painting/subterranodon.png` (+ ride variant)

#### Sounds (21 files):
- `sounds/mob/subterranodon/subterranodon_idle_*.ogg` (4 files)
- `sounds/mob/subterranodon/subterranodon_hurt_*.ogg` (3 files)
- `sounds/mob/subterranodon/subterranodon_death_*.ogg` (3 files)
- `sounds/mob/subterranodon/subterranodon_attack_*.ogg` (4 files)
- `sounds/mob/subterranodon/subterranodon_flap_*.ogg` (6 files)

#### Models/Blockstates:
- Item models for spawn egg and egg block
- Block models for egg (4 variants × 3 states = 12 models)
- Blockstate definitions

#### Data Files:
- Loot tables (entity + blocks)
- Advancements
- Tags (entity type tags)

### Vanilla Minecraft Integration Points

#### 1. Entity Registration
**Location:** `src/main/java/net/minecraft/world/entity/EntityType.java`
- Add `SUBTERRANODON` static field
- Register in bootstrap method
- Add to appropriate MobCategory

#### 2. Item Registration  
**Location:** `src/main/java/net/minecraft/world/item/Items.java`
- Add spawn egg item
- Register with appropriate CreativeTab

#### 3. Sound Registration
**Location:** `src/main/java/net/minecraft/sounds/SoundEvents.java`
- Register 5 sound events (idle, hurt, death, attack, flap)

#### 4. Renderer Registration
**Location:** `src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java`
- Register SubterranodonRenderer

#### 5. Spawn Placement
**Location:** `src/main/java/net/minecraft/world/entity/SpawnPlacements.java`
- Register spawn placement rules (no biome spawning needed, but rules still required for spawn egg)

### Forge API Replacements Needed

#### 1. Registration System
**Forge:** DeferredRegister + DeferredHolder
**Replacement:** Direct registration in vanilla registry bootstrap

#### 2. Entity Data Synchronization
**Forge:** Network packets with custom codecs
**Replacement:** Vanilla SynchedEntityData system (already used)

#### 3. Spawn Placement Events
**Forge:** RegisterSpawnPlacementsEvent
**Replacement:** Direct SpawnPlacements.register() calls

#### 4. Client/Server Proxy System
**Forge:** `AlexsCaves.PROXY` for client-side checks
**Replacement:** `level().isClientSide` checks + custom input handling

#### 5. Keybind System
**Forge:** `MountedEntityKeyMessage` networking
**Replacement:** Custom packet or simplified control system

### Features That Cannot Be Implemented (for AC-TODO.md)

#### 1. Mod-Specific Items
- Trilocaris Tail (food item from AlexsCaves)
- Cooked Trilocaris Tail
- Other AC-specific items referenced in taming/feeding

**Workaround:** Use vanilla items (COD, COOKED_COD only)

#### 2. Mod-Specific Blocks
- Pewen Branch (from AlexsCaves trees)
- AC cave painting blocks
- Subterranodon egg block (complex multi-egg system)

**Workaround:** 
- Spawn on leaves/grass only
- Skip cave paintings
- Simplify or skip egg block

#### 3. Mod-Specific Tags
- `ACTagRegistry.DINOSAURS_SPAWNABLE_ON`
- `ACTagRegistry.SUBTERRANODON_FLEES`

**Workaround:** Create equivalent vanilla tags or hardcode

#### 4. Mod-Specific Biomes
- Primordial Caves biome

**Workaround:** No natural spawning (spawn egg only)

#### 5. Mod-Specific Systems
- World data (boss defeat tracking)
- Advancement triggers
- Cave book entries

**Workaround:** Skip these systems

#### 6. Pack Behavior
- Interactions with other Subterranodon instances
- Formation flying

**Decision Needed:** Implement simplified version or skip?

#### 7. Advanced Pathfinding
- Citadel's multithreaded pathfinding system
- Advanced flying navigation

**Workaround:** Use vanilla FlyingPathNavigation or simplified version

### Estimated Complexity

**Lines of Code to Port/Write:** ~3,000-4,000 lines
**Files to Create:** ~30-40 files
**Time Estimate:** 20-30 hours of development + testing
**Risk Level:** HIGH - Many interdependencies and complex systems

### Recommended Approach

#### Phase 1: Minimal Viable Entity (4-6 hours)
1. Port basic entity class (simplified, no flying initially)
2. Port basic model and renderer
3. Register entity, spawn egg, sounds
4. Get it spawning and rendering

#### Phase 2: Flying Behavior (6-8 hours)
1. Implement flight mechanics
2. Port flight AI goals
3. Add flight move controller
4. Test and refine

#### Phase 3: Advanced Features (8-10 hours)
1. Mounting system
2. Taming system
3. Egg laying (simplified)
4. Pack behavior (if time)

#### Phase 4: Polish (2-4 hours)
1. All sounds working
2. All textures
3. Proper animations
4. AC-TODO.md documentation

### Critical Dependencies from Citadel

The following Citadel classes are CRITICAL and must be ported:

1. **AdvancedEntityModel** - Without this, cannot use the exact model
2. **AdvancedModelBox** - Core of the model system
3. **AdvancedPathNavigate** - Required for flight pathfinding

These alone are ~1000 lines of code from Citadel.

### Conclusion

This is a major integration project that significantly exceeds the scope of typical code changes. It's essentially porting a substantial portion of a complex mod into vanilla Minecraft. 

**Recommendation:** Proceed incrementally, starting with the simplest viable version and building up complexity in phases. Document all limitations and missing features in AC-TODO.md.
