# Tropical Fish

The loose **Tropical Fish item** is food. It does not retain the colors of a living [Tropical Fish](../mobs/TropicalFish.md); use a [Bucket of Tropical Fish](BucketOfTropicalFish.md) to transport a living specimen with its appearance. Its ID is `minecraft:tropical_fish`. [Item registration][food-items] · [Death item][loot-tropical_fish] · [Bucket appearance][tropical-components]

## Obtaining

A Tropical Fish mob drops **one** with mob loot enabled. The item also appears in the [Fishing](../mechanics/Fishing.md) fish-loot subtable. [Death table][loot-tropical_fish] · [Fishing subtable][fishing-fish]

It is ordinarily listed in the [inventory browser](../mechanics/InventoryBrowser.md), allowing insertion in Survival and Creative separately from those loot routes. [Listing][food-list] · [Client][browser-client] · [Server][browser-server]

## Usage

Eat it for **1 food point** and **0.2 saturation**. It is also included in the accepted foods for a tamed [Nautilus](../mobs/Nautilus.md#taming-and-feeding); the companion guide explains feeding priorities and breeding. [Food properties][food-values] · [Saturation calculation][food-math] · [Food tag][nautilus-food] · [Loose fish members][fish-tag]

## Behavior

The loose item has ordinary food behavior and no fish-release interaction. A live fish bucket stores separate pattern and color components and follows a different use path. [Registration][food-items] · [Bucket components][tropical-components] · [Release][bucket-release]

## Notes

Do not use this loose item as a substitute for the bucket when collecting aquarium fish.

Related: [Tropical Fish mob](../mobs/TropicalFish.md) · [Bucket of Tropical Fish](BucketOfTropicalFish.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked the loose item against the live bucket, food values, mob/fishing loot, Nautilus food tag and browser listing. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[food-items]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/Items.java#L1667-L1672
[loot-tropical_fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/tropical_fish.json
[tropical-components]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java#L165-L213
[fishing-fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[food-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1728-L1729
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[food-values]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/food/Foods.java#L34-L44
[nautilus-food]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/item/nautilus_food.json
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50

[food-math]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32

[fish-tag]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/item/fishes.json
