# Raw Rabbit

**Raw Rabbit** (`minecraft:rabbit`) is meat obtained from [Rabbits](../mobs/Rabbit.md). Eating it restores **3 hunger points**, or **1½ hunger icons**, and up to **1.8 saturation points**. Cooking improves both values. [Item food registration][items] · [Food values][foods] · [Saturation calculation and cap][food-constants] [food-data]

## Obtaining

An adult Rabbit normally drops **one Raw Rabbit**. Looting can add up to one more per level, giving **1–4 at Looting III**. Babies do not supply this ordinary loot, and **doMobLoot** must be enabled. A burning Rabbit, or the loot table's direct-attacker Fire Aspect condition, converts the meat to [Cooked Rabbit](CookedRabbit.md). [Rabbit loot][rabbit-loot] · [Looting calculation][looting] · [Smelting-loot tag][smelts] · [Loot gate][living]

## Cooking

Each method converts **one Raw Rabbit into one Cooked Rabbit**:

| Method | Recipe time | At normal 20 ticks per second |
| --- | ---: | ---: |
| [Furnace](../blocks/Furnace.md) | 200 game ticks | 10 seconds |
| [Smoker](Smoker.md) | 100 game ticks | 5 seconds |
| Lit [Campfire](Campfire.md) | 600 game ticks | 30 seconds |

[Smelting recipe][smelting] · [Smoking recipe][smoking] · [Campfire recipe][campfire]

These are recipe times while the device can cook, not a promise that an unfueled or unlit device progresses. See [Cooked Rabbit](CookedRabbit.md) for the resulting food and its stew use. [Active recipe loading][recipes] · [Furnace cooking][furnace-tick] · [Lit Campfire dispatch and cooking][campfire-block] [campfire-tick]

## Other uses

Raw Rabbit is in the meat tag included by the [Wolf](../mobs/Wolf.md) food tag. It can therefore feed tame Wolves through their food interaction; it does not tame a wild Wolf. [Meat tag][meat] · [Wolf food tag][wolf-food] · [Wolf interaction][wolf]

The normal Butcher trade pool includes **4 Raw Rabbits for 1 Emerald** at profession level 1. The trade is one possible offer, and the listed amount is its base price. [Trade pool][trades] · [Offer selection][villager]

## Related pages

- [Rabbit](../mobs/Rabbit.md), [Cooked Rabbit](CookedRabbit.md)
- [Rabbit Hide](RabbitHide.md), [Rabbit's Foot](RabbitsFoot.md), [Items](Items.md)

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game test was run. Spawn conditions, data packs, gamerules, and later builds can change the result.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[foods]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/Foods.java
[food-constants]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/FoodConstants.java
[rabbit-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/rabbit.json
[looting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java
[smelts]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[living]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java
[smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/cooked_rabbit.json
[smoking]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smoking/cooked_rabbit_from_smoking.json
[campfire]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_rabbit_from_campfire_cooking.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
[campfire-block]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java
[meat]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/meat.json
[wolf-food]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/wolf_food.json
[wolf]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java
[trades]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/Villager.java
