# Blue Egg

**Blue Egg** (`minecraft:blue_egg`) is the egg laid by **cold chickens**. It stacks to **16** and can hatch cold chicks or replace an ordinary egg in selected food recipes. **It is not directly edible:** using a held egg throws it. [Item registration][item] · [Laying loot][lay-loot] · [Use action][use]

## Obtaining

Keep a living adult cold [Chicken](../mobs/Chicken.md) and collect the **one blue egg** it drops when it lays. Babies and chickens marked as chicken jockeys do not lay eggs. See [Chicken egg production](../mobs/Chicken.md#egg-production-and-variants) for the shared laying timer and farming details. [Laying conditions][laying] · [Variant-specific loot][lay-loot]

A [taiga](../biomes/TaigaAndSnowyBiomes.md) is one confirmed place to look: its bundled spawn table includes chickens, and the biome matches the cold-variant selection rule. This is an example, not a complete distribution list; the [Chicken finding guide](../mobs/Chicken.md#finding-chickens) covers the ground and light requirements. [Biome spawns][spawn] · [Variant rule][variant] · [Biome tag][biomes] · [Spawn selection][variant-selection]

## Behavior

Throw a blue egg, or launch it from a [Dispenser](Dispenser.md), to try to hatch chicks. A successfully hatched chick inherits the egg's **cold variant**, even in a different biome. For the shared hatch odds and space checks, see [Egg: throwing and hatching](Egg.md#throwing-and-hatching). [Stored variant][item] · [Hatching][hatch] · [Dispenser support][dispenser]

## Usage

The bundled **[Cake](Cake.md)** and **[Pumpkin Pie](PumpkinPie.md)** recipes accept blue eggs through the eggs tag, alongside ordinary and brown eggs. See [Egg recipes](Egg.md#selected-recipes) for the ingredients and arrangement. [Eggs tag][eggs] · [Cake recipe][cake] · [Pumpkin-pie recipe][pie]

## Notes

Related: [Egg](Egg.md) · [Brown Egg](BrownEgg.md) · [Chicken](../mobs/Chicken.md) · [Items](Items.md)

### Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game laying, variant, hatching, dispenser, or crafting test was run. These are bundled defaults; data packs and customized item components can change the described behavior.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1600-L1608
[use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/EggItem.java#L23-L41
[laying]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Chicken.java#L69-L122
[lay-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/chicken_lay.json
[variant-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Chicken.java#L164-L170
[hatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/ThrownEgg.java#L60-L89
[dispenser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L66-L73
[eggs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/eggs.json
[cake]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/cake.json
[pie]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/pumpkin_pie.json
[variant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/chicken_variant/cold.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_cold_variant_farm_animals.json
[spawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/biome/taiga.json
