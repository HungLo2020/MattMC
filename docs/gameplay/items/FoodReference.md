# Food reference

Choose food by what one serving supplies, how it is consumed, and what else it does. This comparison covers **59 registered handheld foods**, including **19 MattMC additions**. Each food links to its existing item guide for acquisition and other uses. [Hunger, saturation, and healing](../mechanics/Hunger.md) explains the shared caps, exhaustion and recovery rules.

Bread and Baked Potato both supply **5 hunger and 6 saturation** before caps; Cooked Mutton supplies **6 and 9.6**. Golden Carrot also supplies 6 hunger, with **14.4 saturation**. The bowl foods can provide a larger meal, but each serving occupies its own inventory slot. The tables below make these tradeoffs visible without treating nutrition as a promise of healing.

## Reading the values

**Hunger** is the item's nutrition in points. **Saturation** is its contribution before the shared caps; the displayed decimals are the intended values calculated from the registered nutrition and modifier. These are food values, not the extra hunger, healing or damage that a status effect might later cause. [Food construction][food-builder] · [Calculation][formula] · [Stew helper][stew-values]

Unless the exceptions below say otherwise, a food uses **32 use ticks** (a configured `consumeSeconds` value of 1.6), stacks to **64**, has no registered returned container or additional consumption effect, and does not set the **always-eat** flag. The flag permits the ordinary hunger check to pass even at full hunger; the player check has its own invulnerable-player exception. Use ticks and configured seconds are source values, not measured elapsed eating time. [Food component][food-component] · [Default use][default-use] · [Tick conversion and food gate][consume-ticks] · [Player check][food-gate] · [Default stack][stack-default]

## Other registered foods

