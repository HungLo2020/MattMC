# Oak Button

Oak Button is a placeable redstone input registered as `minecraft:oak_button`. It normally emits a 30-game-tick pulse and can be held active by an arrow.

## Crafting and use

Craft one with one Oak Planks in a shapeless recipe. Place it on a supported floor, wall, or ceiling face, then interact to operate it. Its signal while powered is 15.

The [canonical block guide](../blocks/Buttons.md) covers support, timing, direction, and exceptional trigger behavior. The exact recipe names its ingredient; do not substitute a similarly named imported material without checking recipe data.

## Related pages

- [Oak Button block behavior](../blocks/Buttons.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No circuit simulation or in-game timing test was run; feature flags can select different wire evaluators.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/oak_button.json)
- [Block behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ButtonBlock.java)
- [Block registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java)
