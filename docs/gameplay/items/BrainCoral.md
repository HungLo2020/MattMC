# Brain Coral

**Brain Coral** (`minecraft:brain_coral`) is a living coral plant for underwater reef decoration. [Item registration][item]

## Obtaining

Break the placed `minecraft:brain_coral` with **Silk Touch** to collect **1 Brain Coral**. This living form has no required-tool gate. **Shears alone**, an ordinary pickaxe or bare hands give nothing; they do not produce the dead form. [Block requirements][block] · [Loot][loot] · [Mining gate][mining] [tool-gate][]

Find living pieces in [Warm Ocean reefs](../blocks/Coral.md#warm-ocean-reefs). [Underwater Bone Meal in Warm Ocean](../blocks/Coral.md#bone-meal-for-plants-and-fans) can also grow this species among its possible plant/fan outputs; the result is not guaranteed. [Growth selection][growth] · [Output tag][outputs]

## Usage

Place it upright on a **sturdy upper face**, not only on Sand. It is the plant form, separate from the full block and fan in the [brain coral family](../blocks/Coral.md#brain-coral). Collect it before removing its support; support loss does not bypass Silk Touch. See [placement and support](../blocks/Coral.md#placement-and-structural-support).

## Behavior

Keep it waterlogged or beside water. Without either, it becomes [Dead Brain Coral](DeadBrainCoral.md); water added afterward will not reverse the change. See [water requirements](../blocks/Coral.md#keeping-living-coral-alive) and [drying](../blocks/Coral.md#drying-and-dead-forms).

## Notes

- See the [coral harvest rules](../blocks/Coral.md#harvesting-coral) for the other forms and normal Survival drop conditions.
- The checked built-in recipe data provides no recipe for this item. [Recipe resources][recipes]
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04; no gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L893-L893
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4985-L4989
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/brain_coral.json
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L295
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L81-L146
[outputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/coral_plants.json
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
