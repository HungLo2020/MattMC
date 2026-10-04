# Nether Brick Fence

**Nether Brick Fence** (`minecraft:nether_brick_fence`) is a fence for barriers, posts and Nether masonry detail. It is separate from the [Nether Brick Wall](NetherBrickWall.md), and its recipe combines full blocks with small brick items. [Item binding][item] · [Registration helper][factory]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), use **4 full [Nether Bricks](NetherBricks.md) blocks and 2 small [Nether Brick](NetherBrick.md) items → 6 Nether Brick Fences**. Fill two rows with **full block / small brick / full block** in each row. The full blocks and small items have distinct slots; Red Nether Bricks are not substitutes. [Exact fence recipe][craft]

The bundled [stonecutting choices](../blocks/NetherBricks.md#stonecutting) do not produce this fence. For the checked Fortress collection route, see [collecting placed Nether masonry](../blocks/NetherBricks.md#collect-placed-masonry).

Use an **unbroken pickaxe**, including Wood, to collect the placed block. Ordinary mining returns **1 Nether Brick Fence item**. Hand breaking or an unsuitable tool does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. Explosion recovery has a separate survival condition. [Exact loot][loot] · [Tool and collection rules](../blocks/NetherBricks.md#collect-placed-masonry)

## Usage

Build a connected barrier or use a fence post to secure eligible mobs already held on [Leads](Lead.md). The fence interaction attaches those existing leashes to the post. See the [fence guide](../blocks/NetherBricks.md#placement-and-the-fence) for the full behavior. [Fence interaction][fence] · [Lead binding][lead] · [Active interaction dispatch][interaction]

## Behavior

It uses fence behavior rather than wall behavior. It connects to another Nether Brick Fence, suitable sturdy block faces and properly aligned Fence Gates. Registered wooden fences belong to a different connection group and do not connect directly to it. The fence can be waterlogged where Water can exist, and its connections update as neighbors change. [Fence rules][fence] · [Fence tags][fences] · [Wooden group][wood-fences]

## Notes

The exact item and block ID is `minecraft:nether_brick_fence`. Follow [crafting and cracking](../blocks/NetherBricks.md#crafting-and-cracking) for the shared mixed-item recipe and [placement and the fence](../blocks/NetherBricks.md#placement-and-the-fence) for its collision and connection rules. [Block registration][block] · [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/block bindings, exact production recipes, complete loot, mining tags and tool gates, and the relevant placement or interaction rules. No in-game crafting, smelting, mining, placement or interaction test was run. Data packs can change recipes, tags and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L576
[factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2750-L2782
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2499-L2508
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/nether_brick_fence.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/nether_brick_fence.json
[fence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceBlock.java#L61-L124
[lead]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/LeadItem.java#L35-L59
[interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L393
[fences]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/fences.json
[wood-fences]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/wooden_fences.json
