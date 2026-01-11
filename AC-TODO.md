# Alex's Caves Subterranodon - Unimplemented Features

This document tracks features from the Alex's Caves mod's Subterranodon entity that could not be fully integrated into the vanilla Minecraft fork.

## Missing AlexsCaves Mod Dependencies

### Items and Resources
- **Trilocaris Tail** (raw and cooked) - Used for taming the Subterranodon
  - Currently replaced with COD and COOKED_COD as taming items
  - Future: Implement Trilocaris entity and its drops

### Blocks
- **Pewen Branch** - A spawnable surface for Subterranodons
- **Multiple Dinosaur Eggs Block** - Custom egg block with 1-4 eggs
  - Currently need to implement a simplified version
- **Cave Paintings** - Decorative blocks showing Subterranodon art
  - cave_painting_subterranodon.json
  - cave_painting_subterranodon_ride.json

### Entity Interactions
- **ACTagRegistry.SUBTERRANODON_FLEES** - Tag for entities the Subterranodon flees from
  - Current implementation: Empty tag, no flee behavior
  - Future: Define which entities should trigger flee behavior

### World Generation Features
- **Subterranodon Roost Feature** - Natural nesting areas
  - Files: SubterranodonRoostFeature.java
  - Worldgen JSONs in data/alexscaves/worldgen/
  - Future: Implement as a custom structure/feature

### Boss Dependencies  
- **ACWorldData.isPrimordialBossDefeatedOnce()** - Spawn condition check
  - Currently removed from spawn rules
  - Future: Implement custom boss progression system

### Advanced Mod Systems

#### Citadel Mod Dependencies
- **IDancesToJukebox** - Interface for dancing to music discs
  - Partially implemented with stub
- **IAdvancedPathingMob** - Advanced pathfinding interface
  - Partially implemented with stub
- **AdvancedPathNavigate** - Multithreaded pathfinding system
  - Replaced with vanilla PathNavigation
  - Future: Implement custom pathfinding for better flying behavior

#### Forge API Replacements Needed
- **Forge Event System** - Used for custom entity events
  - Need to implement vanilla event handling
- **Network Message System** - For client-server synchronization
  - MountedEntityKeyMessage for mount controls
  - Need custom packet implementation

#### Custom Rendering
- **Citadel Server Entity Collision** (ICustomCollisions)
  - Custom collision box handling for riders
  - Partially adapted to vanilla

### Advancements
- **tame_subterranodon.json** - Achievement for taming
  - Future: Integrate into vanilla advancement system

### Sounds
All Subterranodon sounds need to be registered:
- subterranodon_idle (4 variants)
- subterranodon_hurt (3 variants)
- subterranodon_death (3 variants)
- subterranodon_attack (4 variants)
- subterranodon_flap (6 variants)

### Loot Tables
- entities/subterranodon.json - Dropped items on death
- blocks/subterranodon_egg.json - Egg block drops
- blocks/cave_painting_*.json - Cave painting drops

### Textures and Models
- Entity textures (3 variants):
  - subterranodon.png (base)
  - subterranodon_retro.png
  - subterranodon_tectonic.png
- Egg textures (cracked variants)
- Spawn egg texture
- Cave painting textures

### Language Files
Need translation entries for:
- Entity name: "entity.alexscaves.subterranodon"
- Spawn egg: "item.alexscaves.spawn_egg_subterranodon"
- Egg block: "block.alexscaves.subterranodon_egg"
- Cave paintings
- Book entries (9 language variants)

## Custom Systems to Implement

### Pack Behavior
- PackAnimal interface for group flying
- Formation flying with leader/follower dynamics
- Synchronized landing when pack leader lands

### Flight Mechanics
- Custom flying AI with hovering
- Stamina/meter system for controlled flight
- Keybind-based altitude control (up/down keys)
- Flight collision handling for mounted riders
- Custom move controller for flight physics

### Egg Laying and Hatching
- LaysEggs interface
- Egg burying animation
- Hatching mechanics with crack stages
- Baby dinosaurs tamed if hatched from player-placed eggs

### Mounting System
- Custom riding position and animation
- FlyingMount interface
- KeybindUsingMount for player controls
- RidingMeterMount for stamina display
- Rider collision detection to prevent getting stuck

### Advanced AI Goals
- SubterranodonFlightGoal - Complex flight pathfinding
- SubterranodonFleeGoal - Fleeing from threats while flying
- SubterranodonFollowOwnerGoal - Flying to follow owner
- AnimalJoinPackGoal - Pack formation
- AnimalBreedEggsGoal - Egg-based breeding
- AnimalLayEggGoal - Egg laying behavior

### Alternative Skins
- Alt skin system (retro and tectonic variants)
- Skin selection mechanism

## Notes for Future Development

1. The Subterranodon is designed as part of the "Primordial Caves" biome system
2. It interacts with other AlexsCaves entities (fleeing, pack behavior)
3. The mod uses Citadel library for advanced entity features
4. Forge-specific features need vanilla or Fabric equivalents
5. The entity has complex client-side rendering with custom models and animations
6. Network synchronization is critical for mount controls and flight behavior

## Implementation Priority

### High Priority (Core Functionality)
1. ✅ Basic entity class structure
2. ✅ Entity registration
3. ⬜ Spawn egg functionality
4. ⬜ Basic AI and movement
5. ⬜ Textures and basic rendering

### Medium Priority (Enhanced Features)
1. ⬜ Flight mechanics
2. ⬜ Mounting/riding system
3. ⬜ Taming mechanics
4. ⬜ Sound effects
5. ⬜ Egg laying and hatching

### Low Priority (Polish and Integration)
1. ⬜ Pack behavior
2. ⬜ Alternative skins
3. ⬜ Advanced pathfinding
4. ⬜ World generation features
5. ⬜ Advancements
6. ⬜ Cave paintings
7. ⬜ Full localization

## Technical Challenges

1. **Multithreaded Pathfinding**: The mod uses AdvancedPathNavigate from Citadel for better performance
2. **Client-Server Synchronization**: Mount controls require proper packet handling
3. **Custom Collision Boxes**: Rider collision detection needs special handling
4. **Complex Animation State**: Multiple overlapping animations (flying, hovering, flapping, biting)
5. **Pack AI Coordination**: Leader-follower flight formation requires sophisticated AI
