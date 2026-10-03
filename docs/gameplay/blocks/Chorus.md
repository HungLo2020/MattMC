# Chorus Plants and Flowers

**Collect the flowers before cutting down a Chorus Plant.** Flowers are the replanting item; stems give Chorus Fruit. Breaking the supporting stem first can destroy the attached flowers without dropping them. [Flower loot][flower-loot] · [Stem loot][stem-loot] · [Support-loss destruction][flower-tick] · [Entity-free destruction][destroy-default] · [Loot entity check][entity-condition] · [Required entity][entity-predicate]

## Plants, flowers and fruit

| Form | Registry ID | What it provides |
| --- | --- | --- |
| <span id="chorus-plant">Chorus Plant</span> | `minecraft:chorus_plant` | Branching placed stem; ordinary loot is 0–1 Chorus Fruit |
| <span id="chorus-flower">Chorus Flower</span> | `minecraft:chorus_flower` | Growing or dead tip; directly harvest it for a flower to replant |
| [Chorus Fruit](../items/ChorusFruit.md) | `minecraft:chorus_fruit` | Edible fruit with a random-teleport effect |
| [Popped Chorus Fruit](../items/PoppedChorusFruit.md) | `minecraft:popped_chorus_fruit` | Smelted, inedible building ingredient |

Both plant items are ordinary Natural Blocks category entries, so the [inventory item browser](../mechanics/InventoryBrowser.md) can request them in Creative. This is separate from harvesting a stem or growing a flower. [Category entries][creative]

Both plant blocks have hardness and blast resistance **0.4**, and neither requires a correct tool to drop its applicable loot. Both have registered block items and Creative entries, but the stem's existence as an item does **not** mean ordinary harvesting returns the stem. [Block properties][blocks] · [Strength meaning][strength] · [Tool gate][tool-gate] · [Block items][block-items] · [Creative entries][creative] · [Stem loot][stem-loot] · [Fruit registrations][fruit-items]

## Finding wild chorus

In the normal world preset, the End uses its End biome source, which selects **End Highlands on the outer islands**. The highlands feature list includes the placed Chorus Plant feature. This chooses surface positions and calls the live Chorus generator only when the target is empty and the block below is **End Stone**; the generator builds stems and ends branches with age-5 flowers. These source-defined placement attempts do not guarantee a plant at every highlands location. Use [the End guide](../dimensions/End.md) for travel. [Normal preset][preset] · [Biome selection][biome] · [Highlands feature][highlands] · [Placement settings][placed-json] · [Configured feature][configured-json] · [Registered generator][feature-registry] · [Active generation caller][generation] · [Placed-feature dispatch][placed] · [Configured dispatch][configured] · [Plant placement][plant-feature] · [Generated branches and flowers][generation-shape]

## Harvest flowers before stems

**Break a flower directly in Survival** to collect **one Chorus Flower**, including by hand. Its age does not change this loot: an age-5 dead flower is still worth collecting. No Silk Touch or Fortune is needed. The flower loot requires a breaking entity; ordinary support-loss destruction supplies none, so flowers lost when a plant collapses do not provide the same drop. Explosion survival is a separate loot condition. [Flower loot][flower-loot] · [Player harvest dispatch][harvest] · [Player loot context][player-loot] · [Entity check][entity-condition] · [Null-entity rejection][entity-predicate] · [Support-loss tick][flower-tick] · [Default destruction][destroy-default] · [World destruction and loot][destroy]

You can also knock a flower down with an allowed **impact projectile**, such as a Snowball, when `projectilesCanBreakBlocks` is enabled (default **true**). The flower callback destroys it with the projectile as the breaking entity. The projectile must pass its interaction permission: player-owned shots use the owner's permission; non-player-owned shots require `mobGriefing`; ownerless shots pass that ownership check. Ordinary stem blocks do not have this flower-specific projectile harvest callback. [Flower hit][flower-hit] · [Permission and break gates][projectile] · [Impact-projectile tag][impact] · [Rule defaults][rules] · [Stem behavior][stem]

**Break stems for fruit.** Each stem's bundled loot chooses **0 or 1 Chorus Fruit**, with no Fortune multiplier or Silk Touch self-drop. Cutting a supporting stem can make unsupported branches break in turn, so harvest flowers you want to keep first. Ordinary block-item spawning requires `doTileDrops`; the stem loot separately applies explosion decay. [Stem loot][stem-loot] · [Stem support and collapse][stem-support] · [Drop gate][drops]

