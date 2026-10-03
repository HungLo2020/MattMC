# Cherry Sign

`minecraft:cherry_sign` is the inventory item for both standing and wall Cherry signs. It stacks to 16. [Item binding][item]

## Obtaining

Use Cherry Planks and a Stick in [this variant’s sign recipe](../blocks/Signs.md#ordinary-sign-variants); the guide gives the exact layout and yield. [Recipe][recipe]

It is also an ordinary [inventory-browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) entry, visible in Survival as well as Creative; insertion requires Creative. [Category listing][listing]

## Usage

Follow the [standing and wall placement rules](../blocks/Signs.md#standing-and-wall-signs), then use the guide for [editing both faces](../blocks/Signs.md#writing-and-editing-both-faces).

## Behavior

Ordinary mining returns one matching **blank item**. Text, dye, glow, and wax are not copied into that drop. See [mining and saved text](../blocks/Signs.md#mining-drops-and-saved-text) for collection conditions. [Exact loot][loot]

## Notes

Checked item binding, recipe, listing, and loot at `25319cecd6bee492767c5b15ee53b9213d1ec1ee` on 2026-10-02; no gameplay test. Shared placement and editing details remain in the [Signs guide](../blocks/Signs.md).

[item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1434-L1436
[recipe]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe/crafting/cherry_sign.json
[listing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1160
[loot]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table/blocks/cherry_sign.json
