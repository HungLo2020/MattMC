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

For a **10-point hit that reaches this armor stage**, with all pieces surviving this hit's wear and no attacking-weapon armor modifiers or other protections:

- A full Iron set (15 armor, 0 toughness) reduces this stage's result to **6 damage points**
- A full Diamond set (20 armor, 8 toughness) reduces it to **about 3 damage points**

These are examples of one calculation stage, not promises of final health lost in every fight. Resistance, enchantments, absorption, shields, difficulty-related processing, and damage-specific rules can also affect the outcome. Do not interpret a full armor bar as always stopping 80% of every attack.

Damage in the bundled armor-bypass tag skips this armor reduction. Examples include Fall, Drowning, Wither, Ender Pearl, and Sonic Boom damage. Other defenses may have their own rules; see [Shields and death protection](DefensiveItems.md).

## How incoming hits wear your armor

For a positive hit that reaches ordinary armor handling, divide the damage at that stage by **4**, round down, and use a **minimum of 1 durability point per eligible worn piece**. The player checks Boots, Leggings, Chestplate and Helmet separately: a full set does not share one wear amount. Only damageable equipment configured to take damage when its wearer is hurt, and able to be hurt by that damage source, qualifies. Default humanoid armor uses this setting. [Wear calculation][wear-calculation] · [Player slots][wear-player] · [Armor setup][wear-armor-setup] · [Equipment default][wear-equipment-default]

| Damage reaching this stage | Base wear requested from each eligible piece |
| ---: | ---: |
| 1 | 1 |
| 7 | 1 |
| 8 | 2 |
| 10 | 2 |
| 20 | 5 |

These are **requests before Unbreaking**, not guaranteed losses. A 10-point hit requests 2 from each of four eligible pieces, or 8 across the set. Armor and toughness reduce the hit **after** this wear request; Resistance, Protection enchantments and absorption follow later. Losing few or no red hearts therefore does not mean your armor avoided wear. [Damage-stage order][wear-order] · [Resistance and enchantments][wear-magic] · [Unbreaking and ordinary wear exceptions](../enchanting/DurabilityEnchantments.md#unbreaking-a-chance-for-each-wear-point)

Use the damage that actually reaches this stage, not an attack's advertised strength. Shield blocking happens earlier. During the repeat-hit immunity window, an equal or smaller hit can be rejected, while a stronger hit can pass only its increase into ordinary armor handling. [Blocking and repeat-hit handling][wear-early]

Two exceptions matter when judging damage sources:

- **Armor-bypassing damage skips this ordinary all-piece wear as well as armor reduction.** The bundled tag includes Fall, Drowning and Wither damage. This says nothing about separate wear triggers such as the head impact below. [Armor-stage gate][wear-calculation] · [Bypass tag][wear-bypass]
- **Default Netherite armor resists fire-tagged wear.** Its item fire resistance makes the piece ineligible for wear from sources in the fire tag, such as Lava. Wearing it does not grant the player Fire Resistance or fire immunity. [Netherite properties][wear-netherite] · [Item fire resistance][wear-fire-property] · [Item damage check][wear-item-resistance] · [Resistance tag check][wear-resistance-test] · [Fire tag][wear-fire-tag]

Repair nearly broken armor before a dangerous fight. If the hit's wear breaks a piece, its armor and toughness modifiers are removed **before that same hit's armor reduction is calculated**. The stack remains available for repair; its enchantments have their own behavior, as explained under [Durability and repairs](#durability-and-repairs). [Wear before attributes][wear-calculation] · [Break callback][wear-stack] · [Attribute removal][wear-break]

### Head protection against falling impacts

Falling Anvil, Falling Block and Falling Stalactite damage have an extra head-slot step. With a nonempty head slot, the game first requests wear from eligible head equipment using the same divide-by-four rule, then multiplies incoming damage by **0.75**. Ordinary armor handling can request wear again afterward. [Helmet-damage tag][wear-helmet-tag] · [Early head step][wear-early] · [Head-slot wear][wear-player]

For example, take a fresh **10-point falling-anvil hit**, with no earlier blocking or repeat-hit adjustment, an ordinary unbroken armor set with enough durability, and no durability-modifying enchantments: the head step requests **2** durability, then reduces damage to **7.5**. The ordinary armor stage requests **1 more from each piece**, so the Helmet loses **3** in total and the other pieces lose **1 each**. Armor and any later defenses then reduce the remaining damage; 7.5 is not the promised health loss. This is source-derived arithmetic, not an in-game measurement. [Anvil damage source][wear-anvil-source] · [Head step][wear-early] · [Ordinary wear][wear-calculation]

The early 25% reduction checks a **nonempty head slot**, without requiring an armor item or an unbroken item. Its wear request has the separate equipment and durability checks above. This step also precedes repeat-hit rejection, so head wear can be requested even when the later ordinary hit is rejected. Do not use the ordinary armor-wear rule to predict every head-slot result. [Head test and ordering][wear-early] · [Wear eligibility][wear-calculation] · [Broken-stack wear gate][wear-stack]

[Thorns retaliation](../enchanting/UnderwaterAndThornsEnchantments.md#thorns-retaliation-and-wear) and [Soul Speed travel](../enchanting/MobilityEnchantments.md#soul-speed-terrain-boost-and-wear) have separate wear triggers. Use those guides for their conditions, and [Durability and repair](Durability.md#choose-a-repair-method) for choosing a repair route.

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

The incoming-hit wear and falling-impact guidance was source-reviewed on **2026-10-10** at `f86206767dadde696adfed4e04c5ee97cd0d0885`. The examples follow the checked player damage path and bundled tags; no in-game damage or wear test was run. Earlier material and repair verification below retains its original source scope.

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

[wear-calculation]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1766-L1786
[wear-player]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/player/Player.java#L777-L794
[wear-armor-setup]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Item.java#L459-L466
[wear-equipment-default]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L178-L186
[wear-order]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/player/Player.java#L787-L811
[wear-magic]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1789-L1829
[wear-early]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1199
[wear-bypass]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
[wear-netherite]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Items.java#L1395-L1406
[wear-fire-property]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[wear-item-resistance]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[wear-resistance-test]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/component/DamageResistant.java#L21-L23
[wear-fire-tag]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[wear-stack]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L508
[wear-break]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3552
[wear-helmet-tag]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/damage_type/damages_helmet.json
[wear-anvil-source]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/AnvilBlock.java#L99-L102
