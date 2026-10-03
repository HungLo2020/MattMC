# Limestone

Limestone is the placeable item for the [Limestone building family](../blocks/Limestone.md#limestone), registered as `minecraft:limestone`.

## Obtaining and use

Request **Limestone** through the [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) in **Creative**. It is an ordinary listed item. No crafting, smelting or stonecutting recipe, or naturally generated supply, was found in the checked bundled data; see [acquisition and recipe limits](../blocks/Limestone.md#inventory-access-and-recipe-limits). [Listed item][listed] · [Browser assembly][browser] · [Client request][client] · [Server request][server]

Normal tools cannot recover it through the bundled mining tags. The old assumption that any stone-like block can simply be mined with a pickaxe does not substitute for this block's missing tool-tag wiring. Read the [standard-tool mining warning](../blocks/Limestone.md#standard-tool-mining) before placing it.

Place it for full-block construction. No separate item-only action is registered here: the ordinary BlockItem caller places the registered block and consumes one item on successful Survival placement. Its loot table has one matching-item entry, conditional on reaching the loot path and passing its explosion-survival condition; this does not promise recovery with a normal pickaxe. [Item binding][item] · [Item factory][factory] · [Placement][placement] · [Exact loot][loot]

The family guide documents the stairs, slabs, walls, smooth, pillar and chiseled forms and their current limitations. Use the [precise Limestone entry](../blocks/Limestone.md#limestone) to compare them.

## Related pages

- [Limestone block family](../blocks/Limestone.md#limestone)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `404782796a9de2571994f6ab83f91574c387b296` on 2026-10-02. Existing full-block guidance and the missing-tool-tag warning were retained; browser access, exact item routing and conditional loot were refreshed. No in-game placement, harvesting or crafting test was run.

[browser]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[factory]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/Items.java#L76-L85
[listed]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L296-L305
[loot]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/resources/data/minecraft/loot_table/blocks/limestone.json
[placement]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L83
[server]: https://github.com/HungLo2020/MattMC/blob/404782796a9de2571994f6ab83f91574c387b296/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
