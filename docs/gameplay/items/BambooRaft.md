# Bamboo Raft

**Bamboo Raft** (`minecraft:bamboo_raft`) is a **two-seat** water vehicle without a cargo inventory. The item stacks to **one**. [Item registration][item] · [Capacity][capacity]

## Obtaining

Craft **1 Bamboo Raft from 5 [Bamboo Planks](BambooPlanks.md)** in a [Crafting Table](../blocks/CraftingTable.md). Put planks at the two ends of one row and fill the row below with three more. No shovel is required. [Recipe][recipe]

The recipe uses Bamboo Planks specifically. Raw [Bamboo](Bamboo.md), [Block of Bamboo](BlockOfBamboo.md), and [Bamboo Mosaic](BambooMosaic.md) cannot replace those five planks. [Recipe][recipe]

This item has a [Creative inventory entry][creative].

## Usage

Use it to travel with another eligible passenger. Follow [placing and rowing](OakBoat.md#placing-and-rowing) for the controls and [carrying another passenger](OakBoat.md#carrying-another-passenger) for mob-loading restrictions. Two seats do not mean every mob can board. [Placement][placement] · [Passenger capacity][capacity]

For cargo, craft **1 Bamboo Raft with 1 [Chest](../blocks/Chest.md)** into a [Bamboo Raft with Chest](BambooRaftWithChest.md). That exchanges the second seat for 27 storage slots; use the crafting grid rather than trying to attach a Chest to a placed raft. [Upgrade recipe][upgrade]

## Behavior

Damaging the placed raft until it breaks returns a fresh matching item when **`doEntityDrops` is enabled**. Creative attacks discard the vehicle instead. See [recovery rules](OakBoat.md#recovering-and-dispensing) before collecting it. [Damage and item drop][damage]

This item supplies **1,200 default furnace burn ticks** through the boat fuel tag. Burning it consumes the item. [Fuel table][fuel] · [Boat tag][boats-tag]

Its [Dispenser](Dispenser.md) placement is registered. Follow the shared [water and fallback conditions](OakBoat.md#recovering-and-dispensing). [Dispenser registration][dispenser]

## Notes

The placed raft is registered separately from its inventory item and returns the matching `bamboo_raft` item. [Entity registration][entity]

Related: [Bamboo Raft with Chest](BambooRaftWithChest.md) · [Oak Boat](OakBoat.md) · [Oak Boat with Chest](OakBoatWithChest.md) · [Transport](../mechanics/Transport.md#choosing-a-vehicle) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `f5473e41dc4af8ced756db517fada27288df07a3`. Registrations, recipes and their loader, fuel tags, and shared vehicle callbacks were checked. No in-game crafting, placement, rowing, cargo, recovery, dispenser, or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1261-L1263
[entity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java#L297-L300
[creative]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1531
[recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/bamboo_raft.json
[capacity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L730-L759
[placement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/BoatItem.java#L30-L87
[upgrade]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/bamboo_chest_raft.json
[damage]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[fuel]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[boats-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/boats.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L145-L164
