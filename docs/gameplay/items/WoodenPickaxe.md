# Wooden Pickaxe

**Wooden Pickaxe** (`minecraft:wooden_pickaxe`) has **59 durability** and a **2.0 matching-block mining-speed value** before player modifiers. See [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for its shared actions, wear, and collection rules. [Registration][registration] · [Material properties][materials]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), use **3 accepted planks** across the top row and **2 [Sticks](Stick.md)** down the middle beneath them. This makes **1 Wooden Pickaxe**. [Recipe][recipe] · [Accepted head materials][repair-wooden]

The three head slots can use different accepted materials. [The checked plank list](../mechanics/PickaxesAndShovels.md#choose-and-make-a-tool) includes 12 plank types; Pewen Planks are absent. [Material tag][repair-wooden] · [Slot matching][pattern]

## Mining and upkeep

Its material denies drops from blocks in the Stone-, Iron-, and Diamond-requirement tags. In particular, its speed does not make it suitable for collecting Iron Ore. See [Mining tiers](../mechanics/Mining.md#tier-restrictions) and [Ore resources](../blocks/OreResources.md). [Material exclusion tag][deny-wooden] · [Pickaxe targets][pickaxe-tag] · [Drop rules][tool]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [accepted planks](../mechanics/PickaxesAndShovels.md#choose-and-make-a-tool). Ordinary wear retains a fully damaged stack; repair it before relying on its mining speed and correct-tool drops. [Repair material][repair-wooden] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Broken-stack rules][broken] · [Use and speed guards][use-guard] · [Drop guard][hit-mine]

It is also [furnace fuel](../mechanics/PickaxesAndShovels.md#recycling-and-fuel), including after it becomes broken.

Related: [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, or item-use test was run. The documented creation route is verified; this page does not inventory trades, chest loot, or every other acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1308
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[recipe]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/crafting/wooden_pickaxe.json
[repair-wooden]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[deny-wooden]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L564-L588
