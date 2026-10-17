# Minecart with Chest

A **Minecart with Chest** carries 27 slots of cargo along rails. Use it for freight; choose the plain [Minecart](Minecart.md) when you want to ride. [Storage and interaction][chest]

## Obtaining

Combine **one [Chest](Chest.md) and one [Minecart](Minecart.md)** to craft **one Minecart with Chest**. This is a shapeless recipe: the two ingredients can occupy any two crafting slots. It specifically uses the ordinary Chest, not a Trapped Chest, Copper Chest, or Ender Chest. [Recipe][recipe] · [Recipe loading][loader] · [Shapeless matching][shapeless]

You can also find placed Chest Minecarts in **Mineshaft corridors** when that structure generates them. The corridor's random chest attempts create a rail and a loot-bearing cart; finding a Mineshaft does not guarantee finding a cart. Empty its cargo before collecting it using the recovery rules below. [Structure selection][mineshaft-set] · [Cart generation][mineshaft-cart] · [Random attempts][mineshaft-attempts]

## Usage

Use the item on a supported rail. Ordinary, Powered, Detector, and Activator Rails all accept it; use on a non-rail block fails. A [Dispenser](Dispenser.md) places it when there is rail directly in front, or air in front with rail below; otherwise it ejects the item. [Placement][placement] · [Rail tag][rail-tag] · [Dispenser registration][dispenser-registration] · [Dispenser behavior][dispenser]

Interact with the cart to open its **three-row, 27-slot inventory**. A stationary [Hopper](../blocks/Hopper.md) can transfer to or from a cart within its container search area; allow room and time at a loading stop. For route construction and stopping, follow [Transport](../mechanics/Transport.md#starting-and-stopping-a-cart). For a fullness monitor, see [Detector Rail output](../blocks/Rails.md#detector-rail-output). [Inventory][chest] · [Container access][container-access] · [Hopper container lookup][hopper-containers]

## Behavior

Ordinary Survival damage that destroys the cart returns **one combined Minecart with Chest item** when `doEntityDrops` is enabled. It does not split into a Chest and a plain Minecart. Stored items spill separately; the recovered cart item is not a packed inventory. Creative attacks discard the vehicle instead of returning its cart item, so unload valuables first. [Matching drop][chest] · [Vehicle recovery][drops] · [Cargo removal][container-drops]

Cargo fullness affects the container cart's natural slowdown, so test a freight stop with its intended load. Movement also depends on the world's selected minecart implementation; [Transport](../mechanics/Transport.md#why-another-railway-design-may-behave-differently) owns those route and setting differences. [Cargo slowdown][slowdown] · [Movement selection][movement]

## Notes

* This item is registered as `minecraft:chest_minecart` and stacks to **one**. [Registration][registration]
* It is listed in ordinary Creative categories. Catalog visibility alone is not a Survival acquisition route; see the current [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Category entry][creative]
* See [Rails](../blocks/Rails.md) for track support, propulsion, detection, and activation, and [Redstone](../redstone/Redstone.md) for connected components.

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. Active registration, recipe loading, structure creation, placement, dispenser, container, and recovery paths were inspected. No in-game crafting, Mineshaft search, freight, or dispenser test was run. Data packs and server/world settings can change the bundled behavior.

[chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartChest.java#L28-L72
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chest_minecart.json
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[mineshaft-set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/mineshafts.json
[mineshaft-cart]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L339-L358
[mineshaft-attempts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L392-L400
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/MinecartItem.java#L27-L67
[rail-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/rails.json
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L461-L466
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java#L25-L68
[container-access]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/ContainerEntity.java#L81-L94
[hopper-containers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L344-L393
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[container-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecartContainer.java#L34-L80
[slowdown]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecartContainer.java#L100-L113
[movement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L97-L104
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1178-L1180
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1539-L1543
