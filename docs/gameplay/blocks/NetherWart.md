# Nether Wart crop

Nether Wart is a brewing crop planted on **Soul Sand**. It has a separate growth implementation from ordinary Wheat, so Wheat's farmland, bone-meal, and custom area-harvest rules should not be assumed to apply.

## Finding and planting

The Nether fortress stalk-room generator places Nether Wart above Soul Sand. That is a verified structure source, not a promise that every fortress contains the room or that every crop is mature when found.

Plant the [Nether Wart item](../items/NetherWart.md) on Soul Sand. The support check names Soul Sand specifically; Soul Soil is not accepted by this crop's checked placement predicate.

## Growth

Nether Wart grows through ages **0–3**, with age 3 mature. An eligible random tick has a **one-in-ten** chance to advance one stage. The method does not impose Wheat's brightness check or a Nether-only dimension condition.

No bone-meal growth interface is implemented by this block. It also does not inherit the ordinary CropBlock's empty-hand harvest/reset or 3 × 3 hoe-harvest methods. Plan to collect and replant it rather than assuming those MattMC Wheat conveniences work for every plant.

Random ticks are not ordinary game ticks, so the growth chance is not a fixed hatch-like timer or guaranteed completion time.

## Harvest

The loot table gives one Nether Wart for an immature crop. At age 3, its base count becomes **2–4**, with a Fortune bonus and explosion decay. Keep some of the harvest for replanting before using the rest in brewing.

## Related pages

- [Nether Wart item and brewing](../items/NetherWart.md)
- [Brewing](../brewing/Brewing.md)
- [Wheat](Wheat.md), which has different harvesting behavior
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game brewing, growth, or collection test was run.

- [Crop implementation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/NetherWartBlock.java)
- [Harvest loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/nether_wart.json)
- [Fortress crop placement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L989-L992)
- [Planting item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
