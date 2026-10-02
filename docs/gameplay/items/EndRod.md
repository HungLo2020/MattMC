# End Rod

An **End Rod** (`minecraft:end_rod`) is the inventory form of the slim, directional **light-level-14** block. [Item registration][item] · [Block registration][block]

## Obtaining

Follow the [End Rod crafting and collection guide](../blocks/EndRod.md#crafting-and-collecting) for the four-rod recipe and verified End City template examples. Mining a placed End Rod normally returns one item, including by hand. [Loot][loot]

## Usage

Place it on a floor, wall, or ceiling. The [block guide](../blocks/EndRod.md#facing-support-and-collision) owns its six directions, tip-to-tip placement rule, narrow collision, and ability to remain after support removal.

## Behavior

End Rods do not waterlog; advancing water replaces the placed rod and runs its drop path. Read [Water and lighting](../blocks/EndRod.md#water-and-lighting) before using them near a channel or underwater build.

## Notes

- This item is the item form of `minecraft:end_rod`
- Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no in-game placement, fluid, or collection test was run

[item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L468
[block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L4212-L4214
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/end_rod.json
