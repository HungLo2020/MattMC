# Stick

**Stick** (`minecraft:stick`) is a crafting ingredient for tool handles, torches, ladders, and other equipment. It is also a small fuel item. Crafting from planks is a predictable early source; leaf drops are a possible extra. [Registration][item] · [Plank recipe][recipe] · [Leaf loot][leaves]

## Crafting

Place **two planks vertically**, one above the other, to craft **4 sticks**. Each slot accepts the planks tag independently, so two different accepted plank types can be mixed. [Oak Planks](OakPlanks.md) are one accepted input. The two-cell layout fits in the inventory crafting grid. [Stick recipe][recipe] · [Accepted planks][planks] · [Pattern ingredient matching][pattern]

An alternative recipe places **two [Bamboo](Bamboo.md) items vertically** to make **1 stick**. This uses ordinary bamboo items and has a different output count from the plank recipe. [Bamboo recipe][bamboo]

Oak leaves can also drop sticks without shears or Silk Touch. See [Oak leaf drops](../blocks/Oak.md#leaf-drops) for the chance, Fortune effects, and 1–2-stick quantity after a successful roll. That is one verified non-crafting route, not every possible source of sticks.

## Useful early recipes

Sticks are verified ingredients for:

- [Torches](Torch.md), using Coal or Charcoal above the stick
- [Wooden Pickaxes](WoodenPickaxe.md), as the vertical handle
- [Bows](Bow.md) and [Fishing Rods](FishingRod.md), together with String
- [Ladders](Ladder.md), using seven sticks in an H-shaped 3 × 3 pattern to make three ladders

The linked item/block guides and recipe book hold the other layouts. [Torch recipe][torch] · [Pickaxe recipe][pickaxe] · [Bow recipe][bow] · [Fishing Rod recipe][rod] · [Ladder recipe][ladder]

## Fuel

One stick supplies **100 default furnace burn ticks**. Two sticks therefore supply enough energy for one uninterrupted **200-tick** furnace recipe, provided no fuel time is wasted. A stick used as fuel is consumed rather than retained as a crafting ingredient. [Fuel table][fuel] · [Furnace burning and consumption][furnace]

For comparison, keep the plank-to-stick conversion for crafting needs: two oak planks contain **600 burn ticks**, while their four crafted sticks contain **400**. Making sticks is therefore not a direct-fuel upgrade for those planks. [Oak-plank fuel comparison](OakPlanks.md#fuel) · [Recipe][recipe] · [Fuel values][fuel]

Related: [Oak Planks](OakPlanks.md) · [Oak](../blocks/Oak.md) · [Charcoal](Charcoal.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game crafting, harvesting, or fuel test was run. The examples use bundled recipes and ingredient tags; data packs can change them.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1349
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/stick.json
[leaves]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[planks]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/planks.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[bamboo]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/stick_from_bamboo_item.json
[torch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/torch.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/wooden_pickaxe.json
[bow]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/bow.json
[rod]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/fishing_rod.json
[ladder]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/ladder.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[furnace]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
