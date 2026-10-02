# Ink Sac

**Ink Sacs** are a crafting resource and remove glow from sign text. They are distinct from both [Black Dye](BlackDye.md) and [Glow Ink Sacs](GlowInkSac.md). Their ID is `minecraft:ink_sac`. [Item registration][ink-items] · [Sign action][ink-use]

## Obtaining

Adult [Squid](../mobs/Squid.md#drops) drop **1–3**, with Looting bonuses; babies do not supply normal death loot. See that mob guide for conditions. The [Fishing guide](../mechanics/Fishing.md) covers the separate fishing-loot route. [Squid table][loot-squid] · [Age and mob-loot gates][baby-loot] · [Fishing junk entry][fishing-junk]

Ink Sacs are also ordinary listed items in the [inventory browser](../mechanics/InventoryBrowser.md), available through insertion in Survival and Creative. [Listing][ink-list] · [Client][browser-client] · [Server][browser-server]

## Usage

- Craft **1 Ink Sac into 1 Black Dye**. Recipes that require Black Dye do not automatically accept an Ink Sac. [Dye recipe][black-dye]
- Combine **1 Book, 1 Ink Sac and 1 Feather** to make **1 Book and Quill**, in any arrangement. [Recipe][book-quill]
- Use an Ink Sac to remove glow from the selected face of a sign. Follow [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) for eligible text, waxing and consumption rules. [Sign action][ink-use]

## Behavior

Removing sign glow retains its text and dye color; the item changes the selected face's glowing-text flag. It does not recolor that text black. [Application][ink-use]

## Notes

Ink particles squirted by a living Squid are a visual response, not collectible Ink Sac items. [Squirt code][squid-ink]

Related: [Squid](../mobs/Squid.md) · [Glow Ink Sac](GlowInkSac.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked registration, Squid loot, the two listed recipes, sign application and ordinary browser listing; sign workflow remains on Signs. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[ink-items]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/Items.java#L1692-L1693
[ink-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/InkSacItem.java#L9-L21
[loot-squid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/squid.json
[baby-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[ink-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1831-L1832
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[black-dye]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/recipe/crafting/black_dye.json
[book-quill]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/recipe/crafting/writable_book.json
[squid-ink]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L174-L203

[fishing-junk]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/gameplay/fishing/junk.json#L81-L90
