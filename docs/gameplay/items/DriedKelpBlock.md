# Dried Kelp Block

A **Dried Kelp Block** (`minecraft:dried_kelp_block`) packs nine pieces of [Dried Kelp](DriedKelp.md) into one placeable block. It can be unpacked or burned as Furnace fuel. The [Kelp block guide](../blocks/Kelp.md#dried-kelp-blocks) covers placed shape, mining, and drops. [Block registration][pack-block] · [Item registration][pack-item]

## Packing and unpacking

| Recipe | Exact input and arrangement | Output |
| --- | --- | --- |
| Pack | **9 Dried Kelp**, filling the 3 × 3 Crafting Table grid | **1 Dried Kelp Block** |
| Unpack | **1 Dried Kelp Block**, shapeless | **9 Dried Kelp** |

Packing uses the dried food item, not raw Kelp. Unpacking fits the personal crafting grid. [Packing recipe][pack-recipe] · [Unpacking recipe][unpack-recipe]

## Fuel and building

One block supplies **4,001 default Furnace burn ticks**, enough for **20 uninterrupted 200-tick recipes**, with one burn tick left over. This is the bundled value `1 + 200 × 20`. Plan continuous batches; already-burning fuel is spent even if processing stops. [Fuel entry][fuel] · [Server fuel setup][fuel-load] · [Furnace fuel planning](../blocks/Furnace.md#fuel-planning)

The block is also a flammable construction material. Ordinary mining returns one block without a tool or Silk Touch requirement; Fortune does not multiply it. See the [placed-block rules](../blocks/Kelp.md#dried-kelp-blocks) for hardness, hoe speed, support, and explosion limits. [Fire registration][fire] · [Loot][pack-loot]

Related: [Dried Kelp cooking and food](DriedKelp.md) · [Kelp farming](../blocks/Kelp.md) · [Furnace](../blocks/Furnace.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, packing/unpacking, block loot, default fuel, and fire registration checked. No in-game crafting, harvesting, or fuel test was run.

[pack-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4749-L4751
[pack-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1596
[pack-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dried_kelp_block.json
[unpack-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dried_kelp.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-load]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L340
[fire]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FireBlock.java#L484
[pack-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dried_kelp_block.json
