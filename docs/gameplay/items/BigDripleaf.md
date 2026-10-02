# Big Dripleaf

**Big Dripleaf** (`minecraft:big_dripleaf`) is the shared inventory item for the leaf and its stem (`minecraft:big_dripleaf_stem`). It places a leaf; a connected taller column creates stems below the top. [Dripleaf items] · [Shared block item] · [Big support and stacking]

## Obtaining

Both leaves and stems drop **one Big Dripleaf**, including by hand. Bone Meal can [extend a planted column](../blocks/Dripleaves.md#bone-meal-conversion-and-growth) or convert Small Dripleaf into big form. The guide covers [verified natural sources](../blocks/Dripleaves.md#checked-acquisition-routes). No bundled recipe makes this item. [big_dripleaf loot] · [big_dripleaf_stem loot] · [Big Bone Meal growth] · [Small conversion]

## Usage

Build a [tilting platform](../blocks/Dripleaves.md#standing-tilt-and-reset), or grow more stems to harvest. It has a **65%** ordinary composting level-increase chance. [Big composting]

## Behavior

Use the exact [ground and water rules](../blocks/Dripleaves.md#big-dripleaf-and-stems). The leaf can be waterlogged; its stems have no collision and need a leaf/stem above. Removing the top can collapse the stem column. Power at the leaf prevents the ordinary entity tilt, but **projectile hits can still force full tilt**. [Stem support and collapse] · [Entity and power tilt] · [Projectile tilt]

## Notes

There is no separately registered Big Dripleaf Stem inventory item. [Dripleaves](../blocks/Dripleaves.md#big-dripleaf) owns the exact timing, redstone exceptions, harvesting, and growth rules. [Shared block item]

## Sources and verification

Source-reviewed on **2026-10-02** at `96e5604a6abaec697de2004b1ba9775e303bfba7`. Checked the shared item mapping, both loot tables, placement/support, growth, composting, and tilt callbacks. No gameplay test was run.

[Dripleaf items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L380-L381
[Shared block item]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L2764-L2771
[Big support and stacking]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L138-L167
[big_dripleaf loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/big_dripleaf.json
[big_dripleaf_stem loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/big_dripleaf_stem.json
[Big Bone Meal growth]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L169-L189
[Small conversion]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SmallDripleafBlock.java#L117-L137
[Big composting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L171
[Stem support and collapse]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafStemBlock.java#L59-L102
[Entity and power tilt]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L191-L223
[Projectile tilt]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java#L128-L135
