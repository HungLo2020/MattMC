# Cake

Cake is food eaten **after placing it as a block**. A whole Cake has seven slices, and each accepted interaction restores **2 hunger points and 0.4 saturation before caps**. You can add one uncolored or dyed [Candle](Candles.md) to an untouched Cake, and recover that candle when the first slice is eaten. This guide covers `minecraft:cake` and all **17 candle-cake block variants**. [Slice eating and removal][eat] · [Saturation calculation][saturation] · [Candle recovery on eating][recovery]

## Crafting and carrying

One Cake requires **3 Milk Buckets, 2 Sugar, 1 accepted egg, and 3 Wheat** in the existing [Cake crafting layout on Egg](../items/Egg.md#selected-recipes). The 3 × 3 layout needs a Crafting Table. The eggs ingredient tag accepts [Egg](../items/Egg.md), [Blue Egg](../items/BlueEgg.md), or [Brown Egg](../items/BrownEgg.md); it does not accept every item whose name contains “egg.” [Complete Cake recipe][recipe] · [Accepted eggs][eggs]

The three Milk Buckets leave **three empty Buckets** as crafting remainders. The normal result-slot path puts them in emptied crafting slots, or handles an occupied slot through inventory/drop fallback. The Cake item itself stacks only to **one** and is registered as a block item without a hand-eating consumable component. Place it before eating. [Milk Bucket remainder][milk] · [Crafting remainder lookup][remainders] · [Remainder placement][result] · [Cake item registration][cake-item] · [Block-item use path][block-item]

## Placement and collection

Place Cake above a block that the current source considers **solid**; an ordinary full solid block is a straightforward base. This is the solid-state support test, rather than the center-support test used by standalone candles. Cake is half a block high and its footprint shrinks as slices are eaten. Losing solid support below removes the Cake. [Cake shape and initial state][shape] · [Placement and support loss][support] · [Block-item placement validation][placement] · [Solid-state calculation][solid]

**Breaking Cake does not recover a Cake item or slices**, even with Silk Touch. Its complete block loot table has no drop pools. Choose its position before placing it. Plain Cake and all candle-cakes have hardness and blast resistance **0.5**, no correct-tool requirement, and no membership in the four ordinary mining-tool tags. A candle-cake returns its candle when normally broken, but still loses the food. [Empty Cake loot table][cake-loot] · [Cake properties][cake-reg] · [Inherited candle-cake properties][candle-cake-reg] · [Axe tag][axe] · [Pickaxe tag][pickaxe] · [Shovel tag][shovel] · [Hoe tag][hoe]

Cake and candle-cakes do not waterlog. They keep Cake's forced-solid property, which rejects incoming water from their cell. A candle-cake can instead be extinguished by its empty-hand interaction, a Water potion's local block check, or a Wind Charge; see [candle extinguishing](Candles.md#lighting-and-extinguishing). Both Cake forms use the destroy piston reaction, so pushing into them destroys the food rather than moving it intact. [Empty default fluid state][fluid] · [Fluid solid-state test][motion] · [Flowing-fluid admission][water] · [Copied properties][property-copy] · [Piston destruction drops][piston]

## Eating slices

Use the placed Cake to eat one slice. In ordinary Survival, you need **less than 20 hunger points**; being injured with a full hunger bar is not enough. The `canEat(false)` check also permits a player with the invulnerable ability, such as ordinary Creative mode, to consume slices at full hunger. Spectator's normal interaction path cannot eat the Cake. [Active Cake interaction][eating] · [Player eating condition][can-eat] · [Hunger threshold][needs-food] · [Server interaction dispatch][server-use]

Each successful use adds 2 hunger points, then 0.4 saturation subject to the normal caps. Seven slices offer **14 hunger points and 2.8 saturation in total before caps**, potentially shared across multiple players or visits. The last bite removes the block. Cake does not directly call a health-healing effect; later natural regeneration follows the existing [Hunger, saturation, and healing guide](../mechanics/Hunger.md). [Food amount and bite progression][slice] · [Food addition and caps][food-caps] · [Modifier calculation][sat]

An empty hand is a simple way to eat. Many other held items fall through to the same Cake interaction before their own use. Holding a valid candle on a **whole, uneaten Cake** instead adds the candle, even when the player is too full to eat. Secondary use while holding an item can bypass the normal block interaction, so release it when adding a candle or eating through a held-item interaction. [Cake held-item handling][held-item] · [Interaction priority and secondary use][dispatch]

## Adding and recovering a candle

Use **one Candle of any of the 17 variants** on an untouched Cake. The Survival interaction consumes that item and creates the matching **unlit candle-cake**. A Cake that has already lost a slice cannot accept a candle through this path. A candle-cake holds just **one candle**; the four-candle stacking rule applies only to standalone candle blocks. [Whole-Cake candle insertion][add] · [Accepted candle items][candle-items] · [Registered candle-to-cake mapping][mapping] · [Unlit mapped state][mapped-state]

The first successful eating interaction on a candle-cake does two things: it changes the block to ordinary Cake with **one bite taken**, leaving six slices, and drops **one matching candle item**. This happens whether the candle was lit or unlit. If hunger prevents eating, it neither takes a slice nor drops the candle. Mining the candle-cake or removing its support also returns the candle through its loot table, but destroys the whole food block. [Eating and candle-drop callback][eat-candle] · [Starting from a full Cake state][first-bite] · [Candle-cake support loss][support-loss] · [Active server loot dispatch][drops]

To extinguish a lit candle **without eating**, click its upper candle portion with an empty hand: the hit must be more than half a block above the Cake's base. Clicking the cake body instead reaches the eating path. Clicking an unlit candle can also reach eating, so a second empty-hand click is not a relight command. Flint and Steel and Fire Charge are routed to their ignition handlers without first eating a slice. [Ignition, extinguishing, and eating priority][hit-choice]

All 17 candle-cakes emit **light 3 while lit and 0 while unlit**. Their comparator value stays 14 either way. They have no separately registered item or crafting recipe: make them by adding a candle after placing Cake. The pick-block clone handler returns ordinary Cake. The matrix below checks every color's exact registry mapping and matching-candle loot. [Light registration][cake-light] · [Lit emission helper][lit-helper] · [Pick-block result][clone]

| Color | Exact candle-cake block ID | Matching candle and loot | Registration |
| --- | --- | --- | --- |
| Uncolored | <span id="candle-cake"></span>`minecraft:candle_cake` | [Use uncolored candle](Candles.md#candle) · [Loot][loot-candle_cake] | [Source][reg-candle_cake] |
| White | <span id="white-candle-cake"></span>`minecraft:white_candle_cake` | [Use white candle](Candles.md#white-candle) · [Loot][loot-white_candle_cake] | [Source][reg-white_candle_cake] |
| Orange | <span id="orange-candle-cake"></span>`minecraft:orange_candle_cake` | [Use orange candle](Candles.md#orange-candle) · [Loot][loot-orange_candle_cake] | [Source][reg-orange_candle_cake] |
| Magenta | <span id="magenta-candle-cake"></span>`minecraft:magenta_candle_cake` | [Use magenta candle](Candles.md#magenta-candle) · [Loot][loot-magenta_candle_cake] | [Source][reg-magenta_candle_cake] |
| Light Blue | <span id="light-blue-candle-cake"></span>`minecraft:light_blue_candle_cake` | [Use light blue candle](Candles.md#light-blue-candle) · [Loot][loot-light_blue_candle_cake] | [Source][reg-light_blue_candle_cake] |
| Yellow | <span id="yellow-candle-cake"></span>`minecraft:yellow_candle_cake` | [Use yellow candle](Candles.md#yellow-candle) · [Loot][loot-yellow_candle_cake] | [Source][reg-yellow_candle_cake] |
| Lime | <span id="lime-candle-cake"></span>`minecraft:lime_candle_cake` | [Use lime candle](Candles.md#lime-candle) · [Loot][loot-lime_candle_cake] | [Source][reg-lime_candle_cake] |
| Pink | <span id="pink-candle-cake"></span>`minecraft:pink_candle_cake` | [Use pink candle](Candles.md#pink-candle) · [Loot][loot-pink_candle_cake] | [Source][reg-pink_candle_cake] |
| Gray | <span id="gray-candle-cake"></span>`minecraft:gray_candle_cake` | [Use gray candle](Candles.md#gray-candle) · [Loot][loot-gray_candle_cake] | [Source][reg-gray_candle_cake] |
| Light Gray | <span id="light-gray-candle-cake"></span>`minecraft:light_gray_candle_cake` | [Use light gray candle](Candles.md#light-gray-candle) · [Loot][loot-light_gray_candle_cake] | [Source][reg-light_gray_candle_cake] |
| Cyan | <span id="cyan-candle-cake"></span>`minecraft:cyan_candle_cake` | [Use cyan candle](Candles.md#cyan-candle) · [Loot][loot-cyan_candle_cake] | [Source][reg-cyan_candle_cake] |
| Purple | <span id="purple-candle-cake"></span>`minecraft:purple_candle_cake` | [Use purple candle](Candles.md#purple-candle) · [Loot][loot-purple_candle_cake] | [Source][reg-purple_candle_cake] |
| Blue | <span id="blue-candle-cake"></span>`minecraft:blue_candle_cake` | [Use blue candle](Candles.md#blue-candle) · [Loot][loot-blue_candle_cake] | [Source][reg-blue_candle_cake] |
| Brown | <span id="brown-candle-cake"></span>`minecraft:brown_candle_cake` | [Use brown candle](Candles.md#brown-candle) · [Loot][loot-brown_candle_cake] | [Source][reg-brown_candle_cake] |
| Green | <span id="green-candle-cake"></span>`minecraft:green_candle_cake` | [Use green candle](Candles.md#green-candle) · [Loot][loot-green_candle_cake] | [Source][reg-green_candle_cake] |
| Red | <span id="red-candle-cake"></span>`minecraft:red_candle_cake` | [Use red candle](Candles.md#red-candle) · [Loot][loot-red_candle_cake] | [Source][reg-red_candle_cake] |
| Black | <span id="black-candle-cake"></span>`minecraft:black_candle_cake` | [Use black candle](Candles.md#black-candle) · [Loot][loot-black_candle_cake] | [Source][reg-black_candle_cake] |

## Comparator output

Point a [Redstone Comparator](RedstoneComparator.md#placement-and-input-directions) at the Cake to read how much remains. Cake provides an analog reading, rather than directly acting as a redstone power source. Its reading is **twice the number of slices remaining**. [Cake analog output][cake-output] · [Comparator reads block analog values][comparator] · [Default direct and neighbor signal][signal]

| Slices eaten | Slices remaining | Cake reading |
| ---: | ---: | ---: |
| 0 | 7 | 14 |
| 1 | 6 | 12 |
| 2 | 5 | 10 |
| 3 | 4 | 8 |
| 4 | 3 | 6 |
| 5 | 2 | 4 |
| 6 | 1 | 2 |
| 7 | 0; Cake removed | No Cake reading |

A candle-cake always represents an untouched seven-slice Cake, so it reads **14**. Lighting or extinguishing it does not change that reading. Eating its first slice drops the candle and leaves ordinary Cake reading **12**. This offers a source-derived food-supply indicator; the actual circuit has not been tested in-game. [Candle-cake analog output][candle-output]

## Related pages

- [Cake item](../items/Cake.md), [Candles](Candles.md), and [Hunger](../mechanics/Hunger.md)
- [Egg recipes](../items/Egg.md#selected-recipes), [Milk Bucket](../items/MilkBucket.md), and [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `beaa5747b36af51b001a13ce8b6648319ba6faf5` on 2026-10-02. All covered registry entries, producing recipes, loot tables, and relevant tags were checked against the active placement, interaction, food, projectile, fluid, and comparator code. No gameplay test of support removal, eating, lighting, water, or redstone output was run.

[eat]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L86-L102
[saturation]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[recovery]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L86-L98
[recipe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/cake.json
[eggs]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/item/eggs.json
[milk]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533
[remainders]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java#L18-L31
[result]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L109
[cake-item]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/Items.java#L1714-L1714
[block-item]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L45
[shape]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L32-L51
[support]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L105-L129
[placement]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L142
[solid]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L482-L497
[cake-loot]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/cake.json
[cake-reg]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L2106-L2108
[candle-cake-reg]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5901-L5951
[axe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[fluid]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[motion]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L534-L543
[water]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[property-copy]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1081
[piston]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L287-L299
[eating]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L71-L102
[can-eat]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[needs-food]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/food/FoodData.java#L88-L94
[server-use]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L375
[slice]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L86-L102
[food-caps]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/food/FoodData.java#L19-L29
[sat]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[held-item]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L54-L69
[dispatch]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L387
[add]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L54-L68
[candle-items]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/item/candles.json
[mapping]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L51-L59
[mapped-state]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L146-L152
[eat-candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L86-L94
[first-bite]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L86-L102
[support-loss]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L110-L129
[drops]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Block.java#L364-L375
[hit-choice]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L72-L98
[cake-light]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5901-L5906
[lit-helper]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L7128-L7130
[clone]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L100-L108
[loot-candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/candle_cake.json
[reg-candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5901-L5903
[loot-white_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/white_candle_cake.json
[reg-white_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5904-L5906
[loot-orange_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/orange_candle_cake.json
[reg-orange_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5907-L5909
[loot-magenta_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/magenta_candle_cake.json
[reg-magenta_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5910-L5912
[loot-light_blue_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/light_blue_candle_cake.json
[reg-light_blue_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5913-L5915
[loot-yellow_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/yellow_candle_cake.json
[reg-yellow_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5916-L5918
[loot-lime_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/lime_candle_cake.json
[reg-lime_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5919-L5921
[loot-pink_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/pink_candle_cake.json
[reg-pink_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5922-L5924
[loot-gray_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/gray_candle_cake.json
[reg-gray_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5925-L5927
[loot-light_gray_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/light_gray_candle_cake.json
[reg-light_gray_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5928-L5930
[loot-cyan_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/cyan_candle_cake.json
[reg-cyan_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5931-L5933
[loot-purple_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/purple_candle_cake.json
[reg-purple_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5934-L5936
[loot-blue_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/blue_candle_cake.json
[reg-blue_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5937-L5939
[loot-brown_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/brown_candle_cake.json
[reg-brown_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5940-L5942
[loot-green_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/green_candle_cake.json
[reg-green_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5943-L5945
[loot-red_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/red_candle_cake.json
[reg-red_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5946-L5948
[loot-black_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/black_candle_cake.json
[reg-black_candle_cake]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5949-L5951
[cake-output]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L131-L143
[comparator]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L98-L118
[signal]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L363-L373
[candle-output]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L131-L139
