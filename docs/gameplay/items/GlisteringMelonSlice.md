# Glistering Melon Slice

A **Glistering Melon Slice** is a **Healing** brewing ingredient. Despite being made from a Melon Slice, the ordinary item **cannot be eaten** and does not restore hunger or heal you by itself. [Registration][melon-item] · [Brewing recipe][melon-brewing] · [Use dispatch][item-use]

## Obtaining

At a **Crafting Table**, surround **one [Melon Slice](MelonSlice.md) with eight [Gold Nuggets](GoldNugget.md)** to make **one Glistering Melon Slice**. Use the slice, not a whole Melon block. One Gold Ingot makes nine nuggets, enough for this recipe with one left over. [Recipe][melon-craft] · [Nugget conversion][gold-divide]

A **level-5 (master) Farmer [Villager](../mobs/Villager.md#interacting-and-unlocking-offers)** sells **three Glistering Melon Slices for a base price of four Emeralds**. Demand and special-price adjustments can change the displayed cost. [Farmer offer][farmer-trades] · [Offer quantity and cost][offer-values] · [Active selection][trade-selection] · [Price adjustments][price-adjustments]

## Usage

Put the ingredient above **Awkward Potions** in a fueled Brewing Stand to brew **Potions of Healing**. One Glistering Melon Slice is consumed per operation and can transform up to three matching bottles. Follow [Brewing](../brewing/Brewing.md) to prepare the bottles and fuel the stand. [Healing recipe][melon-brewing] · [Awkward starting mix][start-mix] · [Batch processing][brew-batch]

**Use the correct base:** Glistering Melon Slice + Water Bottle makes a **Mundane Potion**, not Healing. Prepare Awkward Potions first. [Water and Awkward branches][start-mix]

Healing can be strengthened with Glowstone Dust or converted to Harming with a Fermented Spider Eye. Those are follow-on potion recipes; adding more Glistering Melon Slices is not the strengthening step. See [Brewing modifiers](../brewing/Brewing.md#extending-strengthening-and-changing-potions) and [Instant effects](../effects/InstantEffects.md) for potion use and recipient differences. [Follow-on transformations][melon-brewing]

## Behavior

An ordinary Glistering Melon Slice stacks to **64**. Its plain registration supplies neither a food nor a consumable component, so the eating action is unavailable. **[Golden Carrot](GoldenCarrot.md)** is the edible gilded crop ingredient and brews Night Vision instead. [Plain registration][melon-item] · [Registration helper][item-helper] · [Default components][stack-default] · [Use handling][item-use] · [Golden Carrot registration][carrot-item] · [Night Vision recipe][carrot-brewing]

## Notes

- This item is registered as `minecraft:glistering_melon_slice`
- It is a potion ingredient, not a finished healing item or a food substitute

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked active registration/components, ordinary recipe data, Farmer offer construction/selection, server brewing initialization and batch consumption. No in-game crafting, trading, brewing or eating test was run. This guide lists practical supply routes rather than every loot occurrence; data packs and item components can change the bundled behavior. [Server brewing initialization][brewing-init]

[melon-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1793-L1793
[melon-brewing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L170-L174
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L173-L197
[melon-craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/glistering_melon_slice.json#L1-L17
[gold-divide]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/gold_nugget.json#L1-L11
[farmer-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L81-L119
[offer-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1481
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[price-adjustments]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L97
[start-mix]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L224-L235
[brew-batch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L152-L190
[item-helper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2792-L2798
[stack-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[carrot-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2062-L2062
[carrot-brewing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L146-L150
[brewing-init]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
