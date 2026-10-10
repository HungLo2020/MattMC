# Grindstone

The Grindstone item places the `minecraft:grindstone` workstation for matching-item repairs and removing non-curse enchantments.

## Obtaining

Craft one from two Sticks, one Stone Slab, and two planks-tag items: Stick–slab–Stick above Plank–empty–Plank. A pickaxe collects the placed block; its correct-tool requirement applies.

## Use

Place and interact with it to open the two-input menu. Repairing matching equipment also strips useful enchantments, while curses remain. There is no level payment, and removed non-curse enchantments can return experience points.

Read the [Grindstone block guide](../blocks/Grindstone.md) before sacrificing enchanted gear. For preserving and combining enchantments or renaming, use an [Anvil](../blocks/Anvil.md).

Use the block guide's [input troubleshooting](../blocks/Grindstone.md#if-an-input-or-output-is-missing) before adding a second item: two Enchanted Books cannot be processed together, while two fully repaired matching damageable items can still consume both inputs. Creative also consumes committed inputs. [Menu validation and matching items][current-menu] · [Consumption][current-use]

The placed block is a **Weaponsmith job site**. It has no hopper-accessible menu inventory or Comparator readout, and pistons cannot move it; see [automation and redstone](../blocks/Grindstone.md#automation-and-redstone) and [Weaponsmith job site](../blocks/Grindstone.md#weaponsmith-job-site) in the block guide.

## Related pages

- [Grindstone placement, repairs, and experience](../blocks/Grindstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game repair, disenchantment, experience, mining, or placement test was run. Enchantment and item components and data packs can change the applicable rules.

- [Menu inputs, repair, enchantments, experience, and consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java)
- [Block interaction and support behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/GrindstoneBlock.java)
- [Placement orientations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java)
- [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/grindstone.json)
- [Registration and mining requirement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Pickaxe mining tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/grindstone.json)
- [Curse tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/enchantment/curse.json)
- [Prior-work progression](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java)

The additional usage limits and routes were source-reviewed on **2026-10-10** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`; no gameplay test was run. The [block guide's sources](../blocks/Grindstone.md#sources-and-verification) cover its automation, piston and villager behavior.

[current-menu]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L122-L164
[current-use]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L66-L77
