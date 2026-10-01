# Stone

Stone is a solid building block registered as `minecraft:stone`. Mining it normally produces [Cobblestone](../items/Cobblestone.md); Silk Touch lets you collect the stone block itself.

## Obtaining

- Mine stone with a suitable pickaxe. Its registration requires a correct tool for drops and its mining tag identifies pickaxes.
- With **Silk Touch**, its loot table selects one Stone item instead of Cobblestone.
- Smelt one Cobblestone in a furnace to obtain one Stone. The recipe takes **200 ticks** (10 seconds at 20 ticks per second) and specifies **0.1 experience**.

These acquisition rules are verified from source. This page does not yet catalog world-generation distributions or all renewable generation methods.

## Uses

Stone can be placed directly for construction. Verified crafting options include:

| Ingredients and arrangement | Result |
| --- | --- |
| Four Stone in a 2 × 2 square | Four Stone Bricks |
| One Stone, shapeless | One Stone Button |
| Two Stone side by side | One Stone Pressure Plate |

Use [Stone Bricks](../items/StoneBricks.md) for a tiled appearance, or use the [button](../items/StoneButton.md) and [pressure plate](../items/StonePressurePlate.md) as redstone components. The table is a selection, not a complete recipe list.

## Block properties

| Property | Value |
| --- | --- |
| Hardness | 1.5 |
| Blast resistance | 6 |
| Mining tool family | Pickaxe |
| Note-block instrument when underneath | Bass drum |

## Related pages

- [Stone item](../items/Stone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at [snapshot fffe4a073f0b](https://github.com/HungLo2020/MattMC/commit/fffe4a073f0b8d867902b067a6dd022cda31926f) on 2026-10-01; not an in-game test.

- [Registration](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Mining tag](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Silk Touch and normal drops](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/loot_table/blocks/stone.json)
- [Smelting recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/smelting/stone.json)
- [Stone Bricks recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/crafting/stone_bricks.json)
- [Button recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/crafting/stone_button.json)
- [Pressure plate recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/crafting/stone_pressure_plate.json)
