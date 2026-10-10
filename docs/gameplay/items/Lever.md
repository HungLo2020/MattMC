# Lever

The **Lever** item places a switch that holds its selected on/off state. Both the item and placed block use `minecraft:lever`. See the [canonical Lever block guide](../blocks/Lever.md) for support, output direction, and environmental interactions. [Item registration][items]

## Crafting and use

Craft **one Lever** with **one Stick directly above one Cobblestone**. The vertical recipe fits the inventory crafting grid and names Cobblestone specifically; other stone-crafting materials are not substitutions. Ordinary Survival breaking also returns one Lever, without Silk Touch or a required tool tier. [Recipe][recipe] · [Block loot][loot] · [Current physical properties][physics]

Place it on a supported floor, wall, or ceiling face, then use the placed switch to toggle it. It supplies strength **15 while on**, with no automatic release timer. For a straightforward toggle, release Sneak; secondary use with an item in either hand can bypass the normal block interaction. [Placement][attachment] · [Switching][lever] · [Interaction handling][use]

## Related pages

- [Lever block behavior](../blocks/Lever.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Item/block registration, recipe, loot, current native properties, and active placement/use paths were checked. No crafting, placement, or circuit gameplay test was run; the block guide owns the detailed mechanics.

[items]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L1019
[recipe]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/recipe/crafting/lever.json
[loot]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/loot_table/blocks/lever.json
[physics]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/physics.rs#L337-L343
[attachment]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java
[lever]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/LeverBlock.java
[use]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L396
