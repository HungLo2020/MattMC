# Wheat Seeds

Wheat Seeds are the planting item for the [Wheat crop](../blocks/Wheat.md), registered as `minecraft:wheat_seeds`. Use them on valid Farmland rather than trying to plant the harvested Wheat item.

## Obtaining

Break **Short Grass without shears** to start a field. Its seed entry has a **12.5% chance** to succeed; Fortune does **not** raise that chance. A success gives **1 seed** without Fortune, **1–3** with Fortune I, **1–5** with Fortune II, or **1–7** with Fortune III. Within each successful Fortune range, every count is equally likely. Shears select the Short Grass block instead of seeds.

These counts assume bundled loot, block drops enabled, and no explosion loss. Once a field is growing, use [Wheat crop yields](../blocks/Wheat.md#drops-and-uses) for mature and immature drops, the different Fortune calculation, and how many seeds remain after replanting.

These are verified routes, not an exhaustive chest, trading, or vegetation-loot catalogue.

## Planting and other uses

Plant on [Farmland](../blocks/Farmland.md) with adequate light. Hydration and crop spacing affect growth, while bone meal advances age. A successful Survival planting spends **one seed**. See the [crop controls](../blocks/Wheat.md#mattmc-harvesting-controls) for MattMC's mature reset harvests, which do not spend a replacement seed.

Seeds also belong to the bundled chicken-food tag used by [Emus](../mobs/Emu.md), and to [Roadrunner](../mobs/Roadrunner.md)'s breeding-food tag. Feeding animals consumes resources that could otherwise expand the field.

## Related pages

- [Wheat crop](../blocks/Wheat.md)
- [Wheat](Wheat.md)
- [Farmland](../blocks/Farmland.md)
- [Items](Items.md)

## Sources and verification

Short Grass acquisition, Fortune counts, and planting cost rechecked at `f86206767dadde696adfed4e04c5ee97cd0d0885` on **2026-10-10** from bundled data and the active drop/placement paths. No in-game drop sampling or planting test was performed. The original review and its source links are retained below.

- [Current Short Grass alternatives and chance](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/loot_table/blocks/short_grass.json) and [uniform Fortune count](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L166)
- [Tool context and block-drop gate](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/Block.java#L363-L425) and [explosion decay](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyExplosionDecay.java#L27-L44)
- [Seed registration](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Items.java#L1354), [placement cost](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L83), and [Survival consumption](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079)

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Short Grass loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/short_grass.json)
- [Wheat loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/wheat.json)
- [Planting registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Chicken/Emu food tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/chicken_food.json)
- [Roadrunner food tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/roadrunner_breedables.json)
