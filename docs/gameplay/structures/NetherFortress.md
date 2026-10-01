# Nether Fortress

A Nether Fortress (`minecraft:fortress`) is a branching complex of Nether-brick bridges, stairs, and corridors. Visit for Blazes and their rods, look for Nether Wart to start a crop, and search optional corridor chests. Plan a safe route back to your portal before entering.

## Where to look

With the bundled generation data, fortresses are eligible in all five Nether biomes: Nether Wastes, Soul Sand Valley, Crimson Forest, Warped Forest, and Basalt Deltas. Eligibility applies to the structure's start; it does not promise a fortress in every biome patch.

Fortresses share a placement set with bastion remnants. The set selects between them with fortress weight 2 and bastion weight 3, and can try the remaining choice if one cannot generate. Treat these as placement weights, not a guarantee that exactly two of every five structures you encounter will be fortresses.

Search for connected dark Nether-brick walkways and enclosed corridors. There is no fixed compass direction or guaranteed travel distance in this guide. If you have permission level 2, run `/locate structure minecraft:fortress` in the Nether. Read the [locating cautions](Structures.md#finding-a-structure) before using its coordinates.

## What you can find

The layout is assembled from randomly selected pieces. Blaze-spawner platforms, enclosed corridors, and Nether Wart rooms are possible pieces, not a checklist that every fortress must contain.

- **Blaze spawners:** a generated monster-throne piece places a spawner configured for Blazes. Blazes also belong to the fortress's natural monster selection, so a spawner is not the only way to encounter one
- **Nether Wart:** a stalk room places two planted beds over Soul Sand. Collect a starter supply and follow the [crop guide](../blocks/NetherWart.md) for planting, growth, and harvest rules
- **Chests:** some small left- and right-turn corridor pieces place a chest. Each of those piece types makes a one-in-three chest decision; this is not a one-in-three chance for the whole fortress

### Chest rewards

The bundled fortress chest table makes 2–4 rolls from a weighted pool. Possible rewards include Diamonds, Iron and Gold Ingots, Nether Wart, Obsidian, Flint and Steel, Saddles, golden equipment, and horse armor including Copper Horse Armor. A selected wart entry gives 3–7 wart; a selected Obsidian entry gives 2–4 blocks. These are entry amounts, not guaranteed chest totals.

A separate single roll selects either nothing with weight 14 or one Rib Armor Trim Smithing Template with weight 1. Thus the template chance is **1 in 15 per chest using this unchanged table**. Loot is random; do not plan your return trip around finding Obsidian or Flint and Steel.

### Blaze Rods and progression

The bundled Blaze loot table requires a player-attributed kill for its rod pool. It gives a base 0–1 Blaze Rod, with a Looting count increase, so a kill does not guarantee a rod. One rod crafts into two Blaze Powder. Powder is used in [brewing](../brewing/Brewing.md) and in the Eye of Ender recipe used for a [Stronghold](Stronghold.md) expedition.

Keep a spawner intact if you want to return to it. This page does not establish a farm design, spawn rate, or lighting arrangement that safely disables a spawner.

## Hazards and a safer approach

Fortress monster selection includes Blazes, Zombified Piglins, Wither Skeletons, Skeletons, and Magma Cubes. The bundled structure override applies within its pieces; a separate natural-spawn check also uses fortress monsters above Nether Bricks inside a recognized fortress. Placing Nether Bricks elsewhere does not create fortress spawning by itself. Actual spawning still depends on the applicable spawn rules and world conditions.

- **Blazes attack at range.** Their attack goal launches small fireballs when they can see a target; those projectiles can ignite a hit entity. Use corners and solid cover while securing a retreat
- **Wither Skeletons punish close combat.** A successful melee hit applies [Wither](../effects/Wither.md) for 200 ticks, normally 10 seconds at 20 ticks per second
- **Lava can be part of the structure.** The castle-entrance piece explicitly places a lava source. Look before crossing or opening the floor
- **The surrounding Nether remains dangerous.** Review [Nether hazards](../dimensions/Nether.md#hazards-to-plan-around), particularly water-bucket evaporation and exploding beds

Mark explored branches and protect exposed crossings before chasing mobs or opening chests. If your goal is wart and the fortress has no stalk room, another fortress may be a better next destination than repeatedly searching the same corridors.

## Related pages

- [Structures](Structures.md)
- [Nether](../dimensions/Nether.md)
- [Nether Wart crop](../blocks/NetherWart.md)
- [Brewing](../brewing/Brewing.md)
- [Stronghold](Stronghold.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game fortress generation, combat, chest-opening, or collection test was performed. The generation and loot claims use active `src/main` code and bundled data; changed data packs, world settings, and existing terrain can differ.

- [Registered structure type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java), [fortress definition and spawn override](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure/fortress.json), [allowed biome tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/nether_fortress.json), and [its five Nether biomes](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/is_nether.json)
- [Shared fortress/bastion placement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure_set/nether_complexes.json), [weighted selection and retry](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L544), and [fortress assembly](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressStructure.java)
- [Piece selection, lava, optional chests, wart, and Blaze spawners](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java) and [fortress chest loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/nether_bridge.json)
- [Blaze rod loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/blaze.json), [powder recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/blaze_powder.json), and [Eye of Ender recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/ender_eye.json)
- [Fortress-area natural monster selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L314-L336) and [piece-bounded spawn overrides](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L425-L450)
- [Blaze attacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Blaze.java), [small-fireball hits](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/SmallFireball.java), and [Wither Skeleton melee effect](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/WitherSkeleton.java#L93-L104)
