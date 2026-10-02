# Drowned

A **Drowned** is an aquatic Zombie variant that can fight in water and on land. Some carry **Tridents**, **Fishing Rods**, or a [Nautilus Shell](../items/NautilusShell.md). Converting an ordinary Zombie creates a Drowned, but does not roll those natural equipment items. [Drowned implementation][drowned] · [Active registration][entities] [attributes] · [Conversion route][zombie] [mob]

<span id="obtaining"></span>

## Finding Drowned

Natural spawn entries occur in all nine ordinary/deep/temperature [Ocean biomes](../biomes/Oceans.md), **River, Frozen River, and Dripstone Caves**. Ocean and river entries configure individual Drowned; Dripstone Caves configures groups of four. Spawn-list weights, population limits, and placement checks still determine whether an attempt succeeds. [Ocean tables][ocean] [deep_ocean][] [cold_ocean][] [deep_cold_ocean][] [lukewarm_ocean][] [deep_lukewarm_ocean][] [warm_ocean][] [frozen_ocean][] [deep_frozen_ocean][] · [River][river] · [Frozen River][frozen_river] · [Dripstone Caves][dripstone_caves]

Ordinary natural spawning requires **water at the candidate and below it**, a non-Peaceful difficulty, the monster darkness test, valid water placement, and unobstructed space. The bundled Overworld's block-light limit is 0. On top of that:

- **River and Frozen River:** the special biome tag gives a **1-in-15 additional random check**, without the extra depth requirement below
- **Other biomes:** a **1-in-40 additional check** and **Y strictly below sea level minus 5**

These are checks within an eligible spawn attempt, not percentages of all water blocks or a guaranteed rate. [Spawn predicate and depth][drowned] · [Water placement registration][placements] · [More-frequent tag][more-drowned] · [River tag][rivers] · [Darkness][monster] · [Overworld light setting][overworld] · [Spawn dispatch][natural]

