# Reinforced Deepslate

**Reinforced Deepslate** (`minecraft:reinforced_deepslate`) is a separate placeable block with a Creative inventory entry. It is not a crafted upgrade in the ordinary Deepslate recipe chain. [Item registration][items] · [Creative entry][creative]

## Collection and placement

Its bundled loot table is empty. **Survival mining gives no Reinforced Deepslate item, even with Silk Touch or Fortune**, and no crafting, smelting or stonecutting recipe produces it in the checked resource set. [Empty block loot][loot-reinforced-deepslate]

The [placed-block guide](../blocks/Deepslate.md#reinforced-deepslate) explains its hardness, resistance, piston restriction and interaction limits. It has no ordinary Deepslate axis-placement property. [Registration][blocks] · [Piston restriction][pistons]

Related: [Deepslate family](../blocks/Deepslate.md) · [Deepslate item](Deepslate.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`; registration, relevant recipes and mining loot checked. No in-game collection, crafting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[loot-reinforced-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/reinforced_deepslate.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java
[pistons]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L225-L235
