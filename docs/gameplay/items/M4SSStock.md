# M4SS Stock

M4SS Stock contributes **0.85× camera pitch recoil and 0.9× camera yaw recoil** on its compatible firearms. [Attachment data][data] · [Active recoil loader][recoil-loader]

## Obtaining

Craft **one M4SS Stock** in the **Stock** tab of the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), using the exact materials below. Keep them in your player inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the craft control and inventory limits. [Recipe][recipe] · [Stock grouping][index] · [Table tab][stock-tab] · [Output count][recipe-loading]

| Material | Count per craft |
| --- | ---: |
| Iron Ingot (`minecraft:iron_ingot`) | 8 |
| Leather (`minecraft:leather`) | 5 |

M4SS Stock can also be obtained from the Creative Menu. It is registered as `minecraft:stock_m4ss`. [Registration][registration] · [Creative entries]

## Usage

Carry the stock, hold a compatible firearm in your **main hand**, and press **Z** with the default bindings. Select **Stock**, then **M4SS Stock** in the Refit screen. [Default key][keys] · [Refit selection][refit-screen]

A gun holds one installed Stock; fitting this item consumes one carried copy, including in Creative. The shared [refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) covers replacement, **Unload**, and returned-item limits. Installation does not require a nearby workbench. [Installation][refit-server] · [Slot storage][refit-storage]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Stock |
| Attachment level | Not level-based |
| Max stack size | 1 |
| Camera pitch recoil factor | 0.85× |
| Camera yaw recoil factor | 0.9× |
| Compatible guns | [AKM](AKM.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Vector SMG](VectorSMG.md) |

These **17 guns** accept both the Stock category and this exact stock ID. Its stored level **0** is metadata, not an active stock upgrade tier. [Accepted items][guns] · [Compatibility][compatibility] · [Definition][definition] · [Level handling][level-storage-tooltip] · [Stack limit][registration]

## Behavior

Pitch is vertical view movement; yaw is sideways movement. The factors above contribute to **sampled camera recoil** alongside the gun's recoil curve, aiming, posture, and other installed modifiers. They are source settings, not measured reductions in the final kick. [Combined recoil][recoil-combined] · [Curve sampling][recoil-sampling]

The imported weight (`weight`) and aim-down-sights (`ads`) fields are **inactive in the reviewed attachment paths**; they do not supply working handling bonuses or penalties. [Attachment data][data] · [Active loader][recoil-loader] · [Aim transition][aim-transition]

This stock does not directly change shot spread or damage. Camera recoil changes view direction and can affect where later shots are aimed. The gun-model shoot animation is separate and does not apply these stock camera factors. See [TaCZ firearms](../mechanics/TaCZFirearms.md) for shared controls and stat limits. [Shot values][shot-values] · [Shot creation][shot-calculation] · [View rotation][camera-rotation] · [Shoot animation][model-animation] · [Model sampling][model-sampling]

## Notes

* This item is part of the integrated TaCZ firearms system.
* Its separate model uses the gun's `stock_pos` mount when the stock's display and model data load successfully. Existing `stock_default` parts hide on installation; adapter visibility depends on matching parts in that gun model. These source conditions do not guarantee in-game appearance or alignment. [Stock display][display] · [Mount and submission][attachment-data-and-mount] · [Conditional visibility][state-and-visibility]

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, compatibility, active effects, and bundled model inputs were checked in source. No in-game crafting, recoil, appearance, multiplayer, browser, or aggregate build test was run.

[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L182
[stock-tab]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L185
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L82
[compatibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[level-storage-tooltip]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczAttachmentItem.java#L17-L42
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[recoil-combined]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L148
[recoil-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L432
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L12-L61
[shot-values]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[shot-calculation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L179
[camera-rotation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[model-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L69-L114
[model-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1301-L1397
[attachment-data-and-mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L615
[state-and-visibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1100-L1173
[Creative entries]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/stock_m4ss.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/stock_m4ss.json#L1-L7
[data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/stock_m4ss_data.json#L1-L14
[display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/attachments/stock_m4ss_display.json#L1-L10
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L203
[guns]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L13-L73
