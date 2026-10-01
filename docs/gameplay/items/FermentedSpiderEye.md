# Fermented Spider Eye

**Fermented Spider Eye** (`minecraft:fermented_spider_eye`) changes selected potions into Weakness, Invisibility, Slowness, or Harming. It is a separate item from the ordinary [Spider Eye](SpiderEye.md) and is not registered as food. [Item registration][item] · [Brewing transformations][brewing]

## Crafting

Combine these ingredients in any arrangement:

- **1 [Spider Eye](SpiderEye.md)**
- **1 [Brown Mushroom](BrownMushroom.md)**
- **1 [Sugar](Sugar.md)**

The **shapeless** recipe produces **1 fermented spider eye** and fits in the inventory's 2 × 2 crafting grid. The mushroom ingredient is specifically a brown mushroom; the recipe does not accept a red mushroom as a substitute. [Bundled recipe][recipe]

This is a confirmed acquisition route, not a complete survey of every loot source.

## A direct route to Weakness

Brew **Water Bottle + Fermented Spider Eye → Potion of Weakness**. This route **does not require Nether Wart or Awkward Potion**. The base drinkable result lasts **1,800 ticks (1 minute 30 seconds at 20 TPS)**. Adding **Redstone Dust** makes extended Weakness lasting **4,800 ticks (4 minutes)**. [Registered mixes][brewing] · [Potion durations][potions]

For brewing-stand fuel, batches, and changing a drinkable potion into splash or lingering form, use the [Brewing guide](../brewing/Brewing.md). The server initializes the reviewed mix list, and the content transformations also work with registered splash and lingering containers while preserving their bottle form. Their effect-delivery rules differ from simply drinking a potion. [Server initialization][server] · [Container and content rules][brewing]

One practical use is curing a [Zombie Villager](../mobs/ZombieVillager.md): it must have **Weakness** when you use an ordinary [Golden Apple](GoldenApple.md) on it to start conversion. The fermented eye is a brewing ingredient; using the eye itself on the zombie villager does not perform that curing interaction. [Curing interaction][curing]

## Other registered transformations

These are selected useful routes from the reviewed list:

| Starting potion | Result after adding fermented spider eye |
| --- | --- |
| Night Vision | Invisibility, 3 minutes |
| Extended Night Vision | Extended Invisibility, 8 minutes |
| Swiftness or Leaping | Slowness, 1 minute 30 seconds |
| Extended Swiftness or Extended Leaping | Extended Slowness, 4 minutes |
| Healing, Poison, or Extended Poison | Harming |
| Healing II or Poison II | Harming II |

Durations are for the drinkable potion at 20 TPS; Harming is an instant effect. **Extended Poison becomes ordinary Harming**, not an extended instant-effect potion. The reviewed list also has **no fermented-eye transformation for Swiftness II or Leaping II**, so do not assume every stronger potion can use the same route. [Exact mix list][brewing] · [Resulting effects][potions]

## Trading

A [Wandering Trader](../mobs/WanderingTrader.md) can offer **3 [Emeralds](Emerald.md) for 1 fermented spider eye**. The listing permits **two uses**. It belongs to a group from which the trader selects two offers, so it is not guaranteed on every trader. This is a sale of your eye to the trader, not a way to buy one. [Offer and quantities][trades] · [Active trader selection][trader] · [Random offer selection][selection]

Related: [Spider Eye](SpiderEye.md) · [Brewing](../brewing/Brewing.md) · [Potion](Potion.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game crafting, brewing, curing, or trading test was run. Data packs can change the crafting recipe; enabled content, merchant selection, and entity state can affect availability.

[item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1779-L1780
[brewing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/fermented_spider_eye.json
[potions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[server]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[curing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L137-L153
[trades]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[trader]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L134-L140
[selection]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L234
