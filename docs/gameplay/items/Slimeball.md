# Slimeball

**Slimeballs** (`minecraft:slime_ball`) are ingredients for Sticky Pistons, Slime Blocks, and [Magma Cream](MagmaCream.md). They also feed Frogs. The ordinary item stacks to **64**. [Item registration][items] · [Default item components][components]

## Obtaining

The main renewable source is the **smallest [Slime](../mobs/Slime.md)**. With mob loot enabled it normally drops **0–2 Slimeballs**, with a maximum of **5 at Looting III**. Medium and large Slimes first split into smaller ones; they have no ordinary Slimeball drop of their own. Player attribution is not required for this material pool. A Frog killing a small Slime uses a separate **one-Slimeball** branch. The mob guide covers spawn conditions, size, and the Looting attacker restriction. [Slime loot][loot] · [Death loot gates][death]

Other checked routes are:

- **Unpack a Slime Block:** one [Slime Block](SlimeBlock.md) crafts into **9 Slimeballs**, without a required arrangement
- **Wandering Trader:** the randomized offers can include **one Slimeball for 4 Emeralds**, with 5 uses for that offer; it is not guaranteed on every trader
- **Baby Panda sneezes:** each completed sneeze can roll a **1-in-700** chance of one Slimeball when mob loot is enabled. This is a rare bonus, not a guaranteed drop or a death-loot source

[Unpacking recipe][unpack] · [Trader offer and price construction][trades] · [Active trader selection][trader] · [Panda sneeze goal and callback][panda] · [Sneeze loot][panda-loot]

## Crafting and feeding

| Ingredients and arrangement | Result |
| --- | --- |
| 9 Slimeballs filling a 3×3 crafting grid | 1 [Slime Block](SlimeBlock.md) |
| 1 Slimeball directly above 1 [Piston](Piston.md) | 1 [Sticky Piston](StickyPiston.md) |

Slime Blocks can be unpacked again using the recipe above. For the recipe that combines Slimeballs with Blaze Powder, see [Magma Cream](MagmaCream.md#crafting). [Slime Block recipe][block-recipe] · [Sticky Piston recipe][piston-recipe]

**MattMC's Lead recipe uses five [String](String.md), producing two Leads; it does not use a Slimeball.** Keep Slimeballs for the recipes that actually list them. [Current Lead recipe][lead]

Slimeball is in the bundled Frog-food item tag. Feeding eligible adult [Frogs](../mobs/Frog.md) uses their normal breeding-food interaction; the Frog AI also recognizes this item for temptation. This does not tame the Frog. [Frog-food tag][frog-food] · [Frog food callback][frog] · [Animal feeding interaction][animal] · [Frog temptation][frog-ai]

For brewing, distinguish a Slimeball from a **Slime Block**: the checked Oozing recipe adds a **Slime Block to Awkward Potion**. A loose Slimeball is not that ingredient. See [Brewing](../brewing/Brewing.md) for the stand, fuel, and starting potion. [Registered brewing ingredients][brewing]

Related: [Slime](../mobs/Slime.md) · [Magma Cream](MagmaCream.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game crafting, trading, Panda, or Frog test was run. These are selected verified uses and acquisition routes; integrated-mob uses outside this cluster were not exhaustively surveyed. Active data packs can replace the bundled recipes, tags, and loot. [Recipe loading][recipe-load]

[items]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java
[components]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/slime.json
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[unpack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/slime_ball.json
[trades]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[trader]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L134-L140
[panda]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Panda.java
[panda-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/panda_sneeze.json
[block-recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/slime_block.json
[piston-recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/sticky_piston.json
[lead]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/lead.json
[frog-food]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/frog_food.json
[frog]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L357-L375
[animal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L154
[frog-ai]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/frog/FrogAi.java
[brewing]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L59-L90
