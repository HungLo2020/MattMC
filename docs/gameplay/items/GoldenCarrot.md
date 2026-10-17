# Golden Carrot

A **Golden Carrot** is edible food, a **Night Vision** brewing ingredient and accepted food for Rabbits and ordinary Horses and Donkeys. Decide which job you need before using it: eating one does not give the potion's Night Vision effect. [Food registration][carrot-item] · [Food values][carrot-food] · [Brewing recipe][carrot-brewing]

## Obtaining

At a **Crafting Table**, surround **one Carrot with eight [Gold Nuggets](GoldNugget.md)** to make **one Golden Carrot**. The recipe uses nuggets, not ingots. One Gold Ingot converts to nine nuggets, enough for this recipe with one left over. [Golden Carrot recipe][carrot-craft] · [Nugget conversion][gold-divide]

A **level-5 (master) Farmer [Villager](../mobs/Villager.md#interacting-and-unlocking-offers)** sells **three Golden Carrots for a base price of three Emeralds**. That is the listed base offer, before demand and special-price adjustments; check the Villager's displayed price. [Farmer offer][farmer-trades] · [Offer quantity and cost][offer-values] · [Active trade selection][trade-selection] · [Price adjustments][price-adjustments]

## Usage

### Eating

One serving supplies **6 hunger points (3 food icons)** and **14.4 saturation before caps**. It uses the ordinary hunger-gated food behavior and does not grant a registered potion effect. Use [Food Reference](FoodReference.md) to compare meals and [Hunger](../mechanics/Hunger.md) for saturation, healing and the hunger cap. [Food values][carrot-food] · [Saturation calculation][food-builder] · [Formula][saturation] · [Consumption gate][food-gate] · [Default food][default-food] · [Food setup][default-food-builder]

### Brewing

Add a Golden Carrot to **Awkward Potions** in a fueled Brewing Stand to make **Night Vision**. One ingredient is consumed for the operation and can transform up to three matching bottles. Night Vision can then be modified into Invisibility with a Fermented Spider Eye. Follow [Brewing](../brewing/Brewing.md) for the bottle preparation, fuel and further modifiers. [Transformation][carrot-brewing] · [Batch processing][brew-batch]

### Feeding animals

- **[Rabbits](../mobs/Rabbit.md#moving-breeding-and-coats):** holding a Golden Carrot attracts them through their food-following goal; feeding ready adults starts breeding, and feeding babies advances growth. Ordinary Carrots and Dandelions are also in their food tag, so gold is not required for this job · [Food tag][rabbit-food] · [Following goal][rabbit-goals] · [Food check][rabbit-food-check] · [Feeding][animal-feeding]
- **[Horses](../mobs/Horse.md#taming-and-feeding) and [Donkeys](../mobs/Donkey.md#taming-feeding-and-riding):** an accepted feeding can heal **4 health points (2 hearts)**, advance a baby's growth, or affect temper. A tamed adult outside its breeding cooldown can enter love mode; actual mating also needs the normal full-health and partner conditions. Use an unoccupied animal without the secondary-use inventory gesture for the normal feeding route · [Food tag][horse-food] · [Food check][horse-food-check] · [Horse interaction][horse-interact] · [Donkey interaction][donkey-interact] · [Feeding effects][horse-eating] · [Parent conditions][horse-parent]

Ordinary accepted feeding consumes one item. Feeding a mount does not itself tame it; follow its own guide for taming and breeding. [Horse consumption][horse-feed] · [Rabbit consumption][animal-feeding]

## Behavior

An ordinary Golden Carrot stacks to **64** and has a food component. The similarly crafted **[Glistering Melon Slice](GlisteringMelonSlice.md)** does not: use Golden Carrots when you need something edible. [Registration][carrot-item] · [Food component setup][item-properties] · [Default stack][stack-default] · [Melon registration][melon-item]

## Notes

- This item is registered as `minecraft:golden_carrot`
- Animal feeding, eating and brewing each spend the item for their own outcome

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registration, food values, crafting, active Farmer trade selection, server brewing initialization and bottle processing, and the shared/species animal interaction paths. No in-game eating, trading, brewing or animal-feeding test was run. Values describe ordinary bundled items; data packs and components can change behavior. [Server brewing initialization][brewing-init] · [Mob interaction dispatch][mob-interact]

[carrot-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2062-L2062
[carrot-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L26-L26
[carrot-brewing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L146-L150
[carrot-craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_carrot.json#L1-L17
[gold-divide]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_nugget.json#L1-L11
[farmer-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L81-L119
[offer-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1481
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[price-adjustments]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L97
[food-builder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[saturation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L98
[default-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[default-food-builder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[brew-batch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L152-L190
[rabbit-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/rabbit_food.json#L1-L7
[rabbit-goals]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Rabbit.java#L103-L114
[rabbit-food-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Rabbit.java#L343-L346
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[horse-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/horse_food.json#L1-L12
[horse-food-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L502-L505
[horse-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java#L157-L177
[donkey-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java#L141-L166
[horse-eating]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L443-L481
[horse-parent]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L800-L806
[horse-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L410-L417
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L372
[stack-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[melon-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1793-L1793
[brewing-init]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[mob-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1077
