# Diamond Chestplate

Diamond Chestplate (`minecraft:diamond_chestplate`) provides **8 armor points** and **2 toughness** when worn in the chest slot. Its default durability is **528**. Carrying it in ordinary inventory does not grant those armor attributes.

## Crafting

Use **eight [Diamonds](Diamond.md)** in a Crafting Table, filling every slot except the top-center slot. The recipe makes one Chestplate. Other armor pieces use different patterns; this is not a full-set recipe.

## Use and repair

Equip it in the chest slot. Armor points do not translate to one fixed percentage reduction for every incoming hit; see [Armor and damage reduction](../mechanics/Armor.md) for toughness, mixed sets, and bypassing damage types.

The bundled repair tag accepts Diamond. An Anvil material repair restores up to **132 durability per ingredient**, limited by missing durability, and has its normal level cost. Combining matching armor is a separate repair route. A Grindstone strips non-curse enchantments, so use [Anvil mechanics](../mechanics/AnvilMechanics.md) when deciding what to preserve.

## Upgrading

A Diamond Chestplate is the base item for its matching Netherite Chestplate Smithing recipe, with a Netherite Upgrade template and the netherite-material addition. See [Smithing](../smithing/Smithing.md) before spending the template or upgrading an item with valuable components.

## Related pages

- [Armor guide](../mechanics/Armor.md)
- [Diamond](Diamond.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game crafting, equipping, damage, repair, or smithing test was run. Tables use the bundled ordinary item components; modified items and data packs can differ.

- [Registered armor items](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Armor material values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java)
- [Slot durability multipliers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorType.java)
- [Slot-specific attribute modifiers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java)
- [Item property construction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java)
- [Diamond Chestplate recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/diamond_chestplate.json)
- [Repair ingredient tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/repairs_diamond_armor.json)
- [Netherite upgrade recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smithing/netherite_chestplate_smithing.json)
