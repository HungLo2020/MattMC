# Monster Room

A **Monster Room**, often called a dungeon, is a small cave chamber with Cobblestone and Mossy Cobblestone, an ordinary [Monster Spawner](../blocks/MonsterSpawner.md), and possible [Chests](../blocks/Chest.md). Visit for chest supplies and collectible rewards, or preserve the cage as a place to return for mob encounters. **A room need not have two chests, and breaking its spawner does not let you take it home.** [Room generation][room]

## Finding and recognizing a room

Explore caves for an abrupt patch of Cobblestone or Mossy Cobblestone around a roughly rectangular chamber. Look for the cage before entering; nearby monster sounds can justify a careful look, but do not identify a room by themselves. Prepare food, armor, a weapon, lighting, a pickaxe and blocks for closing exposed approaches. Mark the cave junctions and the room's coordinates before taking a detour.

In the bundled normal world, the two ordinary room placements are listed in **all 54 Overworld biome definitions**, including Mushroom Fields and Deep Dark, and in **[Primordial Plains](../biomes/PrimordialPlains.md)**. The biome's ordinary monster list is therefore not a reliable test for whether a room can generate. **[Dry Midlands](../biomes/DryMidlands.md)** and **[Primordial Ocean](../biomes/SpecialBiomes.md#primordial-ocean)** use three different placements of the same room feature. [Overworld selection][overworld-biomes] · [Mushroom Fields][mushroom] · [Deep Dark][deep-dark] · [Primordial Plains][plains] · [Dry Midlands][midlands] · [Primordial Ocean][ocean]

Primordial Ocean is a candidate in the **loaded Primordial Caves dimension definition**. Its absence from the literal Normal preset's two-biome entry does not remove this route: the loaded dimension takes precedence. Use [Primordial Caves](../dimensions/PrimordialCaves.md#what-currently-generates) for access, the effective biome source and the separate return-portal rules. [Loaded dimension][dimension] · [Normal preset][normal] · [Loading][loader] · [Dimension precedence][bake]

### Where generation tries

These are **candidate positions per chunk decoration pass when the placement is selected**, not rooms per chunk. Each surviving candidate receives a random X/Z position in the chunk and a uniformly selected height, then must pass the actual-position biome filter and room checks. The ranges below are inclusive and use the bundled normal generators. [Decoration][decoration] · [Placement chain][placed] · [Biome check][biome-filter] · [Horizontal spread][square]

| Placement ID | Biomes using it | Candidate heights | Candidate count and extra filter |
| --- | --- | --- | --- |
| `minecraft:monster_room` | Ordinary Overworld biomes; Primordial Plains | Y=0–319 | 10 candidates |
| `minecraft:monster_room_deep` | Same as above | Y=-58–-1 | 4 candidates |
| `minecraft:dungeon/high` | Dry Midlands; Primordial Ocean | Y=121–319 | 4 candidates; each must pass a 1-in-2 rarity filter |
| `minecraft:dungeon/mid` | Dry Midlands; Primordial Ocean | Y=0–120 | 13 candidates |
| `minecraft:dungeon/low` | Dry Midlands; Primordial Ocean | Y=-64–-1 | 9 candidates; each must pass a 1-in-3 rarity filter |

[Ordinary placement][ordinary] · [Deep placement][deep] · [High placement][high] · [Middle placement][mid] · [Low placement][low] · [Rarity filter][rarity]

The upper bound is **319 in both dimensions**: placement height uses the generator's 384-block range beginning at -64. Primordial Caves' taller dimension setting does not raise these attempts to 335. Height ranges describe sampled room origins, not guaranteed usable caves or a recommended mining level. [Height context][height-context] · [Height anchors][anchors] · [Overworld terrain settings][overworld-noise] · [Primordial terrain settings][primordial-noise] · [Primordial dimension type][dimension-type]

The room generator rejects a site unless its proposed footprint has a solid floor and ceiling and **one to five edge positions with two air blocks vertically**. It then hollows and lines the room, subject to support and protected-block checks. Large open caverns, sealed solid rock and unsuitable floor/ceiling sites can all reject candidates. These requirements make cave exploration a practical search method; they do not establish a particular seed, travel distance or successful-room rate. [Terrain and replacement checks][room]

