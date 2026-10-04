# Gold Ingot

A **Gold Ingot** is the full-size gold ingredient used for crafting, storage and [Piglin bartering](../mobs/Piglin.md#bartering). Keep ingots for those jobs; convert them to [Gold Nuggets](GoldNugget.md) when a recipe calls for smaller pieces. [Registration][gold-item] · [Nugget conversion][gold-divide]

## Obtaining

| Route | Input | Output |
| --- | --- | --- |
| Crafting Table, filled 3 × 3 grid | 9 Gold Nuggets | 1 Gold Ingot · [Recipe][gold-combine] |
| Any crafting-grid slot | 1 Gold Block | 9 Gold Ingots · [Recipe][gold-unpack] |
| Furnace | 1 Raw Gold | 1 Gold Ingot, 200 cooking ticks, 1.0 recipe XP · [Recipe][raw-smelt] |
| Blast Furnace | 1 Raw Gold | 1 Gold Ingot, 100 cooking ticks, 1.0 recipe XP · [Recipe][raw-blast] |

For finding and mining gold, including the tool requirements and processing ore blocks collected with Silk Touch, use [Ores and Ancient Debris](../blocks/OreResources.md#processing-raw-metal). A **Raw Gold Block** is packed raw material; it is different from the **Gold Block** in the unpacking recipe. See [resource storage](../blocks/ResourceStorageBlocks.md) for both forms.

Recycling one of the supported golden equipment items produces **one Gold Nugget**, not one ingot. See [Gold Nugget recycling](GoldNugget.md#obtaining) before spending fuel on spare gear. Recipe XP is recorded by the cooker and awarded through its experience-handling rules; it is not an immediate fractional orb. [Recycling recipe][gold-recycle] · [Cooker XP][xp-award]

## Usage

Offer Gold Ingots to an eligible adult [Piglin](../mobs/Piglin.md#bartering) for a random barter result. A direct interaction spends **one ingot** and requires an adult that is neither already admiring nor temporarily prevented from admiring. A dropped offer additionally depends on its ground-pickup rules, including `mobGriefing` and the Piglin's loot-pickup flag. See that guide for interruptions and the bundled reward table. [Active interaction][piglin-interact] · [Offer gates][barter-interact] · [Pickup gates][piglin-pickup]

Useful crafting budgets include:

| Make | Ingredients | Result |
| --- | --- | --- |
| Small gold ingredients | 1 Gold Ingot | 9 Gold Nuggets · [Recipe][gold-divide] |
| Compact storage | 9 Gold Ingots, filling the grid | 1 Gold Block · [Recipe][gold-pack] |
| [Golden Apple](GoldenApple.md) | 8 Gold Ingots surrounding 1 Apple | 1 Golden Apple · [Recipe][gold-apple] |
| [Golden Boots](GoldenBoots.md) | 4 Gold Ingots in the boots pattern | 1 pair · [Recipe][gold-boots] |
| [Powered Rails](PoweredRail.md) | 6 Gold Ingots in the outer columns, 1 Stick in the center, 1 Redstone Dust below it | 6 Powered Rails · [Recipe][rail] |
| [Netherite Ingot](NetheriteIngot.md) | 4 Gold Ingots + 4 Netherite Scraps, in any arrangement | 1 Netherite Ingot · [Recipe][netherite] |

## Behavior

An ordinary Gold Ingot stacks to **64**. Holding one does **not** count as wearing Piglin-safe armor; that check examines equipped armor slots. Read the [Piglin safety rules](../mobs/Piglin.md#gold-armor-and-aggression) before approaching a group. [Item properties][item-properties] · [Default stack][stack-default] · [Armor check][gold-armor]

## Notes

- This item is registered as `minecraft:gold_ingot`
- The recipes above are selected practical uses, not a complete inventory of gold-bearing loot or crafting recipes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active registration, ordinary recipe data and loader, cooker XP handling, and Piglin offer/pickup gates. No in-game mining, crafting, cooking or barter test was run. Server settings and data packs can change the bundled results. [Recipe loading][recipe-loading]

[gold-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1303-L1303
[gold-divide]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_nugget.json#L1-L11
[gold-combine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_ingot_from_nuggets.json#L1-L17
[gold-unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_ingot_from_gold_block.json#L1-L12
[raw-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_raw_gold.json#L1-L11
[raw-blast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_raw_gold.json#L1-L11
[gold-recycle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/gold_nugget_from_smelting.json#L1-L21
[xp-award]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L387
[piglin-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L250-L263
[barter-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L535-L550
[piglin-pickup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L399-L402
[gold-pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_block.json#L1-L16
[gold-apple]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_apple.json#L1-L17
[gold-boots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_boots.json#L1-L15
[rail]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/powered_rail.json#L1-L18
[netherite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot.json#L1-L19
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L372
[stack-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[gold-armor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L638-L646
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
