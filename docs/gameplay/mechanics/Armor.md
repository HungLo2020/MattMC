# Armor and damage reduction

Armor supplies defensive attributes while worn in its matching equipment slots. It is separate from health, absorption, active Shield blocking, and protective status effects. MattMC includes **Copper armor**, so use the current material table rather than a pre-Copper equipment list.

## Ordinary humanoid armor values

These are armor points from functional, unbroken default Helmets, Chestplates, Leggings, and Boots. They exclude enchantments, attribute modifications, Turtle Shell helmets, animal armor, and integrated special equipment.

| Material | Helmet | Chestplate | Leggings | Boots | Full-set armor | Full-set toughness |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Leather | 1 | 3 | 2 | 1 | 7 | 0 |
| Copper | 2 | 4 | 3 | 1 | 10 | 0 |
| Chainmail | 2 | 5 | 4 | 1 | 12 | 0 |
| Iron | 2 | 6 | 5 | 2 | 15 | 0 |
| Gold | 2 | 5 | 3 | 1 | 11 | 0 |
| Diamond | 3 | 8 | 6 | 3 | 20 | 8 |
| Netherite | 3 | 8 | 6 | 3 | 20 | 12 |

A piece contributes its attributes in the matching slot, not by being carried in ordinary inventory. Mixed sets add their applicable piece values; the table's full-set column assumes all four pieces of that material.

Each Netherite piece also adds 0.1 to the knockback-resistance attribute. That is a separate attribute from armor and toughness, not extra hearts or an unconditional immunity.

## Why armor points are not a fixed reduction percentage

The normal armor calculation uses the incoming damage, total armor, and toughness. A larger hit can reduce the fraction stopped by armor; toughness reduces that pressure. The ordinary effective-armor calculation is clamped and then divided by 25 to obtain its reduction fraction.

For a **10-point hit that reaches this armor stage**, with no attacking-weapon armor modifiers or other protections:

- A full Iron set (15 armor, 0 toughness) reduces this stage's result to **6 damage points**
- A full Diamond set (20 armor, 8 toughness) reduces it to **about 3 damage points**

These are examples of one calculation stage, not promises of final health lost in every fight. Resistance, enchantments, absorption, shields, difficulty-related processing, and damage-specific rules can also affect the outcome. Do not interpret a full armor bar as always stopping 80% of every attack.

Damage in the bundled armor-bypass tag skips this armor reduction. Examples include Fall, Drowning, Wither, Ender Pearl, and Sonic Boom damage. Other defenses may have their own rules; see [Shields and death protection](DefensiveItems.md).

## Durability and repairs

Default durability is a material multiplier times the slot's base value: **Helmet 11, Chestplate 16, Leggings 15, Boots 13**. For example:

| Chestplate | Durability | Armor points | Toughness |
| --- | ---: | ---: | ---: |
| [Copper](../items/CopperChestplate.md) | 176 | 4 | 0 |
| [Iron](../items/IronChestplate.md) | 240 | 6 | 0 |
| [Diamond](../items/DiamondChestplate.md) | 528 | 8 | 2 |

Durability measures remaining functional use, not extra health. In this MattMC snapshot, reaching maximum damage retains the item as a **broken stack** rather than deleting it. The equipment-break callback stops its item attribute effects, and ordinary equipment updates exclude broken items from applying those effects. Repairing and reusing retained gear is distinct from finding a replacement. [Wolf Armor](../items/WolfArmor.md#fully-damaged-armor-in-this-snapshot) has a separate absorption path whose missing broken check is documented there. Repair ingredients come from each armor material's tag. The checked Copper, Iron, and Diamond armor tags contain Copper Ingot, Iron Ingot, and Diamond respectively.

A retained enchanted armor piece can still contribute **damage-protection enchantment effects** when their conditions match: the active equipment iterator does not exclude broken stacks. This is separate from the armor attributes removed at breakage. The intended passive-effect contract remains under [#800 review](https://github.com/HungLo2020/MattMC/issues/800#issuecomment-5961208983); no blanket rule that every passive must stop has been established. [Equipment iteration](https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L177) · [Active damage calculation](https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1811-L1822)

Use [Anvil mechanics](AnvilMechanics.md) for repair costs, material amounts, and preserving enchantments. A Grindstone can repair matching pieces but removes non-curse enchantments; choose the method deliberately.

## Crafting and upgrading

The ordinary Copper, Iron, and Diamond crafting patterns use **5 materials for a Helmet, 8 for a Chestplate, 7 for Leggings, and 4 for Boots**: 24 materials for all four pieces. These are separate recipes, not one full-set craft. The supporting Chestplate pages show the exact eight-material pattern.

Netherite equipment follows the matching Diamond-item Smithing upgrade with a Netherite Upgrade template and the accepted addition material. The existing [Smithing guide](../smithing/Smithing.md) covers template consumption, duplication, and retained components. This guide's stat table is not a claim that every listed armor family has the same crafting or acquisition route.

[Durability and repair](Durability.md) compares retained broken gear and the data kept by Anvil, Grindstone, and crafting-grid repairs.

## Related pages

- [Aqua Affinity, Respiration and Thorns](../enchanting/UnderwaterAndThornsEnchantments.md): underwater attributes, retaliation and effect-specific broken-equipment behavior
- [Equipment curses](../enchanting/EquipmentCurses.md): Binding restrictions and Vanishing's death-inventory check

- [Protection enchantments](../enchanting/ProtectionEnchantments.md): the separate damage-reduction stage, matching damage types and broken-stack distinctions

- [Centipede Leggings](../items/CentipedeLeggings.md): chainmail-based attributes with separate recipe, repair ingredient and enchantment-tag limits

- [Combat and defensive stages](Combat.md#layer-defenses-without-adding-their-percentages)
- [Combat effects](../effects/CombatEffects.md): Resistance, Absorption, Health Boost, and attack modifiers

- [Copper Chestplate](../items/CopperChestplate.md)
- [Iron Chestplate](../items/IronChestplate.md)
- [Diamond Chestplate](../items/DiamondChestplate.md)
- [Anvil mechanics](AnvilMechanics.md)
- [Defensive items](DefensiveItems.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game crafting, equipping, damage, repair, or smithing test was run. Tables use the bundled ordinary item components; modified items and data packs can differ.

- [Registered armor items](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Armor material values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java)
- [Slot durability multipliers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorType.java)
- [Slot-specific attribute modifiers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java)
- [Item property construction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java)
- [Armor damage calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/damagesource/CombatRules.java)
- [Armor-bypass application and other protection stages](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Armor-bypass damage tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json)
- [Copper helmet recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_helmet.json)
- [Copper chestplate recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_chestplate.json)
- [Copper leggings recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_leggings.json)
- [Copper boots recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_boots.json)
- [Iron helmet recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/iron_helmet.json)
- [Iron chestplate recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/iron_chestplate.json)
- [Iron leggings recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/iron_leggings.json)
- [Iron boots recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/iron_boots.json)
- [Diamond helmet recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/diamond_helmet.json)
- [Diamond chestplate recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/diamond_chestplate.json)
- [Diamond leggings recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/diamond_leggings.json)
- [Diamond boots recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/diamond_boots.json)

- [Retained broken-stack durability path](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485), [broken equipment attribute gate](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2650-L2683), and [equipment-break effects](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3553)
