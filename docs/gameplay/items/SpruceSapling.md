# Spruce Sapling

Plant one Spruce Sapling for a single Spruce tree, or four matching saplings in a 2 × 2 square for a large Spruce-family tree. The large route can also convert eligible nearby ground to Podzol.

## Obtaining

[Taiga](../biomes/TaigaAndSnowyBiomes.md#taiga) is a useful starting destination: its standing Pine-shaped and Spruce-shaped trees both provide Spruce Leaves. Break those leaves **without Shears or Silk Touch**, or gather their natural decay drops. Each leaf has a chance of one Spruce Sapling: **5% without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, or **10% with Fortune III**. Shears or Silk Touch select the leaf block instead, and decay uses no Fortune. These are ordinary non-explosion chances, not a promised return from every tree. [Leaf loot][leaf-loot] · [Fortune lookup][fortune] · [Decay][decay] · [Tool-free drops][empty-tool]

A Wandering Trader can offer **5 Emeralds → 1 Spruce Sapling**, with **8 uses**. Offers are randomly selected, so that stock is not guaranteed on any particular trader. The **taiga-house chest loot table** can also select **1–5 Spruce Saplings** in one entry; this is a possible find, not a guaranteed chest or village yield. [Trade][trades] · [Quantities][trade-count] · [Active trader selection][trader] [trade-choice] · [Chest entry][chest]

Breaking a planted Spruce Sapling normally returns **one Spruce Sapling**, including by hand. Silk Touch is unnecessary and Fortune does not increase it; the table separately checks explosion survival. No bundled direct crafting recipe for this sapling was found. [Plant loot][plant-loot] · [Registration][registration] · [Harvest gate][gate]

## Usage

A **single sapling** selects `spruce`. Four Spruce Saplings in a **2 × 2 square at the same height** select `mega_spruce` or `mega_pine`, with a **50% selection chance each**. Those are feature choices, not guaranteed successful trees. Both large forms use [Spruce Logs](../blocks/TreeLogsAndRoots.md#spruce-timber) and Spruce Leaves; a separate Pine Sapling is not needed for this route. [Grower][selection] · [Square dispatch][dispatch] · [Single tree][tree] · [Mega Spruce][large-tree] · [Mega Pine][other-large]

Plant on **Farmland or a dirt-tag block**, such as Dirt, Grass Block, Coarse Dirt, Podzol, Rooted Dirt, Moss Block or Mud. Valid support permits placement; the later tree attempt also needs room. Spruce Saplings have no waterlogged state, and a carried inventory stack does not run the planted block's growth. [Support][support] · [Soil tag][soil] · [Placement][placement] · [Sapling state and callbacks][growth]

Both large configurations include a **Podzol ground decorator**. Keep valuable growing beds away from the planting area: it can replace eligible dirt-tag blocks around the tree, beyond the four blocks directly underneath. See [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md#space-failure-and-recovery) for the detailed ground and clearance rules. [Large features][large-tree] [other-large] · [Ground decorator][ground-decorator] · [Ground eligibility][ground-test]

A [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants) holds a decorative sapling; remove it and plant it in soil for growth. Spare saplings supply **100 default furnace burn ticks** each, or a **30% composting chance** after the guaranteed first accepted layer in an empty [Composter](../blocks/Composter.md). Ordinary Survival composting spends the item even if a later roll fails. [Potted forms][pot-registration] · [Pot interaction][pot] · [Fuel][fuel] [sapling-tag] · [Compost entry][compost] · [Consumption][compost-use] · [Layer roll][compost-roll]

## Behavior

A newly placed sapling starts at **hidden stage 0**. A selected natural random tick needs **brightness at least 9 above it** and a **1-in-7** roll to advance. The first advance changes it to stage 1; a later advance attempts a tree. There is no fixed growth time. [Sapling stages][growth]

[Bone Meal](BoneMeal.md) gives a **45% chance per accepted use to advance**, without the natural-growth brightness test. One Bone Meal is consumed in ordinary Survival even if the roll fails, only the hidden stage changes, or the selected tree cannot fit. More Bone Meal cannot substitute for the correct layout or open space. [Growth callback][growth] · [Consumption][meal]

Only the initiating sapling in a matching square needs to reach its tree-attempt stage; the other three can still be at stage 0. Without a matching square, Spruce uses its single route. If a complete square is found but its large-tree placement fails, that attempt **does not fall back to a single tree**. The grower restores all four positions using the initiating sapling's state, rather than preserving four separate stage values. [Pattern, attempt and recovery][dispatch]

Both large configurations allow Vines to obstruct the checked space. Clear overhead and side obstacles before repeatedly spending Bone Meal. For detailed geometry, ticking and the limits of plant restoration, use the [family growth guide](../blocks/SaplingsAndAzaleas.md#ordinary-sapling-comparison) and its [space and recovery section](../blocks/SaplingsAndAzaleas.md#space-failure-and-recovery). [Large configurations][large-tree] [other-large] · [Clearance][space]

## Notes

- Exact registered block/item ID: **`minecraft:spruce_sapling`**. Its item is also listed in the Natural Blocks Creative inventory. [Block][registration] · [Item][items] · [Creative listing][creative]
- The recipe browser looks up recipe **outputs**, not leaf drops, chest loot, trader stock or growth. Its cache can also be empty when it cannot obtain a recipe manager. A blank lookup therefore is not proof that this item is unobtainable. [Lookup][recipe-browser] · [Empty-result handling][browser-empty]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registration, leaf/plant/chest loot, trades, bundled recipes and the active tree routes were checked. No in-game harvesting, growth, chest-looting, trading, or browser test was run. Data packs and server settings can change outcomes.

Related: [Tree Leaves](../blocks/TreeLeaves.md#leaf-families-and-drops) · [Taiga biomes](../biomes/TaigaAndSnowyBiomes.md) · [Items](Items.md)

[leaf-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/spruce_leaves.json#L1-L62
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[decay]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L56-L65
[empty-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L345-L367
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L781-L789
[trade-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1478
[trader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L140
[chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_taiga_house.json#L116-L131
[plant-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/spruce_sapling.json#L1-L21
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L213-L217
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L27-L65
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L127-L193
[tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/spruce.json#L1-L64
[large-tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/mega_spruce.json#L1-L69
[other-large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/mega_pine.json#L1-L69
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L136
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L34-L79
[ground-decorator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/AlterGroundDecorator.java#L25-L70
[ground-test]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L197-L203
[pot-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2658-L2683
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L48-L90
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L80-L88
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L243-L255
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[space]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L114
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L132-L142
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L873-L885
[recipe-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java#L29-L132
[browser-empty]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L327-L337
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[sapling-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/saplings.json#L1-L15
