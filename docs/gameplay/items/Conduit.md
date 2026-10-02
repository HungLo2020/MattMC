# Conduit

**Conduit** (`minecraft:conduit`) places the water-and-frame device that supplies Conduit Power and, with a complete frame, attacks selected nearby hostile mobs. It is not a storage or crafting container. Use the [Conduit block guide](../blocks/Conduit.md) for activation and exact limits. [Item registration][s1] · [Active device ticker][s2]

## Crafting and collection

Follow the [eight Nautilus Shells and Heart of the Sea recipe](../blocks/Conduit.md#crafting-and-collecting). Mining a Conduit returns one matching item; a pickaxe is faster, but Silk Touch and a correct-tool tier are not required. Frame and water conditions must be rebuilt at the new location. [Recipe][s3] · [Loot][s4] · [Mining tag][s5] · [Properties][s6]

Related: [Conduit construction and effects](../blocks/Conduit.md) · [Heart of the Sea](HeartOfTheSea.md) · [Nautilus Shell](NautilusShell.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `b6b5f733b316cef6852866924e2f11f12b0c4f5c`. No in-game crafting, mining, activation, or combat test was run. The linked block guide owns the recipe and placed-device behavior.

[s1]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/item/Items.java#L955-L955
[s2]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L110
[s3]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/recipe/crafting/conduit.json
[s4]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/loot_table/blocks/conduit.json
[s5]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[s6]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L5178-L5188
