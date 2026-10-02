# Dried Kelp

**Dried Kelp** (`minecraft:dried_kelp`) is a quick-to-eat food made by cooking raw [Kelp](Kelp.md). It also packs into [Dried Kelp Blocks](DriedKelpBlock.md) for storage and fuel. [Food registration][dry-item]

## Drying Kelp

Every checked cooking route takes **1 Kelp** and produces **1 Dried Kelp**:

| Device | Recipe time | Time at 20 ticks per second | Recipe XP field |
| --- | ---: | ---: | ---: |
| [Furnace](../blocks/Furnace.md) | **200 ticks** | **10 seconds** | **0.1** |
| [Smoker](Smoker.md) | **100 ticks** | **5 seconds** | **0.1** |
| Lit [Campfire](Campfire.md) | **600 ticks** | **30 seconds** | **0.1** |

[Smelting recipe][smelt] · [Smoking recipe][smoke] · [Campfire recipe][camp] · [Furnace type][furnace] · [Smoker type][smoker] · [Campfire input][camp-input] · [Lit cooking dispatch][camp-ticker]

The Furnace and Smoker need fuel. The Smoker halves the fuel's burn duration as well as using the faster recipe, so its speed does not double the number of these recipes completed per fuel item. Follow [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) and [experience handling](../blocks/Furnace.md#experience-and-troubleshooting) for those shared rules. [Smoker fuel duration][smoker]

The Campfire's active cooking callback drops the finished item into the world and has **no recipe-XP payout**. Its recipe's 0.1 field is not evidence of an experience reward from Campfire cooking. [Cooking completion][camp-cook]

You can also recover Dried Kelp through the [block unpacking recipe](DriedKelpBlock.md#packing-and-unpacking).

## Eating

One Dried Kelp restores **1 hunger point**, half a food icon, and contributes **0.6 saturation before the food caps**. Its eating duration is **16 ticks**, about **0.8 seconds at 20 ticks per second**. In ordinary Survival, it requires missing hunger; it has no always-edible property. [Food values][food] · [Saturation calculation][food-calc] · [Eating duration][consumable] · [Consumption and timing][eat] · [Hunger gate][hungry] · [Food application and caps][food-data]

Food recovery is applied through the consumable's food listener, and successful eating consumes one item. For healing that follows hunger/saturation, see [Hunger and healing](../mechanics/Hunger.md). [Item use dispatch][item-use] · [Consume listeners][eat] · [Food listener][food-listener]

Loose Dried Kelp is **not a default Furnace fuel**. Pack it into a Dried Kelp Block to use the checked fuel entry; that conversion is separate from eating it. [Fuel list][fuel]

Related: [Kelp farming](../blocks/Kelp.md) · [Dried Kelp Block](DriedKelpBlock.md) · [Furnace](../blocks/Furnace.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked all bundled cooking routes for Dried Kelp, device dispatch, timing, food/consumable components, hunger and saturation application, and default fuels. No in-game cooking, XP, eating, or fuel test was run. Data packs can change recipes.

[dry-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1747
[smelt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/dried_kelp_from_smelting.json
[smoke]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smoking/dried_kelp_from_smoking.json
[camp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/campfire_cooking/dried_kelp_from_campfire_cooking.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[smoker]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/SmokerBlockEntity.java
[camp-input]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L89-L105
[camp-ticker]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L308-L324
[camp-cook]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L53-L85
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/food/Foods.java#L23
[food-calc]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[consumable]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/component/Consumables.java#L22
[eat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[hungry]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[food-data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/food/FoodData.java#L19-L29
[item-use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Item.java#L173-L198
[food-listener]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/food/FoodProperties.java#L42-L59
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
