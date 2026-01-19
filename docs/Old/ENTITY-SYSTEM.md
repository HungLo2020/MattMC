# Entity System

## Overview

The entity system is the foundation for all dynamic objects in Minecraft - players, mobs, items, projectiles, and vehicles. The system provides lifecycle management, physics simulation, AI behaviors, pathfinding, and network synchronization.

## Architecture

```
┌─────────────────────────────────────────┐
│         Entity Base (Entity.java)        │
│  - Position & Movement                   │
│  - Collision Detection                   │
│  - Data Synchronization                  │
│  - Tick Logic                            │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌──────────┐    ┌──────────────┐
│ Living   │    │  Non-Living  │
│ Entity   │    │  Entities    │
└────┬─────┘    └──────┬───────┘
     │                 │
     ├─── Mob         ├─── Item
     ├─── Player      ├─── Projectile
     ├─── Animal      ├─── Vehicle
     └─── Monster     └─── Display
```

## Core Components

### 1. Entity Base Class

**Location**: `net.minecraft.world.entity.Entity`

The root class for all entities (~3,600 lines).

**Key Responsibilities**:
- Position and movement
- Collision detection and physics
- World interaction
- Network synchronization
- Persistence (save/load)
- Tick updates

**Core Fields**:
- `position`: Current Vec3 position (double precision)
- `blockPosition`: BlockPos for current block
- `deltaMovement`: Velocity vector
- `xRot`, `yRot`: Pitch and yaw rotation
- `onGround`: Ground contact flag
- `level`: World reference
- `uuid`: Unique identifier
- `passengers`: Riding entities
- `vehicle`: Entity being ridden

**Core Methods**:
- `tick()`: Called every game tick (20 TPS)
- `move()`: Apply movement with collision
- `checkInsideBlocks()`: Block collision effects
- `hurt()`: Damage processing
- `kill()`: Entity removal
- `startRiding()`, `stopRiding()`: Vehicle system
- `save()`, `load()`: NBT persistence

**Entity Types**:
Defined in `EntityType` registry, includes:
- Living entities (mobs, players)
- Items and experience orbs
- Projectiles (arrows, fireballs)
- Vehicles (boats, minecarts)
- Display entities (item/block/text displays)
- Area effects (TNT, area effect clouds)

### 2. Living Entity

**Location**: `net.minecraft.world.entity.LivingEntity`

Base for all entities with health, AI, and combat.

**Features**:
- **Health System**: HP, damage, death
- **Attributes**: Max health, movement speed, attack damage, armor, etc.
- **Effects**: Potion effects (buffs/debuffs)
- **Equipment**: Armor, held items
- **AI System**: Goal-based AI
- **Animation**: Limb swing, hurt animation
- **Combat**: Attack cooldown, knockback

**Key Fields**:
- `health`: Current health points
- `activeEffects`: Active potion effects
- `attackTimer`: Melee attack cooldown
- `hurtTime`: Damage flash duration
- `deathTime`: Death animation timer
- `lastHurtByPlayer`: For XP drops

**Key Methods**:
- `hurt()`: Process damage
- `die()`: Death handling
- `heal()`: Restore health
- `addEffect()`, `removeEffect()`: Potion effects
- `travel()`: Movement with physics
- `aiStep()`: AI tick processing

### 3. Mob Entity

**Location**: `net.minecraft.world.entity.Mob`

Base for all AI-controlled entities.

**Features**:
- **AI Goals**: Priority-based behavior system
- **Targeting**: Enemy selection
- **Navigation**: Pathfinding
- **Sensing**: Entity detection
- **Leashing**: Lead mechanics
- **Persistence**: Despawn rules

**AI Components**:

**GoalSelector**: Priority queue of AI goals
```java
goalSelector.addGoal(0, new FloatGoal(this));         // Swim
goalSelector.addGoal(1, new PanicGoal(this, 2.0D));   // Run when hurt
goalSelector.addGoal(2, new BreedGoal(this, 1.0D));   // Breeding
goalSelector.addGoal(3, new TemptGoal(this, ...));    // Follow player with food
goalSelector.addGoal(4, new FollowParentGoal(this, ...)); // Baby follows adult
goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
goalSelector.addGoal(6, new LookAtPlayerGoal(this, ...));
goalSelector.addGoal(7, new RandomLookAroundGoal(this));
```

**TargetSelector**: Enemy selection goals
```java
targetSelector.addGoal(1, new HurtByTargetGoal(this));
targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
```

**Navigation System**:
- **PathNavigation**: High-level pathfinding
- **Path**: Sequence of path points
- **NodeEvaluator**: Path cost calculation
- Types: Ground, Flying, Climbing, Swimming

**Sensing System**:
- **Sensing**: Detects nearby entities
- Vision range
- Memory of seen entities

### 4. Entity AI System

**Location**: `net.minecraft.world.entity.ai`

Sophisticated goal-based AI framework.

#### AI Goals

**Location**: `net.minecraft.world.entity.ai.goal`

Individual behavior units with priorities.

**Common Goals**:

