# Wheat

Wheat is a harvested crop ingredient registered as `minecraft:wheat`. It does not place the crop and is not registered as player food; use Wheat Seeds for planting and Bread for a crafted food.

## Obtaining

Harvest mature [Wheat crops](../blocks/Wheat.md). MattMC supports empty-hand harvesting and same-crop 3 × 3 hoe harvesting that reset mature crops to age 0; the crop guide explains durability and loot differences.

## Crafting and animal use

- Three Wheat in a row craft **one Bread**.
- Nine Wheat in a shapeless recipe craft **one Hay Bale**.
- One Hay Bale in a shapeless recipe returns **nine Wheat**.
- Wheat is the current bundled cow-food tag's ingredient, used to attract and breed [Cows](../mobs/Cow.md).

These are verified uses, not a complete list of all recipes and animal interactions.

## Related pages

- [Wheat crop](../blocks/Wheat.md)
- [Wheat Seeds](WheatSeeds.md)
- [Bread](Bread.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Crop loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/wheat.json)
- [Bread recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/bread.json)
- [Hay Bale recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/hay_block.json)
- [Unpacking Hay Bale](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/wheat.json)
- [Cow food](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/cow_food.json)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
