# Snow

**Snow** (`minecraft:snow`) is the item that places a stackable Snow layer. Use it for shallow ground cover; it is separate from a full Snow Block, Powder Snow and the throwable Snowball. [Registration][items] · [Placement][snow]

## Obtaining

Follow [Snow collecting and crafting](../blocks/Snow.md#collecting-and-crafting-snow) for the layer recipe and exact shovel/Silk Touch drops. A Silk Touch shovel recovers matching Snow items from stacks of one through seven layers; an eight-layer stack instead gives a Snow Block. [Layer loot][loot-snow]

## Usage

Place layers on [valid support](../blocks/Snow.md#placing-layers-and-keeping-their-support) and add more from above, up to eight in one position. Layers can disappear when support changes or block light reaches the [melting threshold](../blocks/Snow.md#snowfall-light-and-game-rules). [Layer implementation][snow]

## Related pages

- [Snow and Powder Snow](../blocks/Snow.md), [Snow Block](SnowBlock.md), [Snowball](Snowball.md), and [Snow Golem](../mobs/SnowGolem.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java
[loot-snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/snow.json
