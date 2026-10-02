# Crimson and Warped Fungi

Use **Crimson Fungus on Crimson Nylium** or **Warped Fungus on Warped Nylium**, then apply Bone Meal to attempt a huge fungus. A small fungus can survive on several other soils, but those supports do not activate its huge-growth callback. These two blocks use the planted huge-fungus features rather than the [sapling tree grower](SaplingsAndAzaleas.md). [Crimson registration][s1] · [Warped registration][s2]

## Fungus and substrate comparison

| Small block ID | Required ground for huge growth | Active planted feature | Generated material choices |
| --- | --- | --- | --- |
| <span id="crimson-fungus">`minecraft:crimson_fungus`</span> | `minecraft:crimson_nylium` | [crimson_fungus_planted][s3] | Crimson Stem, Nether Wart Block, possible Shroomlight and Weeping Vines |
| <span id="warped-fungus">`minecraft:warped_fungus`</span> | `minecraft:warped_nylium` | [warped_fungus_planted][s4] | Warped Stem, Warped Wart Block, possible Shroomlight |

The material list is not a guaranteed yield. The caps use placement checks and random choices; Weeping Vines belong to the Crimson cap branch in this implementation. [Tree Logs and Roots](TreeLogsAndRoots.md) owns the stem and hyphae construction details. [Stem placement][s5] · [Cap and vine behavior][s6]

### Placing a small fungus

Both small fungi accept **either Nylium**, Soul Soil, Farmland, and the dirt block tag. Mycelium is explicitly accepted as well and also belongs to that dirt tag. This allows decorative planting on soils such as Dirt, Grass Block, Podzol, Mud, or Muddy Mangrove Roots. **Soul Sand is not Soul Soil** and is not in the checked support tag. Neither fungus has a waterlogged state or a brightness requirement in its support check. [Fungus support][s7] · [Inherited vegetation support][s8] · [Nylium tag][s9] · [Dirt supports][s10]

The two small blocks have no collision, break instantly, and can be recovered without a special tool. Ordinary loot returns **one matching fungus**, with no Silk Touch or Fortune branch; explosions use a survival condition. [Flower Pots](FlowerPot.md) covers potted fungi as decorations. [Crimson properties][s11] · [Warped properties][s12] · [Crimson loot][s13] · [Warped loot][s14] · [Harvest gate][s15]

## Bone Meal and huge growth

On the matching Nylium, an accepted Bone Meal application has a **40% chance to call the planted huge-fungus feature** and consumes one Bone Meal in ordinary Survival. A failed chance leaves the small fungus in place. There is no hidden two-stage sapling progression, flower search, or 2 × 2 combined-plant check. Four fungi do not combine into a larger planted feature. [Fungus target and success checks][s16] · [Bone Meal dispatch and consumption][s17] · [Planted huge-fungus entry][s18]

These fungi do **not** grow huge by waiting for ordinary random ticks: their registrations do not enable random ticking and their class implements the Bone Meal path. The checked growth callback and planted feature contain no light or Nether-dimension requirement. The matching Nylium and the actual placement result matter, not simply being in a Nether biome. [Fungus implementation][s19] · [Planted placement implementation][s20] · [Crimson registration][s21] · [Warped registration][s22]

### Obstructions and recovery

**Clear overhead and side space before using Bone Meal.** This path does not perform the tree feature's up-front clearance scan. After checking the matching base, it clears the small fungus and tries to place the stem and cap. A solid obstruction can make individual positions be skipped **without failing the whole feature**. A successful chance can therefore consume the fungus while leaving an incomplete shape. There is no sapling-style restoration wrapper after this placement begins. [Base check and consumption of the small fungus][s23] · [Per-position replacement and stem placement][s24] · [Per-position cap placement][s25]

The planted configurations set `planted: true`. That disables this feature's natural-generation wide-stem roll and its natural-generation depth check; it does not promise an unobstructed structure or bypass the world's block-placement limits. The stem can replace the configuration's listed plants as well as normally replaceable blocks, while the cap checks normally replaceable positions. Do not use a cramped growing site as a reliable way to preserve nearby plants. [Crimson planted settings][s26] · [Warped planted settings][s27] · [Planted flag and replacement paths][s28]

