# Dragon Head

**Dragon Head** (`minecraft:dragon_head`) is a trophy collected from an End Ship, a wearable head-slot item, and a placeable decoration with a powered jaw animation. Its standing and wall behavior is documented in [Heads and Skulls](../blocks/HeadsAndSkulls.md#dragon-heads). [Registration][items] · [Ship template][ship] · [Animated kinds][power]

## Obtaining

Search an [End City](../structures/EndCity.md#towers-bridges-and-ships) for a ship. The ship template has **one wall-mounted Dragon Head at its front**. Break that block to collect the ordinary Dragon Head item; no special tool or Silk Touch is required. Prepare a place to catch the drop before removing a head exposed over the void. [Ship contents][ship] · [Loot][loot-dragon_head] · [Block properties][blocks] · [Harvest test][harvest]

A city does not guarantee a ship. The End-city generator selects ships during bridge generation and loads the corresponding ship template; finding the city itself or defeating the Ender Dragon is not the head collection step. See [End City](../structures/EndCity.md) for search conditions and generation limits. [Ship selection][ship-route] · [Template loading][ship-loader]

## Usage

Place the item as a standing head or against a wall. Redstone power animates the jaw of either form. For the Ender Dragon imitation sound, put the **standing form above a Note Block** and play the Note Block; see [sound behavior and the wall-form limitation](../blocks/HeadsAndSkulls.md#note-block-sounds). [Item pairing][paired] · [Power and ticker][power] · [Jaw model][dragon-animation]

To wear the head, put it in the inventory's head equipment slot. Ordinary held use in the air does not quick-swap it into that slot. [Equipment registration][items] · [Held-use rule][head-use]

## Behavior

The item ID is `minecraft:dragon_head`; its wall block is `minecraft:dragon_wall_head`. Both return one Dragon Head under the ordinary block-loot rules and retain a supplied custom name. Placed orientation and power are not copied into the dropped item. See [breaking and retained data](../blocks/HeadsAndSkulls.md#breaking-and-retained-data). [Shared loot alias][wall-properties] · [Loot components][loot-dragon_head]

## Notes

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`, including the compressed End Ship template and its active generation path. No in-game city search, collection, animation, equipment, or sound test was run. World generation, data packs, or earlier visitors can change what a ship contains.

Related: [Heads and Skulls](../blocks/HeadsAndSkulls.md) · [End City](../structures/EndCity.md) · [Ender Dragon](../mobs/EnderDragon.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2063-L2097
[ship]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/end_city/ship.nbt
[power]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/AbstractSkullBlock.java#L38-L80
[loot-dragon_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/dragon_head.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L2740-L2803
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[ship-route]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L198-L209
[ship-loader]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L334-L360
[paired]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L23-L52
[dragon-animation]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/model/dragon/DragonHeadModel.java#L47-L51
[head-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Item.java#L173-L188
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
