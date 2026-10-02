# Piglin Head

**Piglin Head** (`minecraft:piglin_head`) is a charged-Creeper trophy, wearable head-slot item, and placeable decoration. Both standing and wall forms have ears that animate while powered. [Item registration][items] · [Reward][charged-piglin] · [Animated kinds][power]

## Obtaining

The verified mob route requires a charged [Creeper](../mobs/Creeper.md) to kill an ordinary [Piglin](../mobs/Piglin.md), with mob loot enabled and the Creeper's special-head allowance unused. The table gives one Piglin Head and consumes that allowance; several eligible victims in one explosion do not each award a special head. [Killer callback][creeper] · [Mob-loot gate][mob-loot] · [Reward table][charged-piglin]

The special victim check is **`minecraft:piglin`**. Piglin Brutes and Zombified Piglins are not substitutes in this table. The charged-Creeper route does not require the player to deliver the final hit. [Victim selection][charged-root]

## Usage

Place the head for decoration, then supply redstone power to animate its ears. Put the **standing form above a Note Block** for the Piglin imitation sound. See [Heads and Skulls](../blocks/HeadsAndSkulls.md#powered-animation) for animation and [Note Block sounds](../blocks/HeadsAndSkulls.md#note-block-sounds) for playback, including the wall-form limitation. [Power and ticker][power] · [Ear animation][piglin-animation]

To wear it, move it into the inventory's head equipment slot. Held use in the air does not quick-swap it into place. Its visibility calculation halves the visibility factor used against **Piglins and Piglin Brutes**; this does not make the wearer invisible or establish that those mobs will be peaceful. [Equipment registration][items] · [Held-use rule][head-use] · [Matched-mob visibility][visibility]

## Behavior

One Piglin Head item places `minecraft:piglin_head` or `minecraft:piglin_wall_head`. Both normally drop the same inventory item, retaining a supplied custom name. A particular tool or Silk Touch is not required. Follow the [placed-family guide](../blocks/HeadsAndSkulls.md#breaking-and-retained-data) for support, water, collection, and piston rules. [Item pairing][paired] · [Block properties][blocks] · [Harvest test][harvest] · [Loot][loot-piglin_head] · [Wall loot alias][wall-properties]

## Notes

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. No in-game acquisition, equipment, visibility, animation, placement, or sound test was run. Data packs and game rules can alter the described drop route.

Related: [Heads and Skulls](../blocks/HeadsAndSkulls.md) · [Piglin](../mobs/Piglin.md) · [Creeper](../mobs/Creeper.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2063-L2097
[charged-piglin]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/piglin.json#L1-L16
[power]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/AbstractSkullBlock.java#L38-L80
[creeper]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L170-L179
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[charged-root]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json#L1-L82
[piglin-animation]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/model/PiglinHeadModel.java#L28-L35
[head-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Item.java#L173-L188
[visibility]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L902-L914
[paired]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L23-L52
[blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L2740-L2803
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot-piglin_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/piglin_head.json
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
