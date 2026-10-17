# Short Dry Grass

<span id="dry-short-grass"></span>

**Short Dry Grass** is registered as **`minecraft:short_dry_grass`** for both item and block. The existing page filename is `DryShortGrass.md`; `minecraft:dry_short_grass` is not its registered ID. [Dry grass item registry] · [Block-derived item IDs] · [Display names]

## Obtaining

Use **Shears or Silk Touch I+** to recover **one Short Dry Grass**. Without either condition the block drops nothing, including no seeds. The [family guide](../blocks/ShrubsAndDryGrass.md#checked-acquisition-examples) covers verified Desert/Badlands patches and the tall plant's multiplication route. [short_dry_grass loot]

## Usage

Plant it on the [dry-vegetation support group](../blocks/ShrubsAndDryGrass.md#support-and-water). **One accepted Survival Bone Meal use converts it in place to Tall Dry Grass**, still occupying one block. [Short dry growth] · [Bone Meal use]

It has a **30%** ordinary composting level-increase chance and provides **100 burn ticks** as standard Furnace fuel. See [recipes, compost, and fuel](../blocks/ShrubsAndDryGrass.md#recipes-compost-and-fuel) for the shared limits and empty-composter exception. [Compost values] · [Fuel table]

## Behavior

It breaks instantly, has no collision or waterlogged form, and does not grow automatically. Incoming water or support loss does not supply the Shears/Silk Touch needed to recover it. No bundled crafting or smelting recipe makes this item. [Dry plant and Bush registry] · [Short dry growth] · [Water replacement] · [Water drops] · [Support destruction] · [short_dry_grass loot]

## Notes

[Short Dry Grass](../blocks/ShrubsAndDryGrass.md#short-dry-grass) is separate from ordinary [Short Grass](ShortGrass.md). Keep the exact item ID when using commands or reading recipes.

## Sources and verification

Source-reviewed on **2026-10-02** at `053cd852a8609f4002234ce0d445d3a345b551ae`. Checked the item-ID derivation, display name, full loot table, support/growth callbacks, recipe absence, composting and fuel. No gameplay test was run.

[Dry grass item registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L322-L323
[Block-derived item IDs]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2743-L2783
[Display names]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/assets/minecraft/lang/en_us.json
[short_dry_grass loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/short_dry_grass.json
[Short dry growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ShortDryGrassBlock.java#L16-L52
[Bone Meal use]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[Compost values]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L110-L114
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L109
[Dry plant and Bush registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L727-L783
[Water replacement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[Water drops]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Support destruction]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/Level.java#L262-L284
