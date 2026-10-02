# Biomes

Biomes choose local terrain materials, decorations, and possible mob spawns. In MattMC, a biome's name does not guarantee that it contains all the creatures or resources associated with an upstream mod.

## Overworld ocean families

- [Ocean biomes](Oceans.md): all nine ordinary, deep, cold, lukewarm, warm, and frozen selections, with checked vegetation, aquatic candidates, and structure eligibility

## Nether biomes

- [Nether biomes](NetherBiomes.md): all five Normal-preset selections, fungus forests, soul ground, Basalt/Blackstone terrain, mob candidates and structure eligibility

## End biomes

- [End biomes](EndBiomes.md): all five selections, Highlands Chorus/gateways, Highlands/Midlands city eligibility and outer-island feature limits

## Primordial Caves biomes

The bundled **Normal** world preset selects these two biomes inside [Primordial Caves](../dimensions/PrimordialCaves.md):

- [Primordial Plains](PrimordialPlains.md): grass-and-dirt surface rules, ordinary plains vegetation and ore feature lists, and farm-animal spawn entries. Bring wood and food; the dimension's ceiling and darkness affect placement and spawning.
- [Dry Midlands](DryMidlands.md): sand and sandstone surface rules, cactus and dry-grass patches, and ore-bearing geode features. Its ordinary stone/deepslate ore targets were corrected in [#781](https://github.com/HungLo2020/MattMC/issues/781); separate magma/gravel target and rabbit/camel/husk spawn restrictions remain.

Use the dimension guide for portal access, return travel, and respawn rules. Both biomes share its ceiling and lack of skylight; they are not ordinary open-air Overworld plains or desert.

The server's missing-dimension fallback uses a Primordial Plains-only biome preset instead. Existing worlds and data packs can differ from the Normal preset, so Dry Midlands is not guaranteed in every Primordial Caves world setup.

## Reading these guides

A **feature entry** tells the generator what to attempt. Height, supporting blocks, space, and placement filters can prevent that attempt from placing anything. A **spawn entry** is likewise a candidate, not a promise that a mob appears: light, ground, difficulty, mob limits, and each creature's own checks still apply.

The individual pages distinguish those data entries from verified constraints. No in-game generation or spawn-rate test was run for this source review. This index covers the ocean, Nether and End families and the two custom biomes above; other Overworld biome families remain to be expanded.

## Related pages

- [Primordial Caves](../dimensions/PrimordialCaves.md)
- [Content guide](../ContentGuide.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Index updated on 2026-10-02 after integrating master `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. The [ocean guide](Oceans.md) records its own verified sources; the unchanged custom-dimension evidence below remains pinned to its 2026-10-01 review at `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.

- [Normal world preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json)
- [Server dimension fallback](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L563) and [fallback biome preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L81-L93)
- [Biome-feature generation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L398) and [natural spawning checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/NaturalSpawner.java)

The [Nether](NetherBiomes.md) and [End](EndBiomes.md) family guides record their own 2026-10-02 review at `79f20bccc697135bd56a472c64f59981dce47fe0`.
