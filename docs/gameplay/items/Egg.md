# Egg

**Egg** (`minecraft:egg`) is the ordinary egg laid by temperate chickens. It stacks to **16**, can be thrown to try to hatch chicks, and is used in food recipes. **It is not directly edible:** using the held item throws it. [Item registration][item] · [Throwing interaction][use]

## Obtaining

Keep an adult temperate [Chicken](../mobs/Chicken.md). Its laying loot table supplies one ordinary egg when it lays; warm chickens supply [Brown Eggs](BrownEgg.md), and cold chickens supply [Blue Eggs](BlueEgg.md). See [Chicken egg production](../mobs/Chicken.md#egg-production-and-variants) for the timer, adult requirement, and chicken-jockey exclusion. This is a confirmed renewable route, not a complete list of possible loot sources. [Laying loot][lay]

An egg is not chicken breeding food. To breed two adults or speed a chick's growth by feeding, use an item listed in the [Chicken food guide](../mobs/Chicken.md#food-and-breeding). [Chicken-food tag][food]

## Throwing and hatching

Use an egg while holding it to launch it, consuming **one egg in Survival**. On a server-side impact, it rolls:

- **1 in 8** to attempt hatching
- Within that successful roll, **1 in 32** to attempt **four chicks** instead of one

The four-chick outcome is therefore **1 in 256 throws** before spawn checks; the one-chick outcome is **31 in 256**. Entity creation and space checks can prevent actual chicks from appearing. Throw into a roomy enclosure rather than treating each successful random roll as a guaranteed addition to the flock. The projectile is discarded after impact. [Throw and consumption][use] · [Impact and hatch checks][hatch]

The chick inherits the variant stored on the egg item: the bundled ordinary egg produces temperate chicks, blue eggs cold chicks, and brown eggs warm chicks. Hatching does not select a new variant from the impact biome. New chicks start at **24,000 ticks until adulthood**; see [Chicken](../mobs/Chicken.md#food-and-breeding) for growth and feeding. [Egg components][item] · [Variant assignment and age][hatch]

A [Dispenser](Dispenser.md) also launches all three egg types. It is another way to attempt hatching, rather than an incubation block. [Dispenser registration][dispenser]

## Selected recipes

Both recipes below accept **ordinary, brown, or blue eggs** through the bundled eggs tag:

| Result | Ingredients and arrangement |
| --- | --- |
| 1 [Cake](Cake.md) | Top row: 3 milk buckets; middle: sugar, egg, sugar; bottom: 3 wheat |
| 1 [Pumpkin Pie](PumpkinPie.md) | 1 pumpkin + 1 sugar + 1 egg, shapeless |

These are selected uses, not every possible recipe. Save eggs for crafting when your flock is already large enough. [Eggs tag][eggs] · [Cake recipe][cake] · [Pumpkin-pie recipe][pie]

Related: [Chicken](../mobs/Chicken.md) · [Brown Egg](BrownEgg.md) · [Blue Egg](BlueEgg.md) · [Crafting](../crafting/Crafting.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game throwing, hatch-rate, dispenser, or crafting test was run. Data packs and customized item components can change the bundled loot, recipes, and egg variant data described here.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1600-L1608
[use]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/EggItem.java#L23-L41
[lay]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/chicken_lay.json
[food]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/chicken_food.json
[hatch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/ThrownEgg.java#L60-L89
[dispenser]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L67-L73
[eggs]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/eggs.json
[cake]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/cake.json
[pie]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/pumpkin_pie.json
