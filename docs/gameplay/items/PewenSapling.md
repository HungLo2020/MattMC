# Pewen Sapling

Pewen Sapling is a placeable sapling registered as `minecraft:pewen_sapling`. It is wired to the integrated Pewen tree grower, rather than being only a decorative registry entry.

## Obtaining

Creative inventory lists the item. The Pewen Pines loot table also offers a sapling drop: **5%** without Fortune, increasing to **6.25%, about 8.33%, and 10%** at the listed Fortune levels. Shears or Silk Touch select the pines block itself instead. This is a possible drop, not a guarantee of a self-sustaining tree farm.

Natural biome placement of Pewen trees is not verified by this review, so obtaining the first sapling in an ordinary Survival world remains unresolved.

## Growing

Plant it with appropriate ground and generous dry space above and around it. The [Pewen family guide](../blocks/Pewen.md#growing-and-obtaining-wood) explains the tree's ground and clearance tests.

Natural growth uses the standard sapling brightness and staged-growth checks. Bone meal can advance a growth stage, but it cannot bypass a failed tree placement. The growth path is source-connected; a full in-game tree-growth test has not been performed here.

## Related pages

- [Pewen Log](PewenLog.md)
- [Pewen Pines](PewenPines.md)
- [Items](Items.md)

## Sources and verification

Reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source/data review only; no in-game placement, growth, harvesting, or crafting test.

- [Sapling block](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L218-L222)
- [Grower](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/block/grower/PewenGrower.java)
- [Sapling behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/SaplingBlock.java)
- [Pines loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json)
