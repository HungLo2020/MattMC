# Woodland Mansion

A Woodland Mansion (`minecraft:mansion`) is an expedition for [Evokers](../mobs/Evoker.md), [Vindicators](../mobs/Vindicator.md), possible captive [Allays](../mobs/Allay.md), and room-dependent treasure. Bring a plan for both axe attacks and [Vexes](../mobs/Vex.md) that can pass through walls. A mansion visit does not itself start a [raid](../mechanics/Raid.md).

## Finding a mansion

The bundled **normal Overworld** allows mansion starts in **Dark Forest and Pale Garden**. Both occur in the normal Overworld biome source. Eligibility is a search region, not a promise that every forest contains a mansion. [Allowed biomes][biomes] · [Normal preset][normal] · [Parameter list][parameters] · [Biome mapping][biome-map] · [Forest choices][forest-choices]

New starts require structure generation enabled. The loaded mansion set uses **80-chunk spacing, 20-chunk separation and triangular placement**; generation also rejects its sampled starting height below Y=60. These are candidate-placement rules, not a fixed distance between visible buildings. Data packs, custom presets and terrain generated earlier can differ. [Generation option][option] · [Loaded registries][load] · [Set/biome filtering][filter] · [Placement set][set] · [Generation caller][generate] · [Height test][mansion-start]

