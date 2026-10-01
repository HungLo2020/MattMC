# Rotten Flesh

**Rotten Flesh** (`minecraft:rotten_flesh`) is an emergency food, a food for tame wolves, and a cleric trading material. Eating it can apply Hunger, so feeding a hurt wolf or saving it for trades is often a better use of a surplus. [Item registration][item] · [Eating effects][consumables] · [Wolf feeding][wolf] · [Cleric trade][trades]

## Obtaining

Confirmed examples include [Zombie](../mobs/Zombie.md) and [Drowned](../mobs/Drowned.md) drops. Their bundled loot tables each roll **0–2 rotten flesh**, with Looting increasing the possible maximum by one per level, reaching **5 with Looting III**. These flesh pools do not require a player-attributed kill and have no fire-based cooked replacement. Mob loot must be enabled. These are selected sources, not a complete loot catalog. [Zombie loot][zombie-loot] · [Drowned loot][drowned-loot] · [Mob-loot rule][monster]

## Eating

One rotten flesh provides:

- **4 hunger points**, equivalent to two full hunger icons
- **0.8 saturation**, subject to the normal saturation cap
- An **80% chance of Hunger I for 600 ticks**, or **30 seconds at 20 TPS**

The normal eating action takes **32 ticks**, about **1.6 seconds**. In ordinary Survival play it cannot be eaten at a full hunger bar. Hunger increases food exhaustion; it is not Poison and does not directly deal health damage. The food still restores its configured nutrition when the Hunger roll succeeds. [Food values][foods] · [Saturation calculation][food-constants] · [Food caps][food-data] · [Consumption rules][consumable] · [Effect and eating time][consumables] · [Hunger behavior][hunger]

## Feeding wolves

Rotten flesh belongs to the bundled meat tag, which is included in **wolf food**. Using it on an injured tame [Wolf](../mobs/Wolf.md) heals **8 health points (4 hearts)**, up to the wolf's maximum health. This feeding interaction consumes the food and heals directly; it does not apply the player's Hunger-on-eating effect. [Meat tag][meat] · [Wolf-food tag][wolf-food] · [Healing interaction][wolf]

It can also put ready adult tame wolves into breeding mode. Heal injured wolves first, then feed two ready adults and keep them out of the sitting pose. Rotten flesh does **not** tame a wild wolf; that interaction uses [Bones](Bone.md). [Wolf breeding and taming][wolf] · [Animal feeding][animal]

## Trading

The standard **level-1 cleric** trade list buys **32 rotten flesh for 1 [Emerald](Emerald.md)** at its base price. The displayed cost can change with the merchant's price adjustments, and an exhausted offer must become available again before another trade. Check the actual offer rather than treating 32 as a permanent price. [Cleric offer][trades] · [Trade selection][villager] · [Price calculation][prices]

Related: [Zombie](../mobs/Zombie.md) · [Wolf](../mobs/Wolf.md) · [Trading](../trading/Trading.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game feeding, eating, loot, or trading test was run. Data packs can change loot and food tags; server rules and entity state affect which interactions are available.

[item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1760
[consumables]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumables.java
[wolf]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java
[trades]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[zombie-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/zombie.json
[drowned-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/drowned.json
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[foods]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java
[food-constants]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java
[food-data]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java
[consumable]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumable.java
[hunger]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/effect/HungerMobEffect.java
[meat]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/meat.json
[wolf-food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/wolf_food.json
[animal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[villager]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L843
[prices]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L97
