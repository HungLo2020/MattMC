# Biomes

Biomes choose local terrain materials, decorations, and possible mob spawns. In MattMC, a biome's name does not guarantee that it contains all the creatures or resources associated with an upstream mod.

## Choose an Overworld landscape

- [Plains and meadows](PlainsAndMeadows.md): four grassland, flower and Cherry Grove destinations
- [Temperate forests and Mushroom Fields](TemperateForests.md): seven timber, fungal and Pale Garden destinations
- [Deserts, Badlands and Savannas](DesertsBadlandsAndSavannas.md): seven dry-land material and structure comparisons
- [Jungles and Swamps](JunglesAndSwamps.md): five tropical/wetland vegetation and animal comparisons
- [Taiga and snowy plains](TaigaAndSnowyBiomes.md): six Spruce, cold-travel and ice-feature choices
- [Mountains and windswept biomes](MountainsAndWindsweptBiomes.md): eight exposed, snowy and wooded highland choices
- [Cave biomes](CaveBiomes.md): Lush Caves, Dripstone Caves and Deep Dark
- [Rivers and shores](RiversAndShores.md): five river and coastal material/encounter comparisons

## Overworld ocean families

- [Ocean biomes](Oceans.md): all nine ordinary, deep, cold, lukewarm, warm, and frozen selections, with checked vegetation, aquatic candidates, and structure eligibility

## Nether biomes

- [Nether biomes](NetherBiomes.md): all five Normal-preset selections, fungus forests, soul ground, Basalt/Blackstone terrain, mob candidates and structure eligibility

## End biomes

- [End biomes](EndBiomes.md): all five selections, Highlands Chorus/gateways, Highlands/Midlands city eligibility and outer-island feature limits

## Primordial Caves biomes

