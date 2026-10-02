# Glass

**Glass** (`minecraft:glass`) is an ordinary, uncolored full glass block for windows and crafting. Its shared placed behavior is in [Glass and Glass Panes](../blocks/GlassAndPanes.md). [Block registration][blocks] · [Item registration][items]

## Obtaining

Smelt **one [Sand](Sand.md) or [Red Sand](RedSand.md)** in a fueled [Furnace](../blocks/Furnace.md) to make **one Glass**. The bundled smelting recipe takes **200 ticking game ticks**, about **10 seconds at 20 ticks per second**. Its ingredient tag contains both sand items. [Smelting recipe][smelting] · [Accepted sand][sand]

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

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L620-L632
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L281-L282
[smelting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/glass.json
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/smelts_to_glass.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/glass.json
