# Bone Block

**Bone Block** (`minecraft:bone_block`) stores Bone Meal and makes an axis-oriented building block. **Use a pickaxe when recovering a placed block**; breaking it by hand loses the ordinary block drop. Its matching item uses the same ID. [Name][names] · [Block registration][block] · [Item registration][item] · [Harvest gate][gate]

## Packing and unpacking Bone Meal

| Crafting input and arrangement | Result |
| --- | --- |
| 9 Bone Meal filling a Crafting Table's 3 × 3 grid | 1 Bone Block |
| 1 Bone Block anywhere in a crafting grid | 9 Bone Meal |

These recipes are reversible without resource loss. **Bones are not the packing ingredient**: convert them to Bone Meal first if that is your starting material. Mining a placed Bone Block gives the block, not nine loose Bone Meal; unpack it in a crafting grid afterward. [Packing recipe][pack] · [Unpacking recipe][unpack] · [Bone-to-Bone-Meal recipe][bone-meal] · [Block loot][loot]

The loose [Bone Meal](../items/BoneMeal.md) is the fertilizer item. A held or placed Bone Block is not itself the Bone Meal interaction item; unpack it before using plant-growth actions such as [Bamboo fertilizing](Bamboo.md#growth-and-bone-meal) or [Nylium renewal](NetherGroundAndVegetation.md#bone-meal-on-netherrack). [Bone Meal item registration][fertilizer-item] · [Fertilizer dispatch][fertilizer]

## Finding fossils

**Nether fossils in Soul Sand Valley** are a checked natural source. The bundled structure set selects the Nether fossil structure; its biome tag restricts this structure to Soul Sand Valley. The active generator chooses one of 14 fossil templates and places its blocks after finding an eligible site. All 14 checked templates contain Bone Blocks. Structure placement and terrain checks mean this is not a promise of a fossil at every candidate location. [Structure set][nether-set] · [Structure configuration][nether-config] · [Biome tag][nether-biomes] · [Structure dispatch][structure-dispatch] · [Registered type][structure-type] · [Generator][nether-generator] · [Template selection][nether-pieces] · [Example template][nether-template] · [Template placement][template-place] · [Structure placement dispatch][structure-place]

**Desert, Swamp, and Mangrove Swamp** also include the upper/lower Overworld fossil features in their biome data. Their placed features resolve to the coal-overlay and diamond-overlay fossil configurations, which choose bone-bearing spine or skull templates. The fossil generator applies decay and overlay processors and can reject a site with too many empty corners, so the surviving Bone Block count varies. These are checked fossil routes, not an inventory of every structure or imported feature containing bone. [Desert features][desert] · [Swamp features][swamp] · [Mangrove Swamp features][mangrove-swamp] · [Upper placement][upper] · [Lower placement][lower] · [Coal configuration][coal-fossil] · [Diamond configuration][diamond-fossil] · [Registered feature][feature] · [Generator and processors][fossil-generator] · [Example bone template][overworld-template] · [Biome-feature dispatch][biome-dispatch] · [Placed-feature callback][placed-dispatch] · [Configured-feature callback][configured-dispatch]

## Mining and drops

Use an **unbroken pickaxe, including Wooden**. Bone Block is pickaxe-tagged, requires a correct tool, and has no higher-tier restriction in the checked tags. Successful ordinary mining drops **one Bone Block**, with no Silk Touch requirement or Fortune multiplier. The loot table includes an explosion-survival check, so explosion destruction is not a guaranteed recovery method. [Properties][block] · [Pickaxe tag][pickaxe] · [Wooden-tool exclusions][wood-denials] · [Tier tags][stone-tier] · [Iron tier][iron-tier] · [Diamond tier][diamond-tier] · [Tool rules][tool] · [Tool assignment][tool-assignment] · [Broken-tool guard][broken] · [Harvest dispatch][harvest] · [Complete loot][loot]

## Placement and use

Bone Block has **hardness 2 and blast resistance 2**. It is a full cube with an `axis` state: clicking a top or bottom face places its axis vertically; a side face places it horizontally. It has no waterlogged state and does not need a continuing support block. [Registration][block] · [Strength values][strength] · [Pillar placement][pillar] · [Shape and support][support]

A Bone Block under a [Note Block](NoteBlock.md) normally selects the **xylophone** instrument; a supported instrument block above the Note Block takes priority. The placed block has no ordinary fire-consumption entry, and its item is not a default Furnace fuel. There is no bundled Stonecutter or smelting conversion for Bone Block; use the crafting-grid unpacking route above. [Instrument property][block] · [Note Block instrument selection][note] · [Fire table][fire] · [Default fuel table][fuel]

Related: [Bone Block item](../items/BoneBlock.md) · [Bone Meal](../items/BoneMeal.md) · [Resource Storage Blocks](ResourceStorageBlocks.md) · [Mining](../mechanics/Mining.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`. Checked the registry, exact recipes and loot, tool tags and mining gate, pillar state, instrument, fuel/fire tables, both fossil caller chains, and referenced fossil-template palettes. No in-game crafting, mining, note, or world-generation test was run. Data packs can change recipes, tags, loot, and fossil generation.

[names]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/assets/minecraft/lang/en_us.json
[block]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4344-L4353
[item]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L769
[gate]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[pack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bone_block.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bone_meal_from_bone_block.json
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bone_meal.json
[loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/bone_block.json
[fertilizer-item]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java
[fertilizer]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[nether-set]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/structure_set/nether_fossils.json
[nether-config]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/structure/nether_fossil.json
[nether-biomes]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/nether_fossil.json
[structure-dispatch]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L574
[structure-type]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L32
[nether-generator]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFossilStructure.java#L30-L65
[nether-pieces]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFossilPieces.java#L25-L97
[nether-template]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/structure/nether_fossils/fossil_1.nbt
[template-place]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L81-L94
[structure-place]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L98
[desert]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/biome/desert.json
[swamp]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[mangrove-swamp]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json
[upper]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/placed_feature/fossil_upper.json
[lower]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/placed_feature/fossil_lower.json
[coal-fossil]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/configured_feature/fossil_coal.json
[diamond-fossil]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/configured_feature/fossil_diamonds.json
[feature]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L78
[fossil-generator]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/feature/FossilFeature.java#L26-L81
[overworld-template]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/structure/fossil/spine_1.nbt
[biome-dispatch]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[placed-dispatch]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L39-L60
[configured-dispatch]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L24-L26
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[tool-assignment]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[broken]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L588
[harvest]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[strength]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[pillar]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L21-L55
[support]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[note]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L54-L84
[fire]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/FireBlock.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
