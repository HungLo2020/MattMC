# End Portal Frame

End Portal Frame is the inventory item `minecraft:end_portal_frame`, used to place the frame block. The [End portal and frame guide](../blocks/EndPortals.md#end-portal-frame) owns its layout, facing, eye state, comparator output, activation, and travel rules. [Item registration][item]

## Obtaining

The frame appears in the ordinary Functional Blocks category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can therefore supply it **in Creative**, subject to the browser's feature, cursor, and inventory-space checks. It is not restricted to the permission-gated operator category. A permitted `give` command is another route. **There is no bundled crafting recipe or ordinary Survival mining route.** The placed frame has hardness −1 and no loot table, so Silk Touch does not make it collectible. For a structure-based route, use frames generated in a [Stronghold](../structures/Stronghold.md). [Functional category][frame-category] · [Frame listing][creative] · [Browser list][browser-list] · [Client request][browser-client] · [Server insertion][browser-server] · [Command permission][give] · [Block registration][block] · [Mining rule][mining] · [Generated frames][stronghold]

## Usage

Place frames facing inward around a 3×3 opening, with three frames on each side. New frames start without an eye and face opposite the player's horizontal placement direction. The [full layout and activation instructions](../blocks/EndPortals.md#ring-layout-and-activation) explain the 12-frame ring and its optional corners. [Placement][placement] · [Pattern][pattern]

## Behavior

Use an [Eye of Ender](EyeOfEnder.md#filling-portal-frames) on an empty frame to fill it. Once the correctly facing ring is complete, activation replaces the nine interior cells with End portal blocks. Keep that opening clear of belongings. A comparator reads 0 from an empty frame and 15 from a filled one. [Insertion][eye] · [Comparator][placement]

## Notes

This is the frame item; it is not an End Portal or End Gateway item. Those travel blocks have no matching item registrations. [Item registry][items]

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. No in-game browser request, collection, placement, activation, or command test was run. Bundled recipes were checked for outputs; custom recipes can change item availability.

Related: [Placed frames and portals](../blocks/EndPortals.md) · [Eye of Ender](EyeOfEnder.md) · [End](../dimensions/End.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L583
[creative]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1259-L1263
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java#L25-L35
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2544-L2565
[mining]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L347-L357
[stronghold]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L814-L848
[placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L25-L66
[pattern]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L83-L115
[eye]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/EnderEyeItem.java#L35-L67
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[frame-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1279
[browser-list]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
