# Dead Fire Coral Block

**Dead Fire Coral Block** (`minecraft:dead_fire_coral_block`) is a dead reef block for dry or underwater decoration. [Item registration][item]

## Obtaining

Mine the placed `minecraft:dead_fire_coral_block` with an **unbroken pickaxe** to collect **1 Dead Fire Coral Block**. Silk Touch is unnecessary; the wrong tool gives nothing. [Block requirements][block] · [Loot][loot] · [Mining gate][mining] [tool-gate][]

You can also obtain it by mining [Fire Coral Block](FireCoralBlock.md) (`minecraft:fire_coral_block`) with an unbroken pickaxe **without Silk Touch**, or by letting that living full block dry out and then mining it. [Living-block loot][living-loot] · [Drying](../blocks/Coral.md#drying-and-dead-forms)

## Usage

Use it for solid reef shapes on land or underwater. Unlike a coral plant or fan, it does not need a supporting face. See [placement and support](../blocks/Coral.md#placement-and-structural-support).

## Behavior

It stays dead with or without water. Water does not turn it back into [Fire Coral Block](FireCoralBlock.md); see [dead forms and nonrevival](../blocks/Coral.md#drying-and-dead-forms).

## Notes

- An unbroken wooden pickaxe already meets the material requirement. [Pickaxe tag][pickaxe] · [Tool durability check][tool-durability] See the [coral harvest rules](../blocks/Coral.md#harvesting-coral) for the other forms and normal Survival drop conditions.
- The checked built-in recipe data provides no recipe for this item. [Recipe resources][recipes]
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04; no gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L885-L885
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4857-L4865
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dead_fire_coral_block.json
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L295
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[living-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/fire_coral_block.json
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool-durability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
