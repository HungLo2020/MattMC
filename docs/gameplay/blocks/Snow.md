# Snow and Powder Snow

Use **Snow layers** for shallow ground cover, **Snow Blocks** for solid construction, and **Powder Snow** when you want a bucket-carried block that entities can sink into. Their similar appearance hides different harvesting, support and freezing rules. [Registrations][blocks] [items]

## Registered snow blocks

| Block | Registry ID | Inventory form |
| --- | --- | --- |
| Snow, with 1–8 layers | `minecraft:snow` | [Snow](../items/Snow.md) |
| Full Snow Block | `minecraft:snow_block` | [Snow Block](../items/SnowBlock.md) |
| Powder Snow | `minecraft:powder_snow` | [Powder Snow Bucket](../items/PowderSnowBucket.md), `minecraft:powder_snow_bucket` |

Eight placed layers remain the Snow block ID; placing the eighth layer does not turn them into `snow_block`. Powder Snow has no separate ordinary `powder_snow` inventory item. [Layer state][snow] · [Item registration][items]

## Collecting and crafting snow

Use an **unbroken shovel**, including Wood, for Snow layers and Snow Blocks. Both require a correct tool and are in the shovel-mining tag, without a higher material-tier requirement. Breaking them by hand does not collect their ordinary loot. [Registrations][blocks] · [Tool tags][shovel] [wood-denials] [stone-tier] [iron-tier] [diamond-tier] · [Tool checks][tool-material] [player-tool] [broken-tool] [break-dispatch]

| What you mine with the correct shovel | Without Silk Touch | With Silk Touch |
| --- | --- | --- |
| 1–7 Snow layers | 1–7 Snowballs, matching the layer count | 1–7 Snow items, matching the layer count |
| 8 Snow layers | 8 Snowballs | 1 Snow Block |
| Full Snow Block | 4 Snowballs | 1 Snow Block |

These ordinary Survival harvest counts assume `doTileDrops = true`, its default; disabling that rule suppresses dropped block items. Fortune does not increase these counts. The eight-layer Silk Touch result is a full block even though the placed layer stack had a different ID. [Snow loot][loot-snow] · [Snow Block loot][loot-snow-block] · [Drop dispatch and rule][block] [rules]

| Ingredients and arrangement | Result |
| --- | --- |
| 4 [Snowballs](../items/Snowball.md) in a 2 × 2 square | 1 Snow Block |
| 3 Snow Blocks in one horizontal row | 6 Snow items |

These are the bundled snow crafting recipes; Powder Snow and its filled bucket have no crafting recipe. Snowballs are thrown items, not placeable Snow layers. [Block recipe][recipe-snow-block] · [Layer recipe][recipe-snow] · [Active recipe loading][recipes] · [Snowball use][snowball-item]

Find natural Snow and Snow Blocks in cold terrain. The normal Overworld's active surface rules include Snow Blocks in Snowy Slopes and Grove, with patches of Powder Snow mixed into those biomes. Its cold-biome top-layer feature can also place Snow and ordinary Ice. These are generation examples, not a promise that every surface is safe solid snow. [Normal preset and biome selection][normal-preset] [biome-parameters] [overworld-biomes] · [Surface data and dispatch][overworld] [terrain] [surface] [native-surface] · [Snowy Slopes features][slopes] [placed-feature-freeze-top-layer] [configured-feature-freeze-top-layer] [features] [snow-worldgen] [biome-generation] [placed-feature]

