# Target

A **Target** (`minecraft:target`) turns a projectile hit into a short redstone signal. A hit closer to the center of the struck face produces greater strength, from **1 to 15**; the idle Target outputs 0. It has no inventory or scoring menu. [Target implementation][target] · [Projectile dispatch][projectile]

## Crafting, collecting and placement

Craft **one Target from one Hay Bale and four Redstone Dust**. Put the Hay Bale in the center, with Redstone directly above, below, left and right; leave the corners empty. [Recipe][target-recipe]

A **hoe speeds up breaking**, but no correct tool or minimum tier is required for its drop. Ordinary Survival harvesting by hand returns one Target. Its loot has no Silk Touch requirement, Fortune multiplier or stored score; explosion survival still applies. [Hoe tag][hoe] · [Registration][target-registration] · [Harvest gate][harvest] · [Loot][target-loot]

The placed block has no facing property. Its default power is 0, and any face can be scored using that face's center. It does not rotate to face the player or retain a previous pulse when ordinarily broken and replaced. [Default state and face calculation][target] · [Placement][placement] · [Loot][target-loot]

## Hit position and signal strength

The calculation uses the **larger of the two offsets from the center of the struck face**. For a north/south face those offsets are horizontal and vertical; for the top/bottom face they are the two horizontal coordinates. Distance traveled and projectile damage do not enter this strength calculation. [Strength calculation][target-strength]

| Largest offset from the face center | Calculated strength |
| --- | ---: |
| 0 blocks: face center | 15 |
| 0.25 blocks | 8 |
| 0.5 blocks: face edge | 1 |

These are mathematical examples from the source, not measured shots. The bands follow square offsets across the face, and rounding means the highest-strength area is a small region around the center. An edge hit still gives at least 1 when the projectile reaches this block-hit handler. [Rounding and minimum][target-strength]

## Pulse duration and repeated hits

| Projectile class | Pulse duration |
| --- | --- |
| Arrows and other AbstractArrow projectiles, including a thrown Trident | 20 game ticks |
| Other projectiles reaching the block-hit handler, such as a Snowball | 8 game ticks |

At the usual 20 game ticks per second, those are nominally one second and 0.4 seconds. Tick timing depends on the world continuing to tick. The duration depends on projectile class, not the hit's strength. [Duration selection][target-hit] · [Trident inheritance][trident] · [Snowball dispatch][snowball]

**A hit during an existing pulse does not replace its strength or extend its timer.** The first hit schedules a reset, and later hits leave that pulse alone while the scheduled tick remains pending. The reset sets power back to 0. For separate measured shots, wait for the previous pulse to end. [Pending-tick check][target-hit] · [Reset][target-reset]

An arrow embedded in the Target does not hold the signal on indefinitely: its collision triggers the hit, then the scheduled reset releases the signal. This differs from the continuing arrow check on an [Oak Button](Buttons.md#arrow-detail). [Arrow impact][arrow] · [Target reset][target-reset]

## Wiring and a practice indicator

The Target reports its current signal in every queried direction. Adjacent [Redstone Dust](RedstoneDust.md) connects toward it even while it is idle, because it remains a signal-source block at power 0. It does not supply a separate strong/direct signal through an adjacent solid block; use a direct connection to dust or a receiving component. [Signal output][target-reset] · [Wire connection rule][wire] · [Direct-signal default][direct]

For a first indicator, place a [Redstone Lamp](RedstoneLamp.md) directly beside the Target, leaving a shooting face clear. With no other power source involved, any accepted hit powers the lamp; a lamp shows on/off, not all fifteen accuracy levels. The lamp has its own delayed turn-off, so its visible flash can outlast the Target's pulse. This layout is source-derived and has not been tested in game. [Neighbor-signal reads][signal] · [Lamp handling][lamp]

To distinguish strengths, use a [Comparator](RedstoneComparator.md) with its rear input directly against the Target. With no side input, it passes the rear strength; in compare mode a side reference of 15 permits only a strength-15 hit. Keep unrelated power away from the Target and follow the Comparator guide's direction and side-input rules. A [Repeater](RedstoneRepeater.md) restores any positive input to 15, so put it after the accuracy decision if you need to extend the output. [Rear input][diode-input] · [Comparison][comparator]

## Player credit

A hit increments the Target Hit statistic and checks the Target-hit advancement trigger only when the projectile's **current owner is a server-side player**. A projectile without that player owner can still produce a signal, but this handler does not award a nearby player credit. Separate advancement conditions still apply. [Owner check][target-hit] · [Advancement trigger][trigger] · [Bundled Bullseye conditions][bullseye]

The credited strength is calculated for that individual hit. Even while the block is busy with an earlier pulse, the new hit can be credited using its own strength without changing the live redstone output. A hit statistic therefore is not a count of distinct output pulses. [Hit and pending-pulse ordering][target-hit]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`. No gameplay test of hit accuracy, pulse timing, attribution or circuits was run. Examples assume normal loaded, ticking blocks and no unrelated power; projectile-specific effects can need separate checks.

Related: [Target item](../items/Target.md) · [Bow](../items/Bow.md) · [Arrow](../items/Arrow.md) · [Redstone basics](../redstone/Redstone.md) · [Blocks](Blocks.md)

[target]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/TargetBlock.java#L26-L114
[projectile]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L266-L291
[target-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/target.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[target-registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5745-L5747
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[target-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/target.json
[placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[target-strength]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/TargetBlock.java#L61-L77
[target-hit]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/TargetBlock.java#L42-L59
[trident]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L27-L33
[snowball]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/Snowball.java#L52-L66
[target-reset]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/TargetBlock.java#L79-L113
[arrow]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L486-L505
[wire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L381-L389
[direct]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L372-L374
[signal]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/SignalGetter.java#L61-L82
[lamp]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java#L36-L55
[diode-input]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L123-L143
[comparator]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L72-L117
[trigger]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/advancements/critereon/TargetBlockTrigger.java#L19-L40
[bullseye]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/advancement/adventure/bullseye.json
