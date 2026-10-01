# Anvil Repairing, Naming, and Combining

MattMC anvils charge at most **40 experience levels per completed operation**. They do **not** reject an otherwise valid result with the vanilla “Too Expensive!” rule. A Survival player at level 40 can take a result whose calculated cost exceeds 40; the payment is clamped to 40. These are whole experience levels, not a fixed number of experience points. [Payment and eligibility][payment] · [Cost cap][cost] · [Cost display][screen]

## Set up the inputs

1. Put the item you want to keep in the **left slot**
2. Put repair materials, a matching sacrifice item, or an enchanted book in the **middle slot**
3. Optionally change the name, then inspect the output and level cost
4. Take the result to commit the operation

The left item provides the result's identity and components. The right item is the donor. Swapping them can change the cost, surviving enchantments, and preserved item data. For a simple rename, leave the right slot empty. [Result construction][result]

## Repairing durability

### With repair materials

The target must be damageable and accept the ingredient through its repairable-item component. Each material repairs up to **one quarter of maximum durability, rounded down**, and adds **1 level** to the work cost. Only the amount needed and available is consumed; the last ingredient can repair less than a quarter. Depending on rounding, four ingredients are not guaranteed to fully repair a heavily damaged item. [Material repair][material] · [Repair-item check][repair-check]

For the standard tool families, the bundled ingredients are:

| Tool material | Accepted repair ingredient |
| --- | --- |
| Wood | Any item in the planks tag |
| Stone | Cobblestone, Blackstone, or Cobbled Deepslate |
| Copper | Copper Ingot |
| Iron | Iron Ingot |
| Gold | Gold Ingot |
| Diamond | Diamond |
| Netherite | Netherite Ingot |

These follow the tool material's repair tags, not the appearance or name of an arbitrary integrated item. Armor has its own tags; for example, both Iron and Chainmail armor accept Iron Ingots. [Tool-material wiring][tool-materials] · [Wood][wood] · [Stone][stone] · [Copper][copper] · [Iron][iron] · [Gold][gold] · [Diamond][diamond] · [Netherite][netherite] · [Armor wiring][armor] · [Iron armor tag][iron-armor] · [Chainmail tag][chainmail]

### With a matching item

Two damageable items of the **same item type** can combine durability. The result receives their remaining durability plus **12% of the left item's maximum durability, rounded down**, capped at full durability. If that improves the left item, the repair adds **2 levels** to the work cost. Enchantments on the donor are then evaluated separately. The sacrificed item is consumed when you take the result. [Same-item repair][sacrifice] · [Input consumption][payment]

## Combining enchantments

Use compatible same-type equipment or an enchanted book as the donor:

- Two equal levels combine into the next level, up to that enchantment's maximum
- Unequal levels keep the higher level, still capped at the maximum
- The enchantment must support the target item, and it must be compatible with the enchantments already retained on the result
- Conflicting enchantments are skipped; if every donor enchantment is rejected, the combination has no result

Creative bypasses the supported-item check, but **does not bypass enchantment incompatibility or maximum-level caps**. Enchanted books as targets also bypass the supported-item check. [Combining rules][combining] · [Compatibility and supported items][enchantment]

Each accepted donor enchantment adds its **resulting level × anvil-cost multiplier**. With an enchanted-book donor, the multiplier is halved and rounded down, with a minimum of 1. Each incompatibility found adds another level before the overall 40-level cap. [Enchantment costs][combining]

For example, two **Sharpness III enchanted books with zero prior-work penalties** combine into Sharpness IV for **4 levels**, assuming no other enchantments or name change. Sharpness's multiplier is 1 and its maximum is V. [Sharpness definition][sharpness]

## Naming and prior work

Changing an item's name, or clearing an existing custom name, adds **1 level** to the work cost. With no prior anvil penalty and an empty right slot, this is a one-level operation, including renaming an ordinary stack. The name field accepts up to **50 characters** after text filtering; an unchanged name alone does not produce work to collect. [Naming and result cost][naming] · [Name validation][name-validation]

The final charge is the **sum of both inputs' prior-work penalties and the current work charges**, capped at 40. Fresh standard items start with a prior-work value of 0. After repair or enchantment combining, the result's stored penalty becomes **twice the larger input penalty, plus 1**. A repeatedly worked item can therefore progress through **0 → 1 → 3 → 7 → 15 → 31 → 63**. This stored value can exceed 40 even though the payment cannot. [Prior-work calculation][cost] · [Default components][defaults]

A rename-only operation does not apply that doubling step. With the right slot empty, it preserves the left item's prior-work value rather than resetting it. An item with a large penalty can still cost 40 levels just to rename. [Naming and penalty handling][cost]

## What is kept and consumed

The result starts as a copy of the **left stack**, including its count and components. The anvil updates the damage, custom name, enchantments, and prior-work component as the operation requires. Other left-item data is retained; unrelated donor data, such as its custom name or trim, is not copied across. Enchanted books store the combined enchantments in their stored-enchantment component. [Stack copy][copy] · [Result updates][cost] · [Enchantment components][components]

Taking the result clears the left input. Material repair uses only the counted ingredients; a non-material combination clears the right donor stack. Rename-only work leaves the right stack unconsumed, but a populated right slot can still change validation and cost, so keep it empty when naming. [Input consumption][payment]

Creative waives the experience requirement and payment, and skips the anvil's menu-use wear roll. **The menu's input consumption still runs in Creative**, so do not use its slots as storage. Survival and Adventure have no such experience exemption. [Payment, consumption, and wear][payment] · [Mode abilities][modes]

## If the output is missing or unavailable

- **Output is present but cannot be taken:** check your level against the displayed cost
- **No output:** the repair ingredient may be invalid, the item may already be fully repaired, or no applicable operation remains
- **Book combination fails:** check supported equipment and conflicts with existing enchantments
- **Custom item cannot even be renamed:** the menu requires an enchantment-storage component on the target, which custom item data can remove

[Result validation][result] · [Enchantment-storage check][components]

See [Durability and repair](Durability.md) before choosing a different repair method: crafting-grid repair creates a fresh item and does not preserve the same custom data as an Anvil.

## Related pages

- [Anvil block and wear](../blocks/Anvil.md)
- [Anvil item](../items/Anvil.md)
- [Enchanting](../enchanting/Enchanting.md)
- [Smithing](../smithing/Smithing.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-01** at commit `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game tests were run. Data packs and item components can alter repair ingredients, enchantment eligibility, compatibility, and multipliers. The examples describe the bundled definitions and the stated input conditions.

[payment]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L71-L116
[result]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L119-L268
[material]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L133-L151
[sacrifice]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L153-L175
[combining]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L175-L223
[naming]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L226-L246
[cost]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L238-L276
[name-validation]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L298-L301
[screen]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/gui/screens/inventory/AnvilScreen.java#L94-L117
[repair-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1109
[tool-materials]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L33
[wood]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json
[stone]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[copper]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/copper_tool_materials.json
[iron]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/iron_tool_materials.json
[gold]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/gold_tool_materials.json
[diamond]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json
[netherite]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[armor]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L15-L20
[iron-armor]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/repairs_iron_armor.json
[chainmail]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/repairs_chain_armor.json
[enchantment]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L180
[sharpness]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/enchantment/sharpness.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[copy]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L607-L615
[components]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L69-L83
[modes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/GameType.java#L62-L77
