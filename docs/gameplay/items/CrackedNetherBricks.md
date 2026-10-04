# Cracked Nether Bricks

**Cracked Nether Bricks** (`minecraft:cracked_nether_bricks`) is the cracked decorative finish of the full [Nether Bricks](NetherBricks.md) block. Make it by smelting the regular full block. [Item binding][item] · [Registration helper][factory]

## Obtaining

Smelt **1 regular Nether Bricks block → 1 Cracked Nether Bricks** in a fueled [Furnace](../blocks/Furnace.md). The recipe takes **200 game ticks**, nominally **10 seconds at 20 ticks per second**, and specifies **0.1 recipe XP**. Use the full regular block, not a small [Nether Brick](NetherBrick.md) item or Red Nether Bricks. See [Furnace experience](../blocks/Furnace.md#experience-and-troubleshooting) for payout. [Smelting recipe][smelt] · [Furnace recipe type][furnace]

Use an **unbroken pickaxe**, including Wood, to collect the placed block. Ordinary mining returns **1 Cracked Nether Bricks item**. Hand breaking or an unsuitable tool does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. Explosion recovery has a separate survival condition. [Exact loot][loot] · [Tool and collection rules](../blocks/NetherBricks.md#collect-placed-masonry)

## Usage

Use the cracked finish for textured walls, worn masonry and accents beside the [regular and chiseled forms](../blocks/NetherBricks.md#variants). The [family crafting and cracking guide](../blocks/NetherBricks.md#crafting-and-cracking) distinguishes this smelt from firing Netherrack into the small brick ingredient.

## Behavior

It places as an ordinary full block with no player-selected facing or axis. Correct-tool mining returns the cracked block item, preserving the finish. See [full-block placement](../blocks/NetherBricks.md#placement-and-the-fence) for the shared family properties. [Block registration][block] · [Loot][loot]

## Notes

The exact item and block ID is `minecraft:cracked_nether_bricks`. Follow the [Nether Bricks guide](../blocks/NetherBricks.md) for the recipes and placed behavior. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/block bindings, exact production recipes, complete loot, mining tags and tool gates, and the relevant placement or interaction rules. No in-game crafting, smelting, mining, placement or interaction test was run. Data packs can change recipes, tags and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L574
[factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2750-L2782
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5874-L5882
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cracked_nether_bricks.json
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cracked_nether_bricks.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
