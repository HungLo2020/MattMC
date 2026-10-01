# Magma Cube

**Magma Cubes** (`minecraft:magma_cube`) are fire-immune hopping enemies that split into smaller cubes when killed. Hunt the medium and large sizes for [Magma Cream](../items/MagmaCream.md), a Fire Resistance brewing ingredient. **Even the smallest cube can hurt you.** [Entity registration][registration] · [Size and attack overrides][magma] · [Loot table][loot]

## Finding Magma Cubes

The bundled Nether biome lists include:

- **Basalt Deltas:** Magma Cube weight 100, with listed groups of 2–5
- **Nether Wastes:** weight 2, with listed groups of 4
- **[Nether Fortresses](../structures/NetherFortress.md):** the fortress monster selection includes Magma Cubes at weight 3, with listed groups of 4

Weights choose among entries in the relevant list; they are not percentages or promises that an entire group will spawn. Fortress selection applies inside fortress pieces, with a separate route above Nether Bricks inside a recognized fortress. Placing Nether Bricks elsewhere does not create that route. [Basalt Deltas data][basalt] · [Nether Wastes data][wastes] · [Fortress data][fortress] · [Piece selection][chunk-spawns] · [Natural-spawn fortress route][natural]

**Magma Cube spawning has no darkness or moon-phase requirement.** Its registered predicate only rejects Peaceful, but natural spawning still checks supporting ground, empty space, collision, and the inherited liquid-free obstruction rule. Fire immunity does not make a lava-filled spawning volume valid in this snapshot. Mob caps and player-distance rules still apply. [Spawn registration][spawn] · [Magma Cube predicate][magma] · [Ground placement][ground] · [Inherited obstruction][mob] · [Spawn caller][natural]

### Treasure-bastion spawner

The treasure-bastion generation chain includes a lava-basin piece containing a **Magma Cube spawner**. This is a specific bastion layout, not a feature of every bastion. Its template lists a **16-block player activation range** and ordinary Magma Cube spawn data without a custom light rule. The active spawner still checks space, nearby mobs, obstruction, and difficulty. **Torches alone do not disable this bundled spawner.** No containment or farm layout has been tested here. [Bastion definition][bastion] · [Starting pool][bastion-starts] · [Treasure connector][treasure-start] · [Base pool][treasure-pool] · [Spawner template][treasure-template] · [Template block-entity loading][template-load] · [Spawner checks][spawner]

For Creative placement, use the [Magma Cube Spawn Egg](../items/MagmaCubeSpawnEgg.md).

## Sizes and combat

Magma Cubes inherit the Slime spawn initialization, which chooses ordinary sizes **1, 2, or 4**, with local difficulty able to shift the roll toward a larger size. Their health and dimensions scale with size; they also gain **3 armor points per size unit**. [Inherited size initialization][slime] · [Armor and damage overrides][magma] · [Default attribute registration][defaults] · [Base dimensions][registration]

| Size | Default health | Width and height | Armor points | Contact damage on Normal, before defenses |
| --- | --- | --- | --- | --- |
| Small, size 1 | 1 point, half a heart | 0.52 blocks | 3 | 3 points, 1.5 hearts |
| Medium, size 2 | 4 points, 2 hearts | 1.04 blocks | 6 | 4 points, 2 hearts |
| Large, size 4 | 16 points, 8 hearts | 2.08 blocks | 12 | 6 points, 3 hearts |

They use the Slime's player/Iron Golem targeting and contact-attack path, but remove the small-size damage exemption and add **2** to the size-based attack value. A hit still requires active AI, melee reach, and line of sight; difficulty and player defenses affect the final damage. Their ground jumps gain extra upward velocity with size. [Magma Cube overrides][magma] · [Inherited targeting and contact checks][slime] · [Player contact dispatch][player-touch] · [Player difficulty scaling][player-damage]

