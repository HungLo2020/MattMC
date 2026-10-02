# Chorus Fruit

**Chorus Fruit** (`minecraft:chorus_fruit`) is food from Chorus Plant stems. Eating it also attempts a random teleport, so it is not a predictable way to choose your landing place. [Item registration][item] · [Teleport effect][teleport]

## Obtaining

[Harvest Chorus Plant stems](../blocks/Chorus.md#harvest-flowers-before-stems) for **0–1 Chorus Fruit per stem**. The bundled loot has no Fortune multiplier or Silk Touch alternative. Flowers instead provide the replanting item when collected directly. [Stem loot][loot]

## Usage

Eat it, or smelt it into [Popped Chorus Fruit](PoppedChorusFruit.md) for [Purpur construction](../blocks/EndStoneAndPurpur.md#chorus-fruit-to-purpur) and [End Rods](../blocks/EndRod.md#crafting-and-collecting). Popped fruit is inedible; the building recipes specifically require the popped ingredient. [Item registrations][item] · [Smelting][smelting]

## Behavior

Raw fruit restores **4 hunger points** and **2.4 saturation before caps**, and can be eaten at full hunger. Its random teleport can fail, can dismount a rider, and does not guarantee a safe escape. See [eating and building](../blocks/Chorus.md#eating-and-building) for the 16-attempt limit, landing validation, consumption and cooldown; [Hunger](../mechanics/Hunger.md#food-values) owns the general food caps. [Food values][food] · [Saturation formula][formula] · [Teleport attempts][teleport]

## Notes

This item is not a placeable Chorus Flower or stem. Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`; no in-game harvesting, eating, teleport or cooking test was run.

Related: [Chorus guide](../blocks/Chorus.md) · [Chorus Flower](ChorusFlower.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2232-L2235
[teleport]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/consume_effects/TeleportRandomlyConsumeEffect.java#L22-L81
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/chorus_plant.json#L1-L30
[smelting]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/smelting/popped_chorus_fruit.json#L1-L10
[food]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/food/Foods.java#L10-L14
[formula]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
