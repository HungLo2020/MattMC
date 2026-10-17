# Glowstone, Sea Lanterns, Shroomlights, and Froglights

These seven full blocks give steady **light level 15**. Their most useful differences are how you obtain them and whether mining preserves the block. For small floor or ceiling fixtures, see [Lanterns](Lanterns.md); [Torches](Torch.md), [Redstone Lamps](RedstoneLamp.md), and [Copper Lighting](CopperLighting.md) keep their own recipes and controls.

## Blocks and light

Each listed ID has a matching inventory item. The light value is constant in its registration: it does not require redstone power, fuel, darkness, or nearby water. [Item registrations][items] · [Glowstone][reg-glow] · [Sea Lantern][reg-sea] · [Shroomlight][reg-shroom] · [Froglights][reg-frogs]

| Block ID | Light | Hardness | Ordinary mining result |
| --- | ---: | ---: | --- |
| `minecraft:glowstone` | 15 | 0.3 | 2–4 Glowstone Dust; Silk Touch preserves the block |
| `minecraft:sea_lantern` | 15 | 0.3 | 2–3 Prismarine Crystals; Silk Touch preserves the block |
| `minecraft:shroomlight` | 15 | 1.0 | 1 Shroomlight |
| `minecraft:ochre_froglight` | 15 | 0.3 | 1 Ochre Froglight |
| `minecraft:verdant_froglight` | 15 | 0.3 | 1 Verdant Froglight |
| `minecraft:pearlescent_froglight` | 15 | 0.3 | 1 Pearlescent Froglight |
| `minecraft:carmine_froglight` | 15 | 0.3 | 1 Carmine Froglight; starting Survival supply is unverified |

The two resource-drop counts above are without Fortune. These blocks have **full-block collision**, no gravity or ongoing attachment requirement, and no waterlogged state. They can form solid lighting blocks beside water, without storing Water in the same block. None burns down with time. [Block defaults][default] · [Registrations][reg-glow] [reg-sea][] [reg-shroom][] [reg-frogs]

The four Froglights use `RotatedPillarBlock`: clicking a top/bottom face places the texture axis vertically; clicking an east/west or north/south face sets that horizontal axis. Orientation does not change the registered light level. Glowstone, Sea Lantern, and Shroomlight have no placement-axis property. [Pillar placement][pillar] · [Froglight registration][reg-frogs]

## Tools, Silk Touch, and Fortune

**None of these seven registrations has a required-tool drop gate.** Hand mining can produce the ordinary result. A hoe is the tagged efficient tool for Shroomlight; the other six have no matching entry in the bundled ordinary axe, pickaxe, shovel, or hoe mining tags. Silk Touch's loot conditions on Glowstone and Sea Lantern check the enchantment, not a particular pickaxe material. [Registrations][reg-glow] [reg-sea][] [reg-shroom][] [reg-frogs] · [Player harvest gate][gate] · [Active mining dispatch][harvest] · [Mining tags][hoe] [axe][] [pickaxe][] [shovel] · [Loot details below](#glowstone)

Normal item spawning follows `doTileDrops`. The listed mining counts are not explosion guarantees: dust/crystal branches apply explosion decay, and ordinary self-drop tables use their own conditions. Carmine's bundled self-drop table is notably simpler and has no explosion-survival condition; that does not create a starting acquisition route. [Drop dispatch and game rule][drops] · [Carmine loot][loot-carmine_froglight]

## Glowstone

Mine **`minecraft:glowstone`** with Silk Touch to recover **one Glowstone block**. Otherwise its loot starts with a uniform **2–4 Glowstone Dust**, adds a uniform bonus from **0 to the Fortune level**, then caps the result at **4** before explosion handling. Fortune improves the chance of a larger result; even Fortune III does not guarantee four Dust and never raises this ordinary drop above four. [Loot][loot-glowstone] · [Fortune formula][fortune]

Craft **four Glowstone Dust in a 2 × 2 square → one Glowstone**, using the inventory grid or a Crafting Table. Mining and recrafting can therefore lose material when fewer than four Dust drop. Keep the [Glowstone Dust item](../items/GlowstoneDust.md) and [Brewing](../brewing/Brewing.md) pages for dust-specific uses. [Block recipe][recipe-glowstone]

### Finding Glowstone

The normal preset's Nether uses the active Nether biome preset. All five bundled Nether biomes install `glowstone` and `glowstone_extra` placements, which resolve the registered Glowstone cluster feature. Its first block requires air beneath Netherrack, Basalt, or Blackstone, then its randomized additions build a cluster below and around that point. This is a generation condition; a block you place yourself does not need that ceiling. [Normal preset][normal] · [Nether biome selection][normal-biomes] · [Nether Wastes][biome-nether_wastes] · [Crimson Forest][biome-crimson_forest] · [Warped Forest][biome-warped_forest] · [Soul Sand Valley][biome-soul_sand_valley] · [Basalt Deltas][biome-basalt_deltas] · [Placements][placed-glowstone] [placed-glowstone_extra] · [Configuration][feature-glowstone_extra] · [Feature implementation][glow-feature]

