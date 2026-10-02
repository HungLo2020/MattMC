# Tall Dry Grass

<span id="dry-tall-grass"></span>

**Tall Dry Grass** is registered as **`minecraft:tall_dry_grass`** for both item and block. It occupies **one block**. The existing page filename is `DryTallGrass.md`; `minecraft:dry_tall_grass` is not its registered ID. [Dry grass item registry] · [Block-derived item IDs] · [Tall dry growth] · [Display names]

## Obtaining

Use **Shears or Silk Touch I+** to collect **one Tall Dry Grass**. It does not drop two short plants, and ordinary hand mining gives nothing. Bone Meal converts planted Short Dry Grass into this form. The [family guide](../blocks/ShrubsAndDryGrass.md#checked-acquisition-examples) covers verified natural patches and a selected trader offer of **1 for 1 Emerald**, up to 12 uses. [tall_dry_grass loot] · [Short dry growth] · [Trader plant offers] · [Offer construction]

## Usage

Bone Meal on the tall plant creates **one Short Dry Grass in a valid adjacent horizontal cell**, leaving the original tall plant. It does not make this plant taller or create an upper half. It has a **30%** ordinary composting level-increase chance and supplies **100 standard Furnace burn ticks**. [Tall dry growth] · [Adjacent spread helper] · [Compost values] · [Fuel table]

## Behavior

Use the same [dry-vegetation support group](../blocks/ShrubsAndDryGrass.md#support-and-water) as Short Dry Grass. It has no automatic growth, collision, or waterlogged state. Water/support-loss drops use no harvesting tool and cannot recover this plant. No bundled recipe produces the item. [Dry plant and Bush registry] · [Dry plant support] · [Tall dry growth] · [Water replacement] · [Water drops] · [Support destruction] · [tall_dry_grass loot]

## Notes

This is separate from ordinary [Tall Grass](TallGrass.md). [Shrubs and Dry Grass](../blocks/ShrubsAndDryGrass.md#tall-dry-grass) owns the exact propagation, sound, harvest, and resource comparison.

## Sources and verification

Source-reviewed on **2026-10-02** at `053cd852a8609f4002234ce0d445d3a345b551ae`. Checked the actual block/item ID, display name, single-block class, complete loot, Bone Meal callback, selected trader listing, recipe absence, composting and fuel. No gameplay test was run.

[Dry grass item registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L322-L323
[Block-derived item IDs]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2743-L2783
[Tall dry growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/TallDryGrassBlock.java#L16-L53
[Display names]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/assets/minecraft/lang/en_us.json
[tall_dry_grass loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/tall_dry_grass.json
[Short dry growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ShortDryGrassBlock.java#L16-L52
[Trader plant offers]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L824-L829
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Adjacent spread helper]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/BonemealableBlock.java#L20-L37
[Compost values]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L110-L114
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L109
[Dry plant and Bush registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L727-L783
[Dry plant support]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/DryVegetationBlock.java#L15-L41
[Water replacement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[Water drops]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Support destruction]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/Level.java#L262-L284
