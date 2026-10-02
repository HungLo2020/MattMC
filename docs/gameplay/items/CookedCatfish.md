# Cooked Catfish

**Cooked Catfish** (`minecraft:cooked_catfish`) restores **5 hunger points (2½ drumsticks)** and **5 saturation**, subject to the normal food caps. Its registered nutrition is higher than [Raw Catfish](RawCatfish.md), but the checked source does not supply a cooking recipe between the two items. [Registration][c-food-items] · [Food values][food-values] · [Saturation formula][food-math] · [Caps][food-cap]

## Obtaining

It is an ordinary food-category item, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) provides it in **Survival as well as Creative**. No bundled recipe or loot table directly supplies it in the checked inventory. Browser access is the verified item-acquisition route here. [Category listing][c-food-list] · [Recipe data][recipe-data] · [Bundled data][bundled-data]

## Usage

Eat it through the normal food interaction. The item has both the Food and Consumable components, and completed consumption applies the food values to the player. [Component registration][food-component] · [Active consumption][consumption] · [Food application][food-use]

## Behavior

A registered cooked item is not evidence that a Furnace, Smoker or Campfire recipe exists for its raw counterpart. The [Catfish guide](../mobs/Catfish.md#drops) also explains why killing that mob is not a configured cooked-fish supply. [Food registrations][c-food-items] · [Recipe directory][recipe-data]

## Notes

Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. Item registration, food values, category and bundled recipe/loot references were checked. No runtime food or cooking test was run.

Related: [Raw Catfish](RawCatfish.md) · [Catfish](../mobs/Catfish.md) · [Hunger](../mechanics/Hunger.md) · [Items](Items.md)

[c-food-items]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1669-L1674
[food-values]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/Foods.java#L53-L54
[food-math]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-cap]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodData.java#L18-L30
[c-food-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1720-L1721
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data
[food-component]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Item.java#L366-L371
[consumption]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/component/Consumable.java#L78-L98
[food-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L54
