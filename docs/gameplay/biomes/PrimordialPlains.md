# Primordial Plains

Primordial Plains (`minecraft:primordial_plains`) is one of the two biomes selected for [Primordial Caves](../dimensions/PrimordialCaves.md) by the bundled Normal world preset. Its surface rules favor grass and dirt, but it shares the dimension's ceiling and lack of skylight. **Bring food, wood, and lighting rather than relying on its plains-style feature and animal lists.**

## Terrain and resources

The shared generator starts with stone and applies grass-and-dirt rules to suitable Primordial Plains floors. It also has bedrock floor and roof rules and an unusually high deepslate transition, from Y=120 to Y=128. The biome lists cave, extra-underground-cave, and canyon carvers; the name does not imply a flat, open surface.

Its active feature list includes:

- Coal, iron, gold, redstone, diamond, lapis lazuli, and copper ore placements using the ordinary, non-slash-named ore definitions
- Amethyst geodes, monster rooms, lava lakes, water and lava springs, and underground stone/gravel/dirt deposits
- Plains-tree selection, flowers, grass and tall grass, bushes, mushrooms, pumpkins, sugar cane, firefly bushes, and glow lichen

These are generation attempts, not guaranteed finds. In particular, the tree placement uses the `OCEAN_FLOOR` heightmap and an oak-sapling survival check. Several other vegetation placements also use surface heightmaps. Those rules do not establish that wood or plants populate accessible cave floors beneath the roof.

The tree selector references ordinary oak, fancy oak, and fallen oak features. It does **not** reference [Pewen](../blocks/Pewen.md). Do not assume that an upstream prehistoric forest is generated here.

The biome data has temperature 0.8, downfall 0.4, and precipitation enabled. Those biome values do not add skylight or remove the dimension's ceiling.

## Mobs: listed candidates and restrictions

The following are the biome's spawn-table entries. Weights are relative choices within a category, **not percentages or observed spawn rates**; group ranges are configured values, not guaranteed numbers encountered.

| Category | Mob | Weight | Group range |
| --- | --- | ---: | ---: |
| Creature | [Sheep](../mobs/Sheep.md) | 12 | 4 |
| Creature | [Pig](../mobs/Pig.md) | 10 | 4 |
| Creature | [Chicken](../mobs/Chicken.md) | 10 | 4 |
| Creature | [Cow](../mobs/Cow.md) | 8 | 4 |
| Creature | [Horse](../mobs/Horse.md) | 5 | 2–6 |
| Creature | [Donkey](../mobs/Donkey.md) | 1 | 1–3 |
| Ambient | [Bat](../mobs/Bat.md) | 10 | 8 |
| Monster | [Spider](../mobs/Spider.md) | 100 | 4 |
| Monster | [Zombie](../mobs/Zombie.md) | 95 | 4 |
| Monster | [Zombie villager](../mobs/ZombieVillager.md) | 5 | 1 |
| Monster | [Skeleton](../mobs/Skeleton.md) | 100 | 4 |
| Monster | [Creeper](../mobs/Creeper.md) | 100 | 4 |
| Monster | [Slime](../mobs/Slime.md) | 100 | 4 |
| Monster | [Enderman](../mobs/Enderman.md) | 10 | 1–4 |
| Monster | [Witch](../mobs/Witch.md) | 5 | 1 |
| Underground water creature | [Glow squid](../mobs/GlowSquid.md) | 10 | 4–6 |

All six creature entries use the animal spawn check: ordinary natural spawning needs grass blocks and raw brightness **above 8**. With no skylight, an unlit grass floor does not meet that requirement. Lighting a suitable area addresses only that one condition; spawning still depends on the normal placement and population checks.

Glow squid require water, raw brightness 0, and a position at least 33 blocks below sea level. With the Normal preset's sea level of -6, that means **Y≤-39**. Other mobs retain their own spawn checks, so the table alone is not a farm-design guarantee.

There are no custom MattMC creature entries in this biome's table. It does not establish a natural spawn route for the prehistoric mobs described in other content pages.

## Related pages

- [Primordial Caves: access and dimension rules](../dimensions/PrimordialCaves.md)
- [Dry Midlands](DryMidlands.md)
- [Pewen](../blocks/Pewen.md)
- [Biomes](Biomes.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Feature references, surface rules, spawn entries, and the restrictions above were inspected in active sources. No in-game generation or spawning test was run; data packs and existing world settings can change these results.

- [Biome settings, features, and spawn table](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json)
- [Normal preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json), [terrain and surface rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/noise_settings/primordial_caves.json), and [dimension settings](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/primordial_caves.json)
- [Tree placement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/placed_feature/trees_plains.json) and [tree selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/configured_feature/trees_plains.json)
- [Spawn-predicate bindings](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java), [animal light check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114), and [spawnable-ground tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json)
- [Glow squid checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/GlowSquid.java#L108-L119)
