# Shulker Shell

**Shulker Shells** (`minecraft:shulker_shell`) are the mob-drop ingredients used to craft a Shulker Box. Collect them from [Shulkers](../mobs/Shulker.md) in [End Cities](../structures/EndCity.md); a single kill does not guarantee a shell. [Item registration][item] · [Shell loot][loot]

## Obtaining

With mob loot enabled, an ordinary Shulker death has a **50% chance to drop one shell**. Applicable Looting raises that chance to **56.25%, 62.5%, or 68.75%** at levels I, II, or III. A successful roll still produces only one shell. The [Shulker guide](../mobs/Shulker.md#shell-drops) explains the player-credit and Looting distinctions. [Loot table][loot]

## Crafting a Shulker Box

At a Crafting Table, place these in one vertical column:

1. Shulker Shell
2. Chest
3. Shulker Shell

The recipe consumes **two shells and one ordinary Chest** to make **one uncolored Shulker Box**. It needs three rows, so it does not fit the inventory's 2 × 2 crafting grid. A Trapped Chest or Ender Chest is not the Chest ingredient in this recipe. [Crafting recipe][recipe]

For storage, collection, recoloring, and washing rules, see the [Shulker Box guide](../blocks/ShulkerBox.md).

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game shell collection, Looting, or crafting test was run. Custom loot tables and recipes can change these defaults.

Related: [Shulker](../mobs/Shulker.md) · [End City](../structures/EndCity.md) · [Chest](../blocks/Chest.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2281
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/shulker.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/shulker_box.json
