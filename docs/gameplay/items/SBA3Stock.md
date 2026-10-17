# SBA3 Stock

SBA3 Stock contributes **0.8× camera yaw recoil** on its 17 compatible guns. It supplies no pitch modifier. [Attachment data][data] · [Active loader][recoil-loader]

## Obtaining

At the **[TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table)**, select the **Stock** tab. Carry these exact materials; one craft produces **one SBA3 Stock**. [Recipe][recipe] · [Stock grouping][index] · [Table tabs][stock-tab] · [Output and group loading][recipe-loading]

| Material | Count per craft |
| --- | ---: |
| Iron Ingot (`minecraft:iron_ingot`) | 14 |
| Leather (`minecraft:leather`) | 5 |

Follow the shared [workbench controls](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting and inventory limits.

SBA3 Stock can be obtained from the Creative Menu. It is registered as `minecraft:stock_sba3`. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the stock, hold a compatible gun in your **main hand**, then press **Z** with the default bindings. Select **Stock**, then **SBA3 Stock** from the compatible carried items. [Default key][keys] · [Held-gun controls][input] · [Refit screen][refit-screen]

One stock occupies the gun's Stock slot. Installation consumes one carried copy, including in Creative. Refitting does not require a nearby table; use the shared [refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, **Unload** and inventory-return limits. [Slot storage][refit-storage] · [Installation][refit-server]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Stock |
| Attachment level | Not level-based |
| Max stack size | 1 |
| Camera pitch recoil factor | 1× (no modifier) |
| Camera yaw recoil factor | 0.8× |
| Compatible guns | [AKM](AKM.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Vector SMG](VectorSMG.md) |

The **17 linked guns** are the complete accepted set for this stock. Compatibility requires both the Stock category and this exact attachment ID. The stored level is **0** metadata, not a stock tier or unlock requirement. [Accepted items][accepted-guns] · [Fit checks][compatibility] · [Definition][definition] · [Level handling][level-storage-tooltip] · [Stack limit][registration]

## Behavior

SBA3 Stock supplies only a **0.8× yaw** modifier. Its missing pitch modifier contributes **1×**, meaning this stock leaves that axis unchanged; it does not remove pitch recoil. [Attachment data][data] · [Active loader][recoil-loader]

The factors describe this item's contribution to **sampled camera recoil**, not measured reductions in final kick. Gun recoil curves, aiming, posture and other installed modifiers still affect the result. [Combined recoil][recoil-combined] · [Factor combination][recoil-factors] · [Curve sampling][recoil-sampling]

The imported weight and hip-fire spread (`inaccuracy`) fields are **inactive in the reviewed attachment paths**. They do not provide a movement penalty or tighter hip-fire spread here. The current aiming transition does not use stock ADS values. [Attachment data][data] · [Active loader][recoil-loader] · [Aim transition][aim-transition]

This stock makes **no direct spread or damage change** in the reviewed shot calculation. Camera recoil changes view direction and can affect where later shots are aimed. Gun-model shoot animation is separate and is not multiplied by these stock camera factors. [Shot calculation][shot-calculation] · [Shot values][shot-values] · [View rotation][camera-rotation] · [Shoot animation][model-animation] · [Model sampling][model-sampling]

## Notes

* This item is part of the integrated TaCZ firearms system.
* Its bundled display supplies a separate stock model and texture. The first-person renderer selects the held gun geometry by gun ID; all **17 accepted bundled gun models** have the required `stock_pos` mount. The display also selects `ar_stock_adapter` where that named adapter part exists; an adapter part is not required for the separate stock mesh. Existing `stock_default` parts hide when a stock is installed. [Display][display] · [Model selection][first-person-model-selection] · [Selected geometry][fixed-gun-model-path] · [Bundled gun models][bundled-gun-models] · [Mount and submission][attachment-data-and-mount] · [Conditional visibility][state-and-visibility]
* Model assets and mount presence establish the bundled rendering inputs, not a tested appearance or alignment. Display/model loading and recoil-data loading are separate paths. [Display loading][display-loader] · [Geometry loading][geometry-loader] · [Recoil loading][recoil-loader]

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, accepted items, active effect consumers and bundled stock mounts were checked in source. No in-game crafting/refitting, recoil measurement, appearance, multiplayer, aggregate build or browser test was run.

[data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/stock_sba3_data.json#L1-L11
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/stock_sba3.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/stock_sba3.json#L1-L7
[stock-tab]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L185
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L182
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L35-L125
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L82
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[accepted-guns]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L13-L73
[compatibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L207
[level-storage-tooltip]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczAttachmentItem.java#L17-L42
[recoil-combined]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L148
[recoil-factors]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L537
[recoil-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L432
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L12-L61
[shot-calculation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L179
[shot-values]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[camera-rotation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[model-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L69-L114
[model-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1301-L1397
[display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/attachments/stock_sba3_display.json#L1-L10
[first-person-model-selection]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L669-L696
[fixed-gun-model-path]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L159-L169
[bundled-gun-models]: https://github.com/HungLo2020/MattMC/tree/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun
[attachment-data-and-mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L615
[state-and-visibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1100-L1173
[display-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L128
[geometry-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L757-L831
