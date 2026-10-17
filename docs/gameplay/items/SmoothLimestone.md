# Smooth Limestone

**Smooth Limestone** (`minecraft:smooth_limestone`) is the ordinary item form of [this Limestone-family block](../blocks/Limestone.md#smooth-limestone). Its placement and recovery limits are covered by that substantive family guide. [Exact item binding][item] · [Block registration][blocks]

## Obtaining

Request **Smooth Limestone** in MattMC's [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item), in **Creative**. This is an ordinary listed item. No crafting, smelting or stonecutting recipe, or natural supply, was found for the family in the checked bundled data; request each form separately. See [acquisition and recipe limits](../blocks/Limestone.md#inventory-access-and-recipe-limits). [Listing][listed] · [Browser entries][browser] · [Client request][client] · [Server handling][server]

**Normal tools cannot recover placed Limestone through the bundled mining tags.** Read the [mining warning](../blocks/Limestone.md#standard-tool-mining) before placing a valuable supply; Silk Touch does not bypass that gate.

## Usage

Place it as a smooth decorative full block. Its current class has no cave-painting interaction. There is no checked crafting or smelting conversion from ordinary Limestone to this item. [Current block class][smooth] · [Family details](../blocks/Limestone.md#pillar-chiseled-and-smooth-blocks)

## Behavior

This is an ordinary BlockItem; successful placement uses one item in Survival and places the registered block. The shared [mining and drop limits](../blocks/Limestone.md#standard-tool-mining) still apply: a same-item loot entry does not make normal pickaxe recovery work. [Item factory][factory] · [Placement caller][placement] · [Exact loot][loot]

## Notes

The exact registered ID is **`minecraft:smooth_limestone`**. This block comes from bundled cave content integrated into MattMC. Follow the [precise family entry](../blocks/Limestone.md#smooth-limestone) for placement, water and recovery details.

Related: [Limestone family](../blocks/Limestone.md) · [Items](Items.md)

Source-reviewed at `404782796a9de2571994f6ab83f91574c387b296` on 2026-10-02. No in-game crafting, mining, placement or water test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/Blocks.java#L100-L125
[browser]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[factory]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L76-L85
[listed]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L296-L305
[loot]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/resources/data/minecraft/loot_table/blocks/smooth_limestone.json
[placement]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L83
[server]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[smooth]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/custom/SmoothLimestoneBlock.java
