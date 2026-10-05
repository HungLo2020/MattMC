# Factory Issued Tactical Stock

Factory Issued Tactical Stock reduces the camera-recoil factors on its compatible firearms. It can be crafted and installed, but its bundled first-person replacement stock is not selected by the current rendering path. [Recoil profile][data] · [Display loading][display-loader] · [Stock visibility][state-and-visibility]

## Obtaining

Craft **one Factory Issued Tactical Stock** in the **Stock** tab of the **[TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table)** using these exact materials. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for carried materials and crafting controls. [Recipe][recipe] · [Stock group][index] · [Table tabs][stock-tab] · [Output count][recipe-loading]

| Material | Count per craft |
| --- | ---: |
| Iron Ingot (`minecraft:iron_ingot`) | 14 |
| Leather (`minecraft:leather`) | 4 |

Factory Issued Tactical Stock can also be obtained from the Creative Menu. It is registered as `minecraft:oem_stock_tactical`. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the stock and hold a compatible gun in your **main hand**. Press **Z** with the default bindings, select **Stock**, then choose **Factory Issued Tactical Stock**. The gun stores one stock; installing consumes one carried copy, including in Creative. Follow the shared [refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, **Unload** and inventory-return rules. Installation does not require a nearby workbench. [Refit selection][refit-screen] · [Default key][keys] · [Installation][refit-server] · [Slot storage][refit-storage]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Stock |
| Attachment level | Not level-based |
| Max stack size | 1 |
| Camera pitch recoil factor | 0.80× |
| Camera yaw recoil factor | 0.70× |
| Compatible guns | [AKM](AKM.md), [DB-2 Durin](DB2Durin.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-MP5A5](HKMP5A5.md), [RPK](RPK.md), [Vector SMG](VectorSMG.md) |

These **6 guns** accept this exact item as well as the Stock category. The stored level `0` is metadata, not a stock upgrade tier or unlock requirement. [Accepted items][gun-list] · [Compatibility][compatibility] · [Definition][definition] · [Level handling][level-storage-tooltip] · [Stack limit][registration]

## Behavior

The stock contributes **0.80× pitch** and **0.70× yaw** to sampled camera recoil. These source factors combine with the gun's recoil curve, aiming and posture, and other installed modifiers; they are not measured reductions in the final kick. They still apply when the replacement stock model is unavailable. [Profile][data] · [Independent recoil loading][recoil-loader] · [Combined recoil][recoil-combined] · [Factor combination][recoil-factors] · [Curve sampling][recoil-sampling]

Its imported weight, aim-down-sights (`ads`), spread (`inaccuracy`) and melee fields are **inactive in the reviewed attachment paths**. They provide no movement or ADS adjustment, direct spread or damage modifier, or stock melee benefit here. [Profile][data] · [Active attachment fields][recoil-loader] · [Aim transition][aim-transition] · [Shot values][shot-values] · [Active controls][input]

Camera recoil changes view direction and can affect where later shots are aimed. The gun-model shoot animation is separate and does not use these stock factors. [View rotation][camera-rotation] · [Shot direction][shot-calculation] · [Animation trigger][model-animation] · [Model sampling][model-sampling]

## Notes

* This item is part of the integrated TaCZ firearms system.
* **Bundled first-person replacement unavailable:** this stock's [display file][display] omits mandatory `model` and `texture` fields. Loading fails before its OEM adapter name is read. The inspected path therefore selects neither a separate stock model nor the built-in OEM replacement. Installation and camera recoil remain active. [Display reader][display-loader] · [Missing-field handling][missing-string] · [Attachment submission][attachment-data-and-mount] · [Adapter selection][state-and-visibility]
* The renderer still hides the named `stock_default` parts on [AKM][ak47-default], [DB-2 Durin][db_short-default], [HK G3][hk_g3-default], [HK-MP5A5][hk_mp5a5-default] and [Vector][vector45-default] when this stock is installed. [RPK's selected model][rpk-model] has no such node. This concerns those named parts and does not establish that all stock-shaped geometry or the whole gun disappears. [Held-gun model selection][first-person-model-selection] · [Selected model paths][fixed-gun-model-path] · [Default-stock visibility][state-and-visibility]

* DB-2 Durin accepts only this stock and its [selected model][db_short-model] lacks `stock_pos`, the mount used for separately drawn stocks. Its [built-in Tactical variant][db_short-oem] follows the separate OEM selection rule above; neither the missing mount nor the display failure prevents refitting or recoil effects. [Accepted stock][gun-list] · [Mount requirement][attachment-data-and-mount]

This bundled OEM display limitation is tracked in [#815](https://github.com/HungLo2020/MattMC/issues/815). The issue records source evidence, not a verified in-game appearance or a completed repair.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`, covering the recipe, exact fitting list, camera-recoil consumers and bundled OEM selection path. This is **source review**; no in-game crafting, refitting, recoil, appearance or multiplayer test, aggregate build, or browser verification was run. Custom resource-pack behavior is outside this review.

[data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/oem_stock_tactical_data.json#L1-L33
[display-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L128
[state-and-visibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1100-L1173
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/oem_stock_tactical.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/oem_stock_tactical.json#L1-L7
[stock-tab]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L185
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L182
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L82
[gun-list]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L108
[compatibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L160
[level-storage-tooltip]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczAttachmentItem.java#L17-L42
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[recoil-combined]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L148
[recoil-factors]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L537
[recoil-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L432
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L12-L61
[shot-values]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L35-L125
[camera-rotation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[shot-calculation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L179
[model-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L69-L114
[model-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1301-L1397
[display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/attachments/oem_stock_tactical_display.json#L1-L8
[missing-string]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/util/GsonHelper.java#L97-L103
[attachment-data-and-mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L615
[ak47-default]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/ak47_geo.json#L33-L37
[db_short-default]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/db_short_geo.json#L1531-L1535
[hk_g3-default]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/hk_g3_geo.json#L6609-L6613
[hk_mp5a5-default]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/hk_mp5a5_geo.json#L1835-L1839
[vector45-default]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/vector45_geo.json#L8079-L8083
[rpk-model]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/rpk_geo.json#L1-L9864
[first-person-model-selection]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L669-L696
[fixed-gun-model-path]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L159-L169
[db_short-model]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/db_short_geo.json#L1-L3158
[db_short-oem]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/db_short_geo.json#L857-L861
