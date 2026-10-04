# Cthulhu K7 Brake

Cthulhu K7 Brake reduces both the vertical and horizontal camera-recoil components on compatible TaCZ firearms.

## Obtaining

Craft **one Cthulhu K7 Brake** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in **Muzzle**. Carry these materials for one craft; the recipe does not consume another muzzle attachment. [Recipe][recipe] · [Recipe group][index] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 16 |
| [Gold Ingots](GoldIngot.md) | 5 |

The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) covers crafting controls and inventory handling. The table creates the attachment item; install it separately through Refit. [Craft transaction][craft]

It is registered as `minecraft:muzzle_brake_cthulhu` and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) is visible in Survival, but its catalog does not grant ordinary Survival item insertion. Use the recipe above to craft it in Survival. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Muzzle** and choose the carried attachment. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for installation, replacement, removal, and inventory-space limits. [Default key][keys] · [Refit opening][refit-input] · [Carried-item selection][refit-screen] · [Server installation][refit-server]

A gun has only **one Muzzle slot**. Muzzle devices are alternatives, so these brakes and compensators cannot stack together through normal refitting. Recoil modifiers from attachments in other slots can combine with the selected muzzle device. [Slot storage][slot-storage] · [Modifier collection][collection] · [Combined evaluation][evaluation]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [AKM](AKM.md), [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M1 Carbine](M1Carbine.md), [M1 Garand](M1Garand.md), [M16A1 Service Rifle](M16A1ServiceRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M1A1 Thompson](M1A1Thompson.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [M700 Sniper Rifle](M700SniperRifle.md), [MK14 EBR](MK14EBR.md), [P90 PDW](P90PDW.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Sturmgewehr 44](Sturmgewehr44.md), [Type 81-1 Service Rifle](Type811ServiceRifle.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

These **31 guns** accept both the Muzzle category and this attachment's exact ID. A shared gun class or attachment category alone does not establish compatibility. [Attachment definition][definition] · [Gun definitions][gun-definitions] · [Fit check][fit]

## Behavior

Both axis multipliers are below 1, so this brake reduces each camera-recoil component at the modifier stage. [Attachment profile][profile] · [Profile consumer][profile-loader] · [Modifier evaluation][evaluation]

| Camera-recoil component | This attachment's multiplier | Effect at the modifier stage |
| --- | ---: | --- |
| Vertical (pitch) | ×0.85 | Reduced |
| Horizontal (yaw) | ×0.80 | Reduced |

These are **component multipliers with other inputs held fixed**, not guaranteed ratios of final camera movement. The gun's sampled recoil, aiming/zoom, pose, and modifiers from other installed attachments also affect the result. The resulting recoil values drive changes to the player's view rotation. [Recoil calculation][collection] · [Combined evaluation][evaluation] · [Sampling][sampling] · [Camera movement][camera]

This camera-recoil effect is not a direct damage or server bullet-dispersion bonus. The firing path computes those values separately. [Shot creation][server-shot] · [Dispersion and damage calculation][dispersion-damage]

## Notes

- This item is part of the integrated TaCZ firearms system.
- Its imported ADS and hip/aim inaccuracy fields are not applied by the active attachment-profile consumer. They do not establish an aim-speed or accuracy change in this snapshot. [Imported profile][profile] · [Active profile consumer][profile-loader]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The recipe, custom workbench loader/group/output, registration, 31-gun fit set, refit storage, and recoil path from profile loading through collection, evaluation, sampling, and camera movement were checked. Client shot feedback triggers that recoil path, and the frame renderer applies it before camera setup. This is **source review**, not an in-game crafting, refitting, or recoil test. [Recipe loader][loader] · [Shot feedback][feedback] · [Frame application][frame]

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L184
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L21
[refit-input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L46
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L111
[refit-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[slot-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L66
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L13-L73
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[collection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[evaluation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L519-L537
[sampling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L433
[camera]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[feedback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L127-L148
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L757-L763
[server-shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L175
[dispersion-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L61
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L142-L142
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_brake_cthulhu.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/muzzle_brake_cthulhu.json#L1-L6
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_brake_cthulhu_data.json#L1-L19
