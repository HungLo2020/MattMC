# Bamboo Shelf

Bamboo Shelf is the item for **`minecraft:bamboo_shelf`**. Craft **six Stripped Blocks of Bamboo**, three across the top row and three across the bottom with an empty middle row, into **six Bamboo Shelves**. Bamboo items, Bamboo Planks, and unstripped Blocks of Bamboo are not recipe substitutes. [Recipe][recipe-bamboo_shelf]

It has the same three-stack storage and powered hotbar exchanges as the other [Shelves](../blocks/Shelves.md). Powered shelves of different woods can connect when adjacent, aligned, and within the three-Shelf limit. [Interaction][shelf] · [Connection rules][chains] [shelf-tag]

Hand mining drops one empty Bamboo Shelf, with an axe as the efficient tool. Its stored stacks spill separately; Silk Touch does not put them inside the dropped Shelf. [Loot][loot-bamboo_shelf] · [Mining tag][axe] · [Removal dispatch][chunk] [spill]

See [Bamboo](../blocks/Bamboo.md) for the raw material and [Shelf persistence](../blocks/Shelves.md#mining-saving-and-moving-contents) for the distinction between placed storage and item components.

[recipe-bamboo_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/bamboo_shelf.json
[shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ShelfBlock.java
[chains]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/SideChainPartBlock.java
[shelf-tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/wooden_shelves.json
[loot-bamboo_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/bamboo_shelf.json
[axe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[chunk]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L357
[spill]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
