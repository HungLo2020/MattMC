# Daylight Detector

The **Daylight Detector item** (`minecraft:daylight_detector`) places a redstone source that reads local skylight and sky conditions. Its normal/inverted modes, dimension limits and circuit use are documented in the [Daylight Detector block guide](../blocks/DaylightDetector.md#daylight-detector). [Item registration][items] · [Calculation][daylight-calc]

## Obtaining and placing

Craft **one detector with three ordinary Glass blocks across the top row, three Nether Quartz items across the middle and three wooden slabs across the bottom**. Accepted slab woods may be mixed. Glass Panes, Quartz Blocks and Bamboo Mosaic Slabs do not match those ingredients. [Recipe][daylight-recipe] · [Slab tag][slabs]

An axe speeds up harvesting, but no correct-tool tier is required for its ordinary drop. Hand harvesting returns one detector; its loot has no Silk Touch requirement or Fortune bonus. Explosion survival applies. [Registration][daylight-registration] · [Axe tag][axe] · [Harvest gate][harvest] · [Loot][daylight-loot]

## Use and limits

Ordinary placement starts in **normal mode at power 0**. The dropped item does not preserve an inverted mode or previous strength. A player allowed to build can interact with the placed detector to switch modes and immediately recalculate its output. [Default state][daylight-state] · [Mode switch][daylight-use] · [Loot][daylight-loot]

Automatic updates occur every 20 game ticks only in skylight-enabled dimension types. Inverted mode uses its own formula, so it is not simply 15 minus the normal mode's final signal. Read the [dimension limits](../blocks/DaylightDetector.md#dimension-limits) before treating it as a day/night clock outside the Overworld. [Ticker][daylight-ticker] · [Formula][daylight-calc]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`; no crafting, placement, light, dimension or circuit gameplay test was run.

Related: [Daylight Detector block](../blocks/DaylightDetector.md) · [Nether Quartz](NetherQuartz.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L1018-L1028
[daylight-calc]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L59-L75
[daylight-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/daylight_detector.json
[slabs]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[daylight-registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2855-L2859
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[daylight-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/daylight_detector.json
[daylight-state]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L28-L57
[daylight-use]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L77-L96
[daylight-ticker]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L98-L120
