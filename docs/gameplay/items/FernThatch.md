# Fern Thatch

**Fern Thatch** (`minecraft:fern_thatch`) is a full green building block, not a soil-dependent plant. It is also explicitly listed in Natural Blocks Creative. [Registration][blocks] [items] [creative]

## Obtaining

Use the [Fern Thatch recipe](../blocks/FloodBasaltAndFernThatch.md#fern-thatch-crafting-and-collecting): four Fern/Large Fern ingredients in a 2 × 2 square produce one block, and those two ingredients can be mixed. Ordinary harvesting returns one Fern Thatch even by hand; Silk Touch is unnecessary and Fortune adds nothing. [Recipe/tag][thatch-recipe] [ferns] · [Loot and tool requirement][loot-fern-thatch] [blocks]

## Usage

Place it as a wall, roof or floor. It has full collision, no directional state and no Bone Meal growth behavior. Follow [building with thatch](../blocks/FloodBasaltAndFernThatch.md#building-with-thatch) for the placed properties and a small example. [Plain block registration][blocks] [properties]

## Related pages

- [Flood Basalt and Fern Thatch](../blocks/FloodBasaltAndFernThatch.md), [Fern](Fern.md), [Large Fern](LargeFern.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[thatch-recipe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/recipe/fern_thatch.json
[ferns]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/item/ferns.json
[loot-fern-thatch]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/fern_thatch.json
[properties]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
