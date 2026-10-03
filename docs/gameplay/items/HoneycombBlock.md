# Honeycomb Block

Honeycomb Block is the placeable item for `minecraft:honeycomb_block`. Use the [Honeycomb Block guide](../blocks/HoneycombBlock.md) for its building and recovery rules. [Registration][item]

## Obtaining

Craft **four [Honeycomb](Honeycomb.md) in a 2 × 2 square into one block**, or recover one from a placed Honeycomb Block by hand. [Recipe][recipe] · [Block loot][loot] · [Drop gate][gate]

Its ordinary Natural Blocks entry also supplies the separate [Creative inventory item-browser route](../mechanics/InventoryBrowser.md). [Entry][entry]

## Usage

Place it as a decorative full cube. **No bundled recipe unpacks it into loose Honeycomb.** Reserve loose Honeycomb for waxing, Candles and Beehives; this block does not substitute for the custom waxing item. [Different item registrations][honeycomb-item] [item] · [Waxing handler][wax]

## Behavior

This is an ordinary BlockItem with no registered food component, so **it is not food**. It is also distinct from the [Honey Block](HoneyBlock.md) and its movement effects. Its placed block has no facing or waterlogged state. See [the block guide](../blocks/HoneycombBlock.md#honeycomb-honey-blocks-and-waxing) for the distinctions. [Registration][item] · [BlockItem factory][factory] · [Separate blocks][blocks]

## Notes

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02, including the full bundled recipe scan. No in-game crafting, eating or placement test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2481
[recipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/honeycomb_block.json#L1-L15
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/honeycomb_block.json#L1-L21
[gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[entry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1013
[honeycomb-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2462
[wax]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/HoneycombItem.java#L82-L129
[factory]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2750-L2755
[blocks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5758-L5765
