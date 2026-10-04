# Sombrero

The **Sombrero** is a **head-slot wearable** with a stack limit of **one**. Its default registration supplies **no armor, toughness, knockback-resistance bonus or durability**. It occupies the same slot as a protective helmet, so consider that equipment trade-off before wearing it. [Registration][item] · [Shared defaults][defaults] · [Head-slot component][properties]

## Obtaining

**No bundled Survival acquisition route was found.** The checked recipe and loot-table resources contain no Sombrero recipe or drop, and the active Java references establish no trade or mob reward. [Recipes][recipes] · [Loot tables][loot] · [Trades][trades] · [Registration][item]

The Sombrero is also **absent from the ordinary category list** used by MattMC's [inventory item browser](../mechanics/InventoryBrowser.md#which-items-appear). Merely being registered does not put it in that catalog. [Category list][categories] · [Browser assembly][browser]

A player with **permission level 2** can request the registered item with `/give @s minecraft:sombrero 1`. This is a command-supplied item, not a Survival crafting recipe. See [Commands](../commands/Commands.md) for permissions and self-targeting. [Give syntax and permission][give] · [Registry entry][item]

## Usage

To wear a Sombrero already in your inventory:

1. Open your inventory and place it in the **head slot**, removing the existing helmet first
2. Alternatively, **shift-click** it from your main inventory or hotbar while the head slot is empty

Both routes use the item's head-slot equipment component. The inventory's slot checks do not require the held-use swap flag. [Inventory slots][inventory] · [Shift-click route][quick-move] · [Slot acceptance][armor-slot] · [Equipment-slot selection][equipment-slot]

**Using the Sombrero while holding it does not equip it**, even into an empty head slot. Its `swappable=false` setting skips the shared held-use equipment action. This flag does not prevent inventory placement, and it does not lock the default Sombrero onto your head: ordinary inventory removal remains available. Added enchantments that prevent equipment changes are a separate restriction. [Registration][item] · [Flag setup][properties] · [Held-use check][use] · [Removal check][armor-slot]

A **Dispenser** can also equip it onto an eligible living target in the block directly ahead whose head slot is empty. The default equipment component allows dispensing, and this route checks that flag and the target's eligibility rather than the held-use swap flag. If no eligible target is found, the normal item-dispensing behavior is used. [Equipment defaults][equippable] · [Dispenser dispatch][dispatch] · [Target eligibility][dispenser-target] · [Placement and fallback][dispense]

## Behavior

The Sombrero registers no food, special-use effect, custom attack benefit or protective attribute. It also lacks maximum-damage and current-damage components, so the ordinary durability system does not wear it down into a broken item. This is not an extra defensive benefit: wearing it supplies **zero armor points**. [Registration][item] · [Default components][defaults] · [Damageability test][damageable]

There is **no default repair material or durability to restore**. The item lacks an enchantability component and is absent from the bundled head-armor/enchantable groups, so ordinary table enchanting and Survival enchanted-book application do not give it the usual helmet enchantments. Creative/custom items and data packs can differ. See [Enchanting](../enchanting/Enchanting.md), [Anvil mechanics](../mechanics/AnvilMechanics.md) and [Durability and repair](../mechanics/Durability.md). [Registration][item] · [Table eligibility][enchantable] · [Head armor tag][head-tag] · [Enchantable equipment tag][enchant-equippable] · [Durability tag][enchant-durability] · [Supported-item check][enchant-support] · [Anvil check][anvil]

Ordinary use on another living entity does not enable the shared direct-equip interaction: `equip_on_interact` is false by default. This is separate from inventory placement and the Dispenser route. [Equipment defaults][equippable] · [Living-entity item interaction][interact]

## Notes

- Registry ID: `minecraft:sombrero`
- The item comes from bundled content integrated into MattMC; its name does not establish an upstream recipe or mob interaction
- Default equipment behavior is source-reviewed; a particular worn appearance is not certified by this guide

Related: [Moose Headgear](MooseHeadgear.md) · [Leather Cap](LeatherCap.md) · [Armor and damage reduction](../mechanics/Armor.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item registration, resource/category gaps, inventory and held-use callers, Dispenser handling, default components and enchantment support. No command, inventory-browser, equipping, dispensing, enchanting, combat or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1529
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L423-L429
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[categories]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[give]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/GiveCommand.java#L23-L49
[inventory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L59
[quick-move]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L101-L127
[armor-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ArmorSlot.java#L32-L52
[equipment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3575-L3584
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L173-L191
[equippable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L178-L189
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L112
[dispenser-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3555-L3572
[dispense]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L15-L36
[damageable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L450
[enchantable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L970
[head-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/head_armor.json
[enchant-equippable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/equippable.json
[enchant-durability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[enchant-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L179-L180
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L188
[interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L591-L604
