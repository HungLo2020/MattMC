# Netherrack

**Netherrack** (`minecraft:netherrack`) is the Nether's basic rock, a source of small Nether Brick items, and the starting ground for Bone Meal conversion to Nylium. **Bring an unbroken pickaxe** to collect it; easy breaking does not remove its correct-tool requirement. [Name][names] · [Block registration][block] · [Item registration][item] · [Harvest gate][gate]

## Finding and collecting

The normal world preset assigns the Nether its noise generator and Nether settings. Those settings use **Netherrack as the default solid terrain block**, and the active noise-fill routine writes that default where its terrain calculation requests it. This establishes ordinary Nether terrain as an acquisition route, without promising Netherrack at every surface or height. [Normal preset][preset] · [Nether settings][settings] · [Active fill entry][fill-entry] · [Default-block placement][fill]

Mining [Crimson or Warped Nylium](NetherGroundAndVegetation.md#variants-and-harvesting) with a suitable pickaxe **without Silk Touch** also gives Netherrack. Covered Nylium can convert into placed Netherrack through its [cover-conversion rule](NetherGroundAndVegetation.md#covered-nylium-becomes-netherrack). [Crimson loot][crimson-loot] · [Warped loot][warped-loot] · [Cover conversion][nylium]

## Mining and placement

An **unbroken Wooden Pickaxe is sufficient**; there is no higher-tier requirement in the checked tags. Successful ordinary mining drops **one Netherrack**, with no Silk Touch requirement or Fortune multiplier. Hand mining and unsuitable or broken tools do not collect it. Explosion destruction has a separate loot-survival check. [Pickaxe tag][pickaxe] · [Wooden-tool exclusions][wood-denials] · [Stone tier][stone-tier] · [Iron tier][iron-tier] · [Diamond tier][diamond-tier] · [Tool rules][tool] · [Tool assignment][tool-assignment] · [Broken-tool guard][broken] · [Active harvest][harvest] · [Loot table][loot]

Netherrack has **hardness 0.4 and blast resistance 0.4**. It is a full block with no facing, axis, or waterlogged state and remains placed without support beneath it. Its low hardness is not a fixed break time. [Registered properties][block] · [Strength values][strength] · [Block implementation][netherrack] · [Default shape and support][support]

## Processing and crafting

- **Small Nether Brick items:** follow [Nether Bricks' smelting recipe](NetherBricks.md#make-the-small-brick-item), including its Furnace time and recipe XP. The result is the small item, not a full Nether Bricks block. Netherrack goes into the ingredient slot and is not a default Furnace fuel. [Smelting recipe][smelt] · [Fuel table][fuel]
- **Netherite Upgrade templates:** Netherrack is the center material in the [existing template's duplication recipe](../items/SmithingTemplateNetheriteUpgrade.md#duplicating). A first template is still required. [Upgrade duplication][upgrade]
- **Rib Armor Trim templates:** place an existing Rib template at top-center, Netherrack at the center, and Diamonds in all seven other slots of a Crafting Table to make **two Rib templates**. [Rib duplication][rib]

The checked recipe bundle has no crafting recipe that produces Netherrack and no Stonecutter conversion using it. Smelting Netherrack to small bricks is the relevant masonry conversion; see [Nether Bricks](NetherBricks.md) for subsequent building shapes.

## Nylium and vegetation

Use [Bone Meal on Netherrack beside existing Nylium](NetherGroundAndVegetation.md#bone-meal-on-netherrack) to turn it into Crimson or Warped Nylium. That guide owns the exact neighborhood, overhead-clearance, item-consumption, and mixed-color rules. The operation converts the target ground block; it does not directly create a huge fungus or a plant item. [Netherrack conversion][netherrack] · [Active Bone Meal dispatch][bone-meal]

Plain Netherrack is not the planting ground accepted by the checked Crimson/Warped Roots, Nether Sprouts, or small Nether fungus support rules. Convert it to suitable ground first; use [Nether Ground and Vegetation](NetherGroundAndVegetation.md#roots-and-nether-sprouts) and [Nether Fungi](NetherFungi.md) for those plants. [Root support][roots] · [Sprout support][sprouts] · [Fungus support][fungus] · [Shared vegetation support][vegetation] · [Dirt tag][dirt]

## Persistent fire

Fire lit **on top of Netherrack** can persist without consuming the rock. Netherrack belongs to the infinite-burn support tags used by the bundled Overworld, Nether, and End dimension types, and the active fire tick skips ordinary rain/burnout removal for such support. This does not light the block automatically or prevent a player from extinguishing the fire. Fire can still spread to nearby flammable blocks when the applicable fire rules allow it. [Overworld tag][infiniburn-overworld] · [Nether tag][infiniburn-nether] · [End tag][infiniburn-end] · [Dimension assignments][overworld-dimension] · [Nether dimension][nether-dimension] · [End dimension][end-dimension] · [Active fire checks and spread][fire-tick]

Related: [Netherrack item](../items/Netherrack.md) · [Nether Bricks](NetherBricks.md) · [Nether Ground and Vegetation](NetherGroundAndVegetation.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`. Checked the registry, loot and tool tags, bundled recipe tree, normal Nether generation caller, Bone Meal and plant-support paths, and dimension-specific fire-support tags. Existing smelting and Nylium-conversion details retain their linked canonical owners. No in-game generation, mining, cooking, planting, or fire test was run. Data packs and game rules can change the relevant behavior.

[names]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/assets/minecraft/lang/en_us.json
[block]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1986-L1995
[item]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L515
[gate]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[preset]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L26-L36
[settings]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json#L1-L6
[fill-entry]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L309-L336
[fill]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L382-L399
[crimson-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/crimson_nylium.json
[warped-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/warped_nylium.json
[nylium]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/NyliumBlock.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[tool-assignment]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[broken]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L588
[harvest]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/netherrack.json
[strength]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[netherrack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/NetherrackBlock.java
[support]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[smelt]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/smelting/nether_brick.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[upgrade]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/netherite_upgrade_smithing_template.json
[rib]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/rib_armor_trim_smithing_template.json
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[roots]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/RootsBlock.java
[sprouts]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/NetherSproutsBlock.java
[fungus]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L53-L68
[vegetation]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[dirt]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/dirt.json
[infiniburn-overworld]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/infiniburn_overworld.json
[infiniburn-nether]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/infiniburn_nether.json
[infiniburn-end]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/infiniburn_end.json
[overworld-dimension]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/dimension_type/overworld.json
[nether-dimension]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/dimension_type/the_nether.json
[end-dimension]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/dimension_type/the_end.json
[fire-tick]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/FireBlock.java#L131-L181
