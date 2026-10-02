# End City

**End Cities** (`minecraft:end_city`) are towers and bridges on the outer islands of the [End](../dimensions/End.md). Search them for enchanted equipment and [Shulker Shells](../items/ShulkerShell.md). A city that generates an **End Ship** can also provide [Elytra](../items/Elytra.md), but finding a city does not guarantee a ship. [Generation][pieces] · [Treasure][loot]

## Where to search

In the normal world preset, End Cities are eligible in **End Highlands** and **End Midlands**. The central island's The End biome, End Barrens, and Small End Islands are not in the bundled eligibility tag. The active End biome source selects the central biome near the origin and the outer biomes farther away. Explore the larger outer islands rather than repeatedly circling the dragon arena. [World preset][preset] · [Biome source][biomes] · [Eligible biomes][tag]

The bundled placement set uses triangular random spread with **20-chunk spacing** and **11-chunk separation**. Those are placement settings, not a promise of one city every 320 blocks. A candidate must pass the biome check and the structure's terrain-height check, which rejects its sampled base below Y=60. [Placement][placement] · [Structure definition][definition] · [Generation checks][structure] · [Biome and height checks][base-structure]

The [Ender Dragon](../mobs/EnderDragon.md#victory-and-rewards) fight opens gateways that help reach the outer End. Take food, spare blocks, tools, weapons, and a planned return route; the first city may have no Elytra. Keep enough supplies to return without flight.

## Towers, bridges, and ships

Cities assemble from several room, tower, roof, and bridge templates. Their size and chest count vary. Some rooms contain [Shulkers](../mobs/Shulker.md), and an exposed bridge can lead toward a separate floating ship across a gap. Do not assume the bridge continues all the way to its reward. [City assembly][pieces]

Ships are chosen during bridge generation. Before a ship has been selected, the branch makes a **1-in-(10 − generation depth)** roll. Branch depth, the number of attempted bridges, and collision rejection all affect the final layout. This is **not a fixed chance per city**; the ordinary generator selects at most one ship per city assembly. [Ship selection and branch rejection][pieces]

The checked templates contain these rewards before placement or later player changes:

| Location | Template contents |
| --- | --- |
| Selected three-floor house top | One treasure chest |
| Wide tower top | Two treasure chests |
| End Ship | Two treasure chests, one Elytra frame marker, and three Shulker markers |

The ship also contains a Dragon Head and a Brewing Stand holding **two Potions of Healing II**. These are placed template contents, separate from the randomized chest loot. The table describes the relevant pieces, not guaranteed totals for every city. [House template][house-template] · [Tower template][tower-template] · [Ship template][ship-template] · [Marker handling][pieces]

## Chest loot and the Elytra frame

City and ship treasure chests use the same loot table. Its main pool makes **2–6 weighted selections**, with possibilities including Diamonds, Iron/Gold Ingots, Emeralds, Beetroot Seeds, a Saddle, horse armor, and enchanted Iron/Diamond equipment. The equipment entries apply the loot-enchantment routine with a level input of 20–39; that does not mean a level-39 enchantment appears on the item. Rolls are selections, not guaranteed distinct stacks or specific valuables. [Treasure table][loot]

A separate pool gives each chest a **1-in-15 chance of one Spire Armor Trim Smithing Template**. See [Smithing](../smithing/Smithing.md) for using trim templates. This chance applies to each unchanged chest table, not to the city as a whole. [Trim loot][loot]

**Elytra is displayed in an item frame inside the ship, not rolled from these chests.** The ship's Elytra marker creates a frame containing one ordinary Elytra. In Survival, strike the occupied frame to release the item and pick it up; normal entity drops must be enabled. Secure the room and the floor before collecting it. A missing or previously looted frame is not replenished by opening the ship's chests. [Reward creation][pieces] · [Frame drops][frame]

## Surviving the climb

- Take cover from Shulker bullets and remove attackers before lingering on narrow ledges
- A successful bullet hit can apply **10 seconds of Levitation** at 20 TPS. Plan where you will land when it ends
- **Levitation prevents Elytra gliding**, including starting a glide. Newly collected wings are not an escape button while that effect is active
- Keep a route down through the structure and a route back between islands; losing your footing over the void is different from an ordinary fall onto a roof

The [Shulker guide](../mobs/Shulker.md) covers projectiles, teleporting, defenses, and shell drops. Read the [Elytra guide](../items/Elytra.md) before relying on the wings for a return trip. [Bullet effects][bullet] · [Glide conditions][flight]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Reviewed the active structure codec, placement and biome filtering, template loader/marker dispatch, compressed NBT templates, and loot data. No in-game city search, generation, combat, chest opening, or frame-collection test was run. Structure generation settings, data packs, template overrides, and already explored worlds can change what is present.

Related: [End](../dimensions/End.md) · [Shulker](../mobs/Shulker.md) · [Elytra](../items/Elytra.md) · [Structures](Structures.md)

[preset]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/biome/TheEndBiomeSource.java
[tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/end_city.json
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure_set/end_cities.json
[definition]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/end_city.json
[structure]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityStructure.java
[base-structure]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L131-L205
[pieces]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java
[house-template]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/end_city/third_floor_2.nbt
[tower-template]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/end_city/fat_tower_top.nbt
[ship-template]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/end_city/ship.nbt
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/chests/end_city_treasure.json
[frame]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L157-L243
[bullet]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/ShulkerBullet.java#L279-L294
[flight]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2883-L2920

Additional wiring: [registered structure codec](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java), [eligible structure sets](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java), [generation caller](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L539), [template loading](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java), and [marker dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L81-L118)
