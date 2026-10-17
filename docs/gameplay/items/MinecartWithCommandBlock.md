# Minecart with Command Block

A **Minecart with Command Block** is an operator/mapmaking tool that runs a stored command from a rail vehicle. Its editor and catalog entry require Creative instant-build and permission level **2 or higher**. Use [Commands](../commands/Commands.md) for syntax and [Command Blocks](../blocks/CommandBlocks.md) for the command system's permissions and feedback settings. [Editor][editor] · [Permission check][permissions] · [Operator category][operator]

## Obtaining

An authorized in-game player can use **`/give @s minecraft:command_block_minecart 1`**. The `give` command requires permission level 2. No recipe, loot-table item entry, or villager trade for this cart was found in the inspected bundled sources; it is not made by combining a Command Block and Minecart. [Give registration][give] · [Item registration][registration] · [Bundled recipes][recipes] · [Bundled loot][loot] · [Villager trades][trades]

The cart is in the permission-gated operator category. MattMC's item browser passes the player's Game Master permission into that category builder; a visible catalog entry still does not establish a Survival insertion route. Follow the current [inventory browser mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Category][operator] · [Browser category loading][browser]

## Usage

Use a supplied item on a rail to place the cart. Its registered item uses the shared minecart placement handler, which checks the rail and placement conditions; it does **not** apply the placed Command Block item's Game Master placement check. Opening and saving the cart's command editor still require the permissions above. [Registration][registration] · [Rail placement][placement] · [Editor gate][editor] · [Save gate][save]

A [Dispenser](Dispenser.md) also has a registered placement behavior for this variant: rail in front, or air in front with rail below, places the cart; other arrangements eject the item. Do not assume dispensing preserves a preconfigured command: Command Block Minecart custom entity data is operator-restricted, and the dispenser supplies no player to that check. [Dispenser registration][dispenser-registration] · [Dispenser behavior][dispenser] · [Item configuration][configuration] · [Restricted entity type][restricted-type] · [Custom-data permission][custom-data]

Interact in Creative with permission level 2 or higher to edit the command. A powered **Activator Rail** attempts execution with a **four-game-tick activation cooldown**. Use [Rails](../blocks/Rails.md#activator-rail-effects) for that trigger and [Transport](../mechanics/Transport.md) for track layout. [Interaction and activation][cart]

## Behavior

**`commandBlocksEnabled`**, default **true**, is MattMC's server gate for saving cart commands and dispatching ordinary stored commands. Query it with `/gamerule commandBlocksEnabled` before diagnosing a disabled-command-block message; do not substitute an assumed `enable-command-block` server property. The [Command Blocks guide](../blocks/CommandBlocks.md#access-and-server-enable-gate) owns the shared gate and troubleshooting. [Server gate][server-gate] · [Rule default][command-rule] · [Save check][save] · [Execution check][execution]

The command runs at the cart's position with **permission level 2** and **the cart as its executing entity**. Thus `@s` refers to this cart, not the player who configured it. A Comparator reading an occupied Detector Rail can read the cart's command success count; see [Detector Rail output](../blocks/Rails.md#detector-rail-output). [Command source][command-source]

Ordinary Survival destruction drops a **plain [Minecart](Minecart.md)** when `doEntityDrops` is enabled, not the combined command-cart item or a Command Block. Creative attacks discard the vehicle without that drop. The Pick Block result is the command-cart item, which is a different path from destruction. [Drop and pick results][drop-pick] · [Vehicle recovery][drops]

## Notes

* This item is registered as `minecraft:command_block_minecart` and stacks to **one**. [Registration][registration]
* Possession, placement, command editing, and command execution have separate checks. Giving someone the item does not grant command-editor permissions.
* [Redstone](../redstone/Redstone.md) links ordinary trigger and sensing components; this cart does not use the Impulse/Repeating/Chain block layout controls. [Cart editor][cart-editor]

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. Active item/entity registration, operator category, placement/configuration, dispenser bootstrap, editor/save packet, activation, execution, and recovery paths were inspected; bundled recipes, loot tables, trades, and world-generation references were searched. No game commands, server changes, editor saves, placement, or dispenser tests were executed. Server settings and later source/data-pack changes can alter availability or behavior.

[editor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/BaseCommandBlock.java#L171-L181
[permissions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1818
[operator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2143
[give]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/GiveCommand.java#L23-L41
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2142-L2146
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/MinecartItem.java#L27-L67
[save]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L644-L664
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L461-L466
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/MinecartDispenseItemBehavior.java#L25-L68
[configuration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L119-L134
[restricted-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1617
[custom-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1788-L1802
[cart]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartCommandBlock.java#L77-L88
[server-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L1521-L1523
[command-rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameRules.java#L238-L240
[execution]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/BaseCommandBlock.java#L95-L139
[command-source]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartCommandBlock.java#L124-L137
[drop-pick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartCommandBlock.java#L37-L45
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[cart-editor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/MinecartCommandBlockEditScreen.java