Monster rooms are **biome features**, not entries in the structure registry. There is no bundled `/locate structure minecraft:monster_room` target. The Generate Structures setting gates registered structure work separately from these biome-decoration attempts. [Feature registration][registration] · [Configured feature][configured] · [Decoration paths][decoration] · [Locate registry][locate]

## Secure the chamber before looting

The room's cage is selected equally from **Skeleton, Zombie, Zombie, Spider**. This gives a **50% Zombie, 25% Skeleton and 25% Spider** choice when the generator configures the cage. It does not promise the number or variants of mobs that will appear. [Cage selection][room] · [Equal array selection][random]

| Cage mob | Approach |
| --- | --- |
| [Zombie](../mobs/Zombie.md) | Keep a retreat route and watch for small, fast babies; a prolonged Hard-difficulty fight can attract reinforcements |
| [Skeleton](../mobs/Skeleton.md) | Use solid cover to break its line of sight before crossing the room |
| [Spider](../mobs/Spider.md) | Check walls and the ceiling; use roofed cover because a tall open wall does not reliably contain a climber |

Work from a controlled opening, close side approaches, and clear existing mobs before standing at a chest. Check the surrounding cave for drops, water, lava and unrelated monsters. The cage's selected mob does not describe everything that can enter through an opening.

**Light the possible spawn spaces, not only the cage.** The three ordinary room mobs use darkness checks, and the bundled Overworld and Primordial Caves settings require block light 0 for those default attempts. Lighting can block new attempts where it reaches; it does not remove mobs already present or prove that every nearby space is covered. For activation distance, spawn area, delays, nearby-mob limits and custom rules, use the canonical [Monster Spawner guide](../blocks/MonsterSpawner.md#space-light-and-unsuccessful-attempts). [Spawner checks][spawner] · [Mob bindings][spawn-bindings] · [Darkness rules][darkness] · [Spawner light exception][spawn-reason] · [Overworld light settings][overworld-type] · [Primordial light settings][dimension-type]

## Optional chests and their rewards

Each room makes **up to two chest placements**, with at most three candidate positions for each. A candidate must be empty and have exactly one solid horizontal neighbor. Failed candidates can leave fewer chests; overlapping generation or later changes can also affect what remains. A placed chest is assigned `minecraft:chests/simple_dungeon`. Check the chamber edges, but do not tear it apart expecting a second chest to be hidden somewhere. [Chest placement and table assignment][room]

Each fresh assigned chest rolls all three pools below. **Weights compare entries within one pool; they are not per-chest percentages.** Counts are quantities for one selected entry, and repeated selections are possible. Inventory slots can split the resulting stacks. No named reward is guaranteed. [Exact chest table][loot] · [Pool selection][pool] · [Container filling][fill]

| Pool and rolls | Possible reward | Count per selection | Weight |
| --- | --- | ---: | ---: |
| Valuables: 1–3 rolls | Leather | 1–5 | 20 |
| Same pool | Golden Apple | 1 | 15 |
| Same pool | Enchanted Golden Apple | 1 | 2 |
| Same pool | [Otherside Music Disc](../items/MusicDiscOtherside.md) | 1 | 2 |
| Same pool | [13 Music Disc](../items/MusicDisc13.md); [Cat Music Disc](../items/MusicDiscCat.md) | 1 | 15 each |
| Same pool | Name Tag | 1 | 20 |
| Same pool | Golden Horse Armor | 1 | 10 |
| Same pool | Copper Horse Armor; Iron Horse Armor | 1 | 15 each |
| Same pool | Diamond Horse Armor | 1 | 5 |
| Same pool | Randomly enchanted book | 1 | 10 |
| Supplies: 1–4 rolls | Iron Ingot | 1–4 | 10 |
| Same pool | Gold Ingot | 1–4 | 5 |
| Same pool | Bread | 1 | 20 |
| Same pool | Wheat | 1–4 | 20 |
| Same pool | Bucket | 1 | 10 |
| Same pool | Redstone Dust; Coal | 1–4 | 15 each |
| Same pool | Melon Seeds; Pumpkin Seeds; Beetroot Seeds | 2–4 | 10 each |
| Mob materials: 3 rolls | Bone; Gunpowder; Rotten Flesh; String | 1–8 | 10 each |

The book selects one enchantment from the bundled `#minecraft:on_random_loot` set, then a level within that enchantment's allowed range. The set includes the non-treasure group, Curse of Binding, Curse of Vanishing, Frost Walker and Mending; it is not a promise of Mending or any other specific enchantment. See [Enchanting](../enchanting/Enchanting.md) for using the result. [Book function][enchant] · [Selection tag][enchant-tag]

## Preserve, recover and return

Decide whether you want to keep the cage before mining. **An ordinary spawner drops no spawner item, even with Silk Touch.** Preserving it keeps the possibility of future mob encounters; destroying it is permanent for that room unless someone replaces it by another means. The [Monster Spawner guide](../blocks/MonsterSpawner.md#finding-and-preserving-one) owns its mining reward and reuse limits. [Spawner block loot][spawner-loot]

After securing the area, collect wanted Cobblestone and Mossy Cobblestone with a suitable pickaxe. Their ordinary block tables return the corresponding block, with normal tool/drop rules still applying. Avoid opening the floor or walls into an unchecked cave while stripping materials, and keep the cage's surroundings controlled if preserving it. Empty the chests before recovering them; a chest item is not portable filled storage. [Cobblestone loot][cobble-loot] · [Mossy Cobblestone loot][mossy-loot] · [Pickaxe tag][pickaxe] · [Required tools][blocks] · [Chest handling](../blocks/Chest.md#crafting-and-collecting)

Record the dimension, coordinates, cage mob and a marked return route. **Chest loot is a one-time supply:** generating its contents clears the stored loot-table reference, so leaving and returning does not refill it. A preserved spawner has separate player, space, light and population requirements; a room is not a guaranteed farm yield or an automatic supply of all three cage mobs. Use the mob guides for their drops, and keep the Primordial portal route separate from the cave route when exploring there. [Loot consumption][container] · [Spawner requirements](../blocks/MonsterSpawner.md) · [Primordial return route](../dimensions/PrimordialCaves.md#destination-and-return-route)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked bundled biome consumers, the loaded-dimension override, feature registration and placement chain, height resolution, room/chest/cage generation, exact chest pools, lighting predicates, block recovery and one-time loot handling. This was source and data inspection; no in-game search, room-generation, combat, lighting, loot-distribution or farm test was run. Data packs, custom worlds, later builds and already-generated or modified terrain can differ.

Related: [Monster Spawner](../blocks/MonsterSpawner.md) · [Chest](../blocks/Chest.md) · [Primordial Caves](../dimensions/PrimordialCaves.md) · [Structures](Structures.md)

[room]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L23-L131
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[mushroom]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json#L25-L34
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json#L33-L42
[plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json#L25-L34
[midlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json#L43-L53
[ocean]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json#L31-L41
[dimension]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L48
[bake]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183
[decoration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L395
[placed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L40-L63
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java#L22-L28
[square]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/InSquarePlacement.java#L20-L25
[ordinary]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/monster_room.json
[deep]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/monster_room_deep.json
[high]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/dungeon/high.json
[mid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/dungeon/mid.json
[low]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/dungeon/low.json
[rarity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/RarityFilter.java#L22-L25
[height-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldGenerationContext.java#L10-L20
[anchors]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/VerticalAnchor.java#L53-L96
[overworld-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L14-L19
[primordial-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/noise_settings/primordial_caves.json#L14-L19
[dimension-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L103
[configured]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/monster_room.json
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L53-L69
[random]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/Util.java#L697-L699
[spawner]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/BaseSpawner.java#L84-L185
[spawn-bindings]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L143-L158
[darkness]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L112
[spawn-reason]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java#L28-L30
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/overworld.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json
[pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L63-L101
[fill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootTable.java#L148-L196
[enchant]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantRandomlyFunction.java#L54-L86
[enchant-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/on_random_loot.json
[spawner-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/spawner.json
[cobble-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cobblestone.json
[mossy-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/mossy_cobblestone.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L11-L31
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[container]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L90
