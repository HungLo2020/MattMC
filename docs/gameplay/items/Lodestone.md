# Lodestone

## Obtaining

Craft **1 Iron Ingot surrounded by 8 Chiseled Stone Bricks → 1 Lodestone**. The [placed Lodestone guide](../blocks/Lodestone.md#crafting-and-loot) also documents checked Bastion bridge and Ruined Portal chest routes. [Exact recipe][recipe]

## Usage

Place the block and use a [Compass](Compass.md#lodestone-binding) on it to bind that Compass to the block's coordinates and dimension. Several Compasses can use the same marker; binding consumes no Lodestone or fuel. [Binding interaction][bind]

## Behavior

Recover the block with an **unbroken Pickaxe**, including Wood or Gold, to get one Lodestone. Moving it does not move existing Compass targets: bind each Compass again at the new position. The [block guide](../blocks/Lodestone.md#placing-and-recovering-the-block) explains tool tiers, loot and marker removal; [Compass](Compass.md) covers ordinary navigation. [Harvest rule][harvest] · [Loot][loot] · [Target checks][tracker]

## Notes

* This item is the item form of the `minecraft:lodestone` block. [Item registration][items]
* Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`; no in-game crafting, mining, binding or dimension test was run

Related: [Placed-block guide](../blocks/Lodestone.md) · [Compass](Compass.md) · [Items](Items.md)

[recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/lodestone.json#L1-L17
[bind]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/CompassItem.java#L21-L75
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/lodestone.json#L1-L21
[tracker]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/component/LodestoneTracker.java#L14-L40
[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2482-L2482
