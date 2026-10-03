# Raw Catfish

**Raw Catfish** (`minecraft:raw_catfish`) is an edible item supplying **2 hunger points (one drumstick)** and **1.2 saturation**, before the normal food caps. It is separate from a living [Catfish](../mobs/Catfish.md) or a Catfish bucket. [Registration][c-food-items] · [Food values][food-values] · [Saturation calculation][food-math] · [Food caps][food-cap]

## Obtaining

Request it through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**; it is an ordinary food-category entry. [Category listing][c-food-list]

No bundled recipe or loot table supplies Raw Catfish in the checked data. In particular, the Catfish mob's default and size-specific loot tables are missing, so its name does not establish a configured Raw Catfish death drop. Items previously carried by a Catfish are a separate recovery case. [Bundled recipes][recipe-data] · [Entity loot files][loot-data] · [Missing-table fallback][loot-fallback] · [Catfish stored-content drops][c-inventory]

## Usage

Eat it when the normal food-consumption check allows. The active food component applies its nutrition and saturation to the player. [Food component wiring][food-component] · [Consumption and hunger gate][consumption] · [Food application][food-use]

## Behavior

The separate [Cooked Catfish](CookedCatfish.md) item exists, but **no bundled cooking recipe connects Raw Catfish to it** in this snapshot. Do not substitute the familiar Raw Cod recipe. [Both food registrations][c-food-items] · [Bundled recipe data][recipe-data]

Raw Catfish is also absent from the ordinary `fishes` item tag. For example, it is not one of the accepted fish foods for an [Alligator Snapping Turtle](../mobs/AlligatorSnappingTurtle.md#feeding-and-breeding). [Actual fish tag][fishes]

## Notes

Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. Registered food, category entry, active consumption and the complete bundled recipe/loot inventory were checked. No eating, cooking or drop test was run.

Related: [Cooked Catfish](CookedCatfish.md) · [Catfish](../mobs/Catfish.md) · [Hunger](../mechanics/Hunger.md) · [Items](Items.md)

[c-food-items]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1669-L1674
[food-values]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/Foods.java#L53-L54
[food-math]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-cap]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodData.java#L18-L30
[c-food-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1720-L1721
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/recipe
[loot-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[c-inventory]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L119-L159
[food-component]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Item.java#L366-L371
[consumption]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/component/Consumable.java#L78-L98
[food-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L54
[fishes]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/tags/item/fishes.json
