# Cartography Table

The **Cartography Table item** (`minecraft:cartography_table`) places a workstation for enlarging, copying, and locking maps. Its menu operations are documented in the [Cartography Table block guide](../blocks/CartographyTable.md).

## Obtaining and placing

Craft one table with two Paper above four planks in a two-wide, three-high pattern. The six occupied recipe slots require a Crafting Table. Plank types may be mixed because each slot accepts the planks tag. Breaking a placed Cartography Table ordinarily returns one matching item, subject to explosion survival. [Recipe][recipe] · [Loot][loot]

Place it and interact to open its two-input map menu. A filled map plus Paper enlarges an eligible survey, an Empty Map copies it, and a Glass Pane locks its current terrain. See the [operation table](../blocks/CartographyTable.md#the-three-operations) for exact costs and restrictions. The workstation is also a Cartographer job site; the [Trading guide](../trading/Trading.md) covers employment conditions.

The menu is not permanent map storage. Keep saved maps in an actual container when you are done working, and review the output before changing a valued survey.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`; no crafting, placement, menu, or profession gameplay test. [Block interaction][block] and the canonical block guide contain the operation sources.

Related: [Cartography Table block](../blocks/CartographyTable.md) · [Map](Map.md) · [Items](Items.md)

[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/cartography_table.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/cartography_table.json
[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CartographyTableBlock.java
