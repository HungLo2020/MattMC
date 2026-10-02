# Mud Bricks

**Mud Bricks** (`minecraft:mud_bricks`) is the inventory form of the matching block. See [Mud, Packed Mud and Mud Bricks](../blocks/MudAndMudBricks.md#mud-bricks) for placed behavior and the shared production chain.

## Obtaining

Craft Mud Bricks from Packed Mud using the [family recipe table](../blocks/MudAndMudBricks.md#crafting-and-stonecutting), or mine the [checked Trail Ruins masonry](../blocks/MudAndMudBricks.md#natural-supplies). Collection requires an unbroken pickaxe; a Wooden Pickaxe is sufficient. Ordinary mining returns one Mud Bricks item, with no Silk Touch requirement or Fortune bonus.

## Usage

Place full Mud Bricks or convert them into [Mud Brick Stairs, Slabs and Walls](../blocks/MudAndMudBricks.md#crafting-and-stonecutting). Stonecutting improves the stair yield and accepts one block at a time.

## Behavior

Placed Mud Bricks are full cubes without a continuing support requirement or waterlogging. There is no bundled reverse recipe returning Packed Mud or Wheat. See [placed Mud Bricks](../blocks/MudAndMudBricks.md#mud-bricks).

## Notes

- This is a registered placeable block item
- Recipes and loot can be changed by server data packs
- Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`; no in-game test was run

[Item registrations][packed-bricks-items] · [Block properties][packed-bricks-reg] · [Mud Bricks loot][loot-mud_bricks] · [Pickaxe tag][pickaxe-tag] · [Player tool gate][gate]

[packed-bricks-items]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L542-L543
[packed-bricks-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L2264-L2273
[loot-mud_bricks]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud_bricks.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
