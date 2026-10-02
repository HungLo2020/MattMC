# Rabbit's Foot

A **Rabbit's Foot** (`minecraft:rabbit_foot`) is a brewing ingredient for **Potion of Leaping**. Carrying one does not itself grant Jump Boost or a luck effect; the ordinary item has no such use component. [Item registration][items] · [Brewing registration][brewing]

## Obtaining

An adult [Rabbit](../mobs/Rabbit.md#drops-and-looting) has a **10% chance** to drop one Foot when its death has player kill credit. Looting I, II, and III increase that chance to **13%, 16%, and 19%**. Looting improves the chance, not the Foot count. Babies do not use the ordinary death-loot table, and **doMobLoot** must be enabled. [Rabbit loot][rabbit-loot] · [Chance calculation][foot-chance] · [Player-credit condition][player-credit] · [Loot and credit handling][living]

A Foot can also be selected as a tame [Cat's morning gift](../mobs/Cat.md#sleep-and-morning-gifts), or be recovered from a [Fox carrying one](../mobs/Fox.md#items-in-the-mouth). Neither route guarantees a Foot on each attempt. [Cat gift table and active gift behavior][cat-gift] [cat] · [Fox carried equipment and exchange][fox]

## Brewing and trading

Add **Rabbit's Foot to Awkward Potion** to make **Potion of Leaping**, whose base drinkable effect is **Jump Boost for 3 minutes** at normal tick speed. Adding it directly to Water instead makes **Mundane Potion**. Follow [Brewing](../brewing/Brewing.md) for stand setup, fuel, preparation, and potion forms. [Registered starting mix and its Water/Awkward branches][brewing] · [Leaping effect][potions] · [Server brewing initialization][server]

The normal Cleric trade pool includes **2 Rabbit's Feet for 1 Emerald** at profession level 3. This is the listed base trade; check the Villager's actual offer and price. [Trade definition][trades] · [Active offer selection][villager]

## Related pages

- [Rabbit](../mobs/Rabbit.md), [Brewing](../brewing/Brewing.md), [Brewing Stand](../blocks/BrewingStand.md)
- [Rabbit Hide](RabbitHide.md), [Items](Items.md)

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game test was run. Spawn conditions, data packs, gamerules, and later builds can change the result.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[brewing]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[rabbit-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/rabbit.json
[foot-chance]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java
[player-credit]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java
[living]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java
[cat-gift]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/gameplay/cat_morning_gift.json
[fox]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Fox.java
[potions]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[server]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/MinecraftServer.java
[trades]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/Villager.java
