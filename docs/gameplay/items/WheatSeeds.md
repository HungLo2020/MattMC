# Wheat Seeds

Wheat Seeds are the planting item for the [Wheat crop](../blocks/Wheat.md), registered as `minecraft:wheat_seeds`. Use them on valid Farmland rather than trying to plant the harvested Wheat item.

## Obtaining

Short Grass has a **12.5% seed-drop chance** in its non-shears loot branch, with Fortune affecting the result's bonus count. Shears select the Short Grass block instead. Wheat crop loot also supplies seeds; mature crop seed loot differs from immature crop loot.

These are verified routes, not an exhaustive chest, trading, or vegetation-loot catalogue.

## Planting and other uses

Plant on [Farmland](../blocks/Farmland.md) with adequate light. Hydration and crop spacing affect growth, while bone meal advances age. See the crop guide for MattMC's right-click and area-harvest controls.

Seeds also belong to the bundled chicken-food tag used by [Emus](../mobs/Emu.md), and to [Roadrunner](../mobs/Roadrunner.md)'s breeding-food tag. Feeding animals consumes resources that could otherwise expand the field.

## Related pages

- [Wheat crop](../blocks/Wheat.md)
- [Wheat](Wheat.md)
- [Farmland](../blocks/Farmland.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Short Grass loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/short_grass.json)
- [Wheat loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/wheat.json)
- [Planting registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Chicken/Emu food tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/chicken_food.json)
- [Roadrunner food tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/roadrunner_breedables.json)
