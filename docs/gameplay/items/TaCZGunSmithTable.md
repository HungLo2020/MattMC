# TaCZ Gun Smith Table

The TaCZ Gun Smith Table item (`minecraft:gun_smith_table`) places the corresponding [TaCZ workbench](../blocks/TaCZWorkbenches.md#gun-smith-table). Its placed menu makes firearms from materials carried by the player. [Item registration][items] · [Menu wiring][block-menus]

## Obtaining

Craft one using the [Gun Smith Table recipe in the workbench guide](../blocks/TaCZWorkbenches.md#crafting-the-tables), collect a placed table, or take it from the Creative inventory. The table recipe uses the `minecraft:planks` tag for its two planks. [Recipe][gun-recipe] · [Creative entry][creative]

## Usage

Place the item, then use the block. See the [shared workbench controls](../blocks/TaCZWorkbenches.md#using-a-workbench) for choosing a recipe, reading material counts, and crafting. The table has no separate material, fuel, or output slots. [Menu][menu]

## Behavior

Materials stay in the player's inventory until a craft succeeds. In Survival, the output goes into that inventory, with any uninserted remainder dropped. Creative crafting still needs ingredients present but does not consume them; leave room for the output, because Creative inventory insertion discards overflow. [Craft transaction][transaction] · [Inventory insertion][inventory-add]

## Notes

- [Placement, mining, and drops](../blocks/TaCZWorkbenches.md#placing-and-collecting) are documented on the block guide
- [Current limitations](../blocks/TaCZWorkbenches.md#current-limitations) cover Creative material checks, untranslated labels, and the bundled recipe list
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`; no in-game test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L2678-L2689
[block-menus]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/TaczWorkbenchBlock.java#L98-L144
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/gun_smith_table.json#L1-L18
[creative]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1072-L1074
[menu]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L51-L122
[transaction]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106

[inventory-add]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Inventory.java#L246-L290
