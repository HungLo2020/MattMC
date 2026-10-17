# Ruined Portal

A **Ruined Portal** is a place to collect chest supplies, recover Obsidian and sometimes gold, or prepare a new route to the [Nether](../dimensions/Nether.md). Inspect the ground before looting: a broken frame can stand beside Lava, Magma Blocks or a difficult drop. The bundled templates contain **no active portal blocks**; finding the ruin does not establish a working portal or supply everything needed to repair it. [Templates][templates] · [Placement and alterations][piece]

## Prepare for the expedition

- Bring food, lighting, spare building blocks and a way back to your starting point
- Bring an **unbroken Diamond or Netherite Pickaxe** if you intend to collect Obsidian or Crying Obsidian. An **unbroken Iron, Diamond or Netherite Pickaxe** collects Gold Blocks. A chest's Golden Pickaxe does not satisfy either requirement; follow the [Obsidian mining rules](../blocks/Obsidian.md#mining-and-block-properties) and [storage-block harvesting guide](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block)
- Bring a shovel for buried approaches and plan a breathing/escape route for submerged ruins
- For repair, pack ordinary Obsidian and usable [Flint and Steel](../items/FlintAndSteel.md), or a [Fire Charge](../items/FireCharge.md). Treat chest supplies as a possible bonus
- Prepare for the dimension **beyond** the portal before entering. An Overworld salvage trip can stop at the chest without becoming a Nether expedition

The harvesting requirements follow the registered correct-tool requirement, pickaxe and tier tags, tool rules and broken-stack check. [Block properties][blocks] · [Pickaxe tag][pickaxe] · [Iron requirement][iron-tier] · [Diamond requirement][diamond-tier] · [Tool rules][tool-material] · [Broken-tool check][tool-stack]

## Where to look

The bundled structure set contains **seven variants**, using eligible biomes in the normal **Overworld and Nether**. All share the same portal-template collection; the variant changes placement and decoration rather than promising a particular frame. [Structure set][set] · [Normal preset][normal] · [Template selection][structure]

| Variant and structure ID | Eligible bundled biomes | Search approach |
| --- | --- | --- |
| Standard · `minecraft:ruined_portal` | Beaches, rivers, taigas and forests, including Grove and Pale Garden; Mushroom Fields, Ice Spikes, Dripstone Caves, Lush Caves, Savanna, Snowy Plains, Plains and Sunflower Plains | Can be **underground or on the land surface**. A surface search can miss it. [Biomes][standard-biomes] · [Definition][standard] |
| Desert · `minecraft:ruined_portal_desert` | Desert | **Partly buried**, without an air pocket requested by this setup. [Biomes][desert-biomes] · [Definition][desert] |
| Jungle · `minecraft:ruined_portal_jungle` | Jungle, Bamboo Jungle and Sparse Jungle | Land surface, with vines, overgrowth and stronger mossy decoration. [Biomes][jungle-biomes] · [Definition][jungle] |
| Swamp · `minecraft:ruined_portal_swamp` | Swamp and Mangrove Swamp | Uses **ocean-floor placement**, with vines; do not assume a dry surface approach. [Biomes][swamp-biomes] · [Definition][swamp] |
| Mountain · `minecraft:ruined_portal_mountain` | Badlands and windswept hill families; Savanna Plateau, Windswept Savanna, Stony Shore, Meadow, Cherry Grove, Snowy Slopes, Frozen Peaks, Jagged Peaks and Stony Peaks | Can be **inside the mountain or on the land surface**. [Biomes][mountain-biomes] · [Definition][mountain] |
| Ocean · `minecraft:ruined_portal_ocean` | The bundled ocean and deep-ocean families | Ocean floor; prepare for water access. [Biomes][ocean-biomes] · [Definition][ocean] |
| Nether · `minecraft:ruined_portal_nether` | Nether Wastes, Soul Sand Valley, Crimson Forest, Warped Forest and Basalt Deltas | Variable height within Nether terrain, with eligible masonry replaced by Blackstone forms. [Biomes][nether-biomes] · [Definition][nether] · [Replacements][blackstone] |

Height selection examines terrain after choosing the setup; the table is not a fixed Y-level or visibility guide. The shared random-spread placement uses **spacing 40 and separation 15, in chunks**. Those are candidate-placement settings, not a promise to find a portal every 40 chunks. New starts require structure generation to be enabled and must pass the allowed-biome check. [Height selection][height] · [Placement data][set] · [Candidate calculation][random-spread] · [World option][world-option] · [Set filtering][set-filter] · [Start selection][starts]

