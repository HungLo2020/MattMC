# Player Head

**Player Head** (`minecraft:player_head`) is a placeable and wearable decoration that can carry a player's profile for its appearance. The same item places standing and wall forms; use [Heads and Skulls](../blocks/HeadsAndSkulls.md#player-heads) for the complete placed-block guide. [Registration][items] · [Profile appearance][render]

## Obtaining

The item is listed in Creative. The reviewed built-in recipes, entity/chest loot, and structure templates provide **no ordinary Survival source**. A profile-equipped head supplied through commands, a map, or server content is a separate source; this page does not claim a player-death drop or a crafting recipe. [Creative entry][creative] · [Registered item][items]

Once a Player Head exists, breaking its standing or wall block normally returns one Player Head item. That is recovery of an existing head, not a way to create the first one in Survival. [Block loot][loot-player_head] · [Shared wall loot][wall-properties]

## Usage

Put it in the inventory's **head equipment slot** to wear it. Its item registration disables held-item quick swapping, so ordinary use in the air does not equip it like a swappable helmet. [Equipment registration][items] · [Held-use handling][head-use]

Profile data can supply the player appearance and a profile-based item name. A custom display name and a player profile are separate data: renaming a head does not itself assign the named player's profile. [Profile-based name][player-name] · [Separate stored components][entity] · [Appearance lookup][render]

## Behavior

The item places `minecraft:player_head` or `minecraft:player_wall_head`. Placement transfers its supplied components into the skull block entity; the ordinary block loot copies its **profile, custom Note Block sound, and custom name** back to the dropped item. See [placement and recovery](../blocks/HeadsAndSkulls.md#breaking-and-retained-data) for support, water, tool, and wall-form rules. [Placement transfer][block-item] · [Component handling][entity] · [Retained loot data][loot-player_head]

A standing Player Head above a Note Block uses a supplied custom sound. With no custom sound present, that playback path is silent. Neither a player's profile nor a custom display name supplies the missing sound. See [Note Block sounds](../blocks/HeadsAndSkulls.md#note-block-sounds), including the wall-form limitation. [Sound lookup][note-sound] · [Stored sound component][entity]

## Notes

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. No in-game profile loading, appearance, placement/recovery, equipment, or audio test was run. Custom content and data packs can add acquisition routes or change supplied data.

Related: [Heads and Skulls](../blocks/HeadsAndSkulls.md) · [Note Block](NoteBlock.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2063-L2097
[render]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/renderer/blockentity/SkullBlockRenderer.java#L80-L103
[creative]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1252-L1258
[loot-player_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/player_head.json
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[head-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Item.java#L173-L188
[player-name]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/PlayerHeadItem.java#L9-L20
[entity]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/SkullBlockEntity.java#L38-L107
[block-item]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L103
[note-sound]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L139-L172
