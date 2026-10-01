# Melon

**Melon** (`minecraft:melon`) is the whole, placeable fruit block. Eat [Melon Slices](MelonSlice.md) rather than the block item. [Registration][item]

## Obtaining

Harvest a Melon with Silk Touch to obtain the whole block. Without Silk Touch, its loot gives slices instead. The [farming guide](../blocks/PumpkinAndMelon.md#harvesting-and-keeping-the-stem) covers exact drops, Fortune, natural sources, and renewable stems.

## Crafting

Combine **9 Melon Slices** in the nine slots of a **3 × 3 crafting grid** to make **1 Melon**. The bundled recipe is shapeless, but all nine ingredient slots are required. [Recipe][recipe]

Placing this item creates a fruit block, not a growing stem. Start a crop with [Melon Seeds](MelonSeeds.md). Breaking a crafted or placed Melon follows the ordinary Melon loot rules; without Silk Touch, recovering all nine input slices is not guaranteed.

Related: [Pumpkin and Melon farming](../blocks/PumpkinAndMelon.md) · [Melon Slice](MelonSlice.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. The current [shapeless recipe codec and matching][codec] and [Melon loot][loot] were checked; no in-game crafting or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L558
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/melon.json
[codec]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L61-L99
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/melon.json
