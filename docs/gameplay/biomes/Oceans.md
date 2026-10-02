# Ocean biomes

MattMC's normal Overworld selects **nine ocean biome variants**. Choose a route based on the resources you want: Warm Ocean has the checked coral-generation route, ordinary/cold/lukewarm oceans have Kelp and Seagrass entries, and frozen variants include iceberg features. The tables below describe current generation and spawn candidates, not guaranteed finds at every location. [Normal preset][normal] · [Overworld parameter preset][parameter-preset] · [Preset-to-selector wiring][parameter-provider] · [Ocean selection][selection]

## Ocean families

Every ID in this table uses the `minecraft:` namespace. Deep variants participate in a separate continentalness range of the biome selector; the name does not promise a fixed seabed Y or diving depth. In the warmest selection slot, both ranges choose **Warm Ocean**, rather than a separate Deep Warm Ocean biome. [Selection mapping][selection]

| Family | Biome IDs | Selected feature entries | Selected aquatic spawn candidates |
| --- | --- | --- | --- |
| Ordinary | `ocean`, `deep_ocean` | Seagrass and Kelp | Cod, Squid, Dolphin |
| Cold | `cold_ocean`, `deep_cold_ocean` | Seagrass and Kelp | Cod, Salmon, Squid |
| Lukewarm | `lukewarm_ocean`, `deep_lukewarm_ocean` | Seagrass and Kelp | Cod, Pufferfish, Tropical Fish, Squid, Dolphin |
| Warm | `warm_ocean` | Coral formations, Seagrass, Sea Pickles | Pufferfish, Tropical Fish, Squid, Dolphin |
| Frozen | `frozen_ocean`, `deep_frozen_ocean` | Packed/Blue Ice icebergs and Blue Ice features | Salmon, Squid; Polar Bears in the creature list |

The nine exact biome definitions supply those lists: [Ocean][ocean], [Deep Ocean][deep-ocean], [Cold][cold], [Deep Cold][deep-cold], [Lukewarm][lukewarm], [Deep Lukewarm][deep-lukewarm], [Warm][warm], [Frozen][frozen], and [Deep Frozen][deep-frozen]. A creature's entry still needs its own placement, environmental, population, and difficulty checks. The table is not a complete list of cave or land-category creatures.

All nine include **Drowned** in their monster lists. Frozen variants also list Polar Bears; an icy route is not free of animal encounters. For an established companion interaction, see [Dolphin](../mobs/Dolphin.md), whose feeding and treasure guidance has separate conditions.

## Vegetation and resources

For **coral**, the Warm Ocean biome includes `warm_ocean_vegetation`. Its placed feature uses an ocean-floor heightmap and a biome filter, then selects among coral-tree, coral-claw, and coral-mushroom generators. Lukewarm in a biome's name does not automatically add that Warm Ocean coral feature. Water, available space, and each generator's checks still control actual placement. [Placed feature][coral-placement] · [Coral selector][coral] · [Coral placement checks][coral-checks]

