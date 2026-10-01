# Charcoal

Charcoal is a fuel and torch ingredient registered as `minecraft:charcoal`. It provides a wood-based route to furnace fuel once you have an input log and enough starting fuel.

## Making charcoal

Smelt one item in the `logs_that_burn` tag in a [Furnace](../blocks/Furnace.md). The recipe produces **one Charcoal**, takes **200 ticks** (10 seconds at 20 ticks per second), and declares **0.15 experience**.

The tag contains standard overworld log families, including oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, and pale oak. It does not include every wood-like block: Pewen, crimson, and warped wood are not named in that bundled tag. Check tag membership rather than assuming any log can become charcoal.

## Uses

One Charcoal supplies **1,600 default furnace burn ticks**, enough for eight uninterrupted 200-tick recipes. Lit fuel can be wasted if processing stalls, so keep inputs and output space ready.

One Charcoal above one Stick crafts **four Torches**. Unlike Coal, Charcoal is not accepted by the checked Coal Block crafting recipe, which explicitly names Coal.

## Related pages

- [Coal](Coal.md)
- [Torch](Torch.md)
- [Furnace fuel planning](../blocks/Furnace.md#fuel-planning)
- [Items](Items.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [Charcoal recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smelting/charcoal.json)
- [Accepted log families](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/logs_that_burn.json)
- [Default fuel values](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java)
- [Torch recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/torch.json)
- [Coal block requires Coal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/coal_block.json)
