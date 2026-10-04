# Prismarine Bricks

**Prismarine Bricks** (`minecraft:prismarine_bricks`) are the brick-patterned full block made from [Prismarine Shards](PrismarineShard.md). Keep full blocks for Conduit frames, or turn them into matching stairs and slabs. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **9 [Prismarine Shards](PrismarineShard.md) → 1 Prismarine Bricks** at a [Crafting Table](../blocks/CraftingTable.md). The recipe is shapeless but occupies **all nine slots**, one Shard per slot; it cannot fit the inventory’s 2 × 2 grid. It does not use ordinary Prismarine or Prismarine Crystals. [Exact recipe][craft] · [Shapeless matching][shapeless]

The family guide covers [collecting full blocks from Ocean Monuments](../blocks/Prismarine.md#ocean-monument-route) and the [other full-block recipes](../blocks/Prismarine.md#crafting-full-blocks).

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/Prismarine.md#recovering-placed-blocks)

## Usage

Use the full block for construction or make [Prismarine Brick Stairs](PrismarineBrickStairs.md) and [Slabs](PrismarineBrickSlab.md). Both their [crafting](../blocks/Prismarine.md#crafting-stairs-slabs-and-walls) and [stonecutting](../blocks/Prismarine.md#stonecutting) routes use Prismarine Bricks as the exact finish.

## Behavior

Full Prismarine Bricks count toward a [Conduit frame](../blocks/Conduit.md#build-a-valid-frame), alongside full Prismarine, Dark Prismarine and Sea Lanterns. The active check accepts these exact blocks; brick stairs and slabs, including double slabs, do not count. [Accepted blocks][conduit] · [Server frame check][frame] · [Active ticker][conduit-ticker]

This full block has no pillar axis. Correct-tool mining preserves the full block instead of returning Shards. [Block registration][block] · [Exact loot][loot]

## Notes

The exact block and item ID is `minecraft:prismarine_bricks`. Follow the [family variants](../blocks/Prismarine.md#prismarine-brick-forms), [stonecutting](../blocks/Prismarine.md#stonecutting) and [placed behavior](../blocks/Prismarine.md#placement-water-and-support) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks, plus the active Conduit frame consumer. No in-game crafting, harvesting, placement or Conduit test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[conduit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L162
[conduit-ticker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L49-L58
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L57-L65
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L751-L751
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3153-L3156
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/prismarine_bricks.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/prismarine_bricks.json
