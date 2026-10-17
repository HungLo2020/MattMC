# Dripleaves

**Small Dripleaf** is a two-block decorative plant that needs Shears for recovery. Bone Meal converts it into **Big Dripleaf**, whose leaf can support an entity briefly before tilting. Big leaves and stems both drop the same Big Dripleaf item, making Bone Meal growth a way to increase that building material. [Dripleaf registry] · [Dripleaf items] · [Small conversion] · [small_dripleaf loot] · [big_dripleaf loot] · [big_dripleaf_stem loot]

[Lily Pads](LilyPad.md) are a separate surface plant. [Kelp](Kelp.md) and [Seagrass](Seagrass.md) own their underwater growth and harvesting rules.

## Forms and inventory items

| Placed form | Exact block ID | Inventory form and normal player harvest |
| --- | --- | --- |
| <span id="small-dripleaf"></span>Small Dripleaf | `minecraft:small_dripleaf`, lower and upper halves | [Small Dripleaf](../items/SmallDripleaf.md): **one item when either half is mined with Shears**, otherwise none. [small_dripleaf loot] · [Double-plant harvest] |
| <span id="big-dripleaf"></span>Big Dripleaf leaf | `minecraft:big_dripleaf` | [Big Dripleaf](../items/BigDripleaf.md): **one item**, including by hand. [big_dripleaf loot] |
| <span id="big-dripleaf-stem"></span>Big Dripleaf stem | `minecraft:big_dripleaf_stem` | The same **Big Dripleaf item**, one per harvested stem; no separate stem item is registered. [Shared block item] · [big_dripleaf_stem loot] |

## Support and water

### Small Dripleaf

**Clay and ordinary Moss Block** support Small Dripleaf on dry land or in water. They are the two entries in `#minecraft:small_dripleaf_placeable`. Other ground works only when the lower plant cell contains **source Water** and the ground is **Farmland or a member of `#minecraft:dirt`**. That dirt tag currently contains Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. No light threshold appears in the support check. [Small support and placement] · [small_dripleaf_placeable tag] · [Plant soil support] · [dirt tag]

One item places both halves, so the upper space must be replaceable and inside the build limit. Both halves face opposite the player's horizontal facing. Each half can be waterlogged independently; the placement helper copies water-tagged fluid at each cell into that half's waterlogged state. The lower-cell **source-water support requirement** still applies on ground outside Clay/Moss, even though this copying helper recognizes flowing water too. [Dripleaf items] · [Small support and placement] · [Double-plant placement and water] · [Double-height item] · [Water-tag lookup]

The upper half needs its matching lower half. Removing either half removes the pair; loss of valid ground also breaks it. **Draining the lower half on water-dependent ground can therefore lose the plant without a Shears drop.** Clay/Moss does not depend on retaining that water. [Double-plant neighbor survival] · [Small support and placement] · [Waterlogged bucket behavior] · [Support-loss drops] · [small_dripleaf loot]

### Big Dripleaf and stems

The ground tag for Big Dripleaf is a different list: **Clay, Moss Block, Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Mud, Muddy Mangrove Roots, and Farmland**. Both dry and waterlogged plants can use it. **Pale Moss Block is absent.** A Small Dripleaf surviving on source-watered Pale Moss should be moved to Clay or ordinary Moss before conversion if you want the resulting Big Dripleaf to remain supported. [big_dripleaf_placeable tag] · [small_dripleaf_placeable tag] · [Big support and stacking] · [Stem support and collapse]

A leaf can sit directly on valid ground or on another Big Dripleaf leaf/stem. Placing another leaf above an existing leaf changes the lower leaf into a stem. The new item placement keeps the column's facing when there is a leaf/stem below; otherwise it faces opposite the player. **Stems need support below and another stem or leaf above.** They do not turn into replacement leaves when the top is removed. A broken top or interrupted column can therefore collapse connected stems, each yielding a Big Dripleaf item. Invalid stems schedule their destruction check one tick later. [Big support and stacking] · [Big placement and states] · [Stem support and collapse] · [big_dripleaf_stem loot]

Big leaf/stem placement and Bone Meal growth set the new waterlogged flag only from **source Water** at that cell. All three dripleaf forms use source-water bucket filling and draining. Removing water leaves a Big Dripleaf supported if its ordinary ground/column requirements still hold. None of these support or growth checks requires sunlight or a particular biome. [Big placement and states] · [Random-height conversion] · [Stem support and collapse] · [Waterlogged bucket behavior] · [Big Bone Meal growth] · [Stem Bone Meal growth]

## Bone Meal conversion and growth

**Small Dripleaf does not duplicate itself with Bone Meal.** Use it on either half to convert the whole plant into a Big Dripleaf column, preserving facing. The conversion chooses a total height of **2–5 blocks**, stops at an obstruction or build limit, uses stems below, and places one leaf at the top. It first clears the old upper half back to its contained fluid. The growth space can be air, a Water block, or Small Dripleaf. Use a ground block valid for the resulting Big Dripleaf. [Small conversion] · [Random-height conversion] · [big_dripleaf_placeable tag]

