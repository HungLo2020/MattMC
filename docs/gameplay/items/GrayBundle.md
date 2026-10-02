# Gray Bundle

**Gray Bundle** (`minecraft:gray_bundle`) is the gray variant of the [Bundle](Bundle.md). It stacks to one and uses the shared Bundle controls and capacity. [Registration][gray-item]

## Obtaining

Put **one Bundle and one [Gray Dye](GrayDye.md)** in any two crafting-grid slots to make **one Gray Bundle**. You can use an uncolored Bundle or another color, with contents still inside. The recipe accepts the full Bundle item tag; it copies the input's stored items and other saved components. An already gray Bundle combined with Gray Dye produces no result. [Exact recipe][gray-recipe] · [Accepted variants][bundles-tag] · [Recipe matching][transmute] · [Copied result][transmute-result] · [Component preservation][component-copy]

Get the first uncolored Bundle through [its crafting and village-loot routes](Bundle.md#obtaining). Gray Bundle is also directly listed in the ordinary [Inventory Browser](../mechanics/InventoryBrowser.md), available in **Survival and Creative**. [Category entries][category] · [Browser list][browser] · [Server access][browser-server]

## Usage

Use the [inventory controls](Bundle.md#inventory-controls) to insert or take stored stacks, and [scroll over the Bundle](Bundle.md#selecting-a-stored-stack) to choose a shown stack. Holding Use in the world [drops its stored stacks](Bundle.md#emptying-in-the-world).

## Behavior

Use the color to distinguish supplies; Gray Bundle has the same storage capacity as every other Bundle color. [Registration][gray-item]

The [capacity rules](Bundle.md#capacity) depend on each stored item's maximum stack size. [Nesting rules](Bundle.md#nested-bundles-and-blocked-items) allow another Bundle with an added space cost and exclude Shulker Boxes. Recoloring this Bundle with a different dye keeps its contents; see the [full color list](Bundle.md#colors-and-recoloring).

## Notes

- Exact item ID: `minecraft:gray_bundle`.
- Exact bundled recipe ID: `minecraft:crafting/gray_bundle`.
- Source-reviewed on **2026-10-02** at `1d7e3e91f2a2694339f78b8673993e98d496ca5f`. The shared [Bundle notes](Bundle.md#notes) describe the review and testing limits.

[gray-item]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/Items.java#L1636-L1638
[gray-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/gray_bundle.json#L1-L10
[bundles-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/item/bundles.json#L1-L21
[transmute]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/crafting/TransmuteRecipe.java#L38-L77
[transmute-result]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L55
[component-copy]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L637
[category]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1464-L1480
[browser]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1919
