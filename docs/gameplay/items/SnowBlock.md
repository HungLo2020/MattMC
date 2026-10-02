# Snow Block

A **Snow Block** (`minecraft:snow_block`) is the full solid snow-building block. It differs from the thin [Snow layers](Snow.md) left by a Snow Golem and from Powder Snow.

## Crafting and collecting

Craft **one Snow Block from four Snowballs** in a 2 × 2 square. Two blocks supply the body of one [Snow Golem](../mobs/SnowGolem.md#build-a-snow-golem).

Use a **usable shovel** to harvest a placed Snow Block: it requires the correct tool and belongs to the shovel-mining tag. Without Silk Touch, its ordinary loot gives **four Snowballs**; a correct Silk Touch tool preserves **one Snow Block**. Breaking it by hand is not an equivalent collection route.

## Use

Place it as a building block or use two full blocks in the Snow Golem pattern. Snow layers, even when visually deep, and Powder Snow do not match that pattern's required block ID. See the [Snow Golem guide](../mobs/SnowGolem.md) for head placement, care, Snow trails, and environmental hazards.

## Related pages

- [Snowball](Snowball.md), [Snow](Snow.md), and [Carved Pumpkin](CarvedPumpkin.md)
- [Snow Golem](../mobs/SnowGolem.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Block/item registration, recipe, tool gate, loot alternatives, and golem ingredient identity were checked. No gameplay crafting, harvesting, or construction test was run.

- [Block registration and correct-tool requirement](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/snow_block.json)
- [Shovel mining tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/shovel.json)
- [Snow Block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/snow_block.json)
- [Exact golem body predicate](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java)
- [Player harvesting gate](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
