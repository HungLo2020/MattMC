# Trail Ruins

**Trail Ruins** are buried Overworld excavation sites. Bring a serviceable [Brush](../items/Brush.md) to recover their archaeology finds: seven Pottery Sherd designs, four armor-trim templates and the Relic Music Disc are possible rewards from particular [Suspicious Gravel](../items/SuspiciousGravel.md) blocks. Finding the structure does not guarantee the collectible you want. [Rare rewards][rare-loot] · [Loot assignment][houses-processor]

## Where to search

In the bundled **normal Overworld**, search these six biomes:

- [Taiga, Snowy Taiga, Old Growth Pine Taiga and Old Growth Spruce Taiga](../biomes/TaigaAndSnowyBiomes.md)
- [Old Growth Birch Forest](../biomes/TemperateForests.md)
- [Jungle](../biomes/JunglesAndSwamps.md)

Ordinary Birch Forest, Sparse Jungle and Bamboo Jungle are not in the Trail Ruins eligibility tag. Eligible terrain is a search area, not a promise that each forest contains a ruin. The normal Overworld biome source includes the six listed biomes. [Structure and biome tag][definition] · [Exact biome list][biomes] · [Normal preset][normal] · [Biome parameters][parameters] · [Parameter mapping][parameter-map] · [Overworld selection][biome-map]

New starts require **structure generation enabled**, an eligible biome and a successful placement candidate. The bundled random-spread set uses **34-chunk spacing and 8-chunk separation**; this does not mean a ruin appears every 34 chunks or give a guaranteed walking distance. Explore new terrain when looking for structures added or changed since older chunks were generated. Custom presets, data packs and world-specific template overrides can change these results. The bundled [Primordial Caves](../dimensions/PrimordialCaves.md) dimension uses different biomes and is not a Trail Ruins search destination under these rules. [Generation option][generation-option] · [Set and biome filtering][set-filter] · [Placement set][placement-set] · [Candidate calculation][random-spread] · [Start creation][starts] · [Normal dimensions][normal] · [Template precedence][template-load]