These tags do not include the End's biomes or the bundled Primordial Caves candidates: **Primordial Plains, Dry Midlands and Primordial Ocean**. The loaded Primordial dimension definition takes precedence over the Normal preset's smaller biome list; neither establishes a ruined-portal search route there. See [Primordial Caves generation](../dimensions/PrimordialCaves.md#what-currently-generates). Data packs, custom presets, saved template overrides and previously generated terrain can differ. [Loaded Primordial dimension][primordial-dimension] · [Dimension precedence][dimension-precedence] · [Biome-source filtering][set-filter] · [Template overrides][template-load]

### Using locate

With permission level 2, search **all seven variants in the current dimension** with:

```text
/locate structure #minecraft:ruined_portal
```

The `#` matters: `minecraft:ruined_portal` without it selects **only the standard variant**. A specific variant can also be searched, for example:

```text
/locate structure minecraft:ruined_portal_nether
```

The result does not transport you, switch dimensions or build a ruin. Its suggested teleport retains your current Y with `~`; it is **not a safe arrival point**, particularly for underground, submerged or Nether ruins. A failed search does not prove that no portal exists anywhere in the world. [Variant tag][locate-tag] · [Permissions and search][locate] · [Result coordinates][locate-output]

## Survey the ruin before digging

Look for the broken Obsidian formation, masonry and surrounding Netherrack. Generation chooses among **ten ordinary templates and three giant templates**, with a **5% giant-template selection branch**, then rotates and may mirror the selected template. This is one selected layout, not thirteen parts assembled together. [Selection and transforms][structure] · [Template collection][templates]

The final ruin can differ substantially from its raw template:

- Each processed ordinary Obsidian block has a **15% replacement chance** to become Crying Obsidian. Do not count every dark frame block as repair material. [Aging processor][aging]
- Each template Gold Block has a **30% removal chance**. One checked ordinary template already contains no Gold Block before processing, so visible gold is not guaranteed. [Removal rule][piece] · [Template without gold][no-gold-template]
- Masonry can become cracked, mossy, stairs or slabs; jungle setups can add persistent Jungle Leaves and vines. Netherrack patches and downward columns are added around and below the ruin. [Aging][aging] · [Surrounding decoration][piece]
- Template Lava becomes Magma Block for ocean-floor placement, Netherrack for cold placements, or has a **20% Magma replacement chance** otherwise. Non-cold placements also have a **7% Magma replacement chance** for processed Netherrack and for the surrounding Netherrack placements. These rules do not remove every hazard already present in the terrain. [Lava and Netherrack rules][piece]

Dig from a controlled edge, inspect beneath blocks before removing them, and build a dry, stable work area. The placement code can preserve existing Lava where a template would have put a non-full-shaped block. Burial, surrounding fluids and placement changes can obstruct access even when the raw template contains a chest. [Existing-Lava handling][submerged] · [Template placement][template-place]

### Hazards at the site

**Magma Blocks hurt living entities that step on them without stepping carefully** and can start downward [Bubble Columns](../blocks/BubbleColumns.md) in source water. Sneaking addresses the ordinary floor callback; it is not protection from nearby Lava, falls or the underwater approach. See [Magma-floor safety](../blocks/SoulSandSoilAndMagma.md#magma-floor-safety) for the separate protection rules. [Magma callback][magma] · [Column creation and contact][bubbles] · [Active step dispatch][step-dispatch]

**Lava and fire can affect the surrounding world.** Lava can ignite nearby flammable material when the fire rules allow it, and ordinary fire on Netherrack or Magma has persistent support in both bundled dimensions. Clear or protect nearby wood and vegetation before lighting the frame; do not rely on rain to clear that supported fire. The [Fire guide](../blocks/Fire.md#support-persistence-and-spread) owns spread and extinguishing rules. Ordinary Water Bucket placement evaporates in the Nether, so prepare blocks and another escape plan there. [Lava ignition][lava-fire] · [Fire handling][fire-tick] · [Persistent bases][infiniburn-overworld] · [Nether bases][infiniburn-nether] · [Bucket behavior][bucket] · [Nether dimension type][nether-type]

**Check for nearby Piglins before opening the chest or mining gold in the Nether.** Those actions have separate anger triggers, even while wearing gold armor. The [Piglin guide](../mobs/Piglin.md#gold-armor-and-aggression) explains visibility and retaliation limits. The checked ruin templates save no mobs or spawners, and their definitions have no structure spawn overrides; that does not make the surrounding area free of hostiles. [Chest interaction][chest-use] · [Guarded-block breaking][guard-break] · [Guarded tag][guarded] · [Anger selection][piglin-anger] · [Templates][templates] · [Definitions][standard] [nether][]

## Chest rewards and resource recovery

Each of the **thirteen raw templates contains one Chest assigned to `minecraft:chests/ruined_portal`**. This establishes the loot route, not a guaranteed intact or accessible chest in every generated ruin. The placed chest loads that assignment and generates its contents through the server's loot registry when unpacked. [Templates][templates] · [Saved data placement][template-place] · [Chest data loading][chest-load] · [Menu unpacking][container-menu] · [Loot resolution][container-loot]

The main pool makes **4–8 weighted selections**. Useful possible entries include:

| Purpose | Possible result from one selected entry |
| --- | --- |
| Repair and ignition | **1–2 Obsidian**, **1–4 Flint**, **9–18 Iron Nuggets**, **1 Flint and Steel**, or **1 Fire Charge** |
| Gold supplies | **4–24 Gold Nuggets**, **2–8 Gold Ingots**, or **1–2 Gold Blocks** |
| Equipment | One randomly enchanted golden tool, sword or armor piece, or one Golden Horse Armor |
| Food and brewing | One Golden Apple or Enchanted Golden Apple, **4–12 Golden Carrots**, or **4–12 Glistering Melon Slices** |
| Other finds | One Clock, Bell or Light Weighted Pressure Plate |

The rows group alternatives; you do **not** receive every listed item. Entries can repeat, and these quantities are **per selection**, not whole-chest caps. Crying Obsidian is not an entry in this chest table. [Complete chest table][loot] · [Repeated weighted selection][loot-pool]

A second, separate pool makes one choice between **nothing** at weight 1 and **1–2 [Lodestones](../blocks/Lodestone.md)** at weight 2. That is a **2-in-3 configured chance** for Lodestones, not a guaranteed navigation reward. [Separate pool][loot] · [Default weights][loot-weights] · [Selection][loot-pool]

For placed resources, use the correct **unbroken** pickaxe and secure the area where drops will land. Each harvested Obsidian, Crying Obsidian or Gold Block drops one matching block; Silk Touch is unnecessary and Fortune does not multiply these drops. Keep ordinary Obsidian needed for repair, save [Crying Obsidian](../items/CryingObsidian.md) for its own uses, and follow [Gold Block packing/unpacking](../blocks/ResourceStorageBlocks.md#gold-block) for processing recovered gold. [Harvest dispatch][harvest] · [Obsidian loot][obsidian-loot] · [Crying loot][crying-loot] · [Gold loot][gold-loot]

## Repair and light a normal Nether portal

A ruin is a starting point for construction. Choose a usable frame position and follow the canonical [portal construction guide](../blocks/NetherPortals.md#building-and-activating):

1. Make an **upright rectangle** with a clear interior **2–21 blocks wide and 3–21 blocks high**
2. Use **ordinary Obsidian** along the bottom, top and both sides. Replace Crying Obsidian on any required edge; it is not accepted as frame material
3. The four outside corners are optional. The smallest frame therefore needs **10 ordinary Obsidian blocks** around a **2×3 interior**, counting any usable pieces already present
4. Remove blocks and fluids from the opening. The shape check permits air, fire-tagged blocks and existing Nether portal cells, not arbitrary decorations; initial ignition looks for a valid shape with no existing portal cells
5. Place fire **inside** the opening, for example by using usable Flint and Steel on the inner top face of a bottom-frame block. This normal ignition route works in the **Overworld and Nether only**

These are construction checks, not a promise that the discovered ruin needs only one missing block. If the opening will not light, recheck the required edges, Crying Obsidian, orientation, interior size, obstructions and the ignition tool's condition. [Frame predicate and dimensions][frame] · [Empty-shape requirement][frame-empty] · [Fire placement and dimension gate][fire-activate] · [Flint and Steel placement][flint-use] · [Broken-item use guard][item-use]

After activation, removing required frame support or obstructing the active interior can collapse the ordinary portal on relevant neighbor updates. The ordinary portal targets the **Nether from the Overworld**, and the **Overworld from the Nether**. Exit selection can use another portal or attempt new construction; it does not promise an exact pairing or safe landing. Check the [Nether portal destination and linking rules](../blocks/NetherPortals.md#destination-and-linking), then record and protect both exits. [Support check][portal-support] · [Destination and creation failure][portal-destination]

## Optional MattMC route: Primordial Caves

Repair and activate the ordinary portal **first**. To convert it, drop **one [Pitcher Pod](../items/PitcherPod.md), separated from its stack**, into the complete active Nether portal. The conversion consumes the **entire dropped item entity**, including a whole stack if that is what you threw, and the item is discarded even when the completeness check prevents conversion. Crying Obsidian is not the conversion ingredient. [Conversion and discard][conversion]

This changes that portal's destination to [Primordial Caves](../dimensions/PrimordialCaves.md); keep another ordinary Nether route if needed. A converted portal used **inside Primordial Caves returns toward the Overworld**, including when the original conversion happened in the Nether. Its support and exit-search rules differ from an ordinary Nether portal, so use the [Primordial portal guide](../blocks/NetherPortals.md#primordial-caves-portal) before altering the frame or relying on a return route. [Converted support and destination][primordial-portal]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked seven structure definitions, their shared placement set and recursively resolved biome tags, all **13 compressed NBT templates**, placement processors, both chest-loot pools, harvesting, ignition and MattMC's portal-conversion route. No in-game search, seed survey, generation, mining, chest opening, fire-spread, repair, conversion or portal round trip was performed. Preparation advice follows the inspected source; server data packs, settings and saved worlds can change results.

The active world loader reads the structure/structure-set registries and tags from resources. The registered ruined-portal type reaches its template selector, and chunk placement dispatches the chosen piece into template placement and processing. Template loading checks saved overrides before bundled resources; chest loot comes from reloadable server resources. This verifies the source route rather than a generated-world result. [World loading][world-load] · [Registry loading][registry-load] · [Resource scan][registry-read] · [Type registration][structure-type] · [Chunk placement][chunk-place] · [Piece dispatch][piece-dispatch] · [Template dispatch][template-dispatch] · [Template loading][template-load] · [Loot reload][loot-reload]

Related: [Structures](Structures.md) · [Nether](../dimensions/Nether.md) · [Nether and Primordial Caves portals](../blocks/NetherPortals.md) · [Obsidian and Crying Obsidian](../blocks/Obsidian.md) · [Fire and Soul Fire](../blocks/Fire.md)

[piece]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java#L114-L285
[structure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure.java#L34-L157
[height]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure.java#L169-L237
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L49
[tool-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[standard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal.json
[standard-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_standard.json
[desert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_desert.json
[desert-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_desert.json
[jungle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_jungle.json
[jungle-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_jungle.json
[swamp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_swamp.json
[swamp-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_swamp.json
[mountain]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_mountain.json
[mountain-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_mountain.json
[ocean]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_ocean.json
[ocean-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_ocean.json
[nether]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_nether.json
[nether-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_nether.json
[blackstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/BlackstoneReplaceProcessor.java
[random-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L69-L83
[world-option]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[set-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L578
[primordial-dimension]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[dimension-precedence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183
[template-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L66-L125
[locate-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/ruined_portal.json
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L107
[locate-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L171-L180
[aging]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/BlockAgeProcessor.java#L34-L99
[templates]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ruined_portal
[no-gold-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ruined_portal/portal_3.nbt
[submerged]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/LavaSubmergedBlockProcessor.java#L18-L33
[template-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L254-L323
[magma]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/MagmaBlock.java#L29-L64
[bubbles]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BubbleColumnBlock.java#L50-L107
[step-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L847-L852
[lava-fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L78-L118
[fire-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L137-L211
[infiniburn-overworld]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/infiniburn_overworld.json
[infiniburn-nether]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/infiniburn_nether.json
[bucket]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BucketItem.java#L101-L140
[nether-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_nether.json
[chest-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L247-L258
[guard-break]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487
[guarded]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[piglin-anger]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L497-L533
[chest-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L82-L89
[container-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L79-L92
[container-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ruined_portal.json
[loot-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[obsidian-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/obsidian.json
[crying-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/crying_obsidian.json
[gold-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/gold_block.json
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L26-L179
[frame-empty]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L50-L61
[fire-activate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L156-L218
[flint-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java#L25-L46
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[portal-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L91-L108
[portal-destination]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L186-L232
[conversion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172
[primordial-portal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L69-L141
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L70
[registry-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-read]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[structure-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L35
[chunk-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
[template-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L81-L117
[loot-reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
