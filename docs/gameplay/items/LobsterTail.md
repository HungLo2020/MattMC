# Lobster Tail

**Lobster Tail** (`minecraft:lobster_tail`) is a raw food item and the ingredient for [Cooked Lobster Tail](CookedLobsterTail.md). Eating it supplies **2 hunger points (one drumstick)** and **1.2 saturation**, subject to normal caps. [Registration][l-food-items] · [Food values][food-values] · [Saturation formula][food-math] · [Caps][food-cap]

## Obtaining

The ordinary food-category listing makes it available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. [Category entry][l-food-list]

The checked [Lobster](../mobs/Lobster.md#drops) has no bundled `entities/lobster` loot table, so it does not have a configured Lobster Tail death drop. No separate recipe or loot source for the raw tail was found in the bundled inventory. [Entity loot files][loot-data] · [Missing-table fallback][loot-fallback] · [Recipe data][recipe-data]

## Usage

Cook **one Lobster Tail into one Cooked Lobster Tail** using a Furnace, Smoker or lit Campfire. Use the [shared cooking table](CookedLobsterTail.md#cooking) for exact time and XP distinctions. Eating the raw tail instead uses the ordinary Food and Consumable components. [Smelting][tail-smelt] · [Smoking][tail-smoke] · [Campfire recipe][tail-campfire] · [Food wiring][food-component] · [Consumption][consumption]

## Behavior

Do not assume this imported food has the feeding uses of another mod version. The bundled [Mimic Octopus](../mobs/MimicOctopus.md) taming/healing tag lists four ordinary raw fish items and **does not include Lobster Tail**. The ordinary `fishes` tag omits it too. [Octopus tag][octopus-food] · [Fish tag][fishes]

## Notes

The item reuses the registered Raw Catfish food values; this is why it gives 2 hunger and 1.2 saturation in the checked build. Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. No in-game eating, drop or cooking test was run. [Exact food assignment][l-food-items]

Related: [Cooked Lobster Tail](CookedLobsterTail.md) · [Lobster](../mobs/Lobster.md) · [Items](Items.md)

[l-food-items]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1680-L1681
[food-values]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/Foods.java#L53-L54
[food-math]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-cap]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodData.java#L18-L30
[l-food-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1732-L1733
[loot-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe
[tail-smelt]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe/smelting/cooked_lobster_tail.json
[tail-smoke]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe/smoking/cooked_lobster_tail_from_smoking.json
[tail-campfire]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_lobster_tail_from_campfire_cooking.json
[food-component]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Item.java#L366-L371
[consumption]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/component/Consumable.java#L78-L98
[octopus-food]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/tags/item/mimic_octopus_tameables.json
[fishes]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/tags/item/fishes.json
