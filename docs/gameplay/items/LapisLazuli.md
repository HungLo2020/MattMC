# Lapis Lazuli

Lapis Lazuli is an enchanting material and crafting ingredient registered as `minecraft:lapis_lazuli`.

## Obtaining

The Lapis Ore loot table selects the ore block with Silk Touch. Its ordinary branch selects **4–9 Lapis Lazuli** before the Fortune ore-drop bonus and explosion decay. Correct-tool rules apply; the ore belongs to the Stone-required group in the bundled mining-tier tags.

This is one verified mining route, not an ore-distribution or all-loot survey.

## Enchanting

The Enchanting Table's material slot specifically accepts Lapis Lazuli. Its top, middle, and bottom offers consume **one, two, or three lapis** respectively in ordinary Survival use. The displayed level requirement is a separate check; more lapis cannot substitute for missing experience levels.

See [Enchanting](../enchanting/Enchanting.md) for level costs, item eligibility, and the 15-bookshelf setup.

## Crafting

- One Lapis Lazuli in a shapeless recipe makes **one Blue Dye**.
- Nine Lapis Lazuli in a 3 × 3 square make **one Block of Lapis Lazuli**.
- One Block of Lapis Lazuli in a shapeless recipe returns **nine Lapis Lazuli**.

Blue Dye is a different item; converting all your lapis to dye removes it from the table's accepted input until you obtain more lapis.

## Related pages

- [Enchanting Table](../blocks/EnchantingTable.md)
- [Mining tiers](../mechanics/Mining.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game enchanting, anvil, mining, or crafting test was run.

- [Lapis Ore loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/lapis_ore.json)
- [Mining tier](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json)
- [Lapis slot and payments](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java)
- [Dye recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/blue_dye.json)
- [Block packing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/lapis_block.json)
- [Block unpacking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/lapis_lazuli.json)
