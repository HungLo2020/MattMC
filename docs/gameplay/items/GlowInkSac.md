# Glow Ink Sac

**Glow Ink Sacs** make sign text glow and craft Glow Item Frames. Their ID is `minecraft:glow_ink_sac`. [Registration][ink-items] · [Sign action][glow-ink-use] · [Frame recipe][glow-frame]

## Obtaining

Adult [Glow Squid](../mobs/GlowSquid.md#drops) drop **1–3**, with Looting bonuses; babies do not supply normal death loot. See the mob guide for the dark-water spawn conditions and exact loot rules. [Loot][loot-glow_squid] · [Baby gate][baby-loot]

They are also ordinary listed items in the [inventory browser](../mechanics/InventoryBrowser.md), available through insertion in Survival and Creative. [Listing][ink-list] · [Client][browser-client] · [Server][browser-server]

## Usage

Use one on an eligible sign face to enable glowing text. The [Signs guide](../blocks/Signs.md#dye-glow-ink-and-wax) owns the face-selection, text, waxing and consumption rules. [Application][glow-ink-use]

Combine **1 Item Frame and 1 Glow Ink Sac** in any arrangement to craft **1 Glow Item Frame**. [Recipe][glow-frame]

## Behavior

Glow Ink changes the sign face's glow flag without replacing its wording or dye color. Use an [Ink Sac](InkSac.md) to remove that glow again. [Enable glow][glow-ink-use] · [Remove glow][ink-use]

## Notes

The Glow Squid's glowing ink particles are not collectible sacs; the item comes through loot or other explicit item routes. [Particle response][squid-ink] · [Glow particle choice][GlowSquid]

Related: [Glow Squid](../mobs/GlowSquid.md) · [Glow Item Frame](GlowItemFrame.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked registered item behavior, adult loot, frame recipe and browser listing; sign interaction details remain on Signs. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[ink-items]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/Items.java#L1692-L1693
[glow-ink-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/GlowInkSacItem.java#L9-L21
[glow-frame]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/recipe/crafting/glow_item_frame.json
[loot-glow_squid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/glow_squid.json
[baby-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[ink-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1831-L1832
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[ink-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/InkSacItem.java#L9-L21
[squid-ink]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L174-L203
[GlowSquid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/GlowSquid.java
