# Cherry Sapling

A single Cherry Sapling grows a Cherry tree for pink-toned timber and foliage. Allow room around the planting site for its branches and canopy.

## Obtaining

Search [Cherry Grove](../biomes/PlainsAndMeadows.md#cherry-grove) for the Cherry Leaves that supply your first saplings. Break Cherry Leaves **without Shears or Silk Touch**, or collect the drops when eligible natural leaves decay. Each leaf has a chance to yield one Cherry Sapling: **5% without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, and **10% with Fortune III**. Shears or Silk Touch select the leaf block instead of this sapling roll; decay uses no Fortune. These are ordinary non-explosion chances, not a promised replacement for every planted tree. [Leaf loot][leaf-loot] · [Fortune lookup][fortune] · [Decay][decay] · [Tool-free drops][empty-tool]

A Wandering Trader can offer **5 Emeralds → 1 Cherry Sapling**, with **8 uses** for that offer. Offers are randomly selected, so check the actual trader rather than assuming it carries every sapling. [Offer][trades] · [Quantities][trade-count] · [Active selection][trader] [trade-choice]

Breaking an already planted Cherry Sapling normally returns **one matching item**, including by hand. Neither Silk Touch nor Fortune improves that recovery; the loot table has its own explosion-survival condition. No bundled direct crafting recipe for this item was found. [Plant loot][plant-loot] · [Registration][registration] · [Harvest gate][gate]

## Usage

Use **one sapling per tree**. The active grower selects `cherry`, or `cherry_bees_005` when a nearby flower-tag block qualifies. Both use [Cherry Logs](../blocks/TreeLogsAndRoots.md#cherry-timber) and Cherry Leaves. A 2 × 2 square does not select a combined Cherry tree. [Grower][selection] · [Cherry feature][tree] · [Bee-capable feature][bee-tree]

Place the item on **Farmland or a dirt-tag block**, such as Dirt, Grass Block, Coarse Dirt, Podzol, Rooted Dirt, Moss Block or Mud. Placement checks support, while the later tree attempt checks space separately. These saplings have no waterlogged state. Carrying one in your inventory does not run the placed block's growth. [Support][support] · [Ground tag][soil] · [Placement check][placement] · [Sapling state and callbacks][growth]

For a small decoration, put it into a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants); remove it and plant it in soil to grow a tree. Spare items supply **100 default furnace burn ticks** each, or a **30% composting chance** after the guaranteed first accepted layer in an empty [Composter](../blocks/Composter.md). Composting consumes the item on an ordinary Survival attempt even when a later roll fails. Keep your replanting supply first. [Potted forms][pot-registration] · [Pot interaction][pot] · [Fuel][fuel] [sapling-tag] · [Compost entry][compost] · [Consumption][compost-use] · [Layer roll][compost-roll]

## Behavior

A newly placed sapling starts at **hidden stage 0**. A selected natural random tick requires **brightness at least 9 above it** and passes a **1-in-7** roll to advance. The first advance changes it to stage 1; a later advance attempts the selected tree. There is no fixed waiting time. [Stages and random growth][growth]

[Bone Meal](BoneMeal.md) gives a **45% chance per accepted use to advance**, without the natural-growth brightness check. Ordinary Survival use consumes one Bone Meal even if the roll fails, only the hidden stage changes, or the tree cannot fit. Bone Meal does not bypass the planting pattern or clearance checks. [Sapling callback][growth] · [Consumption][meal]

A flower-tag block within two blocks horizontally and one vertically selects the bee-capable configuration. That configuration makes a **5% nest-decorator roll** and still needs a suitable air cell beside the trunk with air in front. Flowers help select the route, but do not guarantee a Bee Nest. Follow the [family flower guide](../blocks/SaplingsAndAzaleas.md#flowers-and-bee-capable-trees) for its full search area. [Search][flower-search] · [Flower tag][flowers] · [Bee feature][bee-tree] · [Nest checks][nest]

For an obstructed single-tree attempt, the grower restores the original sapling state. For detailed planting patterns, ticking and geometry checks, use [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md#ordinary-sapling-comparison) and its [space and recovery section](../blocks/SaplingsAndAzaleas.md#space-failure-and-recovery). A valid support block is not a guaranteed clear canopy. [Tree checks][space] · [Restoration][dispatch]

## Notes

- Exact registered block/item ID: **`minecraft:cherry_sapling`**; this page is the inventory form of the planted block. It is also listed in the Natural Blocks Creative inventory. [Block][registration] · [Item][items] · [Creative listing][creative]
- The recipe browser searches recipe **outputs**; it does not document leaf drops, trader stock, or growth. Its cache can also be empty when it cannot obtain the recipe manager. An empty lookup therefore is not proof that the item cannot be obtained. [Lookup and cache][recipe-browser] · [Empty-result handling][browser-empty]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registrations, relevant loot, trader offers, growth callbacks and selected feature data were checked; the bundled recipe inventory contained no direct producing recipe. No in-game harvesting, growth, trading, or browser test was run. Data packs and server settings can change outcomes.

Related: [Tree Leaves](../blocks/TreeLeaves.md#leaf-families-and-drops) · [Tree Logs and Roots](../blocks/TreeLogsAndRoots.md) · [Items](Items.md)

[leaf-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cherry_leaves.json#L1-L62
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[decay]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L56-L65
[empty-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L345-L367
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L781-L789
[trade-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1478
[trader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L140
[plant-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cherry_sapling.json#L1-L21
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L243-L253
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L27-L65
[tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/cherry.json#L1-L87
[bee-tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/cherry_bees_005.json#L1-L92
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L136
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L34-L79
[pot-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2658-L2683
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L48-L90
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L80-L88
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L243-L255
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[flower-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L195-L203
[flowers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/flowers.json#L1-L19
[nest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L38-L66
[space]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L114
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L127-L193
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L132-L142
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L873-L885
[recipe-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java#L29-L132
[browser-empty]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L327-L337
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[sapling-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/saplings.json#L1-L15
