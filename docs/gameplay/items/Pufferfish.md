# Pufferfish

The loose **Pufferfish item** is a brewing ingredient and Nautilus taming food. Eating it applies several harmful effects. It is different from the living [Pufferfish mob](../mobs/Pufferfish.md) and the [Bucket of Pufferfish](BucketOfPufferfish.md). Its ID is `minecraft:pufferfish`. [Item registration][food-items] · [Consumption][puffer-food]

## Obtaining

A [Pufferfish](../mobs/Pufferfish.md#drops) drops one with mob loot enabled. [Fishing](../mechanics/Fishing.md) also has Pufferfish in its fish-loot subtable; use that guide for fishing conditions rather than treating the subtable weight as an overall catch probability. [Death table][loot-pufferfish] · [Fish subtable][fishing-fish]

It is also an ordinary item in the [inventory browser](../mechanics/InventoryBrowser.md), available through insertion in Creative. [Listing][food-list] · [Client][browser-client] · [Server][browser-server]

## Usage

- Brew an **Awkward Potion with Pufferfish** to make Water Breathing; follow [Brewing](../brewing/Brewing.md) for the apparatus and potion workflow. [Active mixture][puffer-brew]
- Use it for [Nautilus taming and feeding](../mobs/Nautilus.md#taming-and-feeding). [Taming tag][nautilus-tame] · [Food tag][nautilus-food] · [Loose fish members][fish-tag]

## Behavior

Eating one supplies **1 food point** and **0.2 saturation**, but applies **Poison II for 60 seconds, Hunger III for 15 seconds, and Nausea I for 15 seconds** to a susceptible player. These are eating effects; the living fish's sting applies the separate Poison I contact effect. Use [Poison](../effects/Poison.md) and [Milk Bucket](MilkBucket.md) for treatment details. [Food properties][food-values] · [Saturation calculation][food-math] · [Consumption effects][puffer-food] · [Mob sting][puff-contact]

## Notes

The loose item cannot be released as a living fish. Capture the animal with a Water Bucket when you want to transport it alive. [Capture][bucket-capture]

Related: [Pufferfish mob](../mobs/Pufferfish.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked item/consumable registration, fish loot, active brewing mixture, Nautilus tags and browser listing; potion and companion workflows remain with their guides. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[food-items]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/Items.java#L1667-L1672
[puffer-food]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/component/Consumables.java#L49-L57
[loot-pufferfish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/pufferfish.json
[fishing-fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[food-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1728-L1729
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[puffer-brew]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L168-L168
[nautilus-tame]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/item/nautilus_taming_items.json
[nautilus-food]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/item/nautilus_food.json
[food-values]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/food/Foods.java#L34-L44
[puff-contact]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Pufferfish.java#L123-L152
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93

[food-math]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32

[fish-tag]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/item/fishes.json
