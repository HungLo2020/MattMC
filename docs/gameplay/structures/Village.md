# Village

Visit a **Village** to meet potential trading partners, find crop starters and supplies, and establish a useful stop on an Overworld route. Protect the residents before stripping buildings: a working settlement can be worth more than its first chest. Layouts vary, and no village guarantees a particular house, profession, population, Camel, chest or reward. [Village selections][village-set] · [Piece selection and fit][jigsaw-fit]

## Where to search

The bundled normal world has five village styles. These are the eligible **start biomes**, not a guarantee that each patch contains a village or that every building stays inside that biome.

| Style and structure ID | Eligible start biomes | Useful visual clues |
| --- | --- | --- |
| Plains, `minecraft:village_plains` | [Plains and Meadow](../biomes/PlainsAndMeadows.md) | Oak and Cobblestone buildings. [Eligibility][plains-biomes] · [Example house][plains-house-template] |
| Desert, `minecraft:village_desert` | [Desert](../biomes/DesertsBadlandsAndSavannas.md#desert) | Sandstone buildings, including Smooth and Cut Sandstone. [Eligibility][desert-biomes] · [Example house][desert-house-template] |
| Savanna, `minecraft:village_savanna` | [Savanna](../biomes/DesertsBadlandsAndSavannas.md#savanna) | Acacia construction. [Eligibility][savanna-biomes] · [Example house][savanna-house-template] |
| Snowy, `minecraft:village_snowy` | [Snowy Plains](../biomes/TaigaAndSnowyBiomes.md) | Spruce construction and snowy surroundings; some pieces use Snow Blocks or ice. [Eligibility][snowy-biomes] · [House selections][snowy-houses] · [Example house][snowy-house-template] |
| Taiga, `minecraft:village_taiga` | [Taiga](../biomes/TaigaAndSnowyBiomes.md) | Spruce and Cobblestone construction. [Eligibility][taiga-biomes] · [Example house][taiga-house-template] |

Search the **Overworld** in the bundled normal setup. Nearby-looking alternatives such as Sunflower Plains, Savanna Plateau and Snowy Taiga are not village start biomes in these tags. The Nether, End and loaded [Primordial Caves](../dimensions/PrimordialCaves.md#what-currently-generates) biome selections do not match them either. Custom worlds and data packs can change eligibility. [Normal preset][normal-preset] · [Loaded Primordial dimension][primordial] · [Dimension selection][dimension-bake] · [Biome-source filtering][biome-filter]

New starts require structure generation to be enabled. The shared placement grid chooses candidate chunks, then checks the structure and its start biome. Each style begins near the sampled surface and grows connected pieces subject to depth, connection and space checks. Do not expect evenly spaced towns, a fixed street plan or every available building. Inspect paths and drops yourself before using the village as a travel stop. [Generation option][generation-option] · [Candidate placement][spread] · [Start generation][start-generation] · [Start-biome check][start-biome] · [Plains definition][plains-definition] · [Desert definition][desert-definition] · [Savanna definition][savanna-definition] · [Snowy definition][snowy-definition] · [Taiga definition][taiga-definition] · [Surface start][jigsaw-start] · [Expansion checks][jigsaw-fit]

### Buying a village map

An **Apprentice Cartographer**, trading level 2, can offer a village map with a base payment of **8 Emeralds plus 1 Compass**. Raise a suitable Cartographer through [Trading](../trading/Trading.md#offers-and-trading-levels), then check its actual offers before collecting the payment. These maps are in the regular trade table; they do not require the experimental trade rebalance. [Regular Cartographer offers][map-offers] · [Table selection][trade-table-selection]

| Map destination | Cartographer types eligible to offer it |
| --- | --- |
| Plains Village | Taiga, Snow, Savanna, Desert |
| Desert Village | Savanna, Jungle |
| Savanna Village | Plains, Jungle, Desert |
| Snowy Village | Taiga, Swamp |
| Taiga Village | Swamp, Snow, Plains |

The restriction uses the villager's **stored type**, not whichever biome it is currently standing in. Moving a Cartographer to another biome does not itself change the check. Up to two successful offers are chosen at random from that level's pool, which also includes other listings, so eligibility does not promise your preferred map. The displayed Emerald price can differ from the base price. [Type check][map-type] · [Offer selection][offer-selection] · [Level offer limit][trade-table-selection] · [Price calculation][trade-price]

A map offer also needs a successful structure search from the **Cartographer's position in its current dimension**. The search is finite, and a failed search produces no offer for that listing. A destination may be far away; do not interpret the search parameter as a fixed travel distance. Its exclusion of already referenced starts is not a record of which places you personally explored. The five village-map target tags select the matching village styles, without a normal-versus-zombie distinction. [Map creation][map-search] · [Search gates][map-search-gates] · [Search traversal][search-rings] · [Reference check][search-references] · [Plains target][plains-map-tag] · [Desert target][desert-map-tag] · [Savanna target][savanna-map-tag] · [Snowy target][snowy-map-tag] · [Taiga target][taiga-map-tag]

**Jungle Explorer Maps and Swamp Explorer Maps lead to a [Jungle Temple](JungleTemple.md) and [Swamp Hut](SwampHut.md), respectively.** They are not routes to additional generated village styles. [Jungle map target][jungle-map-tag] · [Swamp map target][swamp-map-tag]

### Locating with commands

With permission level 2, search the current dimension for any of the five styles:

```text
/locate structure #minecraft:village
```

For a specific style, replace the tag with an exact structure ID from the table, for example:

```text
/locate structure minecraft:village_desert
```

The `#` matters: `minecraft:village` is the aggregate **tag**, not a standalone structure ID. The result gives X and Z with `~` for Y; its suggested teleport preserves your current height and is not a safe arrival point. Commands and maps find a structure, not a guaranteed living population or particular shop. [Village tag][village-tag] · [Permission and command][locate-command] · [Search and result][locate-search] · [Coordinate formatting][locate-result]

## Arrive before you collect

Bring your own food, armor, weapon, usable tools, lighting and spare solid blocks. Add Emeralds and a Compass if map trading is the goal. Carry a bed if you want a separate travel camp, and understand [sleeping and respawn](../blocks/Bed.md#sleeping-and-respawn) before changing your return point.

1. **Record the route and check the terrain.** Mark a clear way back, inspect house entrances, and repair awkward paths or exposed drops before using them regularly. A generated street is not a safety inspection.
2. **Find and protect living residents.** Keep hostile mobs away, add lighting and build secure shelter while preserving usable routes to beds and workstations. [Zombies](../mobs/Zombie.md) target villagers, and door-breaking is possible under their qualifying conditions; a wooden door alone is not a complete defense. [Zombie targets][zombie-targets] · [Door behavior][zombie-doors]
3. **Check your effects before moving into the settlement.** Bad Omen can turn entry into a raid countdown. Use [Raid: starting or avoiding a raid](../mechanics/Raid.md#starting-or-avoiding-a-raid) for recognition and cancellation; simply finding generated houses is not the full village-recognition rule.
4. **Keep essential beds and job sites.** Removing a bed or workstation can undermine the residents you came to use. Follow [Villager jobs](../mobs/Villager.md#professions-and-job-sites), [breeding and population](../mobs/Villager.md#breeding-and-population), and [trade restocking](../trading/Trading.md#stock-and-restocking) before redesigning the settlement.
5. **Inspect the actual inhabitants and offers.** Resident selections include unemployed adults, nitwits and babies. A workshop's appearance does not guarantee a matching trader, and a job-site block does not guarantee that its owner can reach it. [Plains resident pool][plains-residents] · [Desert resident pool][desert-residents] · [Savanna resident pool][savanna-residents] · [Snowy resident pool][snowy-residents] · [Taiga resident pool][taiga-residents]

Village pieces can connect to animal, Cat and Iron Golem templates. Ordinary Desert meeting points also have a Camel route. Look for what actually placed and survived; these routes do not promise a headcount or a mount at every destination. Use [Camel](../mobs/Camel.md#finding-a-camel) for riding and alternative finding routes, and [Iron Golem](../mobs/IronGolem.md#defense-and-player-relations) for defense, repair and player relations. A golem is not complete protection against every threat. [Example town-center connectors][plains-center-template] · [Animal selections][animals-pool] · [Cat selections][cats-pool] · [Golem pool][golem-pool] · [Desert center][desert-center-template] · [Camel pool][camel-pool] · [Camel template][camel-template] · [Entity inclusion][entity-settings] · [Entity placement][entity-placement]

## Zombie villages

All five styles have **zombie town-center candidates** alongside their ordinary starts. These abandoned variants are a generation choice, not proof that a raid or infection already happened there. Expect a damaged-looking, less protected settlement: the associated zombie processors can omit doors and lights and replace some construction with Cobwebs. Details vary by style and piece; even the snowy zombie town-center entries and snowy zombie houses do not use identical processor settings. [Plains starts][plains-starts] · [Desert starts][desert-starts] · [Savanna starts][savanna-starts] · [Snowy starts][snowy-starts] · [Taiga starts][taiga-starts] · [Plains changes][zombie-plains] · [Desert changes][zombie-desert] · [Savanna changes][zombie-savanna] · [Snowy house changes][zombie-snowy] · [Snowy house selections][snowy-zombie-houses] · [Taiga changes][zombie-taiga]

Zombie-house connectors use separate resident pools containing **Zombie Villagers**. Do not enter expecting to trade. If you want to restore residents, prepare a roofed enclosure and the supplies in the [Zombie Villager curing guide](../mobs/ZombieVillager.md#curing-step-by-step) before approaching them. Curing uses Weakness and an ordinary Golden Apple; the mob remains dangerous during the process. The reviewed village chest tables do not supply Golden Apples, so bring your own curing supplies. [Example zombie house][zombie-house-template] · [Zombie resident pool][zombie-residents] · [Resident template][zombie-resident-template] · [Curing interaction][curing]

The checked zombie resident templates are marked persistent, which protects against ordinary distance despawning; it does not make them safe from sunlight, damage or removal on Peaceful. Keeping their shelter intact matters more than immediately recovering its materials. A village map or `/locate` result cannot tell you whether those original residents are still alive. [Resident data][zombie-resident-template] · [Persistence loading][persistence-load] · [Peaceful and despawn order][despawn] · [Sunlight behavior][zombie-sunlight]

## Food, crops and building supplies

Inspect farms as well as chests. Some bundled farm pieces contain Wheat that style-specific rules can replace with other crops or stems.

| Style | Additional crops or stems in the checked farm rules |
| --- | --- |
| Plains | Carrots, Potatoes, Beetroot. [Rules][farm-plains] |
| Desert | Beetroot, Melon stems. [Rules][farm-desert] |
| Savanna | Melon stems. [Rules][farm-savanna] |
| Snowy | Carrots, Potatoes. [Rules][farm-snowy] |
| Taiga | Potatoes, Pumpkin stems. [Rules][farm-taiga] |

These are possible replacements, not a list promised in every plot. Replaced crops start young, and a stem is not an immediately harvestable Melon or Pumpkin. Keep enough planting material to maintain the farm or start one at home. Use the [Wheat](../blocks/Wheat.md#mattmc-harvesting-controls), [root-crop](../blocks/RootCrops.md#mattmc-harvesting-controls), [Farmland](../blocks/Farmland.md) and [Pumpkin and Melon](../blocks/PumpkinAndMelon.md) guides for growth and MattMC's harvesting controls. [Farm selections][plains-houses] · [Desert selections][desert-houses] · [Savanna selections][savanna-houses] · [Snowy selections][snowy-houses] · [Taiga selections][taiga-houses] · [Rule execution][rule-processor]

Some village pieces or decorations contain **[Hay Bales](../blocks/HayBale.md#getting-and-unpacking-hay-bales)**, which you can recover and unpack into Wheat. Buildings also offer wood, stone and other useful materials. Decide whether to keep the settlement first: leave its roofs, safe paths, beds and functioning job sites in place until you have replacements. Use the [wood construction](../blocks/WoodConstruction.md#mining-and-drops), [logs](../blocks/TreeLogsAndRoots.md#mining-and-placement) and [Mining](../mechanics/Mining.md) guides for recovery, since appearance alone does not establish the right tool or drop. [Example Hay-bearing house][hay-template] · [Plains decoration selections][plains-decor]

## Chests and worthwhile stops

Check rooms individually. A house may have no chest, and a profession-themed building can contain a workstation without a loot chest. The assigned table follows the **generated container**, not the job held by a villager who later lives there. [Example chest-bearing templates][plains-house-template] · [House selections, including empty choices][plains-houses] · [Template block-entity loading][container-placement]

### Ordinary house supplies

The five house tables each make **3–8 main loot rolls**, plus a separate one-roll Bundle-or-empty pool. The table below lists useful possibilities, not complete chest contents or guaranteed finds. Main entries have different weights, and repeated selections are possible. [Weighted rolls][loot-pool]

| House style | Selected possible supplies |
| --- | --- |
| Plains | Bread, Apples, Potatoes, Emeralds, Oak Saplings, Books. [Assigned template][plains-house-template] · [Loot][plains-house-loot] |
| Desert | Bread, Wheat, Emeralds, Cactus, Books. [Assigned template][desert-house-template] · [Loot][desert-house-loot] |
| Savanna | Bread, Wheat Seeds, Emeralds, Acacia Saplings, a Saddle or Bucket. [Assigned template][savanna-house-template] · [Loot][savanna-house-loot] |
| Snowy | Bread, Potatoes, Beetroot Seeds, Coal, Emeralds, a Furnace or Blue Ice. [Assigned template][snowy-house-template] · [Loot][snowy-house-loot] |
| Taiga | Bread, Potatoes, Sweet Berries, Pumpkin Seeds, Emeralds, Spruce Saplings or Logs. [Assigned template][taiga-house-template] · [Loot][taiga-house-loot] |

Some zombie houses retain the matching house-loot assignment. A chest is a reason to inspect a ruined building carefully, not evidence that the room is safe. [Example zombie house chest][zombie-chest-template]

### Workshop and profession-themed chests

Look for these as **optional finds**, not mandatory stops in every town:

- **Weaponsmith chest:** its 3–8 main rolls can select Iron equipment, food, ingots, Diamonds, Obsidian, a Saddle or Horse Armor. A selected Diamond entry gives **1–3 Diamonds** and a selected Obsidian entry **3–7 Obsidian**; those are per-roll amounts, not guaranteed chest totals. It also has the separate Bundle-or-empty roll. [Plains Weaponsmith template][weaponsmith-template] · [Loot][weaponsmith-loot]
- **Toolsmith chest:** can supply an Iron Pickaxe or Shovel, ingots, Diamonds, Coal, Sticks and Bread. Checked chest-bearing Toolsmith templates occur among Desert and Taiga selections. [Desert template][toolsmith-template] · [Taiga template][taiga-toolsmith-template] · [Loot][toolsmith-loot]
- **Cartographer chest:** can supply Paper, ordinary empty Maps, a Compass, Bread, Sticks and a possible Bundle. These are not the village destination maps sold through trading. Checked chest-bearing Cartographer templates occur in Plains, Savanna, Snowy and Taiga selections. [Example template][cartographer-template] · [Loot][cartographer-loot]
- **Other themed rooms:** possible tables include leather gear and Saddles in Tanneries, fish and Water Buckets in a Fisher cottage, meat and Coal in a Butcher shop, and Wool or Shears in a Shepherd's house. Check the room you actually find; there is no universal profession-chest set for each village. [Tannery template][tannery-template] · [Tannery loot][tannery-loot] · [Fisher template][fisher-template] · [Fisher loot][fisher-loot] · [Butcher template][butcher-template] · [Butcher loot][butcher-loot] · [Shepherd template][shepherd-template] · [Shepherd loot][shepherd-loot]

## Make the return worthwhile

Record the coordinates, route, useful residents, their job sites and any resources you left behind. Establish storage and a safe sleeping arrangement before treating the place as a regular stop. If you plan to move residents, consult [Transport](../mechanics/Transport.md) and prepare the whole route first; finding a village does not complete a transport setup.

**Generated chests do not refill when you revisit.** Once a pending chest table is unpacked, its table reference is cleared and the resulting inventory is saved. Houses and their original template residents are generation placements, not a timed replenishment service. Seek another village for more unopened structure chests; maintain crops, [breed villagers](../mobs/Villager.md#breeding-and-population), and support [restocking](../trading/Trading.md#stock-and-restocking) for renewable benefits. [One-time chest loot][container-loot] · [Chest saving][chest-save] · [Structure placement][structure-placement] · [Entity placement][entity-placement]

Related: [Structures](Structures.md) · [Villager](../mobs/Villager.md) · [Trading](../trading/Trading.md) · [Raid](../mechanics/Raid.md) · [Iron Golem](../mobs/IronGolem.md) · [Zombie Villager](../mobs/ZombieVillager.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the five loaded definitions, exact biome and map tags, weighted pools, decoded template block/entity data, processors, Cartographer offer and search calls, loot assignments and active placement/loading paths. The pool/template graph is a conservative inventory of candidates: it does **not** prove that every connected template fits or is accepted in a generated village. [Bundled data source][server-data] · [Worldgen registry loading][world-loader] · [Loaded registry types][registry-types] · [Template loading and data conversion][template-load] · [Jigsaw dispatch][jigsaw-dispatch] · [Piece placement][piece-placement] · [Entity settings][entity-settings] · [Loot loading][loot-loading]

No in-game village search, map purchase, generation, combat, curing, crop harvest, chest opening, resident transport or fortification test was run. Advice here is source-informed, not a tested safe layout or yield guarantee. Existing chunks, changed terrain, data packs, custom template overrides and server/world settings can differ.
[animals-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/common/animals.json
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L45-L64
[butcher-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_butcher.json
[butcher-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/savanna/houses/savanna_butchers_shop_2.nbt
[camel-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/camel.json
[camel-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/desert/camel_spawn.nbt
[cartographer-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_cartographer.json
[cartographer-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_cartographer_1.nbt
[cats-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/common/cats.json
[chest-save]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L82-L99
[container-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L90
[container-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L280-L310
[curing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L139-L162
[desert-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_desert.json
[desert-center-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/desert/town_centers/desert_meeting_point_1.nbt
[desert-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/village_desert.json
[desert-house-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_desert_house.json
[desert-house-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/desert/houses/desert_medium_house_1.nbt
[desert-houses]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/houses.json
[desert-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_desert_village_maps.json
[desert-residents]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/villagers.json
[desert-starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/town_centers.json
[despawn]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L607-L630
[dimension-bake]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183
[entity-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L525
[entity-settings]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L166-L181
[farm-desert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/farm_desert.json
[farm-plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/farm_plains.json
[farm-savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/farm_savanna.json
[farm-snowy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/farm_snowy.json
[farm-taiga]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/farm_taiga.json
[fisher-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_fisher.json
[fisher-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_fisher_cottage_1.nbt
[generation-option]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[golem-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/common/iron_golem.json
[hay-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_stable_1.nbt
[jigsaw-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L134-L152
[jigsaw-fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L347-L452
[jigsaw-start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L63-L159
[jungle-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_jungle_explorer_maps.json
[locate-command]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L68
[locate-result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L162-L182
[locate-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L91-L107
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[loot-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L102
[map-offers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L340-L400
[map-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1550-L1577
[map-search-gates]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L1328-L1343
[map-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1581-L1603
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[persistence-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L379-L389
[piece-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/PoolElementStructurePiece.java#L91-L126
[plains-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_plains.json
[plains-center-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/town_centers/plains_fountain_01.nbt
[plains-decor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/decor.json
[plains-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/village_plains.json
[plains-house-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_plains_house.json
[plains-house-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_big_house_1.nbt
[plains-houses]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/houses.json
[plains-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_plains_village_maps.json
[plains-residents]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/villagers.json
[plains-starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/town_centers.json
[primordial]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[registry-types]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L107
[rule-processor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/RuleProcessor.java#L27-L45
[savanna-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_savanna.json
[savanna-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/village_savanna.json
[savanna-house-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_savanna_house.json
[savanna-house-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/savanna/houses/savanna_medium_house_1.nbt
[savanna-houses]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/savanna/houses.json
[savanna-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_savanna_village_maps.json
[savanna-residents]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/savanna/villagers.json
[savanna-starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/savanna/town_centers.json
[search-references]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L277-L305
[search-rings]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L168-L201
[server-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/packs/repository/ServerPacksSource.java#L47-L54
[shepherd-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_shepherd.json
[shepherd-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/snowy/houses/snowy_shepherds_house_1.nbt
[snowy-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_snowy.json
[snowy-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/village_snowy.json
[snowy-house-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_snowy_house.json
[snowy-house-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/snowy/houses/snowy_small_house_5.nbt
[snowy-houses]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/snowy/houses.json
[snowy-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_snowy_village_maps.json
[snowy-residents]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/snowy/villagers.json
[snowy-starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/snowy/town_centers.json
[snowy-zombie-houses]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/snowy/zombie/houses.json
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L69-L84
[start-biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L131-L140
[start-generation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L577
[structure-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L103
[swamp-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_swamp_explorer_maps.json
[taiga-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_taiga.json
[taiga-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/village_taiga.json
[taiga-house-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_taiga_house.json
[taiga-house-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/taiga/houses/taiga_medium_house_1.nbt
[taiga-houses]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/houses.json
[taiga-map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_taiga_village_maps.json
[taiga-residents]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/villagers.json
[taiga-starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/town_centers.json
[taiga-toolsmith-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/taiga/houses/taiga_tool_smith_1.nbt
[tannery-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_tannery.json
[tannery-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_tannery_1.nbt
[template-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L318-L327
[toolsmith-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_toolsmith.json
[toolsmith-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/desert/houses/desert_tool_smith_1.nbt
[trade-price]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L100
[trade-table-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L836
[village-set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[village-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/village.json
[weaponsmith-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_weaponsmith.json
[weaponsmith-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_weaponsmith_1.nbt
[world-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L32-L58
[zombie-chest-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/zombie/houses/plains_big_house_1.nbt
[zombie-desert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/zombie_desert.json
[zombie-doors]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L145-L166
[zombie-house-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/zombie/houses/plains_small_house_1.nbt
[zombie-plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/zombie_plains.json
[zombie-resident-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/zombie/villagers/unemployed.nbt
[zombie-residents]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/zombie/villagers.json
[zombie-savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/zombie_savanna.json
[zombie-snowy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/zombie_snowy.json
[zombie-sunlight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L228-L253
[zombie-taiga]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/zombie_taiga.json
[zombie-targets]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L113-L121
