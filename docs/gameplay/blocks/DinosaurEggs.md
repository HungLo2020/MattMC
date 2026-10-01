# Placed dinosaur eggs

This guide covers the registered **Tremorsaurus**, **Relicheirus**, **Grottoceratops**, and **Vallumraptor Egg** blocks. They hatch their named species, unlike the Turtle Egg or Dragon Egg placeholders returned by those animals' current breeding code. [Subterranodon Egg](SubterranodonEgg.md) has its own guide.

## Obtaining and collecting

All four named egg items are explicitly listed in Creative. Their IDs are `minecraft:tremorsaurus_egg`, `minecraft:relicheirus_egg`, `minecraft:grottoceratops_egg`, and `minecraft:vallumraptor_egg`.

A natural-world or working breeding source for these species eggs is not established. Tremorsaurus, Relicheirus, and Grottoceratops return Turtle Eggs; Vallumraptor returns a Dragon Egg. Mating them is not a verified way to obtain their named eggs.

If a named egg is already placed, its bundled loot table returns the egg item **only with Silk Touch**. Ordinary breaking and trampling are not safe collection methods. The tables do not copy the hatch-progress state into the dropped item; placing it again starts from the default state.

## Hatching conditions

Place the egg above a block whose state is considered **solid** by the active habitat check. This simplified rule does not require a special nest or an upstream mod habitat.

Growth uses random ticks with a **one-in-twenty chance per growth check**. The default `hatch` state progresses **0 → 1 → 2**, then a further successful check hatches the egg. This is not one check per second, and the game does not promise a fixed hatching time.

The default `needs_player` state is false. If customized to true, growth also requires a non-spectator player within 15 blocks. That growth gate is separate from ownership selection at hatch time.

Hatching removes the egg block. Tremorsaurus, Relicheirus, and Grottoceratops produce one baby; a Vallumraptor cluster produces its stored count of babies. Each starts with the standard negative baby-age value of 24,000 ticks. Keep space around the egg and protect it from footsteps while waiting.

## Vallumraptor clusters

Vallumraptor Eggs can share a block in a cluster of **one to four**. Using the same egg item on a non-full cluster increases its count. The current class removes one egg at a time on its break/trample path, destroying the block once none remain. Its Silk Touch loot entry produces one egg per loot evaluation rather than packaging the whole cluster into one item. Remaining cluster state and hatching should still be tested before relying on a valuable setup.

## Ownership differs by species

- **Tremorsaurus and Vallumraptor:** the hatch path tames the baby to the nearest non-spectator player within **10 blocks** and orders it to sit. This uses proximity, not who placed the egg. If no eligible player is close enough, that path does not assign ownership
- **Relicheirus and Grottoceratops:** these species do not enable hatching-based taming. Being nearby does not make the baby an owned mount or follower

Read the [Tremorsaurus](../mobs/Tremorsaurus.md) and [Relicheirus](../mobs/Relicheirus.md) guides before relying on owner commands or breeding.

## Protecting the egg

The current trample check allows **players** to break these eggs; DinosaurEntity animals are explicitly excluded. A step has a one-in-one-hundred break check, and a fall has a one-in-three check. A successful trample removes an egg without an item drop; a multiple-egg Vallumraptor cluster can retain its remaining eggs.

A non-Creative player's successful trample can also make nearby living mobs of the egg's species target that player, except a tame animal owned by the trampler. Avoid walking on an egg even when you are only testing a Creative build: Creative prevents that anger branch, not the egg-destruction check.

## Related pages

- [Tremorsaurus Egg item](../items/TremorsaurusEgg.md)
- [Relicheirus Egg item](../items/RelicheirusEgg.md)
- [Grottoceratops Egg item](../items/GrottoceratopsEgg.md)
- [Vallumraptor Egg item](../items/VallumraptorEgg.md)
- [Subterranodon Egg](SubterranodonEgg.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game hatching, ownership, collection, trampling, or breeding test was run. Data packs and modified block states can change these conditions.

- [Shared egg growth, hatching, ownership, and trampling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Tremorsaurus egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/TremorsaurusEggBlock.java)
- [Relicheirus egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/RelicheirusEggBlock.java)
- [Block registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Tremorsaurus egg loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/tremorsaurus_egg.json)
- [Relicheirus egg loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/relicheirus_egg.json)
- [Tremorsaurus ownership and breeding placeholder](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java)
- [Relicheirus breeding placeholder](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/RelicheirusEntity.java)

- [Multiple-egg placement, removal, and hatch count](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/MultipleDinosaurEggsBlock.java)
- [Vallumraptor egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/VallumraptorEggBlock.java)
- [Grottoceratops egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/GrottoceratopsEggBlock.java)
- [Vallumraptor egg loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/vallumraptor_egg.json)
- [Grottoceratops egg loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/grottoceratops_egg.json)
