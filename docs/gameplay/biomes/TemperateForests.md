# Temperate forests and Mushroom Fields

Choose **Forest for mixed Oak/Birch**, **the Birch forests for Birch timber and Wildflowers**, **Flower Forest for a broad flower palette**, **Dark Forest for Dark Oak and large mushrooms**, or **Pale Garden for Pale Oak, pale moss, and the natural Creaking Heart route**. **Mushroom Fields** is a separate fungal destination with Mycelium and Mooshroom candidates. These seven IDs are all selected by the bundled Normal Overworld. [Normal preset][normal] · [Parameter preset][parameters] · [Provider][provider] · [Selection][selection]

## Compare destinations

| Destination | Checked resource distinction | Passive-creature list distinction |
| --- | --- | --- |
| [Forest](#forest) | Oak/Birch trees with Leaf Litter decorators | Farm animals plus Wolves |
| [Flower Forest](#flower-forest) | Oak/Birch with a wider small-flower palette | Farm animals plus Rabbits |
| [Birch Forest](#birch-forest) | Birch trees and Wildflowers | Sheep, Pigs, Chickens, Cows |
| [Old Growth Birch Forest](#old-growth-birch-forest) | Tall-Birch branch as well as ordinary Birch | Same four farm-animal candidates |
| [Dark Forest](#dark-forest) | Dark Oak, other trees, huge mushrooms, Leaf Litter | Same four farm-animal candidates |
| [Pale Garden](#pale-garden) | Pale Oak, pale moss, Eyeblossoms, conditional Hearts | Empty creature list; ordinary monsters remain listed |
| [Mushroom Fields](#mushroom-fields) | Huge mushrooms and Mycelium surface rule | Mooshrooms; empty biome monster list |

A listed feature is an attempt subject to supporting blocks, space, placement, and biome checks. A listed mob still needs its own spawning rules. The individual biome sources below support this comparison; it is not a count of guaranteed trees or encounters.

## Forest

**ID: `minecraft:forest`.** Forest is a mixed timber stop. Its tree selector includes standing Oak, fancy Oak, and Birch routes plus fallen-tree branches. The standing routes in this selection carry Leaf Litter decoration. This is a source-backed reason to search below generated trees, rather than an assumption that every Leaf block drops litter. Use [tree materials](../blocks/TreeLogsAndRoots.md), [tree leaves](../blocks/TreeLeaves.md), and [Leaf Litter](../blocks/FlowerbedsAndLeafLitter.md) for harvesting and use. [Biome][biome-forest] · [Tree placement][place-trees_birch_and_oak_leaf_litter] · [Tree selection][config-trees_birch_and_oak_leaf_litter]

The separate forest-flower feature selects Lilac, Rose Bush, Peony, or Lily of the Valley patches; ordinary flowers and small mushrooms have additional entries. Their natural placement is distinct from which flowers Bone Meal can select on Grass Block, covered by [Flowers](../blocks/Flowers.md#bone-meal-on-grass-block). Forest also lists Wolves alongside Sheep, Pigs, Chickens, and Cows. Flower Forest does not retain that Wolf entry. [Biome][biome-forest] · [Flower placement][place-forest_flowers] · [Forest flower configuration][config-forest_flowers] · [Flower Forest comparison][biome-flower_forest]

## Flower Forest

**ID: `minecraft:flower_forest`.** Choose Flower Forest for a collection trip: its small-flower noise provider contains Dandelion, Poppy, Allium, Azure Bluet, four tulip colors, Oxeye Daisy, Cornflower, and Lily of the Valley. The palette varies by position, so a single clearing need not contain every flower. It also retains a separate tall-flower/forest-flower route. [Biome][biome-flower_forest] · [Small-flower placement][place-flower_flower_forest] · [Small-flower palette][config-flower_flower_forest] · [Forest-flower configuration][config-forest_flowers]

The tree selector supplies Oak, fancy Oak, Birch, and a fallen-Birch branch. Its standing tree choices use bee-bearing configurations, so look for **occupied nests**, not a Bee entry in the biome's creature table. The creature table adds Rabbits to the four farm-animal types and omits Wolves. See [Bee](../mobs/Bee.md#finding-bees) for nest conditions and [Flowers](../blocks/Flowers.md) for safe collection and propagation. [Tree placement][place-trees_flower_forest] · [Tree selector][config-trees_flower_forest] · [Nest implementation][nest-call] · [Biome][biome-flower_forest]

## Birch Forest

**ID: `minecraft:birch_forest`.** Birch Forest is the straightforward Birch timber destination. Its tree selector uses ordinary Birch with a fallen-Birch branch, and its biome supplies Wildflowers through a separate placed feature. Forest-flower patches and ordinary flowers are also listed; Wildflowers are not simply the page's name for all of those plants. [Biome][biome-birch_forest] · [Tree placement][place-trees_birch] · [Tree selector][config-trees_birch] · [Wildflower placement][place-wildflowers_birch_forest] · [Wildflower configuration][config-wildflowers_birch_forest]

The passive list is Sheep, Pigs, Chickens, and Cows, with no Wolf or Rabbit entry. Choose this biome for Birch and ground cover; choose Forest for its Wolf candidate or Flower Forest for its expanded small-flower palette. Use [saplings](../blocks/SaplingsAndAzaleas.md) to renew your timber supply and [flowerbeds](../blocks/FlowerbedsAndLeafLitter.md) to propagate collected Wildflowers. [Biome][biome-birch_forest]

## Old Growth Birch Forest

**ID: `minecraft:old_growth_birch_forest`.** This variant shares Birch Forest's Wildflowers, forest-flower routes, and four farm-animal candidates, but changes the tree selector. It can choose tall Birch, ordinary Birch, and fallen variants. **Old growth does not mean every tree is tall:** the selector still has ordinary Birch as its default, and each chosen feature must successfully place. Plan access to higher logs without treating the variant name as a fixed trunk-height promise. [Biome][biome-old_growth_birch_forest] · [Tree placement][place-birch_tall] · [Tree selector][config-birch_tall] · [Wildflower placement][place-wildflowers_birch_forest]

It is also the only destination on this page included in the checked **Trail Ruins** biome tag. That gives it a separate exploration reason beyond wood collection. The Trail Ruins structure definition and structure set are active, but terrain and start placement still decide individual finds. [Eligibility][tag-trail_ruins] · [Definition][structure-trail_ruins] · [Placement set][set-trail_ruins]

## Dark Forest

**ID: `minecraft:dark_forest`.** Dark Forest's vegetation selector mixes **Dark Oak**, Oak, fancy Oak, Birch, fallen trees, and huge red/brown mushrooms. A Dark Forest tree is therefore not automatically Dark Oak. The biome also adds a separate Leaf Litter patch, ordinary forest-flower entries, and small-mushroom entries. Search by the actual tree or mushroom you need; use [tree materials](../blocks/TreeLogsAndRoots.md) and [Mushrooms](../blocks/Mushrooms.md) for the different harvest rules. [Biome][biome-dark_forest] · [Vegetation placement][place-dark_forest_vegetation] · [Vegetation selector][config-dark_forest_vegetation]

Sheep, Pigs, Chickens, and Cows remain in the passive list, and the normal hostile list is populated. Bring lighting and a marked return route while searching. Dark Forest and Pale Garden are the two allowed biomes in the bundled **Woodland Mansion** tag; neither a canopy nor that eligibility guarantees a Mansion nearby. See [Structures](../structures/Structures.md) before turning a timber trip into a structure expedition. [Biome][biome-dark_forest] · [Mansion tag][tag-woodland_mansion] · [Mansion definition][structure-mansion] · [Mansion placement set][set-woodland_mansions]

## Pale Garden

**ID: `minecraft:pale_garden`.** Pale Garden supplies **Pale Oak**, [pale-moss](../blocks/MossAndPaleMoss.md) tree decoration and ground patches, and **Eyeblossoms**. Both checked Eyeblossom feature configurations initially place the closed form and schedule a block tick; the plant's later behavior belongs to [Eyeblossoms](../blocks/Eyeblossoms.md). The biome's creature list is empty, so bring food instead of planning to find ordinary livestock there. Its hostile list is still populated. [Biome][biome-pale_garden] · [Tree placement][place-pale_garden_vegetation] · [Tree selection][config-pale_garden_vegetation] · [Pale Oak decoration][config-pale_oak] · [Moss placement][place-pale_moss_patch] · [Moss configuration][config-pale_moss_patch] · [Flower patch placement][place-pale_garden_flowers] · [Flower patch configuration][config-pale_forest_flowers] · [Single-flower placement][place-flower_pale_garden] · [Single-flower configuration][config-flower_pale_garden]

For **Creaking Hearts**, the vegetation selector tries its Heart-bearing branch with a 0.1 selection chance. The selected tree enables a Heart decorator, but the tree must place and the decorator must find a log enclosed by log-tag neighbors on all six sides. This is not a claim that one in ten visible trees contains a Heart. Creaking is not in this biome's monster table: its checked encounter route is the active Heart. [Selector][config-pale_garden_vegetation] · [Selected placement][place-pale_oak_creaking_checked] · [Tree configuration][config-pale_oak_creaking] · [Active decorator caller][tree-call] · [Heart decorator][heart-call] · [Biome table][biome-pale_garden]

Read [Creaking Heart](../blocks/CreakingHeart.md#finding-a-natural-heart), [Creaking](../mobs/Creaking.md), and [Resin](../blocks/Resin.md) before working around a natural Heart. Those guides own activation, the night encounter, collection, and Resin production. Pale Garden also qualifies for the Mansion route above; pale trees do not establish a different Mansion type. [Mansion definition][structure-mansion] · [Mansion tag][tag-woodland_mansion]

## Mushroom Fields

**ID: `minecraft:mushroom_fields`.** Mushroom Fields is a distinct fungal stop, selected in the Overworld's off-coast continentalness band. Its surface rule supplies **Mycelium** on qualifying surface positions, and its vegetation selects huge red or brown mushrooms with additional small-mushroom entries. The tree features used by the forests above are absent from its checked list, so pack wood or saplings for a longer stay. The selector band is not a measured island shape, travel distance, or fixed coastline. [Selection][selection] · [Biome][biome-mushroom_fields] · [Mycelium surface rule][surface] · [Surface-rule caller][surface-call] · [Huge-mushroom placement][place-mushroom_island_vegetation] · [Huge-mushroom selection][config-mushroom_island_vegetation] · [Mushroom placement checks][huge-checks]

**Mooshroom is the only creature-list entry**, with configured groups of four to eight. Its actual spawn predicate requires a block in the Mooshroom-support tag, which contains Mycelium, and raw brightness above 8. A suitable biome alone does not create a herd. For mushroom collection and the Mooshroom interaction route already covered by the wiki, use [Mushrooms](../blocks/Mushrooms.md). [Biome][biome-mushroom_fields] · [Registered spawn check][spawn-registration] · [Mooshroom rule][mooshroom-rule] · [Ground tag][tag-mooshrooms_spawnable_on] · [Brightness helper][animal-rule]

The biome's monster list is **empty**, but this is not universal protection from enemies. The same biome still lists ordinary/deep monster-room features; successful generation can place a mob spawner. Mineshafts, Strongholds, and Trial Chambers also retain biome eligibility. Treat discovered rooms and underground structures separately from the empty ordinary monster table. [Biome][biome-mushroom_fields] · [Room placement][place-monster_room] · [Deep room placement][place-monster_room_deep] · [Room configuration][config-monster_room] · [Room/spawner implementation][monster-room-call] · [Mineshaft tag][tag-mineshaft] · [Stronghold tag][tag-stronghold] · [Trial Chambers tag][tag-trial_chambers]

## Shared travel and encounter checks

The first five forest variants list Sheep, Pigs, Chickens, and Cows; only Forest adds Wolves and only Flower Forest adds Rabbits. Their creature entries remain conditional. Farm animals require Grass Block through their ground tag and adequate brightness; Wolf/Rabbit predicates use their own ground tags and raw brightness above 8. The active natural-spawn caller also checks allowed positions, distance, space, and the actual mob rules. None of those lists proves an animal will appear on demand. [Registered predicates][spawn-registration] · [Animal rule][animal-rule] · [Animal ground][tag-animals_spawnable_on] · [Wolf rule][wolf-rule] · [Wolf ground][tag-wolves_spawnable_on] · [Rabbit rule][rabbit-rule] · [Rabbit ground][tag-rabbits_spawnable_on] · [Natural-spawn caller][spawn-call]

All seven destinations are eligible for the checked Mineshaft, Stronghold, and Trial Chambers routes, and all use the standard Ruined Portal biome tag. These filters do not promise a nearby structure or its exact footprint. The definitions and loaded structure sets must agree, structure generation must be enabled, and the start still has placement/terrain checks. Read [Structures](../structures/Structures.md#when-generation-is-eligible) and [Stronghold](../structures/Stronghold.md) for preparation. [Mineshaft definition][structure-mineshaft] · [Mineshaft set][set-mineshafts] · [Mineshaft tag][tag-mineshaft] · [Stronghold definition][structure-stronghold] · [Stronghold set][set-strongholds] · [Stronghold tag][tag-stronghold] · [Trial Chambers definition][structure-trial_chambers] · [Trial Chambers set][set-trial_chambers] · [Trial Chambers tag][tag-trial_chambers] · [Standard portal tag][tag-ruined_portal_standard] · [Portal definition][structure-ruined_portal] · [Portal set][set-ruined_portals] · [Structure setting][structure-setting] · [Generation caller][structure-call]

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. Checked Normal-preset/selector wiring, loaded biome and feature data, active tree/plant/decorator callers, selected surface and spawn rules, nested structure-biome tags, structure definitions, and placement sets. No in-game generation survey, search-rate, spawn-rate, harvesting, Creaking, or Resin test was run. Resource and encounter guides linked above retain their own scope and ownership. Seeds, data packs, custom presets, and already-generated terrain can differ. [World loader][world-load] · [Registry codecs][load] · [Resource loader][load-files] · [Biome-feature execution][feature-call] · [Placement execution][placed-call] · [Tree/decorator execution][tree-call] · [Plant checks][plant-call]

Related: [Biomes](Biomes.md) · [Plains and meadows](PlainsAndMeadows.md) · [Trees and saplings](../blocks/SaplingsAndAzaleas.md)

[animal-rule]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[biome-birch_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/birch_forest.json
[biome-dark_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/dark_forest.json
[biome-flower_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/flower_forest.json
[biome-forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/forest.json
[biome-mushroom_fields]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[biome-old_growth_birch_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/old_growth_birch_forest.json
[biome-pale_garden]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/pale_garden.json
[config-birch_tall]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/birch_tall.json
[config-dark_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/dark_forest_vegetation.json
[config-flower_flower_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/flower_flower_forest.json
[config-flower_pale_garden]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/flower_pale_garden.json
[config-forest_flowers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/forest_flowers.json
[config-monster_room]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/monster_room.json
[config-mushroom_island_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/mushroom_island_vegetation.json
[config-pale_forest_flowers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pale_forest_flowers.json
[config-pale_garden_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pale_garden_vegetation.json
[config-pale_moss_patch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_patch.json
[config-pale_oak]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak.json
[config-pale_oak_creaking]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_creaking.json
[config-trees_birch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_birch.json
[config-trees_birch_and_oak_leaf_litter]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_birch_and_oak_leaf_litter.json
[config-trees_flower_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_flower_forest.json
[config-wildflowers_birch_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/wildflowers_birch_forest.json
[feature-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L386
[heart-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/CreakingHeartDecorator.java#L34-L58
[huge-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/AbstractHugeMushroomFeature.java#L49-L108
[load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[load-files]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L333
[monster-room-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L31-L125
[mooshroom-rule]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L71-L75
[nest-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L39-L67
[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[parameters]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[place-birch_tall]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/birch_tall.json
[place-dark_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/dark_forest_vegetation.json
[place-flower_flower_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/flower_flower_forest.json
[place-flower_pale_garden]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/flower_pale_garden.json
[place-forest_flowers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/forest_flowers.json
[place-monster_room]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/monster_room.json
[place-monster_room_deep]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/monster_room_deep.json
[place-mushroom_island_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/mushroom_island_vegetation.json
[place-pale_garden_flowers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/pale_garden_flowers.json
[place-pale_garden_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/pale_garden_vegetation.json
[place-pale_moss_patch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/pale_moss_patch.json
[place-pale_oak_creaking_checked]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/pale_oak_creaking_checked.json
[place-trees_birch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_birch.json
[place-trees_birch_and_oak_leaf_litter]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_birch_and_oak_leaf_litter.json
[place-trees_flower_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_flower_forest.json
[place-wildflowers_birch_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/wildflowers_birch_forest.json
[placed-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L37-L65
[plant-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L44
[provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[rabbit-rule]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Rabbit.java#L423-L427
[selection]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[set-mineshafts]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/mineshafts.json
[set-ruined_portals]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[set-strongholds]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/strongholds.json
[set-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/trail_ruins.json
[set-trial_chambers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/trial_chambers.json
[set-woodland_mansions]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/woodland_mansions.json
[spawn-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L250-L325
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L113-L157
[structure-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L577
[structure-mansion]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/mansion.json
[structure-mineshaft]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/mineshaft.json
[structure-ruined_portal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/ruined_portal.json
[structure-setting]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59
[structure-stronghold]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/stronghold.json
[structure-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/trail_ruins.json
[structure-trial_chambers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json
[surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L1750-L1768
[surface-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L241-L264
[tag-animals_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[tag-mineshaft]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/mineshaft.json
[tag-mooshrooms_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/mooshrooms_spawnable_on.json
[tag-rabbits_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/rabbits_spawnable_on.json
[tag-ruined_portal_standard]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_standard.json
[tag-stronghold]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/stronghold.json
[tag-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trail_ruins.json
[tag-trial_chambers]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json
[tag-wolves_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/wolves_spawnable_on.json
[tag-woodland_mansion]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/woodland_mansion.json
[tree-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L126-L172
[wolf-rule]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L667-L671
[world-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L32-L45
