# Minecart with Hopper

A **Minecart with Hopper** is a five-slot moving collector. Use it to gather items along a rail route, then unload at a stationary Hopper or open its inventory yourself. [Cart inventory and collection][hopper] · [Container access][container-access] · [Hopper container lookup][hopper-containers]

## Obtaining

Combine **one [Hopper](Hopper.md) and one [Minecart](Minecart.md)** to craft **one Minecart with Hopper**. The bundled recipe is shapeless; put the two ingredients in any two crafting slots. Craft the Hopper separately using its [ingredient guide](../blocks/Hopper.md#crafting-and-collecting). [Recipe][recipe] · [Recipe loading][loader] · [Shapeless matching][shapeless]

## Usage

Use the item on a rail, not on bare ground. The bundled rail tag accepts ordinary, Powered, Detector, and Activator Rails. A [Dispenser](Dispenser.md) places the cart onto rail directly in front, or onto rail below an air block in front; without that track arrangement it ejects the item. [Placement][placement] · [Rail tag][rail-tag] · [Dispenser registration][dispenser-registration] · [Dispenser behavior][dispenser]

Interact to open the **five inventory slots**. While enabled, the cart tries to pull from an accessible container above and collect dropped items in its pickup regions, subject to available space and container slot rules. It does not perform the placed Hopper's outward push step: use a receiving [Hopper](../blocks/Hopper.md#point-the-output-correctly) to extract cargo. [Cart collection][hopper] · [Pull and pickup checks][hopper-transfer] · [Container access][container-access] · [Receiving Hopper lookup][hopper-containers]

## Behavior

A **powered Activator Rail disables the cart's own collection**; an **unpowered Activator Rail enables it again**. That enabled state stays with the cart after it leaves the rail and is saved. Ordinary track does not reset it. If collection stops, check this state as well as inventory space. This does not lock other Hoppers out of its inventory. See [Activator Rail effects](../blocks/Rails.md#activator-rail-effects) for the shared control rules. [Activation and saved state][hopper] · [Default movement callback][old-activation] · [Experimental callback][new-activation] · [External container access][hopper-containers]

When ordinary Survival damage destroys it, the cart returns **one combined Minecart with Hopper item** if `doEntityDrops` is enabled. Cargo spills separately rather than staying inside that item. Creative attacks discard the vehicle without returning its cart item; empty it before dismantling the line. [Matching drop][hopper] · [Vehicle recovery][drops] · [Cargo removal][container-drops]

## Notes

* This item is registered as `minecraft:hopper_minecart` and stacks to **one**. [Registration][registration]
* It is listed in ordinary Creative categories; the current [inventory browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) still apply. Seeing the entry is not a Survival source. [Category entry][creative]
* Use [Transport](../mechanics/Transport.md) for routes and stops, [Detector Rail output](../blocks/Rails.md#detector-rail-output) for fullness readings, and [Redstone](../redstone/Redstone.md) for the surrounding circuit. The placed Hopper's transfer cooldown is not a rate specification for this vehicle.

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. Active registration, recipe loading, placement, dispenser, collection and activation callbacks, saved state, and recovery were inspected. No in-game collection, unloading, timing, crafting, or dispenser test was run. Tags, data packs, and server/world settings can change results.

[hopper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartHopper.java#L41-L143
[container-access]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/ContainerEntity.java#L81-L94
[hopper-containers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L344-L393
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/hopper_minecart.json
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/MinecartItem.java#L27-L67
[rail-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/rails.json
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L461-L466
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java#L25-L68
[hopper-transfer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L218-L260
[old-activation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L55-L69
[new-activation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/NewMinecartBehavior.java#L228-L253
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[container-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecartContainer.java#L34-L80
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1187-L1189
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1539-L1543
