# Beacon

**Beacon** (`minecraft:beacon`) is the item used to place the mineral-base effect device. Its [placed-block guide](../blocks/Beacon.md) covers the five valid base materials, pyramid sizes, payment, beam clearance, effect choices, and coverage. [Item registration][s1]

## Crafting and collection

Use the exact [Beacon recipe on Nether Star](NetherStar.md#crafting-a-beacon). Mining an existing Beacon returns one Beacon without Silk Touch or a correct-tool tier. The loot keeps a custom name but does not save the selected powers or base level, so configure it again after moving it. [Checked recipe][s2] · [Loot][s3] · [Block properties][s4]

The Beacon block is not a payment item for another Beacon. Use the accepted ingots or gems listed in [effect selection](../blocks/Beacon.md#select-and-pay-for-effects). [Payment items][s5]

Related: [Beacon construction and effects](../blocks/Beacon.md) · [Nether Star](NetherStar.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `b6b5f733b316cef6852866924e2f11f12b0c4f5c`. No in-game crafting, placement, or harvesting test was run. The linked block guide owns placed behavior, and Nether Star owns the crafting layout.

[s1]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/item/Items.java#L604-L604
[s2]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/recipe/crafting/beacon.json
[s3]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/loot_table/blocks/beacon.json
[s4]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L2640-L2650
[s5]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
