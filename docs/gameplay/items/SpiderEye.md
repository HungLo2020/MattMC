# Spider Eye

**Spider Eye** (`minecraft:spider_eye`) is a brewing ingredient for Poison potions and a crafting ingredient for [Fermented Spider Eye](FermentedSpiderEye.md). It is edible, but its consumption effect applies Poison, making it a poor routine food choice. [Registration][item] · [Eating effect][consumables] · [Brewing][brewing]

## Obtaining

Confirmed sources include:

- [Spiders](../mobs/Spider.md) and [Cave Spiders](../mobs/CaveSpider.md): on a **qualifying player-attributed kill**, the eye pool has a **1-in-3 chance of one eye without Looting**. Looting can increase the count, with a possible maximum of **4 eyes at Looting III**.
- [Witches](../mobs/Witch.md): their randomized ingredient pool can select spider eyes. Each selected eye entry rolls **0–2**, with a Looting count bonus. The pool can select an ingredient more than once and does not have the spiders' player-kill condition.

Mob loot must be enabled. These are selected routes, not a complete list of chest, entity, or other loot sources. Environmental spider kills without qualifying player credit are therefore useful for string but do not satisfy the eye pool's kill condition. [Spider loot][spider-loot] · [Cave-spider loot][cave-loot] · [Witch loot][witch-loot] · [Integer count rolls][uniform] · [Looting bonus][looting-count] · [Mob-loot rule][monster]

## Eating

One spider eye provides **2 hunger points (one full hunger icon)** and **3.2 saturation**, subject to the normal cap. Its normal eating action takes **32 ticks**, about **1.6 seconds**, and ordinary Survival consumption requires a non-full hunger bar. [Food values][foods] · [Saturation calculation][food-constants] · [Food caps][food-data] · [Eating requirements][consumable]

Consumption attempts to apply **Poison I for 100 ticks**, or **5 seconds at 20 TPS**, with **100% probability**. This is separate from a brewed Poison potion's duration. Poison's damage tick only runs while health is above **1 health point (half a heart)**, but other damage sources can still kill a weakened player. [Effect and eating time][consumables] · [Default effect probability][apply-effect] · [Poison behavior][poison]

## Brewing Poison

Use a fueled [Brewing Stand](../blocks/BrewingStand.md):

1. Brew a **Water Bottle with Nether Wart** to make Awkward Potion
2. Brew that **Awkward Potion with Spider Eye** to make Poison

The base drinkable potion applies **Poison I for 900 ticks (45 seconds)**. Adding **Redstone Dust** extends it to **1,800 ticks (90 seconds)**. Adding **Glowstone Dust** to the base Poison potion instead makes **Poison II for 432 ticks (21.6 seconds)**. All times assume 20 TPS. [Registered mixes][brewing] · [Potion definitions][potions]

**Water Bottle + Spider Eye makes Mundane Potion**, not Poison. The current starting-mix registration explicitly distinguishes Water from Awkward. See the [Brewing guide](../brewing/Brewing.md) for fuel, batch handling, and splash/lingering forms. [Starting-mix rules][brewing] · [Server initialization][server]

For additional brewing transformations, craft a [Fermented Spider Eye](FermentedSpiderEye.md); that page holds its recipe and uses. Ordinary and fermented eyes are separate ingredients with different registered recipes.

Related: [Spider](../mobs/Spider.md) · [Fermented Spider Eye](FermentedSpiderEye.md) · [Poison](../effects/Poison.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game loot, consumption, or brewing test was run. Data packs, enabled content, server rules, and effect immunity can affect actual results; splash and lingering delivery have their own application rules.

[item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1779
[consumables]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumables.java
[brewing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[spider-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/spider.json
[cave-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/cave_spider.json
[witch-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/witch.json
[uniform]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java
[looting-count]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[foods]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java
[food-constants]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java
[food-data]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java
[consumable]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumable.java
[apply-effect]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java
[poison]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/effect/PoisonMobEffect.java
[potions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[server]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
