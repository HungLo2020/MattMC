# Thin Bone

## Obtaining

Thin Bone (`minecraft:thin_bone`) is a registered block item in ordinary Natural Blocks. MattMC's [inventory browser](../mechanics/InventoryBrowser.md) can supply it in Creative. No bundled crafting recipe produces or consumes it. [Item](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/Items.java#L770) · [Category](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L805)

Eating the fourth serving of a [Dinosaur Chop](../blocks/DinosaurChop.md#the-leftover-thin-bone) leaves a placed Thin Bone, but does not award its item. The block requires a correct tool and lacks a standard bundled mining-tool assignment, so do not rely on ordinary mining to recover that remnant. The linked guide traces the separate tool and loot checks.

## Usage

Place it as a rotated pillar; its axis follows the clicked face. Its registered class uses full-block collision, not a special thin collision shape. [Placed behavior and source evidence](../blocks/DinosaurChop.md#the-leftover-thin-bone)

## Behavior

The name does not make this the loose Bone item or establish a Bone Meal recipe. The reviewed bundle has no Thin Bone recipe or food-use component. Treat it as a placed decorative block, with the recovery limitation above.

## Notes

Source-reviewed at `b153e7232bbb43920a8694afbdb0053c2e219d77` on 2026-10-02. No placement, eating or harvesting gameplay test was run. [Dinosaur Chop family](../blocks/DinosaurChop.md) · [Items](Items.md)
