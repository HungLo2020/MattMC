# Spruce Door

`minecraft:spruce_door` places a **two-block-tall doorway**. Its shared placed-block guide is [Wood construction: Doors](../blocks/WoodConstruction.md#doors). [Item binding][item] · [Block type][block]

## Obtaining

Craft **6 [Spruce Planks](SprucePlanks.md) into 3 Spruce Doors** using the [door layout](../blocks/WoodConstruction.md#crafting-construction-shapes): two columns of three in a Crafting Table. Every plank must be this exact material. [Recipe][recipe]

The ordinary [inventory-browser entry](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) also supplies this item in Survival and Creative, subject to the browser's limits. [Category listing][listing]

## Usage

Interact to open or close it by hand, or use redstone. The [Door guide](../blocks/WoodConstruction.md#doors) explains floor support, upper-space clearance and hinge selection; [power rules](../blocks/WoodConstruction.md#waterlogging-and-power) explain placement under power and later signal changes.

## Behavior

The two halves form one door and have no waterlogged state. Normal hand mining recovers **one matching door for the assembly**, with the loot entry on its lower half; Silk Touch is unnecessary and explosion recovery is conditional. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Exact loot][loot]

## Notes

Spruce Door burns for **200 ticks** as default furnace fuel. Placed-block fire spread and nearby lava ignition are separate rules; see [fire and furnace fuel](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Fuel table][fuel] · [Door tag][fuel-tag]

Item binding, recipe, listing and loot reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Shared behavior remains in the linked family guide; no gameplay test.

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1070
[block]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Blocks.java#L4113-L4123
[recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/spruce_door.json
[listing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L101
[loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/spruce_door.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/wooden_doors.json
