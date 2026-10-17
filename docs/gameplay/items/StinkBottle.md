# Stink Bottle

## Obtaining

Use a Glass Bottle on a matching exposed face of [Skunk Spray](../blocks/SkunkSpray.md#collecting-and-clearing-spray). A successful use removes that face and gives one Stink Bottle, consuming one Glass Bottle outside Creative. The filled bottle drops beside the player if it cannot fit in the inventory. This is a coating-collection action, not a skunk mob drop. [Collection][collection]

Stink Bottle is registered, but it has no entry in the checked Creative category lists. Therefore the category-based [inventory item browser](../mechanics/InventoryBrowser.md) does not provide it merely because it has an item ID. No bundled recipe or loot source was found. [Registration][item] · [Category lists][categories] · [Bundled data][data]

## Usage

The checked registration uses a plain item with no throwing, drinking, spray-placement, or status-effect action. Do not treat it as a splash potion or a reusable spray source. [Registration][item] · [Basic item use][use]

## Behavior

Its maximum stack size is **one**. A Glass Bottle is configured as its crafting remainder, but no bundled recipe using it was found; that property alone does not let you empty or refill it. [Item properties][item] · [Bundled recipes][recipes]

## Notes

* This item is registered as `minecraft:stink_bottle`; Skunk Spray itself has no inventory-item mapping.
* For the mob's Nausea attack, conditional effect cloud, and the coating's decay, see [Skunk Spray](../blocks/SkunkSpray.md).
* Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02; no in-game collection or item-use test was run.

[collection]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L93-L115
[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L1766-L1769
[use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Item.java#L164-L197
[categories]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[data]: https://github.com/HungLo2020/MattMC/tree/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft
[recipes]: https://github.com/HungLo2020/MattMC/tree/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe
