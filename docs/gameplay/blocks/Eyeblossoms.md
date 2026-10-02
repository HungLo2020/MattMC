# Eyeblossoms

An **Eyeblossom** switches between **Open** and **Closed** forms with the natural dimension's day/night cycle. Harvest the current form to choose its dye or Suspicious Stew ingredient. The open flower is hazardous to Bees, and its bright-looking center **does not emit block light**. [Eyeblossom registrations] · [Eyeblossom behavior] · [Block defaults]

## Forms and exact IDs

Each ID names both a placed block and its matching item. The recipes below use one flower and consume it. Stew effects are level I; seconds assume 20 TPS. The shared stew ingredients and eating behavior belong to [Suspicious Stew](../items/SuspiciousStew.md#obtaining). [Eyeblossom items] · [Display names] · [Recipe execution] · [Ingredient consumption] · [Stew consumption]

| Current form | Exact ID | Dye recipe | Suspicious Stew effect | Ordinary harvest |
| --- | --- | --- | --- | --- |
| <span id="open-eyeblossom"></span>[Open Eyeblossom](../items/OpenEyeblossom.md) | `minecraft:open_eyeblossom` | **1 Orange Dye** [Orange dye] | **Blindness, 220 ticks / 11 s** [Open stew] | **1 Open Eyeblossom** [open_eyeblossom loot] |
| <span id="closed-eyeblossom"></span>[Closed Eyeblossom](../items/ClosedEyeblossom.md) | `minecraft:closed_eyeblossom` | **1 Gray Dye** [Gray dye] | **Nausea, 140 ticks / 7 s** [Closed stew] | **1 Closed Eyeblossom** [closed_eyeblossom loot] |

## Planting and collecting

Plant either form on **Farmland or the dirt block tag**: Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. No light, hydration, or nearby-water test is part of its survival rule. Both forms occupy one cell with no facing or waterlogged state and no entity collision; losing valid support removes the plant. [Plant support] · [Soil tag] · [Flower class] · [Eyeblossom registrations]

Break either form by hand to collect **one item of its current form**. Both break instantly and have no correct-tool requirement; their loot tables have no Shears, Silk Touch, or Fortune condition. Their explosion-survival checks can prevent recovery after a blast. [Eyeblossom registrations] · [Tool gate] · [open_eyeblossom loot] · [closed_eyeblossom loot]

Both can be displayed in a [Flower Pot](FlowerPot.md#supported-plants). That page owns potting, retrieval, support, loot, and the [potted day/night behavior](FlowerPot.md#growth-and-eyeblossoms); the rules below concern the unpotted plant.

## Opening, closing, and nearby plants

In a **natural dimension**, the desired form is **open during day-time ticks 12,600–23,400 inclusive, modulo 24,000**, and **closed otherwise**. The check uses world time. It does not require sky exposure, a visible moon from the flower, darkness at the block, or Pale Moss beneath it. Outside natural dimensions, the flower does not change through this callback. [Eyeblossom behavior] · [Night helper] · [Night window]

Changes occur when an eligible **random or scheduled tick** runs, so all flowers do not switch at one exact instant. A flower that changes also schedules nearby flowers still in the same previous state, within **three blocks horizontally and two vertically** in each direction. Each scheduled delay is randomly selected between the integer-truncated values of **5 × distance and 10 × distance ticks**. This lets a group change in a wave; it does not create additional plants. [Eyeblossom behavior] · [Random ticks] · [Scheduled ticks]

A successful change plays its opening/closing sound and emits a transformation particle. An **open** Eyeblossom directly above **Pale Moss Block** can additionally play its idle sound on a 1-in-700 client animation-callback roll. That sound condition is separate from changing state, and the roll is not a fixed per-second rate. Neither registered form has a light-emission value above the zero default. [Eyeblossom behavior] · [Eyeblossom registrations] · [Block defaults]

## Bees and harmful effects

**Open Eyeblossom is in the Bee-attractive block tag; Closed Eyeblossom is not.** Open flowers can be chosen by the Bee's reachable-flower search and pollination goal under its normal conditions. Being tagged does not guarantee a Bee visit; see [Bee pollination](../mobs/Bee.md#flowers-and-pollination). [Bee plants] · [Bee attraction] · [Bee flower search] · [Bee pollination]

On the server outside Peaceful difficulty, a **Bee inside a Bee-attractive Eyeblossom** receives **Poison I for 25 ticks** if it is not already poisoned. In the bundled tags that means the open form. This contact callback specifically checks for Bees: it does **not** apply the flower's stew effect to players walking through it. [Eyeblossom behavior] · [Bee plants]

Feeding an **Open Eyeblossom item** to a Bee consumes it and applies the same 25-tick Poison effect through a special flower interaction, instead of the ordinary breeding interaction. That feeding branch does not contain the contact callback's Peaceful exclusion. Closed Eyeblossom is absent from the Bee food tag, even though both block forms expose the same interaction-effect method. Use another suitable flower for an apiary. [Bee food] · [Bee feeding] · [Eyeblossom behavior]

## Finding and propagating Eyeblossoms

**Pale Garden** in the Normal Overworld includes the `flower_pale_garden` placed feature. It uses a **Closed Eyeblossom** state, checks empty space and survival, then schedules a tick so the plant can adjust to the current time. This is a checked natural acquisition chain, not a guarantee of a flower at every location in the biome. [Normal preset] · [Overworld preset] · [Overworld biomes] · [pale_garden biome] · [flower_pale_garden placement] · [flower_pale_garden feature] · [Biome feature execution] · [Placed feature execution] · [Feature registration] · [Simple feature registration] · [Patch execution] · [Plant feature execution]

**Direct Bone Meal on either flower does not duplicate or grow it.** The flower classes have no Bone Meal target implementation. Instead, Bone Meal on **Grass Block with air above in Pale Garden** can select its eligible Eyeblossom flower feature. Grass Block makes a 1-in-8 flower-branch choice at eligible empty candidate cells among its 128 candidate attempts; terrain and placement checks still apply. Placing another Eyeblossom nearby does not make Grass Block copy it. [Flower class] · [Eyeblossom behavior] · [Bone Meal use] · [Grass Block growth] · [Flower feature filter] · [flower_pale_garden feature]

A possible **Wandering Trader** offer sells **1 Open Eyeblossom for 1 Emerald**, with **7 uses**. It belongs to a group from which five offers are chosen, so it may be absent. Plant that item and let the natural-dimension time rule change it when you want a Closed Eyeblossom. [Trader offers] · [Offer construction] · [Trader selection] · [Offer randomization] · [Eyeblossom behavior]

## Compost and related plants

Either form has a **65%** ordinary chance to increase a [Composter](Composter.md) level; the first accepted item in an empty composter succeeds automatically. Neither form appears in the checked standard Furnace fuel list. [Compost values] · [Compost initialization] · [Compost first layer] · [Fuel table]

Related: [Small and tall flowers](Flowers.md) · [Flowerbeds and Leaf Litter](FlowerbedsAndLeafLitter.md) · [Grass and Ferns](GrassAndFerns.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `20354edd390fadb09b43132d712facf4188c4119`. Checked both block/item registrations, names and full loot tables, support, time and tick wiring, nearby transitions, client effects, Bee contact/feeding/pollination paths and tags, direct and Grass Block Bone Meal paths, selected acquisition chains, exact dye/stew recipes, and compost/fuel lists. No in-game transition, Bee, crafting, growth, trading, or world-generation test was run. Data packs can change tags, recipes, loot, and features.

[Eyeblossom registrations]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/Blocks.java#L6850-L6879
[Eyeblossom behavior]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/EyeblossomBlock.java
[Block defaults]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L994-L1004
[Eyeblossom items]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/Items.java#L343-L344
[Display names]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/assets/minecraft/lang/en_us.json
[Recipe execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/inventory/ResultSlot.java
[Stew consumption]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java
[Orange dye]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_open_eyeblossom.json
[Open stew]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_open_eyeblossom.json
[open_eyeblossom loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/open_eyeblossom.json
[Gray dye]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/crafting/gray_dye_from_closed_eyeblossom.json
[Closed stew]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_closed_eyeblossom.json
[closed_eyeblossom loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/closed_eyeblossom.json
[Plant support]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/block/dirt.json
[Flower class]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/FlowerBlock.java
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Night helper]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L69-L71
[Night window]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/Level.java#L365-L372
[Random ticks]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/level/ServerLevel.java#L484-L510
[Scheduled ticks]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/level/ServerLevel.java#L765-L770
[Bee plants]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[Bee attraction]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L677
[Bee flower search]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1234-L1255
[Bee pollination]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1100-L1171
[Bee food]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/item/bee_food.json
[Bee feeding]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/animal/Bee.java#L570-L590
[Normal preset]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Overworld preset]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L110
[Overworld biomes]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L76-L103
[pale_garden biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/pale_garden.json
[flower_pale_garden placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/flower_pale_garden.json
[flower_pale_garden feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/flower_pale_garden.json
[Biome feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[Placed feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L43-L69
[Feature registration]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L63-L67
[Simple feature registration]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L120
[Patch execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java
[Plant feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java
[Bone Meal use]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[Grass Block growth]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L93
[Flower feature filter]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L45-L58
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L750-L829
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1420-L1481
[Trader selection]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Offer randomization]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L238
[Compost values]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L90-L167
[Compost initialization]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/Bootstrap.java#L49-L50
[Compost first layer]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L318
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L39-L109
