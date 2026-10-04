# Brain Coral Fan

**Brain Coral Fan** (`minecraft:brain_coral_fan`) is a living coral fan for underwater reef decoration. [Item registration][item]

## Obtaining

Break either of its placed fan forms with **Silk Touch** to collect **1 Brain Coral Fan**. This living form has no required-tool gate. **Shears alone**, an ordinary pickaxe or bare hands give nothing; they do not produce the dead form. [Block requirements][block] · [Loot][loot] · [Mining gate][mining] [tool-gate][]

Find living pieces in [Warm Ocean reefs](../blocks/Coral.md#warm-ocean-reefs). [Underwater Bone Meal in Warm Ocean](../blocks/Coral.md#bone-meal-for-plants-and-fans) can also grow this species among its possible plant/fan outputs; the result is not guaranteed. [Growth selection][growth] · [Output tag][outputs]

## Usage

This one item places both **floor** `minecraft:brain_coral_fan` and **wall** `minecraft:brain_coral_wall_fan` forms. The wall form uses the same item and loot table; there is no separate wall-fan inventory item. [Wall registration][wall] · [Shared item][fan-item] · [Shared loot][wall-loot]

Use a sturdy upper face for a floor fan or a sturdy side face for a wall fan. Collect the fan before removing its support; support loss does not bypass Silk Touch. See [placement and support](../blocks/Coral.md#placement-and-structural-support).

## Behavior

Keep it waterlogged or beside water. Without either, it becomes [Dead Brain Coral Fan](DeadBrainCoralFan.md); water added afterward will not reverse the change. See [water requirements](../blocks/Coral.md#keeping-living-coral-alive) and [drying](../blocks/Coral.md#drying-and-dead-forms).

## Notes

- See the [coral harvest rules](../blocks/Coral.md#harvesting-coral) for the other forms and normal Survival drop conditions.
- The checked built-in recipe data provides no recipe for this item. [Recipe resources][recipes]
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04; no gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L906-L909
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5065-L5069
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/brain_coral_fan.json
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L295
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L81-L146
[outputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/corals.json
[wall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5145-L5149
[fan-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L13-L52
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
