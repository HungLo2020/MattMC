# Petrified Oak Slab

**Petrified Oak Slab** (`minecraft:petrified_oak_slab`) is a registered slab with **pickaxe harvesting rules**. It is a separate block from ordinary [Oak Slab](WoodConstruction.md#slabs); its wood-like name does not give it the ordinary wooden slab's recipe, fuel, or fire behavior. Its matching item is registered under the same ID. [Name][names] · [Block registration][block] · [Item registration][item] · [Pickaxe tag][pickaxe]

## Survival availability

**The checked current sources provide no ordinary acquisition route for a fresh Survival world.** There is no bundled crafting, Stonecutter, or smelting recipe producing it, no generated structure-template occurrence, and no loot source other than recovery of the already placed slab itself. It is also absent from the explicit Creative-tab item lists; this page does not repeat the old item's generic Creative-menu claim. [Block recovery table][loot] · [Creative-tab definitions][creative]

Legacy conversion mappings recognize the old wooden variant of Stone Slab as Petrified Oak Slab. Those registered data fixes explain how an existing slab or item can be retained when old data is upgraded; they are not a new resource recipe or natural-generation route. An already existing or administrator-supplied slab can be placed and recovered under the rules below. [Legacy block mappings][legacy-block] · [Legacy item mapping][legacy-item] · [Registered data fixes][fixes]

## Mining and drops

Use an **unbroken pickaxe, including Wooden**. The block requires a correct tool and has no higher-tier restriction in the checked tags. An axe is not the normal harvesting tool for this slab. Ordinary successful mining returns **one Petrified Oak Slab from a single slab, or two from a double slab**. It does not give Oak Planks or ordinary Oak Slabs. Silk Touch is unnecessary and Fortune does not increase the count. Explosion destruction applies quantity decay. [Registration][block] · [Mining tag][pickaxe] · [Wooden-tool exclusions][wood-denials] · [Stone tier][stone-tier] · [Iron tier][iron-tier] · [Diamond tier][diamond-tier] · [Tool rules][tool] · [Tool assignment][tool-assignment] · [Broken-tool guard][broken] · [Harvest dispatch][harvest] · [Complete loot table][loot]

## Placement, doubling, and water

The slab has `type=bottom`, `top`, or `double`, plus a `waterlogged` state. Click a top face for a bottom slab or an underside for a top slab; a side click uses the height of the click. A single slab occupies half a block. Place another **Petrified Oak Slab** into its missing half to make a full-height double slab. An ordinary Oak Slab cannot complete it. [State, shapes, placement, and matching-item check][slab]

A single slab can hold source water through placement or bucket waterlogging. Combining it into a double slab clears the waterlogged state, and a double slab rejects bucket waterlogging. Neither the single nor double form needs continuing support beneath it. [Slab water behavior][slab] · [Bucket filling and recovery][water] · [Default support][support]

## Material properties

Petrified Oak Slab has **hardness 2 and blast resistance 6**. Its registration selects the **bass-drum** Note Block instrument, has no lava-ignition flag, and its block has no ordinary fire-consumption entry. It is absent from the wooden-slab item tag used for slab fuel, and is **not a default Furnace fuel**. These are placed-block and fuel rules, not a claim that a dropped slab item is immune to lava. [Properties][block] · [Strength values][strength] · [Fire table][fire] · [Wooden-slab item tag][wooden-slabs] · [Fuel table][fuel]

Related: [Petrified Oak Slab item](../items/PetrifiedOakSlab.md) · [Ordinary wood construction](WoodConstruction.md) · [Mining](../mechanics/Mining.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`. Checked registration, all current-source references to the ID, the bundled recipe and loot trees, 1,202 bundled structure NBT files, Creative-tab lists, legacy mappings, tool tags, slab placement/water behavior, and fire/fuel tables. The acquisition conclusion applies to the checked bundle; pre-existing worlds, commands, or data packs can supply blocks differently. No gameplay availability, legacy-world upgrade, mining, placement, waterlogging, or fire test was run.

[names]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/assets/minecraft/lang/en_us.json
[block]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3857-L3861
[item]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L400
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/petrified_oak_slab.json
[creative]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[legacy-block]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/util/datafix/fixes/BlockStateData.java#L1255-L1290
[legacy-item]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/util/datafix/fixes/ItemStackTheFlatteningFix.java#L226
[fixes]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/util/datafix/DataFixers.java#L498-L513
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[tool-assignment]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[broken]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L588
[harvest]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[slab]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L134
[water]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L20-L51
[support]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[strength]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[fire]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/FireBlock.java
[wooden-slabs]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