The checked recipe bundle has no recipe producing Chorus Plant or Chorus Flower. Grow new stems from replanted flowers; the ordinary stem loot does not supply a Survival building stock of stem items. [Growing stems][growth] · [Registered items][block-items] · [Stem loot][stem-loot]

## Planting and growth

Start with a collected **Chorus Flower on End Stone**, leaving open space above and around it. End Stone Bricks do not satisfy the flower's End Stone support check. A newly placed ordinary flower starts at **age 0**, even when its dropped item came from an age-5 flower: the loot does not copy the age, and the ordinary placement path uses the default state. [Flower support][flower-support] · [Default age][flower-default] · [Flower loot][flower-loot] · [Default placement state][default-place]

Flowers can also survive directly on a Chorus Plant stem. With **air below**, a flower can hang from exactly one horizontally adjacent stem only when its other three horizontal neighbors are air. Neither arbitrary soil nor a nearby flower substitutes for that stem. Support loss schedules a check after one game tick, which destroys the unsupported flower. [Survival conditions][flower-support] · [Neighbor scheduling][flower-update] · [Scheduled check][flower-tick]

Only flowers at ages **0–4** receive growth random ticks. Growth needs air directly above and stays within the world-height limit. A successful upward step replaces the old flower with a connected stem and puts a flower of the **same age** above it; the new position's horizontal neighbors and the space two blocks above the old flower must be empty. Whether upward growth is attempted also depends on the stem column below and a random check. [Random ticking][random-ticking] · [Upward growth][growth]

When an eligible upward attempt cannot proceed, younger flowers can try horizontal branches. The branch position and the space below it must be empty, and its other horizontal neighbors must be clear. A new branch flower has the old age **plus one**. If those attempts fail, or an age-4 flower cannot grow upward, the flower becomes **age 5** and stops growing. Simply blocking the space immediately above can pause growth without immediately killing the flower. [Branching and dead age][branches] · [Neighbor clearance][neighbors] · [Growth entry][growth]

There is no light-level or dimension restriction in these growth checks, and **Bone Meal does not grow Chorus**: neither plant block implements the required Bone Meal interface. Replant a collected flower to start again; stems alone do not have the flower's growth routine. Growth timing depends on random ticks rather than a fixed countdown; `randomTickSpeed=0` stops these flower growth ticks in ticking chunks. [Flower implementation][flower-default] · [Growth checks][growth] · [Branch checks][branches] · [Stem implementation][stem] · [Bone Meal dispatch][bone-meal] · [Random-tick rule][random-rule] · [Chunk tick dispatch][chunk-ticks] · [Block random ticks][block-ticks]

## Shape, support and water

Stems use a **10/16-block central cube with connecting arms**. They connect visually to adjacent Chorus Plants or Flowers, and downward to End Stone; connection appearance alone does not guarantee survival. Flowers keep ordinary full-block collision, with a smaller **14/16-wide, 15/16-high support shape**. Both can obstruct movement. [Stem width and connections][stem-connections] · [Pipe shapes][pipe] · [Flower support shape][flower-default] · [Default collision][collision]

A stem normally stands on another stem or End Stone. Horizontal support can instead come from a neighboring stem with a stem or End Stone beneath that neighbor. A stem with a horizontal stem neighbor fails its support test when **both its own above and below positions are non-air**. Invalid stems schedule a one-tick check and break, which is why a cut can propagate through a plant. [Stem support, scheduling and connections][stem-support]

Neither plant has a waterlogged state. Both are explicitly non-solid for fluid admission, so water reaching their positions replaces them and runs an entity-free loot path: stems can yield fruit, but flowers do not meet their breaking-entity condition. Keep the flowers dry until collected; flushing the whole plant is not a flower-harvesting method. [Block registrations][blocks] · [State definitions][stem-states] · [Flower state][flower-state] · [Fluid admission][fluid-admission] · [Fluid replacement][fluid-replacement] · [Water drops][water-drops] · [Entity-free loot][entity-free-loot] · [Flower condition][flower-loot]

## Eating and building

Raw Chorus Fruit restores **4 hunger points** and **2.4 saturation before the normal caps**, and can be eaten with a full hunger bar. Consumption also makes up to **16 random teleport attempts**: the initial candidate is within 8 blocks on each axis, with the candidate height clamped to the world's logical range. The teleport routine then searches downward for supporting ground, so the final vertical change is not limited to 8 blocks. The final position must have a loaded chunk, no collision and no liquid intersecting the entity; an attempt can fail. [Fruit registration][fruit-items] · [Food values][food] · [Food effect][food-apply] · [Saturation formula][food-formula] · [Caps][food-caps] · [Always-edible consumption][consume] · [Teleport effect assignment][consumables] · [Teleport attempts][teleport] · [Landing validation][landing]

