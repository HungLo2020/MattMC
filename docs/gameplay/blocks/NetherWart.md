# Nether Wart crop

**Nether Wart** (`minecraft:nether_wart`) grows on **Soul Sand** and supplies the [brewing ingredient](../items/NetherWart.md). Bring home both wart and its soil to establish a bed near your base. The crop is separate from the solid [Nether Wart Block](NetherGroundAndVegetation.md#nether-and-warped-wart-blocks), which cannot replace loose wart for planting. [Crop and item][crop] · [Item registration][item]

## Finding and planting

There are several checked starter sources:

- **Nether fortress stalk rooms** place Nether Wart on Soul Sand. Those generated crops start at age 0; their age when you arrive depends on subsequent growth. The room is a possible fortress piece, not a feature of every fortress. [Room selection][fortress-selection] · [Beds][fortress-beds] · [Default age][catalog] · [Default state][templates]
- **Fortress chests** can select **3–7 Nether Wart** from their loot table. That is the amount for a selected wart entry, not a guaranteed chest reward. [Chest assignment][fortress-chest] · [Chest loot][chest-loot]
- **Bastion housing-unit centers** have templates containing mature wart on Soul Sand. The housing start connects to the center pool; this does not make every kind of Bastion a wart source. [Bastion start][bastion-start] · [Housing connections][housing-base] · [Center pool][housing-pool] · [Center templates][center-0] · [Center 1][center-1] · [Center 2][center-2] · [Housing processor][housing-processor]

Use one loose Nether Wart on the top of **Soul Sand** to plant a crop at **age 0**, consuming one item in ordinary Survival use. Soul Soil, Farmland and Netherrack fail the crop's support check. Removing the Soul Sand removes the unsupported crop on its neighbor update. Follow [Soul Sand acquisition and movement](SoulSandSoilAndMagma.md) for obtaining the bed and handling its slowdown. [Planting item][item] · [Placement and consumption][placement] · [Exact support][crop] · [Support-loss behavior][vegetation] · [Default state][catalog] · [Default state][templates]

## Growth

Nether Wart has **ages 0–3**; **age 3 is mature**. Each eligible random tick has a **1-in-10 chance** to increase the age by one. Mature plants stop requesting random ticks. The actual growth check has no light, hydration, biome, dimension or neighboring-crop condition. A supported bed can therefore grow outside the Nether, including indoors or underground, without irrigation or growth lighting. [Growth callback][crop] · [Native ticking policy][policy] · [Tick dispatch][ticks]

A **continuous Soul Sand bed** is a valid layout: Nether Wart has no Wheat-style same-crop spacing penalty. Leave each plant its own block cell and choose bed width for convenient harvesting and replanting. Ordinary walking through the non-colliding crop does not invoke a Farmland-style trampling conversion on Soul Sand. Soul Sand's movement penalty still applies; a walkway beside the bed makes access easier. [Growth and support][crop] · [Crop physics][physics] · [Soul Sand behavior][sand]

There are **four ages but only three bundled appearances**: age 0 uses the small model, ages **1 and 2 share the middle model**, and age 3 uses the mature model. A plant can therefore advance once without visibly changing. Resource packs may change these appearances. [Age models][models]

**Bone Meal does not grow Nether Wart.** The crop does not implement the growth interface that Bone Meal calls. Light changes and rearranging neighbors likewise do not accelerate its checked growth roll. Keep the farm in terrain receiving random ticks; the 1-in-10 chance is per eligible crop tick, not per game tick or a promised time to maturity. See [ticks and chunk activity](../mechanics/TicksAndChunkActivity.md). [Crop implementation][crop] · [Bone Meal dispatch][bonemeal]

## Harvest

**Break the crop, then replant.** Nether Wart does not inherit [Wheat's empty-hand reset or hoe area harvest](Wheat.md#mattmc-harvesting-controls). Using a hoe on it does not grant those interactions. Its hardness and blast resistance are **0**, with no correct-tool requirement, so ordinary hand harvesting is sufficient. The crop has no collision, emits no light, and a piston destroys it rather than moving it intact. [Inheritance and behavior][crop] · [Ordinary interaction][base-use] · [Native definition][catalog] · [Physical profile][physics] · [Physical defaults][physical-defaults] · [Light and fluid][intrinsic] · [Harvest gate][gate] · [Piston handling][piston]

| Crop state / harvesting tool | Ordinary Nether Wart drop |
| --- | --- |
| Ages 0–2, with or without Fortune | **1** |
| Age 3, no Fortune | **2–4** |
| Age 3, Fortune I | **2–5** |
| Age 3, Fortune II | **2–6** |
| Age 3, Fortune III | **2–7** |

Mature loot rolls a base **2–4**, then adds a random **0 through the Fortune level** on the harvesting tool. The ranges are possible totals, not equally likely totals or guaranteed maximum yields. The active mining path passes the held tool to the drop calculation. Silk Touch has no alternate drop branch and does not preserve the planted age. Explosion decay can reduce the result, and ordinary block-drop rules still apply. [Loot][loot] · [Fortune formula][fortune] · [Mining and tool context][mining] · [Drop dispatch][drops]

Reserve **one wart per crop you want to replant** before brewing or crafting with the rest. Breaking an immature plant only returns the planting item, so waiting for age 3 supplies the normal surplus. For brewing and other uses, follow the [Nether Wart item guide](../items/NetherWart.md).

## Related pages

- [Nether Wart item and brewing](../items/NetherWart.md)
- [Brewing](../brewing/Brewing.md)
- [Soul Sand and Soul Soil](SoulSandSoilAndMagma.md)
- [Wheat](Wheat.md), which has different harvesting behavior
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Java support, growth, item placement and loot callers were checked against native defaults, physical definitions, intrinsic values and tick policy, plus bundled loot, blockstate models and fortress/Bastion data. The Bastion claim follows the active structure start, jigsaw pools and decoded template blocks, rather than the presence of an unused asset alone. [Structure configuration][bastion-structure] · [Jigsaw generation][jigsaw] · [Template placement][pool-placement] · [Native property/state bridge][bridge]

No in-game generation, planting, growth, harvesting, brewing or piston test was run. These are source-defined routes and constraints, not guarantees about a particular world, chest, farm rate or modified data pack.

[crop]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/NetherWartBlock.java#L19-L70
[item]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L1765
[fortress-selection]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L38-L48
[fortress-beds]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L989-L992
[catalog]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/catalog.rs#L414
[templates]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/templates.rs#L77
[fortress-chest]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L705-L712
[chest-loot]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/loot_table/chests/nether_bridge.json#L68-L85
[bastion-start]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/worldgen/template_pool/bastion/starts.json
[housing-base]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/structure/bastion/units/air_base.nbt
[housing-pool]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/worldgen/template_pool/bastion/units/center_pieces.json
[center-0]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/structure/bastion/units/center_pieces/center_0.nbt
[center-1]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/structure/bastion/units/center_pieces/center_1.nbt
[center-2]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/structure/bastion/units/center_pieces/center_2.nbt
[housing-processor]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/worldgen/processor_list/housing.json
[placement]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/BlockItem.java#L49-L143
[vegetation]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L28-L49
[policy]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/policy.rs#L105
[ticks]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerLevel.java#L486-L511
[physics]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/physics.rs#L116-L120
[physical-defaults]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/physics.rs#L43-L50
[sand]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/SoulSandBlock.java
[models]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/assets/minecraft/blockstates/nether_wart.json
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/BoneMealItem.java#L66-L83
[base-use]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L205-L213
[intrinsic]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/intrinsic/declarations.rs#L26
[gate]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[piston]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L37-L49
[loot]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/loot_table/blocks/nether_wart.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L166
[mining]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L297
[drops]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/Block.java#L388-L425
[bastion-structure]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/worldgen/structure/bastion_remnant.json
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L134-L156
[pool-placement]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L138-L180
[bridge]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/NativeBlockDefinitions.java#L44-L58
