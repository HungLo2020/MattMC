# Acacia Door

`minecraft:acacia_door` places a **two-block-tall doorway**. Its shared placed-block guide is [Wood construction: Doors](../blocks/WoodConstruction.md#doors). [Item binding][item] · [Block type][block]

## Obtaining

Craft **6 [Acacia Planks](AcaciaPlanks.md) into 3 Acacia Doors** using the [door layout](../blocks/WoodConstruction.md#crafting-construction-shapes): two columns of three in a Crafting Table. Other plank materials cannot fill this recipe. [Recipe][recipe]

MattMC's JEI-style [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) also supplies this ordinary listed item in Creative, subject to the browser's limits. [Category listing][listing]

## Usage

Use Acacia Doors as normal entrances, redstone-controlled gates or compact movement barriers. Interact with a door by hand or change its redstone power to open and close it. Steady power does not prevent hand operation; see [power rules](../blocks/WoodConstruction.md#waterlogging-and-power). [Hand and power interaction][door-use]

## Behavior

The lower half needs a **sturdy upper face beneath it**, and the upper space must be replaceable and inside the build limit. Follow the [Door guide](../blocks/WoodConstruction.md#doors) for placement and loss of support. [Placement][door-placement] · [Support][door-support]

Adjacent doors can influence hinge selection to form a double-door layout. Neighboring full collision blocks and the click position also matter; matching material alone does not guarantee the desired hinges. [Hinge selection][door-placement]

Acacia Doors have no waterlogged state. See [Water and power behavior](../blocks/WoodConstruction.md#waterlogging-and-power). For mob-specific entrance risks, use [Zombie door breaking](../mobs/Zombie.md#doors-and-daylight) and [Vindicator doors during raids](../mobs/Vindicator.md#doors-during-raids). [Door states][door-water]

Normal hand mining recovers **one Acacia Door for the two-block assembly**. The loot entry belongs to the lower half, requires no Silk Touch and has an explosion-survival condition. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Exact loot][loot]

## Notes

Acacia Door burns for **200 ticks** as default furnace fuel. Placed-block fire spread and nearby lava ignition are separate rules; see [fire and furnace fuel](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Fuel table][fuel] · [Door tag][fuel-tag]

Item binding, recipe, listing and loot reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Shared behavior remains in the linked family and mob guides; no gameplay test.

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1073
[block]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Blocks.java#L4146-L4156
[recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/acacia_door.json
[listing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L140
[loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/acacia_door.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/wooden_doors.json
[door-placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L141-L199
[door-support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L240-L245
[door-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L201-L238
[door-water]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L266-L269
