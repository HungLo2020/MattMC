# Oak Boat with Chest

An **Oak Boat with Chest** (`minecraft:oak_chest_boat`) combines **one passenger seat** with **27 storage slots**. Choose it for supplies rather than a second passenger. The item stacks to one.

## Crafting and controls

Combine one [Oak Boat](OakBoat.md) and one [Chest](../blocks/Chest.md) in a shapeless crafting recipe. The recipe uses the ordinary boat item; interacting with a placed boat while holding a Chest is not this crafting operation.

Place it with enough collision-free room, board without Sneak/Crouch, and use your movement controls to row. Sneak/Crouch plus interact opens the storage from outside. While riding, the inventory control opens the vehicle's container. See [Chest-boat storage and controls](OakBoat.md#chest-boat-storage) for the full source-grounded procedure.

The storage contents are saved with the vehicle, but are not owner-locked. Breaking the boat does not package the cargo inside its item: contents drop separately. Unload valuables before recovering it, especially over water. Ordinary damage returns the matching vehicle item when entity drops are enabled; Creative attacks instead discard the vehicle.

## Sources and verification

Source-reviewed on 2026-10-01 at `b81c01943c9f3254e713c365a1dd633392929cb2`; no crafting, storage, rowing, or recovery gameplay test. [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/oak_chest_boat.json), [item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1209-L1212), and [capacity, inventory, and destruction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java).

Related: [Oak Boat](OakBoat.md) · [Transport](../mechanics/Transport.md) · [Items](Items.md)
