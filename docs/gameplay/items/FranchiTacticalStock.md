# Franchi Tactical Stock

Franchi Tactical Stock fits only the **SPAS-12** and contributes **0.8× pitch and 0.8× yaw camera recoil**. [Attachment data][data] · [Active loader][recoil-loader]

## Obtaining

At the **[TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table)**, select the **Stock** tab. Carry these exact materials; one craft produces **one Franchi Tactical Stock**. [Recipe][recipe] · [Stock grouping][index] · [Table tabs][stock-tab] · [Output and group loading][recipe-loading]

| Material | Count per craft |
| --- | ---: |
| Iron Ingot (`minecraft:iron_ingot`) | 16 |
| Leather (`minecraft:leather`) | 3 |

Follow the shared [workbench controls](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting and inventory limits.

Franchi Tactical Stock can be obtained from the Creative Menu. It is registered as `minecraft:stock_tactical_spas_12`. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the stock, hold a compatible gun in your **main hand**, then press **Z** with the default bindings. Select **Stock**, then **Franchi Tactical Stock** from the compatible carried items. [Default key][keys] · [Held-gun controls][input] · [Refit screen][refit-screen]

One stock occupies the gun's Stock slot. Installation consumes one carried copy, including in Creative. Refitting does not require a nearby table; use the shared [refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, **Unload** and inventory-return limits. [Slot storage][refit-storage] · [Installation][refit-server]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Stock |
| Attachment level | Not level-based |
| Max stack size | 1 |
| Camera pitch recoil factor | 0.8× |
| Camera yaw recoil factor | 0.8× |
| Compatible guns | [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md) |

Only the linked SPAS-12 accepts this stock. Compatibility requires both the Stock category and this exact attachment ID. The stored level is **0** metadata, not a stock tier or unlock requirement. [Accepted items][accepted-guns] · [Fit checks][compatibility] · [Definition][definition] · [Level handling][level-storage-tooltip] · [Stack limit][registration]

## Behavior

Franchi Tactical Stock multiplies its camera pitch and yaw recoil contributions by **0.8** on the SPAS-12. [Attachment data][data] · [Active loader][recoil-loader]

The factors describe this item's contribution to **sampled camera recoil**, not measured reductions in final kick. Gun recoil curves, aiming, posture and other installed modifiers still affect the result. [Combined recoil][recoil-combined] · [Factor combination][recoil-factors] · [Curve sampling][recoil-sampling]

The imported weight and hip-fire spread (`inaccuracy`) fields are **inactive in the reviewed attachment paths**. They do not provide a movement penalty or tighter pellet spread here. The current aiming transition does not use stock ADS values. [Attachment data][data] · [Active loader][recoil-loader] · [Aim transition][aim-transition]

This stock makes **no direct spread or damage change** in the reviewed shot calculation. Camera recoil changes view direction and can affect where later shots are aimed. Gun-model shoot animation is separate and is not multiplied by these stock camera factors. [Shot calculation][shot-calculation] · [Shot values][shot-values] · [View rotation][camera-rotation] · [Shoot animation][model-animation] · [Model sampling][model-sampling]

## Notes

* This item is part of the integrated TaCZ firearms system.
* Its bundled display supplies a separate stock model and texture, with **no adapter field**. The held SPAS-12 uses its gun-ID-selected geometry, whose `stock_pos` node provides the stock mount. Installing the stock also hides that model's existing `stock_default` parts. [Display][display] · [Model selection][first-person-model-selection] · [Selected geometry][fixed-gun-model-path] · [SPAS-12 mount][spas-mount] · [Mount and submission][attachment-data-and-mount] · [Visibility][state-and-visibility]
* Model assets and mount presence establish the bundled rendering inputs, not a tested appearance or alignment. Display/model loading and recoil-data loading are separate paths. [Display loading][display-loader] · [Geometry loading][geometry-loader] · [Recoil loading][recoil-loader]

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, accepted items, active effect consumers and bundled stock mounts were checked in source. No in-game crafting/refitting, recoil measurement, appearance, multiplayer, aggregate build or browser test was run.

[data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/stock_tactical_spas_12_data.json#L1-L14
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/stock_tactical_spas_12.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/stock_tactical_spas_12.json#L1-L7
[stock-tab]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L185
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L182
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L35-L125
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L82
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[accepted-guns]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L64
[compatibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L209
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
[display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/attachments/stock_tactical_spas_12_display.json#L1-L9
[first-person-model-selection]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L669-L696
[fixed-gun-model-path]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L159-L169
[spas-mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/spas_12_geo.json#L7082-L7092
[attachment-data-and-mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L615
[state-and-visibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1100-L1173
[display-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L128
[geometry-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L757-L831
