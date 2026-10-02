# Diamond Pickaxe

**Diamond Pickaxe** (`minecraft:diamond_pickaxe`) has **1,561 durability** and a **8.0 matching-block mining-speed value** before player modifiers. See [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for its shared actions, wear, and collection rules. [Registration][registration] · [Material properties][materials]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), use **3 Diamonds** across the top row and **2 [Sticks](Stick.md)** down the middle beneath them. This makes **1 Diamond Pickaxe**. [Recipe][recipe] · [Accepted head materials][repair-diamond]

This is the base for a [Netherite Pickaxe](NetheritePickaxe.md) upgrade. See [Smithing](../smithing/Smithing.md) before spending a template; an upgrade carries the damage value across.

## Mining and upkeep

Its material exclusion tag is empty, so it meets the material-tier check for Obsidian and Ancient Debris. A matching pickaxe-family rule and the block’s loot conditions are still required. See [Mining tiers](../mechanics/Mining.md#tier-restrictions) and [Ore resources](../blocks/OreResources.md). [Material exclusion tag][deny-diamond] · [Pickaxe targets][pickaxe-tag] · [Drop rules][tool]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Diamond](Diamond.md). Ordinary wear retains a fully damaged stack; repair it before relying on its mining speed and correct-tool drops. [Repair material][repair-diamond] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Broken-stack rules][broken] · [Use and speed guards][use-guard] · [Drop guard][hit-mine]

Related: [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, or item-use test was run. The documented creation route is verified; this page does not inventory trades, chest loot, or every other acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1333
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[recipe]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/crafting/diamond_pickaxe.json
[repair-diamond]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json
[deny-diamond]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L564-L588
