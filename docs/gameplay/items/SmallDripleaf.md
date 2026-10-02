# Small Dripleaf

**Small Dripleaf** (`minecraft:small_dripleaf`) places a two-block-tall plant. **Use Shears to recover it**, and save any small plants you want to keep before using Bone Meal. [Dripleaf items] · [small_dripleaf loot] · [Small conversion]

## Obtaining

Mining either half with Shears gives **one item** for the plant. Hand mining and Silk Touch without Shears give none. The family guide covers [Lush Caves generation and the trader offer](../blocks/Dripleaves.md#checked-acquisition-routes): a selected trader listing sells **2 for 1 Emerald**, up to five uses. [Double-plant harvest] · [small_dripleaf loot] · [Trader offers] · [Offer construction]

## Usage

Plant it on **Clay or ordinary Moss Block**, or follow its [source-water-dependent ground rules](../blocks/Dripleaves.md#small-dripleaf). Bone Meal on either half **converts the plant into Big Dripleaf**; it does not drop an extra Small Dripleaf. No bundled recipe makes or reverses this conversion. [Small support and placement] · [small_dripleaf_placeable tag] · [Small conversion]

## Behavior

Its halves can be waterlogged independently, but the upper half needs the lower half. Removing support loses the plant without a Shears harvest. Its **30%** ordinary composting chance is lower than Big Dripleaf's; the full support, water, and growth comparison belongs to [Dripleaves](../blocks/Dripleaves.md). [Double-plant placement and water] · [Double-plant neighbor survival] · [Support-loss drops] · [small_dripleaf loot] · [Small composting]

## Notes

The item and block share `minecraft:small_dripleaf`; the lower/upper halves are states of that block, not separate item IDs. [Dripleaf registry] · [Dripleaf items]

## Sources and verification

Source-reviewed on **2026-10-02** at `96e5604a6abaec697de2004b1ba9775e303bfba7`. Checked the item, paired-half harvest, complete loot, support, water, Bone Meal conversion, selected trader offer, and composting. No gameplay test was run.

[Dripleaf items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L380-L381
[small_dripleaf loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/small_dripleaf.json
[Small conversion]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SmallDripleafBlock.java#L117-L137
[Double-plant harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L117
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L815-L828
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Small support and placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SmallDripleafBlock.java#L50-L92
[small_dripleaf_placeable tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/small_dripleaf_placeable.json
[Double-plant placement and water]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L63-L99
[Double-plant neighbor survival]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L39-L60
[Support-loss drops]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[Small composting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L105
[Dripleaf registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L6615-L6635
