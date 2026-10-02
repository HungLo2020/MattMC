# Mangrove Roots

**Mangrove Roots** (`minecraft:mangrove_roots`) is the item form of the block described in [the shared tree-material guide](../blocks/TreeLogsAndRoots.md#mangrove-roots). [Registration][s7]

Mine Mangrove Roots to collect one matching item; an axe is faster, but Silk Touch and a correct-tool tier are not required. The block can be waterlogged and can be used with Mud in the [Muddy Mangrove Roots recipe](../blocks/TreeLogsAndRoots.md#muddy-mangrove-roots). It has **no axe-stripping conversion**. [Loot][s1] · [Stripping map][s2]

One item supplies **300 default furnace burn ticks**. The root is not in the mangrove-log ingredient tag used for planks, or in the burnable-log tag used for Charcoal. [Fuel entry][s3] · [Default fuel scale][s4] · [Mangrove log inputs][s5] · [Charcoal input families][s6]

Related: [Placed behavior](../blocks/TreeLogsAndRoots.md#mangrove-roots) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. No in-game harvesting, crafting, or placement test was run. The shared block guide holds the checked recipes and behavior.

[s1]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_roots.json
[s2]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[s3]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L103-L108
[s4]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L42
[s5]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/mangrove_logs.json
[s6]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[s7]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L220-L278
