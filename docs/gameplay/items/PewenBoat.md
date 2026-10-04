# Pewen Boat

**Pewen Boat** (`minecraft:pewen_boat`) is a **two-seat** water vehicle without a cargo inventory. The item stacks to **one**. [Item registration][item] · [Capacity][capacity]

## Obtaining

If you already have the planks, craft **1 Pewen Boat from 5 [Pewen Planks](PewenPlanks.md)** in a [Crafting Table](../blocks/CraftingTable.md). Put planks at the two ends of one row and fill the row below with three more. No shovel is required. [Recipe][recipe]

Obtaining the inputs is a separate limitation: the bundled log-to-plank recipe has an incompatible ingredient format. See [Pewen Planks](PewenPlanks.md#obtaining) and the [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution) before planning a Survival crafting route. [Planks definition][planks-recipe]

This item has a [Creative inventory entry][creative].

## Usage

Use it to travel with another eligible passenger. Follow [placing and rowing](OakBoat.md#placing-and-rowing) for the controls and [carrying another passenger](OakBoat.md#carrying-another-passenger) for mob-loading restrictions. Two seats do not mean every mob can board. [Placement][placement] · [Passenger capacity][capacity]

The [Pewen Chest Boat](PewenChestBoat.md#obtaining) is separately registered, but its bundled upgrade recipe has the ingredient-format problem described above. Do not assume the ordinary boat can currently be crafted into mobile storage.

## Behavior

Damaging the placed boat until it breaks returns a fresh matching item when **`doEntityDrops` is enabled**. Creative attacks discard the vehicle instead. See [recovery rules](OakBoat.md#recovering-and-dispensing) before collecting it. [Damage and item drop][damage]

**Neither Pewen boat form is furnace fuel with the bundled tags.** Both are absent from the boat and chest-boat fuel groups, and the default fuel table has no separate Pewen-boat entry. [Fuel table][fuel] · [Boat tag][boats-tag] · [Chest-boat tag][chest-tag]

A [Dispenser](Dispenser.md) ejects this item rather than placing its vehicle: Pewen has no boat-placement dispenser registration and uses the default item behavior. Place it by hand. [Boat dispenser registrations][dispenser] · [Fallback selection][default-dispenser] · [Item ejection][ejected-item]

## Notes

The placed boat is registered separately from its inventory item and returns the matching `pewen_boat` item. [Entity registration][entity]

Related: [Pewen Chest Boat](PewenChestBoat.md) · [Oak Boat](OakBoat.md) · [Oak Boat with Chest](OakBoatWithChest.md) · [Transport](../mechanics/Transport.md#choosing-a-vehicle) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `f5473e41dc4af8ced756db517fada27288df07a3`. Registrations, recipes and their loader, fuel tags, and shared vehicle callbacks were checked. No in-game crafting, placement, rowing, cargo, recovery, dispenser, or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1267-L1269
[entity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java#L995-L1002
[creative]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1533
[recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/pewen_boat.json
[capacity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L730-L759
[planks-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/pewen_planks.json
[placement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/BoatItem.java#L30-L87
[damage]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[fuel]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[boats-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/boats.json
[chest-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/chest_boats.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L145-L164
[default-dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L112
[ejected-item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L21-L46
