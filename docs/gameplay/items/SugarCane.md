# Sugar Cane

**Sugar Cane** (`minecraft:sugar_cane`) is the planting item and harvested material for the [Sugar Cane plant](../blocks/SugarCane.md). Use it to expand a waterside crop or craft Sugar and Paper. [Registration][item]

## Obtaining and planting

Collect a wild or farmed Sugar Cane block. The [block guide](../blocks/SugarCane.md) covers natural sources, valid ground, water, growth, and leaving the base for repeat harvests. A Cane item places the same plant; there is no separate seed item.

## Crafting

| Result | Ingredients and arrangement |
| --- | --- |
| **1 [Sugar](Sugar.md)** | **1 Sugar Cane**, shapeless |
| **3 [Paper](Paper.md)** | **3 Sugar Cane in one horizontal row**, using a 3 × 3 crafting grid |

The Paper recipe needs three separate ingredient slots, not a stack of three Cane in one slot. Keep some Cane for planting before converting the harvest. [Sugar recipe][sugar] · [Paper recipe][paper]

Related: [Sugar Cane farming](../blocks/SugarCane.md) · [Crafting Table](../blocks/CraftingTable.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game planting or crafting test was run. The bundled recipes use the current [shaped][shaped] and [shapeless][shapeless] recipe formats read by the [recipe manager][recipes]; data packs can replace them.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L369
[sugar]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/sugar_from_sugar_cane.json
[paper]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/paper.json
[shaped]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapedRecipe.java#L104-L112
[shapeless]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L89-L99
[recipes]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L71-L83
