# Target

The **Target item** (`minecraft:target`) places a block that converts projectile hits into short redstone pulses. Hit position sets strength from 1–15, and projectile class sets the duration. The [Target block guide](../blocks/Target.md#target) owns scoring, timing, wiring and player credit. [Item registration][items] · [Hit processing][target-hit]

## Obtaining and placing

Craft **one Target with a Hay Bale in the center and four Redstone Dust directly above, below, left and right**. Leave the corners empty. [Recipe][target-recipe]

A hoe speeds up breaking the placed block, but no tool tier is required for its ordinary drop. Hand harvesting returns one Target; Silk Touch is unnecessary and Fortune does not multiply it. The loot is subject to explosion survival. [Hoe tag][hoe] · [Registration][target-registration] · [Harvest gate][harvest] · [Loot][target-loot]

Place it with a clear shooting face and connect the circuit beside it. There is no directional placement state or stored score carried by the item. [Default state and output][target]

## Use and limits

Closer-to-center impacts give stronger pulses. Arrows and thrown Tridents take the 20-game-tick path; other handled projectiles take the 8-game-tick path. A new hit while the reset tick is pending does not replace or extend the live pulse. See [Pulse duration and repeated hits](../blocks/Target.md#pulse-duration-and-repeated-hits) before using it as a shot counter. [Hit timing][target-hit] · [Trident type][trident]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`; no crafting, placement, projectile or circuit gameplay test was run.

Related: [Target block](../blocks/Target.md) · [Redstone Dust](RedstoneDust.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L1018-L1028
[target-hit]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/TargetBlock.java#L42-L59
[target-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/target.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[target-registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5745-L5747
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[target-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/target.json
[target]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/TargetBlock.java#L26-L114
[trident]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L27-L33
