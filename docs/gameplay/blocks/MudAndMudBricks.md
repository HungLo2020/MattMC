# Mud, Packed Mud and Mud Bricks

Collect **Mud** for low-surface terrain, mix it with Wheat for **Packed Mud**, then make **Mud Bricks** and matching stairs, slabs and walls. This building chain uses crafting and stonecutting; the separate [Clay and Bricks](ClayAndBricks.md) chain owns Clay production and fired bricks. [Mud registration][mud-reg] · [Packed Mud and Mud Bricks][packed-bricks-reg]

## Registered family and mining

All six blocks have matching placeable items. Names and IDs below distinguish the full Mud Bricks block from its three shapes. [Mud item][mud-item] · [Packed Mud and Mud Bricks items][packed-bricks-items] · [Slab item][slab-item] · [Stair item][stairs-item] · [Wall item][wall-item]

| Block | Exact ID | Efficient tool | Correct tool required for ordinary drops? | Hardness / blast resistance |
| --- | --- | --- | --- | --- |
| [Mud](#mud) | `minecraft:mud` | Shovel | No; hand mining works | 0.5 / 0.5 |
| [Packed Mud](#packed-mud) | `minecraft:packed_mud` | Pickaxe | No; hand mining works | 1 / 3 |
| [Mud Bricks](#mud-bricks) | `minecraft:mud_bricks` | Pickaxe | Yes; an unbroken Wooden Pickaxe is sufficient | 1.5 / 3 |
| [Mud Brick Stairs](#mud-brick-stairs) | `minecraft:mud_brick_stairs` | Pickaxe | Same as Mud Bricks | 1.5 / 3 |
| [Mud Brick Slab](#mud-brick-slab) | `minecraft:mud_brick_slab` | Pickaxe | Same as Mud Bricks | 1.5 / 3 |
| [Mud Brick Wall](#mud-brick-wall) | `minecraft:mud_brick_wall` | Pickaxe | Same as Mud Bricks | 1.5 / 3 |

Packed Mud's pickaxe tag controls mining speed, while its registration retains Dirt's lack of a correct-tool drop requirement. Mud Bricks and all three shapes require a correct tool; hands, unsuitable tools and broken pickaxes do not collect them. None needs a tier above Wood under the bundled tags. These properties are not measured breaking times. [Mud properties][mud-reg] · [Dirt properties][dirt-reg] · [Packed and brick properties][packed-bricks-reg] · [Stairs][stairs-reg] · [Stair inheritance][stairs-copy] · [Slab][slab-reg] · [Wall][wall-reg] · [Property copying][copy] · [Strength values][strength] · [Shovel tag][shovel-tag] · [Pickaxe tag][pickaxe-tag] · [Wood restrictions][wood-tier] · [Stone tier][stone-tier] · [Iron tier][iron-tier] · [Diamond tier][diamond-tier] · [Tool rules][tool] · [Player gate][gate] · [Broken-tool check][broken]

With the required tool where applicable, ordinary mining returns **one matching item**. A double Mud Brick Slab returns **two slabs**, never a Mud Bricks block. Silk Touch is unnecessary and Fortune does not increase these yields. All six loot tables account for explosions: the slab uses explosion decay and the others require explosion survival, so blast recovery is not guaranteed. Ordinary player drops also depend on block drops being enabled. [Mud loot][loot-mud] · [Packed Mud loot][loot-packed_mud] · [Brick loot][loot-mud_bricks] · [Stair loot][loot-mud_brick_stairs] · [Slab loot][loot-mud_brick_slab] · [Wall loot][loot-mud_brick_wall] · [Harvest dispatch][harvest] · [Block-drops rule][drop-rule]

## Mud

Mud has a **14/16-block-high collision surface**, while its visual and support shapes are full cubes. Standing entities therefore sit lower than on Dirt or Packed Mud, but the block still provides full-block support faces. It remains in place when the block underneath is removed. Mud has no waterlogged state; the name does not mean it contains bucket-recoverable water. [Mud shape and support][mud-shape] · [Default survival][default-shape] · [Empty fluid state][default-fluid]

Mud is in the **dirt block tag**, unlike Packed Mud and Mud Bricks. This makes it eligible for plants whose support rules accept that tag; follow [sapling and Azalea planting](SaplingsAndAzaleas.md) or the relevant plant guide. It does not replace [Farmland](Farmland.md) for [Wheat](Wheat.md). [Dirt tag][dirt-tag] · [Vegetation support][plant-support] · [Crop support][crop-support]

For an extra root texture, use Mud in the [Muddy Mangrove Roots recipe](TreeLogsAndRoots.md#muddy-mangrove-roots). That guide owns the root block's recipe, axis and harvesting behavior.

## Packed Mud

Packed Mud is a full-cube building block and the ingredient for Mud Bricks. Use the [family recipe table](#crafting-and-stonecutting) to combine Mud with harvested [Wheat](Wheat.md). Packed Mud does not turn back into Mud when wet, and the checked dripstone conversion recognizes **ordinary Mud only**. It has no waterlogged state or ongoing support requirement. [Registration][packed-bricks-reg] · [Default shape and support][default-shape] · [Default fluid][default-fluid] · [Dripstone source check][drip-mud]

## Mud Bricks

Mud Bricks provide the full building block and the sole stonecutting input for this family. They remain full cubes without support and have no waterlogged state. Use them for the three shapes below; there is no bundled reverse recipe returning Packed Mud, Mud or Wheat. [Registration][packed-bricks-reg] · [Default shape and support][default-shape] · [Default fluid][default-fluid] · [Crafting and cutting recipes](#crafting-and-stonecutting)

## Crafting and stonecutting

| Result | Exact crafting input and layout | Output | Stonecutter alternative |
| --- | --- | ---: | --- |
| Packed Mud | 1 Mud + 1 Wheat, anywhere in the grid | 1 | None |
| Mud Bricks | 4 Packed Mud in a 2 × 2 square | 4 | None |
| Mud Brick Slab | 3 Mud Bricks in one horizontal row | 6 | 1 Mud Bricks → 2 slabs |
| Mud Brick Stairs | 6 Mud Bricks in a three-row stair pattern: one, two, then three | 4 | 1 Mud Bricks → 1 stair |
| Mud Brick Wall | 6 Mud Bricks in two full rows of three | 6 | 1 Mud Bricks → 1 wall |

[Packed Mud recipe][recipe-packed_mud] · [Mud Bricks recipe][recipe-mud_bricks] · [Slab recipe][recipe-mud_brick_slab] · [Stair recipe][recipe-mud_brick_stairs] · [Wall recipe][recipe-mud_brick_wall] · [Slab cutting][cut-slab] · [Stair cutting][cut-stairs] · [Wall cutting][cut-wall]

The first two recipes fit the personal crafting grid; the three shaped-block recipes need a Crafting Table. Every ingredient names the exact item, so Clay Bricks, Dirt, Wheat Seeds and Muddy Mangrove Roots do not substitute. **Stonecutting saves material for stairs:** six Mud Bricks make six stairs at the cutter, compared with four at the Crafting Table. Slab and wall yields per input are unchanged, and cutting lets you use one input at a time. See [Stonecutter controls](Stonecutter.md#using-the-menu); it needs no fuel. The bundled recipe scan found these eight family-output recipes plus Mud's separate root recipe, with no Mud crafting output or Mud/Packed Mud stonecutting input. [Active recipe loading][recipe-load]

## Mud Brick Stairs

Place stairs upright or upside down according to the clicked face and height. They face in the player's horizontal placement direction and automatically form inner or outer corners beside suitably oriented stairs at the same half-height, including other stair materials. They do not need the block beneath them to remain. [Stair placement and corner rules][stairs-state] · [Default survival][default-shape]

Stairs can hold source water. Placing in a source-water cell sets waterlogged; a Water Bucket fills a dry stair, and an empty bucket recovers its water. Flowing water is not the same placement input. [Stair placement][stairs-state] · [Bucket handling][waterlogging]

## Mud Brick Slab

A single slab occupies the lower or upper half of its cell. Place a second **matching Mud Brick Slab** into the free half to form a double slab. It stays the slab block's double state, which explains the two-slab mining result. These states need no continuing support below them. [Slab placement, replacement and shapes][slab-state] · [Slab loot][loot-mud_brick_slab] · [Default survival][default-shape]

A single slab can be waterlogged through source-water placement or a Water Bucket. Completing a double slab clears waterlogging, and a double slab cannot be filled. An empty bucket can recover water from a waterlogged single slab. [Slab water rules][slab-state] · [Bucket handling][waterlogging]

## Mud Brick Wall

Walls connect to other walls, suitable sturdy block faces, Iron Bars and correctly aligned Fence Gates. The connected arms and center post update with the surrounding blocks; the collision reaches **1.5 blocks high**, including on low-looking arms. They remain in place without ground support. [Wall connections and shapes][wall-state] · [Post and arm updates][wall-shape-update] · [Default survival][default-shape]

Walls are waterloggable, with the same source-water placement and bucket filling/pickup behavior as stairs. Adding water does not convert the wall into another mud-family material. [Wall placement][wall-state] · [Bucket handling][waterlogging]

## Natural supplies

**Mangrove Swamp is a confirmed natural Mud source** in the normal Overworld: that world preset selects Overworld noise settings whose Mangrove surface branches place Mud. The active surface generator evaluates those configured rules. This does not mean every block in the biome is Mud. [World preset][normal-world] · [Mud surface rule][mud-surface] · [Generator call][surface-call] · [Active surface adapter][surface-adapter]

**Trail Ruins can supply the whole family**, although individual ruins contain different pieces. Checked bundled templates contain Mud, Packed Mud, Mud Bricks, stairs, slabs and walls; the house processor can additionally replace Mud Bricks with Packed Mud. The active ruin definition, placement set and connected template pools supply the generation route, rather than merely an unused building file. Eligible biomes are Taiga, Snowy Taiga, Old Growth Pine Taiga, Old Growth Spruce Taiga, Old Growth Birch Forest and Jungle. [Ruin definition][trail-structure] · [Placement set][trail-set] · [Biome tag][trail-biomes] · [Start pool][trail-tower-pool] · [Building pool][trail-building-pool] · [Tower additions][trail-hall-pool] · [Building template][trail-room] · [Hall template][trail-hall] · [Packed Mud substitution][trail-process] · [Template placement and processors][jigsaw-place]

Mining the masonry is separate from brushing nearby suspicious terrain. See [Suspicious Sand and Gravel](SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel) before dismantling archaeology blocks.

## Making a continuing supply

Use a **Water Bottle on the top or side of Dirt, Coarse Dirt or Rooted Dirt** to replace that block with Mud. The action needs no air above the soil; Survival use returns an empty Glass Bottle. Refill it at source water, which this bottle action does not remove. [Soil conversion controls](SoilSandAndGravel.md#hoe-shovel-and-water-bottle-actions) owns the full soil/tool comparison. [Water-bottle callback][potion] · [Accepted soil tag][conversion-tag] · [Bottle exchange][bottle-exchange] · [Refilling][bottle-fill]

A Dispenser loaded with Water Bottles also converts eligible soil directly in front of it and returns Glass Bottles through its remainder handling. It has no player top/side-click restriction. An ordinary bucket of water or a non-Water potion does not run this conversion. [Dispenser Water Bottle branch][dispenser] · [Dispenser controls](DispenserAndDropper.md)

**The family is renewable through replenishable soil and crop production.** One checked chain is [water/lava-generated Stone](Stone.md#water-and-lava), [Moss spreading onto that Stone](MossAndPaleMoss.md#moss-blocks-and-spreading), then [Azalea tree growth on Moss](SaplingsAndAzaleas.md#azalea-and-flowering-azalea), which replaces the ground beneath its trunk with Rooted Dirt. Convert that Rooted Dirt into Mud and farm [Wheat](Wheat.md) for Packed Mud. The growth stages need their own space, starter materials and Bone Meal supply; this is a source-checked production chain, not a timed or tested farm design. Bottling alone still consumes one eligible soil block for each Mud. [Generated Stone][lava-stone] · [Moss replacement tag][moss-ground] · [Stone membership][base-stone] · [Moss growth configuration][moss-growth] · [Azalea growth callback][azalea-growth] · [Rooted Dirt provider][azalea-tree] · [Ground placement][azalea-dirt] · [Forced replacement][forced-dirt]

## Turning Mud into Clay

For Clay instead of masonry, follow the canonical [Mud-to-Clay setup](ClayAndBricks.md#turning-mud-into-clay): put Mud **above a support block with downward Pointed Dripstone beneath it**. A water source and cauldron are unnecessary. The successful conversion uses the dripstone's random ticks, so it has no fixed drying timer; ordinary Mud does not dry into Clay simply from being exposed. The route is unavailable in ultra-warm dimensions and does not accept Packed Mud or Mud Bricks. The linked guide owns tip length, merging, waterlogging and timing conditions. [Mud source lookup][drip-mud] · [Conversion callback][drip-convert]

Related: [Mud item](../items/Mud.md) · [Packed Mud item](../items/PackedMud.md) · [Mud Bricks item](../items/MudBricks.md) · [Mud Brick Slab item](../items/MudBrickSlab.md) · [Mud Brick Stairs item](../items/MudBrickStairs.md) · [Mud Brick Wall item](../items/MudBrickWall.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`. Checked all six block/item registrations, inherited properties, complete family loot tables, bundled recipes and expanded item ingredients, mining/tier and other family tags, active interaction and drying callbacks, shapes/waterlogging, Mangrove surface generation and Trail Ruins template wiring. Natural sources listed are checked routes, not a promise about every world preset, structure or trade. No in-game mining, crafting, cutting, generation, bucket, dripstone or farm test was run. Data packs and server rules can alter recipes, loot and tags.

[mud-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L6652-L6662
[packed-bricks-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L2264-L2273
[mud-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L116
[packed-bricks-items]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L542-L543
[slab-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L404
[stairs-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L570
[wall-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L613
[dirt-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L130-L131
[stairs-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L2439
[stairs-copy]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7257
[slab-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L3877-L3886
[wall-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L5281
[copy]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1085
[strength]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[gate]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[loot-mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud.json
[loot-packed_mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/packed_mud.json
[loot-mud_bricks]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud_bricks.json
[loot-mud_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud_brick_stairs.json
[loot-mud_brick_slab]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud_brick_slab.json
[loot-mud_brick_wall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud_brick_wall.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[drop-rule]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[mud-shape]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/MudBlock.java#L13-L48
[default-shape]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[default-fluid]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[dirt-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/dirt.json
[plant-support]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L25
[crop-support]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/CropBlock.java#L53-L56
[drip-mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L544-L556
[recipe-packed_mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/packed_mud.json
[recipe-mud_bricks]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/mud_bricks.json
[recipe-mud_brick_slab]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/mud_brick_slab.json
[recipe-mud_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/mud_brick_stairs.json
[recipe-mud_brick_wall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/mud_brick_wall.json
[cut-slab]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/stonecutting/mud_brick_slab_from_mud_bricks_stonecutting.json
[cut-stairs]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/stonecutting/mud_brick_stairs_from_mud_bricks_stonecutting.json
[cut-wall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/stonecutting/mud_brick_wall_from_mud_bricks_stonecutting.json
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L111
[stairs-state]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
[waterlogging]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L48
[slab-state]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L115
[wall-state]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/WallBlock.java#L54-L154
[wall-shape-update]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/WallBlock.java#L195-L249
[normal-world]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L2-L13
[mud-surface]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L1736-L1750
[surface-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L241-L263
[surface-adapter]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L101-L119
[trail-structure]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/structure/trail_ruins.json
[trail-set]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/structure_set/trail_ruins.json
[trail-biomes]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trail_ruins.json
[trail-tower-pool]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/tower.json
[trail-building-pool]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/buildings.json#L57-L65
[trail-hall-pool]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/tower/additions.json#L21-L29
[trail-room]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/structure/trail_ruins/buildings/large_room_2.nbt
[trail-hall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/structure/trail_ruins/tower/hall_3.nbt
[trail-process]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_houses_archaeology.json#L32-L43
[jigsaw-place]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L138-L180
[potion]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/PotionItem.java#L34-L69
[conversion-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/convertable_to_mud.json
[bottle-exchange]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/ItemUtils.java#L13-L38
[bottle-fill]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/BottleItem.java#L48-L73
[dispenser]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L419-L460
[lava-stone]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L204-L224
[moss-ground]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/moss_replaceable.json
[base-stone]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/base_stone_overworld.json
[moss-growth]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch_bonemeal.json
[azalea-growth]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/AzaleaBlock.java#L36-L54
[azalea-tree]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json
[azalea-dirt]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/BendingTrunkPlacer.java#L45-L57
[forced-dirt]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/TrunkPlacer.java#L66-L75
[drip-convert]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L181-L233
