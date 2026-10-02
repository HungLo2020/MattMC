# Wheat crop

Wheat grows from [Wheat Seeds](../items/WheatSeeds.md) on [Farmland](Farmland.md). MattMC adds practical harvesting interactions to the ordinary crop lifecycle: a mature crop can be harvested and reset without breaking and replanting it.

## Planting and growing

Plant seeds on Farmland. Wheat has ages **0–7**; age 7 is fully grown. Survival requires raw brightness at least 8 in the checked crop code, while random growth requires at least **9**.

Hydrated farmland beneath and around the crop improves its growth calculation. Same-crop neighbors in both horizontal axes or diagonally can halve that calculation, so dense monoculture is not the only layout to consider. This is a random-tick system, not a fixed number of minutes per crop.

Bone meal advances Wheat by a random **2–5 age stages**, capped at age 7. It does not require waiting for the natural random-growth check.

## MattMC harvesting controls

- **Empty-hand use on mature Wheat:** drops the mature crop loot and resets that crop to age 0.
- **Use a hoe on mature Wheat:** harvests the clicked crop and mature Wheat in the surrounding **3 × 3 horizontal area**, resetting each to age 0.
- Hoe area harvesting only selects the **same crop block type** at the same height. It does not harvest immature neighbors or a different adjacent crop.
- The hoe requests durability damage equal to the number of crops harvested, before any normal durability-modifying behavior.

Retained broken hoes also reach the block-side area-harvest handler, while their wear processing skips further damage. See [the current hoe exception](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting); ordinary tilling remains blocked for broken hoes.

The empty-hand path passes an empty tool to loot calculation. The hoe path passes the actual hoe, so tool-dependent loot conditions can differ. Both paths keep the planted crop in place; neither explicitly spends a replacement seed in the checked implementation.

## Drops and uses

The mature Wheat loot table selects one Wheat item and a seed entry with a Fortune-sensitive bonus. Immature Wheat selects seeds instead of the Wheat item. Explosion decay applies. Exact seed counts should be read as loot-table results, not a guaranteed surplus per harvest.

Use [Wheat](../items/Wheat.md) to make Bread or Hay Bales and to feed supported animals such as [Cows](../mobs/Cow.md). Seeds plant the crop; the harvested Wheat item does not.

## Related pages

- [Farmland](Farmland.md)
- [Wheat item](../items/Wheat.md)
- [Wheat Seeds](../items/WheatSeeds.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Growth, bone meal and custom harvest paths](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/CropBlock.java)
- [Crop loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/wheat.json)
- [Seed/item registrations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Farmland rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/FarmBlock.java)
