# Dried Ghast

Dried Ghast is the ordinary block item for `minecraft:dried_ghast`. The [Dried Ghast block guide](../blocks/DriedGhast.md) owns its acquisition, waterlogging, hydration, drying and hatching rules. [Item registration][ghast-item] · [Block-item registration helper][item-helper]

## Obtaining

Craft **1 Dried Ghast** from **8 Ghast Tears around 1 Soul Sand**, recover a placed block by ordinary mining, or use the verified adult-Piglin barter and Nether-Fossil routes in [Obtaining](../blocks/DriedGhast.md#obtaining). Silk Touch is unnecessary for its one-item block drop. [Recipe][ghast-recipe] · [Block loot][ghast-loot] · [Block properties][ghast-block]

It is listed in **Natural Blocks** and can also be requested through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. The browser entry is separate from the crafting, barter and generation routes. [Category][natural-category] · [Entry][ghast-category]

## Usage

Place and **waterlog** it to begin the progression toward a baby [Happy Ghast](../mobs/HappyGhast.md). Follow [placing and adding Water](../blocks/DriedGhast.md#placing-and-adding-water) for bucket handling and dimension restrictions. [Placement and waterlogging][ghast-water]

## Behavior

Hydration advances through **0, 1, 2 and 3**, then a further waterlogged check attempts to hatch a ghastling. Dry checks lower hydration instead. These checks depend on random ticks scheduling 5,000-tick delays, so the code does not define an exact 20-minute hatch timer. See [hydration and drying](../blocks/DriedGhast.md#hydration-and-drying). The hatch entity is `minecraft:happy_ghast`, not the hostile `minecraft:ghast`. [Cycle][ghast-cycle] · [Scheduling][ghast-schedule] · [Hatching][ghast-hatch]

## Notes

- Item and block ID: `minecraft:dried_ghast`
- Normal collection and replacement resets hydration to **0**; see [hatching and collecting](../blocks/DriedGhast.md#hatching-and-collecting-the-block)
- The [canonical guide's verification](../blocks/DriedGhast.md#sources-and-verification) records the source review and runtime limits at `b823010659d7b5095ed021b1c99cf85627e2082a`

[ghast-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L881-L881
[item-helper]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2750-L2781
[ghast-recipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/dried_ghast.json#L1-L18
[ghast-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/dried_ghast.json#L1-L21
[ghast-block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4825-L4829
[natural-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L751-L759
[ghast-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L953-L961
[ghast-water]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L169-L194
[ghast-cycle]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L88-L113
[ghast-schedule]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L161-L167
[ghast-hatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L115-L127
