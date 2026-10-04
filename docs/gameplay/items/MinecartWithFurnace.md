# Minecart with Furnace

A **Minecart with Furnace** uses Coal or Charcoal for propulsion. Its furnace is a motor: interacting fuels the cart rather than opening a smelting inventory. Use the placed [Furnace](../blocks/Furnace.md) for cooking and smelting. [Fuel interaction][fuel]

## Obtaining

Combine **one [Furnace](Furnace.md) and one [Minecart](Minecart.md)** to craft **one Minecart with Furnace**. This is a shapeless recipe; put the ingredients in any two crafting slots. It requires the ordinary Furnace, not a Blast Furnace or Smoker. [Recipe][recipe] · [Recipe loading][loader] · [Shapeless matching][shapeless]

## Usage

Place it by using the item on a rail. Ordinary, Powered, Detector, and Activator Rails all accept minecart placement; bare ground does not. A [Dispenser](Dispenser.md) can place this variant with rail in front, or air in front and rail below; otherwise it ejects the item. [Placement][placement] · [Rail tag][rail-tag] · [Dispenser registration][dispenser-registration] · [Dispenser behavior][dispenser]

Hold **[Coal](Coal.md) or [Charcoal](Charcoal.md)** and interact with the cart from the side opposite your intended starting direction. An accepted fuel item adds **3,600 game ticks** of fuel, three minutes at the normal 20 ticks per second, and sets its push direction horizontally away from you. Wood and Coal Blocks are not in the bundled fuel tag. [Accepted fuels][fuel-tag] · [Fuel and direction][fuel]

A refuel is accepted only if the new total would be at most **32,000 ticks**. An unsuccessful refuel does not change the push direction. This is a fuel budget, not a guaranteed travel distance; stops, slopes, water, and the selected movement model affect the journey. [Refuel condition][fuel] · [Fuel countdown][countdown]

## Behavior

Fuel decreases on server ticks and the push stops when fuel runs out. The cart applies its own propulsion and slowdown, and has a lower maximum speed than the base cart under the same movement model. Plan the track and stopping points using [Transport](../mechanics/Transport.md); use [Rails](../blocks/Rails.md#powered-rail-and-power-propagation) for powered-track behavior rather than assuming an Activator Rail is a motor control. [Countdown][countdown] · [Movement override][propulsion] · [Movement selection][movement]

Ordinary Survival destruction returns **one combined Minecart with Furnace item** when `doEntityDrops` is enabled, rather than separate Furnace and Minecart items. Recovery creates a new item carrying the custom name, not the cart's remaining fuel. Creative attacks discard it without returning its cart item. [Matching drop][drop-item] · [Vehicle recovery][drops]

## Notes

* This item is registered as `minecraft:furnace_minecart` and stacks to **one**. [Registration][registration]
* It is listed in ordinary Creative categories. See the current [inventory browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits); catalog visibility is not a Survival acquisition route. [Category entry][creative]
* [Minecart](Minecart.md) covers the rideable vehicle; [Redstone](../redstone/Redstone.md) links rail controls and sensing components.

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. Active registration, recipe loading, fuel tag and interaction, selected movement callbacks, placement, dispenser, and recovery were inspected. No in-game fueling, propulsion, timing, crafting, or dispenser test was run. Fuel tags, data packs, and world settings can change behavior.

[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartFurnace.java#L108-L129
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/furnace_minecart.json
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/MinecartItem.java#L27-L67
[rail-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/rails.json
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L461-L466
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java#L25-L68
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/furnace_minecart_fuel.json
[countdown]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartFurnace.java#L49-L67
[propulsion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartFurnace.java#L69-L105
[movement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L97-L104
[drop-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartFurnace.java#L74-L82
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1181-L1183
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1539-L1543
