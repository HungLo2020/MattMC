# Chipped Anvil

**Chipped Anvil** (`minecraft:chipped_anvil`) is the middle wear stage of the [Anvil](Anvil.md). It still repairs equipment, renames items, and combines compatible enchantments. [Item registration][items] · [Shared menu][menu]

## Obtaining

A new Anvil becomes Chipped when a completed-use wear roll advances it. Taking a result, including a rename-only result, has a **12% chance** to advance the stage when the player lacks Creative's infinite-material ability. Merely opening the menu does not cause wear. [Completed-use handling][wear] · [Stage transition][stages]

Mine a placed Chipped Anvil with a **unbroken pickaxe** to recover **one Chipped Anvil item**. Breaking it by hand does not satisfy its required-tool check. The drop preserves its worn stage; mining and replacing it does not make it new. The loot table also checks explosion survival. [Tool requirement][blocks] · [Pickaxe tag][pickaxe] · [All anvil stages][anvil-tag] · [Harvest dispatch][harvest] · [Tool check][tool-check] · [Loot][loot]

For a fresh replacement, the [Anvil recipe](../blocks/Anvil.md#crafting-and-collecting) makes one new Anvil from three Iron Blocks and four Iron Ingots. [Recipe][recipe]

In Creative, search the combined [inventory item browser](../mechanics/InventoryBrowser.md) by name. Its catalog is also visible in Survival, but ordinary Survival requests are blocked before item insertion.

## Usage

Place it on firm support, then interact to open the same workstation menu as a new or Damaged Anvil. Wear does not reduce its available operations. MattMC caps a completed operation's charge at **40 experience levels**; follow [anvil mechanics](../mechanics/AnvilMechanics.md) for input order, repair ingredients, enchantments, and naming costs. [Menu][menu] · [Cost cap][cap]

## Behavior

A successful completed-use wear roll turns it into a [Damaged Anvil](DamagedAnvil.md), the last usable stage. Creative skips the menu-use wear roll. See the canonical [wear guide](../blocks/Anvil.md#using-and-wearing-out-an-anvil). [Use wear][wear] · [Transition][stages]

An unsupported anvil can fall and hurt entities below it. Falling has a separate wear roll that can turn a Chipped Anvil into a Damaged Anvil even without hitting a victim, including in Creative. Keep support beneath it and use the [falling and impact guide](../blocks/Anvil.md#falling-and-impact-damage) for the conditions and damage calculation. [Falling trigger][fall] · [Impact wear][impact]

## Notes

This item is the item form of the `minecraft:chipped_anvil` block. The wear stages are separate item IDs, not durability values on one item. [Registrations][items]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game acquisition, mining, menu, falling, or inventory-browser test was run. Data packs and game rules can change recipes and drops. Natural structure placement was not reviewed.

Related: [Anvil block](../blocks/Anvil.md) · [Anvil item](Anvil.md) · [Damaged Anvil](DamagedAnvil.md) · [Items](Items.md)

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
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/chipped_anvil.json
