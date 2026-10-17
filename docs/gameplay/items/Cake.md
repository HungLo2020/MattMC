# Cake

The `minecraft:cake` item places a seven-slice Cake. It stacks to one and must be placed before eating; it is not a food consumed directly from the hand.

## Obtaining

Craft one using the [Cake recipe and egg alternatives](../blocks/Cake.md#crafting-and-carrying). The three Milk Buckets leave empty Buckets. Breaking a placed Cake does not return its item, including with Silk Touch.

## Usage

Place Cake on valid solid support, then use it while hungry. Each slice restores 2 hunger points and 0.4 saturation before caps. The seventh bite removes it. See [Cake](../blocks/Cake.md) for placement, eating conditions, and comparator readings.

## Behavior

An untouched Cake can hold one uncolored or dyed candle. Eating its first slice returns that candle and leaves six ordinary Cake slices. Breaking a candle-cake returns only the candle and destroys the food.

## Notes

General healing and saturation rules remain in [Hunger](../mechanics/Hunger.md). [Recipe][recipe] · [Loot][loot] · [Registration][reg]

## Sources and verification

Source-reviewed at `beaa5747b36af51b001a13ce8b6648319ba6faf5` on 2026-10-02. The linked block guides contain the full placed-behavior evidence. No gameplay test of support removal, eating, lighting, water, or redstone output was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/cake.json
[loot]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/cake.json
[reg]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L2106-L2108
