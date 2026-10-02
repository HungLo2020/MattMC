# Carved Pumpkin

A **Carved Pumpkin** (`minecraft:carved_pumpkin`) is the carved head block used in golem construction and a registered head-slot item.

## Obtaining

Use Shears on a placed ordinary Pumpkin. The existing [Pumpkin carving guide](../blocks/PumpkinAndMelon.md#carving-a-pumpkin) owns that interaction, its seed output, and MattMC's worn-Shears distinction. Breaking a placed Carved Pumpkin normally returns it; no correct tool is required, though an axe is the tagged mining tool.

[Shearing a Snow Golem](../mobs/SnowGolem.md#shearing-and-preserving-it) is another checked route to one Carved Pumpkin.

## Golem construction

Place it last on the completed body for an [Iron Golem](../mobs/IronGolem.md#build-an-iron-golem) or [Snow Golem](../mobs/SnowGolem.md#build-a-snow-golem). The mob guides own the exact layouts and material consumption. Its facing does not change those two pattern matches.

Carving a Pumpkin already sitting on a matching body also replaces it with the head block and can complete the pattern. Keep that in mind if you intended to collect the carved head rather than summon a golem.

Both patterns also accept a Jack o'Lantern. Craft one by placing a Carved Pumpkin directly above an ordinary Torch; this produces one Jack o'Lantern.

## Related pages

- [Pumpkin](Pumpkin.md) and [Jack o'Lantern](JackOLantern.md)
- [Iron Golem](../mobs/IronGolem.md) and [Snow Golem](../mobs/SnowGolem.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registration, carving route, block loot, golem patterns, and Jack o'Lantern recipe were checked. No gameplay test was run.

- [Item registration and head-slot component](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Carving block replacement](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PumpkinBlock.java)
- [Golem pattern checks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java)
- [Mining properties](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Axe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/axe.json)
- [Carved Pumpkin loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/carved_pumpkin.json)
- [Jack o'Lantern recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/jack_o_lantern.json)
