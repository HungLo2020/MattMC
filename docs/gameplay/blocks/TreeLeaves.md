# Tree leaves and Mangrove Propagules

Collect leaves with **Shears or Silk Touch** for permanent hedges, or harvest them without either to roll for planting material, sticks, and occasional fruit. This guide covers the eleven registered vanilla leaf blocks and the Mangrove Propagule. [Oak](Oak.md#leaf-drops) remains the detailed oak harvesting and growing guide; [Tree Logs and Roots](TreeLogsAndRoots.md) covers trunk materials.

## Leaf families and drops

All IDs use the `minecraft:` namespace. Every row can instead drop **one matching leaf block** when harvested with Shears or Silk Touch; this bypasses the material rolls below. A hoe is the efficient ordinary tool when collecting material drops, and Shears have a dedicated **15× leaf mining-speed value**. No leaf in this table has a correct-tool tier requirement. [Leaf registration][s1] · [Shared leaf properties][s2] · [Hoe mining tag][s3] · [Exact leaf tag][s4] · [Shears tool rules][s5] · [Shears registration][s6] · [Harvest eligibility][s7]

| Leaf block ID | Plant roll without Shears / Silk Touch | Fruit roll | Other roll | Checked loot |
| --- | --- | --- | --- | --- |
| <span id="oak-leaves">`oak_leaves`</span> | 1 Oak Sapling (Standard chance) | 1 Apple | 1–2 Sticks | [Loot][s8] |
| <span id="spruce-leaves">`spruce_leaves`</span> | 1 Spruce Sapling (Standard chance) | None | 1–2 Sticks | [Loot][s9] |
| <span id="birch-leaves">`birch_leaves`</span> | 1 Birch Sapling (Standard chance) | None | 1–2 Sticks | [Loot][s10] |
| <span id="jungle-leaves">`jungle_leaves`</span> | 1 Jungle Sapling (Jungle chance) | None | 1–2 Sticks | [Loot][s11] |
| <span id="acacia-leaves">`acacia_leaves`</span> | 1 Acacia Sapling (Standard chance) | None | 1–2 Sticks | [Loot][s12] |
| <span id="cherry-leaves">`cherry_leaves`</span> | 1 Cherry Sapling (Standard chance) | None | 1–2 Sticks | [Loot][s13] |
| <span id="dark-oak-leaves">`dark_oak_leaves`</span> | 1 Dark Oak Sapling (Standard chance) | 1 Apple | 1–2 Sticks | [Loot][s14] |
| <span id="pale-oak-leaves">`pale_oak_leaves`</span> | 1 Pale Oak Sapling (Standard chance) | None | 1–2 Sticks | [Loot][s15] |
| <span id="mangrove-leaves">`mangrove_leaves`</span> | None; use the propagule interaction below | None | 1–2 Sticks | [Loot][s16] |
| <span id="azalea-leaves">`azalea_leaves`</span> | 1 Azalea (Standard chance) | None | 1–2 Sticks | [Loot][s17] |
| <span id="flowering-azalea-leaves">`flowering_azalea_leaves`</span> | 1 Flowering Azalea (Standard chance) | None | 1–2 Sticks | [Loot][s18] |

### Fortune chances

These are the ordinary non-explosion chances for each **separate roll**. The stick count is one or two only when that roll succeeds. A block may give nothing or several kinds of item; the rolls are not a choice between a sapling, stick, or apple.

| Roll | No Fortune | Fortune I | Fortune II | Fortune III |
| --- | ---: | ---: | ---: | ---: |
| Standard plant: all listed saplings except Jungle; Azalea or Flowering Azalea | 5% | 6.25% | about 8.33% | 10% |
| Jungle Sapling | 2.5% | about 2.78% | 3.125% | about 4.17% |
| Sticks, all eleven leaves | 2% | about 2.22% | 2.5% | about 3.33% |
| Apple, Oak and Dark Oak only | 0.5% | about 0.556% | 0.625% | about 0.833% |

The table covers normal Fortune levels I–III. **Pale Oak has no apple pool. Mangrove has no sapling or propagule pool.** Azalea leaves yield Azalea, and Flowering Azalea leaves yield Flowering Azalea; these are shrubs rather than items named “Azalea Sapling.” The complete per-leaf sources are linked above. The active loot condition selects a chance by Fortune level; natural decay passes an empty tool, so it uses the no-Fortune column. Explosions apply their additional loot conditions and are not equivalent to hand harvesting. [Fortune lookup][s19] · [Tool-free decay drops][s20]

## Keeping leaves or letting them decay

Leaves track a distance from **log-tag blocks**, which include every log, wood, stem, and hyphae form in [Tree Logs and Roots](TreeLogsAndRoots.md#logs-wood-stems-and-hyphae). A log has distance 0; a leaf takes one plus the lowest distance among its six face-adjacent neighbors, capped at 7. Any matching log-tag block can preserve any of these leaf species, and mixed leaf species can pass the distance along. Mangrove roots, planks, and Bamboo Blocks are not in this bundled log tag. [Distance propagation][s21] · [Logs][s22] · [Overworld log families][s23] · [Crimson sources][s24] · [Warped sources][s25]

A **non-persistent leaf at distance 7** drops resources and disappears when it receives its selected random tick. This is not a fixed timer after chopping a trunk. Distance updates are scheduled after neighboring changes, and a remaining branch or another nearby tree can prevent decay. If leaves persist, check for remaining logs and leaf connections to another trunk. [Decay and random-tick eligibility][s26] · [Distance update scheduling][s27]

Leaves placed from an item become **persistent**, even if far from logs, so ordinary distance-based decay will not remove a player-built hedge. Their loot table does not distinguish persistent from natural leaves. Recollected leaves become persistent again on placement; the loot does not preserve their old distance or waterlogged state. [Player placement][s28] · [Example leaf loot][s29]

## Building with leaves

The selected leaf blocks have **hardness 0.2 and blast resistance 0.2**. They keep a full-cube collision shape, but their block-support shape is empty, so do not assume their solid appearance gives the sturdy face required by attachments. They block **one light level** in their light-block callback and are explicitly not redstone conductors. These are block rules, not a measured canopy-lighting or shader result. [Leaf properties][s30] · [Common leaf properties][s31] · [Support and light][s32] · [Default collision][s33] · [Strength interpretation][s34]

All eleven can be waterlogged by placement in **source water** or by adding source water with a bucket. Flowing water has a different fluid type from the placement check. An empty bucket can take the stored water back. Water does not make a natural leaf persistent or cancel its distance rule. [Leaf placement and water state][s35] · [Stored leaf fluid][s36] · [Bucket fill and pickup][s37] · [Decay condition][s38]

Dry leaves are flammable; waterlogged leaves have zero ignition and burn odds in the ordinary fire routine. Their flammability does **not** make the leaf items furnace fuel: no selected leaf item is added to the default fuel table. [Leaf fire entries][s39] · [Azalea fire entries][s40] · [Waterlogged fire checks][s41] · [Complete default fuel list][s42]

Leaf items can go into a [Composter](../items/Composter.md): Flowering Azalea Leaves have a **50%** chance to raise a partly filled composter by one level; the other ten have **30%**. The first accepted item in an empty composter raises it unconditionally. The item is consumed on ordinary Survival use even if a later roll fails. [Compost entries][s43] · [Use and consumption][s44] · [Level advancement][s45] · [Active bootstrap][s46]

## Mangrove Propagules

**`minecraft:mangrove_propagule`** supplies a planting item through a separate leaf interaction. Breaking Mangrove Leaves does not roll for it. Use [Bone Meal](../items/BoneMeal.md) on Mangrove Leaves with **air immediately below**: the callback creates a hanging propagule there at age 0 and consumes one Bone Meal in ordinary Survival. It works on player-placed persistent Mangrove Leaves too, because the target check only requires that air cell. Water occupying the cell below fails that air requirement. [Mangrove leaf Bone Meal][s47] · [Bone Meal dispatch and consumption][s48] · [New hanging state][s49]

Leave the supporting Mangrove Leaves in place while the propagule matures. A hanging propagule advances by one age on each selected random tick until **age 4**; Bone Meal on an immature hanging propagule also advances it by one, with no failure roll. Four successful applications on a newly created age-0 hanging propagule therefore mature it, or you can wait for its random ticks. Hanging maturation has no brightness condition in this callback and does not attempt to grow a tree. [Hanging support][s50] · [Hanging maturation and Bone Meal][s51]

**Only age 4 drops a Propagule item.** Earlier ages drop nothing, including with Shears or Silk Touch, because the loot table has only an age-4 condition and no special tool branch. At maturity ordinary breaking gives one item; Fortune does not increase it. Breaking its supporting leaf also removes the hanging block, so harvesting too early loses that propagule. [Propagule loot][s52] · [Support removal][s53]

Place the collected item on **farmland, a dirt-tag block, or Clay**. It places as non-hanging, age 4, and can be waterlogged in source water. You cannot recreate the hanging form merely by clicking the underside of a leaf with the item; the checked item placement uses the non-hanging default. The dirt tag includes Mud and Muddy Mangrove Roots, among other soils. Tree growth and clearance are outside this guide's scope. [Propagule default and placement][s54] · [Vegetation support][s55] · [Dirt-tag supports][s56] · [Block/item registration][s57]

A Propagule item also supplies **100 default furnace burn ticks** through the saplings tag, and its composting chance is **30%** after the guaranteed first layer. [Flower Pots](FlowerPot.md) covers its potted decoration. [Saplings fuel tag][s58] · [Fuel scale and saplings][s59] · [Compost entry][s60] · [Potted registration][s61]

## Scope and related pages

This guide checks placed foliage, drops, decay, and the propagule collection loop. It does not establish biome distributions, sapling growth times, tree heights, or generation rates. [Pewen](Pewen.md) has separate imported foliage behavior; Ancient leaves are not part of this eleven-leaf matrix. Nether wart blocks are separate blocks, not leaf-tag entries in this review.

Related: [Tree Logs and Roots](TreeLogsAndRoots.md) · [Mangrove Leaves item](../items/MangroveLeaves.md) · [Mangrove Propagule item](../items/MangrovePropagule.md) · [Oak](Oak.md) · [Mining](../mechanics/Mining.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. All eleven leaf loot tables and the propagule table were compared with active registered classes, tags, callbacks, and tool/loot dispatch. No in-game harvest, decay, Bone Meal, waterlogging, composting, or tree-growth test was run. Loot and tag changes from data packs, random-tick delivery, and server rules can change outcomes.

[s1]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L551-L613
[s2]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L7200-L7213
[s3]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[s4]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/leaves.json
[s5]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L44
[s6]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L1742-L1745
[s7]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[s8]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[s9]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/spruce_leaves.json
[s10]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/birch_leaves.json
[s11]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/jungle_leaves.json
[s12]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/acacia_leaves.json
[s13]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/cherry_leaves.json
[s14]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_leaves.json
[s15]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_leaves.json
[s16]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_leaves.json
[s17]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/azalea_leaves.json
[s18]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/flowering_azalea_leaves.json
[s19]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L36-L42
[s20]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Block.java#L345-L368
[s21]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L90-L138
[s22]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/logs.json
[s23]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/logs_that_burn.json
[s24]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/crimson_stems.json
[s25]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/warped_stems.json
[s26]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L40-L71
[s27]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L90-L125
[s28]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L174-L184
[s29]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[s30]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L551-L613
[s31]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L7200-L7213
[s32]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L46-L76
[s33]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L326
[s34]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[s35]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L174-L184
[s36]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L140-L143
[s37]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L20-L51
[s38]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L64-L66
[s39]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/FireBlock.java#L402-L410
[s40]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/FireBlock.java#L492-L493
[s41]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/FireBlock.java#L222-L233
[s42]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[s43]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L64-L117
[s44]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L244-L256
[s45]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[s46]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/server/Bootstrap.java#L47-L50
[s47]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangroveLeavesBlock.java#L31-L45
[s48]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[s49]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L143-L149
[s50]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L73-L96
[s51]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L103-L140
[s52]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_propagule.json
[s53]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L73-L96
[s54]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L43-L75
[s55]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[s56]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/dirt.json
[s57]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L139-L144
[s58]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/saplings.json
[s59]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L88
[s60]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L84-L89
[s61]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L2685-L2687