**Big Dripleaf grows upward by one block per successful placement:** the old top becomes a stem and a new untilted leaf appears above it. Bone Meal can be used on the leaf or on a connected stem, which finds that same top. It needs air, a Water block, or Small Dripleaf above, and must fit within build height. There is no five-block maximum in this repeated-growth path and no age counter. [Big Bone Meal growth] · [Stem Bone Meal growth] · [Connected-top lookup] · [Random-height conversion]

These callbacks use an unconditional success roll once the target is accepted; **one Bone Meal is consumed per accepted Survival use**. The leaf's target check does not itself reject an out-of-height destination, so a use at the build ceiling can consume a meal without extending the plant; actual placement still checks the limit. These plants have no registered random-tick growth, so waiting does not replace Bone Meal. [Bone Meal consumption] · [Big Bone Meal growth] · [Stem Bone Meal growth] · [Dripleaf registry]

A source-derived collection loop is to extend Big Dripleaf, harvest its leaf and stems, and replant one of the returned Big Dripleaf items. This increases **Big Dripleaf**, not Small Dripleaf. Save your small plants before converting them; neither the growth callback nor the checked recipes turns big plants back into small ones. [Big Bone Meal growth] · [big_dripleaf loot] · [big_dripleaf_stem loot] · [Small conversion]

## Standing, tilt, and reset

The leaf is the only colliding part of a Big Dripleaf column; stems and Small Dripleaf have no collision. A flat leaf's top is **15/16 of a block high**, and its partial-tilt top is **13/16**. At full tilt the leaf has **no collision**, allowing an entity to fall through. [Dripleaf registry] · [Tilt timings and shapes] · [Tilt scheduling and reset]

The ordinary server trigger requires an entity inside the leaf's block, **on the ground**, with its position above **11/16 of that block's height**, while the leaf is flat and unpowered. It does not test whether the entity is crouching. For a fresh, uninterrupted unpowered cycle:

| State | Next step | Delay |
| --- | --- | --- |
| Flat (`none`) | An eligible entity starts `unstable` | On contact |
| Unstable | Becomes `partial` | **10 game ticks** |
| Partial | Becomes `full`; collision disappears | **10 more ticks** |
| Full | Resets to flat | **100 more ticks** |

Thus the ordinary new cycle loses its platform after about **1 second** and resets about **5 seconds later**, at 20 TPS. Leaving the leaf does not cancel the already-started cycle: scheduled updates advance it without requiring the entity to remain. [Entity inside dispatch] · [Entity and power tilt] · [Tilt timings and shapes] · [Tilt scheduling and reset] · [Scheduled tick dispatch]

### Redstone and projectiles

**Power received at the leaf prevents the ordinary entity trigger.** A powered neighbor update or powered scheduled tick also resets the leaf to flat. Powering a distant stem is not the condition being checked; it is the leaf position's neighbor signal. [Entity and power tilt]

**A projectile hit takes a different path.** Its callback immediately sets the leaf to full tilt and requests a reset tick after **100 ticks**. The checked projectile dispatcher, including the Arrow path, invokes this callback. It does **not** check redstone power first, so even a powered leaf can be forced down until a subsequent powered neighbor update or scheduled tick resets it. [Projectile tilt] · [Projectile hit dispatch] · [Arrow hit dispatch] · [Entity and power tilt] · [Tilt scheduling and reset]

Existing scheduled ticks can affect these timings. The scheduler keeps one pending tick for the same block type and position, and a new request does not replace that pending tick. Treat the table as the clean starting cycle, not a promise that repeated hits or power changes restart a full countdown. [Pending block tick uniqueness] · [Tick identity] · [Scheduled tick dispatch]

## Harvesting and other uses

Small Dripleaf breaks instantly. Its table tests **Shears item identity** and has no Silk Touch alternative or Fortune bonus. Normal player mining handles the selected half before removing the pair, so either half gives **one small item**, not two. Breaking support uses an empty tool and gives none. [Dripleaf registry] · [small_dripleaf loot] · [Double-plant harvest] · [Player mining dispatch] · [Support-loss drops]

Big leaves and stems have hardness **0.1**, require no harvest tier, and each return **one Big Dripleaf** with a hand or ordinary tool. An axe uses its efficient-mining rule; swords also have a 1.5-speed rule for these two blocks. Their complete loot tables have no Silk Touch or Fortune branch. See [Mining](../mechanics/Mining.md) for shared tool behavior. [Dripleaf registry] · [Tool gate] · [big_dripleaf loot] · [big_dripleaf_stem loot] · [mineable/axe tag] · [sword_efficient tag] · [Axe assignment] · [Axe mining rules] · [Sword mining rules]

No bundled recipe produces or consumes the three inventory items covered by this guide and [Lily Pad](LilyPad.md). The checked practical uses are planting, platforms, and [composting](Composter.md): **30%** for Small Dripleaf and **65%** for Big Dripleaf per ordinary level-increase roll. The first accepted item in an empty composter raises its level automatically. [Small composting] · [Big composting] · [Composter success]

