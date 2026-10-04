# Cave biomes

Choose **Lush Caves** for cave plants and clay, **Dripstone Caves** for dripstone and its larger configured Copper Ore feature, and **Deep Dark** for sculk materials with a serious Shrieker/Warden hazard. All three are selected by the bundled Normal preset's Overworld biome source. Cave biome selection varies underground; the surface biome above you does not by itself identify the cave biome below. [Normal preset][normal] · [Parameter preset][overworld-preset] · [Selector dispatch][preset-provider] · [Underground selection][cave-selection]

## Lush Caves {#lush-caves}

**`minecraft:lush_caves`** supplies a varied underground garden. Its active features include floor and ceiling moss patches, Cave Vines, clay patches and pools with dripleaves, rooted Azalea systems, Spore Blossoms, Glow Lichen, and an ordinary-Vine cave feature. Use [Moss](../blocks/MossAndPaleMoss.md), [Vines and Glow Berries](../blocks/Vines.md#cave-vines-and-glow-berries), [Dripleaves](../blocks/Dripleaves.md), and [Hanging Roots and Spore Blossom](../blocks/HangingRootsAndSporeBlossom.md) for the different recovery and regrowth rules. [Biome entries][lush] · [Moss ground][moss] · [Moss vegetation][moss-plants] · [Clay selection][clay-selector] · [Rooted tree configuration][azalea]

The floor moss placement samples underground positions and searches downward for a solid surface; the ceiling variant searches upward. The clay selector chooses an ordinary vegetation patch or a waterlogged one. Both supply Clay and invoke the dripleaf feature. Successful placement still needs replaceable ground, suitable support and space. These are useful places to search, not a promise that every cave room contains every plant. [Floor placement][moss-placement] · [Ceiling placement][ceiling-placement] · [Clay patch][clay] · [Clay pool][clay-pool] · [Patch implementation][vegetation-patch]

The rooted Azalea feature attempts a tree, Rooted Dirt and Hanging Roots through its own root-system placement. A generated Azalea/root connection is a clue worth investigating, but the configured feature and its upward search do not guarantee an accessible passage directly beneath every Azalea you encounter. See [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md#azalea-and-flowering-azalea) for the separate planted-shrub tree route. [Root-system placement][azalea-placement] · [Root-system configuration][azalea] · [Placement checks][root-system]

Lush Caves lists **Axolotls**, **Tropical Fish**, **Glow Squid**, Bats and ordinary hostile candidates. The Axolotl entry uses weight 10 and configured groups of 4–6; it still needs the in-water placement and **Clay immediately below** the candidate position. The checked Axolotl predicate adds no darkness condition. Tropical Fish require Water above and water-tagged fluid below; Lush Caves is specifically tagged to bypass their ordinary near-sea-level height range. Neither rule guarantees a populated pool. [Spawn table][lush] · [Spawn bindings][spawn-bindings] · [Axolotl predicate][axolotl] · [Clay support tag][axolotl-ground] · [Fish predicate][tropical] · [Height exception][tropical-tag]

Bring a way back and ordinary cave supplies. Green vegetation does not remove the biome's Spider, Zombie, Skeleton, Creeper, Slime, Enderman or Witch candidates. Weights are relative choices within a spawn category, not encounter percentages. [Biome table][lush] · [Natural spawning][spawner]

## Dripstone Caves {#dripstone-caves}

**`minecraft:dripstone_caves`** actively attempts large dripstone formations, dripstone clusters and small Pointed Dripstone features. These provide both full Dripstone Blocks and pointed segments when suitable terrain passes placement checks. Read [Dripstone](../blocks/Dripstone.md) before harvesting overhead formations: breaking supports can release falling segments, and collection, growth and cauldron dripping have separate rules. [Biome entries][dripstone] · [Large placement][large-placement] · [Cluster placement][cluster-placement] · [Pointed placement][pointed-placement] · [Large generator][large-generator] · [Cluster generator][cluster-generator]

For Copper, this biome selects **`ore_copper_large`**, while the other two cave biomes use ordinary **`ore_copper`**. The large configured feature has size **20**, compared with **10** for the ordinary small configuration; both placements use 16 attempts and a trapezoid height range from **Y=-16 through Y=112**. Those settings describe generation attempts and geometry, not a guaranteed vein size, blocks per chunk, or mining yield. Host blocks and actual terrain still matter. Use [Ores and Ancient Debris](../blocks/OreResources.md) for tool tiers, Fortune, Silk Touch and processing. [Large placement][copper-large-placement] · [Ordinary placement][copper-placement] · [Large configuration][copper-large] · [Small configuration][copper-small] · [Ore implementation][ore-generator]

The spawn table has Bats, Glow Squid, the ordinary hostile candidates and an additional **Drowned** entry, weight 95 with groups of 4. It does not list Axolotls or Tropical Fish. Drowned are still water- and darkness-dependent; Dripstone Caves lacks the river-specific spawn tag, so ordinary natural attempts also require a position below sea level minus five and the non-river random gate. A dry spike chamber is not a Drowned spawn location. [Biome table][dripstone] · [Drowned checks][drowned] · [River-only tag][drowned-tag]

## Deep Dark {#deep-dark}

**`minecraft:deep_dark`** is a sculk destination, not a safe empty cave. Its feature list contains Sculk Vein and Deep Dark sculk patches. The patch invokes the world-generation sculk spreader, which can create Sensors and summoning-capable Shriekers, and can place a Catalyst on suitable support. The separate direct “extra rare growth” count is zero in this patch configuration; that does not disable the spreader's Shrieker route. [Biome entries][deep-dark] · [Patch placement][sculk-placement] · [Patch configuration][sculk-config] · [Patch implementation][sculk-generator] · [Growth choice and summoning state][sculk-growth]

Use the [Sculk family](../blocks/Sculk.md), [Sensors](../blocks/SculkSensors.md), [Shriekers](../blocks/SculkShrieker.md) and [Warden](../mobs/Warden.md) guides for collection and encounter rules. Plan a retreat before disturbing Sensors or Shriekers. A Shrieker's ability to summon, warning progression, difficulty and other checks are separate from ordinary biome spawning; not every shriek produces a Warden.

All ordinary spawn categories in this biome's JSON are empty. **That is not a universal no-mob rule:** the biome still lists monster-room features, and Warden summoning is a separate active route. Mobs arriving from elsewhere or created by another system are not disproved by an empty table. [Empty spawn tables and features][deep-dark] · [Shrieker response][shriek-response]

Deep Dark is the sole member of the bundled Ancient City eligibility tag. The Ancient City definition reads that tag and appears in the `ancient_cities` structure set. This establishes a structure-generation candidate, **not a city in every Deep Dark patch** or a guarantee of a particular room, loot item or Warden. Structure placement, start validity and generation settings still apply. [Eligibility tag][city-tag] · [Structure definition][city] · [Structure set][city-set] · [Generation caller][structure-call]

## Mining and finding the right cave

For Dripstone Copper and the shared underground ore distributions, see [Finding ores](../mechanics/FindingOres.md#overworld-placements).

All three biome definitions include ordinary Coal, Iron, Gold, Redstone, Diamond, Lapis and Copper placements, Amethyst geodes, and monster-room entries. Lush Caves additionally lists an Ore Clay placement. Underground terrain can contain materials from these systems alongside the distinctive vegetation or sculk. Consult [Ore resources](../blocks/OreResources.md) and [Clay and Bricks](../blocks/ClayAndBricks.md) for harvesting rather than assuming a biome changes the tool requirement. [Lush data][lush] · [Dripstone data][dripstone] · [Deep Dark data][deep-dark]

The selector's cave depth values are **climate/noise coordinates, not literal Y levels**. Lush Caves uses a high-humidity selection range, Dripstone Caves a high-continentalness range, and Deep Dark a separate bottom-biome entry with erosion restrictions. These are search tendencies in the generator, not a fixed depth or distance recipe. [Cave selection][cave-selection] · [Underground/bottom parameter construction][cave-depth]

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. The bundled JSON resources are read through the worldgen registry loader; biome feature entries resolve to placed features, then configured feature implementations during decoration. This review traced that path rather than treating data-generator helper methods as the live biome definition. [World loading][load] · [Registry codecs][registry] · [Resource loading][json-load] · [Biome feature codec][biome-codec] · [Decoration caller][decorate] · [Placed dispatch][placed-dispatch] · [Configured dispatch][configured-dispatch]

No in-game generation, spawn-rate, ore-yield or Warden encounter test was run. Existing chunks and overridden world/data-pack definitions can differ.

Related: [Biomes](Biomes.md) · [Rivers and shores](RiversAndShores.md) · [Mining](../mechanics/Mining.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[overworld-preset]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[preset-provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[cave-selection]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L815-L831
[lush]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[moss]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch.json
[moss-plants]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/moss_vegetation.json
[clay-selector]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/lush_caves_clay.json
[azalea]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/rooted_azalea_tree.json
[moss-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/lush_caves_vegetation.json
[ceiling-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/lush_caves_ceiling_vegetation.json
[clay]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/clay_with_dripleaves.json
[clay-pool]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/clay_pool_with_dripleaves.json
[vegetation-patch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/VegetationPatchFeature.java
[azalea-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/rooted_azalea_tree.json
[root-system]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java
[spawn-bindings]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[axolotl]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/axolotl/Axolotl.java#L539-L546
[axolotl-ground]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/axolotls_spawnable_on.json
[tropical]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java#L263-L271
[tropical-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/allows_tropical_fish_spawns_at_any_height.json
[spawner]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[dripstone]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/dripstone_caves.json
[large-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/large_dripstone.json
[cluster-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/dripstone_cluster.json
[pointed-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/pointed_dripstone.json
[large-generator]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/LargeDripstoneFeature.java
[cluster-generator]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java
[copper-large-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_copper_large.json
[copper-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_copper.json
[copper-large]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_copper_large.json
[copper-small]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_copper_small.json
[ore-generator]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java
[drowned]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/monster/Drowned.java#L109-L130
[drowned-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/more_frequent_drowned_spawns.json
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json
[sculk-placement]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/sculk_patch_deep_dark.json
[sculk-config]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/sculk_patch_deep_dark.json
[sculk-generator]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java#L21-L76
[sculk-growth]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/block/SculkBlock.java#L27-L91
[shriek-response]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L100-L167
[city-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ancient_city.json
[city]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/ancient_city.json
[city-set]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/ancient_cities.json
[structure-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L554-L575
[cave-depth]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L922-L945
[load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L35-L48
[registry]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L111
[json-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[biome-codec]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L29-L41
[decorate]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[placed-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L34-L60
[configured-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
