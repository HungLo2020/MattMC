# Gold Nugget

A **Gold Nugget** is a small gold crafting ingredient. **Nine nuggets make one [Gold Ingot](GoldIngot.md)**; eight coat a Carrot or Melon Slice. Nuggets are also the output from recycling golden equipment. [Registration][gold-nugget-item] · [Ingot recipe][gold-combine] · [Recycling][gold-recycle]

## Obtaining

- Put **one Gold Ingot** in any crafting slot to obtain **nine Gold Nuggets** · [Recipe][gold-divide]
- Mine **Nether Gold Ore** with a suitable pickaxe for **2–6 nuggets before Fortune**. Silk Touch selects the ore block instead. Follow [ore harvesting and enchantment rules](../blocks/OreResources.md#fortune-versus-silk-touch) for the tool checks and altered yields · [Bundled block loot][nether-loot]
- Recycle one of the golden equipment items listed below in a **Furnace or Blast Furnace** for **one nugget** · [Furnace recipe][gold-recycle] · [Blast Furnace recipe][gold-recycle-blast]

The recycling recipes accept Golden Pickaxes, Shovels, Axes, Hoes and Swords; Golden Helmets, Chestplates, Leggings and Boots; and Golden Horse Armor. Each accepted item gives the same **one-nugget** result, regardless of how much gold its crafting recipe used. Furnace processing takes **200 cooking ticks** and blasting takes **100**; both carry **0.1 recipe XP**. The cooker accumulates recipe XP and rounds fractional awards when it releases experience. [Exact accepted items and Furnace values][gold-recycle] · [Blasting values][gold-recycle-blast] · [Cooking result decoder][cooking-result] · [One-item result][single-item] · [XP award][xp-award]

**Raw Gold follows a different recipe:** one Raw Gold cooks into one ingot with **1.0 recipe XP**. Use [Gold Ingot](GoldIngot.md#obtaining) for that route rather than applying the equipment-recycling return to ore processing. [Raw Gold recipe][raw-smelt]

## Usage

| Crafting layout | Output |
| --- | --- |
| Fill all 9 Crafting Table slots with Gold Nuggets | 1 Gold Ingot · [Recipe][gold-combine] |
| Surround 1 Carrot with 8 Gold Nuggets | 1 [Golden Carrot](GoldenCarrot.md) · [Recipe][carrot-craft] |
| Surround 1 Melon Slice with 8 Gold Nuggets | 1 [Glistering Melon Slice](GlisteringMelonSlice.md) · [Recipe][melon-craft] |

One ingot therefore supplies enough nuggets for one coated crop item, with **one nugget left over**. The two finished ingredients have different uses: Golden Carrots are edible and brew Night Vision; Glistering Melon Slices are nonfood ingredients for Healing. Follow their linked pages for those workflows.

## Behavior

Ordinary Gold Nuggets stack to **64**. They are **not Piglin barter currency**: an adult's barter response checks specifically for a Gold Ingot. Convert nuggets first, then use the [Piglin bartering guide](../mobs/Piglin.md#bartering). [Registration][gold-nugget-item] · [Default stack][stack-default] · [Currency item][barter-currency-item] · [Currency check][barter-currency] · [Adult response][barter-response]

## Notes

- This item is registered as `minecraft:gold_nugget`
- See [Gold Ingot](GoldIngot.md) for larger gold recipes and [resource storage](../blocks/ResourceStorageBlocks.md) for block packing

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked active registration, ordinary crafting/cooking recipes, Nether Gold Ore loot, and the Piglin currency check. No in-game mining, recycling, crafting or barter test was run. This is a selected supply guide, not an exhaustive loot catalog; data packs can change recipes and drops.

[gold-nugget-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1764-L1764
[gold-combine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_ingot_from_nuggets.json#L1-L17
[gold-recycle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/gold_nugget_from_smelting.json#L1-L21
[gold-divide]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_nugget.json#L1-L11
[nether-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/nether_gold_ore.json#L1-L61
[gold-recycle-blast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/gold_nugget_from_blasting.json#L1-L21
[cooking-result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/AbstractCookingRecipe.java#L69-L82
[single-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L118-L128
[xp-award]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L387
[raw-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_raw_gold.json#L1-L11
[carrot-craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_carrot.json#L1-L17
[melon-craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/glistering_melon_slice.json#L1-L17
[stack-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[barter-currency-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L77-L83
[barter-currency]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L793-L803
[barter-response]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L399