| Food and item ID | Hunger | Saturation before caps | Checked definitions |
| --- | ---: | ---: | --- |
| [Apple](Apple.md) · `minecraft:apple` | 4 | 2.4 | [item][item-apple] · [value][food-apple] |
| [Baked Potato](BakedPotato.md) · `minecraft:baked_potato` | 5 | 6 | [item][item-baked_potato] · [value][food-baked_potato] |
| [Beetroot](Beetroot.md) · `minecraft:beetroot` | 1 | 1.2 | [item][item-beetroot] · [value][food-beetroot] |
| [Beetroot Soup](BeetrootSoup.md) · `minecraft:beetroot_soup` | 6 | 7.2 | [item][item-beetroot_soup] · [value][food-beetroot_soup] |
| [Bread](Bread.md) · `minecraft:bread` | 5 | 6 | [item][item-bread] · [value][food-bread] |
| [Carrot](Carrot.md) · `minecraft:carrot` | 3 | 3.6 | [item][item-carrot] · [value][food-carrot] |
| [Chorus Fruit](ChorusFruit.md) · `minecraft:chorus_fruit` | 4 | 2.4 | [item][item-chorus_fruit] · [value][food-chorus_fruit] |
| [Cooked Chicken](CookedChicken.md) · `minecraft:cooked_chicken` | 6 | 7.2 | [item][item-cooked_chicken] · [value][food-cooked_chicken] |
| [Cooked Cod](CookedCod.md) · `minecraft:cooked_cod` | 5 | 6 | [item][item-cooked_cod] · [value][food-cooked_cod] |
| [Cooked Mutton](CookedMutton.md) · `minecraft:cooked_mutton` | 6 | 9.6 | [item][item-cooked_mutton] · [value][food-cooked_mutton] |
| [Cooked Porkchop](CookedPorkchop.md) · `minecraft:cooked_porkchop` | 8 | 12.8 | [item][item-cooked_porkchop] · [value][food-cooked_porkchop] |
| [Cooked Rabbit](CookedRabbit.md) · `minecraft:cooked_rabbit` | 5 | 6 | [item][item-cooked_rabbit] · [value][food-cooked_rabbit] |
| [Cooked Salmon](CookedSalmon.md) · `minecraft:cooked_salmon` | 6 | 9.6 | [item][item-cooked_salmon] · [value][food-cooked_salmon] |
| [Cookie](Cookie.md) · `minecraft:cookie` | 2 | 0.4 | [item][item-cookie] · [value][food-cookie] |
| [Dried Kelp](DriedKelp.md) · `minecraft:dried_kelp` | 1 | 0.6 | [item][item-dried_kelp] · [value][food-dried_kelp] |
| [Enchanted Golden Apple](EnchantedGoldenApple.md) · `minecraft:enchanted_golden_apple` | 4 | 9.6 | [item][item-enchanted_golden_apple] · [value][food-enchanted_golden_apple] |
| [Glow Berries](GlowBerries.md) · `minecraft:glow_berries` | 2 | 0.4 | [item][item-glow_berries] · [value][food-glow_berries] |
| [Golden Apple](GoldenApple.md) · `minecraft:golden_apple` | 4 | 9.6 | [item][item-golden_apple] · [value][food-golden_apple] |
| [Golden Carrot](GoldenCarrot.md) · `minecraft:golden_carrot` | 6 | 14.4 | [item][item-golden_carrot] · [value][food-golden_carrot] |
| [Honey Bottle](HoneyBottle.md) · `minecraft:honey_bottle` | 6 | 1.2 | [item][item-honey_bottle] · [value][food-honey_bottle] |
| [Melon Slice](MelonSlice.md) · `minecraft:melon_slice` | 2 | 1.2 | [item][item-melon_slice] · [value][food-melon_slice] |
| [Mushroom Stew](MushroomStew.md) · `minecraft:mushroom_stew` | 6 | 7.2 | [item][item-mushroom_stew] · [value][food-mushroom_stew] |
| [Poisonous Potato](PoisonousPotato.md) · `minecraft:poisonous_potato` | 2 | 1.2 | [item][item-poisonous_potato] · [value][food-poisonous_potato] |
| [Potato](Potato.md) · `minecraft:potato` | 1 | 0.6 | [item][item-potato] · [value][food-potato] |
| [Pufferfish](Pufferfish.md) · `minecraft:pufferfish` | 1 | 0.2 | [item][item-pufferfish] · [value][food-pufferfish] |
| [Pumpkin Pie](PumpkinPie.md) · `minecraft:pumpkin_pie` | 8 | 4.8 | [item][item-pumpkin_pie] · [value][food-pumpkin_pie] |
| [Rabbit Stew](RabbitStew.md) · `minecraft:rabbit_stew` | 10 | 12 | [item][item-rabbit_stew] · [value][food-rabbit_stew] |
| [Raw Beef](RawBeef.md) · `minecraft:beef` | 3 | 1.8 | [item][item-beef] · [value][food-beef] |
| [Raw Chicken](RawChicken.md) · `minecraft:chicken` | 2 | 1.2 | [item][item-chicken] · [value][food-chicken] |
| [Raw Cod](RawCod.md) · `minecraft:cod` | 2 | 0.4 | [item][item-cod] · [value][food-cod] |
| [Raw Mutton](RawMutton.md) · `minecraft:mutton` | 2 | 1.2 | [item][item-mutton] · [value][food-mutton] |
| [Raw Porkchop](RawPorkchop.md) · `minecraft:porkchop` | 3 | 1.8 | [item][item-porkchop] · [value][food-porkchop] |
| [Raw Rabbit](RawRabbit.md) · `minecraft:rabbit` | 3 | 1.8 | [item][item-rabbit] · [value][food-rabbit] |
| [Raw Salmon](RawSalmon.md) · `minecraft:salmon` | 2 | 0.4 | [item][item-salmon] · [value][food-salmon] |
| [Rotten Flesh](RottenFlesh.md) · `minecraft:rotten_flesh` | 4 | 0.8 | [item][item-rotten_flesh] · [value][food-rotten_flesh] |
| [Spider Eye](SpiderEye.md) · `minecraft:spider_eye` | 2 | 3.2 | [item][item-spider_eye] · [value][food-spider_eye] |
| [Steak](Steak.md) · `minecraft:cooked_beef` | 8 | 12.8 | [item][item-cooked_beef] · [value][food-cooked_beef] |
| [Suspicious Stew](SuspiciousStew.md) · `minecraft:suspicious_stew` | 6 | 7.2 | [item][item-suspicious_stew] · [value][food-suspicious_stew] |
| [Sweet Berries](SweetBerries.md) · `minecraft:sweet_berries` | 2 | 0.4 | [item][item-sweet_berries] · [value][food-sweet_berries] |
| [Tropical Fish](TropicalFish.md) · `minecraft:tropical_fish` | 1 | 0.2 | [item][item-tropical_fish] · [value][food-tropical_fish] |

