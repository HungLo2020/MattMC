# Poisonous Potato

Poisonous Potato is a rare potato-harvest byproduct that can poison you when eaten. Its item ID is `minecraft:poisonous_potato`. [Registration][item]

## Obtaining

Harvest a **fully grown ordinary potato crop (age 7)**. Its loot has a separate **2% chance of one Poisonous Potato**, alongside the ordinary Potato drops. Fortune affects the ordinary Potato bonus pool; it does **not** increase this separate poisonous-potato chance or count. Immature crops do not pass the poisonous-potato pool’s maturity condition. [Exact potato loot][potato-loot]

An ordinary Survival break can obtain this loot. MattMC’s empty-hand and hoe [harvest-and-regrow controls](../blocks/RootCrops.md#mattmc-harvesting-controls) also evaluate the mature crop’s loot before resetting its age. The table applies explosion decay, so the 2% figure describes the chance before explosion losses. Use [Root crops](../blocks/RootCrops.md) for planting, growth and repeated harvesting. [Mature harvest callbacks][crop] · [Block drops][block-drop] · [Exact potato loot][potato-loot]

## Usage

Eating one serving supplies **2 hunger points** (one hunger icon) and **1.2 saturation before caps**. The food’s 0.3 saturation modifier produces that 1.2-point contribution. Compare foods in the [Food reference](FoodReference.md); [Hunger](../mechanics/Hunger.md#food-values) explains the caps. [Food value][food] · [Saturation calculation][formula] · [Food caps][caps]

Keep ordinary [Potatoes](Potato.md) for planting. Poisonous Potato is registered as a food item; the ordinary potato crop is planted with Potato. [Item registrations][plant-items] · [Crop planting item][seed]

## Behavior

Finishing a serving has a **60% chance of [Poison I](../effects/Poison.md)** (amplifier 0) for **100 game ticks**, equivalent to 5 seconds at 20 ticks per second. The nutrition is still applied; the effect is a separate server-side roll. [Configured effect][effect] · [Consumption order][listener] · [Chance and effect application][apply]

Hold use for **32 game ticks** (1.6 seconds at 20 ticks per second). Ordinary Survival eating starts only when hunger is below **20 points**; Creative bypasses that hunger check and does not spend the held serving. See [Hunger](../mechanics/Hunger.md) for shared food behavior. [Use duration][duration] · [Consumption gate][listener] · [Player gate][gate] · [Creative abilities][creative] · [Stack consumption][consume] · [Infinite-materials check][infinite]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active item/food/consumable path and the acquisition routes described here. These are source-defined rules, not in-game eating, loot or cooking tests. Loaded recipes, loot and item components can change the results.

Related: [Food reference](FoodReference.md) · [Hunger](../mechanics/Hunger.md) · [Smelting and cooking](../smelting/Smelting.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2059
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L31
[potato-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/potatoes.json
[crop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CropBlock.java#L192-L256
[block-drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[plant-items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2057-L2059
[seed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/PotatoBlock.java#L25-L28
[effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L46-L48
[listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[apply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L48-L62
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1508-L1514
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L76
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[infinite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