**Movement Goals**:
- `WaterAvoidingRandomStrollGoal`: Random wandering
- `RandomLookAroundGoal`: Look in random directions
- `LookAtPlayerGoal`: Track player with head
- `FloatGoal`: Swim when in water
- `PanicGoal`: Run away when hurt
- `FollowParentGoal`: Baby follows adult
- `FollowOwnerGoal`: Tamed pet follows owner

**Combat Goals**:
- `MeleeAttackGoal`: Melee combat
- `RangedAttackGoal`: Ranged attacks
- `RangedBowAttackGoal`: Bow shooting
- `RangedCrossbowAttackGoal`: Crossbow shooting
- `AvoidEntityGoal`: Run from threats

**Interaction Goals**:
- `TemptGoal`: Follow player holding food
- `BreedGoal`: Mating behavior
- `BegGoal`: Dog begging
- `SitWhenOrderedToGoal`: Pet sitting
- `FollowBoatGoal`: Follow nearby boats

**Target Goals**:
- `NearestAttackableTargetGoal`: Select nearest enemy
- `HurtByTargetGoal`: Retaliate when hurt
- `DefendVillageTargetGoal`: Protect village
- `NonTameRandomTargetGoal`: Wild animal targeting

**Goal System**:
- Goals have priorities (lower = higher priority)
- Running goals can interrupt lower priority goals
- Goals can have flags (movement, jumping, looking)
- Mutex flags prevent conflicting behaviors

#### Pathfinding

**Location**: `net.minecraft.world.entity.ai.navigation`

A* pathfinding with terrain-aware cost calculation.

**PathNavigation Types**:
- **GroundPathNavigation**: Walking entities
- **FlyingPathNavigation**: Flying entities (parrots, ghasts)
- **WallClimberNavigation**: Climbing entities (spiders)
- **WaterBoundPathNavigation**: Aquatic entities

**Path Finding**:
- **PathFinder**: A* algorithm implementation
- **BinaryHeap**: Priority queue for open set
- **Node**: Individual pathfinding node
- **NodeEvaluator**: Calculates traversal cost

**Node Types**:
- `OPEN`: Walkable
- `BLOCKED`: Impassable
- `WALKABLE_DOOR`: Can open doors
- `DAMAGE_FIRE`: Fire damage
- `DANGER_FIRE`: Avoid fire
- `WATER`: Water blocks
- `LAVA`: Lava blocks

**Path Following**:
- Advance along path points
- Collision avoidance
- Stuck detection and replanning
- Distance threshold for reaching nodes

#### Brain System

**Location**: `net.minecraft.world.entity.ai.Brain`

Advanced AI for villagers, piglins, etc.

**Features**:
- **Memory**: Stores AI state
- **Sensors**: Updates memories
- **Behaviors**: Conditional actions
- **Schedules**: Time-based activities

**Memory Modules**:
- `NEAREST_VISIBLE_PLAYER`
- `NEAREST_LIVING_ENTITIES`
- `PATH`
- `ATTACK_TARGET`
- `HOME`
- `JOB_SITE`
- `MEETING_POINT`

**Sensors**:
- `NearestLivingEntitySensor`
- `PlayerSensor`
- `VillagerHostilesSensor`
- `GolemSensor`

**Behaviors** (Activities):
- **IDLE**: Default state
- **WORK**: Job-related tasks
- **PLAY**: Child villagers
- **REST**: Sleeping
- **MEET**: Gathering
- **PANIC**: Fleeing

### 5. Entity Attributes

**Location**: `net.minecraft.world.entity.ai.attributes`

Flexible stat system for entities.

**Common Attributes**:
- `MAX_HEALTH`: Maximum HP
- `MOVEMENT_SPEED`: Walking speed
- `ATTACK_DAMAGE`: Melee damage
- `ARMOR`: Damage reduction
- `ARMOR_TOUGHNESS`: High-damage reduction
- `ATTACK_KNOCKBACK`: Knockback strength
- `ATTACK_SPEED`: Attack cooldown
- `KNOCKBACK_RESISTANCE`: Resist being knocked back
- `FOLLOW_RANGE`: AI detection range
- `FLYING_SPEED`: Flight speed
- `JUMP_STRENGTH`: Jump height (horses)

**Attribute Modifiers**:
- Base value
- Additive modifiers
- Multiplicative modifiers
- Applied from equipment, effects, etc.

### 6. Entity Data Synchronization

**Location**: `net.minecraft.network.syncher.SynchedEntityData`

Efficient client-server entity data sync.

**How It Works**:
1. Entity defines data parameters (health, pose, flags, etc.)
2. Server tracks changes each tick
3. Only changed data sent to clients
4. Clients apply updates smoothly

**Common Synced Data**:
- Entity flags (onFire, crouching, sprinting, etc.)
- Health (for living entities)
- Air supply
- Custom name
- Pose (standing, sleeping, swimming, etc.)
- Entity-specific data

**Entity Flags** (byte bitfield):
- Bit 0: On fire
- Bit 1: Crouching
- Bit 2: Unused (previously riding)
- Bit 3: Sprinting
- Bit 4: Swimming
- Bit 5: Invisible
- Bit 6: Glowing
- Bit 7: Fall flying (elytra)

