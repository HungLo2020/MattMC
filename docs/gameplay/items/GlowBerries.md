# Glow Berries

**Glow Berries** (`minecraft:glow_berries`) are both food and the planting item for **Cave Vines**. The placed tip is `minecraft:cave_vines`; its body is `minecraft:cave_vines_plant`. There is no separate Cave Vine inventory item. [Glow Berries block item]

## Obtaining

Use a berry-bearing Cave Vine segment to drop **one Glow Berry** while keeping the vine. Breaking a fruiting segment also yields one; a bare segment yields none. Shears, Silk Touch, and Fortune do not increase those drops. [Lush Caves' registered vine feature](../blocks/Vines.md#source-backed-places-to-collect-starters) is a checked natural source. [Berry collection and light] · [Berry harvest loot] · [Loot cave_vines] · [Loot cave_vines_plant] · [Cave source biome] · [Cave placed feature] · [Cave configured feature]

## Usage

Use the berry item on a suitable underside to plant a hanging Cave Vine. A newly planted tip has no berries; **Bone Meal on a bare tip or body makes that segment fruit**, without extending the column. Pick the fruit, apply Bone Meal again, and collect the next berry while retaining the planted segment. [Glow Berries block item] · [Column placement and support] · [Cave tip growth and berries] · [Cave body berries and conversion] · [Bone Meal use and consumption]

Eating supplies **2 hunger points and 0.4 saturation points before caps** through the ordinary food component. See [Hunger, saturation, and healing](../mechanics/Hunger.md) for the shared limits. Glow Berries are also a [Fox food](../mobs/Fox.md#breeding-a-trusting-fox). [Glow Berries food value] · [Saturation calculation] · [Eating food] · [Hunger and saturation caps] · [Default food components]

## Behavior

A fruiting Cave Vine emits **light level 14**; picking its berry removes that segment's light until it fruits again. Natural growth rolls berry presence on newly extended tips, rather than passively refilling every picked segment. [The Cave Vine guide](../blocks/Vines.md#cave-vines-and-glow-berries) explains growth age, Bee assistance, trimming, support, and collection priority. [Cave-vine registrations] · [Berry collection and light] · [Cave tip growth and berries] · [Cave body berries and conversion]

## Notes

Cave Vines are climbable, but cannot be waterlogged. No producing crafting or smelting recipe for Glow Berries was found in the checked recipe set. [Climbable block tag] · [Cave tip growth and berries] · [Cave body berries and conversion]

## Sources and verification

Source-reviewed on **2026-10-02** at `88d85ee594bc770fa362129690eadb127272dc20`. Checked the item/food components, both Cave Vine block loot tables and the harvest table, berry use and Bone Meal callbacks, light, shared growth/support behavior, and the listed Lush Caves acquisition route. No in-game placement, climbing, growth, harvesting, crafting, eating, or generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Loot cave_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/cave_vines.json
[Loot cave_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/cave_vines_plant.json
[Cave-vine registrations]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6546-L6568
[Glow Berries block item]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L2452-L2454
[Column placement and support]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantBlock.java#L32-L62
[Cave tip growth and berries]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CaveVinesBlock.java#L28-L86
[Cave body berries and conversion]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CaveVinesPlantBlock.java#L27-L69
[Berry collection and light]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CaveVines.java#L23-L53
[Berry harvest loot]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/harvest/cave_vine.json
[Bone Meal use and consumption]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Climbable block tag]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[Glow Berries food value]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/Foods.java#L43
[Saturation calculation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L84
[Eating food]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L56
[Hunger and saturation caps]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/FoodData.java#L14-L29
[Default food components]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[Cave source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[Cave placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/cave_vines.json
[Cave configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/cave_vine.json
