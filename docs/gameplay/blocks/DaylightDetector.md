# Daylight Detector

A **Daylight Detector** (`minecraft:daylight_detector`) supplies a redstone signal from **0 to 15** based on local skylight, the world's sky darkening and, in normal mode, the sun angle. Interact with it to toggle normal and inverted modes. It measures the skylight channel; a nearby Torch or other block-light source does not replace missing skylight. [Signal calculation][daylight-calc] · [Mode switch][daylight-use] · [Light lookup][light-access]

## Crafting, collecting and placement

Craft **one detector from three ordinary Glass blocks, three Nether Quartz items and three wooden slabs**:

```text
Glass  Glass  Glass
Quartz Quartz Quartz
Slab   Slab   Slab
```

The recipe uses the exact Glass and Nether Quartz item IDs. Glass Panes, stained glass and Quartz Blocks do not substitute. Each slab slot accepts the wooden-slabs tag independently, allowing mixed accepted woods; Bamboo Slabs qualify, but Bamboo Mosaic Slabs do not. [Recipe][daylight-recipe] · [Accepted slabs][slabs]

An **axe speeds up breaking**, but there is no correct-tool requirement or minimum tier for the drop. Ordinary Survival hand harvesting returns one detector. The loot has no Silk Touch requirement, Fortune bonus or copied power/mode state, so ordinary replacement begins in normal mode at power 0. [Registration][daylight-registration] · [Axe tag][axe] · [Harvest gate][harvest] · [Loot][daylight-loot] · [Default state][daylight-state]

The detector is **6/16 of a block high**, with no facing property. It uses default-state placement. It has a block entity for ticking, but no item storage or player menu. [Shape and state][daylight-state] · [Placement][placement] · [Block entity][daylight-entity]

## Modes and update timing

Normal mode favors daylight. Inverted mode responds to reduced skylight after sky darkening, making it useful for a darkness-controlled circuit. A player allowed to build can switch modes with the normal use action; the server recalculates strength immediately on that switch. Redstone input does not toggle the mode. [Interaction handler][daylight-use]

Automatic updates run on the server **once every 20 game ticks**, at game-time multiples of 20, and only in dimension types with skylight enabled. A newly placed detector can therefore remain at its default 0 until the next eligible update. Loaded/ticking-world requirements still apply, and game ticks are not guaranteed wall-clock seconds. [Ticker gate and cadence][daylight-ticker] · [Loaded block-entity ticking][loaded-tick]

## What changes the output

The calculation starts with **local skylight minus sky darkening**. The normal mode then adjusts a positive value by the sun angle, rounds it and clamps the result to 0–15. Inverted mode instead subtracts the starting value from 15 and clamps it, **without the normal mode's sun-angle multiplication**. Consequently, inverted output is not generally `15 − normal output`. [Exact calculation][daylight-calc]

Time of day and the world's rain/thunder levels affect sky darkening. Nearby terrain or a roof can reduce the local skylight reaching the detector. The code reads light at the detector's own position, rather than simply testing whether you can see a sun texture or whether raindrops are falling in that biome. Moon phase is not an input to this calculation. [Sky darkening][sky-darken] · [Weather levels][weather] · [Time access][time-access] · [Light lookup][light-access]

These idealized values follow the formula; they are **not gameplay measurements or exact switching-time guarantees**:

| Assumed light and sky conditions at an update | Normal output | Inverted output |
| --- | ---: | ---: |
| Local skylight 15, clear noon angle, sky darkening 0 | 15 | 0 |
| Local skylight 15, clear midnight angle, sky darkening 11 | 0 | 11 |
| Local skylight 0, at any ordinary sky-darkening value | 0 | 15 |

An inverted detector in complete skylight shade can stay powered during the day. Weather can also change a circuit's switching behavior. Give a day/night installation a suitable skylight exposure, then choose its threshold using the signal conditions you actually need. [Detector formula][daylight-calc] · [Sky formula][sky-darken] · [Sun angle][sun-angle]

## Dimension limits

The current bundled dimension-type data matters more than a dimension's visual appearance:

