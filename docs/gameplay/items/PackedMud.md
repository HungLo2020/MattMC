# Packed Mud

**Packed Mud** (`minecraft:packed_mud`) is the inventory form of the matching block. See [Mud, Packed Mud and Mud Bricks](../blocks/MudAndMudBricks.md#packed-mud) for placed behavior and the shared production chain.

## Obtaining

Craft Packed Mud from Mud and Wheat using the [canonical family recipe](../blocks/MudAndMudBricks.md#crafting-and-stonecutting), or collect it from the [checked Trail Ruins route](../blocks/MudAndMudBricks.md#natural-supplies). A pickaxe is the efficient tool, but hand mining still returns one Packed Mud. No Silk Touch or higher tool tier is needed.

## Usage

Place it as a full building block or craft [Mud Bricks](../blocks/MudAndMudBricks.md#crafting-and-stonecutting). Wheat Seeds and Dirt do not replace the recipe's named ingredients.

## Behavior

Packed Mud has no waterlogged state or gravity behavior. The dripstone Clay conversion accepts ordinary Mud, not Packed Mud. See [placed Packed Mud](../blocks/MudAndMudBricks.md#packed-mud).

## Notes

- This is a registered placeable block item
- Recipes and loot can be changed by server data packs
- Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`; no in-game test was run

[Item registrations][packed-bricks-items] · [Block properties][packed-bricks-reg] · [Packed Mud loot][loot-packed_mud] · [Pickaxe tag][pickaxe-tag] · [Dripstone input][drip-mud]

[packed-bricks-items]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L542-L543
[packed-bricks-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L2264-L2273
[loot-packed_mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/packed_mud.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[drip-mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L544-L556
