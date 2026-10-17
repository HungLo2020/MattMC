# DeltaPoint Sight

**DeltaPoint Sight** fits the eight firearms listed below, including the [P90 PDW](P90PDW.md). For a gun outside that list, check the separately registered [DeltaPoint Sight Rised](DeltaPointSightRised.md) and its compatibility before crafting. [Accepted guns][accepted-guns] · [Compatibility checks][compatibility]

## Obtaining

Craft **one DeltaPoint Sight** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Scope**, using **4 Iron Ingots** and **1 Redstone Dust**. The Rised version is a separate item with a different recipe and accepted firearm list. [Recipe][recipe] · [Recipe grouping][index] · [Accepted table groups][table]

Carry the materials in your inventory and follow the shared [workbench instructions](../blocks/TaCZWorkbenches.md#using-a-workbench). The custom workbench loader reads this bundled recipe and defaults its output count to one. [Recipe loader][loader]

The item also appears in the Creative menu and is registered as `minecraft:sight_deltapoint_pistol`. The [inventory browser’s mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) still apply: seeing a catalog entry in ordinary Survival does not supply the item. [Creative entry][creative] · [Registration][registration]

## Usage

Carry the sight, hold a compatible gun in your main hand, and choose **Scope** in the **Refit** screen. The table creates the item; installation consumes one carried sight and replaces the gun’s current Scope attachment. Follow [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for installation, removal and inventory-space precautions. [Refit selection][refit] · [Server installation][refit-server]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | Not level-based |
| Maximum stack size | 1 |
| Configured aimed zoom | 1.5× |
| Compatible guns | [B93R](B93R.md), [CZ 75](CZ75.md), [Glock 17](Glock17.md), [M9A4](M9A4.md), [MK23 Offensive Pistol](MK23OffensivePistol.md), [P320](P320.md), [P90 PDW](P90PDW.md), [Timeless .50 Z-Type](Timeless50ZType.md) |

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
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/sight_deltapoint_pistol.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/sight_deltapoint_pistol.json#L1-L8
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L181
[display]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/display/attachments/sight_deltapoint_pistol_display.json#L9-L14
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/sight_deltapoint_pistol_data.json#L1-L9