Do not rely on a fall, fire, or lava to kill one. The entity is fire-immune and belongs to the fall-damage-immune tag. **Fire Resistance does not prevent its contact damage:** that attack uses ordinary mob-attack damage rather than fire damage. Fire Resistance is useful for surrounding lava hazards, but you still need distance or other defenses against the cube. [Fire immunity][registration] · [Fire/fall rejection][entity] · [Fall-immunity tag][fall-tag] · [Attack damage type][damage-type] · [Fire-damage tag][fire-tag] · [Fire Resistance check][effects]

Killing a medium or large cube creates **2–4 full-health cubes at half its size**, using the same death-splitting path as Slimes. The smallest survivors remain dangerous. [Inherited splitting][slime] · [Conversion dispatch][conversion]

## Drops and Froglights

With mob loot enabled, **medium and large cubes** can drop **0–1 Magma Cream** before Looting. The initial count rolls uniformly from **−2 through 1**, so the unenchanted result is a **25% chance of one cream**, with the other three results producing none. Looting can increase the maximum to **4 at Looting III**. The cream entry excludes frog kills and does not require a player-attributed kill. **Small cubes do not drop Magma Cream.** [Loot table][loot] · [Uniform count][uniform] · [Count function][set-count] · [Empty-stack handling][stack] · [Looting function][looting] · [Death loot gates][death]

Frogs can eat the **smallest size**. A frog kill uses a separate loot branch that yields **one Froglight**, determined by the frog's variant:

| Frog variant | Froglight |
| --- | --- |
| Warm | [Pearlescent Froglight](../items/PearlescentFroglight.md) |
| Cold | [Verdant Froglight](../items/VerdantFroglight.md) |
| Temperate | [Ochre Froglight](../items/OchreFroglight.md) |

The frog must be able to reach and kill its target; merely bringing one near a large cube does not produce a Froglight. These branches have no Looting count bonus. [Frog size restriction][frog] · [Active eating behavior][tongue] · [Frog food tag][frog-tag] · [Frog-variant loot conditions][loot]

A qualifying player-attributed kill has base experience equal to size: **1, 2, or 4 XP** for ordinary sizes, with the normal XP conditions. Split offspring have separate rewards. [Inherited XP value][slime] · [XP conditions][death]

Related: [Slime](Slime.md) · [Magma Cream](../items/MagmaCream.md) · [Brewing](../brewing/Brewing.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. The treasure-bastion NBT was decoded to inspect its connector and spawner data. This is source/data review, not an in-game generation, combat, drop-rate, Froglight, or farm test. Active data packs and custom spawner/entity data can change these rules. [World-generation registry loading][worldgen-load] · [Loot loading and validation][loot-load]

[registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L862-L872
[magma]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/MagmaCube.java
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/magma_cube.json
[basalt]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/basalt_deltas.json
[wastes]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[fortress]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/fortress.json
[chunk-spawns]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L452
[natural]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L155-L336
[spawn]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[ground]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java
[mob]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L728-L741
[bastion]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/bastion_remnant.json
[bastion-starts]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/template_pool/bastion/starts.json
[treasure-start]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/bastion/treasure/big_air_full.nbt
[treasure-pool]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/template_pool/bastion/treasure/bases.json
[treasure-template]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/bastion/treasure/bases/lava_basin.nbt
[template-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L263-L312
[spawner]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/BaseSpawner.java#L31-L187
[slime]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Slime.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[player-touch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L477-L529
[player-damage]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L748
[entity]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Entity.java#L2881-L2885
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[damage-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/damage_type/mob_attack.json
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[effects]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java
[conversion]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L1157-L1184
[uniform]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java
[set-count]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/SetItemCountFunction.java
[stack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemStack.java#L1054-L1069
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[frog]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L357-L375
[tongue]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/frog/ShootTongue.java
[frog-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/frog_food.json
[worldgen-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[loot-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/ReloadableServerRegistries.java
