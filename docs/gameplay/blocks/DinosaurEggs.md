# Placed dinosaur eggs

This guide covers the registered **Tremorsaurus Egg** and **Relicheirus Egg** blocks. They hatch their named species, unlike the current Turtle Egg placeholders returned by those animals' breeding code. [Subterranodon Egg](SubterranodonEgg.md) has its own guide.

## Obtaining and collecting

Both species-egg items are explicitly listed in Creative. With command permission, their IDs are `minecraft:tremorsaurus_egg` and `minecraft:relicheirus_egg`.

A natural-world or working breeding source for these species eggs is not established. The active mobs' egg-state methods return ordinary Turtle Eggs, so mating them is not a verified way to obtain either named egg.

If a named egg is already placed, its bundled loot table returns the egg item **only with Silk Touch**. Ordinary breaking and trampling are not safe collection methods. The tables do not copy the hatch-progress state into the dropped item; placing it again starts from the default state.

## Hatching conditions

Place the egg above a block whose state is considered **solid** by the active habitat check. This simplified rule does not require a special nest or an upstream mod habitat.

Growth uses random ticks with a **one-in-twenty chance per growth check**. The default `hatch` state progresses **0 → 1 → 2**, then a further successful check hatches the egg. This is not one check per second, and the game does not promise a fixed hatching time.

The default `needs_player` state is false. If customized to true, growth also requires a non-spectator player within 15 blocks. That growth gate is separate from ownership selection at hatch time.

Hatching removes the egg block and creates one baby of its species, with the standard negative baby-age value of 24,000 ticks. Keep space around the egg and protect it from footsteps while waiting.

## Ownership differs by species

- **Tremorsaurus:** the hatch path tames the baby to the nearest non-spectator player within **10 blocks** and orders it to sit. This uses proximity, not who placed the egg. If no eligible player is close enough, that path does not assign ownership
- **Relicheirus:** the species does not enable hatching-based taming. Being nearby does not make the baby an owned mount or follower

Read the [Tremorsaurus](../mobs/Tremorsaurus.md) and [Relicheirus](../mobs/Relicheirus.md) guides before relying on owner commands or breeding.

## Protecting the egg

The current trample check allows **players** to break these eggs; DinosaurEntity animals are explicitly excluded. A step has a one-in-one-hundred break check, and a fall has a one-in-three check. A successful trample destroys the egg without an item drop.

A non-Creative player's successful trample can also make nearby living mobs of the egg's species target that player, except a tame animal owned by the trampler. Avoid walking on an egg even when you are only testing a Creative build: Creative prevents that anger branch, not the egg-destruction check.

## Related pages

- [Tremorsaurus Egg item](../items/TremorsaurusEgg.md)
- [Relicheirus Egg item](../items/RelicheirusEgg.md)
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