| Dimension type | Automatic detector updates | Time behavior |
| --- | --- | --- |
| Overworld | Yes | Ordinary day-time angle |
| Overworld Caves | Yes | Ordinary day-time angle; actual skylight still depends on cover |
| The End | Yes | Fixed time 6000, so no ordinary daily sun-angle cycle |
| The Nether | No | Fixed time 18000 |
| Primordial Caves | No | Fixed time 18000 |

These are the five bundled **type definitions**, not a claim that every world uses every type. The current End type explicitly enables skylight. Its fixed noon angle does not guarantee strength 15 everywhere: local skylight and sky darkening still enter the calculation. See [The End](../dimensions/End.md), [The Nether](../dimensions/Nether.md) and [Primordial Caves](../dimensions/PrimordialCaves.md) for their broader mechanics. [Overworld][overworld] · [Overworld Caves][overworld-caves] · [End][end] · [Nether][nether] · [Primordial Caves][primordial] · [Fixed-time calculation][dimension-time]

**No automatic ticker does not mean every manually selected state outputs 0.** The interaction handler still recalculates when toggled in a no-skylight type. Its absent skylight engine returns 0, so the current source predicts **15 in inverted mode and 0 in normal mode** there. This manual behavior has not been tested in-game and does not provide a cycling daylight clock. [Ungated mode switch][daylight-use] · [Light-engine selection][light-create] · [Missing-layer lookup][light-engine] · [Zero light value][no-sky-light] · [Calculation][daylight-calc]

## Wiring and a darkness-controlled lamp

The detector reports its power in every queried direction, and neighboring supported [Redstone Dust](RedstoneDust.md) connects toward it. It has no separate container/comparator reading; a Comparator reads its ordinary rear signal. The block's direct/strong output remains 0, so connect to adjacent dust or a component instead of relying on power through a separate solid support block. [Signal output][daylight-state] · [Source flag][daylight-use] · [Wire connection][wire] · [Direct default][direct] · [Analog default][analog] · [Rear input][diode-input]

For a small source-derived example, place an exposed inverted detector beside a [Repeater](RedstoneRepeater.md), with the Repeater's rear toward the detector and a [Redstone Lamp](RedstoneLamp.md) at its front. Any positive detector input becomes a full-strength output after the Repeater's delay. This has not been tested in game. Cover or weather can turn it on outside the time you expect; for a chosen darkness threshold, use [Comparator compare mode](RedstoneComparator.md#compare-and-subtract-modes) with a suitable side reference before restoring the signal. [Detector signal][daylight-state] · [Rear-input test][diode-input] · [Comparator threshold][comparator]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`. No gameplay test of light levels, weather, dimension behavior, timing or circuits was run. Examples assume the stated source inputs; altered dimension types, world settings and light conditions can change outcomes.

Related: [Daylight Detector item](../items/DaylightDetector.md) · [Redstone basics](../redstone/Redstone.md) · [Redstone components](catalog/redstone.md) · [Blocks](Blocks.md)

[daylight-calc]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L59-L75
[daylight-use]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L77-L96
[light-access]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/BlockAndTintGetter.java#L14-L24
[daylight-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/daylight_detector.json
[slabs]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[daylight-registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2855-L2859
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[daylight-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/daylight_detector.json
[daylight-state]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L28-L57
[placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[daylight-entity]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/DaylightDetectorBlockEntity.java
[daylight-ticker]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L98-L120
[loaded-tick]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L440-L460
[sky-darken]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L639-L644
[weather]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L827-L849
[time-access]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/LevelTimeAccess.java#L5-L18
[sun-angle]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L431-L434
[overworld]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/dimension_type/overworld.json
[overworld-caves]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/dimension_type/overworld_caves.json
[end]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/dimension_type/the_end.json
[nether]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/dimension_type/the_nether.json
[primordial]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[dimension-time]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/dimension/DimensionType.java#L144-L155
[light-create]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/level/ChunkMap.java#L189-L191
[light-engine]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/lighting/LevelLightEngine.java#L96-L102
[no-sky-light]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/lighting/LayerLightEventListener.java#L15-L27
[wire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L381-L389
[direct]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L372-L374
[analog]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L232-L234
[diode-input]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L123-L143
[comparator]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L72-L117
