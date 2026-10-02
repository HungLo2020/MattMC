# Oak Shelf

Oak Shelf is the item for **`minecraft:oak_shelf`**. In a Crafting Table, place **three Stripped Oak Logs across the top row and three across the bottom**, with the middle row empty, to make **six Oak Shelves**. Planks and Stripped Oak Wood do not substitute. [Recipe][recipe-oak_shelf]

The placed Shelf stores three item stacks. Unpowered front use swaps one selected display slot with your main-hand stack; powered use swaps hotbar positions 7–9, or a larger range when connected to other powered Shelves. See [Shelves](../blocks/Shelves.md) for the exact controls and Comparator output. [Block behavior][shelf]

Hand mining recovers one empty Shelf; an axe is faster. **Stored items spill separately, including with Silk Touch.** The item's empty-by-default container component supports data-bearing items, but ordinary block loot does not pack stored items into the drop. [Item component][items] · [Loot][loot-oak_shelf] · [Removal path][chunk] [spill]

[recipe-oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/oak_shelf.json
[shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ShelfBlock.java
[items]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L418-L453
[loot-oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/oak_shelf.json
[chunk]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L357
[spill]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
