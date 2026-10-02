# Recovery Compass

A **Recovery Compass** (`minecraft:recovery_compass`) helps you return toward **your latest recorded death position**. Keep a spare near your respawn point so you can collect it after dying. Its target is a saved location, not a search for your dropped items; reaching that location does not guarantee that anything remains to recover. [Recovery target][angle] · [Death and item-recovery rules](../mechanics/DeathAndRespawn.md#getting-back-to-your-items)

## Obtaining

Craft **8 Echo Shards + 1 Compass → 1 Recovery Compass** in a 3 × 3 Crafting Table grid. Put the Compass in the center and fill the other eight squares with Echo Shards. The ordinary [Compass recipe](Compass.md#crafting) uses **4 Iron Ingots and 1 Redstone Dust**, so those plus eight shards are the total ingredients when crafting both items from scratch. [Recovery recipe][recipe] · [Compass recipe][compass-recipe]

- **Echo Shards:** a selected entry in the bundled ordinary Ancient City chest table gives **1–3 shards**. This is not a guaranteed reward or a per-chest maximum: the table makes several selections. The city's optional Barracks piece contains chests assigned that table. Collect eight before committing the center Compass. [Chest entry and rolls][city-loot] · [Barracks template][barracks] · [Piece pool][city-pool]
- **Iron and Redstone:** follow [Iron Ingot](IronIngot.md#obtaining) for processing Raw Iron or ore and [Redstone Dust](RedstoneDust.md#obtaining) for the correct-tool ore route

See [Deep Dark](../biomes/CaveBiomes.md#deep-dark) for Ancient City eligibility and [Sculk Shriekers](../blocks/SculkShrieker.md) for the hazard while searching. A Deep Dark patch does not guarantee a city, and finding a city does not guarantee enough shards.

Recovery Compass is also an ordinary category-listed item. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) provides a separate item-request route, including Survival under the checked browser rules. [Category entry][creative]

## Usage

1. After respawning, collect your stored Recovery Compass or craft another. You did **not** need to carry one when you died: the death record belongs to the player. [Death recording][death]
2. Return to the **dimension where that death occurred**. The compass does not point through a portal or convert coordinates between dimensions. [Target validity][angle]
3. Read it in your normal inventory while retracing a safe route. With a valid target, its needle direction follows the saved death position horizontally. It does not show the required height, find a walkable path, or remove the hazard that killed you. [Direction calculation][angle] · [Inventory display owner][gui]

There is no charging or location-binding step. This is a separate item from the ordinary [Compass](Compass.md) and its [Lodestone binding](../blocks/Lodestone.md#binding-a-compass). Recovery Compass is registered as a plain item; the death target comes from its display property. [Registration][items] · [Ordinary item interaction][item-use] · [Recovery target][angle]

## Behavior

| Situation | Source-defined result |
| --- | --- |
| Your latest death is in the current dimension | The normal inventory model selects a direction toward that saved block position |
| You have no saved death, or the death was in another dimension | The model selects its random spinning/no-valid-target state |
| You die again | The new death replaces the previous target; there is no selectable death history |
| You obtain a different Recovery Compass | It reads the same player record; crafting a new one does not reset that record |
| You give the compass to another player | In their normal inventory, it reads **their** death record, not the giver's |

[Target selection and validity][angle] · [Inventory owner][gui] · [Recording the latest death][death]

The record saves both **dimension and block position**, survives ordinary player replacement on respawn, and is saved with player data. Login and respawn messages supply it to the local client. Returning to the spot or collecting drops does not itself clear the record. The direction check also treats a position effectively at the saved block's center as invalid, so an unsettled needle is not proof that your items have disappeared. [Saved data][save] · [Respawn copying][restore] · [Server packet data][spawn] · [Login/respawn receiving][client] · [Direction validity][angle]

Keep item-loss rules separate from the compass. It does not protect drops, stop their age counter, or verify that they survived. [Death and respawn](../mechanics/DeathAndRespawn.md) owns inventory loss, `keepInventory`, item damage, and the ticking-dependent recovery window.

## Notes

* Registered item: `minecraft:recovery_compass`, with **Uncommon** rarity. [Registration][items]
* Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The normal inventory path evaluates the compass property, selects its model, and carries the selected geometry and texture into the current Rust renderer. This is an active source path, not a gameplay observation. [Item model][model] · [Property caller][range] · [GUI routing][gui-route] · [Selected-model transport][flat] · [Rust mesh drawing][rust-draw]
* No in-game crafting, chest search, death, rejoin, dimension, needle, or item-recovery test was run. Data/resource packs, server settings and modified player data can change the checked results

Related: [Echo Shard](EchoShard.md) · [Compass](Compass.md) · [Death and respawn](../mechanics/DeathAndRespawn.md) · [Items](Items.md)

[recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/recovery_compass.json
[compass-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/compass.json
[city-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[barracks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[city-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json#L1-L18
[creative]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1481-L1483
[items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1609-L1610
[item-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L164-L191
[death]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L869-L927
[save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L679-L700
[restore]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1590-L1599
[spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2102-L2115
[client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java
[angle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/item/properties/numeric/CompassAngleState.java#L43-L131
[gui]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/GuiGraphics.java#L823-L858
[model]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/assets/minecraft/items/recovery_compass.json
[range]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/item/RangeSelectItemModel.java#L60-L79
[gui-route]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/render/GuiRenderer.java#L203-L255
[flat]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/gui/GuiFlatItemMeshCollector.java#L48-L140
[rust-draw]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/guirender/frontend/mesh_items/recording.rs#L364-L384
