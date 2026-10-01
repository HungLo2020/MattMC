# Iron Chestplate

Iron Chestplate (`minecraft:iron_chestplate`) provides **6 armor points** and **0 toughness** when worn in the chest slot. Its default durability is **240**. Carrying it in ordinary inventory does not grant those armor attributes.

## Crafting

Use **eight [Iron Ingots](IronIngot.md)** in a Crafting Table, filling every slot except the top-center slot. The recipe makes one Chestplate. Other armor pieces use different patterns; this is not a full-set recipe.

## Use and repair

Equip it in the chest slot. Armor points do not translate to one fixed percentage reduction for every incoming hit; see [Armor and damage reduction](../mechanics/Armor.md) for toughness, mixed sets, and bypassing damage types.

The bundled repair tag accepts Iron Ingot. An Anvil material repair restores up to **60 durability per ingredient**, limited by missing durability, and has its normal level cost. Combining matching armor is a separate repair route. A Grindstone strips non-curse enchantments, so use [Anvil mechanics](../mechanics/AnvilMechanics.md) when deciding what to preserve.

## Related pages

- [Armor guide](../mechanics/Armor.md)
- [Iron Ingot](IronIngot.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game crafting, equipping, damage, repair, or smithing test was run. Tables use the bundled ordinary item components; modified items and data packs can differ.

- [Registered armor items](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Armor material values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java)
- [Slot durability multipliers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorType.java)
- [Slot-specific attribute modifiers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java)
- [Item property construction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java)
- [Iron Chestplate recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/iron_chestplate.json)
- [Repair ingredient tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/repairs_iron_armor.json)
