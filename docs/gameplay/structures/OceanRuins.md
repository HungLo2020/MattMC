# Ocean Ruins

**Ocean Ruins** offer chest loot, possible [Buried Treasure](BuriedTreasure.md) maps, and archaeology. Bring a serviceable [Brush](../items/Brush.md): the suspicious blocks can contain rewards that breaking ordinary masonry or opening a chest will not provide.

## Where to search

The bundled Overworld definitions divide ruins by biome:

| Ruin type | Eligible biomes |
| --- | --- |
| Warm (`minecraft:ocean_ruin_warm`) | Warm Ocean, Lukewarm Ocean, Deep Lukewarm Ocean |
| Cold (`minecraft:ocean_ruin_cold`) | Ocean, Cold Ocean, Frozen Ocean, Deep Ocean, Deep Cold Ocean, Deep Frozen Ocean |

The structures use ocean-floor placement and adjust to local ground height. Warm and cold variants share one placement set, with the allowed biome determining which can succeed. Being in an eligible ocean does not guarantee a ruin in that patch.

Both definitions configure a **30% large-ruin selection chance**. A selected large ruin then has a **90% chance to attempt a surrounding cluster** of small ruins. That cluster attempts 4–8 positions and can reject positions that overlap the central ruin, so those numbers are not a guarantee of a particular building count.

A fed adult [Dolphin](../mobs/Dolphin.md) can lead toward an ocean ruin or a shipwreck; it does not guarantee fresh loot. With permission level 2, use `/locate structure minecraft:ocean_ruin_warm` or `/locate structure minecraft:ocean_ruin_cold` in the Overworld. See [Structures](Structures.md#finding-a-structure) before relying on the returned coordinates.

## Chests and treasure maps

Chest markers use a **small** or **large** ruin loot table according to the piece size, not its warm/cold type.

| Chest table | General pool | Separate reward pool |
| --- | --- | --- |
| Small | 2–8 weighted rolls for Coal, Wheat, Rotten Flesh, Emeralds, or a Stone Axe | One roll for a Leather Chestplate, Golden Helmet, enchanted Fishing Rod, or treasure-map attempt |
| Large | 2–8 weighted rolls for Coal, Wheat, Gold Nuggets, or Emeralds | One roll for a Golden Apple, enchanted Book, Leather Chestplate, Golden Helmet, enchanted Fishing Rod, or treasure-map attempt |

The map entry has weight **5 of 12** in the small reward pool and **10 of 23** in the large one, approximately 41.7% and 43.5%. These are map-entry chances per chest using the unchanged table. A working map still depends on a successful destination search. General-pool rolls can repeat the same entry and are not counts of distinct items.

The maps target Buried Treasure, allow previously located destinations, and do not inspect chest contents. Use the [treasure-map guide](BuriedTreasure.md#using-a-treasure-map) for what the X means and what to do if no working map was produced.

## Archaeology

Warm ruin generation can turn template sand into **[Suspicious Sand](../items/SuspiciousSand.md)**. Cold ruin generation can turn template gravel into **[Suspicious Gravel](../items/SuspiciousGravel.md)**. The generator attaches the corresponding archaeology loot table to those blocks.

| Archaeology source | Distinctive possible finds |
| --- | --- |
| Warm suspicious sand | Angler, Shelter, and Snort Pottery Sherds; a Sniffer Egg |
| Cold suspicious gravel | Blade, Explorer, Mourner, and Plenty Pottery Sherds |

Both tables also include an Iron Axe, Emerald, Wheat, Wooden Hoe, Coal, and Gold Nugget. Each distinctive item shown above has a **1-in-15 chance per completed block using its unchanged table**, including the warm ruin's [Sniffer Egg](../items/SnifferEgg.md). Cold ruin archaeology does not include a Sniffer Egg in the bundled table.

Brush suspicious blocks **in place**, keeping their support intact. Completing the brushing releases the stored item and leaves ordinary Sand or Gravel. Breaking the block or letting it fall is not the collection method. Follow the [Brush archaeology instructions](../items/Brush.md#archaeology-basics) for timing and broken-Brush restrictions.

Do not assume every ruin contains exactly five suspicious blocks. The replacement processor is capped at five successful substitutions per template-processing pass; terrain, block decay, and the layered cold-ruin assembly affect the final result. The cold generator overlays brick, cracked, and mossy templates rather than placing just one intact building.

## Hazards and preparation

Some template data markers actively create **persistent Drowned** during structure generation. This is separate from ordinary natural water-mob spawning; a ruin is not made safe merely by avoiding a preferred natural-spawn time. See [Drowned](../mobs/Drowned.md) before entering a populated ruin.

- Secure room to surface before stopping at a chest or suspicious block
- Bring a weapon, reliable food, a Brush with usable durability, and tools for careful excavation
- [Water Breathing and Night Vision](../brewing/Brewing.md) can help with longer dives and obscured blocks
- Clear immediate threats before brushing, and expose suspicious blocks without removing their supporting floor
- Search nearby buildings when a cluster generated, but keep track of your route back

## Related pages

- [Shipwreck](Shipwreck.md)
- [Buried Treasure](BuriedTreasure.md)
- [Brush](../items/Brush.md)
- [Dolphin](../mobs/Dolphin.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. Active structure codecs, piece assembly, compressed NBT template markers/materials, loot tables, and archaeology processing were inspected. No in-game generation, Drowned encounter, chest-opening, or brushing test was run. These are bundled-data rules, not guarantees for every seed, old chunk, or changed data pack.

- [Warm structure definition](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/ocean_ruin_warm.json), [cold definition](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/ocean_ruin_cold.json), [warm biomes](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_ruin_warm.json), [cold biomes](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_ruin_cold.json), and [shared placement set](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure_set/ocean_ruins.json)
- [Registered structure type](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java), [probability/temperature codec and generation entry point](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinStructure.java), and [assembly, suspicious-block processing, chest assignment, and Drowned markers](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java)
- [Example small warm template](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/underwater_ruin/warm_2.nbt), [large warm template](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/underwater_ruin/big_warm_5.nbt), and [cold brick-layer template](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/underwater_ruin/brick_2.nbt); [template loading](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java) and [marker dispatch](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java)
- [Small chest loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/underwater_ruin_small.json), [large chest loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/underwater_ruin_big.json), and [exploration-map function](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/ExplorationMapFunction.java)
- [Warm archaeology loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_warm.json) and [cold archaeology loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_cold.json)
- [Per-pass replacement cap](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/CappedProcessor.java), [loot attachment](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/rule/blockentity/AppendLoot.java), [archaeology loot loading and completion](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java), and [unsupported suspicious-block falling](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/BrushableBlock.java)
