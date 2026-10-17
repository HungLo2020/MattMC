# Efficiency, Fortune and Silk Touch

Use **Efficiency** to improve a suitable tool's block-mining speed, **Fortune** for the resource branches that reward it, and **Silk Touch** for blocks whose loot tables preserve the block. Efficiency can accompany either loot enchantment; Fortune and Silk Touch exclude each other. Choose the tool family and material first using [Mining](../mechanics/Mining.md), then choose the effect you need. [Efficiency definition] · [Fortune definition] · [Silk Touch definition] · [Mining exclusions]

## Levels and supported tools

| Enchantment and exact ID | Normal levels | Supported tools for ordinary Survival application |
| --- | --- | --- |
| Efficiency (`minecraft:efficiency`) | I–V | Pickaxes, Shovels, Axes, Hoes and Shears |
| Fortune (`minecraft:fortune`) | I–III | Pickaxes, Shovels, Axes and Hoes |
| Silk Touch (`minecraft:silk_touch`) | I | Pickaxes, Shovels, Axes and Hoes |

The four tool families include **Wood, Stone, Copper, Iron, Gold, Diamond and Netherite**: 28 named tools. Efficiency additionally supports Shears. These are the actual expanded supported-item tags; imported tools are not automatically included just because their names sound like a pickaxe or axe. Use [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) and [Axes and Hoes](../mechanics/AxesAndHoes.md) for their recipes and material choices. [Mining-tool support] · [Efficiency support] · [Pickaxe items] · [Shovel items] · [Axe items] · [Hoe items]

All three belong to the [Enchanting Table](../blocks/EnchantingTable.md) pool. Offers still depend on the target item, its enchantability, offer strength, random selection and compatibility. See [Enchanting](Enchanting.md) for bookshelf setup, costs and changing offers. [Table pool] · [Pool members] · [Table selection] · [Item and level selection]

**Shears are an Anvil case for Efficiency.** Although their support tag allows Efficiency, the registered Shears have no enchanting-enabled component, so the table gives them no offers. Apply a suitable [Enchanted Book](../items/EnchantedBook.md) with an [Anvil](../blocks/Anvil.md), following its normal experience and repair-cost rules. Fortune and Silk Touch do not include Shears in their ordinary supported-item tag. [Shears registration] · [Table item gate] · [Anvil book and support checks]

The exclusion applies during table selection and Anvil combination, including Books. Efficiency has no conflict with either Fortune or Silk Touch in these definitions, so separate Efficiency/Fortune and Efficiency/Silk Touch tools are a useful choice when you want both resources and collectible blocks. [Compatibility rule] · [Table compatibility filtering] · [Anvil compatibility]

## Efficiency: when the speed bonus applies

Hold the enchanted tool in your **main hand**. At level L, Efficiency adds **L² + 1** to the mining-efficiency attribute. The player uses that addition only when the held item's initial speed against the target block is **greater than 1**. That test happens before Haste, Mining Fatigue, the block-break-speed attribute, water and airborne adjustments. [Level-squared value] · [Attribute effect] · [Main-hand modifier dispatch] · [Active player mining calculation]

| Efficiency level | Added speed term when the initial speed is above 1 |
| --- | ---: |
| I | +2 |
| II | +5 |
| III | +10 |
| IV | +17 |
| V | +26 |

For example, an unbroken Diamond tool on one of its matching mining targets starts at **8**; Efficiency V makes that term **34** before the later player modifiers. A normal tool on an unmatched block uses its **1** baseline, so it gets no Efficiency addition there. Tool speed and drop permission are separate: a matching-family tool can receive the speed bonus while still failing a material-tier drop restriction. [Material values and rule order] · [Speed versus drop matching]

The bonus also applies to Shears' faster block-mining rules, such as Leaves and Wool. It is a block-mining speed effect, not an extra-drop multiplier. A retained **broken tool** reports speed 1, so this Efficiency addition stops. Repair it using [Durability and repair](../mechanics/Durability.md). [Shears block speeds] · [Broken-speed guard]

These numbers are calculation terms, not fixed breaking times or items per second. Block hardness and the correct-tool result also enter break progress; unbreakable hardness still gives zero progress. [Block progress calculation]

## Fortune: the block decides the reward

