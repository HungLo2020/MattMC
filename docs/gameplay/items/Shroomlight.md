# Shroomlight

**Shroomlight** (`minecraft:shroomlight`) is the inventory form of a full lighting block that emits **light level 15**. Collect it from huge fungi for building and decoration. [Item registration][items] · [Block and light][blocks]

## Obtaining

Harvest Shroomlights that appear in the caps of huge Crimson or Warped fungi. You can grow these fungi by applying Bone Meal to a small fungus on its **matching Nylium**; successful growth can include Shroomlights, without a fixed yield. See [Shroomlight sources](../blocks/LuminousBlocks.md#shroomlight) and [growing huge fungi](../blocks/NetherFungi.md#bone-meal-and-huge-growth) for the full routes and growth limits. [Growth callback][fungus] · [Crimson configuration][crimson] · [Warped configuration][warped] · [Cap decoration][feature]

With ordinary block drops enabled, mining a placed Shroomlight returns **one Shroomlight**, including by hand. A hoe is the efficient tool; Silk Touch is unnecessary and Fortune does not increase that self-drop. This is ordinary mining, not a guarantee that every explosion preserves the block. [Loot][loot] · [Registration][blocks] · [Harvest gate][gate] · [Hoe tag][hoe] · [Block-drop rule][drops]

No Shroomlight crafting recipe was found in the checked [bundled recipe data][recipes]. The item is also listed in Creative contents. [Creative listing][creative]

## Usage

Place it as a solid lighting block. Its light needs no redstone signal or fuel. Use the [shared lighting guide](../blocks/LuminousBlocks.md#blocks-and-light) for placement and material comparisons; the source light level does not establish a tested spawn-proof radius. [Light registration][blocks]

## Behavior

Its ordinary recovery is a self-drop; it does not need to be recrafted from Glowstone Dust or Prismarine Crystals. Keep the separate [Glowstone](Glowstone.md) and [Sea Lantern](SeaLantern.md) harvest rules with those items.

## Notes

Source-reviewed on **2026-10-02** at `eaeeffdeb9220de7d839af7c84a047693249c2f7`. No in-game growth, mining, placement, or lighting test was run. Data packs and game rules can change the checked recipes and drops.

Related: [Shroomlight block guide](../blocks/LuminousBlocks.md#shroomlight) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/Blocks.java
[fungus]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L61-L79
[crimson]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[warped]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[feature]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java
[loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/loot_table/blocks/shroomlight.json
[gate]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[hoe]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[drops]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[recipes]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/recipe
[creative]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
