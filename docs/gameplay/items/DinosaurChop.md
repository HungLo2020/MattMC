# Dinosaur Chop

Dinosaur Chop (`minecraft:dinosaur_chop`) is a **non-stackable, placeable food block item**. It has no ordinary food component for eating directly from the inventory. Place it, then interact with an empty hand while hungry to eat from the block.

## Obtaining and using

The ordinary category-listed item can be supplied by the [inventory browser](../mechanics/InventoryBrowser.md) in Creative, or by a command with permission. A bundled crafting or natural-loot source for the initial raw chop was not established: do not assume that registered dinosaurs drop one. See the [acquisition evidence](../blocks/DinosaurChop.md#obtaining-and-keeping-a-chop).

The placed chop supplies **four servings**, each restoring **3 hunger points and 1.2 saturation** before normal caps. The last serving leaves a Thin Bone block. Breaking the placed chop does not return the food, because its bundled block loot table is empty.

Cook one raw item into one [Cooked Dinosaur Chop](CookedDinosaurChop.md) in a furnace (200 ticks), smoker (100 ticks), or campfire (600 ticks). A placed raw chop has a separate random-tick heat conversion that preserves existing bites. See the [Dinosaur Chop block guide](../blocks/DinosaurChop.md) for cooking conditions, placement, waterlogging, and collection limitations.

## Sources and verification

Source-reviewed on 2026-10-01 at `b81c01943c9f3254e713c365a1dd633392929cb2`; no gameplay test. [Block-item registration and stack limit](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1715-L1716), [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1739-L1740), and [block behavior/recipe sources](../blocks/DinosaurChop.md#sources-and-verification).

Related: [Cooked Dinosaur Chop](CookedDinosaurChop.md) · [Thin Bone](ThinBone.md) · [Items](Items.md)
