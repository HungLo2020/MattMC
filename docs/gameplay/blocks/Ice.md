# Ice, Packed Ice, Blue Ice and Frosted Ice

Use **Packed Ice** or **Blue Ice** for a slippery floor that does not melt under ordinary lighting. Use **Ice** when you specifically want its light-sensitive or water-producing behavior. **Frosted Ice** is the temporary block made by Frost Walker and cannot be harvested as an item. [Registrations][blocks] [items] · [Ice callbacks][ice] [frosted]

## Registered ice blocks

| Block | Registry ID | Friction value | Hardness / blast resistance | Light-sensitive melting |
| --- | --- | --- | --- | --- |
| [Ice](../items/Ice.md) | `minecraft:ice` | 0.98 | 0.5 / 0.5 | Random ticks |
| [Packed Ice](../items/PackedIce.md) | `minecraft:packed_ice` | 0.98 | 0.5 / 0.5 | No melting callback |
| [Blue Ice](../items/BlueIce.md) | `minecraft:blue_ice` | 0.989 | 2.8 / 2.8 | No melting callback |
| Frosted Ice | `minecraft:frosted_ice` | 0.98 | 0.5 / 0.5 | Scheduled aging and neighbor changes |

The larger friction value retains more horizontal motion in the movement calculation: Blue Ice is more slippery than the other three. Living-entity movement and a boat's ground-friction sampling both use block friction, but these values are not blocks-per-second speed promises. See [Transport](../mechanics/Transport.md) for vehicles. All four are full placed blocks without snow-style stacking or support-loss rules. [Properties][blocks] [properties] · [Movement][living] [boat]

## Harvesting and recipes

**Silk Touch** collects one Ice, Packed Ice or Blue Ice block in ordinary Survival harvesting with `doTileDrops` enabled. Without it, each has **no item drop**, and Fortune does not help. A pickaxe is the efficient mining tool, but these three blocks do **not** require a correct tool for drops: the loot condition is Silk Touch, not a pickaxe material tier. Frosted Ice has an empty loot table even with Silk Touch and no registered ordinary item; its pick-block result is also empty. [Loot][loot-ice] [loot-packed-ice][] [loot-blue-ice][] [loot-frosted-ice] · [Mining and registration][pickaxe] [blocks][] [items][] [player-tool][] [break-dispatch][] · [Frosted pick result][frosted] · [Block-drop rule][block] [rules]

| Ingredients | Result |
| --- | --- |
| 9 Ice in a crafting grid | 1 Packed Ice |
| 9 Packed Ice in a crafting grid | 1 Blue Ice |

Both recipes are shapeless, though nine inputs fill the whole 3 × 3 grid. Starting with ordinary Ice therefore costs **81 Ice per Blue Ice**. The bundled recipes do not unpack Packed/Blue Ice, craft ordinary Ice, or craft Frosted Ice. [Packing recipes][recipe-packed-ice] [recipe-blue-ice] · [Recipe loading][recipes]

Natural examples in the normal Overworld include **Packed Ice spikes in Ice Spikes** and **Packed/Blue Ice iceberg features in Frozen Ocean**. The registered spike feature places Packed Ice; each iceberg's configured state supplies its ice type. These are source-verified acquisition examples, not exhaustive location or yield charts. [Normal preset and biome selection][normal-preset] [biome-parameters][] [overworld-biomes][] · [Ice Spikes route][spikes-biome] [placed-feature-ice-spike][] [configured-feature-ice-spike][] [spike-worldgen] · [Frozen Ocean routes][frozen-ocean] [placed-feature-iceberg-packed][] [configured-feature-iceberg-packed][] [placed-feature-iceberg-blue][] [configured-feature-iceberg-blue][] [iceberg-worldgen] · [Feature dispatch][features] [biome-generation][] [placed-feature][]

