# Hunger and Saturation effects

**Hunger** makes a player's food reserves drain faster by adding exhaustion. **Saturation** replenishes hunger and the saturation reserve while its effect runs. These status effects are separate from the food values on an item: an ordinary meal can add saturation without granting the Saturation effect. Use the [Food reference](../items/FoodReference.md) to compare meals and [Hunger, saturation, and healing](../mechanics/Hunger.md) for the shared food system. [Registered effects][registry] · [Food consumption][food-consume] · [Effect behavior][hunger-effect] [saturation-effect]

## Hunger

Each active game tick adds **0.005 exhaustion × effect level** to a player: 0.005 at Hunger I or 0.015 at Hunger III. At 20 ticks per second, those rates are 0.1 and 0.3 exhaustion per second. The effect does **not** immediately subtract food or deal damage. Its food-exhaustion call ignores players with the invulnerable ability and runs only on the server. Applying the status to another living creature does not give that creature this player-food drain. [Hunger tick][hunger-effect] · [Player exhaustion gate][exhaustion-gate] · [Effect ticking][effect-tick]

When accumulated exhaustion is **greater than 4**, the food tick subtracts 4 exhaustion and spends up to one saturation point first. Only with no saturation left does that step spend one hunger point, and it skips that hunger loss on Peaceful. Movement, fighting, and healing can contribute their own exhaustion; the effect's duration alone therefore does not establish how many food icons you will lose. Peaceful's recovery is a separate rule. [Food processing][food-tick] · [Peaceful recovery][peaceful]

### Checked Hunger sources

