# Flint

**Flint** supplies the tip of ordinary Arrows and is used to craft Flint and Steel and Fletching Tables. Mine ordinary Gravel when you need more, or check a novice Fletcher's offered trades. [Registration][registration] · [Gravel loot][gravel] · [Fletcher listing][fletcher]

## Obtaining

Breaking **ordinary Gravel without Silk Touch** rolls the following chance for **one Flint instead of one Gravel**:

| Fortune level | Flint chance |
| --- | ---: |
| None | 10% |
| I | About 14.29% |
| II | 25% |
| III | 100% |

Silk Touch selects the Gravel block first and bypasses the Flint roll. The non-Silk branch also has an explosion-survival condition, so the table is not a guarantee about every explosion. Replacing and breaking Gravel gives another chance when you recovered Gravel instead of Flint. These rules concern ordinary Gravel, not brushing Suspicious Gravel. [Complete loot branches][gravel] · [Fortune-level lookup][fortune] · [Ordinary Gravel registration][gravel-block]

A **novice, level-1 Fletcher** can offer **10 Flint for 10 Gravel plus one Emerald** at the base price. Only two listings are chosen from its three novice candidates, so a particular Fletcher need not offer this conversion. Check the displayed offer before spending supplies. [Listing][fletcher] · [Two-input offer construction][conversion] · [Number of offers][trade-selection] · [Random selection][random-offers]

Flint appears in the **Ingredients** Creative catalog. The [Inventory Browser mode limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) still apply; visibility is not a Survival acquisition route. [Catalog][catalog]

## Usage

| Make | Recipe | Output |
| --- | --- | --- |
| [Arrows](Arrow.md#crafting-and-drops) | 1 Flint above 1 Stick above 1 Feather | 4 Arrows |
| [Flint and Steel](FlintAndSteel.md) | 1 Flint + 1 Iron Ingot, in any arrangement | 1 Flint and Steel |
| [Fletching Table](FletchingTable.md) | 2 Flint across the top, with two rows of 2 Planks below | 1 Fletching Table |

Use a Crafting Table for the three-row Arrow and Fletching Table recipes. The table recipe accepts the bundled Planks tag; Flint and Steel uses one Iron Ingot. [Arrow recipe][arrow] · [Flint and Steel recipe][lighter] · [Fletching Table recipe][table]

An **apprentice, level-2 Fletcher** can buy **26 Flint for one Emerald** at its base price. Other professions have their own listings; this is one checked selling route. Actual trading prices can vary. [Fletcher buying offer][fletcher] · [Emerald offer construction][emerald-offer]

## Behavior

Flint stacks to **64**. It is the material used in the recipes above; craft the Flint and Steel tool when you need that tool's use action. [Item registration][registration] · [Default construction][item-defaults] · [Item properties][item-properties] · [Stack size][stack]

## Notes

- Item ID: `minecraft:flint`
- The page covers ordinary Gravel, selected Fletcher trades and three crafting uses, not an exhaustive loot or trading inventory
- Related: [Feather](Feather.md), [Arrow](Arrow.md), [Flint and Steel](FlintAndSteel.md), [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked loot branches, the Fortune lookup, active merchant selection and offer construction, and ordinary recipe loading. No in-game mining-probability, crafting or trading test was run. Data packs and server settings can change the bundled results. [Recipe loading][recipes]

[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2797
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L841
[random-offers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[emerald-offer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1217-L1243
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1407-L1407
[gravel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/gravel.json#L1-L68
[fletcher]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L270-L289
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[gravel-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L333-L347
[conversion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1383-L1419
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1800-L1805
[arrow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/arrow.json#L1-L18
[lighter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/flint_and_steel.json#L1-L12
[table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/fletching_table.json#L1-L17
