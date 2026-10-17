# Sculk, veins and catalysts

Bring a **Silk Touch hoe** to collect the sculk family. Without Silk Touch, ordinary mining gives experience from Sculk and its devices, but no block items. A **Sculk Catalyst** turns eligible nearby deaths into spreading sculk; the ordinary Sculk block and Vein do not start that process on their own. [Loot][loot-sculk] · [Hoe tag][hoe] · [Catalyst listener][catalyst]

## Sculk

**Sculk** (`minecraft:sculk`) is a full building block and a substrate for catalyst-driven growth. Its hardness and blast resistance are both **0.2**. It has no vibration detector or redstone-output override: use a [Sculk Sensor](SculkSensors.md#sculk-sensor) for detection. Breaking it without Silk Touch normally gives **1 XP**, while Silk Touch collects one block and suppresses that XP. [Registration][blocks] · [Sculk behavior][sculk] · [Loot][loot-sculk] · [XP dispatch][drop-xp] · [Silk Touch XP effect][silk]

## Sculk Vein

**Sculk Vein** (`minecraft:sculk_vein`) is a thin, non-colliding covering with hardness and blast resistance **0.2**. One position can hold faces on its floor, ceiling and walls. Each face needs a full supporting or collision face behind it; further placement can add vacant supported faces to the same position. Losing support removes the affected face, and losing the last face removes the covering. Collect veins before removing their supports. [Registration][blocks] · [Placement and support loss][faces] · [Exact support test][support]

Veins can be waterlogged, including when placed into source Water. Silk Touch yields **one Vein item per occupied face** at the broken position, before any explosion decay. Shears have no special item-drop branch here; without Silk Touch the loot is empty. Veins have no XP-drop callback. They also have the piston **destroy** reaction, so a piston does not carry them intact. [Face-count loot][loot-sculk_vein] · [Placement][faces] · [Class and behavior][vein] · [Properties][blocks]

## Sculk Catalyst

**Sculk Catalyst** (`minecraft:sculk_catalyst`) has hardness and blast resistance **3**, emits **light level 6**, and listens for living-entity deaths within an **8-block radius**. The event-range check uses the distance between the event's and listener's block positions. A handled death makes it bloom for **8 game ticks**. This is a death-event listener, separate from the wool-occluded sensor vibration system. [Properties][blocks] · [Listener and bloom][catalyst] · [Range calculation][range]

The living entity must not already have had its experience consumed. If it is eligible to drop experience and its calculated reward is positive, the catalyst starts spread charge equal to that reward at the death location. It then marks that death's XP consumed, preventing the normal XP-orb reward from also appearing. The catalyst does not collect XP orbs already on the ground, and its charge check does not require a player kill. Multiple catalysts receive the event in distance order; the consumed flag prevents each from receiving a fresh copy of the same reward. [Active death event][death] · [Catalyst conditions][catalyst] · [Normal XP path][death-loot] · [Distance-order dispatch][distance-delivery]

### What spreads

The active catalyst ticker moves charge through sculk and supported veins. A vein can replace a block behind one of its faces with Sculk, spending one charge for that conversion. The bundled replaceable tag includes ordinary Overworld/Nether base stone, dirt, terracotta and nylium families, plus listed materials such as Sand, Gravel, Clay, Calcite and End Stone. It is a restricted list, not permission to convert every building material. World generation adds some worked Deepslate blocks to a separate tag; catalyst growth does not use those extra entries. [Server ticker][catalyst-tick] · [Spreader caller][catalyst-update] · [Charge update][spread-tick] · [Conversion][vein] · [Catalyst targets][replaceable] · [World-generation targets][replaceable-world]

For a small growing area, provide eligible ground around the death position and room for supported veins to spread. Conversion, movement and decay are randomized, so one point of a mob's XP does not guarantee one recoverable Sculk block. The spreader also limits itself to **32 charge cursors**, each holding at most **1,000** newly assigned charge; that is an internal capacity limit, not a farm yield promise. [Movement checks][spread-movement] · [Charge limits][spread-cap] · [Growth and decay][growth]

Charged Sculk can grow an ordinary Sensor or Shrieker above itself when:

- The Sculk is at least **4 blocks** from the catalyst's position
- The space above is Air or Water
- The checked **9 × 3 × 9** area around that Sculk contains at most two ordinary Sensors/Shriekers
- The randomized charge/growth checks succeed

A growth attempt uses a **10-charge cost** in the catalyst spreader. When a growth state is selected, **1 in 11** choices are Shriekers and the rest Sensors. This is conditional on reaching that growth step, not a chance per death. Catalyst-grown Shriekers have `can_summon=false`; the growth path does not create Catalysts or Calibrated Sensors. [Catalyst spread settings][spread-settings] · [Growth checks and selection][growth]

## Finding and collecting the family

The bundled Normal Overworld's biome selection includes **Deep Dark**. Its feature list runs Sculk Vein and Sculk Patch placement: the patch implementation spreads sculk, may grow Sensors/Shriekers, and may place a Catalyst. These are attempts with terrain and space checks, not a guarantee that every Deep Dark area contains every form. [Normal preset][normal] · [Biome selection][normal-biomes] · [Deep Dark features][deep] · [Placed patch][patch-placed] · [Patch configuration][patch-config] · [Registered feature][features] · [Active placement][patch] · [Vein configuration][vein-config] · [Biome feature caller][biome-place]

**Ancient Cities** are another checked route. Their registered placement set and Deep Dark eligibility lead to a jigsaw structure. The optional Barracks piece has connectors to the sculk pool, which can select the Ancient City Sculk Patch feature. The same Barracks template assigns the city chest loot table to its chests. This establishes a placed-feature and chest route without promising a Barracks in every city. [Placement set][city-set] · [City and start pool][city] · [Eligible biome][city-biome] · [Jigsaw caller][jigsaw] · [Structure-pool wall connector][wall-connector] · [Barracks pool entry][city-pool] · [Barracks template][barracks] · [Sculk pool][city-sculk] · [Feature-pool caller][feature-pool]

A selected entry in the bundled ordinary Ancient City chest table gives **4–10 Sculk**, **1–2 Catalysts**, or **1–3 Sensors**. These are quantities for each selected entry, not guaranteed chest contents. A Warden's ordinary loot table also contains **one Catalyst**, with no Looting increase; normal entity-loot/game-rule restrictions still apply. [City Sculk and Sensor entries][chest-sculk] · [Catalyst entry][chest-catalyst] · [Warden registration][warden-registration] · [Default loot-key mapping][loot-key] · [Entity table lookup][entity-loot] · [Death-loot dispatch][death-loot] · [Warden loot][warden-loot]

The recipe inventory has a direct output recipe for **Calibrated Sculk Sensor** only among these six IDs. Use [the Amethyst recipe guide](Amethyst.md#shard-recipes-and-other-uses) for its ingredients and arrangement. Placing ordinary Sculk or a Vein is not itself a renewable growth trigger.

## Mining and experience

All six blocks are in the **hoe mining-speed tag**, and none of their registrations requires a particular correct-tool tier for drops. Silk Touch is a separate loot condition. Use an unbroken Silk Touch hoe for efficient collection; [Mining](../mechanics/Mining.md) explains the distinction between speed and drop requirements. [Registrations][blocks] · [Hoe tag][hoe] · [Player drop gate][player-tool] · [Mining caller][mine]

| Broken block | With Silk Touch | Without Silk Touch |
| --- | --- | --- |
| Sculk | 1 Sculk, 0 XP | No item, 1 XP |
| Sculk Vein | 1 item per occupied face, 0 XP | No item, 0 XP |
| Sculk Catalyst | 1 Catalyst, 0 XP | No item, 5 XP |
| Sculk Sensor | 1 Sensor, 0 XP | No item, 5 XP |
| Calibrated Sculk Sensor | 1 Calibrated Sensor, 0 XP | No item, 5 XP |
| Sculk Shrieker | 1 Shrieker, 0 XP | No item, 5 XP |

These are ordinary player-mining outcomes with block drops enabled. Fortune has no count bonus in these tables. The calibrated variant inherits the Sensor XP callback; its crafted origin does not remove its Silk Touch collection requirement. Silk Touch sets block XP to zero. [Sculk loot][loot-sculk] · [Vein loot][loot-sculk_vein] · [Catalyst loot][loot-sculk_catalyst] · [Sensor loot][loot-sculk_sensor] · [Calibrated loot][loot-calibrated_sculk_sensor] · [Shrieker loot][loot-sculk_shrieker] · [Catalyst XP][catalyst-tick] · [Sensor XP][sensor-comparator] · [Shrieker XP][shrieker-place] · [XP processing][xp] · [XP game-rule gate][xp-gate] · [Silk Touch effect][silk]

## Sources and verification

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`. Checked all six registrations, loot/XP paths, direct producing recipes, catalyst listener/ticker, replacement and growth rules, Deep Dark features, and Ancient City template/pool/chest wiring. No in-game generation, harvesting, XP, growth-rate or farm test was run. Data packs, game rules, loaded chunks and server ticking can change the relevant results.

Related: [Sensors and calibration](SculkSensors.md) · [Shriekers](SculkShrieker.md) · [Sculk item](../items/Sculk.md) · [Vein item](../items/SculkVein.md) · [Catalyst item](../items/SculkCatalyst.md) · [Blocks](Blocks.md)

[barracks]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[biome-place]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L398
[blocks]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L6062-L6096
[catalyst]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/SculkCatalystBlockEntity.java#L57-L117
[catalyst-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkCatalystBlock.java#L42-L66
[catalyst-update]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/SculkCatalystBlockEntity.java#L29-L39
[chest-catalyst]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json#L38-L53
[chest-sculk]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json#L166-L197
[city]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/structure/ancient_city.json#L1-L48
[city-biome]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ancient_city.json#L1-L5
[city-pool]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json#L1-L18
[city-sculk]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/sculk.json#L1-L19
[city-set]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/structure_set/ancient_cities.json#L1-L14
[death]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1429-L1435
[death-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[deep]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json#L75-L78
[distance-delivery]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/GameEventDispatcher.java#L32-L66
[drop-xp]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DropExperienceBlock.java#L29-L34
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[faces]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L124-L218
[feature-pool]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/structure/pools/FeaturePoolElement.java#L82-L96
[features]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L156
[growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkBlock.java#L28-L93
[hoe]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L23
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L134-L152
[loot-calibrated_sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/calibrated_sculk_sensor.json#L1-L33
[loot-key]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-sculk]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk.json#L1-L33
[loot-sculk_catalyst]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_catalyst.json#L1-L33
[loot-sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_sensor.json#L1-L33
[loot-sculk_shrieker]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_shrieker.json#L1-L33
[loot-sculk_vein]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_vein.json#L1-L127
[mine]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L278-L293
[normal]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L13
[normal-biomes]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L815-L831
[patch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java#L21-L77
[patch-config]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/configured_feature/sculk_patch_deep_dark.json#L1-L12
[patch-placed]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/placed_feature/sculk_patch_deep_dark.json#L1-L27
[player-tool]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656
[range]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/EuclideanGameEventListenerRegistry.java#L114-L122
[replaceable]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/sculk_replaceable.json#L1-L21
[replaceable-world]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/sculk_replaceable_world_gen.json#L1-L11
[sculk]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkBlock.java#L15-L99
[sensor-comparator]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L261-L291
[shrieker-place]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkShriekerBlock.java#L118-L149
[silk]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/enchantment/silk_touch.json#L6-L15
[spread-cap]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSpreader.java#L119-L160
[spread-movement]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSpreader.java#L306-L351
[spread-settings]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSpreader.java#L36-L85
[spread-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSpreader.java#L249-L287
[support]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L246-L264
[vein]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkVeinBlock.java#L69-L132
[vein-config]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/configured_feature/sculk_vein.json#L1-L21
[wall-connector]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/structure/ancient_city/walls/intact_horizontal_wall_1.nbt
[warden-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/entities/warden.json#L1-L16
[warden-registration]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L1483-L1492
[xp]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Block.java#L584-L589
[xp-gate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Block.java#L410-L422
