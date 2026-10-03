# Ravager Spawn Egg

## Obtaining

The Ravager Spawn Egg is an ordinary listed item available from the Creative category and MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. This inventory route is separate from the mob's natural or event spawning. [Entity/egg registration][eggs-register] · [Category entries][eggs-list] · [List assembly][browser-list] · [Request][browser-client] · [Server handling][browser-server]

## Usage

Use it on an ordinary block face with room for a [Ravager](../mobs/Ravager.md). The egg's mob-spawning path rejects this hostile type in Peaceful and consumes one egg on a successful normal Survival spawn. Avoid releasing it beside villagers or unprotected players. [Egg placement and difficulty][egg-use] · [Hostile registration][ravager-registry]

## Behavior

The spawned mob follows its normal hostile behavior. Possessing an egg does not make it safe or friendly. Using an egg on a spawner has a separate server setting and configuration path; see [Spawner](../blocks/MonsterSpawner.md) before doing so. [Spawner/ordinary-use branches][egg-use]

## Notes

- Registry ID: `minecraft:ravager_spawn_egg`
- Mob behavior and event acquisition: [Ravager](../mobs/Ravager.md)

Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. No item-browser insertion, egg use or mob gameplay test was run.

[eggs-register]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1930-L1941
[eggs-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2061-L2072
[browser-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
[egg-use]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L103
[ravager-registry]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L1125-L1132
