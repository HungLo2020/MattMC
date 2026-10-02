# Muddy Mangrove Roots

**Muddy Mangrove Roots** (`minecraft:muddy_mangrove_roots`) is the item form of the block described in [the shared tree-material guide](../blocks/TreeLogsAndRoots.md#muddy-mangrove-roots). [Registration][s7]

Use the exact [Mud + Mangrove Roots recipe](../blocks/TreeLogsAndRoots.md#muddy-mangrove-roots), or mine an existing block to recover one matching item. A **shovel** is the efficient tool; there is no correct-tool tier or Silk Touch requirement. [Loot][s1] · [Shovel mining tag][s2]

Placed muddy roots are axis-oriented and do not waterlog. They have **no axe-stripping conversion** and are not listed in the default furnace fuel table. Their dirt-tag membership is useful as plant support, including for Mangrove Propagules. [Block registration][s3] · [Stripping map][s4] · [Fuel table][s5] · [Dirt tag][s6]

Related: [Placed behavior](../blocks/TreeLogsAndRoots.md#muddy-mangrove-roots) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. No in-game harvesting, crafting, or placement test was run. The shared block guide holds the checked recipes and behavior.

[s1]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/muddy_mangrove_roots.json
[s2]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[s3]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L417-L421
[s4]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[s5]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[s6]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/dirt.json
[s7]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L220-L278