A **level-5 (Master) Cartographer** has a Woodland Explorer Map in the checked normal trade list, with a base price of **14 Emeralds and one Compass**. The offer is created only when its structure search finds a destination; normal [trade pricing](../trading/Trading.md#prices-can-change) can change the Emerald cost. The map tag points to `minecraft:mansion`. [Profession][cartographer] · [Master trade][map-trade] · [Trade selection][trade-call] · [Search and ingredients][map-offer] · [Map destination][map-tag]

With permission level 2, use `/locate structure minecraft:mansion` in the relevant dimension. Follow the [locate arrival-height cautions](Structures.md#finding-a-structure) before teleporting to a result.

## Rooms and residents

Mansions assemble randomized room templates around corridors. First- and upper-floor room sets include secret-room choices; the third-floor routine can leave that floor absent if it cannot construct the connection. Do not plan around finding a particular room, chest count or resident count. [Room construction][room-first] · [Upper rooms][room-upper] · [Floor placement][room-place] · [Third-floor conditions][third-floor]

The checked templates and their active marker handler establish these encounters:

- **Vindicators** are placed at Warrior markers; **Evokers** at Mage markers. For example, one upper room has two Vindicators and one Evoker
- A possible first-floor cage room has **four Allay-group markers**, each choosing **1–3 Allays** when processed. That is 4–12 attempted residents for that selected room, not a mansion-wide guarantee
- A possible secret room contains a configured **Spider spawner**. See [Monster Spawner](../blocks/MonsterSpawner.md) before breaking or approaching one

[Illager room][evoker-room] · [Allay room][allay-room] · [Spider room][spider-room] · [Resident creation][markers]

These are loaded template contents: structure placement calls the template piece, which reads the data markers and creates persistent residents. The mansion's structure definition has **no natural-spawn override**, and its two eligible biome tables do not list Evokers or Vindicators. Clearing generated illagers therefore does not start an outpost-style replacement cycle. Other eligible hostile spawning and the optional spawner remain separate hazards. [Template loading][template-load] · [Piece loading][template-piece] · [Structure placement][structure-place] · [Marker dispatch][marker-call] · [Structure definition][mansion] · [Spawn lookup][spawn-selection] · [Dark Forest spawns][dark-spawns] · [Pale Garden spawns][pale-spawns]

## Chest rewards

A chest created by a mansion Chest marker receives the `minecraft:chests/woodland_mansion` table, which the server resolves when its inventory is unpacked. Its four independent pools are: [Example chest marker][chest-room] · [Marker handling][markers] · [Chest assignment][chest-create] · [Opening][chest-open] · [Loaded-table fill][loot-fill] · [Complete table][table]

| Pool | Rolls and possible contents |
| --- | --- |
| Valuables | 1–3 weighted rolls: Lead, Golden Apple, Enchanted Golden Apple, 13 or Cat music disc, Name Tag, Chainmail Chestplate, Diamond Hoe, Diamond Chestplate or an enchanted book |
| Supplies | 1–4 weighted rolls: 1–4 Iron/Gold Ingots, Bread, 1–4 Wheat, Bucket, 1–4 Redstone or Coal, 2–4 Melon/Pumpkin/Beetroot Seeds, or 2–4 Resin Clumps |
| Mob materials | Three rolls, each giving 1–8 Bone, Gunpowder, Rotten Flesh or String |
| Vex trim | One equally weighted choice between nothing and one Vex Armor Trim Smithing Template |

The final pool means a **50% chance of one [Vex template](../items/SmithingTemplateVexArmorTrim.md) per chest using this table**, not a guarantee after two chests. The other pools use unequal weights where specified; the table above is an inventory of possibilities, not equal odds for each item. [Amounts and weights][table] · [Default entry weight][weight-default]

**Not every mansion chest uses that table.** Bundled rooms also contain fixed inventories: eight Alliums, 28 Dark Oak Saplings, or an Efficiency I Iron Axe. A storage-room template contains empty chests. A separate secret room has a [Trapped Chest](../blocks/TrappedChest.md#crafting-and-obtaining) with two Ender Pearls and nearby explosives; inspect it before opening. [Flower chest][allium-room] · [Sapling chest][sapling-room] · [Axe chest][axe-room] · [Empty storage][empty-room] · [Trapped chest][trap-room]

A [Totem of Undying](../items/TotemOfUndying.md) is an **Evoker drop**, not an entry in the checked mansion chest table. Carrying a Totem in an ordinary inventory slot does not activate it; follow its hand-holding requirement before the next fight. [Evoker table][evoker-loot]

## Preparing an approach

- Mark the entrance, stairs and cleared rooms so a retreat does not lead into unexplored enemies
- Identify Evokers early: their summons can cross walls, so sealing a doorway is not complete protection
- Keep space from Vindicators and check rooms before rescuing Allays or opening chests
- Treat secret rooms as optional hazards as well as possible rewards; clear nearby threats before inspecting unusual storage or a spawner

These precautions follow the reviewed room data and [Evoker](../mobs/Evoker.md#behavior), [Vindicator](../mobs/Vindicator.md#behavior) and [Vex](../mobs/Vex.md#behavior) behavior. They are not an in-game-tested assault route.

## Related pages

- [Pillager Outpost](PillagerOutpost.md) · [Raids](../mechanics/Raid.md)
- [Combat](../mechanics/Combat.md) · [Defensive items](../mechanics/DefensiveItems.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Followed loaded normal-preset biomes and structure sets, mansion assembly, all 73 bundled mansion NBT templates, marker dispatch, resident creation and loot. No world-generation, map-trade, locate, exploration, rescue, chest-opening or combat test was performed. Room and table data do not guarantee the state of a particular explored mansion.

[biomes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/woodland_mansion.json#L1-L6
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L84
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[biome-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[forest-choices]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L74-L95
[option]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L58
[load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L45-L64
[set]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure_set/woodland_mansions.json#L1-L15
[generate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L491
[mansion-start]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionStructure.java#L28-L43
[cartographer]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L340-L345
[map-trade]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L452-L458
[trade-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L836
[map-offer]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1559-L1576
[map-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/structure/on_woodland_explorer_maps.json#L1-L5
[room-first]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L34-L81
[room-upper]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1093-L1127
[room-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L417-L461
[third-floor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L242-L298
[evoker-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_d3.nbt
[allay-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/2x2_a1.nbt
[spider-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x1_as2.nbt
[markers]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1200-L1260
[template-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L123-L129
[template-piece]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L36-L51
[structure-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L101
[marker-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L81-L101
[mansion]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure/mansion.json#L1-L6
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[dark-spawns]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/dark_forest.json#L1-L203
[pale-spawns]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/pale_garden.json#L1-L167
[chest-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_a1.nbt
[chest-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java#L447-L469
[chest-open]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L80-L93
[loot-fill]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[table]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/woodland_mansion.json#L1-L319
[weight-default]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L46-L53
[allium-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x1_b5.nbt
[sapling-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_a4.nbt
[axe-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_a6.nbt
[empty-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_a9.nbt
[trap-room]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_s2.nbt
[evoker-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/evoker.json#L1-L51
