# Cooked Rabbit

**Cooked Rabbit** (`minecraft:cooked_rabbit`) restores **5 hunger points**, or **2½ hunger icons**, and up to **6 saturation points** when eaten. It is also an ingredient for [Rabbit Stew](RabbitStew.md). [Item registration][items] · [Food values][foods] · [Saturation calculation and cap][food-constants] [food-data]

## Obtaining

Cook [Raw Rabbit](RawRabbit.md#cooking) in a Furnace, Smoker, or lit Campfire; that page owns the cooking methods and times. An adult [Rabbit](../mobs/Rabbit.md#drops-and-looting) can also drop the cooked form when its loot is evaluated while burning, or when the direct attacker's main-hand enchantments meet the smelting-loot tag, currently Fire Aspect. The meat count and Looting rules are the same as for the raw form. [Rabbit loot conversion][rabbit-loot] · [Smelting-loot tag][smelts]

## Uses

Eat it as portable food, or keep it for Rabbit Stew. Both bundled mushroom versions of the stew recipe require **Cooked Rabbit**, so Raw Rabbit does not substitute for it. [Brown Mushroom stew recipe][stew-brown] · [Red Mushroom stew recipe][stew-red]

Cooked Rabbit is also a meat-tagged food accepted by tame [Wolves](../mobs/Wolf.md). Their healing and breeding interaction applies; this is not a wild-Wolf taming item. [Meat tag][meat] · [Wolf food tag][wolf-food] · [Wolf interaction][wolf]

## Related pages

- [Raw Rabbit: cooking](RawRabbit.md#cooking), [Rabbit](../mobs/Rabbit.md)
- [Rabbit Stew](RabbitStew.md), [Items](Items.md)

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game test was run. Spawn conditions, data packs, gamerules, and later builds can change the result.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[foods]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/Foods.java
[food-constants]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/FoodConstants.java
[rabbit-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/rabbit.json
[smelts]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[stew-brown]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_brown_mushroom.json
[stew-red]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_red_mushroom.json
[meat]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/meat.json
[wolf-food]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/wolf_food.json
[wolf]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java
