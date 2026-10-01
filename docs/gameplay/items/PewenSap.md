# Pewen Sap

Pewen Sap is a registered plain item, `minecraft:pewen_sap`, listed in Creative. Its name alone does not establish an implemented tree-tapping system.

## Availability and current limits

The checked active item registration gives it no custom item class or food component. A scoped search of active Java code and bundled recipes, loot tables, and tags found no Survival acquisition recipe, tree-extraction interaction, or gameplay use for this item.

In particular, the reviewed [Pewen Branch](PewenBranch.md) and [Pewen Pines](PewenPines.md) loot tables contain saplings, Sticks, and Pine Nuts, not Pewen Sap. Do not gather these blocks expecting a verified sap drop or assume the item has its upstream mod's uses.

Creative access is established; Survival acquisition and useful interactions are **not verified**. Enabled data packs may add behavior beyond this bundled snapshot.

## Related pages

- [Pewen family](../blocks/Pewen.md)
- [Pine Nuts](PineNuts.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game placement, harvesting, eating, or tree-generation test was run. Natural Pewen forest placement remains unverified.

- [Plain item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Branch behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/PewenBranchBlock.java)
- [Branch loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/pewen_branch.json)
- [Pines loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json)
