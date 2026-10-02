# Netherite Shovel

**Netherite Shovel** (`minecraft:netherite_shovel`) has **2,031 durability** and a **9.0 matching-block mining-speed value** before player modifiers. See [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for its shared actions, wear, and collection rules. [Registration][registration] · [Material properties][materials]

## Obtaining

At a [Smithing Table](../blocks/SmithingTable.md), combine **1 [Netherite Upgrade Template](SmithingTemplateNetheriteUpgrade.md) + 1 [Diamond Shovel](DiamondShovel.md) + 1 [Netherite Ingot](NetheriteIngot.md)** to receive **1 Netherite Shovel**. Taking the result consumes one of each input. The addition tag contains only Netherite Ingot in this snapshot. [Recipe][netherite-shovel] · [Addition tag][repair-netherite] · [Consumption][smith-menu] · [Output count][transmute]

The upgrade retains saved changes such as enchantments, a custom name, and damage. It does not reset the tool to full durability. See [Smithing](../smithing/Smithing.md) for the component rules. [Transform callback][smith-transform] · [Result application][transmute-apply] · [Component transfer][stack-patch]

## Mining and upkeep

It uses the same current shovel targets and path/campfire actions as the other six materials. For conditions and exceptions, see [mining and collecting](../mechanics/PickaxesAndShovels.md#mining-and-collecting), [path creation](../mechanics/PickaxesAndShovels.md#making-paths-with-a-shovel), and [campfire extinguishing](../mechanics/PickaxesAndShovels.md#extinguishing-campfires). [Shovel registration][registration] · [Shovel callback][shovel]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Netherite Ingot](NetheriteIngot.md). Ordinary wear retains a fully damaged stack; repair it before relying on its mining speed or shovel-use actions. [Repair material][repair-netherite] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Broken-stack rules][broken] · [Use and speed guards][use-guard] · [Drop guard][hit-mine]

Dropped Netherite tools have [fire and lava resistance](../mechanics/PickaxesAndShovels.md#netherite-upgrades) through their item damage-resistance property.

Related: [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, or item-use test was run. The documented creation route is verified; this page does not inventory trades, chest loot, or every other acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1337-L1339
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[netherite-shovel]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/smithing/netherite_shovel_smithing.json
[repair-netherite]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[smith-menu]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L70-L93
[transmute]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L23
[smith-transform]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L33
[transmute-apply]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
[stack-patch]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[shovel]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ShovelItem.java#L20-L73
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L564-L588
