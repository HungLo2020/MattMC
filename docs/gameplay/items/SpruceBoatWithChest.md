# Spruce Boat with Chest

**Spruce Boat with Chest** (`minecraft:spruce_chest_boat`) carries **one rider and 27 storage slots**. The item stacks to **one**. [Item registration][item] · [Capacity][chest]

## Obtaining

Combine **1 [Spruce Boat](SpruceBoat.md) and 1 [Chest](../blocks/Chest.md)** to make **1 Spruce Boat with Chest**. This shapeless recipe fits the inventory crafting grid; the ingredients can occupy either position. It requires the Spruce Boat specifically, rather than any boat or raft. [Recipe][recipe]

This item has a [Creative inventory entry][creative].

## Usage

Place the vehicle in clear space, board without Sneak/Crouch, and row with your movement controls. Use **Sneak/Crouch + interact** to open storage from outside, or your **inventory control while riding**. Follow the shared [chest-boat storage guide](OakBoat.md#chest-boat-storage) for access and persistence, and [rowing guide](OakBoat.md#placing-and-rowing) for movement. [Placement][placement]

Choose the ordinary [Spruce Boat](SpruceBoat.md) when you need a second passenger; the chest version has only one seat. [Seat limit][chest]

## Behavior

Damaging the placed boat until it breaks returns a fresh matching item when **`doEntityDrops` is enabled**. Creative attacks discard the vehicle instead. **Cargo drops separately; it is not packed inside the recovered item.** Unload valuable supplies before breaking the vehicle. [Damage and item drop][damage] · [Cargo removal][cargo]

This item supplies **1,200 default furnace burn ticks** through the chest-boat tag nested inside the boat fuel tag. Burning it consumes the item. [Fuel table][fuel] · [Boat tag][boats-tag] · [Chest-boat tag][chest-tag]

Its [Dispenser](Dispenser.md) placement is registered. Follow the shared [water and fallback conditions](OakBoat.md#recovering-and-dispensing). [Dispenser registration][dispenser]

## Notes

The placed boat is registered separately from its inventory item and returns the matching `spruce_chest_boat` item. [Entity registration][entity]

Related: [Spruce Boat](SpruceBoat.md) · [Oak Boat](OakBoat.md) · [Oak Boat with Chest](OakBoatWithChest.md) · [Transport](../mechanics/Transport.md#choosing-a-vehicle) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `f5473e41dc4af8ced756db517fada27288df07a3`. Registrations, recipes and their loader, fuel tags, and shared vehicle callbacks were checked. No in-game crafting, placement, rowing, cargo, recovery, dispenser, or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1216-L1218
[entity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java#L1329-L1336
[creative]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1516
[recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/spruce_chest_boat.json
[chest]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L30-L49
[placement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/BoatItem.java#L30-L87
[damage]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[cargo]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L63-L76
[fuel]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[boats-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/boats.json
[chest-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/chest_boats.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L145-L164
