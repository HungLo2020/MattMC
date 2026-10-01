# Hopper

The Hopper item places the `minecraft:hopper` block, a five-slot automatic item-transfer container.

## Obtaining

Craft one from five Iron Ingots and one ordinary Chest: ingots in the upper corners, Ingot–Chest–Ingot across the middle, and one ingot centered below. Collect the placed block with a pickaxe.

## Use

Place its outlet toward the destination container; it can point down or sideways. The placed Hopper pulls from above and pushes toward the outlet. Redstone power stops its own automatic transfers, but does not prevent every outside access to its inventory.

See the [Hopper block guide](../blocks/Hopper.md) for timing, loose-item pickup, locking, inventory handling, and Furnace connections. Its five inventory slots are not carried inside the ordinary dropped block item.

## Related pages

- [Hopper behavior](../blocks/Hopper.md)
- [Iron Ingot](IronIngot.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game transfer, pickup, redstone timing, comparator, or furnace-automation test was run. Container rules, tags, and data packs can change results.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/hopper.json)
- [Placement, redstone, and menu](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/HopperBlock.java)
- [Transfer order, cooldown, item pickup, and saved inventory](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java)
- [Correct-tool requirement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Hopper block loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/hopper.json)
- [Container removal drops](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java)
- [Above-block pickup exceptions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/does_not_block_hoppers.json)
- [Fullness signal calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java)
- [Furnace sided slots](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java)
