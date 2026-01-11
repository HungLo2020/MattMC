# Subteranodon Integration Implementation Guide

## Overview
This guide provides step-by-step instructions to complete the Subteranodon integration after the initial code and asset copying.

## Current State
- ✅ All Citadel library code copied (178 files)
- ✅ All AlexsCaves code copied (942 files)  
- ✅ All Subteranodon assets copied and ready (textures, sounds, models, data)
- ❌ Code does not compile (1000+ Forge API errors)
- ❌ Entity not registered in vanilla systems
- ❌ Spawn egg not created

## Implementation Steps

### Step 1: Clean Up Non-Essential Files (1-2 hours)

Delete all AlexsCaves files EXCEPT these 18 core files:

**Keep:**
```
src/main/java/com/github/alexmodguy/alexscaves/
├── AlexsCaves.java (modify to remove Forge mod loader code)
├── client/
│   ├── model/SubterranodonModel.java
│   └── render/entity/
│       ├── SubterranodonRenderer.java
│       └── layer/SubterranodonRiderLayer.java
└── server/
    ├── entity/
    │   ├── living/
    │   │   ├── DinosaurEntity.java
    │   │   └── SubterranodonEntity.java
    │   ├── ai/
    │   │   ├── SubterranodonFleeGoal.java
    │   │   ├── SubterranodonFlightGoal.java
    │   │   ├── SubterranodonFollowOwnerGoal.java
    │   │   ├── AnimalJoinPackGoal.java
    │   │   ├── AnimalBreedEggsGoal.java
    │   │   ├── AnimalLayEggGoal.java
    │   │   └── AdvancedPathNavigateNoTeleport.java
    │   └── util/
    │       ├── PackAnimal.java
    │       ├── FlyingMount.java
    │       ├── KeybindUsingMount.java
    │       ├── LaysEggs.java
    │       └── RidingMeterMount.java
```

**Delete:** All other 924 AlexsCaves files

Also review Citadel - keep only what's referenced by the above files.

### Step 2: Create Forge API Stub Package (2-3 hours)

Create `src/main/java/net/neoforged/` package with minimal stubs:

**File: `net/neoforged/api/distmarker/Dist.java`**
```java
package net.neoforged.api.distmarker;
public enum Dist {
    CLIENT, DEDICATED_SERVER
}
```

**File: `net/neoforged/api/distmarker/OnlyIn.java`**
```java
package net.neoforged.api.distmarker;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface OnlyIn {
    Dist value();
}
```

**File: `net/neoforged/neoforge/registries/DeferredHolder.java`**
```java
package net.neoforged.neoforge.registries;
public class DeferredHolder<R, T extends R> {
    private final T value;
    public DeferredHolder(T value) { this.value = value; }
    public T get() { return value; }
}
```

**File: `net/neoforged/neoforge/registries/DeferredRegister.java`**
```java
package net.neoforged.neoforge.registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import java.util.function.Supplier;

public class DeferredRegister<T> {
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        return new DeferredRegister<>();
    }
    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<I> supplier) {
        return new DeferredHolder<>(supplier.get());
    }
}
```

Create similar stubs for other required classes as compilation errors are encountered.

### Step 3: Modify AlexsCaves.java (1 hour)

Replace the Forge mod initialization with a simple class:

```java
package com.github.alexmodguy.alexscaves;

import net.minecraft.resources.ResourceLocation;

public class AlexsCaves {
    public static final String MODID = "alexscaves";
    
    public static ResourceLocation prefix(String name) {
        return ResourceLocation.fromNamespaceAndPath(MODID, name);
    }
    
    // Stub proxy for client-side checks
    public static final ClientProxy PROXY = new ClientProxy();
    
    public static class ClientProxy {
        public net.minecraft.client.player.LocalPlayer getClientSidePlayer() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
        public boolean isKeyDown(int key) {
            return false; // Simplified for now
        }
    }
    
    // Stub for sending messages to server
    public static void sendMSGToServer(Object message) {
        // TODO: Implement custom packet system or remove
    }
}
```

### Step 4: Register Entity in EntityType.java (1 hour)

**Location:** `src/main/java/net/minecraft/world/entity/EntityType.java`

Add after existing entity registrations (around line 1000):

```java
// Subteranodon from AlexsCaves
public static final EntityType<com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity> SUBTERRANODON = register(
    "subterranodon",
    EntityType.Builder.of(com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity::new, MobCategory.CREATURE)
        .sized(1.75F, 1.2F)
        .eyeHeight(0.6F)
        .clientTrackingRange(12)
);
```

### Step 5: Create Spawn Egg in Items.java (30 min)

**Location:** `src/main/java/net/minecraft/world/item/Items.java`

Add after existing spawn eggs:

```java
public static final Item SUBTERRANODON_SPAWN_EGG = register(
    "subterranodon_spawn_egg",
    new SpawnEggItem(EntityType.SUBTERRANODON, 0x00B1B2, 0xFFF11C, new Item.Properties())
);
```

### Step 6: Register Sounds in SoundEvents.java (30 min)

**Location:** `src/main/java/net/minecraft/sounds/SoundEvents.java`

Add:

```java
public static final SoundEvent SUBTERRANODON_IDLE = registerSound("alexscaves:subterranodon_idle");
public static final SoundEvent SUBTERRANODON_HURT = registerSound("alexscaves:subterranodon_hurt");
public static final SoundEvent SUBTERRANODON_DEATH = registerSound("alexscaves:subterranodon_death");
public static final SoundEvent SUBTERRANODON_FLAP = registerSound("alexscaves:subterranodon_flap");
public static final SoundEvent SUBTERRANODON_ATTACK = registerSound("alexscaves:subterranodon_attack");

private static SoundEvent registerSound(String name) {
    ResourceLocation id = ResourceLocation.parse(name);
    return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
}
```

