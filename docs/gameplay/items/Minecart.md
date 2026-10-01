# Minecart

A **Minecart** is the basic one-passenger rail vehicle. Place it on track, board it, and use powered sections to keep it moving. It follows the rails; the rider's movement keys provide only a small low-speed nudge, not boat-like steering. For a practical launch-and-stop layout, see [Transport](../mechanics/Transport.md#starting-and-stopping-a-cart). [Placement][placement] · [Boarding][boarding] · [Rider input][input]

## Crafting and obtaining

Craft **one Minecart from five [Iron Ingots](IronIngot.md)** in a [Crafting Table](../blocks/CraftingTable.md). Put one ingot on each side of a row and three across the row below, leaving the center of the upper row empty. [Recipe][recipe]

The active item is `minecraft:minecart`, stacks to **one**, and is listed in Creative. Its registration points to the rideable Minecart entity. Rails are a separate expense: the cart recipe does not produce any track. [Item registration][registration] · [Entity registration][entity] · [Creative entry][creative]

## Placing a cart

Use the held item on a rail block. Ordinary [Rail](Rail.md), [Powered Rail](PoweredRail.md), [Detector Rail](DetectorRail.md), and [Activator Rail](ActivatorRail.md) are all accepted by the bundled rail tag. Using the Minecart item on a non-rail block fails. The placement code adjusts its height for ascending track. [Placement checks][placement] · [Accepted rail types][rail-tag]

The rail must itself remain supported. Removing the block beneath track, or the supporting block at an ascending rail's high end, can remove the rail and interrupt the route. A cart that comes off the track uses separate off-rail motion and loses speed on the ground. [Rail support][support] · [Off-track movement][off-track]

A [Dispenser](Dispenser.md) can also place a Minecart. It needs a rail directly in front, or air directly in front with a rail below. Otherwise it dispenses the Minecart as an item. This behavior is explicitly registered for the plain Minecart. [Dispenser registration][dispenser-reg] · [Dispenser placement][dispenser]

## Riding and moving

Interact with an **empty** cart without holding Sneak/Crouch to board. A plain Minecart has one seat. Press your Sneak/Crouch control to leave it; MattMC's default binding is **Left Ctrl**, while Left Shift is Sprint. Use the action shown in your own controls if you have rebound it. [Boarding][boarding] · [Passenger limit][capacity] · [Dismount control][dismount-control] · [Dismount action][dismount] · [Default bindings][keys] · [Control key mapping][control-key]

On a slow or stationary cart, face along the track and press Forward to give it a small nudge. The server derives that input from your facing and movement controls. Once moving, slopes and powered rails matter more than holding Forward: rider thrust is only added below the low-speed threshold. Track connections determine the route through curves. [Input direction][input-direction] · [Low-speed assistance][input]

Powered rails have two different jobs:

- **Powered:** boost a moving cart, or launch a nearly stopped cart away from a suitable backing block on a flat rail
- **Unpowered:** slow the cart sharply and stop it once it is slow enough

A powered rail without a starting direction does not automatically launch a stationary cart. At a stop, release movement input: low-speed rider input can suppress the braking step. See [Transport](../mechanics/Transport.md) for rail selection and station planning. [Boost and launch][boost] · [Braking and rider exception][input]

## Passengers and unloading

Under the default movement model, an empty moving rideable cart can pick up nearby eligible entities when its horizontal speed is at least **0.1 blocks per tick**. The pickup excludes players, Iron Golems, other minecarts, and entities already riding something. Load a mob into the cart before expecting it to serve as that mob's transport; an occupied cart has no second seat for you. [Pickup conditions][pickup] · [Passenger limit][capacity]

A **powered Activator Rail ejects the passenger** from a plain Minecart. It is useful for unloading, but does not provide the acceleration of a Powered Rail. Keep it out of a passenger route unless dismounting there is intentional. Its ejection callback is invoked by the active rail-movement code. [Passenger ejection][eject] · [Default callsite][eject-call]

## Recovering or converting a cart

Destroying a placed Minecart through ordinary damage drops a Minecart item when `doEntityDrops` is enabled. Creative attacks instead discard the vehicle. The normal item-drop path preserves its custom name. [Damage handling][damage] · [Drop item][drop-item] · [Drop conditions][drop]

The base item is also an ingredient for separate utility vehicles. For example, Minecart plus Chest crafts a [Minecart with Chest](MinecartWithChest.md), and Minecart plus Hopper crafts a [Minecart with Hopper](MinecartWithHopper.md). Both are shapeless recipes. They produce different vehicle items; the one-seat boarding instructions above describe the plain Minecart. [Chest recipe][chest-recipe] · [Hopper recipe][hopper-recipe]

## World settings matter

Default worlds use the legacy minecart movement implementation. The optional `minecart_improvements` feature selects a different one. In default movement, the code's speed limit is **0.4 blocks per tick**, or eight blocks per second at 20 ticks per second, and halves in water. These are source limits, not a measured journey speed. Occupancy, track layout, slopes, and power affect what a cart actually achieves. [Default feature set][defaults] · [Movement selection][selection] · [World feature check][feature-check] · [Legacy speed and friction][speed]

The `minecartMaxSpeed` game rule belongs to the experimental feature. Do not assume changing or quoting that rule describes an ordinary world's rail behavior. [Experimental rule][rule] · [Experimental speed][experimental-speed]

## Related pages

- [Transport](../mechanics/Transport.md)
- [Oak Boat](OakBoat.md)
- [Rail](Rail.md)
- [Powered Rail](PoweredRail.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Registrations, recipe loading and serializers, rail placement, riding callbacks, default and experimental movement selection, passenger pickup/ejection, item recovery, and dispenser behavior were inspected. No in-game crafting, riding, speed, station, or dispenser test was run.

[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/MinecartItem.java#L27-L67
[boarding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/Minecart.java#L23-L35
[input]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L161-L185
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/minecart.json
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1175-L1177
[entity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L910-L912
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1539-L1543
[rail-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/rails.json
[support]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BaseRailBlock.java#L58-L108
[off-track]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L352-L364
[dispenser-reg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L461-L466
[dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java#L25-L68
[capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2386-L2388
[dismount-control]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L314
[dismount]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L451-L459
[keys]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/Options.java#L555-L565
[control-key]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/blaze3d/platform/InputConstants.java#L455-L460
[input-direction]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2159-L2163
[boost]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L242-L271
[pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L369-L395
[eject]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/Minecart.java#L47-L61
[eject-call]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L55-L69
[damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L57
[drop-item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/Minecart.java#L37-L45
[drop]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L68-L75
[chest-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/chest_minecart.json
[hopper-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/hopper_minecart.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/flag/FeatureFlags.java#L33-L41
[selection]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L97-L104
[feature-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L577-L579
[speed]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L410-L418
[rule]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/GameRules.java#L209-L213
[experimental-speed]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/NewMinecartBehavior.java#L467-L470
