# Lopro Tactical Laser

## Obtaining

Craft **one Lopro Tactical Laser** in the **Laser** group of the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) using **10 Iron Ingots and 4 Redstone Dust**. Carry the materials in your inventory; follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting and inventory-space rules. [Recipe][recipe] · [Laser group][index] · [Output count][output]

The registered item is `minecraft:laser_lopro`. It also appears in the combined [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). The catalog is visible in Survival, but ordinary Survival cannot insert its entries; the server's [infinite-materials admission gate](../mechanics/InventoryBrowser.md#mode-and-permission-limits) normally requires Creative. [Registration][registration] · [Category entry][creative]

## Usage

Lopro Tactical Laser is a TaCZ laser attachment. Select **Laser** in the gun's [Refit screen](../mechanics/TaCZFirearms.md#refitting-attachments) to install a carried item on one of the compatible firearms below. That shared guide covers the controls, replacement/removal, and inventory-return limits; the Attachment Table makes the item rather than installing it.

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Laser |
| Attachment level | Not level-based |
| Stack limit | 1 |
| Compatible guns | [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md) |

The fit list requires both the gun's Laser category and this exact attachment ID. Uninstalled copies do not stack. [Compatibility check][compatibility] · [Accepted guns][fits] · [Stack limit][registration]

## Behavior

In the reviewed **first-person rendering path**, the installed attachment body is drawn where the gun model provides a laser mount. The model's laser and flashlight details use full-bright rendering. [Held-gun path][first-person] · [Attachment rendering][renderer] · [Model][geometry] · [Bright details][full-bright]

**A projected laser beam, an on/off control, and a beam-color editor are not implemented in the reviewed paths.** The display reader loads the attachment body but does not consume its imported beam settings; the active gun and refit controls provide no laser switch or color editor. Do not rely on this attachment to draw an aiming line. [Display settings][display] · [Display reader][display-reader] · [Gun controls][controls] · [Refit controls][refit-controls]

The bundled attachment file also contains accuracy, weight, and aim-time values, but the inspected shot and aiming paths do not apply them. The active attachment recoil reader finds no recoil modifier in this profile either. Treat this as a fitting/model choice, without a source-supported accuracy bonus or handling penalty. [Profile][profile] · [Shot setup][shot] · [Ballistics][ballistics] · [Aim transition][aim] · [Recoil reader][recoil]

**FN EVOLYS model limitation:** this gun accepts the attachment, but its bundled model has no `laser_pos` mount. The inspected first-person renderer therefore skips this attachment's model on FN EVOLYS. This is a source-derived limit of that rendering path, not an in-game visibility test. Tracked in [issue #814](https://github.com/HungLo2020/MattMC/issues/814). [Accepted guns][fits] · [Resolved gun model][model-loader] · [FN EVOLYS geometry][evolys-model] · [Geometry parser][geometry-parser] · [Exact mount lookup][marker-lookup] · [Draw/skip check][renderer]

## Notes

* This item is part of the integrated TaCZ firearms system.

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, accepted guns, first-person attachment rendering, controls, and active stat consumers were checked. No in-game crafting, refitting, rendering, or combat test was performed; other resource packs or later code can change the result.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/laser_lopro.json
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/laser_lopro.json
[output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[fits]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[first-person]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L669-L696
[renderer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L620
[geometry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/geo_models/attachment/laser_lopro_geo.json
[full-bright]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L325-L368
[display]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/display/attachments/laser_lopro_display.json
[display-reader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L127
[controls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L106
[refit-controls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L78-L112
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/laser_lopro_data.json
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L175
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L12-L61
[recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L315
[model-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L165-L169
[evolys-model]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/geo_models/gun/fn_evolys_geo.json
[geometry-parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L757-L786
[marker-lookup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1050-L1052
