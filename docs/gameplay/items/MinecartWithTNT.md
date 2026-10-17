# Minecart with TNT

A **Minecart with TNT** carries an explosive along rails. Prepare its route and trigger before placing it: a powered Activator Rail can start its fuse, and collisions or damage can also make it explode. [Activation][priming] · [Collision and damage][damage]

## Obtaining

Combine **one [TNT](TNT.md) and one [Minecart](Minecart.md)** to craft **one Minecart with TNT**. This is a shapeless recipe, so the ingredients can occupy any two crafting slots. See the [TNT item guide](TNT.md) for obtaining the explosive ingredient. [Recipe][recipe] · [Recipe loading][loader] · [Shapeless matching][shapeless]

## Usage

Use the item on a rail. Ordinary, Powered, Detector, and Activator Rails are accepted; using it on another block fails. A [Dispenser](Dispenser.md) places the cart if it faces rail, or air with rail below; otherwise it ejects the item. Dispensing this cart places a vehicle, so account for any powered Activator Rail at the destination. [Placement][placement] · [Rail tag][rail-tag] · [Dispenser registration][dispenser-registration] · [Dispenser behavior][dispenser]

A **powered Activator Rail** attempts to start an **80-game-tick fuse**, nominally four seconds at normal tick speed. Removing power does not cancel an active fuse. [Rails](../blocks/Rails.md#activator-rail-effects) owns activation and power control; [Transport](../mechanics/Transport.md) owns route and stop design. [Fuse behavior][priming] · [Default movement callback][old-activation] · [Experimental callback][new-activation]

## Behavior

Do not treat a cart without a visible fuse as harmless. Sufficient horizontal collision, a fall of **three blocks or more**, or a burning-arrow hit can request an explosion. Destructive fire/explosion damage or destructive damage while moving sufficiently fast can start a shorter randomized fuse. The blast also depends on speed; the ordinary TNT block's blast is not a fixed prediction for this vehicle. [Collision and damage][damage] · [Explosion and falls][explosion] · [Igniting damage][ignition]

For recovery, stop the cart and keep it away from ignition. Ordinary non-igniting damage that destroys a stationary or sufficiently slow cart returns **one combined Minecart with TNT item**, subject to `doEntityDrops`; it does not split into TNT and a plain Minecart. A normal Creative attack discards the cart instead. These recovery rules do not guarantee survival of an already primed or exploding cart. [Destruction branch][damage] · [Vehicle recovery][drops]

The **`tntExplodes` gamerule**, default **true**, gates ordinary fuse priming and the actual blast. If a primed cart reaches the explosion path while the rule is false, it is discarded without exploding. Disabling explosions therefore is not a guarantee that the cart can be recovered. [Rule][tnt-rule] · [Priming gate][priming] · [Explosion gate][explosion]

## Notes

* This item is registered as `minecraft:tnt_minecart` and stacks to **one**. [Registration][registration]
* It is listed in ordinary Creative categories; the current [inventory browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) still apply. Seeing it in Survival is not an acquisition route. [Category entry][creative]
* See [TNT](../blocks/TNT.md) for the placed explosive's separate behavior and [Redstone](../redstone/Redstone.md) for trigger components.

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. Active registration, recipe loading, placement, dispenser, movement-to-activation callbacks, fuse, damage, recovery, and gamerule checks were inspected. No in-game fuse, collision, blast, crafting, or dispenser test was run. Data packs and world/server settings can change results.

[priming]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartTNT.java#L135-L165
[damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartTNT.java#L52-L106
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/tnt_minecart.json
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/MinecartItem.java#L27-L67
[rail-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/rails.json
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L461-L466
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java#L25-L68
[old-activation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L55-L69
[new-activation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/NewMinecartBehavior.java#L228-L253
[explosion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartTNT.java#L103-L133
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[tnt-rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameRules.java#L214-L216
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1184-L1186
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1539-L1543
[ignition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartTNT.java#L212-L221