Eating can dismount a rider. A successful teleport resets fall distance, but random attempts are not a reliable route or a guaranteed escape from the void. The fruit is consumed even if every teleport attempt fails, and ordinary use applies a **20-tick cooldown** (nominally one second at 20 ticks per second). [Dismount and success][teleport] · [Consumption order][consume] · [Cooldown registration][fruit-items] · [Finished-use dispatch][finished-use] · [Cooldown ticks][cooldown]

For construction, follow [Chorus Fruit to Purpur](EndStoneAndPurpur.md#chorus-fruit-to-purpur): smelt raw fruit into Popped Chorus Fruit, then craft Purpur. The existing guide owns cooking time, XP and the Purpur conversion recipes. Popped Chorus Fruit also combines with a Blaze Rod to make [End Rods](EndRod.md#crafting-and-collecting). Popped fruit cannot be eaten, and raw fruit is not a substitute for the popped recipe ingredient. [Smelting recipe][smelting] · [Purpur recipe][purpur] · [End Rod recipe][end-rod] · [Item registrations][fruit-items] · [Eating dispatch][item-use]

Related: [Chorus Plant item](../items/ChorusPlant.md) · [Chorus Flower item](../items/ChorusFlower.md) · [Hunger](../mechanics/Hunger.md) · [Plants catalog](catalog/plants.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked block/item registration, active End generation, loot entity requirements, player/projectile/support-loss/water harvest paths, placement, growth, food and teleport callers, and the bundled recipe tree. No in-game generation, growing, harvesting, projectile, water, eating or teleport test was run. Data packs, game rules and permissions can change the relevant results.

[blocks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4215-L4239
[strength]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[block-items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L469-L470
[creative]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L940-L946
[fruit-items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2232-L2235
[item-use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Item.java#L173-L196
[flower-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/chorus_flower.json#L1-L26
[stem-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/chorus_plant.json#L1-L30
[flower-tick]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L46-L51
[destroy-default]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/LevelWriter.java#L17-L25
[entity-condition]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemEntityPropertyCondition.java#L33-L44
[entity-predicate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/advancements/critereon/EntityPredicate.java#L93-L97
[preset]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L14-L23
[biome]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/biome/TheEndBiomeSource.java#L30-L78
[highlands]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json#L17-L32
[placed-json]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/placed_feature/chorus_plant.json#L1-L23
[configured-json]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/configured_feature/chorus_plant.json#L1-L4
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L67-L71
[generation]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[placed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[configured]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
[plant-feature]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/ChorusPlantFeature.java#L16-L27
[generation-shape]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L202-L254
[harvest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[player-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[destroy]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/Level.java#L263-L284
[flower-hit]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L256-L262
[projectile]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L336-L346
[impact]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/entity_type/impact_projectiles.json#L1-L15
[rules]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L47-L62
[stem]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusPlantBlock.java#L18-L120
[stem-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusPlantBlock.java#L61-L109
[drops]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[growth]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L63-L99
[flower-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L168-L195
[flower-default]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L25-L61
[default-place]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[flower-update]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L150-L166
[random-ticking]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L53-L56
[branches]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L99-L138
[neighbors]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L140-L148
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[random-rule]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L81-L83
[chunk-ticks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L378-L404
[block-ticks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L503
[stem-connections]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusPlantBlock.java#L26-L59
[pipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PipeBlock.java#L33-L65
[collision]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L327
[stem-states]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusPlantBlock.java#L111-L114
[flower-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L197-L200
[fluid-admission]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[fluid-replacement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[water-drops]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[entity-free-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L345-L350
[food]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/food/Foods.java#L10-L14
[food-apply]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L57
[food-formula]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-caps]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[consume]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L101
[consumables]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/Consumables.java#L63-L66
[teleport]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/consume_effects/TeleportRandomlyConsumeEffect.java#L22-L81
[landing]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3352-L3395
[finished-use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L398-L418
[cooldown]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/UseCooldown.java#L27-L39
[smelting]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/smelting/popped_chorus_fruit.json#L1-L10
[purpur]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/purpur_block.json#L1-L15
[end-rod]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/end_rod.json#L1-L16
