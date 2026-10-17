# Bastion Remnant

A Bastion Remnant (`minecraft:bastion_remnant`) is a ruined Blackstone complex in the [Nether](../dimensions/Nether.md), with chest rewards and resident [Piglins](../mobs/Piglin.md), [Piglin Brutes](../mobs/PiglinBrute.md), and possible [Hoglins](../mobs/Hoglin.md). Visit for loot such as Netherite Upgrade Smithing Templates, but prepare a retreat before entering: **gold armor does not pacify Brutes or excuse stealing from Piglins**. [Structure][bastion] · [Residents][piglin-pool] · [Hoglin template][hoglin-template]

## Where to look

In the bundled normal world, Bastions can start in **Nether Wastes, Crimson Forest, Soul Sand Valley, and Warped Forest**. Basalt Deltas is excluded from the start-biome tag. This describes where a start is eligible, not a boundary that every generated piece must stay inside. [Normal Nether][normal] · [Nether biome source][nether-biomes] · [Allowed start biomes][bastion-biomes]

Bastions share a structure-placement set with [Nether Fortresses](NetherFortress.md), weighted **3 for Bastions and 2 for fortresses**. If a selected structure fails to generate, the generator can try the other choice. Those weights do not guarantee three Bastions per five structures found, a fixed travel direction, or a maximum search distance. New starts also require structure generation to be enabled. [Placement set][complexes] · [Selection and retry][structure-select] · [Generation setting][structure-setting]

Look for Blackstone buildings and broken walkways rather than the fortress's Nether-brick corridors. Players with permission level 2 can use `/locate structure minecraft:bastion_remnant` while in the Nether; follow the [locating cautions](Structures.md#finding-a-structure), especially the unsafe suggested teleport height.

## Layouts and residents

The start pool chooses equally weighted **housing units, Hoglin stables, treasure, or bridge** layouts, then connects further pieces. These are different starting layouts, not four wings promised in every Bastion. Optional pieces, failed connections, and processing make a fixed room or gold-block count unreliable. For example, the general degradation processor can replace some Gold Blocks with cracked bricks. [Start pool][starts] · [Assembly][jigsaw-assembly] · [Degradation][degradation]

The bundled pieces connect to mob pools which can place sword Piglins, crossbow Piglins, Brutes, or an empty piece; Hoglins have a separate pool. The templates named `melee_piglin` and `melee_piglin_always` contain **Piglin Brutes**, despite their names. These are placed residents: the active template path creates the saved entity and runs its structure-spawn initialization. [Connected bridge base][bridge-base] · [Piglin pool][piglin-pool] · [Melee pool][melee-pool] · [Brute identity][brute-template] · [Template placement][pool-place] · [Entity initialization][entity-place]

A Bastion has **no special natural-spawn override**. Ordinary biome spawning still applies, so a cleared room is not a permanently safe room. Brutes have no bundled biome spawn-list entry; their verified world-generation route is the resident templates. See the two mob guides for their different behavior. [Empty override][bastion] · [Biome fallback][spawn-overrides]

## Chest rewards

A chest's assigned loot table matters more than the broad name of the building. Decoded template chests connect all four tables below to the active placement and container-loading path. Not every chest in a bridge or stable uses its special table: ramparts can use the general table. [Bridge entrance][bridge-entrance] · [Bridge rampart][bridge-rampart] · [Stable interior][stable-inner] · [Treasure center][treasure-center] · [Block-entity placement][block-place] · [Chest loading][chest-load] · [Loot filling][chest-loot]

| Chest table | Useful rewards in the bundled table |
| --- | --- |
| Bridge | A dedicated roll gives **1 Lodestone**; other pools include gold, a Crossbow, arrows and supplies. [Table][bridge-loot] |
| Hoglin stable | Possible Ancient Debris, Netherite Scrap, diamond tools, a Saddle, gold and supplies. [Table][stable-loot] |
| General (`bastion_other`) | Possible Ancient Debris, Netherite Scrap, diamond tools, Piglin Banner Pattern, Pigstep music disc and Soul Speed equipment/books. [Table][other-loot] |
| Treasure | Three rolls from the valuable-item pool, including possible Netherite Ingots, Ancient Debris, Netherite Scrap, Diamonds, diamond equipment or an Enchanted Golden Apple; another pool supplies materials. [Table][treasure-loot] |

