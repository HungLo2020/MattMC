# Alex's Caves - Subteranadon Implementation TODO

## Overview
This document tracks the implementation status of the Subteranadon mob from Alex's Caves mod into MattMC.

## IMPORTANT NOTE
This is an **EXTREMELY LARGE** task that requires:
- Copying and adapting 100+ Java files from both Citadel and AlexsCaves
- Removing all Forge/NeoForge dependencies (DeferredRegister, DeferredHolder, @EventBusSubscriber, etc.)
- Replacing all mixins with direct source code modifications
- Converting all registry systems to direct Minecraft registry calls
- Adapting all mappings to match MattMC's codebase
- Creating proper JSON files for models, textures, loot tables, recipes, etc.
- Registering everything directly in Minecraft's EntityType, Items, DefaultAttributes classes

**Estimated scope**: 50-100 hours of work for a complete implementation

## Current Status
**PHASE: Files Copied, Working on Compilation**

### Files Copied (80 Java files + 4 assets)
✅ Citadel animation/model system (20 files)
✅ Citadel advanced pathfinding system (18 files) 
✅ AlexsCaves entity classes (2 files: SubterranodonEntity, DinosaurEntity)
✅ AlexsCaves AI goals (7 files)
✅ AlexsCaves entity utilities (5 files: interfaces)
✅ Client rendering (4 files: model, renderer, layer, ColorUtil)
✅ Misc utilities (ACMath)
✅ Textures (3 variants)
✅ Spawn egg model JSON

### Stub Files Created (13 files)
✅ AlexsCaves main class
✅ ACEntityRegistry (stub)
✅ ACBlockRegistry (stub - uses vanilla blocks)
✅ ACItemRegistry (stub - uses vanilla items)
✅ ACSoundRegistry (stub - uses vanilla sounds)
✅ ACTagRegistry (stub)
✅ ACWorldData (stub)
✅ ACAdvancementTriggerRegistry (stub)
✅ ACParticleRegistry (stub)
✅ Block stubs (DinosaurEggBlock, MultipleDinosaurEggsBlock)
✅ Message stubs (3 classes)
✅ Citadel stubs (ExpandedBiomeSource, PathJobFindWater)

### Compilation Issues Remaining (~50 errors)
- NeoForge imports commented out but some code still references them
- Tag/Sound registry API mismatches (ResourceLocation vs String, Holder vs direct)
- CompoundTag serialization API changes
- Missing Citadel tick modifier classes
- Missing Citadel mixin accessor classes
- Some client model loader API differences
- Entity data serialization API changes in 1.21+

The Subteranadon implementation requires a massive dependency tree:
1. Citadel animation/model system (~20 files) - ✅ COPIED
2. Citadel advanced pathfinding system (~15 files) - ✅ COPIED
3. AlexsCaves base entity classes (~10 files) - ⚠️ PARTIAL (2/10)
4. AlexsCaves AI goals (~8 files) - ✅ COPIED
5. Client rendering (~5 files) - ✅ COPIED
6. Assets (textures, models, sounds, JSONs) (~30+ files) - ⚠️ PARTIAL (4/30+)

### Next Steps to Complete Implementation

1. **Fix Compilation Errors** (Est: 10-15 hours)
   - Fix tag/sound registry API calls
   - Fix CompoundTag serialization (changed in 1.21)
   - Remove/stub Citadel tick modifier references
   - Remove/stub mixin accessor references  
   - Fix client model API differences
   - Comment out NeoForge event bus code

2. **Register Entity in Minecraft Classes** (Est: 2-3 hours)
   - Add SUBTERRANODON to EntityType.java
   - Add SUBTERRANODON_SPAWN_EGG to Items.java
   - Add attributes to DefaultAttributes.java
   - Add spawn placement rules

3. **Create Remaining Stubs** (Est: 5-7 hours)
   - Full FlightMoveHelper class
   - Remaining dinosaur entity classes needed by DinosaurEntity
   - Entity data registry
   - Additional block classes if needed
   - Network/packet handling hooks

