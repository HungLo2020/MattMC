# Rabbit Hide

**Rabbit Hide** (`minecraft:rabbit_hide`) is a crafting material used to make [Leather](Leather.md). It is not food. The ordinary item registration has no food component. [Item registration][items]

## Obtaining

An adult [Rabbit](../mobs/Rabbit.md#drops-and-looting) drops **0–1 Hide**, with Looting increasing the possible maximum by one per level, up to **0–4 at Looting III**. Babies do not supply the ordinary loot, and **doMobLoot** must be enabled. [Rabbit loot][rabbit-loot] · [Looting calculation][looting] · [Loot gate][living]

Rabbit Hide can also appear in a tame [Cat's morning gift](../mobs/Cat.md#sleep-and-morning-gifts), or in a [Fox's mouth](../mobs/Fox.md#items-in-the-mouth). The Fox route is recovery of a carried item, not a guaranteed Hide drop from every Fox. [Cat gift table and dispatch][cat-gift] [cat] · [Fox carried items][fox]

## Making Leather

Place **four Rabbit Hides in a 2×2 square** to craft **one Leather**. This fits the player's inventory crafting grid. The bundled recipe is shaped; four Hides scattered arbitrarily across a Crafting Table are not its pattern. [Leather recipe][leather-recipe]

The normal Leatherworker trade pool also includes **9 Rabbit Hides for 1 Emerald** at profession level 3. This is the listed base trade; the offered price and available trades depend on the Villager's current offers and trading rules. [Trade definition][trades] · [Offer selection][villager]

## Related pages

- [Rabbit](../mobs/Rabbit.md), [Leather](Leather.md)
- [Rabbit's Foot](RabbitsFoot.md), [Raw Rabbit](RawRabbit.md), [Items](Items.md)

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game test was run. Spawn conditions, data packs, gamerules, and later builds can change the result.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[rabbit-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/rabbit.json
[looting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java
[living]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java
[cat-gift]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/gameplay/cat_morning_gift.json
[cat]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Cat.java
[fox]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Fox.java
[leather-recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/leather.json
[trades]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/Villager.java
