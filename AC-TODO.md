# AlexsCaves Integration TODO

This document tracks features from the AlexsCaves Subteranodon that cannot be fully implemented due to missing dependencies from the AlexsCaves mod.

## Subteranodon - Unimplemented Features

### Item Dependencies
The following AlexsCaves items are referenced but not available:
- **Trilocaris Tail** - Raw food item for taming/feeding
- **Cooked Trilocaris Tail** - Cooked food item for taming/feeding
- **Extinction Spear** - Weapon that can target Subteranodon

**Workaround:** Using vanilla COD and COOKED_COD as taming/feeding items only.

### Block Dependencies  
The following AlexsCaves blocks are referenced but not available:
- **Pewen Branch** - Tree branch that Subterranodon can spawn on
- **Subterranodon Egg Block** - Multi-egg block system with hatching mechanics
- **Cave Painting blocks** - Decorative blocks featuring Subterranodon

**Workaround:** 
- Spawn on vanilla leaves/grass only
- Egg laying system may be simplified or removed
- Cave paintings not implemented

### Biome Dependencies
The following AlexsCaves biomes are required but not available:
- **Primordial Caves** - Natural spawning biome for Subterranodon

**Workaround:** No natural biome spawning. Spawn egg only.

### Entity Dependencies
Interactions with other AlexsCaves entities:
- **Trilocaris** - Food source mob referenced in feeding behavior
- **Other dinosaurs** - Pack behavior and species interactions
- **Dinosaur Spirit** - Ghost form after death in certain conditions
- **Tremorsaurus** - Can grab Subterranodon

**Workaround:** These interactions cannot be implemented.

### Tag Dependencies
The following data tags are referenced:
- `alexscaves:dinosaurs_spawnable_on` - Valid spawn surfaces
- `alexscaves:subterranodon_flees` - Entities that scare Subterranodon

**Workaround:** Create equivalent minecraft: namespace tags or hardcode values.

### World Data Dependencies
- **ACWorldData** - Tracks primordial boss defeat for spawn rules
- **Post-boss spawning** - Some spawn rules require boss defeat

**Workaround:** Simplified spawn rules without boss requirements.

### Advancement Dependencies
- **Tame Subterranodon** advancement - References AC advancement system
- **Animal Book** entries - In-game guidebook entries

**Workaround:** Standard vanilla advancements can be created separately.

### Gameplay Feature Limitations

#### 1. Egg System
**Original:** Complex multi-egg block with 1-4 eggs, cracking states, hatching mechanics
**Status:** TBD - May implement simplified version or skip

#### 2. Pack Behavior
**Original:** Subterranodon can form packs, follow pack leaders, coordinated flight
**Status:** TBD - Requires PackAnimal system implementation

#### 3. Mounting/Riding
**Original:** Complex riding system with:
- Flight meter/stamina system
- Up/down controls via keybinds
- Slow hover mode vs fast flight
- Rider positioning that changes with flight state

**Status:** Will attempt to implement with simplified controls

#### 4. Taming
**Original:** Tame by feeding Trilocaris Tail with random chance
**Implemented:** Using COD/COOKED_COD instead

#### 5. Breeding
**Original:** Breed with Trilocaris Tail, lays eggs in egg blocks
**Status:** TBD - May use standard vanilla breeding or skip

#### 6. Variants
**Original:** 3 texture variants (normal, retro, tectonic)
**Status:** All textures available, will implement

#### 7. AI Behaviors
**Original:**
- Flee from certain entities
- Follow tamed owner (even while flying)
- Complex 3D flight pathfinding
- Hovering behavior
- Landing behavior

**Status:** Will implement as much as possible, may simplify pathfinding

## Implementation Notes

### Forge API Replacements
The following Forge mod loader features need vanilla equivalents:

1. **DeferredRegister system** → Direct vanilla registration
2. **Network packet system** → Custom packet handling or simplified
3. **Client proxy system** → Client-side checks  
4. **Event bus** → Direct method calls where applicable

### Citadel Library Dependencies
The following Citadel library classes are required:

**Required (Will Port):**
- `AdvancedEntityModel` - For exact model rendering
- `AdvancedModelBox` - Model part system
- `AdvancedPathNavigate` - 3D pathfinding for flying
- `ICustomCollisions` - Custom collision handling
- `IDancesToJukebox` - Jukebox dance behavior
- `IAdvancedPathingMob` - Pathfinding interface

**Optional/TBD:**
- Various utility classes (ACMath, ColorUtil, etc.)

## Future Integration Candidates

If more AlexsCaves content is integrated in the future:

1. **Trilocaris** mob - Would complete the Subterranodon food chain
2. **Other Primordial Cave dinosaurs** - Vallumraptor, Grottoceratops, etc.
3. **Primordial Caves biome** - Would allow natural spawning
4. **Pewen Trees** - Would provide proper nesting/perching locations
5. **Extinction Spear** - Specialized weapon for hunting

## Testing Checklist

When implementation is complete, verify:
- [ ] Spawn egg works and spawns entity
- [ ] Entity renders with correct model
- [ ] Entity has correct hitbox and collision
- [ ] Entity plays sounds (idle, hurt, death, attack, flap)
- [ ] Entity can walk on ground
- [ ] Entity can fly
- [ ] Entity can be tamed with COD
- [ ] Tamed entity can be mounted
- [ ] Mounted flight controls work (forward, turn, up, down)
- [ ] Flight stamina meter works
- [ ] Entity can attack (bite animation + damage)
- [ ] Entity can sit when ordered
- [ ] Baby entities work correctly
- [ ] Entity persists across world save/load
- [ ] All 3 texture variants work

## Known Limitations

1. **No natural spawning** - Spawn egg only, no biome spawns
2. **Limited food options** - Only vanilla fish, no Trilocaris
3. **No cave paintings** - Decorative blocks not implemented
4. **Simplified egg system** - May not match original complexity
5. **No spirit form** - Death mechanics simplified
6. **No cross-mod interactions** - Cannot interact with other AC content

## Version Information

- **Source Mod:** AlexsCaves 1.21.1
- **Source Dependency:** Citadel 1.21
- **Target:** MattMC (Minecraft 1.21.10 fork)
- **Integration Date:** 2026-01-11
