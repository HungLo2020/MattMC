# Smooth Limestone Stairs

**Smooth Limestone Stairs** (`minecraft:smooth_limestone_stairs`) is the ordinary item form of [this Limestone-family block](../blocks/Limestone.md#smooth-limestone-stairs). Its placement and recovery limits are covered by that substantive family guide. [Exact item binding][item] · [Block registration][blocks]

## Obtaining

Request **Smooth Limestone Stairs** in MattMC's [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item), in **Survival or Creative**. This is an ordinary listed item. No crafting, smelting or stonecutting recipe, or natural supply, was found for the family in the checked bundled data; request each form separately. See [acquisition and recipe limits](../blocks/Limestone.md#inventory-access-and-recipe-limits). [Listing][listed] · [Browser entries][browser] · [Client request][client] · [Server handling][server]

**Normal tools cannot recover placed Limestone through the bundled mining tags.** Read the [mining warning](../blocks/Limestone.md#standard-tool-mining) before placing a valuable supply; Silk Touch does not bypass that gate.

## Usage

Use these smooth stairs for stairways, rooflines, seating, trim and angled details.

## Behavior

The clicked face and height choose normal or upside-down placement; nearby compatible stairs can form inner or outer corners. These stairs can be waterlogged. See the [stair placement and corner rules](../blocks/Limestone.md#stairs-and-corners). [Stair state and placement][stairs]

## Notes

The exact registered ID is **`minecraft:smooth_limestone_stairs`**. Follow the [precise family entry](../blocks/Limestone.md#smooth-limestone-stairs) for placement, water and recovery details.

Related: [Limestone family](../blocks/Limestone.md) · [Items](Items.md)

Source-reviewed at `404782796a9de2571994f6ab83f91574c387b296` on 2026-10-02. No in-game crafting, mining, placement or water test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/Blocks.java#L100-L125
[browser]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[item]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L76-L85
[listed]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L296-L305
[server]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[stairs]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
