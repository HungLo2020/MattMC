# Blaze Rod

A Blaze Rod (`minecraft:blaze_rod`) supplies [Blaze Powder](BlazePowder.md), a [Brewing Stand](../blocks/BrewingStand.md), and other crafted items. It can also fuel a furnace, but save rods needed for brewing or [Eyes of Ender](EyeOfEnder.md) before burning them.

## Obtaining from Blazes

[Nether Fortresses](../structures/NetherFortress.md) provide the verified route to Blazes through their monster selection and possible Blaze-spawner pieces. Finding a fortress does not guarantee a spawner platform; see that guide for optional rooms and hazards.

The bundled Blaze loot table has one rod pool gated by a **player-attributed kill**. It gives a base **0–1 rod**, then applies a Looting count increase. A qualifying kill can still give no rod, so take supplies for more than one fight. The condition is part of the loot table, not a guarantee that every environmental death will pay out.

This page documents that ordinary loot route, not an exhaustive list of world-specific rewards or commands.

## Crafting choices

| Result | Required arrangement | Output |
| --- | --- | --- |
| Blaze Powder | One rod, shapeless | 2 powder |
| Brewing Stand | One rod above the center of a row of three stone-crafting materials | 1 stand |
| End Rod | One rod directly above one Popped Chorus Fruit | 4 End Rods |
| Copper Bulb | Copper Block above, left, and right of a central rod; Redstone Dust directly below it | 4 unwaxed, unoxidized bulbs |

The Brewing Stand recipe accepts **Cobblestone, Blackstone, and Cobbled Deepslate**, including mixtures, through its stone-crafting-materials tag. Ordinary Stone is not in that tag.

These are selected verified recipes. Preserve the form you need: a Brewing Stand uses a rod, while the stand's bundled fuel slot accepts **Blaze Powder**, not rods. See the [powder guide](BlazePowder.md) for fuel charges and its separate use as a potion ingredient.

## Furnace fuel

One rod provides **2,400 burn ticks** in an ordinary Furnace under the server's default loaded fuel values. At 20 ticks per second that is **120 seconds**, enough capacity for **12 uninterrupted 200-tick recipes**. It is not a guarantee of 12 outputs if the input runs out, the output slot blocks, or the recipes use different cooking times.

Once lit, furnace fuel keeps counting down even when processing stops. A Blast Furnace or Smoker halves this rod's burn duration to **1,200 ticks** through its own fuel method; use each device only for recipes it supports. See the [Furnace guide](../blocks/Furnace.md) for batch planning.

## Related pages

- [Nether Fortress](../structures/NetherFortress.md)
- [Blaze Powder](BlazePowder.md)
- [Brewing Stand](../blocks/BrewingStand.md)
- [Eye of Ender](EyeOfEnder.md)
- [Furnace](../blocks/Furnace.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game Blaze combat, collection, crafting, or fuel test was performed. Loot and recipes describe bundled data; fuel values were traced to server initialization and the furnace implementations.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1762); [Blaze loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/blaze.json); [Entity registration and default loot-table wiring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java); [Runtime loot loading](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1528)
- [Fortress monster override](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure/fortress.json); [Fortress natural-spawn selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L314-L336); [Optional pieces and Blaze-spawner placement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java)
- [Powder recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/blaze_powder.json); [Brewing Stand recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/brewing_stand.json); [Accepted stone materials](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json); [Brewing fuel tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/brewing_fuel.json)
- [End Rod recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/end_rod.json); [Copper Bulb recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_bulb.json)
- [Server fuel initialization](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/MinecraftServer.java#L334-L342); [Default rod fuel duration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L47); [Burn countdown, fuel use, and cooking](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L141-L203); [Recipe-defined cooking time](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L275); [Blast Furnace burn adjustment](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BlastFurnaceBlockEntity.java); [Smoker burn adjustment](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/SmokerBlockEntity.java)
