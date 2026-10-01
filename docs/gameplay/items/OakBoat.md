# Oak Boat

An **Oak Boat** is a reusable, two-seat vehicle for rowing across water. Choose the ordinary boat when you need another passenger; combine it with a Chest when you want **27 storage slots** and only one rider. This page covers the Oak Boat and the shared controls used by its chest variant. [Passenger limit][passengers] · [Chest capacity][chest-size]

## Crafting and obtaining

Craft **one Oak Boat from five [Oak Planks](OakPlanks.md)** in a [Crafting Table](../blocks/CraftingTable.md). Place a plank in each side of one row, with three planks across the row below. The recipe specifically requires Oak Planks, so mixing wood types does not make this item. No shovel is required. [Boat recipe][recipe]

The item is registered as `minecraft:oak_boat`, stacks to **one**, and has a Creative entry. Its item registration creates the active Oak Boat entity when placed; it is more than a decorative inventory item. [Item registration][registration] · [Entity registration][entity] · [Creative entry][creative]

For storage, combine **one Oak Boat and one [Chest](../blocks/Chest.md)** in a shapeless recipe to obtain an [Oak Boat with Chest](OakBoatWithChest.md). This is a crafting operation, rather than an instruction to use a Chest on an already placed boat. [Chest-boat recipe][chest-recipe]

## Placing and rowing

1. Hold the boat and use it while aiming at the water's surface. Placement needs a valid target and enough collision-free room for the boat
2. Interact with the placed boat without holding your Sneak/Crouch control to board
3. Use your forward, backward, left, and right movement controls to row and turn
4. Press your Sneak/Crouch control to dismount near a suitable landing place

The placement ray includes fluids, but the item is not restricted to placement on water: a clear solid surface can also accept it. The boat's registered width is **1.375 blocks**, so do not plan a waterway around a one-block-wide opening. [Placement][placement] · [Entity size][entity] · [Boarding][boarding] · [Input wiring][input] · [Dismount control][dismount-control] · [Dismount action][dismount]

Forward input supplies stronger thrust than backward input. Turning also changes the boat's heading; looking somewhere else alone is not the rowing control. Releasing movement lets water friction reduce speed rather than applying an instant stop. Leave room to slow down before a dock. [Rowing][rowing] · [Water movement][float]

## Carrying another passenger

A normal boat accepts **two passengers**. The first passenger in its controlling seat supplies player steering. When a player joins a boat whose first passenger is a non-player, the server puts the player first, allowing a player to steer an already loaded animal. [Capacity and control][passengers] · [Passenger ordering][ordering]

Nearby eligible living mobs can be picked up by an unoccupied or non-player-controlled boat. They need a free seat, must not already be riding something, and must be narrower than the boat. The automatic pickup does not run while a player controls it, so load the animal before boarding. Some types are explicitly excluded, including fish, dolphins, squid, and players; this is not a way to scoop up every mob. [Automatic pickup][pickup] · [Width check][width] · [Excluded types][excluded]

A boat cannot accept new passengers while its eye position is underwater. Remaining submerged for **60 ticks**, approximately three seconds at normal tick speed, ejects existing passengers. Downward bubble columns can also eject them, while upward columns can launch the boat. Keep ordinary passenger routes on the surface and away from bubble-column hazards. [Boarding restriction][passengers] · [Submersion][submersion] · [Bubble columns][bubbles]

## Chest-boat storage

The chest variant trades the second seat for **27 slots**, equivalent to a three-row container. To open it from outside, use Sneak/Crouch plus interact. While riding, press your inventory control: the client requests the vehicle inventory, and the server opens the chest boat's menu. [Chest interaction][chest-interact] · [Inventory-key handling][inventory-key] · [Vehicle inventory selection][inventory-select] · [Server inventory request][inventory-server]

The contents are saved and loaded through the chest-vehicle container helpers. This is ordinary mobile storage, not an ownership-locked pet inventory: the open-menu paths do not check who placed the boat. [Inventory persistence][chest-save] · [Container save and load][container-save] · [Menu access][chest-interact]

Breaking the chest boat does **not** pack its contents into the dropped boat item. Its container contents drop separately, so unload valuable cargo before recovering the vehicle, especially over deep water. [Chest destruction][chest-destroy] · [Vehicle item drop][drop]

## Recovering and dispensing

Damage can destroy a placed boat and return the matching boat item when `doEntityDrops` is enabled. Creative attacks use a discard path instead, so they are not a reliable way to collect the vehicle item. The normal drop path creates a fresh matching item and preserves the boat's custom name. [Damage handling][damage] · [Item recovery][drop]

Both Oak Boat variants have registered [Dispenser](Dispenser.md) behavior. A Dispenser places the boat when its front block contains water, or when that block is air with water below. Otherwise it falls back to dispensing the boat as an item. [Dispenser registration][dispenser-reg] · [Dispenser conditions][dispenser]

## Related pages

- [Transport](../mechanics/Transport.md): choosing a route, rail propulsion, and stopping safely
- [Oak Boat with Chest](OakBoatWithChest.md)
- [Minecart](Minecart.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Registrations, bundled recipes and their active serializers, placement and rowing callbacks, passenger handling, inventory access and persistence, destruction, and dispenser wiring were inspected. No in-game crafting, rowing, passenger, storage, or dispenser test was run.

[passengers]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L730-L749
[chest-size]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L30-L49
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/oak_boat.json
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1209-L1212
[entity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L954-L965
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1513-L1514
[chest-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/oak_chest_boat.json
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BoatItem.java#L30-L87
[boarding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L681-L691
[input]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/player/LocalPlayer.java#L848-L857
[dismount-control]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L314
[dismount]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L451-L459
[rowing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L571-L600
[float]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L528-L569
[ordering]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2353-L2369
[pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L270-L288
[width]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L606-L608
[excluded]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/cannot_be_pushed_onto_boats.json
[submersion]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L205-L217
[bubbles]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L303-L324
[chest-interact]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L78-L103
[inventory-key]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/Minecraft.java#L2050-L2057
[inventory-select]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L525-L527
[inventory-server]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1638-L1642
[chest-save]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L51-L61
[container-save]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/ContainerEntity.java#L60-L79
[chest-destroy]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L63-L76
[damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L57
[drop]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L68-L75
[dispenser-reg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L145-L164
[dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/BoatDispenseItemBehavior.java#L22-L52
