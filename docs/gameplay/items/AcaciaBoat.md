# Acacia Boat

**Acacia Boat** (`minecraft:acacia_boat`) is a **two-seat** water vehicle without a cargo inventory. The item stacks to **one**. [Item registration][item] · [Capacity][capacity]

## Obtaining

Craft **1 Acacia Boat from 5 [Acacia Planks](AcaciaPlanks.md)** in a [Crafting Table](../blocks/CraftingTable.md). Put planks at the two ends of one row and fill the row below with three more. No shovel is required. [Recipe][recipe] Use Acacia Planks in every occupied slot; another wood type or a mixed-plank pattern will not produce this boat.

This item has a [Creative inventory entry][creative].

## Usage

Use it to travel with another eligible passenger. Follow [placing and rowing](OakBoat.md#placing-and-rowing) for the controls and [carrying another passenger](OakBoat.md#carrying-another-passenger) for mob-loading restrictions. Two seats do not mean every mob can board. [Placement][placement] · [Passenger capacity][capacity]

For cargo, craft **1 Acacia Boat with 1 [Chest](../blocks/Chest.md)** into a [Acacia Boat with Chest](AcaciaBoatWithChest.md). That exchanges the second seat for 27 storage slots; use the crafting grid rather than trying to attach a Chest to a placed boat. [Upgrade recipe][upgrade]

## Behavior

Damaging the placed boat until it breaks returns a fresh matching item when **`doEntityDrops` is enabled**. Creative attacks discard the vehicle instead. See [recovery rules](OakBoat.md#recovering-and-dispensing) before collecting it. [Damage and item drop][damage]

This item supplies **1,200 default furnace burn ticks** through the boat fuel tag. Burning it consumes the item. [Fuel table][fuel] · [Boat tag][boats-tag]

Its [Dispenser](Dispenser.md) placement is registered. Follow the shared [water and fallback conditions](OakBoat.md#recovering-and-dispensing). [Dispenser registration][dispenser]

## Notes

The placed boat is registered separately from its inventory item and returns the matching `acacia_boat` item. [Entity registration][entity]

Related: [Acacia Boat with Chest](AcaciaBoatWithChest.md) · [Oak Boat](OakBoat.md) · [Oak Boat with Chest](OakBoatWithChest.md) · [Transport](../mechanics/Transport.md#choosing-a-vehicle) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `f5473e41dc4af8ced756db517fada27288df07a3`. Registrations, recipes and their loader, fuel tags, and shared vehicle callbacks were checked. No in-game crafting, placement, rowing, cargo, recovery, dispenser, or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1231-L1233
[entity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java#L212-L215
[creative]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1521
[recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/acacia_boat.json
[capacity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L730-L759
[placement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/BoatItem.java#L30-L87
[upgrade]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/acacia_chest_boat.json
[damage]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[fuel]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[boats-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/boats.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L145-L164