Each unchanged table has a separate **1-in-12** roll for **1 Snout Armor Trim Smithing Template**. A treasure-table chest also has a dedicated roll for **1 Netherite Upgrade Smithing Template**; the bridge, stable and general tables instead have a **1-in-10** chance for one. These statements apply to an unlooted chest using that table, not to every Bastion or every chest you find. Other rewards remain random, and already-looted containers or replacement data packs can differ. [Treasure template pools][treasure-templates] · [General template pools][other-templates] · [Bridge table][bridge-loot] · [Stable table][stable-loot]

## Preparing and exploring

- **Wear one piece of gold armor for ordinary Piglins.** Keep useful combat equipment too: Brutes target players regardless of gold, and attacks or guarded-block theft can anger ordinary Piglins. Read [gold armor and aggression](../mobs/Piglin.md#gold-armor-and-aggression) before opening a chest
- **Secure crossings and a return route first.** Bring food, tools and building blocks; use solid cover against crossbows and avoid dropping into a room you cannot leave
- **Treat treasure basins as a separate hazard.** The connected lava-basin template includes lava and a spawner configured for Magma Cubes. Do not assume that a quiet room or a few torches has disabled it. This review does not establish a spawner-control or farming design. [Basin pool][basin-pool] · [Lava-basin template][basin]
- **Barter with an ordinary adult Piglin away from the fight.** Bring Gold Ingots if that is part of the trip; Brutes do not barter. Follow [bartering](../mobs/Piglin.md#bartering) for valid offers and random results
- **Prepare for the Nether itself.** Water buckets do not place water here and beds explode; follow [Nether hazards](../dimensions/Nether.md#hazards-to-plan-around) and its respawn guidance

For collecting the building itself, follow [Blackstone and Gilded Blackstone mining](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops) and [Gold Block harvesting](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block); a gold-colored tool is not proof that it can collect gold.

Looting chests and collecting placed gold are separate from MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), which can insert ordinary listed items in Creative. Browser availability does not establish a natural source or make a Bastion safer.

Related: [Structures](Structures.md) · [Nether Fortress](NetherFortress.md) · [Piglin](../mobs/Piglin.md) · [Piglin Brute](../mobs/PiglinBrute.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Traced the normal Nether preset, biome eligibility, structure set and selection, jigsaw pools, connected binary templates, resident entity initialization, chest loot dispatch and bundled loot tables. Template contents were decoded from NBT; this is not an in-game generation, data-fix, chest-opening, combat or seed test. Data packs, custom presets, saved templates and existing terrain can differ. No exact seed, full-layout completeness, resident count or guaranteed travel distance was established.

The active placement chain continues from [structure-set filtering][structure-state] through [structure generation][structure-generate], [jigsaw dispatch][jigsaw], [chunk decoration][decorate], [piece dispatch][piece-dispatch] and [pool dispatch][pool-dispatch] to the placement cited above. Runtime template loading also performs [data-version conversion][load-template].

[bastion]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure/bastion_remnant.json#L1-L13
[piglin-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/mobs/piglin.json
[hoglin-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/mobs/hoglin.nbt
[normal]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L24-L35
[nether-biomes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L55-L69
[bastion-biomes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/bastion_remnant.json#L1-L8
[complexes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure_set/nether_complexes.json#L1-L18
[structure-select]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L538
[structure-setting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[starts]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/starts.json#L1-L41
[jigsaw-assembly]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L68-L144
[degradation]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/processor_list/bastion_generic_degradation.json
[bridge-base]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/bridge/starting_pieces/entrance_base.nbt
[melee-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/mobs/piglin_melee.json
[brute-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/mobs/melee_piglin.nbt
[pool-place]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L137-L181
[entity-place]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L522
[spawn-overrides]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[bridge-entrance]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/bridge/starting_pieces/entrance.nbt
[bridge-rampart]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/bridge/ramparts/rampart_0.nbt
[stable-inner]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/hoglin_stable/small_stables/inner_2.nbt
[treasure-center]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/treasure/bases/centers/center_0.nbt
[block-place]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L292-L309
[chest-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L82-L88
[chest-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L88
[bridge-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/bastion_bridge.json
[stable-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/bastion_hoglin_stable.json
[other-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json
[treasure-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/bastion_treasure.json
[treasure-templates]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/bastion_treasure.json#L356-L378
[other-templates]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json#L519-L545
[basin-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/treasure/bases.json
[basin]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/treasure/bases/lava_basin.nbt
[structure-state]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L63
[structure-generate]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L554-L576
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L135-L152
[decorate]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L101
[pool-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/PoolElementStructurePiece.java#L91-L126
[load-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L318-L327
