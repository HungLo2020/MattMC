# Smooth Limestone Slab

**Smooth Limestone Slab** (`minecraft:smooth_limestone_slab`) is the ordinary item form of [this Limestone-family block](../blocks/Limestone.md#smooth-limestone-slab). Its placement and recovery limits are covered by that substantive family guide. [Exact item binding][item] · [Block registration][blocks]

## Obtaining

Request **Smooth Limestone Slab** in MattMC's [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item), in **Survival or Creative**. This is an ordinary listed item. No crafting, smelting or stonecutting recipe, or natural supply, was found for the family in the checked bundled data; request each form separately. See [acquisition and recipe limits](../blocks/Limestone.md#inventory-access-and-recipe-limits). [Listing][listed] · [Browser entries][browser] · [Client request][client] · [Server handling][server]

**Normal tools cannot recover placed Limestone through the bundled mining tags.** Read the [mining warning](../blocks/Limestone.md#standard-tool-mining) before placing a valuable supply; Silk Touch does not bypass that gate.

## Usage

Use these smooth half-height pieces for floors, paths, roofs and detail work.

## Behavior

Place it in the upper or lower half of a block space. Only another slab of the **same item** makes a double slab; ordinary and smooth Limestone Slabs do not mix. Singles can be waterlogged, while doubling clears the stored Water. **The loot table returns only one matching slab even for a double slab**, if a qualifying drop path reaches it. See [slab placement](../blocks/Limestone.md#slabs-and-double-slabs) and the [double-slab loss warning](../blocks/Limestone.md#other-removal-and-double-slab-loss). [Placement][slab] · [Exact loot][loot]

## Notes

The exact registered ID is **`minecraft:smooth_limestone_slab`**. Follow the [precise family entry](../blocks/Limestone.md#smooth-limestone-slab) for placement, water and recovery details.

Related: [Limestone family](../blocks/Limestone.md) · [Items](Items.md)

Source-reviewed at `404782796a9de2571994f6ab83f91574c387b296` on 2026-10-02. No in-game crafting, mining, placement or water test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/Blocks.java#L100-L125
[browser]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[item]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L76-L85
[listed]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L296-L305
[loot]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/resources/data/minecraft/loot_table/blocks/smooth_limestone_slab.json
[server]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[slab]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L58-L133
