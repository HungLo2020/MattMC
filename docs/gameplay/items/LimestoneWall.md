# Limestone Wall

**Limestone Wall** (`minecraft:limestone_wall`) is the ordinary item form of [this Limestone-family block](../blocks/Limestone.md#limestone-wall). Its placement and recovery limits are covered by that substantive family guide. [Exact item binding][item] · [Block registration][blocks]

## Obtaining

Request **Limestone Wall** in MattMC's [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item), in **Survival or Creative**. This is an ordinary listed item. No crafting, smelting or stonecutting recipe, or natural supply, was found for the family in the checked bundled data; request each form separately. See [acquisition and recipe limits](../blocks/Limestone.md#inventory-access-and-recipe-limits). [Listing][listed] · [Browser entries][browser] · [Client request][client] · [Server handling][server]

**Normal tools cannot recover placed Limestone through the bundled mining tags.** Read the [mining warning](../blocks/Limestone.md#standard-tool-mining) before placing a valuable supply; Silk Touch does not bypass that gate.

## Usage

Use these walls for posts, edging and decorative stone detail; check their connection limits before planning a barrier.

## Behavior

**Do not assume ordinary wall connections.** Both Limestone wall items place walls missing from the bundled walls tag: they do not form joining arms with each other, and connections beside tagged stone walls can be asymmetric. These are source-predicted results; see the [connection warning](../blocks/Limestone.md#walls-and-the-missing-wall-tag) before building a pen. The form can be waterlogged. [Wall callback][wall] · [Actual walls tag][wall-tag]

## Notes

The exact registered ID is **`minecraft:limestone_wall`**. Follow the [precise family entry](../blocks/Limestone.md#limestone-wall) for placement, water and recovery details.

Related: [Limestone family](../blocks/Limestone.md) · [Items](Items.md)

Source-reviewed at `404782796a9de2571994f6ab83f91574c387b296` on 2026-10-02. No in-game crafting, mining, placement or water test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/Blocks.java#L100-L125
[browser]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[item]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L76-L85
[listed]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L296-L305
[server]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[wall]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/WallBlock.java#L54-L192
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/resources/data/minecraft/tags/block/walls.json
