# Golden Nautilus Armor

**Golden Nautilus Armor** (`minecraft:golden_nautilus_armor`) equips the body slot of an adult, tamed [Nautilus](../mobs/Nautilus.md). It adds **7 armor points**, **0 armor toughness** and **0 knockback resistance**. These are attributes, not a fixed damage-reduction percentage. [Registration][armor-items] · [Material values][armor-materials] · [Attribute application][armor-attributes]

## Obtaining

It appears in an ordinary equipment category, making it available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival as well as Creative. [Category listing][armor-list]

No bundled crafting recipe, chest loot, entity loot or merchant offer was found for this armor. Its verified acquisition route is the ordinary inventory browser; its material name does not imply a recipe from ingots or gems.

## Usage

Follow the [Nautilus equipment guide](../mobs/Nautilus.md#nautilus-armor): use a dispenser facing a living, tamed adult with an empty body slot. **Ordinary right-click armor application is not enabled** in the checked item properties. The guide also explains recovery with Shears. [Armor properties][armor-properties] · [Interaction default][equip-defaults] · [Dispenser route][dispenser]

The allowed-entity tag also lists Zombie Nautilus, but its ordinary interactions cannot tame it and its equipment slots require a tame adult. That listing is not a normal player equipment route. [Allowed types][armor-tag] · [Slot gate][nautilus] · [Zombie restrictions][zombie]

## Behavior

This item stacks to **one**, has no normal durability component and does not wear down from the animal taking damage. It does not require a repair supply. [Properties][armor-properties]

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
