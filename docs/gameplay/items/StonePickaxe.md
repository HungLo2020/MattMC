# Stone Pickaxe

**Stone Pickaxe** (`minecraft:stone_pickaxe`) has **131 durability** and a **4.0 matching-block mining-speed value** before player modifiers. See [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for its shared actions, wear, and collection rules. [Registration][registration] · [Material properties][materials]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), use **3 blocks chosen from Cobblestone, Blackstone, and Cobbled Deepslate** across the top row and **2 [Sticks](Stick.md)** down the middle beneath them. This makes **1 Stone Pickaxe**. [Recipe][recipe] · [Accepted head materials][repair-stone]

The three head slots can use different accepted materials. Ordinary Stone is not in this ingredient tag. [Material tag][repair-stone] · [Slot matching][pattern]

## Mining and upkeep

It meets the material requirement for Iron and Copper Ore, but its material denies drops from Iron- and Diamond-requirement blocks, including Diamond Ore and Obsidian. See [Mining tiers](../mechanics/Mining.md#tier-restrictions) and [Ore resources](../blocks/OreResources.md). [Material exclusion tag][deny-stone] · [Pickaxe targets][pickaxe-tag] · [Drop rules][tool]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Cobblestone](Cobblestone.md), [Blackstone](Blackstone.md), or [Cobbled Deepslate](CobbledDeepslate.md). Ordinary wear retains a fully damaged stack; repair it before relying on its mining speed and correct-tool drops. [Repair material][repair-stone] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Broken-stack rules][broken] · [Use and speed guards][use-guard] · [Drop guard][hit-mine]

Related: [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, or item-use test was run. The documented creation route is verified; this page does not inventory trades, chest loot, or every other acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1318
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[recipe]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/crafting/stone_pickaxe.json
[repair-stone]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[deny-stone]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L564-L588
