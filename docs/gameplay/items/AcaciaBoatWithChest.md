# Acacia Boat with Chest

## Obtaining

The Acacia Boat with Chest is crafted by combining an Acacia Boat with a Chest. It can also be obtained from MattMC's JEI-style combined creative menu.

In Survival with entity drops enabled, breaking an Acacia Boat with Chest returns its matching boat-with-chest item and spills its cargo as separate item entities. **The dropped boat item does not preserve its stored contents.** [Chest-boat destruction](https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L64-L75) · [Fresh vehicle-item drop](https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L68-L74)

## Usage

The Acacia Boat with Chest is used for travel while carrying extra items. Place it, ride it like a normal boat, and open its storage while near it.

The chest occupies the boat's passenger seat, so the boat can carry only one entity instead of the two entities a regular boat can carry.

An Acacia Boat with Chest can also be used as furnace fuel, lasting 60 seconds and smelting up to 6 items.

## Behavior

It behaves like an Acacia Boat with attached container storage. The inventory has the same number of slots as a single chest.

The storage can be accessed by sneaking and interacting with the boat, by interacting with the chest portion when another entity is riding it, or by opening the player's inventory while riding it.

Hoppers can insert into or extract from a boat with chest. Because of the entity's size, one can interact with multiple hoppers at once when positioned over them.

Opening or breaking a boat with chest can anger nearby piglins, as with other chest containers.

## Notes

* This item is registered as `minecraft:acacia_chest_boat`.
* In MattMC, the creative tab menu has been replaced with a JEI-style combined menu.
* It shares most movement and damage behavior with regular boats.
