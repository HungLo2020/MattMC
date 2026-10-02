# Iron Shovel

**Iron Shovel** (`minecraft:iron_shovel`) has **250 durability** and a **6.0 matching-block mining-speed value** before player modifiers. See [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for its shared actions, wear, and collection rules. [Registration][registration] · [Material properties][materials]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), put **1 Iron Ingot** above **2 [Sticks](Stick.md)** in one vertical three-slot column. This makes **1 Iron Shovel**. The three-row layout does not fit the inventory crafting grid. [Recipe][recipe] · [Accepted material][repair-iron]

## Mining and upkeep

It uses the same current shovel targets and path/campfire actions as the other six materials. For conditions and exceptions, see [mining and collecting](../mechanics/PickaxesAndShovels.md#mining-and-collecting), [path creation](../mechanics/PickaxesAndShovels.md#making-paths-with-a-shovel), and [campfire extinguishing](../mechanics/PickaxesAndShovels.md#extinguishing-campfires). [Shovel registration][registration] · [Shovel callback][shovel]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Iron Ingot](IronIngot.md). Ordinary wear retains a fully damaged stack; repair it before relying on its mining speed or shovel-use actions. [Repair material][repair-iron] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Broken-stack rules][broken] · [Use and speed guards][use-guard] · [Drop guard][hit-mine]

An unwanted tool can also be processed through the [verified nugget-recycling recipes](../mechanics/PickaxesAndShovels.md#recycling-and-fuel).

Related: [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, or item-use test was run. The documented creation route is verified; this page does not inventory trades, chest loot, or every other acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1327
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[recipe]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/crafting/iron_shovel.json
[repair-iron]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/iron_tool_materials.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ShovelItem.java#L20-L73
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L564-L588
