# Damaged Anvil

**Damaged Anvil** (`minecraft:damaged_anvil`) is the final usable wear stage of the [Anvil](Anvil.md). It retains the full repair, naming, and enchantment-combining menu, but its next wear advance destroys it. [Item registration][items] · [Shared menu][menu] · [Final transition][stages]

## Obtaining

A [Chipped Anvil](ChippedAnvil.md) becomes Damaged when a completed-use wear roll advances it. Taking a result, including a rename-only result, has a **12% chance** to advance the stage when the player lacks Creative's infinite-material ability. Opening the menu alone does not trigger that roll. [Completed-use handling][wear] · [Stage transition][stages]

Use a **unbroken pickaxe** to collect a placed Damaged Anvil as **one Damaged Anvil item**. Breaking it by hand fails the required-tool check. Mining and replacing it preserves the Damaged stage; it does not restore the block. The loot table also checks explosion survival. [Tool requirement][blocks] · [Pickaxe tag][pickaxe] · [All anvil stages][anvil-tag] · [Harvest dispatch][harvest] · [Tool check][tool-check] · [Loot][loot]

For a fresh replacement, craft a new Anvil from three Iron Blocks and four Iron Ingots using the [canonical recipe](../blocks/Anvil.md#crafting-and-collecting). [Recipe output][recipe]

In Creative, search the combined [inventory item browser](../mechanics/InventoryBrowser.md) by name. Its catalog is also visible in Survival, but ordinary Survival requests are blocked before item insertion.

## Usage

Place it on firm support and interact to use the same workstation as the other stages. MattMC caps the charge at **40 experience levels per completed operation**. See [anvil mechanics](../mechanics/AnvilMechanics.md) for valid inputs, costs, and result handling. [Shared menu][menu] · [Cost cap][cap]

## Behavior

Each completed use outside Creative's infinite-material ability has a **12% chance to destroy this block**. The result has already been taken; the wear roll removes the anvil without producing an anvil item. Creative skips this menu-use roll. [Result-taking and removal][wear] · [Final wear stage][stages]

Unsupported anvils fall and can hurt entities below. A damaging fall has its own wear roll, which can destroy a Damaged Anvil **without an item drop**, even if no victim is hit. Creative's menu-use exemption does not prevent falling wear. Keep firm support beneath it; the [falling and impact guide](../blocks/Anvil.md#falling-and-impact-damage) gives the exact conditions. [Falling trigger][fall] · [Impact wear][impact] · [Cancelled landing drop][landing]

## Notes

This item is the item form of the `minecraft:damaged_anvil` block. It is a distinct item, not a nearly depleted durability value on the new Anvil item. [Registrations][items]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game acquisition, mining, menu, falling, or inventory-browser test was run. Data packs and game rules can change recipes and drops. Natural structure placement was not reviewed.

Related: [Anvil block](../blocks/Anvil.md) · [Anvil item](Anvil.md) · [Chipped Anvil](ChippedAnvil.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L627-L629
[wear]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L71-L116
[stages]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/AnvilBlock.java#L103-L112
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2804-L2833
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L320
[anvil-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/anvil.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L295
[tool-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/anvil.json
[menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/AnvilBlock.java#L58-L71
[cap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L238-L275
[fall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FallingBlock.java#L27-L65
[impact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L247-L274
[landing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L181-L236
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/damaged_anvil.json