## MattMC additions

These are the actual item registrations. For example, Lobster Tail shares Raw Catfish food values, while Centipede Leg and Raw Kangaroo Meat share Raw Chicken values **and its Hunger-effect chance**. A familiar imported name does not establish an upstream recipe or special effect.

| Food and item ID | Hunger | Saturation before caps | Checked definitions |
| --- | ---: | ---: | --- |
| [Blobfish](Blobfish.md) · `minecraft:blobfish` | 3 | 2.4 | [item][item-blobfish] · [value][food-blobfish] |
| [Boiled Emu Egg](BoiledEmuEgg.md) · `minecraft:boiled_emu_egg` | 6 | 7.2 | [item][item-boiled_emu_egg] · [value][food-cooked_chicken] |
| [Centipede Leg](CentipedeLeg.md) · `minecraft:centipede_leg` | 2 | 1.2 | [item][item-centipede_leg] · [value][food-chicken] |
| [Cooked Catfish](CookedCatfish.md) · `minecraft:cooked_catfish` | 5 | 5 | [item][item-cooked_catfish] · [value][food-cooked_catfish] |
| [Cooked Kangaroo Meat](CookedKangarooMeat.md) · `minecraft:cooked_kangaroo_meat` | 6 | 7.2 | [item][item-cooked_kangaroo_meat] · [value][food-cooked_chicken] |
| [Cooked Lobster Tail](CookedLobsterTail.md) · `minecraft:cooked_lobster_tail` | 5 | 5 | [item][item-cooked_lobster_tail] · [value][food-cooked_catfish] |
| [Cooked Moose Ribs](CookedMooseRibs.md) · `minecraft:cooked_moose_ribs` | 8 | 12.8 | [item][item-cooked_moose_ribs] · [value][food-cooked_porkchop] |
| [Cooked Trilocaris Tail](CookedTrilocarisTail.md) · `minecraft:cooked_trilocaris_tail` | 5 | 5 | [item][item-cooked_trilocaris_tail] · [value][food-cooked_trilocaris_tail] |
| [Dinosaur Nugget](DinosaurNugget.md) · `minecraft:dinosaur_nugget` | 3 | 1.8 | [item][item-dinosaur_nugget] · [value][food-dinosaur_nugget] |
| [Kangaroo Burger](KangarooBurger.md) · `minecraft:kangaroo_burger` | 8 | 12.8 | [item][item-kangaroo_burger] · [value][food-cooked_beef] |
| [Lobster Tail](LobsterTail.md) · `minecraft:lobster_tail` | 2 | 1.2 | [item][item-lobster_tail] · [value][food-raw_catfish] |
| [Pine Nuts](PineNuts.md) · `minecraft:pine_nuts` | 2 | 0.7 | [item][item-pine_nuts] · [value][food-pine_nuts] |
| [Primordial Soup](PrimordialSoup.md) · `minecraft:primordial_soup` | 6 | 7.2 | [item][item-primordial_soup] · [value][food-primordial_soup] |
| [Raw Catfish](RawCatfish.md) · `minecraft:raw_catfish` | 2 | 1.2 | [item][item-raw_catfish] · [value][food-raw_catfish] |
| [Raw Kangaroo Meat](RawKangarooMeat.md) · `minecraft:kangaroo_meat` | 2 | 1.2 | [item][item-kangaroo_meat] · [value][food-chicken] |
| [Raw Moose Ribs](RawMooseRibs.md) · `minecraft:moose_ribs` | 3 | 1.8 | [item][item-moose_ribs] · [value][food-porkchop] |
| [Seething Stew](SeethingStew.md) · `minecraft:seething_stew` | 6 | 7.2 | [item][item-seething_stew] · [value][food-seething_stew] |
| [Serene Salad](SereneSalad.md) · `minecraft:serene_salad` | 5 | 3.5 | [item][item-serene_salad] · [value][food-serene_salad] |
| [Trilocaris Tail](TrilocarisTail.md) · `minecraft:trilocaris_tail` | 2 | 1.2 | [item][item-trilocaris_tail] · [value][food-trilocaris_tail] |

