# FastFire Sight Rised

**FastFire Sight Rised** is the alternate-fit version of [FastFire Sight](FastFireSight.md). Check the firearm list before crafting: this variant has its own accepted guns, including the Lonetrail Hand Cannon and Rhino Revolver. [Accepted guns][accepted-guns] · [Compatibility checks][compatibility]

## Obtaining

Craft **one FastFire Sight Rised** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Scope**, using **6 Iron Ingots** and **1 Redstone Dust**. The recipe makes this variant directly; it does not consume the other sight or a separate riser. [Recipe][recipe] · [Recipe grouping][index] · [Accepted table groups][table]

Carry the materials in your inventory and follow the shared [workbench instructions](../blocks/TaCZWorkbenches.md#using-a-workbench). The custom workbench loader reads this bundled recipe and defaults its output count to one. [Recipe loader][loader]

The item also appears in the Creative menu and is registered as `minecraft:sight_fastfire_rifle`. The [inventory browser’s mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) still apply: seeing a catalog entry in ordinary Survival does not supply the item. [Creative entry][creative] · [Registration][registration]

## Usage

Carry the sight, hold a compatible gun in your main hand, and choose **Scope** in the **Refit** screen. The table creates the item; installation consumes one carried sight and replaces the gun’s current Scope attachment. Follow [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for installation, removal and inventory-space precautions. [Refit selection][refit] · [Server installation][refit-server]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | Not level-based |
| Maximum stack size | 1 |
| Configured aimed zoom | 1.5× |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [.357 Rhino Revolver](357RhinoRevolver.md), [AA12 Shotgun](AA12Shotgun.md), [Accuracy International AWM](AccuracyInternationalAWM.md), [AKM](AKM.md), [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [Gewehr 43](Gewehr43.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M1 Carbine](M1Carbine.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M107 Sniper Rifle](M107SniperRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [M700 Sniper Rifle](M700SniperRifle.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

Type and level come from the registered attachment definition; compatibility requires both the Scope category and this exact attachment ID. The zoom row is the setting consumed by the current first-person field-of-view path, not a measured rendering result. [Definition][definition] · [Registration][registration] · [Compatibility checks][compatibility] · [Display setting][display] · [FOV consumer][scope-fov]

## Behavior

At full aim in first person, the bundled display supplies **1.5× magnification** to the world-view field-of-view calculation. The view transitions as you hold or release Aim; see the shared [gun controls](../mechanics/TaCZFirearms.md#controls). The current accessor uses the first zoom entry, and this sight supplies only one. [Display setting][display] · [Display loading][scope-loader] · [FOV calculation][scope-fov] · [First-person use][first-person] · [Zoom selection][scope-selection]

The imported weight, aim-accuracy and aim-down-sights (ADS) fields are not applied as handling bonuses by the inspected code. Aiming uses the shared transition; projectile spread uses the gun’s accuracy settings and fire-mode adjustments. [Imported profile][profile] · [Aim transition][aim-transition] · [Accuracy calculation][accuracy] · [Attachment profile reader][profile-loader]

The selected zoom also enters the camera-recoil calculation when the gun has recoil data. This sight’s profile supplies no additional pitch/yaw recoil modifier; the final response still depends on the gun, aiming state and other installed attachments. [Camera recoil][camera-recoil] · [Recoil calculation][recoil-scaling] · [Profile][profile]

## Notes

* This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md).
* “Rised” and the item’s name do not establish universal compatibility or a particular reticle appearance. Use the accepted firearm list above.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, registered identity, accepted guns, refitting, display loading, aiming, FOV and handling consumers were checked. This is **source review**, not an in-game crafting, aiming, rendering or multiplayer test. It does not establish reticle appearance or compatibility with other gun packs.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L184
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1667
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[accepted-guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L11-L73
[refit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L111
[refit-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[scope-fov]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L84
[scope-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L125
[scope-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L179-L193
[first-person]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L496-L501
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L14-L61
[accuracy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L48
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L315
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L39
[recoil-scaling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L148
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/sight_fastfire_rifle.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/sight_fastfire_rifle.json#L1-L8
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L185
[display]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/display/attachments/sight_fastfire_rifle_display.json#L9-L14
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/sight_fastfire_rifle_data.json#L1-L9