| Source | Applied effect | Condition or chance |
| --- | --- | --- |
| [Raw Chicken](../items/RawChicken.md), [Centipede Leg](../items/CentipedeLeg.md), [Raw Kangaroo Meat](../items/RawKangarooMeat.md) | Hunger I, **600 ticks** (30 seconds) | **30%** on completed consumption; all three use the same consumable definition [Items][chicken-items] · [Effect][chicken-effect] |
| [Rotten Flesh](../items/RottenFlesh.md) | Hunger I, **600 ticks** (30 seconds) | **80%** on completed consumption [Item][flesh-item] · [Effect][flesh-effect] |
| [Pufferfish](../items/Pufferfish.md) | Hunger III, **300 ticks** (15 seconds) | A **100%** application path for its whole effect list, also containing Poison II and Nausea I [Item][puffer-item] · [Effects][puffer-effects] |
| [Husk](../mobs/Husk.md#combat-and-sunlight) melee | Hunger I, **140 × the integer part of local effective difficulty** ticks | The hit must succeed, the Husk's main hand must be empty, and the target must be a living entity [Hit callback][husk] · [Local difficulty][difficulty] |

The food chances are one roll per consumption action, followed by normal effect acceptance. Their nutrition is delivered separately. Cooking Chicken or Kangaroo Meat changes to registrations without that Hunger consumable; the raw MattMC foods above really do share it. Husk duration uses local difficulty, including time-related inputs, so it is not one fixed duration for each world difficulty; a computed zero duration has no active effect tick. [Probability and copying][consume-roll] · [Consumption dispatch][consume] · [Food values][foods] · [Item registrations][chicken-items] · [Remaining duration][effect-tick]

## Saturation

Every active tick at level I requests **1 hunger point and 2 saturation points**; at level II it requests 2 and 4. In general, hunger gain is the effect level and saturation gain is twice that level. This only changes a player's food data. It has no difficulty multiplier or ordinary eating/full-hunger gate, but each addition caps hunger at **20** and saturation at the resulting hunger level. At full hunger it can still refill missing saturation. [Saturation tick][saturation-effect] · [Modifier formula][formula] · [Food-add caps][food-caps]

Although Saturation is registered as an **instantaneous** effect, Suspicious Stew installs an effect instance with a duration. That instance runs once per remaining tick. A fresh **7-tick Saturation I** instance can request **7 hunger and 14 saturation in total**, separately from the stew's own food values, before caps and other food activity. This is not 7 seconds or a guaranteed final food-bar gain. Saturation does not heal directly; any resulting food-based recovery follows the existing [natural-healing rules](../mechanics/Hunger.md#natural-healing). [Instant tick condition][instant-tick] · [Stew instance][stew-effect] · [Instance timing][effect-tick] · [Food recovery][food-tick]

### Checked Saturation sources

All the servings below store **Saturation I**. An unmodified Suspicious Stew item starts with an empty effect list; its name alone does not establish this effect. [Stew registration][stew-item] · [Stored-effect application][stew-effect]

| Route | Duration | What selects Saturation |
| --- | --- | --- |
| Craft [Suspicious Stew](../items/SuspiciousStew.md) with Dandelion or Blue Orchid | **7 ticks** (0.35 seconds) | Both exact recipes set this effect; use the existing recipe guide for ingredients [Dandelion][dandelion-recipe] · [Blue Orchid][orchid-recipe] |
| Feed either flower to a [brown Mooshroom](../mobs/Mooshroom.md#bowls-milk-and-flower-servings), then take its next bowl serving | **7 ticks** | The flower supplies the stored effect; the animal must have no effect already stored, and the bowl interaction requires an adult [Flower values][dandelion] [orchid] · [Duration conversion][flower-ticks] · [Feeding and bowl route][mooshroom] |
| Level-4 [Farmer](../mobs/Villager.md) offer | **7 ticks** | Saturation stew is one of seven level-4 listings; two distinct offers are chosen, giving **2/7** inclusion for that listing in this checked table [Listings][trade-list] · [Offer construction][trade-effect] · [Selection][trade-select] [trade-caller] |
| [Shipwreck](../structures/Shipwreck.md) supply-chest stew or desert-well [Suspicious Sand](../items/SuspiciousSand.md) archaeology stew | **7–10 ticks** (0.35–0.5 seconds) | When a stew is selected, its effect is chosen uniformly from six entries: **1/6** are Saturation [Supply table][ship-loot] · [Well table][well-loot] · [Effect selection][loot-effect] [random-choice] |

The loot figure is conditional on receiving a stew. The supply pool gives stew weight **10 of 84** per roll and makes **3–10 rolls**; the well table gives stew weight **1 of 8** in its single roll. Neither guarantees a Saturation serving at a structure. The live supply-chest marker and well brushable-block setup connect those tables to their respective routes. For Saturation, the stew loot function retains its rolled **7–10 ticks** instead of multiplying by 20. [Weighted selection][loot-pool] · [Supply route][ship-route] [ship-marker] · [Well route][well-route] · [Duration handling][loot-effect] [uniform]

The category list also generates the flower-effect stew variants, including the 7-tick Saturation serving, for the [inventory item browser](../mechanics/InventoryBrowser.md). Its normal listed-item insertion rules apply separately from crafting, trades, and loot. [Category caller][browser-call] · [Variant generation][browser-stew] · [Flower lookup][flower-lookup] · [Browser list][browser-list]

## Clearing, timing, and commands

[Milk](../items/MilkBucket.md) removes active Hunger and Saturation along with other effects. It stops remaining effect ticks; it does not restore food already lost, erase exhaustion already accumulated, or undo food already gained. [Honey](../items/HoneyBottle.md) removes Poison specifically and does not clear either of these effects. Ordinary finite instances also expire when their remaining duration runs out. Reapplication follows the shared level/duration rules rather than adding two independent copies of the same effect. [Drink definitions][remedies] [milk-definition] · [Clear action][clear] · [Active-effect removal][remove] · [Instance update][refresh] · [Expiry][effect-tick]

Neither effect has a normal registered potion in the checked [brewing](../brewing/Brewing.md) source. Operator `/effect` access requires permission level **2**. For Hunger, a supplied finite duration is in seconds and omission defaults to 30 seconds. For the instantaneous Saturation type, the supplied duration is **ticks**, and omission defaults to **one tick**. The command amplifier is zero-based: 0 means level I. Custom durations, components, or amplifiers are separate from the ordinary source values above. [Potion registrations][potions] · [Command access][command-gate] · [Command duration and instance][command-time]

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`; the checked Java and bundled resource trees are unchanged at documentation baseline `bf1aa1dd57ba225e4392c4c2eba8550c4cd997bd`. This bounded guide covers effect execution, player food consumers, the registered food/Husk routes, described stew recipes/flower callbacks/trades/loot, and clearing/command timing. Existing food, flower, mob, and acquisition guides retain their detailed ownership. **No in-game consumption, attack, trade, loot, command, or timing test was run.** Data packs, changed item components, other active effects, and server timing can change outcomes. Seconds assume 20 game ticks per second.

Related: [Status effects](Effects.md) · [Food reference](../items/FoodReference.md) · [Hunger and healing](../mechanics/Hunger.md) · [Suspicious Stew](../items/SuspiciousStew.md)

[registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L74-L92
[food-consume]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L57
[hunger-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/HungerMobEffect.java#L7-L25
[saturation-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/SaturationMobEffect.java#L7-L20
[exhaustion-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1496-L1502
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L250
[food-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/food/FoodData.java#L32-L77
[peaceful]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L740-L758
[chicken-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1752-L1760
[chicken-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L26-L28
[flesh-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1760-L1760
[flesh-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L58-L60
[puffer-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1672-L1672
[puffer-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L48-L57
[husk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Husk.java#L55-L64
[difficulty]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/DifficultyInstance.java#L43-L60
[consume-roll]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L32-L64
[consume]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L101
[foods]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/food/Foods.java#L4-L61
[formula]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L33
[food-caps]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[instant-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/InstantenousMobEffect.java#L3-L17
[stew-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L71
[stew-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2378-L2385
[dandelion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Blocks.java#L905-L907
[orchid]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Blocks.java#L939-L941
[flower-ticks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L32-L43
[mooshroom]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L93-L169
[trade-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L103-L112
[trade-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1482-L1503
[trade-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[trade-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L839
[loot-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/SetStewEffectFunction.java#L63-L78
[random-choice]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/Util.java#L705-L707
[loot-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L102
[ship-route]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java#L69-L71
[ship-marker]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java#L120-L126
[well-route]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java#L100-L112
[uniform]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java#L27-L30
[browser-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1747-L1753
[browser-stew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2242-L2253
[flower-lookup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/SuspiciousEffectHolder.java#L14-L28
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[remedies]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L24
[remove]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L954
[refresh]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L174
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[command-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L36
[command-time]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L179
[dandelion-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_dandelion.json
[orchid-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_blue_orchid.json
[ship-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json
[well-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/archaeology/desert_well.json
[milk-definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64-L64