## Consumption exceptions

Only departures from the defaults above are listed here. A **Bowl**, **Glass Bottle** or **Bucket** is the configured use remainder after ordinary consumption spends the serving. Infinite-materials handling skips that conversion. These returns are separate from crafting remainders. [Finish-use dispatch][use-dispatch] · [Remainder handling][remainder]

| Food | What differs |
| --- | --- |
| [Dried Kelp](DriedKelp.md) | **16 use ticks**; configured duration 0.8 |
| [Honey Bottle](HoneyBottle.md) | **40 use ticks**; configured duration 2.0; drinks rather than eats; **always-eat**; stack **16**; returns **Glass Bottle**; removes Poison |
| [Golden Apple](GoldenApple.md), [Enchanted Golden Apple](EnchantedGoldenApple.md) | **Always-eat**; effects below |
| [Chorus Fruit](ChorusFruit.md) | **Always-eat**; attempts a random teleport; registered use cooldown 1.0; see its guide for landing and cooldown details |
| [Mushroom Stew](MushroomStew.md), [Beetroot Soup](BeetrootSoup.md), [Rabbit Stew](RabbitStew.md) | Stack **1**; return **Bowl** |
| [Suspicious Stew](SuspiciousStew.md) | Stack **1**; returns **Bowl**; **always-eat**; the serving's stored effects are applied |
| [Serene Salad](SereneSalad.md), [Seething Stew](SeethingStew.md), [Primordial Soup](PrimordialSoup.md) | Stack **1**, with **no registered Bowl return or added consumption effect** |
| [Blobfish](Blobfish.md), [Raw Chicken](RawChicken.md), [Centipede Leg](CentipedeLeg.md), [Raw Kangaroo Meat](RawKangarooMeat.md), [Poisonous Potato](PoisonousPotato.md), [Pufferfish](Pufferfish.md), [Rotten Flesh](RottenFlesh.md), [Spider Eye](SpiderEye.md) | Harmful effects below |

The per-item definitions linked in the food tables establish stack sizes, flags and returned containers. [Short eating and Honey drinking][duration-exceptions] · [Chorus effect][chorus-effect] · [Stored stew effects][stew-listener]

## Effects to account for

The chances below are the registered chance to **attempt** applying the listed effect after consumption; normal effect handling still applies. Effect durations are **game ticks**. They do not predict damage or healing totals. [Consumption dispatch][consume-effects] · [Probability and effect application][apply-effects]

| Food | Registered additional effect |
| --- | --- |
| Blobfish | Poison I, **120 ticks**, 100% |
| Raw Chicken, Centipede Leg, Raw Kangaroo Meat | Hunger I, **600 ticks**, **30%** |
| Poisonous Potato | Poison I, **100 ticks**, **60%** |
| Rotten Flesh | Hunger I, **600 ticks**, **80%** |
| Spider Eye | Poison I, **100 ticks**, 100% |
| Pufferfish | Poison II, **1,200 ticks**; Hunger III, **300 ticks**; Nausea I, **300 ticks**; one 100% application path for the list |
| Golden Apple | Regeneration II, **100 ticks**; Absorption I, **2,400 ticks** |
| Enchanted Golden Apple | Regeneration II, **400 ticks**; Resistance I and Fire Resistance I, **6,000 ticks** each; Absorption IV, **2,400 ticks** |

[Blobfish and Chicken effects][risk-raw] · [Apple effects][apple-effects] · [Potato, Pufferfish, Rotten Flesh and Spider Eye effects][risk-others]

