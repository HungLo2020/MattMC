# Bamboo blocks

**Block of Bamboo** and **Block of Stripped Bamboo** are solid, directionally placed building materials. Make the first from harvested [Bamboo](Bamboo.md), then strip it with an axe for the second. Both feed the [Bamboo Planks recipe](WoodConstruction.md#planks-and-materials); neither is the growing Bamboo stalk. [Names][names] · [Block registrations][blocks] · [Item registrations][items]

## Block of Bamboo

**`minecraft:bamboo_block`** is the unstripped form. Use the [nine-Bamboo packing recipe](Bamboo.md#selected-uses-and-fuel) at a Crafting Table. The recipe is shapeless, but its nine ingredient slots do not fit the personal 2 × 2 grid. There is no bundled reverse recipe that unpacks the block into loose Bamboo. [Packing recipe][pack]

The [Bamboo guide](Bamboo.md#finding-and-collecting) owns the plant's checked Bamboo Jungle generation, harvesting, and regrowth routes. The crafting route here does not require finding a naturally placed Block of Bamboo.

## Block of Stripped Bamboo

**`minecraft:stripped_bamboo_block`** comes from using an **unbroken axe on a placed Block of Bamboo**. One placed block becomes one stripped block, preserving its axis; the interaction requests one point of axe durability and produces no extra material item. The stripped block has no further stripping partner. If an offhand shield takes priority, use secondary interaction, normally sneaking. [Stripping map and use][strip] · [Axis retention][strip-axis] · [Broken-tool guard][broken-use]

Mining the result collects the stripped item. There is no separate bundled crafting, smelting, or Stonecutter recipe producing it. [Stripped loot][loot-stripped] · [Axe conversion][strip]

## Mining and placement

An **axe** gives the efficient mining speed for both forms, but **hand mining also collects them**: neither requires a correct tool or material tier. Ordinary mining returns **one matching block**, with no Silk Touch requirement or Fortune multiplier. The ordinary block stays ordinary; the stripped block stays stripped. Explosion destruction has a separate survival check. [Axe tag][axe] · [Tool rules][tool] · [Tool assignment][tool-assignment] · [Harvest gate][gate] · [Active harvesting][harvest] · [Ordinary loot][loot] · [Stripped loot][loot-stripped]

Both have **hardness 2 and blast resistance 2**. They are full cubes with an `axis` state: click a top or bottom face for a vertical pillar, or a side for a horizontal pillar. They have no waterlogged state and remain placed when supporting blocks are removed. Ordinary placement still checks for an unobstructed destination. [Registered properties][blocks] · [Property helper][properties] · [Strength interpretation][strength] · [Axis and state][pillar] · [Shape and support][support] · [Placement checks][placement]

## Construction uses

Either form works in the [Bamboo Planks conversion](WoodConstruction.md#planks-and-materials), using the two-member Bamboo-block item tag. The **stripped** form is also the exact input for [Bamboo Hanging Signs](Signs.md#hanging-sign-variants) and [Bamboo Shelves](Shelves.md#variants-and-crafting). Those pages own the complete layouts and yields. Ordinary Blocks of Bamboo do not substitute in either stripped-input recipe. [Bamboo ingredient tag][bamboo-tag] · [Planks recipe][planks] · [Hanging Sign recipe][sign] · [Shelf recipe][shelf]

## Fire and fuel

Both blocks can burn in spreading fire and carry the lava-ignition property. They supply **300 default Furnace burn ticks per item** through the Bamboo-block fuel tag. A block's two-plank conversion supplies 600 ticks in total because each plank supplies 300; the nine loose Bamboo used to make that block supply 450 ticks. These totals describe continuous fuel use, not guaranteed completed work if a Furnace is interrupted. See [Furnace fuel planning](Furnace.md#fuel-planning). [Fire entries][fire] · [Block properties][properties] · [Fuel values][fuel] · [Server fuel setup][fuel-init] · [Furnace lookup][fuel-use] · [Plank yield][planks]

Neither block belongs to the bundled **burnable-log ingredient tag**, so neither can be smelted into Charcoal by the ordinary Charcoal recipe. Being Furnace fuel does not make an item a Charcoal ingredient. [Charcoal recipe][charcoal] · [Burnable logs][burnable]

Related: [Block of Bamboo item](../items/BlockOfBamboo.md) · [Block of Stripped Bamboo item](../items/BlockOfStrippedBamboo.md) · [Tree Logs and Roots](TreeLogsAndRoots.md) · [Wood Construction](WoodConstruction.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`. Checked both names, registrations, loot tables, expanded tool and ingredient tags, the bundled recipe tree, stripping, placement, fire, and active fuel lookup. Plant generation and shared construction recipes retain their linked canonical owners. No in-game crafting, stripping, mining, placement, or fire test was run. Data packs can change recipes, tags, loot, and fuel eligibility.

[names]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/assets/minecraft/lang/en_us.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L422-L452
[items]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L233-L256
[pack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bamboo_block.json
[strip]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L100
[strip-axis]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L132
[broken-use]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/bamboo_block.json
[loot-stripped]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/stripped_bamboo_block.json
[axe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[tool-assignment]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[gate]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[properties]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L7162-L7168
[strength]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[pillar]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L21-L55
[support]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[placement]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L137
[bamboo-tag]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/item/bamboo_blocks.json
[planks]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bamboo_planks.json
[sign]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bamboo_hanging_sign.json
[shelf]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/bamboo_shelf.json
[fire]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/FireBlock.java#L363-L383
[fuel]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[fuel-use]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L273
[charcoal]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/smelting/charcoal.json
[burnable]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
