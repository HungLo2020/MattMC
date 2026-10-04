# Building Wand

## Obtaining

Craft one Building Wand with a diamond above a stick. It is also available in the
Tools & Utilities creative tab. The wand stacks to one and has no durability.

## Usage

- Right-click a block face to extend a connected plane of that block, up to
  **128 successful placements** per use. Diagonally touching blocks can belong
  to the same plane
- In Survival, carry the matching block items in your inventory or off hand;
  successful placements consume them. Creative mode does not require a supply
- Sneak-right-click to remove an exposed plane, up to **128 blocks** per use.
  Removed blocks drop no resources, including in Survival
- Protected positions, unbreakable blocks and normal placement restrictions
  still apply. An obstruction can stop the wand from reaching blocks behind it

## Placing slabs

The wand preserves where you clicked within the block for every placement in
the plane. Click the side of an upper slab to extend upper slabs sideways, or
the side of a lower slab to extend lower slabs sideways.

Normal slab merging still applies: clicking the top of a lower slab or the
underside of an upper slab combines it into a double slab. Clicking the top of
an upper slab places a lower slab above; clicking the underside of a lower slab
places an upper slab below.

The plane matches the block type, not every block-state property. If connected
slabs include both halves, the same relative click can extend some slabs and
merge others. Check the surrounding slab halves before using the wand on a
mixed plane. Placement permission is checked at the resolved slab target,
including when that target is the existing slab itself.

## Sources

- [Wand behavior](https://github.com/HungLo2020/MattMC/blob/fix/issue-720-wand-slab-placement/src/main/java/net/minecraft/world/item/BuildingWandItem.java)
- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/fix/issue-720-wand-slab-placement/src/main/resources/data/minecraft/recipe/crafting/building_wand.json)
- [Normal slab placement](https://github.com/HungLo2020/MattMC/blob/fix/issue-720-wand-slab-placement/src/main/java/net/minecraft/world/level/block/SlabBlock.java)
