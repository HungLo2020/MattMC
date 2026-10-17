# White Candle

The item places `minecraft:white_candle`, the White Candle variant. White and uncolored Candles are distinct items.

## Obtaining

Craft one shapelessly from one uncolored Candle and one White Dye. An already dyed candle is not accepted by this recipe. Mine a standalone group to recover its full count of matching items; no special tool or Silk Touch is needed.

## Usage

Place one on valid center support and add matching candles to group up to four in one block. Ignite a dry group with Flint and Steel or Fire Charge; its light is 3 per candle, up to 12. Use an empty hand to extinguish it.

## Behavior

Standalone candles can waterlog and cannot be lit while waterlogged. One candle can also be added to an untouched Cake; eating the first slice returns it. See the [Candles guide](../blocks/Candles.md) for exact support-loss behavior, water rules, lighting, grouping, and all colors.

## Notes

The [Cake guide](../blocks/Cake.md#adding-and-recovering-a-candle) explains the separate candle-cake block and its eating interaction. [Recipe][recipe] · [Loot][loot] · [Registration][reg]

## Sources and verification

Source-reviewed at `beaa5747b36af51b001a13ce8b6648319ba6faf5` on 2026-10-02. The linked block guides contain the full placed-behavior evidence. No gameplay test of support removal, eating, lighting, water, or redstone output was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/white_candle.json
[loot]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/white_candle.json
[reg]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5885-L5885
