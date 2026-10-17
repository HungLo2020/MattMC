# Desert Pyramid

A **Desert Pyramid** is a sandstone expedition with four underground treasure chests and a separate archaeology cellar. Bring a usable [Brush](../items/Brush.md), a pickaxe, lighting and blocks for a controlled descent. **Do not drop through the blue center tile:** the chamber below has a Stone Pressure Plate above nine TNT blocks. [Floor marker][desert-floor] · [Trap and chests][desert-trap]

## Where to search

Search the **[Desert](../biomes/DesertsBadlandsAndSavannas.md#desert)** biome in the normal Overworld. Badlands and Savanna are absent from this structure's allowed tag. The normal biome source includes Desert, and the pyramid has its own random-spread set with **32-chunk spacing and 8-chunk separation**. These settings govern candidate positions; they do not promise a pyramid every 32 chunks or a particular walking distance. [Definition][desert-definition] · [Eligible biome][desert-biomes] · [Placement set][desert-set] · [Spread calculation][positions] · [Normal world][normal] · [Biome parameters][parameters] · [Preset mapping][parameter-map] · [Desert selection][biome-map]

These rules describe the bundled **normal Overworld**. New starts need structure generation enabled, an eligible biome and a successful placement candidate. Data packs, custom presets and already-generated terrain can differ. The loaded definition and structure set are used by the active generation path. [Generation option][structure-option] · [Registry data][world-load] · [Resource and tag loading][resource-load] · [Set filtering][set-filter] · [Start creation][generate] · [Biome check][biome-filter]

The structure builds a rotated **21×21-block main piece**, checks sampled corner heights against sea level, then lowers the building to its lowest checked ground height with a further 0–2-block offset. Expect terrain to conceal some of the building; the visible roof is not the full extent of its underground rooms. [Piece and offset][desert-piece] · [Single-piece terrain check][single] · [Corner samples][corners] · [Ground adjustment][lowest]

With permission level 2, use `/locate structure minecraft:desert_pyramid` in the Overworld. Follow the [dimension and arrival-height cautions](Structures.md#finding-a-structure); the result does not certify a safe descent or untouched loot. [Permission and registry][locate] · [Current-dimension search][locate-search]

## Disarm the treasure chamber

The upper orange-and-blue pattern marks the central shaft. Its **blue tile is directly over the central pressure plate**, while four chests occupy side recesses at the bottom. A 3×3 TNT layer sits beneath that chamber's central floor. [Pattern][desert-floor] · [Shaft, trigger and chest positions][desert-trap]

1. Light the entrance and establish a return route
2. Open a side route and make steps or ledges down, keeping clear of the central landing
3. Remove the Stone Pressure Plate from a safe position before walking across the chamber
4. Check for remaining threats, collect the four chests, then recover unprimed TNT if wanted

The Stone Pressure Plate detects living entities, so a mob can also trigger it. The active TNT handler responds to redstone power; priming is subject to the server's TNT-explosion rule. Do not treat the lack of a player standing on the plate as proof that the trap is harmless. See [pressure plates](../blocks/PressurePlates.md) and [TNT](../blocks/TNT.md) for component behavior. [Stone sensitivity][stone-plate] · [Living-entity detection][pressure] · [TNT priming][tnt]

This piece has no fixed mob residents or placed mob spawner, and its definition has no special spawn override. Ordinary biome spawning and creatures entering the building remain relevant; disabling its TNT trap does not clear the area. [Structure definition][desert-definition] · [Active piece construction][desert-wiring]

## Chest rewards

Each of the **four chest assignments** uses the same Desert Pyramid table. The table is attached during placement and unpacked when the container is first accessed; returning to an emptied chest does not refill it. [Four assignments][desert-trap] · [Chest placement][chest-create] · [Registered table key][loot-keys] · [Container access][container-open] · [One-time loot fill][container-loot]

| Pool | What it can provide |
| --- | --- |
| 2–4 weighted general rolls | Diamonds, Iron/Gold Ingots, Emeralds, Bones, Spider Eyes, Rotten Flesh, Leather, Copper/Iron/Golden/Diamond Horse Armor, an enchanted Book, Golden Apple or Enchanted Golden Apple; an empty outcome is also present |
| Four supply rolls | Bones, Gunpowder, Rotten Flesh, String or Sand, 1–8 per selected roll |
| One separate template roll | **Two Dune Armor Trim Smithing Templates**, or nothing |

[General pool][desert-general] · [Supply pool][desert-supplies] · [Template pool][desert-trim]

The template roll has item weight 1 against empty weight 6: **1 in 7 per chest** using the unchanged table, yielding two templates when selected. This is a per-chest selection chance, not a guaranteed pair per pyramid. General rolls can repeat entries. The chest table does not contain Pottery Sherds; search the archaeology cellar for those. [Template weights][desert-trim] · [Chest rewards][desert-general] · [Archaeology rewards][desert-archaeology]

## Archaeology cellar

Look for the **sand-covered staircase near a corner of the upper floor**. It leads to a shallow room with its own orange-and-blue floor pattern and a collapsed sandy roof, separate from the deep TNT chamber. Excavate carefully from above and the side. The cellar's blue floor tile is not a second copy of the central shaft trap. [Staircase][cellar] · [Cellar room and patterned floor][cellar-sand] · [Collapsed roof][cellar-roof]

The generation pass selects **5–7 suspicious blocks from the cellar's sand candidates**, plus **one separate collapsed-roof position**, for an intended **6–8 Suspicious Sand blocks in a complete, undisturbed placement**. It assigns the Desert Pyramid archaeology table to those positions. This is source-derived placement accounting, not a promise that every old, damaged or modified pyramid still contains that many recoverable blocks. [Cellar candidates][cellar-sand] · [Roof selection][cellar-roof] · [Placement and loot assignment][archaeology] · [Exclusive upper bound][random-bound]

Each completed suspicious block selects one of eight equally weighted items:

- Archer, Miner, Prize or Skull Pottery Sherd
- Diamond, TNT, Gunpowder or Emerald

Each named result is **1 in 8 per block** with the bundled table. The four sherd entries together occupy half the outcomes; collecting all four designs can require more than one expedition. These are brushing rewards, separate from chest loot and the TNT trap. [Archaeology table][desert-archaeology]

Keep each suspicious block supported and brush it **in place**. Work down through exposed layers; removing a lower support can destroy a block above before you brush it. Completion releases its stored item and replaces it with ordinary Sand. Follow [Brush archaeology basics](../items/Brush.md#archaeology-basics) and [suspicious-block handling](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel), including the restriction on starting with a broken Brush. [Brushing and completion][brush] · [Unsupported falling][fragile]

## Related pages

- [Deserts, Badlands and Savannas](../biomes/DesertsBadlandsAndSavannas.md)
- [Decorated Pot and sherd patterns](../blocks/DecoratedPot.md)
- [Jungle Temple](JungleTemple.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. The loaded world-generation definitions, biome eligibility, active procedural piece construction and placement, relevant block/entity interactions and loot assignments were inspected. No in-game search, generation, trap, looting, brushing, encounter or production-rate test was performed. Preparation advice is derived from those source rules.

The active structure creates this procedural piece directly; it does not load a saved building template for this route. The placement caller runs the piece before the archaeology post-pass. [Registered type][types] · [Piece factory][desert-wiring] · [Active placement][active-place] · [Piece and post-pass dispatch][piece-dispatch] · [Archaeology table key][archaeology-key]

[desert-floor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L205-L217
[desert-trap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L273-L309
[desert-definition]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure/desert_pyramid.json#L1-L6
[desert-biomes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/desert_pyramid.json#L1-L5
[desert-set]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure_set/desert_pyramids.json#L1-L14
[positions]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L67-L84
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L13
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[parameter-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L76-L88
[structure-option]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59
[world-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[resource-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[set-filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[generate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L494
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L548-L576
[desert-piece]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L23-L61
[single]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/SinglePieceStructure.java#L21-L33
[corners]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L171-L180
[lowest]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/ScatteredFeaturePiece.java#L69-L91
[locate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L68
[locate-search]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/LocateCommand.java#L95-L107
[stone-plate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L83-L99
[pressure]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java#L43-L49
[tnt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/TntBlock.java#L56-L95
[desert-wiring]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidStructure.java#L18-L29
[chest-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java#L447-L469
[loot-keys]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L40-L42
[container-open]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L49-L89
[container-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[desert-general]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/desert_pyramid.json#L4-L186
[desert-supplies]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/desert_pyramid.json#L187-L272
[desert-trim]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/desert_pyramid.json#L273-L293
[desert-archaeology]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/archaeology/desert_pyramid.json#L1-L44
[cellar]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L315-L343
[cellar-sand]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L345-L408
[cellar-roof]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L411-L440
[archaeology]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidStructure.java#L32-L71
[random-bound]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/util/RandomSource.java#L69-L74
[brush]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L128
[fragile]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L82-L102
[types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L23-L43
[active-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
[archaeology-key]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L130
