# Evoker Spawn Egg

The Evoker Spawn Egg (`minecraft:evoker_spawn_egg`) places a [Evoker](../mobs/Evoker.md). [Item registration][evoker-egg]

## Obtaining

This egg is listed in the ordinary Spawn Eggs category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can insert ordinary listed items in **Creative**; follow that guide for search, cursor and inventory-space requirements. This route is separate from natural mob encounters, recipes and loot. [Category][eggs-tab] · [Egg entry][evoker-entry]

## Usage

Use the egg on a block to attempt placement at the clicked position or the adjacent face, according to collision. Successful ordinary placement consumes one egg, except when the player has the infinite-materials ability used by Creative. The normal egg-spawn path rejects this mob in **Peaceful**. [Block use and spawn check][egg-use] · [Consumption][egg-consume] · [Player exemption][infinite] · [Mob registration][evoker-type]

## Behavior

The placed mob follows its own hostile behavior; possessing its egg does not tame it. Using an egg on a spawner follows a separate configuration path and the server's spawner-enabled setting; see [Monster Spawner](MonsterSpawner.md). [Spawner handling][egg-use]

## Notes

The item is registered as `minecraft:evoker_spawn_egg`. Ordinary browser access is not an operator permission grant for unrelated tools or commands.

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked the item's entity binding, ordinary category entry and shared block-use/spawn path. No browser insertion, egg placement or spawner configuration test was performed.

[evoker-egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L1870
[eggs-tab]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1960-L1970
[evoker-entry]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2017
[egg-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L102
[egg-consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[infinite]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[evoker-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L578-L586
