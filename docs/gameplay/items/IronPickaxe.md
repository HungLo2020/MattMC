# Iron Pickaxe

**Iron Pickaxe** (`minecraft:iron_pickaxe`) has **250 durability** and a **6.0 matching-block mining-speed value** before player modifiers. See [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for its shared actions, wear, and collection rules. [Registration][registration] · [Material properties][materials]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), use **3 Iron Ingots** across the top row and **2 [Sticks](Stick.md)** down the middle beneath them. This makes **1 Iron Pickaxe**. [Recipe][recipe] · [Accepted head materials][repair-iron]

## Mining and upkeep

It meets the material requirement for Diamond, Emerald, Gold, and Redstone Ore. Diamond-requirement blocks such as Obsidian and Ancient Debris remain excluded. See [Mining tiers](../mechanics/Mining.md#tier-restrictions) and [Ore resources](../blocks/OreResources.md). [Material exclusion tag][deny-iron] · [Pickaxe targets][pickaxe-tag] · [Drop rules][tool]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Iron Ingot](IronIngot.md). Ordinary wear retains a fully damaged stack; repair it before relying on its mining speed and correct-tool drops. [Repair material][repair-iron] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Broken-stack rules][broken] · [Use and speed guards][use-guard] · [Drop guard][hit-mine]

An unwanted tool can also be processed through the [verified nugget-recycling recipes](../mechanics/PickaxesAndShovels.md#recycling-and-fuel).

Related: [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, or item-use test was run. The documented creation route is verified; this page does not inventory trades, chest loot, or every other acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1328
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[recipe]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/crafting/iron_pickaxe.json
[repair-iron]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/iron_tool_materials.json
[deny-iron]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L564-L588
