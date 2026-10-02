# Nylium and Nether vegetation

**Nylium** supports renewable Nether vegetation, **Roots and Nether Sprouts** are small decorative plants, and **Nether/Warped Wart Blocks** are solid fungus-cap materials. Their names do not make them interchangeable with the [Nether Wart brewing crop](NetherWart.md). Start with a protected Nylium patch, use Bone Meal on the correct target, and choose the harvesting tool from the table below. [Nylium tag] · [Wart-block tag] · [Nylium vegetation callbacks]

This guide covers seven placed-block IDs and their matching items. [Nether Fungi](NetherFungi.md) owns small-fungus placement and huge-growth details; [Tree Logs and Roots](TreeLogsAndRoots.md#crimson-timber) owns stems/hyphae, and [Vines](Vines.md#weeping-and-twisting-vines) owns the resulting vine columns.

## Variants and harvesting

The table describes ordinary Survival mining. **All pickaxe materials, including Wooden, can satisfy Nylium's tool tier**, provided the tool is not broken. Silk Touch on a tool that fails the pickaxe drop gate does not recover Nylium. Both Wart Blocks are hoe-efficient but also drop by hand. [Pickaxe mining tag] · [Wooden-tool exclusions] · [Tool drop-rule construction] · [Pickaxe tool assignment] · [Broken-tool drop guard] · [Player correct-tool gate] · [Active mining dispatch] · [Hoe mining tag]

| Block and matching item ID | Mining and normal loot | Registry and full loot table |
| --- | --- | --- |
| <span id="crimson-nylium"></span>[Crimson Nylium](../items/CrimsonNylium.md) (`minecraft:crimson_nylium`) | **Pickaxe required:** 1 Crimson Nylium with Silk Touch; otherwise 1 Netherrack | [Registry crimson_nylium] · [Item crimson_nylium] · [Loot crimson_nylium] |
| <span id="warped-nylium"></span>[Warped Nylium](../items/WarpedNylium.md) (`minecraft:warped_nylium`) | **Pickaxe required:** 1 Warped Nylium with Silk Touch; otherwise 1 Netherrack | [Registry warped_nylium] · [Item warped_nylium] · [Loot warped_nylium] |
| <span id="crimson-roots"></span>[Crimson Roots](../items/CrimsonRoots.md) (`minecraft:crimson_roots`) | Hand or tool: **1 Crimson Roots** | [Registry crimson_roots] · [Item crimson_roots] · [Loot crimson_roots] |
| <span id="warped-roots"></span>[Warped Roots](../items/WarpedRoots.md) (`minecraft:warped_roots`) | Hand or tool: **1 Warped Roots** | [Registry warped_roots] · [Item warped_roots] · [Loot warped_roots] |
| <span id="nether-sprouts"></span>[Nether Sprouts](../items/NetherSprouts.md) (`minecraft:nether_sprouts`) | **Shears:** 1 Nether Sprouts; other tools give none | [Registry nether_sprouts] · [Item nether_sprouts] · [Loot nether_sprouts] |
| <span id="nether-wart-block"></span>[Nether Wart Block](../items/NetherWartBlock.md) (`minecraft:nether_wart_block`) | Hand or tool: **1 Nether Wart Block**; hoe is faster | [Registry nether_wart_block] · [Item nether_wart_block] · [Loot nether_wart_block] |
| <span id="warped-wart-block"></span>[Warped Wart Block](../items/WarpedWartBlock.md) (`minecraft:warped_wart_block`) | Hand or tool: **1 Warped Wart Block**; hoe is faster | [Registry warped_wart_block] · [Item warped_wart_block] · [Loot warped_wart_block] |

Fortune does not increase any of these seven block-loot results. Nether Sprouts use an explicit **Shears item condition**, so Silk Touch on another tool does not recover them. Both Roots return their own item without Shears. Nylium has hardness **0.4**, Roots/Sprouts break instantly, and both Wart Blocks have hardness **1.0**. The linked registrations and loot tables distinguish these speed, tool-gate, and item-drop rules.

## Keeping and renewing Nylium

Nylium is a full block with no plant-style floor-support requirement and no waterlogged state. Its special conversion check concerns the block **above**, rather than the supporting block below. [Registry crimson_nylium] · [Registry warped_nylium] · [Full-block support and shape defaults] · [Nylium cover conversion]

### Covered Nylium becomes Netherrack

On an eligible random tick, Nylium tests the block directly above and the occlusion of their shared face. If that test blocks light at **15 or more**, it converts into **Netherrack**. This does **not** check the surrounding light level or require daylight. A full opaque block above is a practical way to lose the surface; clearing the cover does not automatically turn the Netherrack back. Keep a starter patch uncovered, especially after harvesting a huge fungus. [Nylium cover conversion] · [Cover and face occlusion]

Nylium's random-tick method only performs that cover conversion; it does not naturally spread onto neighboring Netherrack. Use the Bone Meal conversion below. [Nylium cover conversion] · [Netherrack target and renewal]

### Bone Meal on Netherrack

To expand or restore a patch:

1. Place **Netherrack** within one block of existing Crimson or Warped Nylium in each coordinate. The checked neighborhood is a **3 × 3 × 3 cube**, including diagonals and blocks one level above or below.
2. Leave **air above the target Netherrack** as a straightforward valid setup. The exact test accepts a block above whose state propagates skylight downward; it does not check whether actual skylight reaches the location.
3. Use **one Bone Meal on that Netherrack**. The accepted Survival operation converts that one block and consumes the Bone Meal.

With only one Nylium color in the neighborhood, the target becomes that color. With **both colors present**, each has a **50% chance**, regardless of how many neighbors of each color are present. The nearby starter Nylium is retained. These callbacks contain no Nether-dimension or ambient-light requirement. [Netherrack target and renewal] · [Nylium tag] · [Bone Meal dispatch and consumption]

This is a conversion of existing Netherrack, not a crafting recipe for Nylium. Use a Silk Touch pickaxe if you want to move the resulting Nylium as an item.

## Bone Meal on Nylium

Use Bone Meal **on Nylium with air immediately above it** to generate small vegetation. Targeting the ground and targeting a small fungus are different operations. [Nether Fungi](NetherFungi.md#bone-meal-and-huge-growth) explains the latter. An accepted ground application consumes one Bone Meal even if individual feature attempts find no useful placement. [Nylium vegetation callbacks] · [Bone Meal dispatch and consumption]

| Target ground | Primary vegetation choices and relative weights | Additional route |
| --- | --- | --- |
| Crimson Nylium | Crimson Roots **87**, Crimson Fungus **11**, Warped Fungus **1** | No additional Sprouts or Twisting Vine call in this branch |
| Warped Nylium | Warped Roots **85**, Crimson Roots **1**, Warped Fungus **13**, Crimson Fungus **1** | A separate **Nether Sprouts** feature, followed by a **1-in-8 chance to attempt Twisting Vines** |

These are **provider weights, not promised harvest percentages**. The Crimson weights total **99**. Both colors can generate either fungus species, and Warped Nylium can occasionally generate Crimson Roots. The separate Sprouts and vine attempts are subject to their own placement conditions; the 1-in-8 roll is not a guarantee that a vine appears. [Nylium vegetation callbacks] · [Configuration crimson_forest_vegetation_bonemeal] · [Configuration warped_forest_vegetation_bonemeal] · [Configuration nether_sprouts_bonemeal] · [Configuration twisting_vines_bonemeal]

Each primary vegetation pass, and the separate Sprouts pass, makes **nine candidate attempts** at the starting height with horizontal offsets from **−2 to +2**. A chosen cell must be empty, and the chosen plant must survive on the block beneath it. Attempts can hit the same cell, and the first vegetation pass can occupy cells before the Sprouts or vine pass reaches them. Clear unwanted small plants to make room for later uses. This describes placement attempts, not nine guaranteed items. [Vegetation placement loop] · [Weighted state selection]

The active callback resolves those named configured features and calls their implementations. For the later growth and harvesting of a generated Twisting Vine, use [Vines](Vines.md#weeping-and-twisting-vines). For using a generated small fungus to produce cap blocks, use [Nether Fungi](NetherFungi.md). [Active vegetation feature keys] · [Vegetation and fungus feature registration] · [Configured feature dispatch]

## Roots and Nether Sprouts

All three small plants accept **either Nylium, Soul Soil, Farmland, or a block in the dirt tag**. The current dirt tag contains Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. **Soul Sand and Netherrack are not accepted** by these checked support rules. Their support test has no brightness or dimension requirement. [Root support and behavior] · [Sprout support and behavior] · [Shared vegetation survival] · [Nylium tag] · [Dirt support tag] · [Block-item placement checks]

Roots and Sprouts have **no maturity stages, natural growth ticks, or direct Bone Meal growth target** in their active classes and registrations. They do not turn into a huge fungus by waiting or by fertilizing the small plant itself. To produce more, use the [Nylium ground route](#bone-meal-on-nylium). [Root support and behavior] · [Sprout support and behavior] · [Registry crimson_roots] · [Registry warped_roots] · [Registry nether_sprouts] · [Bone Meal dispatch and consumption]

They have no entity collision and no waterlogged state. Removing their support breaks them; incoming water can replace them. Those no-tool removal paths recover Roots through their ordinary loot, but **Nether Sprouts are lost because no Shears are supplied**. Shear Sprouts before clearing a plot or washing it with water. [Shared vegetation survival] · [Unsupported-block removal] · [Fluid admission] · [Incoming fluid replacement] · [Water harvest drops] · [Loot crimson_roots] · [Loot warped_roots] · [Loot nether_sprouts]

Crimson and Warped Roots have registered [Flower Pot](FlowerPot.md#supported-plants) forms. Nether Sprouts do not. Potted removal, support, and loot belong to that guide. [Potted root registrations]

## Nether and Warped Wart Blocks

These are **full solid building blocks** with no facing, waterlogging, or stem-support requirement. They do not use leaf-distance or leaf-decay behavior: removing a fungus stem does not make its remaining Wart Blocks decay through a leaf callback. Their ordinary loot is the same full block, without a Silk Touch requirement or Fortune increase. [Registry nether_wart_block] · [Registry warped_wart_block] · [Full-block support and shape defaults] · [Default random-tick eligibility] · [Loot nether_wart_block] · [Loot warped_wart_block]

### Crafting is not reversible storage

**Nine loose [Nether Wart](../items/NetherWart.md)** craft into **one Nether Wart Block**. The checked recipe is shapeless but uses nine ingredient slots, so it needs a Crafting Table. There is **no reverse recipe** turning that block back into loose Nether Wart in the bundled recipe set. Do not pack your last crop supply expecting to unpack it later. [Nether Wart Block recipe] · [Shapeless recipe execution]

No producing recipe was found for Warped Wart Block, either Nylium, either Roots, or Nether Sprouts. No bundled recipe uses any of these seven full item IDs as an ingredient. In particular, a Warped Wart Block is not a substitute for the plantable `minecraft:nether_wart` ingredient.

### Renewable cap blocks

Grow a small fungus on its matching Nylium using the [Nether Fungi guide](NetherFungi.md#bone-meal-and-huge-growth), then harvest the cap:

- The Crimson planted feature uses **Nether Wart Block** as its cap material
- The Warped planted feature uses **Warped Wart Block** as its cap material

The cap's random placement and obstructions affect the result, so there is no fixed block yield per Bone Meal. Both block types return themselves when mined; neither gives the loose brewing crop. The existing fungus guide owns its growth chance, clearance cautions, and recovery behavior. [Configuration crimson_fungus_planted] · [Configuration warped_fungus_planted] · [Planted fungus growth callback] · [Huge-fungus cap placement] · [Loot nether_wart_block] · [Loot warped_wart_block]

## Checked natural sources

The Normal preset's Nether generator selects the bundled Nether noise settings. Their active surface rules select **Crimson Nylium in Crimson Forest** and **Warped Nylium in Warped Forest**, with corresponding Wart Block patches in another branch. These floor rules also apply noise and height conditions; they do not replace every block in either biome. The configured surface rule is passed through the current surface evaluator and its block-writing path. [Normal Nether settings selection] · [Nether surface materials] · [Active noise-setting surface dispatch] · [Active surface-rule setup] · [Compiled material rule] · [Surface block writes]

The two biomes also select concrete vegetation and huge-fungus features:

| Biome | Selected features relevant here | Evidence |
| --- | --- | --- |
| Crimson Forest | Crimson-root vegetation and huge Crimson fungi with Nether Wart Block caps | [Crimson Forest biome] · [Placed feature crimson_forest_vegetation] · [Configuration crimson_forest_vegetation] · [Placed feature crimson_fungi] · [Configuration crimson_fungus] |
| Warped Forest | Warped-root vegetation, a separate Nether Sprouts feature, and huge Warped fungi with Warped Wart Block caps | [Warped Forest biome] · [Placed feature warped_forest_vegetation] · [Configuration warped_forest_vegetation] · [Placed feature nether_sprouts] · [Configuration nether_sprouts] · [Placed feature warped_fungi] · [Configuration warped_fungus] |

The biome-decoration loop runs the selected placed features, and each still needs suitable space and substrate. These are verified acquisition examples, not a complete structure/trade list or a promise that every patch contains every plant. Bring Shears for Sprouts and a Silk Touch pickaxe for Nylium; a hoe speeds cap-block collection. [Active biome decoration] · [Vegetation placement loop] · [Huge-fungus base and placement]

## Composting

Both Roots have a **65%** compost-advance value, Nether Sprouts **50%**, and both Wart Blocks **85%**. These apply to adding an item to a partially filled [Composter](Composter.md); the first accepted item in an empty composter advances it automatically. Composting consumes the material and is not a route back to loose Nether Wart. [Root compost values] · [Sprout compost value] · [Wart-block compost values] · [Compost chance and first layer]

Related: [Nether Fungi](NetherFungi.md) · [Nether Wart crop](NetherWart.md) · [Vines](Vines.md) · [Tree Logs and Roots](TreeLogsAndRoots.md) · [Bone Meal](../items/BoneMeal.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`. Checked all seven block/item registrations and full loot tables, recursive mining/support tags, the sole producing recipe and lack of reverse recipes, active cover/Netherrack-conversion/vegetation callbacks, six natural/Bone Meal vegetation configurations, planted and natural fungus cap choices, the current surface evaluator and two biome feature chains, and compost values. Existing crop, fungus growth, vine, pot, and timber details retain their canonical owners. No in-game mining, support, water, Bone Meal, crafting, or world-generation test was run. Data packs can change tags, loot, recipes, and features.

[Registry crimson_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5519-L5529
[Loot crimson_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/crimson_nylium.json
[Item crimson_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L117
[Registry warped_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5474
[Loot warped_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/warped_nylium.json
[Item warped_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L118
[Registry crimson_roots]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5570-L5581
[Loot crimson_roots]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/crimson_roots.json
[Item crimson_roots]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L364
[Registry warped_roots]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5483-L5494
[Loot warped_roots]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/warped_roots.json
[Item warped_roots]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L365
[Registry nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5495-L5506
[Loot nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/nether_sprouts.json
[Item nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L366
[Registry nether_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L4332-L4334
[Loot nether_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/nether_wart_block.json
[Item nether_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L766
[Registry warped_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5480-L5482
[Loot warped_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/warped_wart_block.json
[Item warped_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L767
[Nylium tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/nylium.json
[Wart-block tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/wart_blocks.json
[Dirt support tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/dirt.json
[Pickaxe mining tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Hoe mining tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[Wooden-tool exclusions]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[Tool drop-rule construction]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[Pickaxe tool assignment]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Item.java#L435-L436
[Broken-tool drop guard]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L588
[Player correct-tool gate]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Active mining dispatch]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[Nylium cover conversion]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L32-L43
[Cover and face occlusion]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L50-L58
[Nylium vegetation callbacks]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L46-L81
[Netherrack target and renewal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NetherrackBlock.java#L25-L71
[Bone Meal dispatch and consumption]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[Root support and behavior]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/RootsBlock.java#L12-L33
[Sprout support and behavior]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NetherSproutsBlock.java#L12-L33
[Shared vegetation survival]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Block-item placement checks]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/BlockItem.java#L111-L138
[Unsupported-block removal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Block.java#L213-L225
[Fluid admission]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Incoming fluid replacement]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L276
[Water harvest drops]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Full-block support and shape defaults]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[Default random-tick eligibility]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L391-L392
[Vegetation placement loop]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L17-L48
[Weighted state selection]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/stateproviders/WeightedStateProvider.java#L33-L36
[Active vegetation feature keys]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/data/worldgen/features/NetherFeatures.java#L33-L40
[Vegetation and fungus feature registration]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L122-L127
[Configured feature dispatch]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
[Configuration crimson_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation.json
[Configuration warped_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation.json
[Configuration nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/nether_sprouts.json
[Configuration crimson_forest_vegetation_bonemeal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation_bonemeal.json
[Configuration warped_forest_vegetation_bonemeal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation_bonemeal.json
[Configuration nether_sprouts_bonemeal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/nether_sprouts_bonemeal.json
[Configuration twisting_vines_bonemeal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/twisting_vines_bonemeal.json
[Configuration crimson_fungus]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus.json
[Configuration warped_fungus]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus.json
[Configuration crimson_fungus_planted]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[Configuration warped_fungus_planted]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[Crimson Forest biome]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json
[Warped Forest biome]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[Placed feature crimson_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/crimson_forest_vegetation.json
[Placed feature warped_forest_vegetation]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/warped_forest_vegetation.json
[Placed feature nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/nether_sprouts.json
[Placed feature crimson_fungi]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/crimson_fungi.json
[Placed feature warped_fungi]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/warped_fungi.json
[Nether surface materials]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json#L404-L560
[Normal Nether settings selection]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Active noise-setting surface dispatch]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L241-L262
[Active surface-rule setup]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L101-L119
[Compiled material rule]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java#L104-L116
[Surface block writes]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java#L220-L230
[Active biome decoration]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Planted fungus growth callback]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L61-L80
[Huge-fungus cap placement]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L149-L176
[Huge-fungus base and placement]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L64
[Nether Wart Block recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/nether_wart_block.json
[Shapeless recipe execution]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L91
[Potted root registrations]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5800-L5805
[Root compost values]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L142-L144
[Sprout compost value]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L121
[Wart-block compost values]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L175-L176
[Compost chance and first layer]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