4. **Client Registration** (Est: 3-5 hours)
   - Register entity renderer
   - Register model layers
   - Create client hooks for initialization

5. **Complete Assets** (Est: 5-10 hours)
   - Create all necessary JSON files (loot tables, recipes, etc.)
   - Copy sound files or create sound JSON definitions
   - Create block models and blockstates if needed
   - Create entity spawning JSONs

6. **Testing & Debugging** (Est: 10-20 hours)
   - Fix runtime errors
   - Test entity spawning
   - Test AI behaviors
   - Test rendering
   - Test riding/mounting
   - Fix any crashes or issues

**Total Remaining Estimate: 35-60 hours**

### Files That Still Need to Be Created/Fixed

**Java Classes:**
- FlightMoveHelper (AI movement)
- Additional dinosaur classes referenced by DinosaurEntity
- More complete stubs for particle/sound systems
- Network packet handlers (without NeoForge)
- Client-side renderer registration hooks

**JSON/Assets:**
- Entity loot table
- Spawn egg loot tables (3 JSONs for block states)
- Block models for eggs  
- Block loot tables
- Recipe JSONs if needed
- Sound definition JSONs
- Language files (en_us.json)
- Damage type tags
- Entity type tags
- Block tags

**Minecraft Source Modifications:**
- EntityType.java - add SUBTERRANODON
- Items.java - add SUBTERRANODON_SPAWN_EGG
- DefaultAttributes.java - add Subteranadon attributes
- Entity renderer registration (client-side init)
- Model layer registration (client-side init)

### Core Entity
- [ ] SubterranodonEntity - Main entity class
- [ ] DinosaurEntity - Base class for Subteranadon
- [ ] TamableAnimal integration

### AI Goals  
- [ ] SubterranodonFlightGoal - Flight behavior
- [ ] SubterranodonFollowOwnerGoal - Owner following when tamed
- [ ] SubterranodonFleeGoal - Fleeing behavior
- [ ] AnimalJoinPackGoal - Pack behavior
- [ ] AnimalBreedEggsGoal - Breeding behavior
- [ ] AnimalLayEggGoal - Egg laying behavior
- [ ] FlightMoveHelper - Flight movement control

### Client Rendering
- [ ] SubterranodonModel - 3D model
- [ ] SubterranodonRenderer - Entity renderer
- [ ] SubterranodonRiderLayer - Rider rendering layer
- [ ] Citadel AdvancedEntityModel framework
- [ ] Citadel AdvancedModelBox system
- [ ] Animation system

### Pathfinding (Citadel Dependency)
- [ ] AdvancedPathNavigate - Advanced pathfinding system
- [ ] AdvancedPathNavigateNoTeleport - Non-teleporting variant
- [ ] IAdvancedPathingMob interface
- [ ] PathJobMoveToLocation
- [ ] PathJobRandomPos  
- [ ] PathJobMoveAwayFromLocation
- [ ] Pathfinding thread pool system
- [ ] PathResult and PathFindingStatus

### Entity Utilities
- [ ] PackAnimal interface - Pack behavior support
- [ ] FlyingMount interface - Rideable flying mount
- [ ] KeybindUsingMount interface - Mount with key controls
- [ ] LaysEggs interface - Egg laying support
- [ ] RidingMeterMount interface - Stamina/meter system
- [ ] IDancesToJukebox interface (Citadel) - Jukebox dancing
- [ ] ICustomCollisions interface (Citadel) - Custom collision handling

### Items
- [ ] Spawn egg item
- [ ] Spawn egg registration in Items class
- [ ] Spawn egg model JSON (item)
- [ ] Spawn egg blockstate JSON (block)
- [ ] Spawn egg loot table JSON

### Blocks
- [ ] Subteranadon egg block (MultipleDinosaurEggsBlock)
- [ ] Egg block variants (4 types, each with 3 crack states)
- [ ] Egg block registration

