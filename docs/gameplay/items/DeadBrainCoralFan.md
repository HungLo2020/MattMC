# Dead Brain Coral Fan

**Dead Brain Coral Fan** (`minecraft:dead_brain_coral_fan`) is a dead coral fan for dry or underwater decoration. [Item registration][item]

## Obtaining

Break either of its placed fan forms with an **unbroken Silk Touch pickaxe** to collect **1 Dead Brain Coral Fan**. Both the correct pickaxe and Silk Touch are required. An ordinary pickaxe, Shears alone or a different Silk Touch tool gives nothing. [Block requirements][block] · [Loot][loot] · [Mining gate][mining] [tool-gate][]

To make a collectible dead piece, place [Brain Coral Fan](BrainCoralFan.md) (`minecraft:brain_coral_fan`), leave it without water until it turns dead, then collect it with that Silk Touch pickaxe. Keep its support intact while it dries. Breaking the living piece without Silk Touch does **not** yield this item. [Living loot][living-loot] · [Drying](../blocks/Coral.md#drying-and-dead-forms)

## Usage

This one item places both **floor** `minecraft:dead_brain_coral_fan` and **wall** `minecraft:dead_brain_coral_wall_fan` forms. The wall form uses the same item and loot table; there is no separate wall-fan inventory item. [Wall registration][wall] · [Shared item][fan-item] · [Shared loot][wall-loot]

Use a sturdy upper face for a floor fan or a sturdy side face for a wall fan. Collect the fan before removing its support; support loss does not bypass Silk Touch. See [placement and support](../blocks/Coral.md#placement-and-structural-support).

## Behavior

It remains dead whether dry or waterlogged; water does not restore [Brain Coral Fan](BrainCoralFan.md). Its support is still required. See [dead forms and nonrevival](../blocks/Coral.md#drying-and-dead-forms).

## Notes

- An unbroken wooden pickaxe already meets the material requirement. [Pickaxe tag][pickaxe] · [Tool durability check][tool-durability] See the [coral harvest rules](../blocks/Coral.md#harvesting-coral) for the other forms and normal Survival drop conditions.
- The checked built-in recipe data provides no recipe for this item. [Recipe resources][recipes]
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04; no gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L930-L935
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5016-L5026
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dead_brain_coral_fan.json
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L295
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[living-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/brain_coral_fan.json
[wall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5096-L5106
[fan-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L13-L52
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool-durability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