Fortune has **no universal item multiplier**. The active loot rule reads the Fortune level on the tool supplied to that drop operation, then applies that block's formula or chance table. A block with no Fortune branch gains no Fortune bonus. [Fortune count reader] · [Fortune chance reader]

Selected examples for ordinary successful player mining, with block drops enabled:

| Target | Fortune result | Related owner and exact loot |
| --- | --- |
| Coal Ore or Diamond Ore | The one-item resource branch can give **1–4 items with Fortune III**; four is not guaranteed | [Ore resources](../blocks/OreResources.md#fortune-versus-silk-touch) · [Coal loot] · [Diamond loot] |
| Redstone Ore | Base 4–5 Dust plus a random 0–L bonus: **4–8 Dust with Fortune III** | [Ore resources](../blocks/OreResources.md#fortune-versus-silk-touch) · [Redstone loot] |
| Gravel | Fortune changes the Flint-selection chance; the Fortune III entry selects **Flint** | [Soil, Sand and Gravel](../blocks/SoilSandAndGravel.md) · [Gravel loot] |
| Ancient Debris | Its table has **no Fortune bonus** and returns the block itself | [Ancient Debris processing](../blocks/OreResources.md#ancient-debris-to-netherite) · [Debris loot] |

The ore formula multiplies the base resource roll by a factor from 1 through L+1, with factor 1 occupying two equally likely draws. Redstone instead adds a random integer from 0 through L. Those are different calculations; the [Ore resources guide](../blocks/OreResources.md#fortune-versus-silk-touch) owns the complete ore comparison. Leaves use their own chance tables; see [Leaves and Propagules](../blocks/TreeLeaves.md). [Ore formula] · [Uniform bonus formula] · [Oak Leaves example]

## Silk Touch: collection and experience

Silk Touch works when a loot table checks the supplied tool for Silk Touch and provides a matching branch. For the checked Coal, Diamond and Redstone ores, that branch selects **one ore block** before the resource branch. Ordinary Glass has a Silk Touch-only item pool. Ancient Debris already drops itself without Silk Touch. Use [Ore resources](../blocks/OreResources.md#fortune-versus-silk-touch), [Glass and Panes](../blocks/GlassAndPanes.md) and [Stone](../blocks/Stone.md) for material-specific results. [Silk tool predicate] · [Glass loot] · [Coal loot] · [Diamond loot] · [Redstone loot] · [Debris loot]

For ores using the checked block-experience path, Silk Touch also sets the mining XP to **zero**. Fortune does not make that XP scale with the number of resource items dropped. Item loot and block XP are separate operations; collecting an ore block does not pay the ordinary resource-mining XP at the same time. [Silk Touch definition] · [Fortune definition] · [Ore XP callback] · [Block XP processing] · [Enchantment XP dispatch]

## Gates that enchantments do not remove

**Use the correct tool and tier where the block requires them.** In ordinary player mining, the server checks drop eligibility before calling the block's player-drop path. Fortune and Silk Touch operate inside that later path; they do not turn an unsuitable pickaxe into a suitable one or repair a missing mining tag. Speed, harvest permission and the loot table must each agree. [Correct-tool gate] · [Active mining and copied tool] · [Player drop call]

A broken tool fails the normal correct-tool check on blocks that require it. However, breaking does not erase the saved enchantments: loot readers still inspect the supplied stack on routes that reach them. Avoid the broader assumption that every Fortune/Silk Touch effect is globally disabled on every broken stack. The last eligible mining operation copies the loot tool and checks eligibility **before** applying that operation's wear, so reaching the durability limit on that use does not retroactively invalidate its otherwise eligible drop. [Broken correct-tool check] · [Broken blocks can still be broken] · [Stored applied level] · [Active mining and copied tool]

**Silk Touch cannot invent an absent reward.** Suspicious Sand and Suspicious Gravel have no ordinary block-loot pools in the bundled data, so these enchantments do not collect the suspicious block or release its archaeological contents through normal mining. Use [Brush](../items/Brush.md#archaeology-basics) for that separate operation. A missing loot table likewise resolves to an empty table; Fortune has no item stack to multiply. [Suspicious Sand loot] · [Suspicious Gravel loot] · [Empty pool defaults] · [Block loot lookup] · [Missing-table fallback]

The block-drop game rule also gates spawning the resulting items and mining XP. These enchantments do not override it. [Item and XP drop gate]

## Why another harvesting method can differ

The drop operation uses the **tool passed by its caller**, not every enchanted item in your inventory. Ordinary player mining passes a copy of the main-hand tool. The checked explosion path passes an empty tool, so merely holding Fortune or Silk Touch while causing an explosion does not add those tool effects to its ordinary block loot. [Tool-bearing loot context] · [Explosion tool context]

MattMC's crop interaction is another distinct caller: ordinary itemless harvesting passes an empty tool, while a mature crop's Hoe-area harvest passes the held Hoe to each harvested crop's loot. Fortune therefore follows the crop table, not a blanket harvest multiplier. For Wheat, its Fortune bonus is on Seeds rather than the single Wheat item. Use the established [Wheat controls](../blocks/Wheat.md#mattmc-harvesting-controls), [root crop controls](../blocks/RootCrops.md#mattmc-harvesting-controls) and [broken-Hoe exception](../mechanics/AxesAndHoes.md#fully-damaged-hoes-still-reach-this-harvest-path) for the full rules. [Crop interaction contexts] · [Wheat loot]

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. This guide covers only Efficiency, Fortune and Silk Touch: their definitions, expanded eligibility/exclusion tags, table and Anvil checks, current attribute/mining consumers, selected loot and XP readers, and normal player/crop/explosion tool contexts. Definitions are loaded from the active enchantment registry resources. [Enchantment registry loader] · [Resource loading]

No game, timed-mining, enchantment-roll, drop-rate, explosion or crop-harvest test was run. The numerical ranges are source-derived possibilities for the cited tables, not measured yields. Data packs can change definitions, tags and loot. Existing [Enchanting](Enchanting.md), [Mining](../mechanics/Mining.md), tool and block guides retain their detailed recipes, controls and material behavior.

[Efficiency definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/efficiency.json
[Fortune definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/fortune.json
[Silk Touch definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/silk_touch.json
[Mining exclusions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/mining.json
[Mining-tool support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/mining_loot.json
[Efficiency support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/mining.json
[Pickaxe items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/pickaxes.json
[Shovel items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/shovels.json
[Axe items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/axes.json
[Hoe items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/hoes.json
[Table pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[Pool members]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[Table selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L198
[Item and level selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[Shears registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1742-L1744
[Table item gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L972
[Anvil book and support checks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L127-L205
[Compatibility rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L162
[Table compatibility filtering]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L551-L575
[Anvil compatibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L205
[Level-squared value]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/LevelBasedValue.java#L115-L123
[Attribute effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/EnchantmentAttributeEffect.java#L19-L36
[Main-hand modifier dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L393-L398
[Active player mining calculation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L652
[Material values and rule order]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[Speed versus drop matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L57
[Shears block speeds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L44
[Broken-speed guard]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[Block progress calculation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L347-L354
[Fortune count reader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L61-L75
[Fortune chance reader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L32-L42
[Coal loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/coal_ore.json
[Diamond loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/diamond_ore.json
[Redstone loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/redstone_ore.json
[Gravel loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/gravel.json
[Debris loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/ancient_debris.json
[Ore formula]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L132-L148
[Uniform bonus formula]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L166
[Oak Leaves example]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[Silk tool predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/predicates/MatchTool.java#L23-L31
[Glass loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/glass.json
[Ore XP callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DropExperienceBlock.java#L29-L36
[Block XP processing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Block.java#L584-L589
[Enchantment XP dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L102-L106
[Correct-tool gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656
[Active mining and copied tool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[Player drop call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[Broken correct-tool check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L576-L589
[Broken blocks can still be broken]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L1111-L1116
[Stored applied level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L50-L53
[Suspicious Sand loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[Suspicious Gravel loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/suspicious_gravel.json
[Empty pool defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootTable.java#L37-L50
[Block loot lookup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L269-L277
[Missing-table fallback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L121
[Item and XP drop gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Block.java#L410-L421
[Tool-bearing loot context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Block.java#L353-L361
[Explosion tool context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L176-L194
[Crop interaction contexts]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/CropBlock.java#L193-L246
[Wheat loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/wheat.json
[Enchantment registry loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L124-L128
[Resource loading]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L330
