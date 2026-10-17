# Candles

Candles are reusable decorative lights. A single block can hold **one to four candles of the same color**, producing light levels **3, 6, 9, or 12** when lit. This guide covers the uncolored Candle and all **16 dyed variants**. Adding a candle to a whole Cake creates a separate [candle-cake block](Cake.md#adding-and-recovering-a-candle), which holds only one candle. [Candle count and light][count-light] · [All candle items][items]

## Crafting and colors

The uncolored Candle uses the existing [String-and-Honeycomb recipe](../items/Honeycomb.md#waxing-and-crafting), yielding **one Candle** in the inventory crafting grid. Each dye recipe is shapeless: **one uncolored Candle + one exact dye → one matching colored candle**. These recipes do not accept an already dyed candle, and no checked producing recipe recolors or removes dye from one. Dye items are ingredients in the crafting grid; using dye on a placed candle has no color-change handler. [Base Candle recipe][base-recipe] · [Placed-item interaction][use] · [Dye item interactions][dye] · [Default block-use result][item-use]

| Color | Exact candle block/item ID | Crafting and loot | Registration |
| --- | --- | --- | --- |
| Uncolored | <span id="candle"></span>`minecraft:candle` | [Base recipe](../items/Honeycomb.md#waxing-and-crafting) · [Recipe][recipe-candle] · [Loot][loot-candle] | [Source][reg-candle] |
| White | <span id="white-candle"></span>`minecraft:white_candle` | 1 Candle + 1 White Dye → 1 · [Recipe][recipe-white_candle] · [Loot][loot-white_candle] | [Source][reg-white_candle] |
| Orange | <span id="orange-candle"></span>`minecraft:orange_candle` | 1 Candle + 1 Orange Dye → 1 · [Recipe][recipe-orange_candle] · [Loot][loot-orange_candle] | [Source][reg-orange_candle] |
| Magenta | <span id="magenta-candle"></span>`minecraft:magenta_candle` | 1 Candle + 1 Magenta Dye → 1 · [Recipe][recipe-magenta_candle] · [Loot][loot-magenta_candle] | [Source][reg-magenta_candle] |
| Light Blue | <span id="light-blue-candle"></span>`minecraft:light_blue_candle` | 1 Candle + 1 Light Blue Dye → 1 · [Recipe][recipe-light_blue_candle] · [Loot][loot-light_blue_candle] | [Source][reg-light_blue_candle] |
| Yellow | <span id="yellow-candle"></span>`minecraft:yellow_candle` | 1 Candle + 1 Yellow Dye → 1 · [Recipe][recipe-yellow_candle] · [Loot][loot-yellow_candle] | [Source][reg-yellow_candle] |
| Lime | <span id="lime-candle"></span>`minecraft:lime_candle` | 1 Candle + 1 Lime Dye → 1 · [Recipe][recipe-lime_candle] · [Loot][loot-lime_candle] | [Source][reg-lime_candle] |
| Pink | <span id="pink-candle"></span>`minecraft:pink_candle` | 1 Candle + 1 Pink Dye → 1 · [Recipe][recipe-pink_candle] · [Loot][loot-pink_candle] | [Source][reg-pink_candle] |
| Gray | <span id="gray-candle"></span>`minecraft:gray_candle` | 1 Candle + 1 Gray Dye → 1 · [Recipe][recipe-gray_candle] · [Loot][loot-gray_candle] | [Source][reg-gray_candle] |
| Light Gray | <span id="light-gray-candle"></span>`minecraft:light_gray_candle` | 1 Candle + 1 Light Gray Dye → 1 · [Recipe][recipe-light_gray_candle] · [Loot][loot-light_gray_candle] | [Source][reg-light_gray_candle] |
| Cyan | <span id="cyan-candle"></span>`minecraft:cyan_candle` | 1 Candle + 1 Cyan Dye → 1 · [Recipe][recipe-cyan_candle] · [Loot][loot-cyan_candle] | [Source][reg-cyan_candle] |
| Purple | <span id="purple-candle"></span>`minecraft:purple_candle` | 1 Candle + 1 Purple Dye → 1 · [Recipe][recipe-purple_candle] · [Loot][loot-purple_candle] | [Source][reg-purple_candle] |
| Blue | <span id="blue-candle"></span>`minecraft:blue_candle` | 1 Candle + 1 Blue Dye → 1 · [Recipe][recipe-blue_candle] · [Loot][loot-blue_candle] | [Source][reg-blue_candle] |
| Brown | <span id="brown-candle"></span>`minecraft:brown_candle` | 1 Candle + 1 Brown Dye → 1 · [Recipe][recipe-brown_candle] · [Loot][loot-brown_candle] | [Source][reg-brown_candle] |
| Green | <span id="green-candle"></span>`minecraft:green_candle` | 1 Candle + 1 Green Dye → 1 · [Recipe][recipe-green_candle] · [Loot][loot-green_candle] | [Source][reg-green_candle] |
| Red | <span id="red-candle"></span>`minecraft:red_candle` | 1 Candle + 1 Red Dye → 1 · [Recipe][recipe-red_candle] · [Loot][loot-red_candle] | [Source][reg-red_candle] |
| Black | <span id="black-candle"></span>`minecraft:black_candle` | 1 Candle + 1 Black Dye → 1 · [Recipe][recipe-black_candle] · [Loot][loot-black_candle] | [Source][reg-black_candle] |

## Placement and grouping

Place a candle above an upper face that supplies **center support**. A full solid block is a simple base. Candles stand upright; the state has no wall, ceiling, or facing mode. The ordinary block-item placement path checks support before placing. [Candle support][support] · [Center-support test][center] · [Block-item placement check][placement-check] · [Candle states][states]

Use another **identical candle item** on the existing group without secondary use to increase its count, up to four. Different colors cannot share one group. Adding a candle preserves the group's lit and waterlogged states: a lit group becomes brighter without a second ignition. Ordinary Survival placement consumes one item per candle. [Matching-item replacement and placement][grouping] · [Placement consumption][consume] · [Creative consumption exception][consume-guard]

**Support-loss detail:** this snapshot checks support for placement, but the standalone Candle's neighbor-shape callback only schedules water ticks and delegates to a default callback that retains the block. Removing the base therefore has no automatic support-loss removal in that checked path. This differs from [Cake](Cake.md#placement-and-collection). Adding more candles still runs placement's support check, and removing water with a bucket also checks support. [Candle neighbor updates][updates] · [Default neighbor behavior][default-update] · [Bucket removal and support check][pickup-check]

## Lighting and extinguishing

A new candle is unlit. Use [Flint and Steel](../items/FlintAndSteel.md) or a [Fire Charge](../items/FireCharge.md) on a dry, unlit group to light **the whole group at once**. Successful ordinary Survival use requests one Flint and Steel durability point or consumes one Fire Charge. Fully broken Flint and Steel cannot perform the player's item-use path; see [Durability](../mechanics/Durability.md). [Default state][defaults] · [Dry and unlit requirement][can-light] · [Flint and Steel lighting][flint] · [Fire Charge lighting and consumption][charge] · [Broken-item guard][broken]

A **burning projectile that actually hits the block** can also light it. The shared candle callback tests whether the projectile is on fire; merely flying near a candle does not call that hit path. Waterlogged candles reject this ignition too. A dispenser facing a dry, unlit group can light it through its separate Flint and Steel behavior. [Burning-projectile handler][projectile] · [Projectile block-hit dispatch][hit] · [Dispenser ignition][dispenser] · [Dispenser behavior dispatch][dispense-dispatch]

To turn a group off, use it with an **empty hand** while permitted to build. That extinguishes every candle in the group without consuming them. The checked block code has no burn-down timer or candle fuel cost; the lit state persists until an interaction changes it. [Empty-hand extinguishing][empty-hand] · [Shared extinguishing][extinguish] · [Registration properties][properties]

Two additional extinguishing routes also work for candles on Cake:

- A thrown **Water potion** hitting a block checks the hit block, the cell just outside its hit face, and that outer cell's four horizontal neighbors for lit candles. It turns them off without waterlogging them. This is a local block check, not every block inside the potion's entity splash radius
- A Wind Charge's trigger explosion can extinguish lit candles it affects. Breeze Wind Charge triggering also requires `mobGriefing`

[Water-potion checked cells][potion-checks] · [Candle extinguishing][potion-dowse] · [Trigger-explosion callback][wind-callback] · [Wind Charge explosion][wind] · [Breeze Wind Charge explosion][breeze] · [Trigger permission][trigger] · [Active trigger-mode mapping][trigger-map] · [Affected-block callback][explosion-dispatch]

## Water and light

Standalone candles **can waterlog**. Placing one into source Water preserves that water; filling a group with a Water Bucket sets its waterlogged state and extinguishes it. A waterlogged group cannot be lit by the checked ignition methods. The placement and liquid-insertion code require the source-Water fluid type, not merely any flowing water or a fluid with the same height. [Placement water check][placement-water] · [Filling and extinguishing][fill-water] · [Accepted water type][admission] · [Water Bucket insertion][bucket-fill]

An ordinary flowing-water state is not accepted by that insertion check. Water spreading can fill the candle only when its proposed fluid state is a source, such as a valid source-conversion result; water near the candle is not by itself an extinguish command. Removing its water with an empty bucket leaves the group unlit and ready to relight if it still has valid support. [Source and flowing-state proposals][fluid-proposal] · [Liquid-container admission][fluid-gate] · [Liquid-container insertion][fluid-insert] · [Water pickup][bucket-remove]

| Placed form | Unlit light | Lit light |
| --- | ---: | ---: |
| One standalone candle | 0 | 3 |
| Two matching candles | 0 | 6 |
| Three matching candles | 0 | 9 |
| Four matching candles | 0 | 12 |
| One candle on Cake | 0 | 3 |

Every color uses the same light calculation. Candle count controls light, but standalone candles do **not** provide a comparator count signal; Cake has its own [slice-count output](Cake.md#comparator-output). [Standalone light formula][light] · [Candle-cake light registration][cake-light] · [Lit emission helper][lit-formula] · [Default comparator capability][no-comparator]

## Mining and collection

All 17 standalone candle blocks have hardness and blast resistance **0.1** and no correct-tool requirement. None belongs to the four standard axe, pickaxe, shovel, or hoe mining tags. Ordinary Survival mining, including by hand, drops **the group's full count of matching candle items**: one, two, three, or four. Silk Touch and Fortune add nothing to these tables, and the items do not keep the group's lit or waterlogged state. Each color's exact table is linked above; explosion decay can reduce explosion drops. [Candle properties][mining-properties] · [Axe tag][axe] · [Pickaxe tag][pickaxe] · [Shovel tag][shovel] · [Hoe tag][hoe] · [Correct-tool check][tool-gate] · [Survival drop dispatch][mining]

Candles use the **destroy** piston reaction. A piston pushing into a group breaks it and runs the same loot table. A candle placed on Cake follows [the different Cake collection rules](Cake.md#adding-and-recovering-a-candle): breaking the cake destroys the food and returns only its candle. [Piston destruction drops][piston]

## Related pages

- [Cake](Cake.md), [Candle item](../items/Candle.md), and [White Candle item](../items/WhiteCandle.md)
- [Honeycomb](../items/Honeycomb.md), [Redstone Comparator](RedstoneComparator.md), and [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `beaa5747b36af51b001a13ce8b6648319ba6faf5` on 2026-10-02. All covered registry entries, producing recipes, loot tables, and relevant tags were checked against the active placement, interaction, food, projectile, fluid, and comparator code. No gameplay test of support removal, eating, lighting, water, or redstone output was run.

[count-light]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L36-L43
[items]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/Items.java#L2498-L2514
[base-recipe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/candle.json
[use]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L76-L93
[dye]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/DyeItem.java#L15-L56
[item-use]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/Item.java#L164-L166
[recipe-candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/candle.json
[loot-candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/candle.json
[reg-candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5884-L5884
[recipe-white_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/white_candle.json
[loot-white_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/white_candle.json
[reg-white_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5885-L5885
[recipe-orange_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/orange_candle.json
[loot-orange_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/orange_candle.json
[reg-orange_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5886-L5886
[recipe-magenta_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/magenta_candle.json
[loot-magenta_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/magenta_candle.json
[reg-magenta_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5887-L5887
[recipe-light_blue_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/light_blue_candle.json
[loot-light_blue_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/light_blue_candle.json
[reg-light_blue_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5888-L5888
[recipe-yellow_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/yellow_candle.json
[loot-yellow_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/yellow_candle.json
[reg-yellow_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5889-L5889
[recipe-lime_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/lime_candle.json
[loot-lime_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/lime_candle.json
[reg-lime_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5890-L5890
[recipe-pink_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/pink_candle.json
[loot-pink_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/pink_candle.json
[reg-pink_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5891-L5891
[recipe-gray_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/gray_candle.json
[loot-gray_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/gray_candle.json
[reg-gray_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5892-L5892
[recipe-light_gray_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/light_gray_candle.json
[loot-light_gray_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/light_gray_candle.json
[reg-light_gray_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5893-L5893
[recipe-cyan_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/cyan_candle.json
[loot-cyan_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/cyan_candle.json
[reg-cyan_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5894-L5894
[recipe-purple_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/purple_candle.json
[loot-purple_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/purple_candle.json
[reg-purple_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5895-L5895
[recipe-blue_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/blue_candle.json
[loot-blue_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/blue_candle.json
[reg-blue_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5896-L5896
[recipe-brown_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/brown_candle.json
[loot-brown_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/brown_candle.json
[reg-brown_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5897-L5897
[recipe-green_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/green_candle.json
[loot-green_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/green_candle.json
[reg-green_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5898-L5898
[recipe-red_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/red_candle.json
[loot-red_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/red_candle.json
[reg-red_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5899-L5899
[recipe-black_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/black_candle.json
[loot-black_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/black_candle.json
[reg-black_candle]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5900-L5900
[support]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L173-L176
[center]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Block.java#L323-L328
[placement-check]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L142
[states]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L135-L138
[grouping]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L88-L105
[consume]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/BlockItem.java#L79-L83
[consume-guard]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[updates]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L107-L123
[default-update]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L168
[pickup-check]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L38-L49
[defaults]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L71-L74
[can-light]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L157-L171
[flint]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java#L26-L57
[charge]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/FireChargeItem.java#L29-L55
[broken]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L365
[projectile]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/AbstractCandleBlock.java#L44-L53
[hit]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L276-L291
[dispenser]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L210-L238
[dispense-dispatch]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L108
[empty-hand]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L76-L85
[extinguish]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/AbstractCandleBlock.java#L83-L99
[properties]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L7245-L7252
[potion-checks]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L49-L67
[potion-dowse]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L110-L120
[wind-callback]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/AbstractCandleBlock.java#L101-L110
[wind]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java#L55-L72
[breeze]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/projectile/windcharge/BreezeWindCharge.java#L22-L39
[trigger]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/ServerExplosion.java#L291-L299
[trigger-map]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1167
[explosion-dispatch]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/ServerExplosion.java#L207-L214
[placement-water]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L95-L128
[fill-water]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L140-L160
[admission]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L22
[bucket-fill]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/BucketItem.java#L126-L139
[fluid-proposal]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L166-L202
[fluid-gate]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L420-L427
[fluid-insert]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[bucket-remove]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L38-L49
[light]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L36-L43
[cake-light]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L5901-L5906
[lit-formula]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L7128-L7130
[no-comparator]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L232-L234
[mining-properties]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java#L7245-L7252
[axe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[piston]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L287-L299
