# Small and tall flowers

Flowers provide movable decoration, dye ingredients, Bee forage, and selected Suspicious Stew effects. This guide covers **13 small flowers and four two-block flowers**, with exact IDs below. Collect a tall flower to start a renewable supply through Bone Meal; collect small flowers from suitable terrain or use the biome-dependent Grass Block route. [Small-flower registrations] · [Tall-flower registrations] · [Small-flower items] · [Tall-flower items]

[Torchflower](../items/Torchflower.md) and [Pitcher Plant](../items/PitcherPlant.md) belong to separate crop lifecycles. [Pink Petals](../items/PinkPetals.md), [Wildflowers](../items/Wildflowers.md), Eyeblossoms, Firefly Bush, and imported Primordial plants are outside this comparison. Potted forms belong to [Flower Pot](FlowerPot.md#supported-plants).

## Small-flower variants

Each row identifies the **block and matching item**. Putting one flower in a crafting grid produces the listed dye; these recipes consume the flower. The stew column gives the effect written by that flower's exact recipe, at level I. See [Suspicious Stew](../items/SuspiciousStew.md#obtaining) for the shared ingredients and eating behavior. Durations are ticks; seconds assume 20 TPS. [Shapeless recipe execution] · [Ingredient consumption] · [Stew effect application]

| Flower and exact ID | Dye from one flower | Suspicious Stew effect | Registry and ordinary loot |
| --- | --- | --- | --- |
| <span id="dandelion"></span>[Dandelion](../items/Dandelion.md) (`minecraft:dandelion`) | **1 Yellow Dye** [Dye dandelion] | Saturation I, **7 ticks / 0.35 s** [Stew dandelion] | [Registry dandelion] · [Loot dandelion] |
| <span id="poppy"></span>[Poppy](../items/Poppy.md) (`minecraft:poppy`) | **1 Red Dye** [Dye poppy] | Night Vision I, **100 ticks / 5 s** [Stew poppy] | [Registry poppy] · [Loot poppy] |
| <span id="blue-orchid"></span>[Blue Orchid](../items/BlueOrchid.md) (`minecraft:blue_orchid`) | **1 Light Blue Dye** [Dye blue_orchid] | Saturation I, **7 ticks / 0.35 s** [Stew blue_orchid] | [Registry blue_orchid] · [Loot blue_orchid] |
| <span id="allium"></span>[Allium](../items/Allium.md) (`minecraft:allium`) | **1 Magenta Dye** [Dye allium] | Fire Resistance I, **60 ticks / 3 s** [Stew allium] | [Registry allium] · [Loot allium] |
| <span id="azure-bluet"></span>[Azure Bluet](../items/AzureBluet.md) (`minecraft:azure_bluet`) | **1 Light Gray Dye** [Dye azure_bluet] | Blindness I, **220 ticks / 11 s** [Stew azure_bluet] | [Registry azure_bluet] · [Loot azure_bluet] |
| <span id="red-tulip"></span>[Red Tulip](../items/RedTulip.md) (`minecraft:red_tulip`) | **1 Red Dye** [Dye red_tulip] | Weakness I, **140 ticks / 7 s** [Stew red_tulip] | [Registry red_tulip] · [Loot red_tulip] |
| <span id="orange-tulip"></span>[Orange Tulip](../items/OrangeTulip.md) (`minecraft:orange_tulip`) | **1 Orange Dye** [Dye orange_tulip] | Weakness I, **140 ticks / 7 s** [Stew orange_tulip] | [Registry orange_tulip] · [Loot orange_tulip] |
| <span id="white-tulip"></span>[White Tulip](../items/WhiteTulip.md) (`minecraft:white_tulip`) | **1 Light Gray Dye** [Dye white_tulip] | Weakness I, **140 ticks / 7 s** [Stew white_tulip] | [Registry white_tulip] · [Loot white_tulip] |
| <span id="pink-tulip"></span>[Pink Tulip](../items/PinkTulip.md) (`minecraft:pink_tulip`) | **1 Pink Dye** [Dye pink_tulip] | Weakness I, **140 ticks / 7 s** [Stew pink_tulip] | [Registry pink_tulip] · [Loot pink_tulip] |
| <span id="oxeye-daisy"></span>[Oxeye Daisy](../items/OxeyeDaisy.md) (`minecraft:oxeye_daisy`) | **1 Light Gray Dye** [Dye oxeye_daisy] | Regeneration I, **140 ticks / 7 s** [Stew oxeye_daisy] | [Registry oxeye_daisy] · [Loot oxeye_daisy] |
| <span id="cornflower"></span>[Cornflower](../items/Cornflower.md) (`minecraft:cornflower`) | **1 Blue Dye** [Dye cornflower] | Jump Boost I, **100 ticks / 5 s** [Stew cornflower] | [Registry cornflower] · [Loot cornflower] |
| <span id="lily-of-the-valley"></span>[Lily of the Valley](../items/LilyOfTheValley.md) (`minecraft:lily_of_the_valley`) | **1 White Dye** [Dye lily_of_the_valley] | Poison I, **220 ticks / 11 s** [Stew lily_of_the_valley] | [Registry lily_of_the_valley] · [Loot lily_of_the_valley] |
| <span id="wither-rose"></span>[Wither Rose](../items/WitherRose.md) (`minecraft:wither_rose`) | **1 Black Dye** [Dye wither_rose] | Wither I, **140 ticks / 7 s** [Stew wither_rose] | [Registry wither_rose] · [Loot wither_rose] |

The effect is attached to the prepared stew. It does not make an ordinary planted Poppy grant Night Vision or a Lily of the Valley poison passersby. Among these 13, **Wither Rose has the separate harmful contact callback** described below. The other twelve use the ordinary flower class, whose Bee interaction effect is empty. [Small-flower class] · [Wither Rose contact]

## Tall-flower variants

One item places both halves. A complete plant normally returns **one flower item**, and one item crafts into **two dyes**. These four are not ingredients in the checked flower-stew recipes and their class does not supply a suspicious-stew effect. [Tall-flower items] · [Two-block placement and support] · [Tall-flower duplication] · [Flower effect lookup]

| Flower and exact ID | Dye from one flower | Registry and ordinary loot |
| --- | --- | --- |
| <span id="sunflower"></span>[Sunflower](../items/Sunflower.md) (`minecraft:sunflower`) | **2 Yellow Dyes** [Dye sunflower] | [Registry sunflower] · [Loot sunflower] |
| <span id="lilac"></span>[Lilac](../items/Lilac.md) (`minecraft:lilac`) | **2 Magenta Dyes** [Dye lilac] | [Registry lilac] · [Loot lilac] |
| <span id="rose-bush"></span>[Rose Bush](../items/RoseBush.md) (`minecraft:rose_bush`) | **2 Red Dyes** [Dye rose_bush] | [Registry rose_bush] · [Loot rose_bush] |
| <span id="peony"></span>[Peony](../items/Peony.md) (`minecraft:peony`) | **2 Pink Dyes** [Dye peony] | [Registry peony] · [Loot peony] |

## Planting and support

Plant these flowers on **Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots, or Farmland**. That is the current dirt-tag expansion plus the separately accepted Farmland block. Sand, ordinary Mangrove Roots, and Dirt Path are not on that list. The flower survival check has **no light, hydration, or nearby-water requirement**. Wither Rose additionally accepts **Netherrack, Soul Sand, and Soul Soil**. [Plant support] · [Accepted soil tag] · [Wither Rose extra support] · [Item placement checks]

Tall flowers need a placeable upper cell within the world's build height. Their lower half sits on the soil; their upper half requires the matching lower half. Removing a necessary support or either half removes the unsupported remainder through neighbor updates. Small flowers occupy one cell and also break when their supporting soil becomes invalid. These blocks have no facing state to choose during placement. [Two-block placement and support] · [Plant support] · [Support-loss removal] · [Small-flower class]

All 17 have no entity collision and **no waterlogged state**. Water that spreads into a flower's cell replaces it and invokes its ordinary block loot. A tall plant's lower-half loot rule still produces one matching flower when the intact plant is removed this way. Keep planted displays out of water channels unless harvesting them is intended. [Small-flower registrations] · [Tall-flower registrations] · [Two-block placement and support] · [Fluid admission] · [Fluid replacement] · [Water harvest drops]

## Harvesting and propagation

**Break a flower by hand to collect it.** All 17 break instantly and have no correct-tool requirement. The loot tables linked in the variant tables have no Silk Touch or Fortune branch: each small flower yields one matching item, and only the lower half of a tall flower yields its one item. Mining either half of a complete tall plant removes the pair without giving two flowers. Explosion-survival conditions can reduce recovery from blasts. [Small-flower registrations] · [Tall-flower registrations] · [Correct-tool drop gate] · [Survival mining dispatch] · [Tall-flower mining]

Use **one Bone Meal on either half of a planted Sunflower, Lilac, Rose Bush, or Peony** to drop **one additional matching flower item** while keeping the original plant. The target and success checks both return true; this duplication does not search for adjacent planting space. It is an item drop, so collect it before it is lost. Under ordinary Survival use, the accepted operation consumes one Bone Meal. [Tall-flower duplication] · [Bone Meal dispatch]

**The 13 small flowers cannot be duplicated by applying Bone Meal directly to them.** They do not implement the Bone Meal target interface, and leaving a planted flower alone does not give these registrations a natural spreading or maturity cycle. Use Grass Block instead when a suitable biome flower feature is available. [Small-flower class] · [Small-flower registrations] · [Tall-flower registrations] · [Bone Meal dispatch]

### Bone Meal on Grass Block

Use Bone Meal on a **Grass Block with air immediately above it**. The growth routine makes 128 candidate attempts, walking across nearby Grass Blocks and avoiding full collision blocks. At an empty candidate cell it chooses the biome's flower route with **1/8 probability**, otherwise its grass route. The flower route selects from that location's eligible biome flower features, then still has to pass the feature's placement and survival conditions. This is **not a promise of 16 flowers**, a fixed output species, or a fixed number of items per use. Nearby planted flowers are not copied. [Grass Bone Meal target] · [Grass propagation] · [Biome flower filtering] · [Final plant placement]

Useful source-backed choices:

- **Swamp:** the selected flower feature produces **Blue Orchids**. [Swamp biome] · [Swamp flower placement] · [Swamp flower selection]
- **Plains:** a position-dependent noise provider selects Dandelion, Poppy, Azure Bluet, Oxeye Daisy, Cornflower, or one of the four tulips. Moving within the biome can change the flower selection; this is not a uniform roll across all nine. [Plains biome] · [Plains flower placement] · [Plains flower selection] · [Plains noise and random choices]
- **Flower Forest:** its flower feature includes Dandelion, Poppy, Allium, Azure Bluet, all four tulips, Oxeye Daisy, Cornflower, and Lily of the Valley, selected by a position-dependent noise provider. Blue Orchid and Wither Rose are absent from that feature. [Flower Forest biome] · [Flower Forest placement] · [Flower Forest flower selection] · [Position-selected flower state]
- **Forest:** its eligible ordinary flower feature uses Poppy and Dandelion. Its separate Lilac/Rose Bush/Peony patches use `random_patch`, and its Lily of the Valley patch uses `no_bonemeal_flower`. Those patches are **excluded from Grass Block's flower-feature filter**. Collect those plants from successful natural patches; duplicate the three tall species after planting them. Lily of the Valley remains available through Flower Forest's different eligible feature. [Forest biome] · [Default flower placement] · [Default flower selection] · [Forest flower placement] · [Forest flower selection] · [Biome flower filtering] · [Flower feature types]

## Finding the first plants

The biome routes above are also examples of registered natural placement. The active terrain-decoration loop runs each biome's placed features, which apply their count, rarity, location, and biome filters before trying to place a plant. Space and soil can still reject a candidate; a biome's feature list does not guarantee a visible flower at every location. [Active biome decoration] · [Final plant placement] · [Forest flower placement] · [Flower Forest placement]

**Sunflower Plains** additionally selects the Sunflower patch feature. Collect one successfully generated Sunflower and use planted duplication for more; the Sunflower patch is a `random_patch`, so its presence does not add Sunflowers to Grass Block's flower selection. These are checked examples, not a complete list of all biomes, structures, or trades containing flowers. [Sunflower Plains biome] · [Sunflower patch placement] · [Sunflower patch] · [Biome flower filtering]

### Wither Rose creation and hazard

A verified Wither Rose source is a living entity's death routine **when the kill credit is a Wither**. With `mobGriefing` enabled, it first tries to place a rose in air at the victim's block position on valid support. If placement fails, or `mobGriefing` is disabled, it spawns a loose Wither Rose item instead. The kill-credit helper prefers a recorded player over the last attacking mob, so a Wither dealing the final blow alone does not guarantee this route. See [Wither](../mobs/Wither.md) for the boss encounter. [Wither Rose creation] · [Kill-credit selection]

A placed Wither Rose attempts to apply **Wither I for 40 ticks** to a living entity inside its block on the server, outside Peaceful difficulty, unless that entity is invulnerable to the Wither damage source. Normal effect acceptance and immunity rules still apply. Continued contact can refresh the effect; its **140-tick stew effect is a separate value**. Keep the plant away from walkways and animal pens. [Wither Rose contact] · [Stew wither_rose]

## Bees, pots, and other uses

All 17 flowers are in both the **Bee food item tag** and **Bee-attractive block tag**, but **Wither Rose is a poor apiary choice**: feeding it applies its Wither effect through a special interaction instead of ordinary breeding, and the planted rose can harm a visiting Bee. The other sixteen are ordinary food-tag choices. Pollination checks the **upper half of a Sunflower**; the other tall flowers do not have that special half filter. See [Bee](../mobs/Bee.md#flowers-and-pollination) for reachability, weather, nectar, and housing behavior. [Bee food items] · [Bee-attractive blocks] · [Bee feeding] · [Bee flower checks]

All thirteen small species can be displayed in a [Flower Pot](FlowerPot.md#supported-plants). The four tall flowers have no filled-pot registration. Potting, removal, filled-pot drops, and the pot's distinct support behavior are covered there.

An **Oxeye Daisy plus one Paper** crafts **one [Flower Banner Pattern](../items/FlowerBannerPattern.md)**, using a shapeless recipe. All 17 flower items are also accepted by the [Composter](../items/Composter.md) with a **65% configured chance** to advance a partially filled composter; the first accepted item in an empty composter advances it automatically. [Flower banner-pattern recipe] · [Flower compost values] · [Compost chance and first layer]

Related: [Bone Meal](../items/BoneMeal.md) · [Suspicious Stew](../items/SuspiciousStew.md) · [Soil, sand, and gravel](SoilSandAndGravel.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked all 17 block/item registrations, all 17 full block-loot tables, every selected dye and stew recipe, supporting and Bee tags, active planting/mining/water/Bone Meal callbacks, selected natural feature chains, Wither Rose creation/contact, compost values, and the Daisy banner-pattern recipe. Crop lifecycles, other plant families, and untraced acquisition routes are outside this page’s scope. No in-game placement, harvesting, crafting, propagation, feeding, or world-generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Small-flower registrations]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L905-L1060
[Tall-flower registrations]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3286-L3333
[Small-flower items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L342-L356
[Tall-flower items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L712-L715
[Plant support]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Accepted soil tag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/dirt.json
[Item placement checks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BlockItem.java#L111-L138
[Two-block placement and support]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L37-L99
[Support-loss removal]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Block.java#L213-L225
[Tall-flower mining]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L134
[Small-flower class]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Tall-flower duplication]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/TallFlowerBlock.java#L13-L37
[Fluid admission]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Fluid replacement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L276
[Water harvest drops]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Grass Bone Meal target]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L39
[Grass propagation]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L43-L89
[Biome flower filtering]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L47-L66
[Flower feature types]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L61-L66
[Survival mining dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[Correct-tool drop gate]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Bee food items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/bee_food.json
[Bee-attractive blocks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[Bee feeding]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/Bee.java#L569-L590
[Bee flower checks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L678
[Wither Rose extra support]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/WitherRoseBlock.java#L44-L49
[Wither Rose contact]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/WitherRoseBlock.java#L74-L89
[Wither Rose creation]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1463
[Kill-credit selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1854-L1860
[Shapeless recipe execution]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L91
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Stew effect application]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L71
[Flower effect lookup]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/SuspiciousEffectHolder.java#L23-L29
[Forest biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/forest.json
[Forest flower placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/forest_flowers.json
[Forest flower selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/forest_flowers.json
[Default flower placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/flower_default.json
[Default flower selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/flower_default.json
[Flower Forest biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/flower_forest.json
[Flower Forest placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/flower_flower_forest.json
[Flower Forest flower selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/flower_flower_forest.json
[Plains biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/plains.json
[Plains flower placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/flower_plains.json
[Plains flower selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/flower_plain.json
[Swamp biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[Swamp flower placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/flower_swamp.json
[Swamp flower selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/flower_swamp.json
[Sunflower Plains biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/sunflower_plains.json
[Sunflower patch placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/patch_sunflower.json
[Sunflower patch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/patch_sunflower.json
[Position-selected flower state]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/levelgen/feature/stateproviders/NoiseProvider.java#L34-L46
[Plains noise and random choices]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/levelgen/feature/stateproviders/NoiseThresholdProvider.java#L50-L58
[Active biome decoration]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Final plant placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L16-L42
[Flower compost values]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L146-L165
[Compost chance and first layer]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[Flower banner-pattern recipe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/flower_banner_pattern.json
[Dye dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_dandelion.json
[Stew dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_dandelion.json
[Registry dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L905-L915
[Loot dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/dandelion.json
[Dye poppy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_poppy.json
[Stew poppy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_poppy.json
[Registry poppy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L928-L938
[Loot poppy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/poppy.json
[Dye blue_orchid]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/light_blue_dye_from_blue_orchid.json
[Stew blue_orchid]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_blue_orchid.json
[Registry blue_orchid]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L939-L949
[Loot blue_orchid]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/blue_orchid.json
[Dye allium]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_allium.json
[Stew allium]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_allium.json
[Registry allium]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L950-L960
[Loot allium]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/allium.json
[Dye azure_bluet]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_azure_bluet.json
[Stew azure_bluet]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_azure_bluet.json
[Registry azure_bluet]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L961-L971
[Loot azure_bluet]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/azure_bluet.json
[Dye red_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_tulip.json
[Stew red_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_red_tulip.json
[Registry red_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L972-L982
[Loot red_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/red_tulip.json
[Dye orange_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_orange_tulip.json
[Stew orange_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_orange_tulip.json
[Registry orange_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L983-L993
[Loot orange_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/orange_tulip.json
[Dye white_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_white_tulip.json
[Stew white_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_white_tulip.json
[Registry white_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L994-L1004
[Loot white_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/white_tulip.json
[Dye pink_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_pink_tulip.json
[Stew pink_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_pink_tulip.json
[Registry pink_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1005-L1015
[Loot pink_tulip]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/pink_tulip.json
[Dye oxeye_daisy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_oxeye_daisy.json
[Stew oxeye_daisy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_oxeye_daisy.json
[Registry oxeye_daisy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1016-L1026
[Loot oxeye_daisy]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/oxeye_daisy.json
[Dye cornflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/blue_dye_from_cornflower.json
[Stew cornflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_cornflower.json
[Registry cornflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1027-L1037
[Loot cornflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/cornflower.json
[Dye lily_of_the_valley]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/white_dye_from_lily_of_the_valley.json
[Stew lily_of_the_valley]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_lily_of_the_valley.json
[Registry lily_of_the_valley]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1049-L1059
[Loot lily_of_the_valley]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/lily_of_the_valley.json
[Dye wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/black_dye_from_wither_rose.json
[Stew wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_wither_rose.json
[Registry wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1038-L1048
[Loot wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/wither_rose.json
[Dye sunflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_sunflower.json
[Registry sunflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3286-L3297
[Loot sunflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/sunflower.json
[Dye lilac]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_lilac.json
[Registry lilac]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3298-L3309
[Loot lilac]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/lilac.json
[Dye rose_bush]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_rose_bush.json
[Registry rose_bush]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3310-L3321
[Loot rose_bush]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/rose_bush.json
[Dye peony]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_peony.json
[Registry peony]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3322-L3333
[Loot peony]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/peony.json
