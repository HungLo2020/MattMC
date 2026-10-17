# EXP3 HCOG

## Obtaining

Craft **1 EXP3 HCOG** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in the **Scope** group. [Recipe][recipe] · [Scope group][index] · [Recipe loading][recipe-loader] · [Table filter][workbench-filter]

| Ingredient | Count |
| --- | ---: |
| Iron Ingots | 14 |
| Gold Ingots | 5 |
| Redstone Dust | 8 |
| Glass | 4 |

See [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the crafting steps. This item is also available from the Creative Menu as `minecraft:sight_exp3`. [Creative entries][creative] · [Registration][registration]

## Usage

Hold a compatible gun in your main hand, carry this sight, and open **Refit** (**Z** by default). Select **Scope**, then choose the sight from your inventory. The gun must accept this exact attachment; the 33 compatible guns are listed below. [Accepted attachments][fit] · [Compatibility checks][fit-check] · [Refit screen][refit-ui] · [Default controls][key-bindings]

The gun holds **one Scope attachment at a time**. Installing consumes one carried sight, including in Creative, and replaces the previous Scope attachment. Keep inventory space for the returned item; use **Unload** in the Scope slot to remove it. See [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for inventory and replacement details. [One attachment per slot][stored-attachment] · [Installation][refit-server]

Close refit and **hold Aim** (right mouse button by default). This sight uses the single **2×** zoom setting below. The listed Zoom binding (**V**) has no active cycling action in this snapshot. [Default controls][key-bindings] · [Active controls][input-handler] · [Selected values][first-values]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | Not level-based |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [AA12 Shotgun](AA12Shotgun.md), [Accuracy International AWM](AccuracyInternationalAWM.md), [AKM](AKM.md), [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [Gewehr 43](Gewehr43.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M1 Carbine](M1Carbine.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M107 Sniper Rifle](M107SniperRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [M700 Sniper Rifle](M700SniperRifle.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md), [MK14 EBR](MK14EBR.md), [P90 PDW](P90PDW.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

## Behavior

| Optical setting | Selected value |
| --- | --- |
| Aimed world magnification | 2× |
| Aimed gun/optic model FOV | 35.0° |
| Aim view | View 1, `scope_view` |

These values come from the sight's display file. The active loader selects its first zoom entry and, because no view list is supplied, defaults to view 1. The model FOV comes from `fov`. [Display settings][display] · [Optical loader][display-loader] · [Selected values][first-values]

In first person, aiming transitions the world view toward **2×** magnification and independently transitions the held gun/optic toward **35.0°** model FOV. The model FOV is not the world-view angle: world FOV also depends on the player's starting FOV. [Optical loader][display-loader] · [First-person FOV][fov]

The bundled sight has the selected `scope_view` node, and source checks found `scope_pos` mounts on its compatible guns. The renderer combines those nodes for aiming placement. Missing geometry would make placement fall back to the gun's `iron_view`; the optical FOV calculation runs separately. [Sight geometry][geometry] · [Aim placement][view-lookup] · [Placement fallback][positioning-fallback] · [Optical loader][display-loader]

Installing the sight enables the renderer's Scope visibility rules: a gun's `mount` node is enabled, `carry` and `sight` nodes are hidden, and `sight_folded` is enabled where those nodes exist. In first person, the sight's ocular geometry also enters the optical stencil route. These source paths do not guarantee visible alignment, glass, or reticle output. [Mount and sight visibility][visibility] · [Attachment rendering][attachment-render] · [Optical rendering path][optical-stencil]

The selected zoom also feeds the common aimed camera-recoil calculation. Its result depends on aim progress and the gun's recoil data; it is not a separate recoil bonus declared by this attachment. [Camera recoil][camera-recoil] · [Zoom factor][recoil-zoom]

## Notes

* This item is part of the integrated TaCZ firearms system.
* The imported data declares `weight: 0.35` and `ads.addend: -0.02`. The checked attachment-data reader only applies recoil fields, which this file does not contain. These imported numbers do not establish an active change to weight or aim-down-sights speed. [Imported data][imported-attachment-data] · [Attachment-data reader][recoil-attachment-parser]
* Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. No in-game crafting, refitting, camera, reticle, or cross-gun rendering test was run.

[creative]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1661-L1670
[registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L183-L183
[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/sight_exp3.json#L1-L33
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/sight_exp3.json#L1-L12
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[workbench-filter]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L178
[fit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L11-L73
[fit-check]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[stored-attachment]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L12-L81
[refit-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[refit-ui]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L109
[key-bindings]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L58
[input-handler]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L105
[first-values]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L128-L194
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/sight_exp3_display.json#L1-L25
[display-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L126
[fov]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L475-L504
[geometry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/attachment/sight_exp3_geo.json#L1299-L1302
[view-lookup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L729-L742
[positioning-fallback]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L846-L870
[visibility]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1100-L1148
[optical-stencil]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L214-L292
[attachment-render]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L537-L615
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[recoil-zoom]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[imported-attachment-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/sight_exp3_data.json#L1-L6
[recoil-attachment-parser]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L319