For a renewable layer source, see the [Snow Golem's Snow trail](../mobs/SnowGolem.md#snow-trail). Its placement path requires `mobGriefing`, air at the target and valid Snow support; its health and biome hazards are separate. [Active golem trail][golem]

## Placing layers and keeping their support

Use another Snow item on top of an existing layer stack to add one layer, up to **eight in one block position**. A one-layer block is replaceable by other blocks. The visible height is one eighth of a block per layer, while the collision height is one layer lower: one layer has no collision height, and eight layers collide at seven eighths of a block. [Placement and shapes][snow]

Snow normally needs a block beneath with a full upper collision face. There are explicit exceptions:

- It cannot survive on **Ice, Packed Ice or Barrier**, even if their face looks suitable
- It can survive on **Honey Block, Soul Sand or Mud** through the allow tag
- A full eight-layer Snow stack can support another Snow block position

Blue Ice is not in the rejection tag and supplies a full upper face. When support becomes invalid, a neighbor update removes the Snow. This does not provide the shovel-harvest drops: the Snow loot pool requires a breaking entity, which the support-loss and melting paths do not supply. [Support rules][snow] · [Allow/reject tags][snow-support-yes] [snow-support-no] · [Blue Ice registration][blocks] · [Removal and loot context][block] [loot-snow] [loot-entity-condition] [entity-predicate]

## Snowfall, light and game rules

During rain/snow weather, the server samples surface columns. Snow accumulation needs Snow precipitation at that location, a target within build height, **block light below 10**, air or existing Snow at the target, and valid support. The temperature used by the biome varies with location and elevation; the cold threshold is below **0.15**, and precipitation must be enabled for snowfall. [Weather update][weather] · [Biome checks][biome]

`snowAccumulationHeight` defaults to **1**. At **0 or below**, this weather path adds no layers. A positive value caps accumulation in the current Snow block at the smaller of the rule and **8**. It does not limit layers you place by hand or remove existing stacks. The surface-sampling and random-block-tick loops both use `randomTickSpeed`, whose default is **3**; **0 stops these particular weather and random-tick changes**. World generation and Snow Golem placement are separate paths. [Rule defaults][rules] · [Chunk dispatch][chunk-tick] [random-tick] · [Accumulation][weather] · [Other placement paths][snow-worldgen] [golem]

Snow layers disappear on a random tick when **block light is 12 or more**. This check uses emitted block light, not sky light or a warm-biome temperature test, and it removes the whole layer stack. Full Snow Blocks have no random-tick melting callback and are suitable for permanent bright-room construction. [Layer melting][snow] · [Full-block registration][blocks]

## Powder Snow: buckets and collision

Collect placed Powder Snow with an empty [Bucket](../items/Bucket.md). The pickup removes the block and gives one Powder Snow Bucket. Breaking it has **no block loot**, including with Silk Touch. A full Powder Snow [Cauldron](../items/Cauldron.md) is another bucket-filling source; its wider mechanics are separate. [Bucket pickup][bucket] [powder] · [Empty loot][loot-powder-snow] · [Full-cauldron interaction][cauldron-bucket]

Use the filled bucket to place Powder Snow through normal block placement. In Survival, successful placement returns an empty Bucket; Creative's infinite-materials path keeps the filled bucket. The filled bucket stacks to **one**. Powder Snow has no support/gravity or random-light-melting callback, and this solid-bucket placement path does not use Water Bucket evaporation in an ultra-warm dimension. [Registration][blocks] [items] · [Placement and returned bucket][solid-bucket] [block-item] [bucket]

A [Dispenser](DispenserAndDropper.md#some-verified-dispenser-actions) can place it into an empty block in front and retain an empty Bucket. If that placement fails, its fallback ejects the filled bucket item. An empty Bucket in a Dispenser can collect a Powder Snow block in front through the same pickup interface. [Dispenser registrations and handling][dispenser] · [Placement restriction][solid-bucket]

Most entities sink into Powder Snow rather than stand on its surface. **Leather Boots** let a player stand on top when approaching from above and not sneaking; sneak to descend deliberately. While inside, boots also enable the special upward movement when jumping or pushing against a horizontal obstruction. Other leather armor prevents freezing but does not grant this surface-walking behavior. [Powder collision and walking check][powder] · [Sneaking/collision context][entity] [collision] · [Upward movement][living]

Rabbits, Foxes, Endermites and Silverfish are explicitly tagged to walk on Powder Snow without boots. That tag alone does not make them freeze-immune. Falling blocks receive solid collision, and an entity falling more than **2.5 blocks** meets a special collision surface at **0.9 block height**. Powder Snow's landing callback plays the appropriate fall sound without applying the ordinary fall damage callback. These details make it different from both solid Snow Blocks and an empty hole. [Collision and landing][powder] · [Walkable-mob tag][walkable]

## Powder Snow: freezing and fire

Powder Snow applies a freezing effect through the active block-contact collector. Each applied Freeze effect adds one to the frozen counter, up to the default **140** threshold. Ordinary continuous exposure is roughly **7 seconds at 20 ticks per second**, but the implementation processes movement steps rather than promising a fixed wall-clock delay in every movement situation. Once fully frozen, a living entity is checked every **40 game ticks** for **1 health point of freezing damage**. The counter falls by **2 per living-entity tick** when outside Powder Snow or unable to freeze. Accumulated frost also slows movement. [Contact dispatch][entity] [effect-collector] [effects] · [Counter and damage][living]

For players, **any one worn Leather Helmet, Chestplate, Leggings or Boots prevents freezing**. Carrying it in inventory does not. The wearable tag also includes Leather Horse Armor. Strays, Polar Bears, Snow Golems and Withers are explicitly freeze-immune; Striders, Blazes and Magma Cubes instead take **five times** the normal freezing damage. [Armor check][living] · [Wearables][freeze-wearables] · [Entity immunity][entity] [freeze-immune] · [Extra-damage tag][freeze-extra]

`freezeDamage` defaults to **true**. Setting it to false makes players immune to freezing damage through the player damage check; it does not disable the frozen counter, movement effects or all mob freezing. [Rule][rules] · [Player damage immunity][player]

Powder Snow extinguishes entities. Before extinguishing, a burning player can destroy the contacted Powder Snow block if allowed to interact there. A burning non-player also needs **`mobGriefing = true`** for this removal. This fire-contact path produces no block drop; it is separate from ordinary light or temperature melting. [Fire/contact callback][powder] [effects]

## A small building example

**Source-based example, not tested in gameplay:** craft Snow Blocks for a bright room's solid walls, then add Snow layers on a supported floor where block light stays **11 or lower**. Keep decorative Powder Snow clearly marked and wear Leather Boots before crossing it. Carry an empty Bucket to remove a misplaced Powder Snow block; a shovel is for the ordinary Snow layers and blocks.

## Related pages

- [Ice, Packed Ice, Blue Ice and Frosted Ice](Ice.md)
- [Snow Golem](../mobs/SnowGolem.md), [Snowball](../items/Snowball.md), and [Leather Boots](../items/LeatherBoots.md)
- [Blocks](Blocks.md) and [Items](../items/Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. Registrations, all relevant recipes/loot, tool tags, surface-generation dispatch, weather ticks, block-contact effects and bucket interactions were checked. No gameplay harvesting, freezing, construction or Dispenser test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java
[shovel]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/snow.json
[loot-snow-block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/snow_block.json
[block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Block.java
[rules]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/GameRules.java
[recipe-snow-block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/recipe/crafting/snow_block.json
[recipe-snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/recipe/crafting/snow.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[snowball-item]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/SnowballItem.java
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biome-parameters]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[overworld]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[terrain]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java
[surface]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java
[native-surface]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java
[slopes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/biome/snowy_slopes.json
[placed-feature-freeze-top-layer]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/freeze_top_layer.json
[configured-feature-freeze-top-layer]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/freeze_top_layer.json
[features]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[snow-worldgen]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/SnowAndFreezeFeature.java
[biome-generation]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[placed-feature]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java
[golem]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/animal/SnowGolem.java#L91-L117
[snow-support-yes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/snow_layer_can_survive_on.json
[snow-support-no]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/snow_layer_cannot_survive_on.json
[loot-entity-condition]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemEntityPropertyCondition.java
[entity-predicate]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/advancements/critereon/EntityPredicate.java#L89-L99
[weather]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L555-L592
[biome]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/Biome.java#L109-L209
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L375-L405
[random-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L474-L515
[bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BucketItem.java
[powder]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/PowderSnowBlock.java
[loot-powder-snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/powder_snow.json
[cauldron-bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L170-L185
[solid-bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/SolidBucketItem.java
[block-item]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BlockItem.java
[dispenser]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java
[entity]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/Entity.java
[collision]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/phys/shapes/EntityCollisionContext.java
[living]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/LivingEntity.java
[walkable]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/entity_type/powder_snow_walkable_mobs.json
[effect-collector]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/InsideBlockEffectApplier.java
[effects]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/InsideBlockEffectType.java
[freeze-wearables]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/item/freeze_immune_wearables.json
[freeze-immune]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/entity_type/freeze_immune_entity_types.json
[freeze-extra]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/entity_type/freeze_hurts_extra_types.json
[player]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L719
