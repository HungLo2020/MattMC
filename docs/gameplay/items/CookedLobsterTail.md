# Cooked Lobster Tail

**Cooked Lobster Tail** (`minecraft:cooked_lobster_tail`) is the cooked form of [Lobster Tail](LobsterTail.md). It provides **5 hunger points (2½ drumsticks)** and **5 saturation**, subject to normal caps. [Registration][l-food-items] · [Food values][food-values] · [Saturation formula][food-math] · [Caps][food-cap]

## Obtaining

Cook a Lobster Tail using one of the three recipes below. The cooked item is also an ordinary food-category entry in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), visible in **Survival as well as Creative**; insertion requires **Creative**. That browser route supplies the item without requiring the raw ingredient. [Category listing][l-food-list] · [Smelting recipe][tail-smelt] [Smoking recipe][tail-smoke] [Campfire recipe][tail-campfire]

## Cooking

Every recipe converts **1 Lobster Tail → 1 Cooked Lobster Tail**.

| Device | Recipe time | Nominal time at 20 TPS | Experience handling |
| --- | --- | --- | --- |
| Furnace | 200 ticks | 10 seconds | 0.35 recipe XP per cooked input, subject to normal furnace payout |
| Smoker | 100 ticks | 5 seconds | 0.35 recipe XP per cooked input, subject to normal furnace payout |
| Lit Campfire | 600 ticks | 30 seconds | Its recipe declares 0.35, but the active Campfire cooking path does not award cooking XP |

[Smelting recipe][tail-smelt] · [Smoking recipe][tail-smoke] · [Campfire recipe][tail-campfire] · [Furnace/Smoker cooking and recipe timing][furnace-tick] [Recipe cooking time][cook-time] · [Device recipe types][furnace-type] [Smoker recipe type][smoker-type] · [Campfire output path][campfire-tick] · [Active Campfire ticker][campfire-call]

Use the [Furnace guide](../blocks/Furnace.md#experience-and-troubleshooting) for XP collection and fuel planning, and [Campfires](../blocks/Campfires.md) for cooking placement and operating conditions. These are ticking recipe times, not measured elapsed-time guarantees. The active recipe manager loads the bundled recipe files. [Recipe loading][recipes-load]

## Usage

Eat the cooked tail through the ordinary food interaction. It uses the Cooked Catfish food definition, supplying more hunger and saturation than the raw tail. [Exact assignment][l-food-items] · [Food values][food-values] · [Consumption][consumption] · [Food application][food-use]

## Behavior

The cooking recipes are verified even though the raw tail has no configured Lobster death-drop source in this snapshot. Keep the [raw item's acquisition limits](LobsterTail.md#obtaining) separate from these usable recipes.

## Notes

Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. Food components, category entries, exact recipes and active device callers were checked. No in-game cooking or XP-payout test was run.

Related: [Lobster Tail](LobsterTail.md) · [Lobster](../mobs/Lobster.md) · [Hunger](../mechanics/Hunger.md) · [Items](Items.md)

[l-food-items]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1680-L1681
[food-values]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/Foods.java#L53-L54
[food-math]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-cap]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodData.java#L18-L30
[l-food-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1732-L1733
[tail-smelt]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe/smelting/cooked_lobster_tail.json
[tail-smoke]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe/smoking/cooked_lobster_tail_from_smoking.json
[tail-campfire]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_lobster_tail_from_campfire_cooking.json
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L154-L188
[cook-time]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L268-L273
[furnace-type]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
[smoker-type]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/level/block/entity/SmokerBlockEntity.java#L12-L17
[campfire-tick]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L53-L77
[campfire-call]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L310-L322
[recipes-load]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L80
[consumption]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/component/Consumable.java#L78-L98
[food-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L54
