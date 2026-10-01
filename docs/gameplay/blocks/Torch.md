# Torch

A Torch is an inexpensive placed light source. Ordinary torches and their wall form emit **light level 14**. They are useful for marking routes and illuminating builds, but this page does not claim that lighting prevents every kind of mob spawn.

## Crafting

Place one [Coal](../items/Coal.md) or [Charcoal](../items/Charcoal.md) directly above one Stick to craft **four Torches**. This small vertical recipe fits a 2 × 2 inventory crafting grid or a [Crafting Table](CraftingTable.md).

Coal and charcoal are explicit alternatives in this recipe. Their equivalence here does not make them interchangeable in every other recipe.

## Placement

A standing torch needs support at the center of the top face beneath it. A wall torch needs a sturdy supporting side face. Removing the required support makes its survival check fail. The item chooses the standing or wall form through its placement behavior.

The registered torch has no collision, breaks instantly, and emits light without a burn-duration or refueling mechanism in the checked block implementation. That distinguishes it from timed fuel inside a furnace.

## Other torch types

These are separate registrations and should not be treated as the same item:

| Type | Registered light |
| --- | ---: |
| Ordinary Torch | 14 |
| Copper Torch | 14 |
| Soul Torch | 10 |
| Redstone Torch, when lit | 7 |

Redstone torches have circuit behavior beyond this lighting guide. Their lower light value does not describe their redstone output strength.

## Related pages

- [Torch item](../items/Torch.md)
- [Coal](../items/Coal.md) and [Charcoal](../items/Charcoal.md)
- [Ambersol](Ambersol.md), a different integrated column-lighting block
- [Blocks](Blocks.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [Torch recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/torch.json)
- [Ordinary torch registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1193-L1202)
- [Other torch registrations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1905-L1914)
- [Soul and copper torch registrations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2033-L2052)
- [Standing support](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BaseTorchBlock.java)
- [Wall support](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/WallTorchBlock.java)
- [Item placement registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L464-L468)
