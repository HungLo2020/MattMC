# Elevator

The **Elevator item** (`minecraft:elevator`) places MattMC's [Elevator block](../blocks/Elevator.md#elevator). It is registered as an ordinary block item; carrying it does not provide the block's teleport action. [Item registration][item] · [Block-item registration][block-item] · [Placement action][place]

## Obtaining

It is listed in **Functional Blocks**, so the [inventory item browser](../mechanics/InventoryBrowser.md) can request it in Creative. No Elevator recipe or matching block loot table was found in the inspected bundled data. Do not assume that breaking an existing Elevator returns the item; see the block guide's [acquisition and harvest limits](../blocks/Elevator.md#obtaining-and-breaking). [Creative tab][creative-tab] · [Creative entry][creative-entry] · [Recipes][recipes] · [Block loot inventory][loot]

## Usage

Use the item to place an Elevator. Align another Elevator above or below it, then use the placed blocks' [Jump/Sneak controls](../blocks/Elevator.md#controls-and-destination-search). The two upward search paths and the server's acceptance checks are explained on that page. [Placement][place]

## Behavior

Teleportation belongs to the placed block and player input handlers. The landing test checks less than a full safe standing space; prepare each destination using the [landing and safety guidance](../blocks/Elevator.md#landing-position-and-safety-limits). [Active block behavior][block]

## Notes

This is the item form of `minecraft:elevator`. Recipes, harvesting, controls and landing behavior are documented on the [canonical placed-block guide](../blocks/Elevator.md#elevator), rather than inherited from an upstream Elevator mod.

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition or teleport test was run.

[All items](Items.md) · [Redstone and transport catalog](../blocks/catalog/redstone.md)

[item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L871
[block-item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L2750-L2752
[place]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L83
[creative-tab]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1036
[creative-entry]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1258-L1262
[recipes]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks
[block]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/ElevatorBlock.java#L28-L85
