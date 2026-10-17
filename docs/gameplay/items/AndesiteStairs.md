# Andesite Stairs

**Andesite Stairs** (`minecraft:andesite_stairs`) is a stepped building piece for stairways, rooflines and trim in the [Andesite family](../blocks/DecorativeStone.md#polished-and-shaped-variants). [Item binding][item]

## Obtaining

Craft **6 blocks in rows of 1, 2 and 3 aligned along one side → 4 Andesite Stairs**. Every occupied slot requires [Andesite](Andesite.md); use the matching full-block finish. [Crafting][craft]

Alternatively, stonecut **1 Andesite → 1 Andesite Stairs** at a [Stonecutter](../blocks/Stonecutter.md). [Stonecutting][cut-raw] Cutting six input blocks gives six stairs, compared with four from the crafting recipe.

Use an **unbroken pickaxe**, including a Wooden Pickaxe, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune does not increase the count. Explosion survival or decay is a separate condition. [Exact loot][loot] · [Tool and drop rules](../blocks/DecorativeStone.md#mining-and-drops)

## Usage

Place the stairs for walkable steps or shaped decoration alongside [Andesite](Andesite.md) and [Andesite Slabs](AndesiteSlab.md). Use the [family recipes](../blocks/DecorativeStone.md#polished-and-shaped-variants) to compare the available finishes.

## Behavior

Your horizontal direction sets the facing, while the clicked face and height choose the lower or upper half. Suitable neighboring stairs automatically form inner or outer corners, including other stair materials when their facing and half fit. Stairs can be waterlogged; see the [shared stair placement rules](../blocks/Stone.md#placing-shaped-blocks). [Placement and corners][shape]

## Notes

This is the ordinary item form of the `minecraft:andesite_stairs` block. Follow the [family guide](../blocks/DecorativeStone.md#polished-and-shaped-variants) for shared recipes and placed behavior. [Block registration][blocks] · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The exact recipes, complete loot table, item binding, tool requirements and relevant placement code were checked. No in-game crafting, mining or placement test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L966-L966
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5246-L5246
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/andesite_stairs.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/andesite_stairs.json
[cut-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/andesite_stairs_from_andesite_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