Honey Bottle removes **Poison specifically**. [Suspicious Stew](SuspiciousStew.md) applies the effect stored on that particular serving; its bare registration has an empty list, while recipes and the browser can provide effect-bearing servings. Use the [flower comparison](../blocks/Flowers.md#small-flower-variants) for the reviewed recipe choices. [Honey removal][duration-exceptions] · [Stew registration][item-suspicious_stew] · [Stew listener][stew-listener] · [Browser stew variants][listed-stews]

## Drinks without food values

These three items register a drinkable consumable **without a FOOD component**. They are not zero-value entries in the food table: their registered effects come through separate consumption actions or listeners. The normal food-specific hunger gate does not apply to a stack lacking FOOD. [Consumption and listeners][consume-effects] · [Food gate][consume-ticks]

| Drink and item ID | Registered use | Stack / returned container | Effect and owner |
| --- | --- | --- | --- |
| [Milk Bucket](MilkBucket.md) · `minecraft:milk_bucket` | **32 use ticks**, configured 1.6 | **1** / **Bucket** | Clears active status effects, including beneficial effects |
| [Potion](Potion.md) · `minecraft:potion` | **32 use ticks**, configured 1.6 | **1** / **Glass Bottle** | Applies the stack's potion contents; use [Brewing](../brewing/Brewing.md) for potion variants |
| [Ominous Bottle](OminousBottle.md) · `minecraft:ominous_bottle` | **32 use ticks**, configured 1.6 | **64** / none registered | Applies Bad Omen for **120,000 ticks**; the bare registration has amplifier 0 (level I), and its component supports amplifiers 0–4 |

[Milk item][nonfood-milk] · [Milk effect][milk-effect] · [Potion item][nonfood-potion] · [Potion listener][potion-listener] · [Ominous item][nonfood-ominous] · [Ominous listener][ominous-listener] · [Default drinking duration][default-use]

## Placed food and food ingredients

[Cake](../blocks/Cake.md) (`minecraft:cake`), [Dinosaur Chop](../blocks/DinosaurChop.md) (`minecraft:dinosaur_chop`) and [Cooked Dinosaur Chop](CookedDinosaurChop.md) (`minecraft:cooked_dinosaur_chop`) are placeable block items, without the handheld FOOD/CONSUMABLE registration used above. Their guides own servings, block interactions, cooking and recovery; a serving from a placed block is not an item eaten for 32 use ticks. [Block-item registrations][placed-items] · [Cake interaction][cake-eat] · [Chop interaction][chop-eat]

A food-category listing alone does not make an ingredient directly edible. For example, [Emu Egg](EmuEgg.md) is distinct from the handheld food [Boiled Emu Egg](BoiledEmuEgg.md), and [Wheat](Wheat.md) is an ingredient for [Bread](Bread.md). [Emu registrations][emu-items] · [Wheat and Bread registrations][wheat-bread]

## Getting a supply

The ordinary food-category listings are available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. Suspicious Stew, Potion and Ominous Bottle have generated component-bearing variants in that category. Browser insertion is an acquisition route of its own; it does not establish a recipe, crop, animal drop or natural source. [Food listings][food-category] · [Stew variants][listed-stews] · [Potion variants][potion-listing] · [Ominous variants][ominous-listing] · [Browser assembly][browser-list] · [Client request][browser-client] · [Server check][browser-server]

For a produced supply, follow [Wheat](../blocks/Wheat.md), [Root crops](../blocks/RootCrops.md), [Fishing](../mechanics/Fishing.md), and the individual food or mob guide. [Smelting and cooking](../smelting/Smelting.md) compares the cooking devices; each food still needs a recipe of that device's type.

## Sources and verification

Source-reviewed on **2026-10-02** at `ac333e7655e092e93f2423a5ab47b50b2b2a9d9a`. This is a comparison of all 59 FOOD registrations and the three additional directly registered CONSUMABLE items in the checked item registry, not an inventory of arbitrary component-modified stacks. Checked their food builders, active consumption listeners, effects, use durations, stack limits, remainders, category listings and browser route. Placed food and special acquisition/animal interactions remain with their existing guides. No game session, eating test, timing measurement, recipe-rate estimate or natural-production test was run.

[food-builder]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[formula]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-component]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default-use]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L73
[consume-ticks]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L102
[food-gate]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[stack-default]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L389
[stew-values]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L59-L61
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L411
[remainder]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/UseRemainder.java#L14-L26
[food-category]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1683-L1762
[browser-list]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[listed-stews]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2242-L2253
[item-apple]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1288
[food-apple]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L4
[item-baked_potato]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2058
[food-baked_potato]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L5
[item-beetroot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2238
[food-beetroot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L7
[item-beetroot_soup]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2240
[food-beetroot_soup]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L8
[item-bread]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1356
[food-bread]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L10
[item-carrot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2056
[food-carrot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L11
[item-chorus_fruit]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2232-L2234
[food-chorus_fruit]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L13
[item-cooked_chicken]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1753
[food-cooked_chicken]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L16
[item-cooked_cod]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1673
[food-cooked_cod]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L17
[item-cooked_mutton]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2148
[food-cooked_mutton]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L18
[item-cooked_porkchop]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1409
[food-cooked_porkchop]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L19
[item-cooked_rabbit]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2123
[food-cooked_rabbit]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L20
[item-cooked_salmon]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1675
[food-cooked_salmon]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L21
[item-cookie]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1733
[food-cookie]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L22
[item-dried_kelp]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1747
[food-dried_kelp]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L23
[item-enchanted_golden_apple]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1412-L1418
[food-enchanted_golden_apple]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L24
[item-glow_berries]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2452-L2454
[food-glow_berries]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L43
[item-golden_apple]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1411
[food-golden_apple]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L25
[item-golden_carrot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2062
[food-golden_carrot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L26
[item-honey_bottle]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2477-L2480
[food-honey_bottle]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L27
[item-melon_slice]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1746
[food-melon_slice]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L28
[item-mushroom_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1350
[food-mushroom_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L29
[item-poisonous_potato]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2059
[food-poisonous_potato]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L31
[item-potato]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2057
[food-potato]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L33
[item-pufferfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1672
[food-pufferfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L34
[item-pumpkin_pie]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2105
[food-pumpkin_pie]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L35
[item-rabbit_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2124
[food-rabbit_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L37
[item-beef]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1750
[food-beef]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L6
[item-chicken]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1752
[food-chicken]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L12
[item-cod]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1668
[food-cod]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L14
[item-mutton]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2147
[food-mutton]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L30
[item-porkchop]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1408
[food-porkchop]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L32
[item-rabbit]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2122
[food-rabbit]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L36
[item-salmon]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1670
[food-salmon]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L39
[item-rotten_flesh]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1760
[food-rotten_flesh]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L38
[item-spider_eye]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1779
[food-spider_eye]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L40
[item-cooked_beef]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1751
[food-cooked_beef]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L15
[item-suspicious_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2378-L2385
[food-suspicious_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L41
[item-sweet_berries]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2449-L2451
[food-sweet_berries]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L42
[item-tropical_fish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1671
[food-tropical_fish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L44
[item-blobfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1667
[food-blobfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L9
[item-boiled_emu_egg]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1858
[item-centipede_leg]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1754
[item-cooked_catfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1674
[food-cooked_catfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L54
[item-cooked_kangaroo_meat]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1758
[item-cooked_lobster_tail]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1681
[item-cooked_moose_ribs]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1784
[item-cooked_trilocaris_tail]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1679
[food-cooked_trilocaris_tail]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L48
[item-dinosaur_nugget]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1685
[food-dinosaur_nugget]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L50
[item-kangaroo_burger]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1759
[item-lobster_tail]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1680
[food-raw_catfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L53
[item-pine_nuts]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1682
[food-pine_nuts]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L49
[item-primordial_soup]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1688
[food-primordial_soup]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L57
[item-raw_catfish]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1669
[item-kangaroo_meat]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1757
[item-moose_ribs]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1783
[item-seething_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1687
[food-seething_stew]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L56
[item-serene_salad]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1686
[food-serene_salad]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L55
[item-trilocaris_tail]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1678
[food-trilocaris_tail]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L47
[duration-exceptions]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L22
[chorus-effect]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L65
[stew-listener]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L42
[consume-effects]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L98
[apply-effects]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L31-L61
[risk-raw]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L23-L28
[apple-effects]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L29-L45
[risk-others]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L46-L63
[nonfood-milk]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533
[milk-effect]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[nonfood-potion]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1770-L1778
[potion-listener]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L232-L235
[nonfood-ominous]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2696-L2702
[ominous-listener]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L32
[placed-items]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1714-L1716
[cake-eat]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L86-L102
[chop-eat]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/alexscaves/server/block/DinosaurChopBlock.java#L146-L183
[emu-items]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1857-L1858
[wheat-bread]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1355-L1356
[potion-listing]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2214
[ominous-listing]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2255-L2261