Trades provide another source. A level-3 **Journeyman Cleric** has a configured **4 Emeralds → 1 Glowstone** offer, with 12 uses before replenishment. Wandering Trader stock can include **2 Emeralds → 1 Glowstone**, with five uses. These are base prices; use the [Villager guide](../mobs/Villager.md) for trading rules. The trader chooses from a pool, so the offer is not guaranteed on every trader. [Trade definitions][trades] · [Villager selection][villager] · [Trader selection][trader] [trade-choice]

### Other block recipes

Glowstone is also an ingredient in **one Redstone Lamp** (one Glowstone plus four Redstone Dust in a cross) and **one Respawn Anchor** (three Glowstone in the middle row between two rows of three Crying Obsidian). Keep operation and charging in [Redstone Lamp](RedstoneLamp.md) and [Respawn Anchor](RespawnAnchor.md). [Lamp recipe][recipe-redstone_lamp] · [Anchor recipe][recipe-respawn_anchor]

## Sea Lantern

Mine **`minecraft:sea_lantern`** with Silk Touch to recover **one Sea Lantern**. Otherwise it starts with a uniform **2–3 Prismarine Crystals**, adds a uniform **0 to Fortune level**, and caps the count at **5** before explosion handling. Its ordinary loot never returns Prismarine Shards. [Loot][loot-sea_lantern] · [Fortune formula][fortune]

| Tool enchantment | Possible ordinary Crystal count |
| --- | ---: |
| No Fortune or Silk Touch | 2–3 |
| Fortune I | 2–4 |
| Fortune II | 2–5 |
| Fortune III | 2–5 |
| Silk Touch | 1 Sea Lantern instead of Crystals |

Craft **four Prismarine Shards in the corners and five Prismarine Crystals in the remaining slots → one Sea Lantern**. This requires the 3 × 3 crafting grid. Even a five-Crystal mining result does not refund the four Shards used to make the block. [Recipe][recipe-sea_lantern] · [Loot][loot-sea_lantern]

**Ocean Monuments** are a verified placed-block source: their active structure configuration is in the Ocean Monuments structure set, and the generator's room/building pieces explicitly place Sea Lanterns. Eligible start biomes are the four tagged deep-ocean biomes, with an additional surrounding-biome check. This is not a guarantee that every deep-ocean location contains a monument. [Structure set][monument-set] · [Structure configuration][monument-data] · [Eligible biomes][monument-biomes] [deep-oceans] · [Normal structure-set selection][structure-sets] · [Generator][monument] · [Lantern placement][monument-pieces]

[Guardians](../mobs/Guardian.md) and [Elder Guardians](../mobs/ElderGuardian.md) provide the separate Prismarine ingredient routes; their loot tables include Shards and a possible Crystals branch. The ingredient drops remain subject to the individual loot branches. [Guardian loot][entity-loot-guardian] · [Elder Guardian loot][entity-loot-elder_guardian]

## Shroomlight

**`minecraft:shroomlight`** drops **one Shroomlight** by hand or tool; Silk Touch is unnecessary and Fortune adds nothing. A hoe speeds harvesting. The block has no Bone Meal growth callback of its own and no bundled crafting recipe. [Registration][reg-shroom] · [Loot][loot-shroomlight] · [Hoe tag][hoe]

Shroomlights are possible cap decorations of both huge Crimson and huge Warped fungi. In the normal Nether, the Crimson/Warped Forest biome features reach their respective natural huge-fungus configurations, both of which name Shroomlight as the decoration. The active cap-placement code chooses those decorations randomly where placement is possible. [Forest features][biome-crimson_forest] [biome-warped_forest] · [Placed fungi][placed-crimson_fungi] [placed-warped_fungi] · [Natural configurations][feature-crimson_fungus] [feature-warped_fungus] · [Cap placement][huge-feature]

