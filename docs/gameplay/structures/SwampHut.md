# Swamp Hut

A **Swamp Hut** is a small raised wooden building associated with a [Witch](../mobs/Witch.md) and a [Cat](../mobs/Cat.md). Visit for the encounter, a possible All Black Cat companion, or to investigate the hut's special spawning area. **There is no generated treasure chest or built-in redstone trap.** Bring a weapon, food and blocks for a safe approach; bring Raw Cod or Raw Salmon if the Cat is your goal. [Building contents][hut-building] · [Generated Witch][hut-witch] · [Generated Cat][hut-cat]

## Where to search

Search the exact **[Swamp](../biomes/JunglesAndSwamps.md#swamp)** biome in the normal Overworld. **Mangrove Swamp is absent from the hut's allowed tag.** The hut has a random-spread structure set with **32-chunk spacing and 8-chunk separation**, describing candidate placement rather than a guaranteed hut density or travel distance. [Definition][hut-definition] · [Allowed biome][hut-biomes] · [Set][hut-set] · [Spread calculation][positions] · [Normal world][normal] · [Parameters][parameters] · [Preset mapping][parameter-map] · [Swamp selection][swamp-map]

These rules describe the bundled **normal Overworld**. New starts need structure generation enabled, an eligible biome and a successful placement candidate. Data packs, custom presets and already-generated terrain can differ. The loaded definition and structure set are used by the active generation path. [Generation option][structure-option] · [Registry data][world-load] · [Resource and tag loading][resource-load] · [Set filtering][set-filter] · [Start creation][generate] · [Biome check][biome-filter]

Look for a Spruce-plank building on Oak-log supports. Its main piece is **7×9 blocks horizontally and 7 blocks tall**, with horizontal rotation and an average-ground-height adjustment. The generation entry uses the chunk-center surface height, then the piece builds its supports downward. Terrain and water around a hut can therefore change how you approach it. [Piece dimensions][hut-piece] · [Surface entry and piece factory][hut-wiring] · [Ground adjustment][average] · [Building and supports][hut-building]

With permission level 2, use `/locate structure minecraft:swamp_hut` in the Overworld. Follow the [dimension and arrival-height cautions](Structures.md#finding-a-structure), especially before accepting a suggested teleport over water or inside the building. [Locate permission][locate] · [Search dimension][locate-search]

## The building and first residents

Inside are a **Crafting Table, an empty Cauldron and a potted Red Mushroom**. The procedural piece does not place a loot chest, Brewing Stand, mob-spawner block or archaeology block. Its furnishings are not a hidden potion cache. See [Cauldrons](../blocks/Cauldrons.md) for what you can do with the basin. [Furnishing placement][hut-building]

Generation attempts **one Witch and one Cat**, positions them inside the building, finalizes them as structure spawns and marks them persistent. Saved flags record those attempts, so this generation routine is not a timer that replaces a killed or moved resident. A visited hut can already be empty. [Saved resident flags][hut-piece] · [Witch creation][hut-witch] · [Cat creation][hut-cat]

Persistence prevents ordinary distance despawning; it does not make a resident invulnerable. The Witch is registered as disallowed in Peaceful, and the Peaceful-removal check runs before ordinary persistence checks. [Witch registration][witch-type] · [Despawn ordering][peaceful]

The generated Cat is positioned before its coat is selected. The bundled All Black selector has higher priority inside a tagged hut, so this structure-generation route is a source of **All Black Cats**. Taming and bringing the Cat home use the normal [Cat care rules](../mobs/Cat.md#taming-and-care); the hut does not tame it for you. [Position before finalization][hut-cat] · [Coat selection][cat-finalize] · [Variant dispatch][variant] · [Priority selection][variant-priority] · [All Black rule][black] · [Hut tag][black-hut] · [Piece-location check][structure-check]

## Can more Witches or Cats appear?

Yes, but three distinct routes matter:

| Route | What it actually does |
| --- | --- |
| Structure generation | One recorded Witch attempt and one recorded Cat attempt when the piece is built |
| Natural spawning inside the recorded piece | Substitutes a Witch-only monster list and a Cat-only creature list, each with candidate group size 1 |
| Separate Cat replenishment routine | Can attempt a Cat at a valid hut position near a player when its nearby Cat check is empty |

[Generated residents][hut-witch] · [Generated Cat][hut-cat] · [Override lists][hut-definition] · [Active override lookup][override] · [Natural caller][spawn-caller] · [Cat replenishment][cat-spawner]

The override uses **the structure piece's recorded bounds**, not the whole Swamp biome. Building a similar wooden hut elsewhere does not create that structure record, and extending the roof does not enlarge its recorded spawning area. Natural attempts still need valid placement, collision space, player distance and the species' spawn rules. Killing the initial Witch does not disable that independent natural-spawn route. [Piece-bound lookup][override] · [Natural validation][spawn-checks]

Witches on the natural route retain their hostile darkness and non-Peaceful requirements. Cats on the natural override route use the ordinary animal check: **Grass Block beneath them and raw brightness above 8** in the bundled rules. A Cat entry in the override therefore does not mean Cats can naturally spawn on every Spruce-plank floor. The generated residents and separate Cat routine follow different creation paths. [Registered predicates][spawn-rules] · [Witch checks][monster-rules] · [Cat's animal checks][animal-rules] · [Ground tag][animal-ground] · [Natural finalization][spawn-finalize]

The separate Cat routine checks an area extending **16 blocks horizontally and 8 vertically** around its candidate and requires it to contain no Cats. Its periodic attempt is not a guaranteed replacement schedule. It also finalizes the Cat's coat **before moving it to the destination**, so do not promise every replacement Cat the generated resident's All Black coat. The [Cat finding and coat guide](../mobs/Cat.md#coat-variants) explains that ordering caveat. [Hut check and creation order][cat-spawner] · [Position-based variant choice][cat-finalize]

## Encounter and useful supplies

Secure the area before approaching the Cat or using the furnishings. The Witch can throw Slowness, Poison, Weakness or Harming splash potions according to its target and distance, and can drink defensive or healing potions. Treat the confined doorway and nearby water as approach constraints; leave yourself a clear retreat route. [Ranged attack choices][witch-attack] · [Drinking behavior][witch-drink]

The hut's renewable resource opportunity comes from **mob drops under suitable spawning conditions**, not a chest refill. With normal mob loot enabled, the bundled Witch table includes a separate **4–8 Redstone Dust** base pool plus weighted rolls for Glowstone Dust, Sugar, Spider Eyes, Glass Bottles, Gunpowder or Sticks; Looting can affect those counts. These are death-loot rules, not an output-per-hour prediction or a tested farm design. [Mob-loot gate][mob-loot] · [Loaded death-loot dispatch][death-loot] · [Witch loot table][witch-loot]

Ordinary listed item entries can also be requested through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. That is a separate acquisition route from a naturally generated hut, taming a resident, or collecting mob drops.

## Related pages

- [Jungles and Swamps](../biomes/JunglesAndSwamps.md)
- [Cat](../mobs/Cat.md) and [Witch](../mobs/Witch.md)
- [Brewing](../brewing/Brewing.md)
- [Jungle Temple](JungleTemple.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. The loaded world-generation definitions, biome eligibility, active procedural piece construction and placement, relevant block/entity interactions and loot assignments were inspected. No in-game search, generation, trap, looting, brushing, encounter or production-rate test was performed. Preparation advice is derived from those source rules.

The hut is constructed procedurally rather than loaded from a building template. Generated residents, natural spawn-list replacement and the separate Cat spawner were checked independently. No renewal rate or tested Witch-farm layout is claimed. [Registered type][types] · [Piece factory][hut-wiring] · [Active placement][active-place] · [Piece dispatch][piece-dispatch]

[hut-building]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java#L46-L95
[hut-witch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java#L97-L111
[hut-cat]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java#L115-L128
[hut-definition]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure/swamp_hut.json#L1-L29
[hut-biomes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/swamp_hut.json#L1-L5
[hut-set]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure_set/swamp_huts.json#L1-L14
[positions]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L67-L84
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L13
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[parameter-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[swamp-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L432-L452
[structure-option]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59
[world-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[resource-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[set-filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[generate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L494
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L548-L576
[hut-piece]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java#L25-L44
[hut-wiring]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutStructure.java#L18-L34
[average]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/ScatteredFeaturePiece.java#L41-L66
[locate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L68
[locate-search]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/LocateCommand.java#L95-L107
[witch-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1502-L1510
[peaceful]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L606-L625
[cat-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Cat.java#L363-L370
[variant]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/variant/VariantUtils.java#L36-L41
[variant-priority]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/variant/PriorityProvider.java#L19-L62
[black]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/cat_variant/all_black.json#L1-L21
[black-hut]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/structure/cats_spawn_as_black.json#L1-L5
[structure-check]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/variant/StructureCheck.java#L16-L18
[override]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[cat-spawner]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/CatSpawner.java#L20-L75
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L265
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L150-L164
[monster-rules]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L112
[animal-rules]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[animal-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json#L1-L5
[spawn-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L209
[witch-attack]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Witch.java#L208-L241
[witch-drink]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Witch.java#L119-L160
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[death-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[witch-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/witch.json#L1-L190
[types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L23-L43
[active-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
