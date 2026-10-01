# Pumpkin Seeds

**Pumpkin Seeds** (`minecraft:pumpkin_seeds`) plant a Pumpkin stem when used on Farmland. They are the planting item for [Pumpkin farming](../blocks/PumpkinAndMelon.md), not a way to place a full-grown fruit immediately. [Registration][item]

## Crafting

Craft **1 uncarved Pumpkin into 4 Pumpkin Seeds**. This recipe is shapeless and fits in the inventory crafting grid. [Recipe][recipe]

## Other farm sources and use

[Carving a placed Pumpkin](../blocks/PumpkinAndMelon.md#carving-a-pumpkin) also drops four seeds while leaving a Carved Pumpkin block. Breaking a Pumpkin stem can drop seeds, but the result can be zero; see the [stem loot table summary](../blocks/PumpkinAndMelon.md#harvesting-and-keeping-the-stem).

Keep the planted stem and harvest its fruit to maintain the farm without spending a new seed each time. The farming guide covers Farmland support, light, growth space, and Bone Meal.

Related: [Pumpkin](Pumpkin.md) · [Pumpkin and Melon farming](../blocks/PumpkinAndMelon.md) · [Chicken food](../mobs/Chicken.md#food-and-breeding) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. The recipe uses the current [shapeless codec][codec]. These are confirmed farming acquisition routes, not an exhaustive list of chest loot or trade offers. No in-game planting, loot, or crafting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1748
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/pumpkin_seeds.json
[codec]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L89-L99
