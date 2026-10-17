# Blue Jay

Blue Jays can briefly follow a player who gives them seeds and sing to reveal nearby monsters with [Glowing](../effects/VisibilityEffects.md#glowing). This is a temporary feeding relationship, rather than permanent pet ownership. Dropping seeds is the clearest way to trigger both benefits without the normal breeding interaction taking priority.

## At a glance

- **Entity ID:** `minecraft:blue_jay`
- **Health:** 10 points (5 hearts)
- **Base attack attribute:** 1 point (half a heart)
- **Registered adult size:** 0.5 blocks wide × 0.5 blocks tall

## Obtaining

The [Blue Jay Spawn Egg](../items/BlueJaySpawnEgg.md) is registered and explicitly listed in Creative. With command permission, use `/summon minecraft:blue_jay`.

**Natural spawning is not established in this snapshot.** The source has a brightness predicate and a spawn-obstruction check for leaves, logs, or Grass Blocks, but no Blue Jay entry was found in the active biome spawn data, biome-building code, or spawn-placement registrations. Those checks alone do not provide a place to find wild birds.

## Seeds, following, and song

The bundled `minecraft:parrot_food` tag contains **Wheat Seeds, Melon Seeds, Pumpkin Seeds, Beetroot Seeds, Torchflower Seeds, and Pitcher Pods**. [Wheat farming](../blocks/Wheat.md) is one ordinary way to replenish seeds. Only **Wheat Seeds** are explicitly used by the held-food attraction goal.

To recruit a temporary companion:

1. Drop a tagged seed yourself near a Blue Jay with an empty beak
2. Let it collect the item; the pickup goal waits until the dropped item is more than 40 game ticks old
3. Collection heals up to **3 health points**, sets you as its last feeder, starts **1,200 game ticks** of following, and starts a **40-tick song**

At 20 ticks per second, those timers are about one minute and two seconds. The bird takes one item from a dropped stack per pickup. Ordinary dropped food also heals it, but only tagged seeds with a recorded thrower grant the following/song effects.

When a song starts, the bird applies **Glowing for 1,200 ticks** to currently loaded living entities implementing the monster `Enemy` category within a box extending about 64 blocks horizontally from it, from Y=-64 to Y=320. This is a scan at song startup, not continuous detection of every monster that enters later. The old special blue-color client handler has been removed; the active effect is Glowing.

Direct feeding behaves differently. Normal animal feeding is checked first, so an eligible adult enters love mode and a chick grows faster before the special following/song branches are considered. When that first interaction does not consume the action, a tagged seed can start following if its timer has expired, or start a song while it is already following. Dropped seeds avoid that breeding priority.

**Following is not saved reliably across a reload:** the last-feeder UUID is saved, but the feeding countdown is not. There is no owner-only sit command or permanent player-taming path here.

## Breeding and behavior

Feed two eligible adults any of the six tagged seeds to breed a live Blue Jay chick. The same food speeds chick growth. Babies cannot enter the class's flying state.

Unfed birds have an avoidance goal for non-Creative players. Temporary trust suppresses starting that avoidance goal. They have a melee goal for acquired targets and a retaliation goal that excludes player attackers; this does not make them a general-purpose player guard. Their active melee goal pecks for 1 damage point through the current server damage wrapper.

## Raccoon partnership limitation

The code can connect a recently fed Blue Jay to a [Raccoon](Raccoon.md) given teaming food by the same player, either directly or as a dropped item. That route requires `minecraft:raccoon_teaming_foods`, which has **no bundled item-tag definition** in this snapshot. No ordinary item can therefore be recommended for establishing the partnership without additional data.

If a data pack enables that route, the linked Blue Jay follows the raccoon, can perch on it, and can copy its combat target. The link is saved. These conditional behaviors do not establish a bundled Survival taming recipe.

## Drops and verification

No dedicated Blue Jay death-loot table or unique Blue Jay resource item was found. This guide establishes no feather or other species-specific death reward.

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`. Spawning, following, song visuals, breeding, combat, and reloading were not tested in-game.

## Related pages

- [Crow](Crow.md)
- [Raccoon](Raccoon.md)
- [Mobs](Mobs.md)

## Sources

- [Goals, spawning checks, and breeding food](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L98-L142)
- [Song timing and monster scan](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L240-L306)
- [Attributes, saves, feeding, offspring, and removed visual handler](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L408-L635)
- [Avoidance, temporary trust, and following](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L732-L953)
- [Parrot-food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/parrot_food.json)
- [Dropped-item pickup and one-item consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L145)
- [Normal animal feeding and breeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L227)
- [Melee goal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/BlueJayAIMelee.java#L48-L71)
- [Active damage wrapper](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1784)
- [Raccoon direct partnership interaction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L192-L199)
- [Raccoon pickup and partnership caller](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L583-L649)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L329-L331)
- [Attribute wiring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L131)
- [Item registry](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1809)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1985)
- [Spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Bundled item tags](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item)
- [Biome spawn data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome)
- [Biome-building code](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/biome)
- [Entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
