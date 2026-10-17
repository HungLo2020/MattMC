# Pillager Outpost

A Pillager Outpost (`minecraft:pillager_outpost`) is a watchtower expedition for chest loot, possible captive residents and encounters with [Pillagers](../mobs/Pillager.md). A captain can provide an [Ominous Bottle](../items/OminousBottle.md), but visiting the tower does not itself start a [raid](../mechanics/Raid.md).

## Where to look

In the bundled **normal Overworld**, outposts are eligible to start in Desert, Plains, Savanna, Snowy Plains, Taiga, Grove, Meadow, Frozen Peaks, Jagged Peaks, Stony Peaks, Snowy Slopes and Cherry Grove. These names come from the structure's biome tag and its nested mountain tag, both represented by the normal Overworld biome source. Eligibility does not promise a tower in every patch. [Structure definition][outpost] · [Allowed biomes][outpost-biomes] · [Mountain expansion][mountain-biomes] · [Normal world][normal-preset] · [Biome parameters][overworld-parameters] · [Preset mapping][overworld-map] · [Biome choices][overworld-biomes]

New starts require structure generation enabled. The loaded structure set uses 32-chunk spacing, 8-chunk separation, a 0.2 placement-frequency setting and a village-set exclusion check of 10 chunks. These are candidate-placement controls, not a guaranteed travel distance or a claim that every tower is a fixed number of blocks from visible village buildings. [Generation option][structure-option] · [Registry loading][worldgen-load] · [Biome/set filtering][structure-filter] · [Placement set][outpost-set] · [Generation caller][structure-create]

