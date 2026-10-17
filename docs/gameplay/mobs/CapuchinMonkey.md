# Capuchin Monkey

The **Capuchin Monkey** is a small animal with four appearance variants, retaliation behavior, and owner controls for following and sitting. Its head-riding pickup route is blocked by the current server riding check. **The bundled data does not define its taming, breeding, or feeding items**, so these pet features do not yet have a verified food-based Survival route. [Food and interaction code][interactions] · [Variants][variants] · [Tag definitions][tags] · [Bundled item tags][item-tags]

## At a glance

- **Health:** 10 points (5 hearts)
- **Base melee damage attribute:** 2 points (1 heart), before combat modifiers
- **Adult size:** 0.45 blocks wide and 0.7 blocks tall
- **Entity ID:** `minecraft:capuchin_monkey`

[Attributes][attributes] · [Attribute wiring][attribute-wiring] · [Entity registration][registration]

## Obtaining

Use the [Capuchin Monkey Spawn Egg](../items/CapuchinMonkeySpawnEgg.md) in Creative or `/summon minecraft:capuchin_monkey` with command permission. **Natural spawning is not established in this snapshot:** no entry was found in the bundled biome spawn tables, biome-building code, or spawn-placement registrations. Do not treat an upstream jungle-spawning guide as a confirmed MattMC route. [Spawn egg][egg] · [Biome data][biomes] · [Biome-building code][biome-code] · [Spawn placements][placements]

The entity contains a bright-location spawn predicate, but that predicate alone does not add it to a biome. Spawn finalization chooses one of four variants, sharing a variant within a spawn group when that group data is used. [Spawn checks][spawn-checks] · [Variants][variants]

## Taming and feeding limitations

The three item tags `minecraft:capuchin_monkey_tameables`, `minecraft:capuchin_monkey_breedables`, and `minecraft:capuchin_monkey_foodstuffs` have **no definitions in the bundled item-tag data**. No banana, apple, or other item is therefore verified as a working monkey food in this snapshot. [Tags][tags] · [Bundled item tags][item-tags]

If an added data pack populates the tags, the source provides these interactions:

- A wild monkey has a **1-in-5 taming chance** when fed an item from its taming tag, either by interacting directly or by collecting an item with a recorded thrower
- Picking up an accepted item heals **5 health points** (2.5 hearts); acceptance checks all three food tags
- Breeding requires **tamed** monkeys and an item in the breeding tag. The offspring is a capuchin and receives the spawning parent's variant

These describe conditional code paths, not foods available in the bundled data. [Interactions and pickup][interactions] · [Offspring][offspring]

## Owner controls

For a monkey that is already tamed and owned by you, use an empty hand to avoid food and item interactions taking priority:

- Interact normally to cycle **wander → follow → sit**

**Head riding is blocked in this snapshot.** Sneak-interacting while you have no other passenger attempts pickup, but the shared server riding check rejects a player as the vehicle. The pickup handler still reports interaction success without creating a server passenger relationship. The head-positioning routine and 20-tick sneak-dismount cooldown require an existing ride, so they are conditional code paths rather than usable pickup instructions. [Pickup attempt][pickup-attempt] · [Shared mount check][mount-check] · [Player registration][player-type] · [Serialization flag][no-save] · [Conditional riding behavior][riding] The rejected player-carry route is tracked in [issue #805](https://github.com/HungLo2020/MattMC/issues/805).

Owner-defense and retaliation goals are registered, and retaliation can alert other nearby monkeys. Avoid provoking a group. [Controls][interactions] · [Goals][goals]

## Combat, equipment, and drops

The implementation includes melee and ranged attack behavior. Its ordinary thrown projectile uses a cobblestone appearance and defines **4 damage points** (2 hearts) on an eligible hit; creating that projectile does not consume cobblestone from an inventory. This is source-defined behavior, not a tested combat strategy. [Projectile creation][attacks] · [Projectile damage][projectile]

**Ancient dart equipment is incomplete.** Giving a monkey an ancient dart and returning that item on death are commented-out TODOs. Banana-peel production is also commented out. Do not expect darts or banana peels from the current interactions. [Dart and food TODOs][interactions] · [Equipment drop TODO][equipment]

No dedicated capuchin monkey death-loot table was found in the bundled entity loot data. [Entity loot data][loot]

## Verification scope

Source-reviewed on **2026-10-01** against MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2`. Taming, breeding, riding, combat, and spawning were not tested in-game. Added data packs can change the missing-tag and loot limitations described above. The head-riding limitation was separately source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; no in-game riding test was run.

## Related pages

- [Mobs](Mobs.md)
- [Raccoon](Raccoon.md)
- [Gorilla](Gorilla.md)
- [Capuchin Monkey Spawn Egg](../items/CapuchinMonkeySpawnEgg.md)

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L79-L85
[attribute-wiring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L125
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1164-L1166
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1805
[tags]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L107-L112
[item-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[biome-code]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/biome
[placements]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L87-L102
[variants]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L514-L535
[interactions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L405-L511
[offspring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L373-L379
[riding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L264-L294
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L113-L136
[attacks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L194-L218
[projectile]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTossedItem.java#L98-L128
[equipment]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L256-L262
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities

[pickup-attempt]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCapuchinMonkey.java#L428-L459
[mount-check]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2291-L2325
[player-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1596-L1606
[no-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2142-L2145
