# Heavy Core

Heavy Core is the epic-rarity item form of `minecraft:heavy_core` and an ingredient for the [Mace](Mace.md). The [Heavy Core block guide](../blocks/HeavyCore.md) owns its acquisition, placement and recovery details. [Registration][item]

## Obtaining

A successful eligible **ominous Vault** opening has a **7.5% bundled chance to eject one Heavy Core**. The block guide explains the 75% unique-roll condition and weight 1/10 calculation; a display preview is not a promised reward. [Parent reward table][ominous] · [Unique selection][unique] · [Vault guide](../blocks/Vault.md#normal-versus-ominous-rewards)

There is no bundled recipe producing a Heavy Core. Its ordinary Ingredients entry separately provides the [Creative inventory item-browser route](../mechanics/InventoryBrowser.md). [Entry][entry]

## Usage

Place **one Heavy Core directly above one [Breeze Rod](BreezeRod.md)** to craft **one Mace**. The recipe fits the personal crafting grid after its outer spaces are trimmed. Recover a placed core before crafting with it. [Recipe][mace] · [Pattern decoding][pattern] · [Trimming][trim]

## Behavior

A placed core is **half a block wide, deep and high**, is centered at the bottom of its space and can be waterlogged. It stays in place without support. **Hand mining can recover one core**; a pickaxe is efficient but is not required for the drop. Follow [placement](../blocks/HeavyCore.md#placement-water-and-shape) and [recovery](../blocks/HeavyCore.md#recovering-a-placed-core) for the source-defined rules.

## Notes

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. No in-game reward, crafting or mining test was run; custom loot/configuration can change the documented chance.

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L173
[ominous]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json#L1-L52
[unique]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_unique.json#L1-L35
[entry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1846
[mace]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/mace.json#L1-L16
[pattern]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L70-L99
[trim]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L103-L136
