# Bubble Coral Block

**Bubble Coral Block** (`minecraft:bubble_coral_block`) is a living reef block. [Item registration][item]

## Obtaining

Mine the placed `minecraft:bubble_coral_block` with an **unbroken Silk Touch pickaxe** to collect **1 Bubble Coral Block**. An unbroken pickaxe without Silk Touch instead gives **1 [Dead Bubble Coral Block](DeadBubbleCoralBlock.md)** (`minecraft:dead_bubble_coral_block`); the wrong tool gives nothing. [Block requirements][block] · [Loot][loot] · [Mining gate][mining] [tool-gate][]

Look for it in [Warm Ocean reefs](../blocks/Coral.md#warm-ocean-reefs), or check [Wandering Trader offers](../blocks/Coral.md#wandering-trader-full-blocks). A trader can offer **1 Bubble Coral Block for 3 Emeralds**, with **8 uses**, but does not always select that offer. [Trade entry][trades]

## Usage

Use it as a solid part of a reef build. It has no plant-style support requirement; see [placement and support](../blocks/Coral.md#placement-and-structural-support).

## Behavior

Keep water beside at least one of its six faces; full Coral Blocks cannot be waterlogged. If left without that water, it becomes [Dead Bubble Coral Block](DeadBubbleCoralBlock.md). Adding water after conversion does not revive it. See [water requirements](../blocks/Coral.md#keeping-living-coral-alive) and [drying](../blocks/Coral.md#drying-and-dead-forms). [Living block behavior][living]

## Notes

- An unbroken wooden pickaxe already meets the material requirement. [Pickaxe tag][pickaxe] · [Tool durability check][tool-durability] See the [coral harvest rules](../blocks/Coral.md#harvesting-coral) for the other forms and normal Survival drop conditions.
- The checked built-in recipe data provides no recipe for this item. [Recipe resources][recipes]
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04; no gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L889-L889
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4895-L4904
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/bubble_coral_block.json
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L295
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L806-L810
[living]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CoralBlock.java#L26-L82
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool-durability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
