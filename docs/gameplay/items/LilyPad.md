# Lily Pad

**Lily Pad** (`minecraft:lily_pad`) places a thin surface platform over suitable Water or Ice. Its [block guide](../blocks/LilyPad.md) owns the exact support and acquisition rules. [Lily Pad registry] · [Lily Pad item] · [Lily support and collision]

## Obtaining

Break a Lily Pad to get **one item**, including by hand. [Swamp and Mangrove Swamp patches](../blocks/LilyPad.md#checked-acquisition-routes) are verified natural sources. A selected Wandering Trader listing sells **5 for 1 Emerald**, with two uses. No bundled recipe makes it. [lily_pad loot] · [Trader offers] · [Offer construction]

## Usage

Use it while targeting a suitable surface. The item raycasts for source Water, then attempts placement above the hit block. Normal Ice and Frosted Ice also satisfy its support rule; Packed Ice and Blue Ice do not satisfy that ice-class branch. See [placement and support](../blocks/LilyPad.md#placement-and-support). [Lily surface placement] · [Lily support and collision]

## Behavior

It has no waterlogged form or Bone Meal growth. **Boats entering its block break it with normal drops.** It has a **65%** ordinary composting level-increase chance. [Lily support and collision] · [Lily Pad registry] · [lily_pad loot] · [Lily composting]

## Notes

The item and block both use `minecraft:lily_pad`. [Lily Pad](../blocks/LilyPad.md) is separate from the tilting [Big Dripleaf](../blocks/Dripleaves.md#big-dripleaf).

## Sources and verification

Source-reviewed on **2026-10-02** at `96e5604a6abaec697de2004b1ba9775e303bfba7`. Checked the block/item, complete loot, surface placement/support, Boat callback, growth absence, selected trader listing, and composting. No gameplay test was run.

[Lily Pad registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2443-L2447
[Lily Pad item]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L572
[Lily support and collision]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/WaterlilyBlock.java#L18-L51
[lily_pad loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/lily_pad.json
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L815-L828
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Lily surface placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/PlaceOnWaterBlockItem.java#L17-L27
[Lily composting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L127