## Checked acquisition routes

**Lush Caves** is a verified natural route. Its `lush_caves_clay` placement leads to dry or waterlogged clay vegetation patches, which invoke the configured `dripleaf` selector. That selector contains one small-plant option and four big-column options with different facings. The active small-feature path places both halves; the big-column path places stems and a leaf, with room-dependent truncation. This confirms both sizes in the feature chain, not a guaranteed count in every cave. [lush_caves biome] · [lush_caves_clay placement] · [lush_caves_clay feature] · [clay_with_dripleaves feature] · [clay_pool_with_dripleaves feature] · [dripleaf feature] · [Vegetation patch execution] · [Wet vegetation execution] · [Dripleaf selector] · [Small feature placement] · [Big column placement]

The Normal world preset's Overworld selection includes Lush Caves, and biome features run through the active placed/configured-feature dispatch. [Normal preset] · [Overworld preset] · [Overworld preset builder] · [Lush Caves selection] · [Biome feature dispatch] · [Placed feature dispatch] · [Configured feature dispatch]

A [Wandering Trader](../mobs/WanderingTrader.md) can offer **2 Small Dripleaves for 1 Emerald**, with **five uses** of that offer. It belongs to a group from which five listings are randomly selected, so an individual trader is not guaranteed to sell it. This is a source of new small plants; direct Bone Meal conversion consumes an existing small plant. [Trader offers] · [Offer construction] · [Trader group selection] · [Random listing selection]

## Sources and verification

Source-reviewed on **2026-10-02** at `96e5604a6abaec697de2004b1ba9775e303bfba7`. Checked all three dripleaf block IDs and their two inventory forms, complete loot, support/mining tags, placement and water callbacks, paired-half removal, Bone Meal paths, active collision/projectile/tick/power behavior, selected Lush Caves features, and trader selection. No in-game growth, harvesting, redstone, projectile, placement, trading, or world-generation test was run. Data packs can change tags, loot, recipes, and features.

[Dripleaf registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L6615-L6635
[Dripleaf items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L380-L381
[Small conversion]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SmallDripleafBlock.java#L117-L137
[small_dripleaf loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/small_dripleaf.json
[big_dripleaf loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/big_dripleaf.json
[big_dripleaf_stem loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/big_dripleaf_stem.json
[Double-plant harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L117
[Shared block item]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L2764-L2771
[Small support and placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SmallDripleafBlock.java#L50-L92
[small_dripleaf_placeable tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/small_dripleaf_placeable.json
[Plant soil support]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/dirt.json
[Double-plant placement and water]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L63-L99
[Double-height item]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/DoubleHighBlockItem.java#L16-L22
[Water-tag lookup]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/LevelReader.java#L142-L144
[Double-plant neighbor survival]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L39-L60
[Waterlogged bucket behavior]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L19-L50
[Support-loss drops]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[big_dripleaf_placeable tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/big_dripleaf_placeable.json
[Big support and stacking]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L138-L167
[Stem support and collapse]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafStemBlock.java#L59-L102
[Big placement and states]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L271-L284
[Random-height conversion]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L94-L126
[Big Bone Meal growth]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L169-L189
[Stem Bone Meal growth]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafStemBlock.java#L104-L136
[Connected-top lookup]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/BlockUtil.java#L123-L133
[Bone Meal consumption]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[Tilt timings and shapes]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L48-L71
[Tilt scheduling and reset]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L230-L263
[Entity inside dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1205
[Entity and power tilt]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L191-L223
[Scheduled tick dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L772
[Projectile tilt]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L128-L135
[Projectile hit dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L275-L291
[Arrow hit dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L487-L490
[Pending block tick uniqueness]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/ticks/LevelChunkTicks.java#L45-L63
[Tick identity]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/ticks/ScheduledTick.java#L23-L38
[Player mining dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L292
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mineable/axe tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[sword_efficient tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/sword_efficient.json
[Axe assignment]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[Axe mining rules]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[Sword mining rules]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[Small composting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L105
[Big composting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L171
[Composter success]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[lush_caves biome]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[lush_caves_clay placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/placed_feature/lush_caves_clay.json
[lush_caves_clay feature]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/configured_feature/lush_caves_clay.json
[clay_with_dripleaves feature]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/configured_feature/clay_with_dripleaves.json
[clay_pool_with_dripleaves feature]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/configured_feature/clay_pool_with_dripleaves.json
[dripleaf feature]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/configured_feature/dripleaf.json
[Vegetation patch execution]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/VegetationPatchFeature.java#L104-L136
[Wet vegetation execution]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/WaterloggedVegetationPatchFeature.java#L60-L80
[Dripleaf selector]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleRandomSelectorFeature.java#L17-L27
[Small feature placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L43
[Big column placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/BlockColumnFeature.java#L16-L71
[Normal preset]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Overworld preset]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L79
[Overworld preset builder]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L106-L109
[Lush Caves selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L817-L822
[Biome feature dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Placed feature dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[Configured feature dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L815-L828
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Trader group selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Random listing selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L234
