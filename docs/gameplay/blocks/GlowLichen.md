# Glow Lichen

**Glow Lichen** (`minecraft:glow_lichen`) is a thin surface plant and **light-level-7** source. Collect it with Shears, attach it to suitable block faces, then use Bone Meal to spread it. It has its own block item and no tip/body pair. Unlike the [Vines family](Vines.md), it is **not climbable**. [Glow Lichen registration] · [Vine and Lichen items] · [Lichen light and Bone Meal] · [Climbable block tag] · [Active climbing check]

## Placement and support

Lichen can attach to **walls, ceilings, and floors**, with up to **six occupied faces in one cell**. Its attachment test accepts a full face in either the neighboring block's support shape or collision shape; it is not limited to the rock list used by natural generation. Use a Glow Lichen item to add a supported vacant face to an existing patch, consuming one item in ordinary Survival placement. [Lichen faces and water state] · [Lichen placement and added faces] · [Full-face attachment checks] · [Block-item placement and consumption]

Each face requires its own support. A neighbor update removes a face when that support is no longer valid; losing the last face removes the block. A surviving face does not preserve unsupported faces elsewhere in the same cell. This block has no entity collision and hardness **0.2**. [Lichen faces and water state] · [Last-face removal] · [Glow Lichen registration]

## Harvesting

**Mine with Shears.** The ordinary loot count is **one Glow Lichen item per occupied face**, so a block with one face yields one and a block with six faces yields six. The table starts from one item, adds one for each true face, then subtracts one; this counts attached faces rather than multiplying by Fortune. [Loot glow_lichen] · [Loot count addition]

Bare hands, axes, and Silk Touch tools that are not Shears fail the explicit Shears-item loot condition. Although Lichen has no correct-tool gate and is in the axe-efficient and sword-efficient tags, those speed tags do not grant its item. Removing its support likewise does not pass a Shears tool into the destruction loot. [Correct-tool drop gate] · [Axe-efficient blocks] · [Sword-efficient blocks] · [Shears speed and wear] · [Detached-block loot uses an empty tool] · [Loot glow_lichen]

Harvest before removing the surrounding blocks if you want the Lichen items. Partial support loss removes the unsupported face from the state without dropping a face item; losing the last face invokes the ordinary no-tool removal route. [Lichen faces and water state] · [Last-face removal] · [Neighbor-removal dispatch]

## Bone Meal spreading

Glow Lichen **does not spread through its own natural random ticks** in the checked registration. Bone Meal is accepted only if the spreader finds a valid destination from one of the current faces. Its success check then returns true and the spread search chooses one valid placement; under ordinary Survival use, one accepted operation consumes one Bone Meal. The same callbacks apply to dry and waterlogged Lichen and do not test light level. [Glow Lichen registration] · [Lichen light and Bone Meal] · [One-face spread search] · [Bone Meal use and consumption]

The spread can add a face:

- **In the same cell**, against another suitable adjacent surface
- **Along the same plane**, into an adjacent cell with supporting surface
- **Around an edge**, using the spreader's corner placement

For a given direction pair, it checks those possibilities in that order; it shuffles which starting face and directions it tries. It stops after the first successful new face, rather than filling every available surface with one use. [One-face spread search] · [Spread target and orientation checks] · [Spread geometry]

A destination must be **air, existing Glow Lichen, or a source-Water block**, and the proposed face must be vacant and supported. Ordinary flowing-water cells and unrelated plants do not pass that spread-destination test. Leave suitable neighboring surfaces and open cells when building a propagation area. [Spread target and orientation checks] · [Lichen placement and added faces]

## Water and light

Glow Lichen can be **waterlogged**. Placing it into source Water preserves that water in its block state; a Water Bucket can fill a dry patch, and an empty Bucket can recover that water while leaving supported Lichen. The callback specifically accepts the source-Water fluid type. Waterlogging does not grant support on an otherwise unsuitable surface. [Lichen placement and added faces] · [Lichen faces and water state] · [Water bucket insertion and pickup]

Do not apply the Vines guide's water-washing rule to this block: incoming-fluid handling recognizes Lichen's water-container interface. Ordinary flowing Water is not accepted as a new waterlogging fluid by that interface; the supported source-Water route fills it. [Lichen water-container implementation] · [Water bucket insertion and pickup] · [Fluid admission and replacement] · [Incoming fluid replacement]

Any state with at least one attached face emits **light level 7**, including a waterlogged state. Extra faces do not add brightness. It has no aging or fuel-consumption timer in the checked class; its continued placement depends on retaining valid faces. [Glow Lichen registration] · [Lichen light and Bone Meal] · [Lichen faces and water state]

## A checked natural source

The **Plains** biome includes the `glow_lichen` placed feature. Its height and surface-relative filters seek underground placements, and its configured initial attachments allow **walls and ceilings**, on **Stone, Andesite, Diorite, Granite, Dripstone Block, Calcite, Tuff, or Deepslate**. Those are natural-generation restrictions; player placement and Bone Meal use the broader face-support rules above. [Lichen source biome] · [Lichen placed feature] · [Lichen configured feature] · [Natural lichen placement]

The feature can make an additional spread attempt after its first placement. It still needs valid space and support, and its attempts are not a guaranteed number of collectible patches. This is one verified biome example, not an exhaustive world-distribution claim. [Natural lichen placement] · [Active biome decoration]

No producing crafting or smelting recipe for Glow Lichen was found in the checked bundled recipe set. Acquire a starter with Shears, then propagate it with Bone Meal.

Related: [Glow Lichen item](../items/GlowLichen.md) · [Vines and Glow Berries](Vines.md) · [Shears](../items/Shears.md) · [Bone Meal](../items/BoneMeal.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `88d85ee594bc770fa362129690eadb127272dc20`. Checked the block/item registration, complete face-count loot table, recipe absence, tool and climbing tags, six-face placement/removal, active Bone Meal spread geometry, source-water bucket behavior, light function, and the listed biome/placed/configured-feature route. No in-game placement, climbing, growth, harvesting, crafting, eating, or generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Loot glow_lichen]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/glow_lichen.json
[Glow Lichen registration]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L2391-L2403
[Vine and Lichen items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L559-L560
[Bone Meal use and consumption]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Shears speed and wear]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L58
[Climbable block tag]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[Active climbing check]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1652-L1667
[Fluid admission and replacement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Incoming fluid replacement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L276
[Detached-block loot uses an empty tool]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/Level.java#L263-L278
[Correct-tool drop gate]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Axe-efficient blocks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[Sword-efficient blocks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/sword_efficient.json
[Full-face attachment checks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L251-L259
[Lichen faces and water state]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L109-L172
[Lichen placement and added faces]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L175-L216
[Last-face removal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L262-L264
[Water bucket insertion and pickup]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[Lichen light and Bone Meal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GlowLichenBlock.java#L14-L53
[One-face spread search]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceSpreader.java#L13-L43
[Spread target and orientation checks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceSpreader.java#L79-L130
[Spread geometry]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceSpreader.java#L177-L197
[Loot count addition]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/storage/loot/functions/SetItemCountFunction.java#L45-L49
[Lichen source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/plains.json
[Lichen placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/glow_lichen.json
[Lichen configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/glow_lichen.json
[Natural lichen placement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/MultifaceGrowthFeature.java#L55-L89
[Active biome decoration]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Block-item placement and consumption]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[Neighbor-removal dispatch]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Block.java#L213-L225
[Lichen water-container implementation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L31-L36