### Resources
- [ ] Entity textures (3 variants: normal, retro, tectonic)
- [ ] Egg textures
- [ ] Sound events
- [ ] Loot tables
- [ ] Tags (spawn-on blocks, etc.)

### Registration
- [ ] EntityType registration in EntityType class
- [ ] Item registration in Items class  
- [ ] Entity attributes in DefaultAttributes
- [ ] Spawn placement rules
- [ ] Renderer registration (client-side)
- [ ] Model layer registration (client-side)

### Interactions & Features TO SKIP/STUB
The following features interact with other Alex's Caves content and should be commented out or stubbed:

- [ ] ACBlockRegistry references - Use vanilla blocks where possible, stub where not
- [ ] ACItemRegistry references (except spawn egg) - Use vanilla items
- [ ] ACEntityRegistry references - Comment out other entity interactions
- [ ] ACTagRegistry - Create minimal stubs or use vanilla tags
- [ ] ACSoundRegistry - Stub or use vanilla sounds temporarily
- [ ] ACParticleRegistry - Stub or use vanilla particles
- [ ] ACAdvancementTriggerRegistry - Stub advancement triggers
- [ ] ACWorldData - Boss defeat checks, world data storage - stub
- [ ] MultipleDinosaurEggsBlock - Complex egg block system - may need simplified version
- [ ] MountedEntityKeyMessage - Network messages for mount controls - may need reimplementation
- [ ] Trilocaris food items - Use vanilla fish items instead
- [ ] Interactions with other AC dinosaurs/mobs - Comment out
- [ ] Cave-specific biome spawning - Skip for now

### Technical Challenges

#### Forge to Direct Minecraft Port
- [ ] Remove NeoForge DeferredRegister system
- [ ] Replace with direct registration in EntityType/Items classes
- [ ] Remove @EventBusSubscriber annotations
- [ ] Convert Forge event system to direct hooks or method injection
- [ ] Remove DeferredHolder wrappers

#### Mixin Replacement
- [ ] No mixins allowed - must use direct source modification or hooks
- [ ] Identify where AC/Citadel uses mixins
- [ ] Replace with inline modifications to Minecraft source
- [ ] Document all inline modifications

#### Mapping Issues
- [ ] Verify all class/method/field names match MattMC's mappings
- [ ] Fix SRG/MCP naming differences if any
- [ ] Test all vanilla Minecraft class references

#### Access Issues
- [ ] No access wideners - must modify source directly
- [ ] No reflection - direct access to all needed fields/methods
- [ ] Make necessary fields/methods public in Minecraft source

## Priority Implementation Order

1. **Phase 1: Core Infrastructure**
   - Citadel basic utilities (AdvancedModelBox, AdvancedEntityModel)
   - Entity base classes (DinosaurEntity, TamableAnimal integration)
   - Basic interfaces (PackAnimal, FlyingAnimal, etc.)

2. **Phase 2: Entity Implementation**
   - SubterranodonEntity with stubbed features
   - Basic AI goals (walking, looking)
   - Entity registration

3. **Phase 3: Flight System**
   - Flight AI goals
   - FlightMoveHelper
   - Basic pathfinding (may use vanilla initially)

4. **Phase 4: Rendering**
   - Basic model (may start simple)
   - Basic renderer
   - Textures
   - Animation (simplified initially)

5. **Phase 5: Items & Spawn**
   - Spawn egg
   - Item registration
   - Spawn egg JSONs

6. **Phase 6: Polish**
   - Full AI behaviors
   - Advanced pathfinding (Citadel system)
   - Riding/mount system
   - Pack behavior
   - Egg laying

## Notes

- The Subteranadon is a tameable flying dinosaur mount
- It has pack AI similar to wolves
- It can be ridden and controlled with keybinds
- It lays eggs when bred
- It has a stamina/meter system for flight
- It dances to jukebox music
- Has 3 texture variants (skins)
