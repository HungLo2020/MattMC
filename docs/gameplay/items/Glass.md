# Glass

**Glass** (`minecraft:glass`) is an ordinary, uncolored full glass block for windows and crafting. Its shared placed behavior is in [Glass and Glass Panes](../blocks/GlassAndPanes.md). [Block registration][blocks] · [Item registration][items]

## Obtaining

Smelt **one [Sand](Sand.md) or [Red Sand](RedSand.md)** in a fueled [Furnace](../blocks/Furnace.md) to make **one Glass**. The bundled smelting recipe takes **200 ticking game ticks**, about **10 seconds at 20 ticks per second**. Its ingredient tag contains both sand items. [Smelting recipe][smelting] · [Accepted sand][sand]

A **Librarian at trading level 3** may offer **4 Glass for a base price of 1 Emerald**. That listing is present in both ordinary and optional Trade Rebalance pools, but individual stock is selected randomly. Follow [Librarian offer pools](../trading/LibrarianTrades.md#ordinary-offer-pools) for the selection and use limits, and [keeping a Librarian supplied](../trading/LibrarianTrades.md#keeping-a-librarian-supplied) for restocking. [Ordinary Glass offer][glass-trade] · [Optional Glass offer][optional-glass-trade] · [Active offer selection][trade-selection]

Use **Silk Touch** to move existing glass: ordinary breaking returns no item, while a qualifying tool recovers **one Glass**. [Block loot][loot]

## Selected crafting uses

- [Glass Panes](GlassPane.md#crafting) provide more thin window pieces from a batch of full blocks
- Choose a [stained-glass color](../blocks/GlassAndPanes.md#forms-and-colors) for its dye recipe
- Follow the existing [Glass Bottle recipe](GlassBottle.md#crafting-and-filling-with-water) for bottles
- [Tinted Glass](TintedGlass.md#crafting) uses Amethyst Shards and has different light and collection rules

For transparent building, light transmission, and pane differences, use the [family guide](../blocks/GlassAndPanes.md).

Related: [Glass Pane](GlassPane.md) · [Smelting](../smelting/Smelting.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes; no gameplay test was run. Data packs can change recipes and loot.

The Librarian acquisition pointer was added on **2026-10-10** after checking the ordinary/optional tables, active selection and sale constructor at `f86206767dadde696adfed4e04c5ee97cd0d0885`. No in-game trading test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L620-L632
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L281-L282
[smelting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/glass.json
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/smelts_to_glass.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/glass.json
[glass-trade]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L319-L327
[optional-glass-trade]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L847-L852
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
