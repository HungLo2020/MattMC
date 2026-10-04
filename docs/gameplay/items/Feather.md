# Feather

A **Feather** is an ingredient for Arrows, Brushes, Book and Quills, and burst-shaped Firework Stars. This page covers the ordinary `minecraft:feather`; [Roadrunner Feather](RoadrunnerFeather.md) is a different item. [Registration][registration] · [Arrow recipe][arrow] · [Brush recipe][brush]

## Obtaining

With mob loot enabled, an adult [Chicken](../mobs/Chicken.md#death-drops-and-experience) drops **0–2 Feathers** before Looting, and a [Parrot](../mobs/Parrot.md) drops **1–2**. The Looting count bonus is randomized, reaching up to three extra Feathers with Looting III. Baby animals do not produce the shared normal death loot. These tables do not require a player kill just to roll the base Feather count. [Chicken table][chicken] · [Parrot table][parrot] · [Adult and game-rule gate][death-gate] · [Looting calculation][looting]

A Feather is also one possible [Cat morning gift](../mobs/Cat.md#sleep-and-morning-gifts), with weight 10 among the table's 62 total weight. That selection occurs only after the Cat's separate sleep and morning-gift checks succeed. [Gift entry][gift] · [Gift dispatch][cat-gift]

Feathers appear in the **Ingredients** Creative catalog. Ordinary Survival can display that catalog without admitting item insertion; see [Inventory Browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Catalog][catalog]

## Usage

These are selected practical recipes:

| Make | Ingredients and arrangement | Output |
| --- | --- | --- |
| [Arrows](Arrow.md#crafting-and-drops) | 1 Flint, 1 Stick, 1 Feather, top to bottom in a vertical column | 4 Arrows |
| [Brush](Brush.md) | 1 Feather, 1 Copper Ingot, 1 Stick, top to bottom | 1 Brush |
| [Book and Quill](BookAndQuill.md) | 1 Book + 1 Ink Sac + 1 Feather, in any arrangement | 1 Book and Quill |
| [Firework Star](FireworkStar.md) with a burst shape | 1 Gunpowder + at least 1 Dye + 1 Feather, in any arrangement | 1 Firework Star |

The three-cell Arrow and Brush columns require the Crafting Table's taller grid. The Firework Star recipe permits one shape ingredient, so do not add a second Feather or another shape ingredient to the same recipe. Optional trail and twinkle ingredients are handled separately. [Arrow][arrow] · [Brush][brush] · [Book and Quill][book] · [Active Firework recipe][star-data] · [Shape mapping and ingredient checks][star-match] · [Star output][star-result]

An **expert, level-4 Fletcher** can buy **24 Feathers for one Emerald** at the base price. The displayed cost can change with trading conditions. This spends your Feathers; it is not a way to acquire them. [Fletcher listing][fletcher] · [Emerald offer construction][emerald-offer] · [Current trade selection][trade-selection]

## Behavior

Ordinary Feathers stack to **64**. Recipes that name `minecraft:feather` require that item; a different bird's named feather is not automatically interchangeable. [Default item construction][item-defaults] · [Item properties][item-properties] · [Stack size][stack] · [Explicit Arrow ingredients][arrow]

## Notes

- Item ID: `minecraft:feather`
- These are selected acquisition routes and uses; chest loot and other animal implementations have not been exhaustively covered here
- Related: [Chicken](../mobs/Chicken.md), [Parrot](../mobs/Parrot.md), [Flint](Flint.md), [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, including death-loot gates, Cat-gift dispatch, crafting data, the active special Firework Star recipe and Fletcher offer construction. No in-game loot, crafting or trading test was run. Data packs and server settings can change the bundled results. [Recipe loading][recipes]

[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2797
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[death-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[looting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L65-L80
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L841
[emerald-offer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1217-L1243
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1352-L1352
[arrow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/arrow.json#L1-L18
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/brush.json#L1-L18
[chicken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/chicken.json#L1-L33
[parrot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/parrot.json#L1-L36
[gift]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/cat_morning_gift.json#L1-L47
[cat-gift]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Cat.java#L500-L598
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1802-L1810
[book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/writable_book.json#L1-L13
[star-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/firework_star.json#L1-L4
[star-match]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/FireworkStarRecipe.java#L15-L95
[star-result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/FireworkStarRecipe.java#L97-L126
[fletcher]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L270-L289
