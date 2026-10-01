# Dry Midlands

Dry Midlands (`minecraft:dry_midlands`) is the sandy biome selected alongside [Primordial Plains](PrimordialPlains.md) in the bundled Normal preset's [Primordial Caves](../dimensions/PrimordialCaves.md). Its source includes cactus patches and ore-bearing geodes, but **some listed mining and mob routes are blocked by missing tags or spawn conditions**. Bring food, wood, and your own lighting.

## Terrain and decorations

The shared terrain rules give Dry Midlands sand-covered floors, sandstone beneath suitable floors, and sandstone ceiling surfaces. Stone and deepslate form the underlying terrain. Cave and canyon carvers are listed, so this is not a promise of level desert terrain.

The biome is configured with temperature 2, downfall 0, and no precipitation. Its decoration list includes cactus and cactus flowers, dead bushes, short and tall dry grass, dripstone patches, red-sand geodes, lava lakes, springs, and monster rooms. Its vegetation placements search cave layers rather than relying only on the world's top surface.

The red-sand geode definition combines red sandstone, red sand, granite, and dripstone blocks. Placement and replacement checks still decide whether any particular attempt succeeds.

Dry Midlands also attempts to place invisible light blocks with light level 7 above cave floors. This is local generated lighting, not skylight or a guarantee that the whole biome is safe.

## Mining: geodes versus ordinary veins

The biome references separate **coal, copper, diamond, gold, iron, lapis, and redstone geode** features. Their configurations directly supply deepslate ores; some also include coal, raw-metal, or redstone blocks. Diamond geodes include a magma-block layer, so approach a find carefully. These features are wired into the biome, although their frequency and successful generation have not been checked in-game.

**Do not use the ordinary ore list as a reliable mining promise.** The separate `ore/...` vein features for those minerals, plus the infested-stone feature, target the tags `minecraft:replaceable/stone_ores` and `minecraft:replaceable/deepslate_ores`. Neither tag is supplied in the reviewed active block-tag data. The runtime test requires exact tag membership, so those targets cannot match the bundled blocks without additional data supplying the tags.

The magma and gravel vein definitions similarly target the missing `minecraft:base_stone_dwarfhollow` tag. This restriction concerns those vein features, not every possible source of gravel, magma, or ore. The geode definitions use a different replacement path, and the red-sandstone vein targets sandstone directly.

For that reason, this page does not recommend an ore-height chart or advertise infested-block veins as a confirmed hazard. A data pack can change this situation.

## Mobs: listed candidates and restrictions

These are data entries, not confirmed encounters. Weights are relative choices within each category, not percentages; group ranges are configured values.

| Category | Mob | Weight | Group range |
| --- | --- | ---: | ---: |
| Creature | [Rabbit](../mobs/Rabbit.md) | 12 | 2–3 |
| Creature | [Camel](../mobs/Camel.md) | 1 | 1 |
| Ambient | [Bat](../mobs/Bat.md) | 10 | 8 |
| Monster | [Spider](../mobs/Spider.md) | 100 | 4 |
| Monster | [Zombie](../mobs/Zombie.md) | 19 | 4 |
| Monster | [Zombie villager](../mobs/ZombieVillager.md) | 1 | 1 |
| Monster | [Skeleton](../mobs/Skeleton.md) | 100 | 4 |
| Monster | [Creeper](../mobs/Creeper.md) | 100 | 4 |
| Monster | [Slime](../mobs/Slime.md) | 100 | 4 |
| Monster | [Enderman](../mobs/Enderman.md) | 10 | 1–4 |
| Monster | [Witch](../mobs/Witch.md) | 5 | 1 |
| Monster | [Husk](../mobs/Husk.md) | 80 | 4 |
| Underground water creature | [Glow squid](../mobs/GlowSquid.md) | 10 | 4–6 |

- **Rabbits and camels:** both require suitable ground and raw brightness above 8. Sand meets their ground requirements, but the dimension has no skylight and its level-7 light feature is insufficient by itself. Do not expect these animals simply because sand is present.
- **Husks:** ordinary natural spawning also requires visible sky. The dimension has no skylight, so that route is blocked despite the table's high husk weight. Spawner checks have a separate exception; this table does not establish a husk-spawner source.
- **Glow squid:** require water and raw brightness 0 at Y≤-39 with this generator's sea level of -6.
- Other entries still need their own placement, difficulty, light, and population checks. No custom MattMC mobs appear in this biome's table.

## Related pages

- [Primordial Caves: access and dimension rules](../dimensions/PrimordialCaves.md)
- [Primordial Plains](PrimordialPlains.md)
- [Biomes](Biomes.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game generation, ore-yield, or spawning test was run. Missing-tag and light/sky conclusions come from the bundled data and active runtime checks, not from a gameplay experiment.

- [Biome settings, features, and spawn table](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json)
- [Normal preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json), [terrain and surface rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/noise_settings/primordial_caves.json), and [dimension settings](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/primordial_caves.json)
- [Geode configurations](https://github.com/HungLo2020/MattMC/tree/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/configured_feature/geode) and [light-block configuration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/configured_feature/disk/light_7.json)
- [Vein configurations](https://github.com/HungLo2020/MattMC/tree/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/configured_feature/ore), [bundled block tags](https://github.com/HungLo2020/MattMC/tree/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block), and [exact tag-match test](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/TagMatchTest.java)
- [Rabbit check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/Rabbit.java#L423-L427), [camel check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/camel/Camel.java#L134-L138), and [shared brightness threshold](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/Animal.java#L112-L114)
- [Rabbit ground tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/rabbits_spawnable_on.json) and [camel ground tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/camels_spawnable_on.json)
- [Husk sky check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/Husk.java#L23-L28), [sky-visibility definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/BlockAndTintGetter.java#L22-L24), and [light engine](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/lighting/LevelLightEngine.java)
- [Glow squid checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/GlowSquid.java#L108-L119)
