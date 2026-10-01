# Tasmanian Devil

Tasmanian Devils can turn dropped Bones into Bone Meal and howl when directly fed Rotten Flesh. Their howl disrupts nearby monsters for a short time. They remain unowned animals with hunting and retaliation behavior; food does not unlock pet commands.

## At a glance

- **Entity ID:** `minecraft:tasmanian_devil`
- **Health:** 14 points (7 hearts)
- **Base melee damage:** 2 points (1 heart), before modifiers
- **Registered adult size:** 0.7 blocks wide × 0.6 blocks tall

## Obtaining

The [Tasmanian Devil Spawn Egg](../items/TasmanianDevilSpawnEgg.md) is registered and explicitly listed in Creative. With command permission, use `/summon minecraft:tasmanian_devil`.

**Natural spawning is not established.** No Tasmanian Devil entry was found in the reviewed biome spawn tables, biome-building code, or spawn-placement registrations. Its creature registration alone does not identify a place to find it in Survival.

## Feeding: the action matters

| Action | Active behavior |
| --- | --- |
| Use Rotten Flesh directly on it | Starts a howl, consuming one outside Creative |
| Use another item with a food component directly | Normal animal breeding or baby-growth feeding |
| Drop any item with a food component, including Rotten Flesh | It eats one and heals up to 5 health points |
| Drop a Bone | It consumes one and drops 3 Bone Meal |

**Rotten Flesh is the only bundled howling food.** It is excluded from breeding food, but remains edible through the dropped-food route. Dropping it therefore heals the animal without triggering the howl. Holding Rotten Flesh also activates its food-attraction goal.

For healing or Bone conversion, let the dropped item remain long enough for pickup: the installed goal requires it to be more than **30 game ticks old**, about 1.5 seconds at 20 ticks per second. The animal can collect food even when healthy, so avoid leaving supplies you want to keep nearby.

See [Rotten Flesh](../items/RottenFlesh.md) and [Bones](../items/Bone.md) for their ordinary acquisition routes.

## Howling near monsters

Directly feed Rotten Flesh when the animal is not already howling. The howl animation lasts **40 ticks**, about two seconds, and temporarily makes it sit. Feeding another howling item during that animation does not start a second howl.

During most of the howl and for a short period afterward, it scans a box extending **16 blocks horizontally and 8 vertically** from its body for mobs in the `Monster` class. It clears their attack targets and remembered attackers, and periodically asks them to navigate away.

This is a brief disruption, not a protective field that prevents damage or spawning. Monsters can have other attack behavior, navigation may fail, and mobs outside the checked class or area are unaffected. The howl does not tame either the Devil or the monsters.

## Bone Meal conversion

Drop a [Bone](../items/Bone.md) where the Devil can collect it. The active pickup callback consumes **one Bone** and drops **three separate Bone Meal items**. Bones do not provide the food-healing branch.

The output is ordinary [Bone Meal](../items/BoneMeal.md), with its normal growth and crafting uses. The usual crafting recipe already turns one Bone into three Bone Meal, so using a Devil provides **no yield bonus**. No unique Devil resource item is registered for this interaction.

## Breeding and behavior

Feed two eligible adults items with a food component other than Rotten Flesh to put them into love mode. The registered breeding goal creates a live Tasmanian Devil baby. The same food check supports ordinary baby-growth feeding. This check accepts more than meat; it does not use a separate meat-only tag.

There is no taming or player-ownership path in the class. Sitting and basking are idle behavior, and an animal with a combat target or in love gets up again. Keep them separate from **Chickens and Rabbits**, which their active target goal hunts. They can also retaliate when attacked.

Their registered ordinary melee goal uses the current inherited attack method. The class also contains an older animation-triggering attack method with the wrong signature for that goal, so its custom animated knockback strike should not be assumed to accompany normal combat. This mismatch does not make the animal harmless.

## Rewards and integration limits

**Do not plan a Bone farm around their kills.** A method describing a 50% Bone reward after killing animals or undead uses an obsolete callback name and is not invoked by the current death path. The confirmed Bone Meal route requires Bones you already supply.

No dedicated Tasmanian Devil death-loot table or species-specific resource item was found. The bone-processing behavior is live item conversion, not a verified death drop.

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game spawning, howling, monster behavior, feeding, conversion, breeding, or combat test was run.

## Related pages

- [Tiger](Tiger.md)
- [Bone Meal](../items/BoneMeal.md)
- [Mobs](Mobs.md)

## Sources

- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1363-L1369)
- [Attribute wiring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L247)
- [Spawn egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1972)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2101)
- [Goals, obsolete kill hook, stats, and food checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTasmanianDevil.java#L83-L156)
- [Idle behavior, howl, and monster disruption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTasmanianDevil.java#L158-L234)
- [Direct interaction, animation, breeding, and item conversion](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTasmanianDevil.java#L236-L325)
- [Howling-food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/tasmanian_devil_howling_foods.json)
- [Shared pickup and one-item consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L145)
- [Current melee-goal call](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L144)
- [Inherited melee damage](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320)
- [Current death callback dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1435)
- [Current killedEntity method](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2761-L2763)
- [Animal feeding and breeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L227)
- [Bone Meal crafting recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/bone_meal.json)
- [Bone Meal use](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77)
- [Animation progression](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/citadel/animation/AnimationHandler.java#L41-L64)
- [Spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Biome spawn data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome)
- [Biome-building code](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/biome)
- [Entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