The separate **Primordial Ocean** definition also contains Drowned candidates. The loaded bundled [Primordial Caves dimension source](../dimensions/PrimordialCaves.md#what-currently-generates) includes that biome, taking precedence over the literal Normal preset definition. Its Drowned entry still needs suitable water, depth, light and the other spawn checks; no encounter rate or portal landing was tested. [Biome definition][primordial_ocean]

[Ocean Ruins](../structures/OceanRuins.md) also have a structure-marker path that creates and finalizes persistent Drowned. Ordinary [Zombie water conversion](Zombie.md#water-conversion) is another route; a [Husk](Husk.md#water-conversion) must first become a Zombie. The ordinary listed [Drowned Spawn Egg](../items/DrownedSpawnEgg.md) provides a separate spawning route through MattMC's [inventory browser](../mechanics/InventoryBrowser.md), including in Survival. [Egg category](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2009) [Ruin creation and checked marker][ruin-pieces] [ruin-template] · [Conversion][zombie] · [Egg registration][items]

<span id="behavior"></span>

## Behavior and danger

Drowned swim toward suitable underwater targets and can seek land when it is dark outside. They target players, Villagers, Iron Golems, Axolotls, and baby Turtles on land through their registered goals. During the bright part of the day, the normal player-selection and melee checks require the target to be in water. This is **not complete daytime safety**: retaliation and retained targets are separate, and the Trident attack goal has no additional daylight check of its own. [Movement, targeting, melee, and ranged goals][drowned] · [Active goal dispatch][mob]

A Drowned holding a Trident in its main hand can throw Trident projectiles repeatedly without spending that held stack. Its thrown projectiles are not normally collectible in Survival; obtain an equipment drop from the mob instead. Full player Trident combat and enchantment guidance is outside this page. [Ranged attack and equipment gate][drowned] · [Projectile construction][trident-projectile] · [Owner and pickup rules][arrow-pickup]

Drowned inherit Zombie sunlight sensitivity, so exposed Drowned on land can burn unless another condition, such as water or head equipment, prevents the trigger. They also inherit the Zombie infection callback for killed Villagers. They do **not** convert back into ordinary Zombies by leaving water, and their own further water conversion is disabled. [Sunlight and infection][zombie] · [Drowned conversion override][drowned]

## Equipment and conversion limits

During ordinary equipment initialization, approximately **6.25%** receive a Trident and **3.75%** receive a Fishing Rod in the main hand. A separate **3%** finalization roll gives a Nautilus Shell if the offhand is empty. That shell is marked for guaranteed equipment dropping. These are equipment rolls, not the chances of receiving those items from every kill. [Equipment and shell initialization][drowned]

For Tridents or Fishing Rods from that ordinary equipment roll, the equipment drop chance is **8.5%** on a qualifying player-credited kill, rising by **one percentage point per Looting level** to **11.5% at Looting III**. The dropped weapon can be badly worn. A held shell marked by the spawn routine drops without needing player kill credit, but still requires **doMobLoot**. Looting does not duplicate the shell. [Equipment drop handler][mob] · [Default/preserved drop chances][drop-chances] · [Looting equipment effect][looting] · [Monster loot gate][monster]

Zombie-to-Drowned conversion preserves the existing equipment and drop chances. It calls the conversion helper, not Drowned spawn finalization, so **an empty-handed Zombie does not gain a fresh Trident or shell roll by drowning**. Existing baby state, custom name, and relevant persistence state also transfer. [Conversion setup][zombie] · [Replacement dispatch][mob] · [Transferred state][conversion] · [Natural equipment path][drowned]

## Other drops and keeping a Drowned

With **doMobLoot** enabled, the bundled table gives **0–2 Rotten Flesh**, up to five with Looting III. A qualifying player-credited kill separately has an **11% chance of one Copper Ingot**, rising to **13%, 15%, and 17%** with Looting I, II, and III. The table has no universal Trident or Nautilus Shell entry; those depend on equipment. Babies can use the monster loot path too. [Drowned loot][drowned-loot] · [Monster baby/loot rules][monster]

The Zombie family's ordinary experience rules also apply: base reward **5 for an adult or 12 for a baby**, before equipment and other reward modifiers, under normal player-credit and gamerule conditions. [Zombie experience][zombie] · [Equipment reward calculation][mob] · [Experience gate][living]

Ordinary Drowned can distance-despawn; a ruin-created Drowned is explicitly marked persistent. Names and normal persistence rules may prevent distance despawning, but do not protect a hostile mob from Peaceful removal. Equipment and relevant Zombie state are saved. [Ruin persistence][ruin-pieces] · [Despawn and save handling][mob] [living][] [zombie][] · [Peaceful exclusion][entities]

## Related pages

- [Zombie](Zombie.md), [Husk](Husk.md), [Zombie Villager](ZombieVillager.md)
- [Nautilus Shell](../items/NautilusShell.md), [Rotten Flesh](../items/RottenFlesh.md), [Mobs](Mobs.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game combat, conversion, curing, loot, or crafting test was run. Data packs, entity state, difficulty, gamerules, and later builds can change these results.

[drowned]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Drowned.java
[entities]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/EntityType.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[zombie]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Zombie.java
[mob]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Mob.java
[ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[deep_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/deep_ocean.json
[cold_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/cold_ocean.json
[deep_cold_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/deep_cold_ocean.json
[lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/lukewarm_ocean.json
[deep_lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/deep_lukewarm_ocean.json
[warm_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json
[frozen_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/frozen_ocean.json
[deep_frozen_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/deep_frozen_ocean.json
[river]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/river.json
[frozen_river]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/frozen_river.json
[dripstone_caves]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/dripstone_caves.json
[placements]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[more-drowned]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/worldgen/biome/more_frequent_drowned_spawns.json
[rivers]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/worldgen/biome/is_river.json
[monster]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Monster.java
[overworld]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/dimension_type/overworld.json
[natural]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[primordial_ocean]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[normal]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[ruin-pieces]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java
[ruin-template]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/structure/underwater_ruin/big_brick_1.nbt
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[trident-projectile]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java
[arrow-pickup]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/DropChances.java
[looting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/looting.json
[conversion]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ConversionType.java
[drowned-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/drowned.json
[living]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java
