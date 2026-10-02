# Empty Map

An **Empty Map** (`minecraft:map`) starts a new terrain survey or copies an existing filled map. It is distinct from `minecraft:filled_map`, the [Map](Map.md) that already refers to saved map data. [Registration][item]

## Crafting and first use

Surround **one Compass with eight Paper** in a 3 × 3 Crafting Table grid to make one Empty Map. The compass is consumed as a recipe ingredient; it is not returned separately. [Recipe][recipe]

Use the Empty Map to create a **scale-0 map of the current region and dimension**. The server consumes one empty item in Survival. If that empties the held stack, the filled map replaces it; otherwise the new map goes to inventory or drops if there is no room. Creative uses the usual infinite-material handling. [Use and inventory exchange][use]

The resulting map covers a grid-aligned 128 × 128-block region. Hold the filled map while exploring to survey it. Using another empty map in the same aligned region is not a way to guarantee a neighboring region. See [Map](Map.md#region-and-scale).

## Copying an existing map

At a [Cartography Table](../blocks/CartographyTable.md), combine one filled map with one Empty Map to obtain two copies of that map. They share the same saved map record. The empty ingredient does not erase or redirect the original survey. A separate crafting-grid cloning recipe supports multiple blank ingredients in separate slots. [Table copying][table] · [Crafting copy logic][clone]

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`; no crafting, activation, inventory, or cloning gameplay test.

Related: [Map](Map.md) · [Paper](Paper.md) · [Compass](Compass.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2060
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/map.json
[use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/EmptyMapItem.java
[table]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java
[clone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/MapCloningRecipe.java
