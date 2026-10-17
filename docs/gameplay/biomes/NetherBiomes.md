# Nether biomes

Choose a Nether route for the material or expedition you want: **Crimson and Warped Forests for fungus timber and vegetation, Soul Sand Valley for soul ground and fossil searches, and Basalt Deltas for Basalt and Blackstone terrain**. The bundled **Normal** preset selects all five biomes below through its Nether multi-noise preset. It does not give them equal area or promise a nearby example of each. [Normal preset][normal] · [Parameter preset][nether-preset] · [Five selections][nether-selection] · [Active selector][nether-source]

Use the [Nether dimension guide](../dimensions/Nether.md) for portals, coordinate scaling, respawning, and dimension-wide hazards. This page helps choose where to explore after arriving.

## Compare the five biomes

See [Finding ores](../mechanics/FindingOres.md#nether-quartz-gold-and-ancient-debris) for Nether Gold, Quartz and Debris origin heights, biome counts and host restrictions.

Surface materials below are outputs of conditional surface rules, not a uniform floor covering. The Normal preset uses Netherrack as its base terrain and applies these biome-specific rules. [Surface settings][nether-surfaces] · [Surface generation][surface-caller]

| Biome and exact ID | What to look for | Biome monster candidates |
| --- | --- | --- |
| [Nether Wastes](#nether-wastes) — `minecraft:nether_wastes` | Netherrack, conditional Soul Sand/Gravel patches, ordinary mushrooms | [Ghast](../mobs/Ghast.md), [Zombified Piglin](../mobs/ZombifiedPiglin.md), Magma Cube, Enderman, Piglin |
| [Soul Sand Valley](#soul-sand-valley) — `minecraft:soul_sand_valley` | Soul Sand/Soul Soil surfaces, Basalt pillars, eligible Nether fossils | Skeleton, [Ghast](../mobs/Ghast.md), Enderman |
| [Crimson Forest](#crimson-forest) — `minecraft:crimson_forest` | Crimson Nylium, huge Crimson Fungi, Weeping Vines | [Zombified Piglin](../mobs/ZombifiedPiglin.md), [Hoglin](../mobs/Hoglin.md), Piglin |
| [Warped Forest](#warped-forest) — `minecraft:warped_forest` | Warped Nylium, huge Warped Fungi, Nether Sprouts, Twisting Vines | Enderman |
| [Basalt Deltas](#basalt-deltas) — `minecraft:basalt_deltas` | Basalt/Blackstone surfaces, Basalt columns, Lava/Magma delta features | [Ghast](../mobs/Ghast.md), Magma Cube |

All five also list **Striders** in the creature category. These are the complete biome monster lists, not every entity that can occur there: structures, spawners, arriving mobs, and players provide other routes. Each entry still needs its placement, difficulty, population, and other spawn checks. [Wastes data][nether_wastes] · [Valley data][soul_sand_valley] · [Crimson data][crimson_forest] · [Warped data][warped_forest] · [Deltas data][basalt_deltas] · [Natural-spawn checks][spawn-checks]

## Nether Wastes

Start here if you want a general Nether-material route without seeking a fungus forest. The biome lists Brown and Red Mushroom patches, Glowstone, Magma, Gravel, Blackstone, Nether Gold, Nether Quartz, and both Ancient Debris placements. A listed feature is an attempt, not a vein or patch in every chunk. Use [Mushrooms](../blocks/Mushrooms.md) and [Ore resources](../blocks/OreResources.md) for collection rules. [Feature list][nether_wastes]

The mixed monster list includes both Piglins and Zombified Piglins; do not treat the names as interchangeable. Read [Piglin](../mobs/Piglin.md) before approaching or opening containers near one. The biome is eligible for both a [Nether Fortress](../structures/NetherFortress.md) and a [Bastion Remnant](../structures/BastionRemnant.md), subject to structure placement and terrain checks. [Structure eligibility](#structures-and-expeditions)

## Soul Sand Valley

Choose this biome for **Soul Sand and Soul Soil**, whose surface rules are distinct from ordinary Netherrack terrain. Its separate Basalt-pillar feature starts in empty space under a nonempty block and grows downward; the biome name does not establish a pillar at every location. [Surface rules][nether-surfaces] · [Pillar entry][soul_sand_valley] · [Pillar placement][placed-basalt_pillar] · [Pillar checks][pillar-checks]

This is the only biome in the bundled **Nether fossil** start tag. Follow [Bone Block](../blocks/BoneBlock.md#finding-fossils) for the checked fossil material and harvesting route. Skeletons and Ghasts are candidates here, so secure a retreat before stopping to collect. The biome also assigns spawn costs to Skeletons, Ghasts, Endermen, and Striders; these feed an additional local density check, not a promise about how many you will encounter. [Fossil tag][fossil-tag] · [Valley spawns and costs][soul_sand_valley] · [Active cost check][spawn-costs]

## Crimson Forest

Look for **Crimson Nylium and huge Crimson Fungi** if you want Crimson stems and cap materials. The huge-fungus configuration uses Crimson Nylium as its required base, Crimson Stem, Nether Wart Block, and possible Shroomlight decoration. Natural placement also checks its generated height. The cap's Nether Wart Block is not the plantable brewing crop; use [Nether Wart](../blocks/NetherWart.md) for that separate route. [Fungus placement][placed-crimson_fungi] · [Fungus configuration][config-crimson_fungus] · [Base and height checks][huge-checks]

The small-vegetation feature can select Crimson Roots, Crimson Fungus, or Warped Fungus. A small Warped Fungus does not mean you crossed into Warped Forest. The vegetation generator needs a Nylium base, empty target space, and a plant that can survive there. Weeping Vines are separately listed. [Biome features][crimson_forest] · [Vegetation placement][placed-crimson_forest_vegetation] · [Vegetation choices][config-crimson_forest_vegetation] · [Placement checks][vegetation-checks]

Hoglins and Piglins belong to this biome's monster list. Before collecting, make a protected stopping place and mark your return route. [Nether Fungi](../blocks/NetherFungi.md), [Nylium and vegetation](../blocks/NetherGroundAndVegetation.md), and [Vines](../blocks/Vines.md#weeping-and-twisting-vines) own growth and harvesting details. [Spawn list][crimson_forest]

## Warped Forest

This is the corresponding route for **Warped Nylium, Warped stems, and Warped Wart Blocks**. The huge-fungus configuration requires Warped Nylium and can decorate the cap with Shroomlights. Nether Sprouts and Twisting Vines have separate feature entries. Small vegetation can also choose Crimson Roots and Crimson Fungus, so plant color alone is not a reliable biome boundary. [Warped features][warped_forest] · [Huge placement][placed-warped_fungi] · [Huge configuration][config-warped_fungus] · [Small placement][placed-warped_forest_vegetation] · [Small choices][config-warped_forest_vegetation]

**Enderman is the sole ordinary biome monster candidate**, with an additional spawn-cost check. That does not make the forest harmless: terrain, Lava, and eligible structures still matter. In particular, a Fortress can supply its own monster list. Use [Enderman](../mobs/Enderman.md) for safe interaction and [Nether vegetation](../blocks/NetherGroundAndVegetation.md) before collecting a starter patch. [Biome spawns][warped_forest] · [Costs][spawn-costs] · [Structure overrides][structure-spawns] · [Fortress-specific lookup][fortress-spawns]

## Basalt Deltas

Explore here for **Basalt and Blackstone terrain**. The feature list adds Basalt/Blackstone blobs, two Basalt-column placements, and a delta generator configured with Lava contents and a Magma Block rim. Columns require suitable starting terrain; the delta checks neighboring blocks and can reject a site. Carry building blocks for deliberate crossings rather than assuming every dark surface is safe footing. [Biome features][basalt_deltas] · [Delta placement][placed-delta] · [Delta materials][config-delta] · [Delta checks][delta-checks] · [Small columns][placed-small_basalt_columns] · [Large columns][placed-large_basalt_columns] · [Column checks][column-checks]

The Gold/Quartz placement counts are **20/32**, versus **10/16** for the other four biomes. These are attempts, not recovered-ore totals: both configured ores still replace **Netherrack**, not arbitrary Basalt or Blackstone. Do not choose Deltas solely on the assumption of twice as much mineable ore. [Deltas Gold][placed-ore_gold_deltas] · [Deltas Quartz][placed-ore_quartz_deltas] · [Other Gold][placed-ore_gold_nether] · [Other Quartz][placed-ore_quartz_nether] · [Gold target][config-ore_nether_gold] · [Quartz target][config-ore_quartz]

Its monster list contains Ghasts and Magma Cubes. [Magma Cube](../mobs/MagmaCube.md) covers the latter's separate spawn and combat checks. **Bastions cannot start in this biome under the bundled tag**, but Fortresses can. See [Blackstone and Basalt](../blocks/BlackstoneAndBasalt.md) for harvesting and construction uses. [Deltas spawns][basalt_deltas] · [Bastion tag][bastion-tag] · [Fortress tag][fortress-tag]

## Structures and expeditions

| Goal | Eligible start biomes in these checked tags | Guide |
| --- | --- | --- |
| Nether Fortress | All five | [Fortress](../structures/NetherFortress.md) |
| Bastion Remnant | All except Basalt Deltas | [Bastion](../structures/BastionRemnant.md) |
| Nether fossil | Soul Sand Valley only | [Bone Block fossil route](../blocks/BoneBlock.md#finding-fossils) |

These are selected expedition routes, not every Nether structure. A start-biome filter does not confine all structure pieces to that biome or guarantee a nearby structure. Fortresses and Bastions share a placement set; fossils have their own set and terrain checks. [Fortress tag][fortress-tag] · [Nested Nether tag][nether-tag] · [Bastion tag][bastion-tag] · [Fossil tag][fossil-tag] · [Complex placement][complex-set] · [Fossil placement][fossil-set] · [Fossil definition][fossil-definition] · [Fossil terrain check][fossil-checks]

All five biome lists include Glowstone and both Ancient Debris placements. Debris targets the Nether base-stone tag and rejects air-exposed candidates in its configurations; it is not a surface-resource guarantee. The [lighting guide](../blocks/LuminousBlocks.md) and [ore guide](../blocks/OreResources.md) own collection conditions. [Large Debris][config-ore_ancient_debris_large] · [Small Debris][config-ore_ancient_debris_small] · [Target materials][nether-stone-tag] · [Large placement][placed-ore_ancient_debris_large] · [Small placement][placed-ore_debris_small] · [Scattered placement][scattered-checks] · [Target and exposure checks][ore-checks]

The [inventory item browser](../mechanics/InventoryBrowser.md) is a separate MattMC acquisition route. An item available there is not evidence that this biome naturally generates it.

## Sources and verification

Source-reviewed on **2026-10-02** at `79f20bccc697135bd56a472c64f59981dce47fe0`. Checked Normal-preset selection, all five biome resource definitions and loading paths, surface rules, listed spawn candidates, selected feature chains, and structure-biome eligibility. No in-game generation, resource-yield, spawn-rate, or expedition test was run. Data packs, presets, seeds, and previously generated terrain can change results.

The active world loader loads biome and feature registries from resources; biome decoration calls the placed feature, applies its filters, and dispatches its configured generator. A JSON entry alone does not bypass ground, height, space, or biome checks. [World loading][world-loader] · [Registry codecs][registry-loader] · [JSON loading][resource-load] · [Biome fields][biome-codec] · [Decoration caller][decoration] · [Placed dispatch][placed] · [Configured dispatch][configured] · [Feature registration][feature-registry] · [Biome filter][biome-filter] · [Layer placement][layer-filter]

Related: [Biomes](Biomes.md) · [Nether](../dimensions/Nether.md) · [End biomes](EndBiomes.md) · [Structures](../structures/Structures.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[nether-preset]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/nether.json
[nether-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L55-L72
[nether-source]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSource.java#L18-L77
[nether-surfaces]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json
[surface-caller]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L225-L262
[nether_wastes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[soul_sand_valley]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json
[crimson_forest]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json
[warped_forest]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[basalt_deltas]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/basalt_deltas.json
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[placed-basalt_pillar]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/basalt_pillar.json
[pillar-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/BasaltPillarFeature.java#L21-L44
[fossil-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/nether_fossil.json
[spawn-costs]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L490-L501
[placed-crimson_fungi]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/crimson_fungi.json
[config-crimson_fungus]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus.json
[huge-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L177
[placed-crimson_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/crimson_forest_vegetation.json
[config-crimson_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation.json
[vegetation-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L16-L49
[placed-warped_fungi]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/warped_fungi.json
[config-warped_fungus]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus.json
[placed-warped_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/warped_forest_vegetation.json
[config-warped_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation.json
[structure-spawns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[fortress-spawns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L336
[placed-delta]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/delta.json
[config-delta]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/delta.json
[delta-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/DeltaFeature.java#L26-L80
[placed-small_basalt_columns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/small_basalt_columns.json
[placed-large_basalt_columns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/large_basalt_columns.json
[column-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/BasaltColumnsFeature.java#L39-L96
[placed-ore_gold_deltas]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_deltas.json
[placed-ore_quartz_deltas]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/ore_quartz_deltas.json
[placed-ore_gold_nether]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_nether.json
[placed-ore_quartz_nether]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/ore_quartz_nether.json
[config-ore_nether_gold]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/ore_nether_gold.json
[config-ore_quartz]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/ore_quartz.json
[bastion-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/bastion_remnant.json
[fortress-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/nether_fortress.json
[nether-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/is_nether.json
[complex-set]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure_set/nether_complexes.json
[fossil-set]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure_set/nether_fossils.json
[fossil-definition]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure/nether_fossil.json
[fossil-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFossilStructure.java#L30-L65
[config-ore_ancient_debris_large]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/ore_ancient_debris_large.json
[config-ore_ancient_debris_small]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/ore_ancient_debris_small.json
[nether-stone-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/block/base_stone_nether.json
[placed-ore_ancient_debris_large]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/ore_ancient_debris_large.json
[placed-ore_debris_small]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/ore_debris_small.json
[scattered-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/ScatteredOreFeature.java#L18-L38
[ore-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java#L110-L130
[world-loader]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-loader]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[resource-load]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L326
[biome-codec]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/Biome.java#L38-L46
[decoration]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L380
[placed]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L39-L60
[configured]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L98-L136
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java#L23-L28
[layer-filter]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/placement/CountOnEveryLayerPlacement.java#L34-L88