Blue Ice also participates in the verified [basalt-making interaction](BlackstoneAndBasalt.md#making-basalt-with-lava); that guide owns the required layout and conditions.

## Ordinary Ice: melting and breaking

Ice melts on a **random tick at block light 11 or higher**. The exact test is block light greater than `11 − light blocking`; ordinary Ice's full shape and non-occluding properties give light blocking **1**. It checks emitted block light, not sky light or biome warmth. In an ordinary dimension, the melt replaces Ice with a water-source block; in an **ultra-warm dimension**, it removes the Ice without leaving water. [Ice melt callback][ice] · [Registration and light blocking][blocks] [properties]

Survival breaking without an enchantment in `prevents_ice_melting` uses a separate rule. In a non-ultra-warm dimension, it leaves water **only if the block below blocks motion or is a liquid**. Unsupported Ice can disappear without leaving water. In an ultra-warm dimension, the break leaves no water. The current prevention tag contains **Silk Touch**, so a Silk Touch harvest preserves the item without this water replacement. Packed Ice and Blue Ice have no such breaking-water callback. [Breaking callback][ice] · [Prevention tag][ice-enchantment] · [Survival dispatch][break-dispatch] · [Packed/Blue registrations][blocks]

`randomTickSpeed = 0` stops ordinary Ice's random-tick melting. It does not turn Frosted Ice into permanent construction, because Frosted Ice uses the separate scheduled-tick system. [Random dispatch][chunk-tick] [random-tick] · [Frosted registration and scheduling][blocks] [frosted][] [scheduled-tick][]

## Freezing ordinary Ice

The server's weather update can freeze an exposed surface **water source** into Ice when:

- The location's adjusted biome temperature is below **0.15**
- The target is inside build height and has **block light below 10**
- It is an actual water block with the source-water fluid type, rather than flowing water or a waterlogged building block
- At least one of its four horizontal neighbors is not water

This freezing check runs before the precipitation check, so it **does not require current rain or snowfall**. The update samples the top position from the motion-blocking heightmap and tests the block immediately below it. A roof changes which surface is considered. The normal `randomTickSpeed`-controlled sampling is probabilistic, not a fixed freeze timer. [Biome freezing test][biome] · [Surface/weather dispatch][weather] [chunk-tick][] [random-tick][]

World generation is separate: the cold-biome `freeze_top_layer` feature calls the same freezing test with the horizontal-neighbor restriction disabled. Existing Ice terrain therefore need not reflect the exact edge-only weather rule. [Configured generation][placed-feature-freeze-top-layer] [configured-feature-freeze-top-layer][] [features][] [snow-worldgen]

## Frosted Ice and Frost Walker

The active **Frost Walker** enchantment replaces eligible surface water beneath an on-ground wearer who is **not riding another entity**. At the ordinary enchantment levels, its disk radius is **3 blocks at level I** and **4 at level II**. It requires air above, an actual water block with source-water fluid, and an unobstructed replacement. The effect is attached to the feet slot and runs through the living entity's location-change enchantment path. See [Enchanting](../enchanting/Enchanting.md) for equipment enchantments. [Enchantment data][frost-walker] · [Active dispatch][living] [enchant-helper][] [enchant][] [enchant-effects][] [replace-disk][]

Frosted Ice starts at age **0** and schedules its first update **60–120 game ticks** after placement. Further checks are generally scheduled **20–40 game ticks** apart. At 20 ticks per second these intervals are nominally **3–6 seconds**, then **1–2 seconds**, but they are **not a guaranteed lifetime**. [Placement and tick callback][frosted] · [Scheduled dispatch][scheduled-tick]

On a scheduled check, aging requires **either a one-in-three random check or fewer than four neighboring Frosted Ice blocks**, together with brightness of at least **`11 − age`**. It counts all six adjacent directions. Brightness includes local sky light outside the End; in the End this callback uses block light only. An eligible age 0, 1 or 2 block advances by one; an eligible age 3 block melts. [Aging and light tests][frosted] · [Light-blocking properties][blocks] [properties]

Melting can advance neighboring Frosted Ice too. A separate Frosted-Ice neighbor notification melts a block with **fewer than two Frosted Ice neighbors**, without waiting for the light test. This makes a damaged or disconnected patch unreliable even in the dark. As with ordinary Ice's melt callback, the result is water outside ultra-warm dimensions and disappearance inside them. Frosted Ice has **no random-tick registration**, so changing `randomTickSpeed` does not disable these scheduled and neighbor paths. [Aging propagation and neighbor callback][frosted] · [Inherited melting][ice] · [Registration][blocks]

## A small floor example

**Source-based example, not tested in gameplay:** craft nine Ice into one Packed Ice and place a short, contained strip with solid blocks around its edges. Use Packed Ice where lamps will illuminate the floor; use Blue Ice if you want its higher slipperiness and can afford the extra material. Bring Silk Touch to recover either floor. If decorating it with [Snow layers](Snow.md#placing-layers-and-keeping-their-support), remember that Snow rejects Packed Ice as support but can stand on Blue Ice.

## Related pages

- [Snow, Snow Blocks and Powder Snow](Snow.md)
- [Transport](../mechanics/Transport.md), [Enchanting](../enchanting/Enchanting.md), and [Basalt](BlackstoneAndBasalt.md#basalt-variants-and-orientation)
- [Blocks](Blocks.md) and [Items](../items/Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. Registered classes/properties, item absence, recipes, loot, light calculations, active weather/random/scheduled dispatch, natural-feature routes and Frost Walker's effect path were checked. No gameplay harvesting, melting, freezing or vehicle-speed test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/IceBlock.java
[frosted]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FrostedIceBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[living]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/LivingEntity.java
[boat]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java
[loot-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/ice.json
[loot-packed-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/packed_ice.json
[loot-blue-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/blue_ice.json
[loot-frosted-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/frosted_ice.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[player-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Block.java
[rules]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/GameRules.java
[recipe-packed-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/recipe/crafting/packed_ice.json
[recipe-blue-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/recipe/crafting/blue_ice.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biome-parameters]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[spikes-biome]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/biome/ice_spikes.json
[placed-feature-ice-spike]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/ice_spike.json
[configured-feature-ice-spike]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/ice_spike.json
[spike-worldgen]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/IceSpikeFeature.java
[frozen-ocean]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/biome/frozen_ocean.json
[placed-feature-iceberg-packed]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/iceberg_packed.json
[configured-feature-iceberg-packed]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/iceberg_packed.json
[placed-feature-iceberg-blue]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/iceberg_blue.json
[configured-feature-iceberg-blue]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/iceberg_blue.json
[iceberg-worldgen]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/IcebergFeature.java
[features]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[biome-generation]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[placed-feature]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java
[ice-enchantment]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/enchantment/prevents_ice_melting.json
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L375-L405
[random-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L474-L515
[scheduled-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[biome]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/Biome.java#L109-L209
[weather]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L555-L592
[placed-feature-freeze-top-layer]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/freeze_top_layer.json
[configured-feature-freeze-top-layer]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/freeze_top_layer.json
[snow-worldgen]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/SnowAndFreezeFeature.java
[frost-walker]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/enchantment/frost_walker.json
[enchant-helper]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L251-L266
[enchant]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java
[enchant-effects]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/enchantment/effects/EnchantmentLocationBasedEffect.java
[replace-disk]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/enchantment/effects/ReplaceDisk.java
