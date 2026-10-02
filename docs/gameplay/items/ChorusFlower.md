# Chorus Flower

**Chorus Flower** (`minecraft:chorus_flower`) is the collectible growing tip used to start a new Chorus Plant. See [Chorus Plants and Flowers](../blocks/Chorus.md#chorus-flower) for the placed-block guide. [Item registration][item]

## Obtaining

Its ordinary Natural Blocks entry is available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival as well as Creative; that insertion route is separate from the harvesting rules below.

[Harvest a flower directly before cutting the stems](../blocks/Chorus.md#harvest-flowers-before-stems). Direct Survival breaking gives one flower, including by hand; allowed impact projectiles can also collect it. Flowers destroyed by ordinary support loss do not supply that drop. The bundled loot does not require Silk Touch and does not increase with Fortune. [Flower loot][loot] · [Projectile callback][hit] · [Support-loss callback][tick]

Wild Chorus grows through the [End Highlands generation route](../blocks/Chorus.md#finding-wild-chorus). The checked recipe bundle has no crafting recipe producing Chorus Flower; Creative lists it separately from the stem. [Creative entries][creative]

## Usage

Plant the flower on **End Stone**, with clearance for growth. End Stone Bricks are not a substitute. Flowers also have specific stem-supported positions; use the [planting and growth rules](../blocks/Chorus.md#planting-and-growth). [Support check][support]

## Behavior

An ordinarily placed flower starts at age 0. At age 5 it stops receiving growth random ticks, but direct collection still gives a usable flower item. Bone Meal does not grow it. The canonical guide covers [growth](../blocks/Chorus.md#planting-and-growth), [water and support](../blocks/Chorus.md#shape-support-and-water), and the difference between flowers and fruit. [Default age and random ticks][age] · [Loot][loot] · [Bone Meal dispatch][bone-meal]

## Notes

The flower is not the edible [Chorus Fruit](ChorusFruit.md) or the [Chorus Plant stem item](ChorusPlant.md). Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`; no in-game harvest or growth test was run.

Related: [Items](Items.md) · [Chorus guide](../blocks/Chorus.md)

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L469-L470
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/chorus_flower.json#L1-L26
[hit]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L256-L262
[tick]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L46-L51
[creative]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L940-L946
[support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L168-L195
[age]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L25-L61
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
