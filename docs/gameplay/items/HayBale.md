# Hay Bale

Hay Bale is the item form of `minecraft:hay_block`. The [Hay Bale block guide](../blocks/HayBale.md) covers placement, landing protection and signal smoke. [Registration][item]

## Obtaining

Craft **nine Wheat into one Hay Bale** with the shapeless nine-ingredient recipe, or recover one from a placed bale by hand. The block guide documents a Plains-village pile source. [Packing][pack] · [Block loot][loot] · [Hand-harvest gate][gate]

It is also an ordinary Natural Blocks entry available through MattMC's [Survival/Creative inventory item browser](../mechanics/InventoryBrowser.md), separately from crafting and generated sources. [Entry][entry]

## Usage

Craft **one bale into nine Wheat**, place it as a block, or use one with four Redstone Dust to craft a [Target](../blocks/Target.md). Animal item-feeding uses are linked from the block guide. [Unpacking][unpack] · [Target recipe][target]

## Behavior

Placed bales orient along the clicked face's axis and reduce the ordinary landing-damage multiplier to **0.2**. A bale directly under a Campfire enables signal-smoke visuals; it does not extend the Bee housing smoke check. See [placement and recovery](../blocks/HayBale.md#placement-and-recovery), [softer landings](../blocks/HayBale.md#softer-landings) and [signal smoke](../blocks/HayBale.md#campfire-signal-smoke).

## Notes

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. No in-game test was run; the block guide records the reviewed scope and source paths.

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L657
[pack]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/hay_block.json#L1-L19
[unpack]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/wheat.json#L1-L11
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/hay_block.json#L1-L21
[gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[entry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1010
[target]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/target.json#L1-L17
