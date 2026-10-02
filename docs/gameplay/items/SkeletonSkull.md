# Skeleton Skull

**Skeleton Skull** (`minecraft:skeleton_skull`) is a collectible decoration and head-slot item. It can come from a charged-Creeper kill or selected Ancient City pieces. One item places both the standing skull and wall skull. [Item registration][items] · [Charged reward][charged-skeleton] · [Ancient City piece pool][ancient-pool]

## Obtaining

A charged [Creeper](../mobs/Creeper.md) can drop one skull when it kills an ordinary [Skeleton](../mobs/Skeleton.md), provided mob loot is enabled and that Creeper has not already produced a special head. Its special route accepts **ordinary Skeletons**, not Strays, Bogged, or Skeleton Horses. Multiple eligible deaths in one explosion do not give one special skull per victim. [Killer callback][creeper] · [Loot gate][mob-loot] · [Exact victim types][charged-root] · [Reward][charged-skeleton]

Ancient City **barracks** and **medium-pillar** pieces also contain placed Skeleton Skulls that can be broken and collected. Both pieces are in the city's structures pool; their presence is optional. See [the family guide](../blocks/HeadsAndSkulls.md#skeleton-skulls) for generation sources. The skull is a placed decoration, not a chest-loot roll. [Barracks template][ancient-barracks] · [Pillar template][ancient-pillar] · [Piece pool][ancient-pool]

## Usage

Use the item to decorate with `minecraft:skeleton_skull` or `minecraft:skeleton_wall_skull`. Put a **standing skull above a Note Block** for the Skeleton imitation sound; the family guide explains [sound behavior and the wall-form limitation](../blocks/HeadsAndSkulls.md#note-block-sounds). [Item pairing][paired] · [Instrument registration][blocks]

You can wear the skull by putting it in the inventory's head equipment slot. Its matching-mob visibility calculation halves the visibility factor used against an ordinary Skeleton; this is not invisibility or immunity from attack. Ordinary held use in the air does not quick-swap the skull onto your head. [Equipment registration][items] · [Visibility calculation][visibility] · [Held-use rule][head-use]

## Behavior

Breaking either placed form normally returns one Skeleton Skull and keeps a supplied custom name. No particular tool or Silk Touch is required for that drop. Follow [Heads and Skulls](../blocks/HeadsAndSkulls.md#placement-support-and-water) for rotation, support removal, and water behavior. [Loot][loot-skeleton_skull] · [Wall loot alias][wall-properties] · [Block properties][blocks] · [Harvest test][harvest]

## Notes

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. No in-game charged-Creeper, Ancient City, collection, visibility, equipment, or sound test was run. Game rules, data packs, and already looted structures can change the result.

Related: [Heads and Skulls](../blocks/HeadsAndSkulls.md) · [Skeleton](../mobs/Skeleton.md) · [Creeper](../mobs/Creeper.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2063-L2097
[charged-skeleton]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/skeleton.json#L1-L16
[ancient-pool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json
[creeper]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L170-L179
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[charged-root]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json#L1-L82
[ancient-barracks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[ancient-pillar]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/ancient_city/structures/medium_pillar_1.nbt
[paired]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L23-L52
[blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L2740-L2803
[visibility]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L902-L914
[head-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Item.java#L173-L188
[loot-skeleton_skull]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/skeleton_skull.json
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