For a repeatable source, use [Crimson or Warped Fungi](NetherFungi.md#bone-meal-and-huge-growth) on their matching Nylium and apply Bone Meal. Both **planted** huge-fungus features also include Shroomlight. The accepted application has a 40% growth-attempt roll and can consume Bone Meal without a useful result; a successful fungus does not promise a fixed Shroomlight yield. Harvest the generated lights with a hoe. [Fungus registration][fungus-reg] [crimson-reg] · [Growth callback][fungus] · [Consumption][meal] · [Planted configurations][feature-crimson_fungus_planted] [feature-warped_fungus_planted]

## Froglights

All four Froglights drop **one matching block**, including by hand. None has a bundled crafting recipe. Silk Touch and Fortune do not change those self-drops. The blocks have the same light level and placement-axis behavior, despite their different names and textures. [Registrations][reg-frogs] · [Ochre loot][loot-ochre_froglight] · [Verdant loot][loot-verdant_froglight] · [Pearlescent loot][loot-pearlescent_froglight] · [Carmine loot][loot-carmine_froglight]

### Ochre, Verdant, and Pearlescent

The verified ordinary production route is a **Frog killing a smallest-size Magma Cube** through its active eating behavior. The Magma Cube loot then produces one light according to the attacking Frog's variant:

| Frog variant | Block obtained |
| --- | --- |
| Temperate | `minecraft:ochre_froglight` |
| Cold | `minecraft:verdant_froglight` |
| Warm | `minecraft:pearlescent_froglight` |

The Frog needs access to the prey; a larger cube or an unreachable one is not this route. Feeding a Frog a Slimeball breeds it, rather than directly producing a light. The three light branches have no Looting bonus. See [Frogs](../mobs/Frog.md#variants-and-where-to-grow-tadpoles) for variants and [Magma Cubes](../mobs/MagmaCube.md#drops-and-froglights) for the canonical drop rules. [Prey tag][frog-food] · [Size restriction][frog] · [Active behavior][frog-ai] [tongue] · [Magma Cube loot][entity-loot-magma_cube]

### Carmine Froglight

**`minecraft:carmine_froglight`** is an actual registered building block with an inventory item, Creative-tab entry, light level 15, the same axis placement as the other Froglights, and a one-block self-drop. [Registration][reg-frogs] · [Item][items] · [Creative entry][creative] · [Loot][loot-carmine_froglight]

**A starting Survival acquisition route is unverified in the checked source.** The Magma Cube table has only the three mappings above, with no Carmine result. The audit found no Carmine crafting recipe, additional production Java/data-JSON reference, or identifier in the 1,202 decompressed bundled structure templates. Its self-drop only recovers a block that already exists; it does not establish how to obtain the first one. Do not assume a fourth Frog variant or a [Rain Frog](../mobs/RainFrog.md) interaction supplies it. [Checked Frog-result table][entity-loot-magma_cube] · [Self-drop][loot-carmine_froglight]

## Building example and related guides

A source-based example, **not gameplay-tested**: use a Sea Lantern as a full lighting block in an underwater wall and ordinary Lanterns under a supported dry walkway. Bring a Silk Touch tool if you plan to move the Sea Lantern later; the Lanterns can be recovered by hand in this source. Use horizontally placed Froglights when you want their pillar texture running along a beam. These source light values do not establish a guaranteed spawn-proof radius. [Sea Lantern loot][loot-sea_lantern] · [Lantern guide](Lanterns.md) · [Froglight axis][pillar]

Related: [Torch](Torch.md) · [Copper Lighting](CopperLighting.md) · [Lanterns](Lanterns.md) · [Nether Fungi](NetherFungi.md) · [Frog](../mobs/Frog.md) · [Glowstone Dust](../items/GlowstoneDust.md)

## Sources and verification

Reviewed against pinned MattMC registrations, active block/feature/AI paths, recipes, tags, loot, and generation data on **2026-10-02**. Carmine availability is deliberately source-qualified. No gameplay placement, lighting-radius, growth, drop-rate, or farm test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java
[reg-glow]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2053-L2062
[reg-sea]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3179-L3188
[reg-shroom]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5535-L5537
[reg-frogs]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L6734-L6753
[default]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L338
[pillar]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[gate]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[hoe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[axe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[drops]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[loot-carmine_froglight]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/carmine_froglight.json
[loot-glowstone]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/glowstone.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L170
[recipe-glowstone]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/glowstone.json
[normal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[normal-biomes]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[biome-nether_wastes]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[biome-crimson_forest]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json
[biome-warped_forest]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[biome-soul_sand_valley]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json
[biome-basalt_deltas]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/basalt_deltas.json
[placed-glowstone]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/glowstone.json
[placed-glowstone_extra]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/glowstone_extra.json
[feature-glowstone_extra]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/glowstone_extra.json
[glow-feature]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/GlowstoneFeature.java
[trades]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L843
[trader]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[recipe-redstone_lamp]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/redstone_lamp.json
[recipe-respawn_anchor]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/respawn_anchor.json
[loot-sea_lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/sea_lantern.json
[recipe-sea_lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/sea_lantern.json
[monument-set]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/structure_set/ocean_monuments.json
[monument-data]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/structure/monument.json
[monument-biomes]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_monument.json
[deep-oceans]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/worldgen/biome/is_deep_ocean.json
[structure-sets]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L66
[monument]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentStructure.java
[monument-pieces]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java
[entity-loot-guardian]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/entities/guardian.json
[entity-loot-elder_guardian]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json
[loot-shroomlight]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/shroomlight.json
[placed-crimson_fungi]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/crimson_fungi.json
[placed-warped_fungi]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/warped_fungi.json
[feature-crimson_fungus]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus.json
[feature-warped_fungus]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus.json
[huge-feature]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java
[fungus-reg]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5475-L5479
[crimson-reg]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5530-L5534
[fungus]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/FungusBlock.java
[meal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[feature-crimson_fungus_planted]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[feature-warped_fungus_planted]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[loot-ochre_froglight]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/ochre_froglight.json
[loot-verdant_froglight]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/verdant_froglight.json
[loot-pearlescent_froglight]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/pearlescent_froglight.json
[frog-food]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/entity_type/frog_food.json
[frog]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L357-L359
[frog-ai]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/animal/frog/FrogAi.java
[tongue]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/animal/frog/ShootTongue.java
[entity-loot-magma_cube]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/entities/magma_cube.json
[creative]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1017-L1020
