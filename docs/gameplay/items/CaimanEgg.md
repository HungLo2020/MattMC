# Caiman Egg

A **Caiman Egg** is the placeable item for `minecraft:caiman_egg`. It is separate from the [Caiman Spawn Egg](CaimanSpawnEgg.md), which creates a mob directly. [Registration][items]

## Obtaining

Its ordinary category entry is available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. A normal Survival egg-producing loop is not established because the Caiman breeding-food tag has no bundled definition. See the [Caiman Egg obtaining and hatching guide](../blocks/AnimalEggs.md#caiman-eggs) for the conditional laying route and collection limits. [Creative listing][creative]

## Usage

Use it to place a Caiman Egg block or add an egg to a cluster of fewer than four. The block guide owns [ground requirements, hatch timing, baby counts, and ownership](../blocks/AnimalEggs.md#caiman-eggs). [Block-item registration][items] · [Placement][caiman-growth]

## Behavior

This is a registered block item, not a throwable spawn egg or food. Do not assume Silk Touch will recover it: no bundled Caiman Egg block-loot table was found. Keep players off the placed cluster. [Block-item factory][block-items] · [Protection and recovery](../blocks/AnimalEggs.md#caiman-eggs)

## Notes

- Item and placed-block ID: `minecraft:caiman_egg`
- [Placed Caiman Egg guide](../blocks/AnimalEggs.md#caiman-eggs)
- [Caiman](../mobs/Caiman.md)
- [Items](Items.md)

Source-reviewed at `099b1184d1a7115915d880b436c8f58c1ed5a422` on 2026-10-02. No in-game acquisition, breeding, collection, or hatching test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L872-L875
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L949-L952
[caiman-growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L120-L148
[block-items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L2750-L2761
