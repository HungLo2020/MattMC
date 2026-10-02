# Cosmaw Spawn Egg

The **Cosmaw Spawn Egg** creates a [Cosmaw](../mobs/Cosmaw.md). It is registered as `minecraft:cosmaw_spawn_egg` and points to that entity type. [Item registration][item]

## Obtaining

This egg is an ordinary category-listed item. Request it through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. No bundled recipe or loot source was found in this review; browser access is separate from natural mob spawning. [Category entry][category]

## Usage

Use the egg on an ordinary block to place the mob at the clicked location or beside the clicked face, according to collision. A successful spawn consumes one egg under normal Survival item rules. [Placement][use] · [Consumption][consume]

## Behavior

The spawned Cosmaw hunts [Cosmic Cod](../mobs/CosmicCod.md). Its [missing food dependency](../mobs/Cosmaw.md#feeding-and-taming-missing-item-dependency) means this egg does not provide a working taming route.

## Notes

- Natural availability, behavior and integration limits belong to the [Cosmaw guide](../mobs/Cosmaw.md).
- Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. No egg-use or inventory-use test was run.

Related: [Cosmaw](../mobs/Cosmaw.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1843
[category]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1999
[use]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[consume]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