Warm Ocean also includes Sea Pickles and Seagrass, but no Kelp feature in its checked biome list. The ordinary/cold/lukewarm family entries do contain Kelp features. Frozen lists omit those Kelp/Seagrass entries and instead include the iceberg features above. The configured iceberg materials are Packed Ice and Blue Ice. [Packed Ice configuration][packed-iceberg] · [Blue Ice configuration][blue-iceberg] These are generation-list differences, not rules forbidding players from bringing items into another biome. [Biome definitions](#ocean-families)

Use the plant guides for [Kelp](../blocks/Kelp.md), [Seagrass](../blocks/Seagrass.md), and [Sea Pickles](../blocks/SeaPickle.md); their harvesting and planting rules are separate from natural placement. Icebergs similarly do not guarantee that ordinary mining recovers their ice blocks.

## Structures and exploration

The bundled structure-biome tags connect these routes:

| Structure route | Eligible ocean families in the checked data |
| --- | --- |
| Shipwreck | All nine, through the ocean tag |
| Warm Ocean Ruins | Lukewarm, Deep Lukewarm, Warm |
| Cold Ocean Ruins | Ordinary, Deep Ocean, Cold, Deep Cold, Frozen, Deep Frozen |
| Ocean Monument | The four deep variants: Deep Ocean, Deep Cold, Deep Lukewarm, Deep Frozen |

These tags are eligibility filters, not a guarantee that a structure is generated or preserved at every eligible location. Structure-set placement and each structure's own terrain checks still apply. [Shipwreck tag][shipwreck-tag] · [Warm ruins tag][warm-ruins] · [Cold ruins tag][cold-ruins] · [Monument tag][monument-tag] · [Deep ocean tag][deep-tag] · [Ocean tag][ocean-tag] · [Shipwreck definition][shipwreck-definition] · [Warm ruins definition][warm-definition] · [Cold ruins definition][cold-definition]

Read [Shipwrecks](../structures/Shipwreck.md), [Ocean Ruins](../structures/OceanRuins.md), and [Buried Treasure](../structures/BuriedTreasure.md) before following loot or map routes. Ruins can also support archaeology; a treasure-map entry is not a promise that lookup always produces a filled map.

The Monument definition replaces the **monster spawn list inside its full structure bounding box** with **Guardian candidates**. Fish use separate water categories. The active spawn lookup applies this structure override before falling back to the biome list; an eligible spawn still needs the mob's other checks. [Monument definition][monument] · [Active override lookup][spawn-override] · [Natural spawning caller][spawn-caller]

Plan surface travel with the [Boat/transport guide](../mechanics/Transport.md), carry food, and leave a reliable return route to air before entering underwater structures. This page does not establish a tested diving or Monument-clearing strategy.

## Integrated ocean content is separate

The registered **Primordial Ocean** definition is not one of these nine Overworld choices. The bundled Normal preset selects **Primordial Plains and Dry Midlands** for [Primordial Caves](../dimensions/PrimordialCaves.md), rather than that ocean definition. A standalone custom biome file does not establish an accessible generated destination. [Normal preset][normal] · [Primordial Ocean definition][primordial]

Similarly, [Orca](../mobs/Orca.md), [Hammerhead Shark](../mobs/HammerheadShark.md), [Cachalot Whale](../mobs/CachalotWhale.md), and [Giant Squid](../mobs/GiantSquid.md) have separate integration limits. None is listed in these checked ocean biome spawn tables. Do not assume that choosing a warm or deep ocean completes their absent bundled spawning routes.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked Normal-preset biome-source wiring, the nine ocean selections and definitions, selected features, aquatic/Drowned/Polar Bear lists, nested structure-biome tags, the Monument override, and Primordial selection. No world-generation survey, spawn-rate, resource-collection, structure-search, or diving gameplay test was run. Seeds, data packs, world presets, and terrain checks affect results.

Related: [Biomes](Biomes.md) · [Structures](../structures/Structures.md) · [Transport](../mechanics/Transport.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[selection]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[coral-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/warm_ocean_vegetation.json
[coral]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/warm_ocean_vegetation.json
[shipwreck-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/shipwreck.json
[warm-ruins]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_ruin_warm.json
[cold-ruins]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_ruin_cold.json
[monument-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_monument.json
[deep-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/is_deep_ocean.json
[ocean-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/is_ocean.json
[monument]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/monument.json
[primordial]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[ocean]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[deep-ocean]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/deep_ocean.json
[cold]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/cold_ocean.json
[deep-cold]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/deep_cold_ocean.json
[lukewarm]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/lukewarm_ocean.json
[deep-lukewarm]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/deep_lukewarm_ocean.json
[warm]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json
[frozen]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/frozen_ocean.json
[deep-frozen]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/deep_frozen_ocean.json

[parameter-preset]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[parameter-provider]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[coral-checks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/CoralFeature.java#L24-L68
[packed-iceberg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/iceberg_packed.json
[blue-iceberg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/iceberg_blue.json
[shipwreck-definition]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/shipwreck.json
[warm-definition]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/ocean_ruin_warm.json
[cold-definition]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/ocean_ruin_cold.json
[spawn-override]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-caller]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L325