Look for the tower above the surrounding terrain. With permission level 2, `/locate structure minecraft:pillager_outpost` is another search route; read the [dimension and arrival-height cautions](Structures.md#finding-a-structure). This guide does not certify outpost generation in every custom preset or in old terrain generated under different data.

## Layout and possible residents

The start pool places a base plate whose jigsaw connectors lead to the tower pool and feature plates; the feature plates then select optional surroundings. The tower pool combines a watchtower template and an overgrown overlay. The side-feature pool includes tents, logs, targets, cages and an empty choice, so no individual side feature is guaranteed. [Base pool][base-pool] · [Base connectors][base-nbt] · [Feature-plate pool][plate-pool] · [Feature connectors][plate-nbt] · [Tower pool][tower-pool] · [Optional features][feature-pool]

The checked cage templates contain:

- **Golem cage:** one Iron Golem in `feature_cage1`
- **Empty cage:** no entity in `feature_cage2`
- **Allay cage:** two Allays in `feature_cage_with_allays`

[Iron Golem template][golem-nbt] · [Empty cage template][empty-nbt] · [Allay template][allay-nbt]

These residents are stored in the actual NBT templates. The jigsaw placement path loads those templates, includes their entities and adds them to the world; this is more than an unused pool name. It still does not guarantee a particular cage will be selected or survive unchanged in an explored outpost. Secure the surrounding Pillagers before opening a cage, especially if your goal is bringing a resident out alive. [Jigsaw caller][jigsaw-caller] · [Resource loading][nbt-load] · [Pool placement settings][pool-place] · [Resident placement][entity-place]

## Chest rewards

The watchtower templates assign the upper chest the `minecraft:chests/pillager_outpost` loot table. The table contains several independent pools rather than one choice from a single combined list. [Tower chest][watchtower-nbt] · [Overlay chest][overgrown-nbt] · [Chest table][outpost-loot]

| Pool | Bundled result |
| --- | --- |
| Crossbow | 0–1 Crossbow |
| Food/crops | 2–3 weighted rolls among 3–5 Wheat, 2–5 Potatoes or 3–5 Carrots |
| Logs | 1–3 rolls, each giving 2–3 Dark Oak Logs |
| Supplies | 2–3 weighted rolls among a Bottle o' Enchanting, 1–6 String, 2–7 Arrows, 1–3 Tripwire Hooks, 1–3 Iron Ingots or an enchanted book |
| Goat Horn | 0–1 horn from the regular-horn tag |
| Sentry template | One choice: nothing at weight 3, or **two Sentry Armor Trim Smithing Templates** at weight 1 |

[Rolls, amounts and weights][outpost-loot]

The final pool gives a **25% chance of two Sentry templates per chest using this unchanged table**. It is not a guarantee after four chests. Regular horns are Ponder, Sing, Seek and Feel. If the optional trade-rebalance pack is enabled, its replacement outpost table adds a separate weighted Quick Charge book pool; use [Trading's rebalance explanation](../trading/Trading.md#optional-trade-rebalance) for that option's scope. [Regular horns][horn-pool] · [Optional replacement table][outpost-rebalance]

An Ominous Bottle is **not a reward in this outpost chest table**. The verified local bottle route is a qualifying captain's death, explained on [Pillager](../mobs/Pillager.md#captains-and-ominous-bottles). Save the bottle until you intend to begin an omen encounter.

## Pillager spawning and a safer approach

The outpost replaces monster selection inside its **full recorded structure bounding box** with Pillagers. Natural-spawn checks still apply: sufficient room and valid ground, player-distance and mob-cap conditions, non-Peaceful difficulty and block light **8 or less** at the attempted position. This Pillager rule does not require darkness from the sky, so daylight alone does not make the outpost safe. [Full-box override][outpost] · [Override lookup][structure-spawns] · [Natural selection][natural-selection] · [Placement registration][pillager-placement] · [Pillager light test][patrol-captain] · [Any-light difficulty rule][light-rule] · [Other placement checks][natural-checks]

Treat the tower as an active hostile area after clearing the first group. The spawning override is tied to the recorded structure, not to a count of living guards or to possession of the chest loot. Placing outpost-looking blocks elsewhere does not create that recorded structure. This source review does not establish a farm design, exact spawn rate or a tested lighting layout that makes the whole structure safe. [Structure lookup][structure-spawns] · [Natural spawn caller][natural-finalize]

- Approach behind solid cover and identify a return route before ascending; Pillagers fire when they can see their target
- Secure the stairs and upper floor before opening the chest, and keep watching the surrounding ground for newly spawned enemies
- If collecting a bottle, distinguish the banner-wearing captain from ordinary guards and avoid bringing Bad Omen into your own occupied settlement unintentionally

These are precautions based on [Pillager targeting and crossbow behavior](../mobs/Pillager.md#behavior), not an in-game-tested assault route.

## Related pages

- [Raid](../mechanics/Raid.md) · [Ominous Bottle](../items/OminousBottle.md)
- [Pillager](../mobs/Pillager.md) · [Iron Golem](../mobs/IronGolem.md) · [Allay](../mobs/Allay.md)
- [Combat](../mechanics/Combat.md) · [Structures](Structures.md)

## Sources and verification

Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. Followed loaded normal-preset biomes, structure-set selection, jigsaw placement, parsed bundled NBT residents/chest metadata, spawn overrides and loot. No world generation, locate, cage rescue, combat, chest-opening or spawn-control test was performed. Template contents and loot tables describe the bundled sources, not guaranteed outcomes for every world or data pack.

[outpost]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json
[outpost-biomes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json
[mountain-biomes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/is_mountain.json
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[overworld-parameters]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[overworld-map]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[structure-option]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L58
[worldgen-load]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[structure-filter]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L45-L64
[outpost-set]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure_set/pillager_outposts.json
[structure-create]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L491
[base-pool]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/base_plates.json
[base-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/base_plate.nbt
[plate-pool]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/feature_plates.json
[plate-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/feature_plate.nbt
[tower-pool]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/towers.json
[feature-pool]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/features.json
[golem-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/feature_cage1.nbt
[empty-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/feature_cage2.nbt
[allay-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/feature_cage_with_allays.nbt
[jigsaw-caller]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L134-L152
[nbt-load]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L123-L129
[pool-place]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L138-L181
[entity-place]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L513
[watchtower-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/watchtower.nbt
[overgrown-nbt]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/structure/pillager_outpost/watchtower_overgrown.nbt
[outpost-loot]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/loot_table/chests/pillager_outpost.json
[horn-pool]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/instrument/regular_goat_horns.json
[outpost-rebalance]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/pillager_outpost.json
[structure-spawns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[natural-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[pillager-placement]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L138
[patrol-captain]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/PatrollingMonster.java#L60-L99
[light-rule]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Monster.java#L115-L132
[natural-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L287
[natural-finalize]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L212
