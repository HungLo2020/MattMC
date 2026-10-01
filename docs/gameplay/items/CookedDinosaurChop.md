# Cooked Dinosaur Chop

Cooked Dinosaur Chop (`minecraft:cooked_dinosaur_chop`) is a **non-stackable, placeable food block item**, not a directly consumable inventory food. It is explicitly listed in Creative and can be made by cooking an existing [Dinosaur Chop](DinosaurChop.md).

## Cooking and eating

One raw chop becomes one cooked chop in a furnace (**200 ticks**), smoker (**100 ticks**), or campfire (**600 ticks**). These correspond to 10, 5, and 30 seconds at 20 TPS. The recipes specify 0.15 experience; campfire completion does not award that recipe XP. These recipes do not establish a Survival source for the first raw chop.

Place the cooked chop, then interact with an empty hand while hungry. A fresh block has **four servings**, each restoring **7 hunger points and 4.9 saturation**, subject to the normal caps. The final serving leaves a Thin Bone block. The empty block loot table means breaking it does not recover the food, even with Silk Touch.

A raw chop can also cook while placed above accepted heat, retaining any bites already taken. Full details, including the waterlogging and inactive comparator-helper limitations, are in the [Dinosaur Chop block guide](../blocks/DinosaurChop.md).

## Sources and verification

Source-reviewed on 2026-10-01 at `b81c01943c9f3254e713c365a1dd633392929cb2`; no gameplay test. [Block-item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1715-L1716), [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1739-L1740), and [block behavior/recipe sources](../blocks/DinosaurChop.md#sources-and-verification).

Related: [Dinosaur Chop](DinosaurChop.md) · [Thin Bone](ThinBone.md) · [Items](Items.md)
