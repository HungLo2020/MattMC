# Golden Apple

A **Golden Apple** (`minecraft:golden_apple`) is food that grants **Regeneration II for 5 seconds** and **Absorption I for 2 minutes**, at normal tick speed. It is also the Apple required to cure a weakened [Zombie Villager](../mobs/ZombieVillager.md). The **Enchanted Golden Apple** is a separate item with different effects and does not substitute in that cure interaction. [Item registrations][items] · [Consumption effects][consumables] · [Cure check][zv]

## Obtaining

At a Crafting Table, surround **one Apple with eight Gold Ingots** to make **one Golden Apple**. The recipe uses Ingots, not Nuggets or Gold Blocks. [Shaped recipe][apple-recipe] · [Active recipe loading][recipes]

The checked **Igloo basement chest** loot also includes one guaranteed Golden Apple. Basements are selected for half of Igloo generation attempts, so an Igloo without a basement does not provide that chest route. See [Zombie Villager](../mobs/ZombieVillager.md#finding-one) for the accompanying curing supplies. This is a selected loot source, not a complete chest-loot catalog. [Basement selection and chest assignment][igloo] · [Chest table][igloo-loot]

<span id="usage"></span>
<span id="behavior"></span>

## Eating and curing

Eating restores **4 hunger points**, or **two hunger icons**, and up to **9.6 saturation points**. It can be eaten even with a full hunger bar. Its timed effects come from the consumption action; carrying it alone does nothing. [Food values][foods] · [Saturation calculation/cap][food-constants] [food-data] · [Consumption][consumable] [consumables]

For a cure, apply Weakness to the Zombie Villager and **interact with the mob while holding the Apple**. Eating it yourself is a different action. See the [complete curing procedure](../mobs/ZombieVillager.md#curing-step-by-step) for timing, protection, preserved trades, and equipment handling. [Cure interaction][zv]

Golden Apples are also part of the [Horse family's feeding and breeding](../mobs/Horse.md#taming-and-feeding) system. Use that guide for the current animal conditions and food values.

## Related pages

- [Zombie Villager](../mobs/ZombieVillager.md), [Brewing](../brewing/Brewing.md)
- [Apple](Apple.md), [Gold Ingot](GoldIngot.md), [Items](Items.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game combat, conversion, curing, loot, or crafting test was run. Data packs, entity state, difficulty, gamerules, and later builds can change these results.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[consumables]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/Consumables.java
[zv]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java
[apple-recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/golden_apple.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[igloo]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooPieces.java
[igloo-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/chests/igloo_chest.json
[foods]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/Foods.java
[food-constants]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/FoodConstants.java
[food-data]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/FoodData.java
[consumable]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/Consumable.java
