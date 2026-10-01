# Chest

A Chest is a persistent storage block with **27 inventory slots**. Two compatible adjacent chests can join into a **54-slot double chest**. Its block and item ID is `minecraft:chest`.

## Crafting and collecting

Arrange eight planks around an empty center in a [Crafting Table](CraftingTable.md) to craft one chest. Each ingredient must match the bundled planks tag. [Pewen Planks](../items/PewenPlanks.md) currently lack that tag membership, so they are not an established substitute in this recipe.

The chest block does not require a special tool for its own drop, and an axe is its tagged mining tool. Its block loot table preserves a custom name. Empty valuable contents before relocating storage; this page does not describe a chest item as portable filled storage.

## Placement and access

Ordinary placement can connect a new chest to a compatible single chest beside it when their facing and connection directions agree. The combined menu has six rows. Secondary-use placement affects automatic connection, so check the resulting shape before filling a large storage wall.

Leave the lid area clear. The opening check rejects a chest when the block above is a redstone conductor, or when a sitting cat occupies the space above it. For a double chest, check both halves if the container will not open.

The block can be waterlogged. It also exposes an analog comparator signal based on its container contents, useful for fullness indicators. This is different from the special trapped-chest opening signal.

## Storage cautions

Opening a chest calls the nearby-piglin anger behavior, so a storage action can have consequences around piglins. The exact anger radius and exceptions are outside this page's verified scope.

This article covers ordinary chests. [Trapped Chests](../items/TrappedChest.md), [Ender Chests](../items/EnderChest.md), and integrated copper storage have distinct behavior and should not be assumed interchangeable.

## Related pages

- [Chest item](../items/Chest.md)
- [Crafting Table](CraftingTable.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/chest.json)
- [Planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
- [Slots](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java)
- [Connection, obstruction and comparator behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ChestBlock.java)
- [Registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1244-L1247)
- [Loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/chest.json)
- [Axe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/axe.json)
