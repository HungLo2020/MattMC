# Building  Wand 

## Obtaining

Craft **one Building Wand** with **one Diamond above one Stick**. This Survival recipe is already present in MattMC. The wand is also listed in the **Tools & Utilities** Creative tab and stacks to **1**. [Recipe][recipe] · [Registration][item] · [Creative entry][creative]

## Usage

Right-click a block face to extend a connected plane of the matching block type. Sneak-right-click selects breaking instead. Each use can perform **up to 128 successful placements or removals**, with the actual number limited by the reachable plane, material supply and permitted actions. It does not promise that every use changes 128 blocks. [Use selection][use] · [Action limit][limit] · [Plane traversal][plane]

In Survival, placement consumes matching block items from your inventory or offhand. Wand breaking does **not produce normal block loot**; do not use it to harvest resources. [Placement][placement] · [Item consumption][block-item] · [Supply selection][supply] · [Breaking][breaking]

**Slab placement still has a known limitation.** The reviewed wand synthesizes a hit at the center of the clicked face instead of retaining your precise click offset. For side faces, its half-height position can change slab replacement or orientation, including merging an upper slab into a double slab. Treat slab planes cautiously; the unresolved behavior is tracked in [#720](https://github.com/HungLo2020/MattMC/issues/720). This is a source-based warning, not a new in-game reproduction. [Current placement][placement] · [Face-center helper][hit] · [Replacement target][context] · [Slab replacement rules][slab]

## Notes

- The earlier [recipe request #684](https://github.com/HungLo2020/MattMC/issues/684) is historical context; the current bundled recipe is listed above.
- [PR #794](https://github.com/HungLo2020/MattMC/pull/794) proposes a slab-placement fix and was **unmerged** at this review. This article describes current master and does not claim that fix is available.

## Trivia

- This item is inspired by building-wand mods for Minecraft.

## Sources and verification

Reviewed on **2026-10-04** at source `cc140840a21e5c6c932c23abf34124418d6506b0`. The recipe, registered item, action limit and current placement consumers were inspected. No live crafting, placement, breaking or multiplayer test was run for this correction.

[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/building_wand.json#L1-L16
[item]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2681-L2686
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1425-L1435
[use]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L37-L74
[limit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L28-L30
[plane]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L121-L152
[placement]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L155-L187
[supply]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L208-L226
[breaking]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L77-L108
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BuildingWandItem.java#L238-L243
[slab]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L99
[block-item]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BlockItem.java#L41-L83
[context]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/context/BlockPlaceContext.java#L25-L58
