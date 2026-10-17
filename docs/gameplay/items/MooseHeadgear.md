# Moose Headgear

**Moose Headgear** is head-slot armor with **1 armor point**, **55 maximum durability**, and no additional toughness or knockback resistance. Its final settings come from Leather helmet properties. [Registration][item] · [Material][material] · [Helmet multiplier][slot] · [Final properties][properties] · [Attributes][attributes]

## Obtaining

Moose Headgear is an ordinary Combat-category entry in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), which can supply listed items in **Creative** under its normal insertion checks. Catalog visibility does not grant Survival items. [Category entry][category] · [Browser assembly][browser]

**No bundled crafting recipe, loot-table entry or trade for the Headgear was found in this snapshot.** [Moose](../mobs/Moose.md#antler-shedding) shed [Moose Antlers](MooseAntler.md), but there is no bundled Antler-to-Headgear recipe. Collecting Antlers does not establish a Survival source for this item. [Recipes][recipes] · [Loot tables][loot] · [Trades][trades] · [Antler shedding][shedding]

## Usage

Place the Headgear in the **head slot** of your inventory. Shift-clicking it from the main inventory or hotbar also fills an empty head slot. Using a functional Headgear while holding it attempts to equip it or swap it with your current head-slot item, subject to the existing item's removal restrictions. Carrying it in ordinary inventory adds no armor. [Inventory slots][inventory] · [Shift-click route][quick-move] · [Slot acceptance][armor-slot] · [Head-slot test][equipment-slot] · [Held use][use] · [Swap restrictions][swap]

A Dispenser can equip it onto an eligible living target in the block directly ahead with an empty head slot. This is a separate route from using the held item. [Equipment defaults][equippable] · [Dispenser dispatch][dispatch] · [Target and empty-slot checks][dispenser-target] · [Equipment placement][dispense]

Its armor contribution uses the shared [Armor and damage reduction](../mechanics/Armor.md) rules. No Headgear-specific attack, knockback or status-effect bonus is registered in the reviewed active code. Leather-based attributes also do **not** give it the [Leather Cap's](LeatherCap.md) tag-based freezing protection, dyeing or armor-trim eligibility: the Headgear is absent from those bundled item tags. [Registration][item] · [Attributes][attributes] · [Freezing tag][freeze] · [Dye tag][dye] · [Trim tag][trim] · [Head armor tag][head-tag]

## Behavior

The Headgear **does not stack**. The registration's initial `durability(300)` is replaced by the later Leather-helmet setup: Leather's multiplier **5 × helmet base 11 = 55**. The component setter replaces the earlier value; 300 is not this item's final maximum durability. [Registration order][item] · [Armor setup][properties] · [Durability setter][durability] · [Component replacement][component] · [Material][material] · [Slot][slot]

At **55 damage**, ordinary wear retains a broken Headgear stack. Its armor attribute is removed on breakage and excluded by ordinary equipment updates. Repair it before relying on that protection. The shared broken-stack guard also stops its held-use equip action. See [Durability, broken items and repair](../mechanics/Durability.md) for the distinction between keeping the stack and retaining its function. [Retained stack][broken] · [Break callback][break-effects] · [Equipment guard][equipment] · [Use guard][use-guard]

The Leather setup supplies **enchantability 15**, but the Headgear is absent from the bundled head-armor and related enchantable tags. Thus the stat alone does not make normal Leather-helmet enchantments available: ordinary Survival table/book application does not gain Protection, Respiration, Aqua Affinity, Unbreaking or Mending merely from the armor material. See [Enchanting](../enchanting/Enchanting.md) for component and supported-item checks. [Material][material] · [Head membership][head-tag] · [Enchantable head armor][enchant-head] · [Armor grouping][enchant-armor] · [Durability grouping][enchant-durability] · [Support checks][enchant-support] · [Anvil checks][anvil-enchant]

That acquisition limit is separate from the behavior of enchantments already present through Creative or custom data. The shared equipment iterator does not exclude broken stacks from matching damage-protection effects. The intended passive-effect policy remains under [#800 review](https://github.com/HungLo2020/MattMC/issues/800#issuecomment-5961208983); this guide does not claim every passive stops at breakage. See [Protection enchantments](../enchanting/ProtectionEnchantments.md#retained-broken-armor-in-this-snapshot). [Equipment iteration][enchanted-equipment]

## Repair

Use **[Leather](Leather.md)** at an [Anvil](../mechanics/AnvilMechanics.md). Each accepted piece restores up to **13 durability**, one quarter of the final 55-point maximum rounded down, with the usual level cost. **Moose Antler is not its repair material.** [Material repair assignment][properties] · [Repair ingredient][repair-tag] · [Anvil calculation][anvil]

Combining two matching Headgear items is another repair route. Compare [Anvil, Grindstone and crafting-grid repair](../mechanics/Durability.md#choose-a-repair-method) before sacrificing a customized item; those methods preserve different data.

## Notes

- Registry ID: `minecraft:moose_headgear`
- The item comes from bundled content integrated into MattMC; upstream recipes or special effects are not evidence of this snapshot's behavior
- Values describe the default item; custom components and server data packs can differ

Related: [Moose](../mobs/Moose.md) · [Moose Antler](MooseAntler.md) · [Leather Cap](LeatherCap.md) · [Sombrero](Sombrero.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registration order, final component setters, equipment callers, recipe/loot/trade scopes, item-tag membership, enchantment support, break handling and repair. No in-game acquisition, equipping, combat, enchanting, breakage, repair or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1782-L1785
[material]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L8-L11
[slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorType.java#L8-L26
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L459-L466
[attributes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1638-L1639
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[shedding]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityMoose.java#L206-L218
[inventory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L59
[quick-move]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L101-L127
[armor-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ArmorSlot.java#L32-L52
[equipment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3575-L3584
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L173-L191
[swap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L127-L155
[equippable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L178-L189
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L112
[dispenser-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3555-L3572
[dispense]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L15-L36
[freeze]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/freeze_immune_wearables.json
[dye]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/dyeable.json
[trim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/trimmable_armor.json
[head-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/head_armor.json
[durability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L386-L390
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponentMap.java#L128-L143
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[break-effects]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3552
[equipment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2668-L2683
[use-guard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L395
[enchant-head]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/head_armor.json
[enchant-armor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/armor.json
[enchant-durability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[enchant-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L129
[anvil-enchant]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L188
[enchanted-equipment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L177
[repair-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/repairs_leather_armor.json
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L127-L151
