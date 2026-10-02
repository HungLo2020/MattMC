# Snow Block

A **Snow Block** (`minecraft:snow_block`) is the full solid snow-building block. It differs from stackable [Snow layers](Snow.md) and bucket-carried Powder Snow. [Registration][blocks] [items]

## Crafting and collecting

See [Snow collecting and crafting](../blocks/Snow.md#collecting-and-crafting-snow) for the four-Snowball recipe and layer recipe. Use an unbroken shovel to collect a placed Snow Block: without Silk Touch it gives **four Snowballs**, while a correct Silk Touch shovel gives **one Snow Block**. Breaking by hand does not collect it. [Loot][loot-snow-block] · [Tool requirement][blocks] [shovel] [player-tool] [broken-tool]

## Use

Use it for solid construction, including [bright rooms where Snow layers would melt](../blocks/Snow.md#snowfall-light-and-game-rules). Two full blocks also form the body of a [Snow Golem](../mobs/SnowGolem.md#build-a-snow-golem). Snow layers, even an eight-layer placed stack, and Powder Snow do not match that body's required block ID. [Full-block registration][blocks] · [Exact golem pattern][pumpkin]

## Related pages

- [Snow and Powder Snow](../blocks/Snow.md), [Snowball](Snowball.md), [Snow](Snow.md), and [Carved Pumpkin](CarvedPumpkin.md)
- [Snow Golem](../mobs/SnowGolem.md) and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. Registration, harvesting rules and the Snow Golem body predicate were checked. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[loot-snow-block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/snow_block.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[player-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java