### Step 7: Register Renderer (Client-side) (30 min)

**Location:** `src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java`

In the registration method, add:

```java
register(EntityType.SUBTERRANODON, com.github.alexmodguy.alexscaves.client.render.entity.SubterranodonRenderer::new);
```

### Step 8: Fix Remaining Compilation Errors (3-5 hours)

Work through compilation errors in the 18 kept files:

1. **Replace ACEntityRegistry references** with `EntityType.SUBTERRANODON`
2. **Replace ACItemRegistry references** with `Items.COD` or `Items.COOKED_COD`
3. **Replace ACSoundRegistry references** with `SoundEvents.SUBTERRANODON_*`
4. **Replace ACBlockRegistry references** with vanilla blocks or remove
5. **Replace ACTagRegistry references** with vanilla tags or hardcoded checks
6. **Remove or stub ACAdvancementTriggerRegistry calls**
7. **Remove or stub ACWorldData references**
8. **Replace MountedEntityKeyMessage** with simplified input handling

For each Forge event or API call:
- Search for vanilla equivalent hook
- If none exists, create minimal stub or remove feature
- Document in AC-TODO.md if removing

### Step 9: Fix DinosaurEntity Base Class (1-2 hours)

DinosaurEntity extends TamableAnimal and implements several interfaces. Review:

1. **IDancesToJukebox** - Keep if Citadel version works, else remove
2. **IAdvancedPathingMob** - Keep if needed for pathfinding, else simplify
3. **LaysEggs** - Implement or stub out egg laying
4. **RidingMeterMount** - Implement for flight meter

Remove references to:
- ACParticleRegistry → Use vanilla particles
- ACWorldData → Remove boss-gating logic
- ACAdvancementTriggerRegistry → Use vanilla advancements

### Step 10: Fix SubterranodonEntity (2-3 hours)

Key areas to address:

1. **Flight mechanics** - Keep intact, uses vanilla Vec3 and movement
2. **AI Goals** - Verify all referenced goals compile
3. **Mounting system** - Keep exact behavior, may need custom input handling
4. **Collision handling** - Citadel's ICustomCollisions may need vanilla equivalent
5. **Network sync** - Entity data accessors work with vanilla, but remove packet sending

### Step 11: Fix Model and Renderer (1 hour)

SubterranodonModel extends Citadel's AdvancedEntityModel.  
Ensure Citadel model classes compile:

1. **AdvancedEntityModel** - Should work with minor tweaks
2. **AdvancedModelBox** - Should work with minor tweaks
3. **BasicModelPart** - Verify interface compatibility

SubterranodonRenderer - Should work once model is fixed.
SubterranodonRiderLayer - Verify passenger rendering works.

### Step 12: Fix AI Goals (1-2 hours)

Review each goal:

1. **SubterranodonFleeGoal** - Uses ACTagRegistry.SUBTERRANODON_FLEES
   - Create vanilla tag `minecraft:subterranodon_flees` or hardcode entities
2. **SubterranodonFlightGoal** - Complex flight AI, should work as-is
3. **SubterranodonFollowOwnerGoal** - Standard follow owner, may work as-is
4. **AnimalJoinPackGoal** - Pack behavior, review dependencies
5. **AnimalBreedEggsGoal** - Breeding with eggs, may simplify
6. **AnimalLayEggGoal** - Egg laying, may simplify or remove

### Step 13: Create Spawn Placement Rules (30 min)

**Location:** `src/main/java/net/minecraft/world/entity/SpawnPlacements.java`

In the static registration block:

```java
register(EntityType.SUBTERRANODON, 
    SpawnPlacementType.ON_GROUND, 
    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 
    com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity::checkSubterranodonSpawnRules
);
```

### Step 14: Test Build (30 min)

```bash
./gradlew compileJava
```

Fix any remaining errors iteratively.

### Step 15: Test In-Game (1-2 hours)

1. Start Minecraft
2. Use spawn egg: `/give @p subterranodon_spawn_egg`
3. Spawn entity
4. Verify:
   - Entity renders correctly
   - Model animates
   - Sounds play
   - Can walk/fly
   - Can be tamed with cod
   - Can be mounted
   - Flight controls work

### Step 16: Final Polish (1-2 hours)

- Add entity attributes registration if not done
- Verify loot tables work
- Test all 3 texture variants
- Document remaining issues in AC-TODO.md
- Update INTEGRATION-STATUS.md with final status

## Estimated Total Time: 15-25 hours

## Tips for Success

1. **Compile frequently** - Fix errors in small batches
2. **Test iteratively** - Don't wait until everything compiles
3. **Use git commits** - Commit after each working step  
4. **Document changes** - Note all Forge→Vanilla replacements
5. **Keep backups** - Before deleting 900+ files, commit current state

## Common Pitfalls

- Don't delete Citadel files that models depend on
- Don't simplify the model/renderer (requirement)
- Don't remove features without documenting in AC-TODO.md
- Don't skip spawn placement registration (spawn egg won't work)
- Don't forget client-side renderer registration

## Success Criteria

✅ Project compiles with zero errors
✅ Spawn egg exists in creative inventory
✅ Entity spawns from egg
✅ Entity renders with correct model
✅ All sounds work
✅ Entity can walk and fly
✅ Entity can be tamed with cod
✅ Entity can be mounted and controlled
✅ Flight meter/stamina works
✅ All 3 texture variants work

## Final Notes

This is a substantial integration project. The code and assets are ready. The challenge is creating the bridge between Forge mod code and vanilla Minecraft systems. Take it step by step, test frequently, and document thoroughly.
