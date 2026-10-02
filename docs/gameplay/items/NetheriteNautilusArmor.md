# Netherite Nautilus Armor

**Netherite Nautilus Armor** (`minecraft:netherite_nautilus_armor`) equips the body slot of an adult, tamed [Nautilus](../mobs/Nautilus.md). It adds **11 armor points**, **3 armor toughness** and **0.1 knockback resistance**. These are attributes, not a fixed damage-reduction percentage. [Registration][armor-items] · [Material values][armor-materials] · [Attribute application][armor-attributes]

## Obtaining

It appears in an ordinary equipment category, making it available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival as well as Creative. [Category listing][armor-list]

At a [Smithing Table](../blocks/SmithingTable.md), combine **Diamond Nautilus Armor + a Netherite Upgrade template + one Netherite Ingot** to make one Netherite Nautilus Armor. The addition tag currently contains Netherite Ingot. See [Smithing](../smithing/Smithing.md) for ingredient consumption and retained item data. [Recipe][netherite-recipe] · [Addition tag][netherite-material]

## Usage

Follow the [Nautilus equipment guide](../mobs/Nautilus.md#nautilus-armor): use a dispenser facing a living, tamed adult with an empty body slot. **Ordinary right-click armor application is not enabled** in the checked item properties. The guide also explains recovery with Shears. [Armor properties][armor-properties] · [Interaction default][equip-defaults] · [Dispenser route][dispenser]

The allowed-entity tag also lists Zombie Nautilus, but its ordinary interactions cannot tame it and its equipment slots require a tame adult. That listing is not a normal player equipment route. [Allowed types][armor-tag] · [Slot gate][nautilus] · [Zombie restrictions][zombie]

## Behavior

This item stacks to **one**, has no normal durability component and does not wear down from the animal taking damage. It does not require a repair supply. The Netherite item is also fire-resistant; this is an item property, not immunity for the Nautilus wearing it. [Registration][armor-items] · [Item fire resistance][fire-item] [Properties][armor-properties]

Compare variants in the [Nautilus armor table](../mobs/Nautilus.md#nautilus-armor).

## Notes

- See [Nautilus care and riding](../mobs/Nautilus.md) before preparing an underwater trip
- Related: [Items](Items.md)

## Related armor

[Copper](CopperNautilusArmor.md) · [Iron](IronNautilusArmor.md) · [Golden](GoldenNautilusArmor.md) · [Diamond](DiamondNautilusArmor.md) · [Netherite](NetheriteNautilusArmor.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Checked active item registration, equipment attributes, ordinary listing, bundled acquisition data and actual equipment gates. No in-game equipment, protection, recovery or smithing test was run.

[armor-items]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L2135-L2139
[armor-materials]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L12-L38
[armor-attributes]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[armor-list]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1646-L1650
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Item.java#L504-L518
[equip-defaults]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L178-L189
[dispenser]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L20-L36
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/entity_type/can_wear_nautilus_armor.json
[nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L71-L179
[zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/ZombieNautilus.java#L54-L92
[netherite-recipe]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/recipe/netherite_nautilus_armor_smithing.json
[netherite-material]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[fire-item]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Item.java#L402-L404