### 7. Collision System

**Location**: Entity.move(), Entity.checkCollision()

Physics-based collision detection.

**Collision Types**:
- **Block Collision**: AABB vs world blocks
- **Entity Collision**: AABB vs entity AABBs
- **Liquid Collision**: Water/lava physics

**AABB** (Axis-Aligned Bounding Box):
- Rectangular hitbox
- Size varies by entity type
- Used for all collision checks

**Movement**:
1. Calculate desired movement vector
2. Test collisions in each axis (X, Y, Z)
3. Reduce movement if collision detected
4. Apply final position
5. Update velocity for next tick

**Special Cases**:
- Stairs: Allow stepping up
- Slabs: Half-block collision
- Water: Swimming/floating physics
- Cobwebs: Slow movement
- Soul sand: Sink slightly

### 8. Entity Types

#### Players

**Location**: `net.minecraft.world.entity.player.Player`

Human-controlled entities.

**Features**:
- Inventory management
- Crafting
- Mining/breaking blocks
- Game mode (survival, creative, etc.)
- Experience and levels
- Hunger system
- Abilities (flight in creative)

**ServerPlayer** vs **LocalPlayer**:
- ServerPlayer: Server-side representation
- LocalPlayer: Client-side with input handling

#### Animals

**Location**: `net.minecraft.world.entity.animal`

Passive mobs with breeding.

**Examples**: Cow, Pig, Sheep, Chicken, Horse

**Features**:
- Breeding with same type
- Baby growth
- Temptation with food items
- Can be leashed

#### Monsters

**Location**: `net.minecraft.world.entity.monster`

Hostile mobs attacking players.

**Examples**: Zombie, Skeleton, Creeper, Spider, Enderman

**Features**:
- Attack behaviors
- Target selection (players, villagers)
- Despawn in peaceful difficulty
- Spawn in darkness

#### Villagers

**Location**: `net.minecraft.world.entity.npc.Villager`

Complex NPCs with professions.

**Features**:
- Profession system (farmer, librarian, etc.)
- Trading interface
- Workstation interaction
- Village gossip system
- Daily schedules (work, sleep, socialize)
- Zombie conversion

#### Projectiles

**Location**: `net.minecraft.world.entity.projectile`

Entities that fly and hit targets.

**Examples**: Arrow, Snowball, Fireball, ThrownTrident

**Features**:
- Physics simulation
- Collision detection
- Owner tracking
- Damage/effect on hit
- Pickup (arrows)

### 9. Entity Lifecycle

**Spawn**:
1. Create entity instance
2. Set position and rotation
3. Add to world
4. Send spawn packet to clients

**Tick** (every 1/20 second):
1. Update position/rotation
2. Process AI (for mobs)
3. Apply effects
4. Check collisions
5. Update age/timers
6. Sync changed data to clients

**Death**:
1. Play death animation
2. Drop items/experience
3. Remove from world
4. Send despawn packet to clients

**Persistence**:
- Entities saved to world file as NBT
- UUID preserved
- Position, health, equipment saved
- AI state not saved (resets on load)

### 10. Special Entity Systems

#### Equipment

Living entities can wear armor and hold items.

**EquipmentSlot**:
- MAINHAND
- OFFHAND
- HEAD
- CHEST
- LEGS
- FEET
- BODY (horse armor)

#### Entity Attachments

Define positions relative to entity for:
- Camera position
- Passenger positions
- Name tag position
- Leash position

#### Riding System

Entities can ride other entities (boats, horses, minecarts).

**Features**:
- Passenger list
- Position offset
- Rotation inheritance
- Dismount mechanics

## Performance Considerations

**Optimization Techniques**:
1. **Entity Culling**: Don't tick distant entities
2. **Lazy Loading**: Load entities only when chunk loads
3. **Despawning**: Remove old item entities, far mobs
4. **Mob Cap**: Limit total mob count
5. **Pathfinding Cache**: Reuse recent paths
6. **AI Skipping**: Skip AI for distant mobs

**Entity Tracking**:
- Entities tracked within view distance
- Update rate varies by distance
- Position delta encoding saves bandwidth

## Key Files

- `Entity.java`: Base entity class (3,600+ lines)
- `LivingEntity.java`: Living entity base (3,000+ lines)
- `Mob.java`: AI entity base (1,000+ lines)
- `Player.java`: Player entity (1,400+ lines)
- `EntityType.java`: Entity type registry (2,000+ lines)
- `Brain.java`: Brain-based AI (600+ lines)
- `GoalSelector.java`: AI goal manager
- `PathNavigation.java`: Pathfinding controller

## Related Systems

- [Networking System](NETWORKING-SYSTEM.md) - Entity data synchronization
- [Render System](RENDER-SYSTEM.md) - Entity rendering
- [World System](WORLD-GENERATION-SYSTEM.md) - Entity spawning
- [Inventory System](INVENTORY-SYSTEM.md) - Entity equipment