The loaded bundled dimension source includes these three biome candidates in [Primordial Caves](../dimensions/PrimordialCaves.md#what-currently-generates). Its registry entry takes precedence over the literal Normal preset's two-biome definition:

- [Primordial Plains](PrimordialPlains.md): grass-and-dirt surface rules, ordinary plains vegetation and ore feature lists, and farm-animal spawn entries. Bring wood and food; the dimension's ceiling and darkness affect placement and spawning.
- [Dry Midlands](DryMidlands.md): sand and sandstone surface rules, cactus and dry-grass patches, and ore-bearing geode features. Its ordinary stone/deepslate ore targets were corrected in [#781](https://github.com/HungLo2020/MattMC/issues/781); separate magma/gravel target and rabbit/camel/husk spawn restrictions remain.

- [Primordial Ocean](SpecialBiomes.md#primordial-ocean) (`minecraft:primordial_ocean`): loaded-dimension selection, aquatic candidates and conditional custom features; separate from the nine Overworld ocean choices.

Use the dimension guide for portal access, return travel, and respawn rules. These biomes share its ceiling and lack of skylight; their names do not imply ordinary Overworld environments.

The server's missing-dimension fallback uses a Primordial Plains-only biome preset instead. That recovery path does not replace a successfully loaded bundled dimension. Existing terrain, data packs and resource loading can differ; no biome occurrence rate or portal landing is guaranteed.

## Special world setup

- [The Void](SpecialBiomes.md#the-void): the dedicated decorated flat preset, starting platform and empty ordinary spawn lists

## Alphabetical biome directory

The table lists all **68 bundled biome resource IDs** at the source checkpoint. Links lead to their exact section or an explicit family comparison. This is a source inventory with useful reviewed routes, not a claim that every detail or runtime outcome has been tested. The Void belongs to a special flat setup; the other guides distinguish their selection paths.

| Biome | Exact resource ID |
| --- | --- |
| [Badlands](DesertsBadlandsAndSavannas.md#badlands) | `minecraft:badlands` |
| [Bamboo Jungle](JunglesAndSwamps.md#bamboo_jungle) | `minecraft:bamboo_jungle` |
| [Basalt Deltas](NetherBiomes.md#basalt-deltas) | `minecraft:basalt_deltas` |
| [Beach](RiversAndShores.md#beach) | `minecraft:beach` |
| [Birch Forest](TemperateForests.md#birch-forest) | `minecraft:birch_forest` |
| [Cherry Grove](PlainsAndMeadows.md#cherry-grove) | `minecraft:cherry_grove` |
| [Cold Ocean](Oceans.md#ocean-families) | `minecraft:cold_ocean` |
| [Crimson Forest](NetherBiomes.md#crimson-forest) | `minecraft:crimson_forest` |
| [Dark Forest](TemperateForests.md#dark-forest) | `minecraft:dark_forest` |
| [Deep Cold Ocean](Oceans.md#ocean-families) | `minecraft:deep_cold_ocean` |
| [Deep Dark](CaveBiomes.md#deep-dark) | `minecraft:deep_dark` |
| [Deep Frozen Ocean](Oceans.md#ocean-families) | `minecraft:deep_frozen_ocean` |
| [Deep Lukewarm Ocean](Oceans.md#ocean-families) | `minecraft:deep_lukewarm_ocean` |
| [Deep Ocean](Oceans.md#ocean-families) | `minecraft:deep_ocean` |
| [Desert](DesertsBadlandsAndSavannas.md#desert) | `minecraft:desert` |
| [Dripstone Caves](CaveBiomes.md#dripstone-caves) | `minecraft:dripstone_caves` |
| [Dry Midlands†](DryMidlands.md) | `minecraft:dry_midlands` |
| [End Barrens](EndBiomes.md#end-barrens) | `minecraft:end_barrens` |
| [End Highlands](EndBiomes.md#end-highlands) | `minecraft:end_highlands` |
| [End Midlands](EndBiomes.md#end-midlands) | `minecraft:end_midlands` |
| [Eroded Badlands](DesertsBadlandsAndSavannas.md#eroded_badlands) | `minecraft:eroded_badlands` |
| [Flower Forest](TemperateForests.md#flower-forest) | `minecraft:flower_forest` |
| [Forest](TemperateForests.md#forest) | `minecraft:forest` |
| [Frozen Ocean](Oceans.md#ocean-families) | `minecraft:frozen_ocean` |
| [Frozen Peaks](MountainsAndWindsweptBiomes.md#frozen-peaks) | `minecraft:frozen_peaks` |
| [Frozen River](RiversAndShores.md#frozen-river) | `minecraft:frozen_river` |
| [Grove](MountainsAndWindsweptBiomes.md#grove) | `minecraft:grove` |
| [Ice Spikes](TaigaAndSnowyBiomes.md#ice-spikes) | `minecraft:ice_spikes` |
| [Jagged Peaks](MountainsAndWindsweptBiomes.md#jagged-peaks) | `minecraft:jagged_peaks` |
| [Jungle](JunglesAndSwamps.md#jungle) | `minecraft:jungle` |
| [Lukewarm Ocean](Oceans.md#ocean-families) | `minecraft:lukewarm_ocean` |
| [Lush Caves](CaveBiomes.md#lush-caves) | `minecraft:lush_caves` |
| [Mangrove Swamp](JunglesAndSwamps.md#mangrove_swamp) | `minecraft:mangrove_swamp` |
| [Meadow](PlainsAndMeadows.md#meadow) | `minecraft:meadow` |
| [Mushroom Fields](TemperateForests.md#mushroom-fields) | `minecraft:mushroom_fields` |
| [Nether Wastes](NetherBiomes.md#nether-wastes) | `minecraft:nether_wastes` |
| [Ocean](Oceans.md#ocean-families) | `minecraft:ocean` |
| [Old Growth Birch Forest](TemperateForests.md#old-growth-birch-forest) | `minecraft:old_growth_birch_forest` |
| [Old Growth Pine Taiga](TaigaAndSnowyBiomes.md#old-growth-pine-taiga) | `minecraft:old_growth_pine_taiga` |
| [Old Growth Spruce Taiga](TaigaAndSnowyBiomes.md#old-growth-spruce-taiga) | `minecraft:old_growth_spruce_taiga` |
| [Pale Garden](TemperateForests.md#pale-garden) | `minecraft:pale_garden` |
| [Plains](PlainsAndMeadows.md#plains) | `minecraft:plains` |
| [Primordial Ocean†](SpecialBiomes.md#primordial-ocean) | `minecraft:primordial_ocean` |
| [Primordial Plains†](PrimordialPlains.md) | `minecraft:primordial_plains` |
| [River](RiversAndShores.md#river) | `minecraft:river` |
| [Savanna](DesertsBadlandsAndSavannas.md#savanna) | `minecraft:savanna` |
| [Savanna Plateau](DesertsBadlandsAndSavannas.md#savanna_plateau) | `minecraft:savanna_plateau` |
| [Small End Islands](EndBiomes.md#small-end-islands) | `minecraft:small_end_islands` |
| [Snowy Beach](RiversAndShores.md#snowy-beach) | `minecraft:snowy_beach` |
| [Snowy Plains](TaigaAndSnowyBiomes.md#snowy-plains) | `minecraft:snowy_plains` |
| [Snowy Slopes](MountainsAndWindsweptBiomes.md#snowy-slopes) | `minecraft:snowy_slopes` |
| [Snowy Taiga](TaigaAndSnowyBiomes.md#snowy-taiga) | `minecraft:snowy_taiga` |
| [Soul Sand Valley](NetherBiomes.md#soul-sand-valley) | `minecraft:soul_sand_valley` |
| [Sparse Jungle](JunglesAndSwamps.md#sparse_jungle) | `minecraft:sparse_jungle` |
| [Stony Peaks](MountainsAndWindsweptBiomes.md#stony-peaks) | `minecraft:stony_peaks` |
| [Stony Shore](RiversAndShores.md#stony-shore) | `minecraft:stony_shore` |
| [Sunflower Plains](PlainsAndMeadows.md#sunflower-plains) | `minecraft:sunflower_plains` |
| [Swamp](JunglesAndSwamps.md#swamp) | `minecraft:swamp` |
| [Taiga](TaigaAndSnowyBiomes.md#taiga) | `minecraft:taiga` |
| [The End](EndBiomes.md#the-end) | `minecraft:the_end` |
| [The Void](SpecialBiomes.md#the-void) | `minecraft:the_void` |
| [Warm Ocean](Oceans.md#ocean-families) | `minecraft:warm_ocean` |
| [Warped Forest](NetherBiomes.md#warped-forest) | `minecraft:warped_forest` |
| [Windswept Forest](MountainsAndWindsweptBiomes.md#windswept-forest) | `minecraft:windswept_forest` |
| [Windswept Gravelly Hills](MountainsAndWindsweptBiomes.md#windswept-gravelly-hills) | `minecraft:windswept_gravelly_hills` |
| [Windswept Hills](MountainsAndWindsweptBiomes.md#windswept-hills) | `minecraft:windswept_hills` |
| [Windswept Savanna](DesertsBadlandsAndSavannas.md#windswept_savanna) | `minecraft:windswept_savanna` |
| [Wooded Badlands](DesertsBadlandsAndSavannas.md#wooded_badlands) | `minecraft:wooded_badlands` |

† The three custom biome labels use the existing wiki names because their IDs have no bundled English biome translation; exact IDs are shown separately.

## Reading these guides

A **feature entry** tells the generator what to attempt. Height, supporting blocks, space, and placement filters can prevent that attempt from placing anything. A **spawn entry** is likewise a candidate, not a promise that a mob appears: light, ground, difficulty, mob limits, and each creature's own checks still apply.

The individual pages distinguish those data entries from verified constraints. No in-game generation or spawn-rate test was run for this source review. The family guides now give each bundled biome ID a reviewed comparison or section. Selected resources, structures and spawning constraints are covered; exhaustive feature details and live world validation remain outside this inventory.

## Related pages

- [Time, weather, and sleep](../mechanics/TimeWeatherAndSleep.md#rain-thunder-and-the-place-you-stand)
- [Primordial Caves](../dimensions/PrimordialCaves.md)
- [Content guide](../ContentGuide.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Index updated on 2026-10-02 at `aaeea0b263d995334061e562cd5b71a853540f7f`. The alphabetical inventory was checked against all 68 files in the [bundled biome resource directory](https://github.com/HungLo2020/MattMC/tree/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/resources/data/minecraft/worldgen/biome); 65 display names match the bundled English translations; three custom names are disclosed fallbacks. Each family guide records its own immutable sources and review limits. The historical source links below remain unchanged evidence for the general callers, and the current [Primordial selection owner](../dimensions/PrimordialCaves.md#what-currently-generates) explains loaded-dimension precedence.

- [Normal world preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json)
- [Server dimension fallback](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L563) and [fallback biome preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L81-L93)
- [Biome-feature generation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L398) and [natural spawning checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/NaturalSpawner.java)

The [Nether](NetherBiomes.md) and [End](EndBiomes.md) family guides record their own 2026-10-02 review at `79f20bccc697135bd56a472c64f59981dce47fe0`.
