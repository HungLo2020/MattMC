# Crimson Shelf

Crimson Shelf is the item for **`minecraft:crimson_shelf`**. Six **Stripped Crimson Stems**, arranged as full top and bottom rows with the middle empty, craft **six Crimson Shelves**. Crimson Planks and Stripped Crimson Hyphae do not substitute in that recipe. [Recipe][recipe-crimson_shelf]

It shares the three-slot storage, waterlogging, powered hotbar exchange, and rear Comparator behavior described in [Shelves](../blocks/Shelves.md). It can connect to powered Shelves of other listed woods. [Block behavior][shelf] · [Connection tag][shelf-tag]

Hand mining returns one empty Crimson Shelf; an axe is faster. Stored items drop separately, including when you use Silk Touch. [Loot][loot-crimson_shelf] · [Registration][registration] · [Removal][chunk] [spill]

The item is excluded from default furnace fuel. Its fuel classification and the placed block's lava-ignition flag are different rules; see [fuel and fire distinctions](../blocks/Shelves.md#fuel-and-fire-distinctions). [Fuel exclusion][nonflammable] [fuel] · [Block registration][registration]

[recipe-crimson_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/crimson_shelf.json
[shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ShelfBlock.java
[shelf-tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/wooden_shelves.json
[loot-crimson_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/crimson_shelf.json
[registration]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L1125-L1184
[chunk]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L357
[spill]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[nonflammable]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
