# Wheat crop

Wheat grows from [Wheat Seeds](../items/WheatSeeds.md) on [Farmland](Farmland.md). MattMC adds practical harvesting interactions to the ordinary crop lifecycle: a mature crop can be harvested and reset without breaking and replanting it.

## Planting and growing

Plant seeds on Farmland. Wheat has ages **0–7**; age 7 is fully grown. Survival requires raw brightness at least 8 in the checked crop code, while random growth requires at least **9**.

Hydrated farmland beneath and around the crop improves its growth calculation. Same-crop neighbors in both horizontal axes or diagonally can halve that calculation, so dense monoculture is not the only layout to consider. This is a random-tick system, not a fixed number of minutes per crop.

Bone meal advances Wheat by a random **2–5 age stages**, capped at age 7. It does not require waiting for the natural random-growth check.

## MattMC harvesting controls

- **Empty-main-hand use on mature Wheat:** drops the mature crop loot and resets that crop to age 0.
- **Use a hoe on mature Wheat:** harvests the clicked crop and mature Wheat in the surrounding **3 × 3 horizontal area**, resetting each to age 0.
- Hoe area harvesting only selects the **same crop block type** at the same height. It does not harvest immature neighbors or a different adjacent crop.
- The hoe requests durability damage equal to the number of crops harvested, before any normal durability-modifying behavior.

For hoe harvesting, do not hold secondary use: while either hand holds an item, that modifier skips the crop's block handler.

Retained broken hoes also reach the block-side area-harvest handler, while their wear processing skips further damage. See [the current hoe exception](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting); ordinary tilling remains blocked for broken hoes.

The empty-hand path uses the no-Fortune loot result below. The hoe path passes the actual hoe into each selected crop's loot calculation, so its Fortune level affects each mature crop's seeds. Both paths reset the planted crop to age 0 without spending a replacement seed.

## Drops and uses

With the bundled loot tables, block drops enabled, and no explosion loss:

| Crop and tool | Wheat items | Wheat Seeds |
| --- | --- | --- |
| Break immature Wheat, ages 0–6, with or without Fortune | **0** | **1** |
| Mature Wheat, age 7, without Fortune | **1** | **1–4** |
| Mature Wheat with Fortune I | **1** | **1–5** |
| Mature Wheat with Fortune II | **1** | **1–6** |
| Mature Wheat with Fortune III | **1** | **1–7** |

A mature crop starts with one seed, then makes **3 + Fortune level** separate bonus rolls, each with about a **57.14%** chance to add one seed. The counts within each range are **not equally likely**. Fortune does not increase the one Wheat item or the immature crop's one seed.

Breaking removes the plant. In Survival, reserve **one seed to replant**: collecting all drops from an unenchanted mature crop leaves **0–3 extra seeds** after replanting, so an individual harvest need not expand the field. MattMC's mature reset harvests above spend no seed, leaving all dropped seeds available for expansion or animal feeding.

Data packs can replace these loot tables, and explosions can reduce the listed drops. If block drops are disabled, the reset handler can still reset a mature crop without spawning its loot.

Use [Wheat](../items/Wheat.md) to make Bread or Hay Bales and to feed supported animals such as [Cows](../mobs/Cow.md). Seeds plant the crop; the harvested Wheat item does not.

## Related pages

- [Farmland](Farmland.md)
- [Wheat item](../items/Wheat.md)
- [Wheat Seeds](../items/WheatSeeds.md)
- [Blocks](Blocks.md)

For the difference between stored skylight, block light and night-time darkening, see [Which light value matters](../mechanics/Light.md#which-light-value-matters).

## Sources and verification

Yield, seed cost, harvest dispatch, and native Wheat state rules rechecked at `f86206767dadde696adfed4e04c5ee97cd0d0885` on **2026-10-10**. The controls describe the crop handlers once reached; no in-game interaction, growth-timing, or tool test was performed. The original review and its source links are retained below.

- [Current Wheat loot](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/loot_table/blocks/wheat.json) and [binomial Fortune calculation](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L66-L115)
- [Mature reset and hoe harvest handlers](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/CropBlock.java#L195-L252) and [server interaction routing](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L388)
- [Tool context and item-drop rule](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/Block.java#L355-L425) and [active loot-table lookup](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L272-L280)
- [Seed planting registration](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Items.java#L1354), [one-item placement cost](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L83), and [Survival consumption](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079)
- [Native Wheat definition](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/content/block/definitions/catalog.rs#L233), [age-0 default](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/content/block/definitions/templates.rs#L49), and [mature random-tick policy](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/content/block/definitions/policy.rs#L101)

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Growth, bone meal and custom harvest paths](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/CropBlock.java)
- [Crop loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/wheat.json)
- [Seed/item registrations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Farmland rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/FarmBlock.java)