With permission level 2, use `/locate structure minecraft:trail_ruins` **in the Overworld**. Its coordinates identify a search destination; they do not guarantee unexcavated loot. The suggested teleport preserves your current height, so follow the [arrival-height cautions](Structures.md#finding-a-structure) before using it. [Locate permission and search][locate] · [Coordinate output][locate-output]

## Recognize the site and follow it underground

Look for an unusual patch of **Gravel and colored Terracotta** among the natural terrain. All five bundled tower-top variants contain those materials. The matching lower towers include combinations of Terracotta, Glazed Terracotta, Cobblestone, stone masonry, Bricks or Mud Bricks. A small exposed remnant can lead to a much larger buried site. These are template-derived identification clues; surface visibility has not been measured in generated worlds. [Tower-top templates][top-templates] · [Starting tower pool][tower-pool] · [Tower templates][tower-templates]

The structure starts from a tower and joins compatible roads, tower additions, buildings and decoration pieces. Its definition projects the start against the world-surface heightmap with a **−15-block start offset** and buried terrain adaptation. That is a surface-relative generation setting, **not a fixed world Y coordinate or an instruction to dig exactly 15 blocks down**. Following the masonry is more useful than treating the exposed tip as the whole site. [Structure settings][definition] · [Height projection][projection] · [Connected pools][roads-pool] · [Buildings][buildings-pool] · [Grouped buildings][grouped-pool] · [Tower additions][additions-pool] · [Decoration][decor-pool]

Not every possible room or piece appears in one ruin. Connections must match, fit within the available space and pass assembly limits. Buried roads can lead sideways to additional rooms; mark explored branches before extending the excavation. The decoded bundled templates contain no chests or saved entities, and the structure has no special spawn override. Ordinary nearby mobs and dark excavation areas still need attention. [Assembly limits][assembly] · [Bundled templates][templates] · [Spawn settings][definition]

## Prepare and excavate without losing finds

Pack a Brush with usable durability, a spare for a longer dig, a shovel, a pickaxe, food, lighting and blocks for safe steps and support. Leave inventory space for both artifacts and bulk excavation material. Record the site coordinates and establish a route back before opening a deep shaft.

1. Clear immediate threats and light the work area
2. Expose small sections from above or the side, working downward through layers
3. Inspect gravel before mining it; brush any suspicious block while it is still supported
4. Keep the block underneath a suspicious block intact until brushing finishes
5. Collect the released item, then remove the ordinary Gravel left behind if you need to reach the next layer
6. Follow roads and walls into adjoining pieces, marking a clear exit as you go

**Breaking Suspicious Gravel is not the archaeology collection method, even with Silk Touch.** Its block-loot table has no item pools. Removing its support can make it fall, and its falling path cancels ordinary placement and recovery on landing. A torch-under-gravel excavation shortcut is therefore unsuitable for preserving these finds. [Empty block loot][gravel-loot] · [Unsupported falling][falling] · [Cancelled landing][falling-land] · [Cancellation flag][cancel-drop]

Hold the Brush's use control on the suspicious block until completion. Ordinary Gravel can produce brushing particles and sounds too; those effects alone do not mean it contains treasure. Successful completion releases the stored item and turns Suspicious Gravel into ordinary Gravel. A fully worn Brush cannot start this block-use route in MattMC. Follow [Brush archaeology basics](../items/Brush.md#archaeology-basics) for progress timing, wear and interruptions, and [suspicious-block handling](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel) for the shared placed-block rules. [Active brushing][brush-use] · [Completion][brush-complete] · [Gravel registration][gravel-block] · [Broken-item guard][broken-guard]

## Which gravel can hold rare finds?

Generation assigns each archaeology block a **common** or **rare** loot table. Both assignments produce the same undusted Suspicious Gravel block; its initial block state does not identify which table it received. The Brush reveals that block's assigned contents, rather than upgrading ordinary Gravel into a rare block. [Common and rare assignments][houses-processor] · [Loot attachment][append-loot] · [First brushing and saved result][brush-loot]

| Piece processing | Common-table substitutions | Rare-table substitutions |
| --- | --- | --- |
| Starting tower, tower additions, buildings, grouped buildings and decoration | Up to 6 | Up to 3 |
| Road pieces | Up to 2 | None from this processor |
| Tower tops | Up to 2 | None from this processor |

[House-family assignments][tower-pool] · [Additions][additions-pool] · [Buildings][buildings-pool] · [Grouped buildings][grouped-pool] · [Decoration][decor-pool] · [Road assignments][roads-pool] · [Top assignments][top-pool] · [House processor][houses-processor] · [Road processor][roads-processor] · [Top processor][top-processor]

These are **caps per template-processing pass**, not guaranteed blocks per room or per ruin. The replacement tag contains only Gravel. House and road processing first changes some Gravel into Dirt or Coarse Dirt; the house processor then selects common blocks before rare ones from the remaining eligible material. Selected common blocks are no longer Gravel and cannot also be selected as rare. Actual recoverable totals depend on the assembled pieces, eligible blocks, placement and the site's condition. Do not turn the 6:3 caps into a universal rare-block percentage. [Eligible material][replaceable] · [First matching rule][rule-processor] · [Replacement cap][cap] · [Processing order][process-order] · [Placement bounds][template-place]

## Rewards and return expectations

With the unchanged bundled tables, each completed assigned block supplies **one item** from its table. Common and rare tables are separate; common-table blocks do not roll for the rare table's artifacts. [Common table][common-loot] · [Rare table][rare-loot] · [Weighted selection][loot-pool] · [Default weights][loot-weights] · [Item creation][loot-item]

| Table | Possible finds | Chance for each named item, conditional on this table |
| --- | --- | --- |
| Common, weight 2 entries | Emerald, Wheat, Wooden Hoe, Clay block, Brick; Yellow, Blue, Light Blue, White or Orange Dye; Red, Green, Purple or Brown Candle | **2/45**, about 4.44% |
| Common, weight 1 entries | Magenta, Pink, Blue, Light Blue, Red, Yellow or Purple Stained Glass Pane; Spruce or Oak Hanging Sign; Gold Nugget, Coal, Wheat Seeds, Beetroot Seeds, Dead Bush, Flower Pot, String or Lead | **1/45**, about 2.22% |
| Rare, seven sherd entries | Burn, Danger, Friend, Heart, Heartbreak, Howl or Sheaf Pottery Sherd | **1/12**, about 8.33% |
| Rare, four template entries | Wayfinder, Raiser, Shaper or Host Armor Trim Smithing Template | **1/12**, about 8.33% |
| Rare, one disc entry | Relic Music Disc | **1/12**, about 8.33% |

[Common entries and weights][common-loot] · [Rare entries][rare-loot]

The common table has 31 entries totaling 45 weight; the rare table has 12 equally weighted entries. Across a rare-table roll, the four trim templates together account for **1/3** of outcomes, but any particular template remains **1/12**. These are table-selection probabilities, not the chance per arbitrary gravel block or a guarantee of a full set from one site. Repeated finds are possible.

Use the [Decorated Pot guide](../blocks/DecoratedPot.md) for sherd patterns, [Armor trims](../mechanics/ArmorTrims.md) for applying and copying templates, and [Jukebox](../blocks/Jukebox.md) for music discs. Keep a wanted template safe while planning copies; the detailed recipes belong in the trim guide.

A completed archaeology block becomes ordinary Gravel and cannot be brushed again for another reward. Return to a site for **unexcavated sections and untouched suspicious blocks**, or search another ruin for missing collectibles. Save the route and mark finished branches so a later expedition can resume efficiently. [One-time loot unpacking][brush-loot] · [Completion and conversion][brush-complete]

## Related pages

- [Brush](../items/Brush.md)
- [Suspicious Gravel](../items/SuspiciousGravel.md)
- [Desert Pyramid](DesertPyramid.md)
- [Ocean Ruins](OceanRuins.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The active world-generation registry/resource path, structure placement, seven Trail Ruins pools, all **84 referenced compressed NBT templates**, three archaeology processors, two archaeology loot tables and Brush/block completion paths were inspected. Preparation advice follows those source rules. No in-game search, generation, visibility, excavation, brushing, encounter or loot-distribution test was performed.

The world loader reads the structure, structure-set, processor-list and template-pool registries from resources. The registered Jigsaw structure passes its pool to the assembly code; placed pool elements load their named templates and configured processors. Template loading checks world-generated overrides before bundled resources. Archaeology loot uses the server's reloadable loot registry and is loaded into the block entity from the processed template's saved data. These checks establish the active source route, not successful generation in every world. [World loading][world-load] · [Registry definitions][registry-load] · [Resource and tag loading][registry-read] · [Registry resource paths][registry-paths] · [Path conversion][file-paths] · [Jigsaw type][structure-type] · [Jigsaw entry point][jigsaw] · [Chunk placement][chunk-place] · [Piece dispatch][piece-dispatch] · [Pool piece placement][pool-piece] · [Template and processor dispatch][pool-place] · [Template resource loading][template-load] · [Loot reload][loot-reload] · [Loot registry type][loot-type] · [Loot resource scan][loot-scan] · [Saved block-entity data][template-place] · [Loot assignment loading][brush-load]

[rare-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_rare.json
[common-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_common.json
[houses-processor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_houses_archaeology.json
[roads-processor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_roads_archaeology.json
[top-processor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_tower_top_archaeology.json
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/trail_ruins.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trail_ruins.json
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[parameters]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[parameter-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L76-L101
[generation-option]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L64
[set-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[placement-set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/trail_ruins.json
[random-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L69-L83
[starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L578
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L107
[locate-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L171-L180
[top-templates]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trail_ruins/tower
[tower-templates]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trail_ruins/tower
[templates]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trail_ruins
[tower-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/tower.json
[roads-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/roads.json
[buildings-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/buildings.json
[grouped-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/buildings/grouped.json
[additions-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/tower/additions.json
[decor-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/decor.json
[top-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/tower/tower_top.json
[projection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L65-L159
[assembly]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L347-L448
[gravel-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/suspicious_gravel.json
[falling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L62-L98
[falling-land]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L180-L234
[cancel-drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L311-L313
[brush-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BrushItem.java#L37-L98
[brush-complete]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L117-L147
[gravel-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L338-L347
[broken-guard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[append-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/rule/blockentity/AppendLoot.java#L11-L32
[brush-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L114
[replaceable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/trail_ruins_replaceable.json
[rule-processor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/RuleProcessor.java#L26-L45
[cap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/CappedProcessor.java#L37-L78
[process-order]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L445-L477
[template-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L254-L323
[loot-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[loot-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootItem.java#L28-L35
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L70
[registry-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-paths]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/registries/Registries.java#L263-L279
[file-paths]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/FileToIdConverter.java#L19-L37
[structure-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L134-L158
[chunk-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
[pool-piece]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/PoolElementStructurePiece.java#L91-L126
[pool-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L138-L181
[template-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L66-L125
[loot-reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[loot-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L13-L27
[loot-scan]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener.java#L49-L74
[brush-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L171-L223
[registry-read]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