After harvesting, recheck the base. Nylium has an active random-tick cover check that can turn it into **Netherrack** when the block above blocks enough light through their shared face. This is a cover/occlusion test, not the small fungus's ambient-light requirement. A Netherrack base fails the matching-Nylium growth target. [Nylium cover check][s29] · [Crimson Nylium random ticks][s30] · [Warped Nylium random ticks][s31] · [Required base][s32]

## Producing more small fungi

With an existing Nylium block, use Bone Meal **on the Nylium with air immediately above** to run its vegetation-placement route. Both Nylium variants have a weighted vegetation provider that includes **both fungus species**, alongside other vegetation. Finding an occasional opposite-colored fungus on that substrate is therefore consistent with these configurations; move it to its matching Nylium before attempting huge growth. This is a renewable route from an existing Nylium patch, not a claim about where to find the first Nylium naturally. [Nylium Bone Meal target and routes][s33] · [Crimson vegetation choices][s34] · [Warped vegetation choices][s35]

Each of those vegetation features makes **nine placement attempts** at the same height, with horizontal offsets from −2 to +2. A chosen position must be empty and the selected plant must survive there. Attempts can repeat positions or fail, so nine attempts are not nine fungi. The normal accepted Bone Meal use is consumed even if the sampled placements leave no useful fungus. Clear small plants to make room for later applications. [Vegetation placement loop][s36] · [Weighted choice callback][s37] · [Bone Meal consumption][s38]

## Sources and verification

Source-reviewed on **2026-10-02** at `e87cde38c872d30ae86139bbee181937603af769`. Both small-fungus registrations and loot tables, both planted huge-fungus configurations, both Nylium vegetation configurations, and their active callbacks were traced. The feature keys are registered, resolved from the configured-feature registry, and dispatched to their registered implementations. No in-game growth, obstruction, Bone Meal, Nylium-conversion, or yield test was run. This guide does not establish natural biome availability or a guaranteed fungus height. Data packs and server settings can change the selected data and behavior. [Huge-fungus feature registration][s39] · [Planting feature keys][s40] · [Vegetation feature keys][s41] · [Configured dispatch][s42]

Related: [Saplings and Azaleas](SaplingsAndAzaleas.md) · [Crimson Fungus item](../items/CrimsonFungus.md) · [Warped Fungus item](../items/WarpedFungus.md) · [Crimson Stem](TreeLogsAndRoots.md#crimson-timber) · [Warped Stem](TreeLogsAndRoots.md#warped-timber) · [Bone Meal](../items/BoneMeal.md) · [Blocks](Blocks.md)

[s1]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5530-L5534
[s2]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5475-L5479
[s3]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[s4]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[s5]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L69-L104
[s6]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L101-L192
[s7]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L53-L59
[s8]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[s9]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/nylium.json
[s10]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/dirt.json
[s11]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5530-L5534
[s12]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5475-L5479
[s13]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/crimson_fungus.json
[s14]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/warped_fungus.json
[s15]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[s16]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L61-L80
[s17]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[s18]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L61
[s19]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FungusBlock.java
[s20]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L61
[s21]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5530-L5534
[s22]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5475-L5479
[s23]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L61
[s24]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L60-L104
[s25]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L107-L155
[s26]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[s27]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[s28]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L40-L104
[s29]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L31-L44
[s30]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5519-L5529
[s31]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5474
[s32]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L65-L69
[s33]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L47-L87
[s34]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation_bonemeal.json
[s35]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation_bonemeal.json
[s36]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L18-L48
[s37]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/stateproviders/WeightedStateProvider.java#L33-L36
[s38]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[s39]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L122-L122
[s40]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/data/worldgen/features/TreeFeatures.java#L72-L75
[s41]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/data/worldgen/features/NetherFeatures.java#L33-L36
[s42]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
