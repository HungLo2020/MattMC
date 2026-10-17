# SE-5 Express Grip

SE-5 Express Grip is a TaCZ grip attachment with separate **0.93× pitch and 0.92× yaw camera-recoil factors**. Its stored level 5 is not a grip upgrade tier. [Attachment data][data] · [Active loader][recoil-loader] · [Definition][definition]

## Obtaining

At the **[TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table)**, select the **Grip** group. Carry these exact materials in your player inventory; one craft produces **one SE-5 Express Grip**. [Recipe][recipe] · [Grip grouping][index] · [Table groups][table-groups] · [Recipe loading and output count][recipe-loading] · [Crafting][crafting]

| Material | Count per craft |
| --- | ---: |
| Iron Ingot (`minecraft:iron_ingot`) | 10 |
| Leather (`minecraft:leather`) | 4 |

SE-5 Express Grip can also be obtained from the Creative Menu. It is registered as `minecraft:grip_se_5`. [Registration][registration] · [Creative entries]

## Usage

1. Carry the grip and hold one of the compatible firearms below in your **main hand**.
2. Press **Z** with the default bindings to open **Refit**.
3. Select **Grip**, then choose **SE-5 Express Grip** from the compatible carried items.

[Held-gun controls][input] · [Default key][keys] · [Refit selection][refit-screen]

Installation consumes one carried grip, including in Creative. A gun has one installed Grip slot; fitting another grip replaces it. Crafting and installation are separate steps, and Refit does not require a nearby workbench. Leave room for removed attachments and follow the shared [refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, **Unload**, and full-inventory behavior. [Installation][refit-server] · [Single-slot storage][storage]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Grip |
| Attachment level | 5 (stored metadata; no active grip-tier effect) |
| Max stack size | 1 |
| Camera pitch recoil factor | 0.93× |
| Camera yaw recoil factor | 0.92× |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M1A1 Thompson](M1A1Thompson.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [UMP45 SMG](UMP45SMG.md) |

The **19 linked guns** are the accepted set for this grip. The gun must accept both the Grip category and this particular item; an available Grip slot alone is insufficient. [Accepted items][guns] · [Compatibility checks][fit] · [Item definition][definition] · [Stack limit][registration]

The stored level **5** does not unlock a grip tier or add a capacity, reload-speed, or model-variant bonus. The active level-based paths read the **Extended Mag** slot. [Capacity][level-capacity] · [Reload timing][level-reload] · [Animation choice][level-animation] · [Model variants][level-model]

## Behavior

SE-5 Express Grip contributes **0.93× pitch** and **0.92× yaw** to camera recoil. Pitch is vertical view movement; yaw is sideways view movement. Those multipliers are respectively 7% and 8% below the unchanged 1× contribution. [Attachment data][data] · [Active loader][recoil-loader]

These are this grip's contributions to **sampled camera recoil**, not measured reductions in the final kick. The gun's recoil curve, aiming and posture, and other installed modifiers also affect the result. [Combined recoil][combined-recoil] · [Factor combination][recoil-factors] · [Curve sampling][recoil-sampling]

The imported weight and aim-down-sights (`ads`) fields are **inactive here**; do not treat them as working handling bonuses or penalties. The current aiming transition does not use grip ADS values. [Attachment data][data] · [Active loader][recoil-loader] · [Aim transition][aim]

There is **no direct grip-driven spread or damage change** in the reviewed shot calculation. Camera recoil does change view direction, so it can affect where later shots are aimed. The gun-model shoot animation is separate and does not use these camera factors; they do not establish matching reductions in weapon-model kick. [Shot calculation][shots] · [Shot values][shot-values] · [View rotation][camera] · [Shoot animation][model-animation] · [Model sampling][model-sampling]

## Notes

* This item is part of the integrated TaCZ firearms system.
* The installed grip uses the gun model's grip mount. A built-in grip or default/tactical handguard changes visibility only when the model contains the corresponding parts. [Mount and submission][mount] · [Conditional visibility][visibility]
* FN EVOLYS has a bundled grip mount. Its [laser-mount warning](FNEVOLYSMachineGun.md#bundled-first-person-laser-mount) concerns the Laser slot. [FN EVOLYS grip mount][fn-grip]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, accepted items, active effect consumers, and bundled grip mounts were checked in source. This is **source review**, not an in-game crafting, recoil, appearance, or multiplayer test.

[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[Creative entries]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L182
[crafting]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L105
[table-groups]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L170
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L47
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[guns]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L11-L71
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L331
[combined-recoil]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L148
[recoil-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L432
[recoil-factors]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L537
[camera]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[aim]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L38-L61
[shots]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L179
[shot-values]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[model-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L69-L114
[model-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L189-L211
[mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L542-L588
[visibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1138-L1172
[fn-grip]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/fn_evolys_geo.json#L10827-L10836
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/grip_se_5.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/grip_se_5.json#L1-L7
[data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/grip_se_5_data.json#L1-L14
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L129
[level-capacity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L320-L331
[level-reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L69-L76
[level-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunAnimationTimings.java#L277-L284
[level-model]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1121-L1158
