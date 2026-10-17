# TD Grip

TD Grip is a fitting and model choice for the twenty compatible firearms below. Its bundled profile supplies **no camera-recoil modifier**. [Profile][profile] · [Active recoil reader][recoil-reader]

## Obtaining

Craft **1 TD Grip** at the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in the **Grip** group, using **10 Iron Ingots and 4 Leather** carried in your inventory. Crafting makes the loose attachment; it does not install it on a gun. [Recipe][recipe] · [Grip group][group] · [Recipe and output loading][recipe-loader] · [Table category][table-category] · [Inventory materials][crafting]

TD Grip is also available from the Creative Menu. Its registered ID is `minecraft:grip_td`. [Definition][definition] · [Registration][registration] · [Creative entry][creative]

## Usage

Carry the grip and hold a compatible firearm in your **main hand**. Press **Z** (the default Refit binding), select **Grip**, then choose **TD Grip** from the compatible carried items. Installation does not require standing at a workbench. [Default binding][keys] · [Opening Refit][refit-input] · [Attachment selection][refit-screen]

A gun holds **one installed Grip**; fitting another replaces that slot. Installation consumes one carried grip, including in Creative. Leave inventory space for a removed attachment and see [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, **Unload**, and full-inventory behavior. [Stored slot][slot] · [Installation][refit-server]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Grip |
| Attachment level | Not level-based |
| Max stack size | 1 |
| Camera pitch recoil (vertical) | 1×; no modifier from this grip |
| Camera yaw recoil (sideways) | 1×; no modifier from this grip |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M1A1 Thompson](M1A1Thompson.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [UMP45 SMG](UMP45SMG.md), [Vector SMG](VectorSMG.md) |

The fit list requires both the gun's Grip category and this exact attachment ID. The camera rows describe this grip's contribution; see the limits below. [Compatibility check][fit-gate] · [Accepted guns][fits] · [Stack limit][registration] · [Profile][profile] · [Active recoil reader][recoil-reader]

## Behavior

**TD Grip leaves both camera-recoil axes unchanged by this attachment.** Its profile contains no recoil object, so its contribution is the identity factor: **1× pitch** (vertical movement) and **1× yaw** (sideways movement). This does not remove the gun's recoil or cancel modifiers from other installed attachments. [Profile][profile] · [Active recoil reader][recoil-reader] · [Combined attachments][combined-modifiers] · [Modifier evaluation][modifier-math]

The gun's sampled recoil curve, aiming state and other installed attachment modifiers still determine its camera response. [Camera recoil sampling][recoil-sampling] · [Random curve samples][sampled-curve] · [Combined attachments][combined-modifiers]

The profile includes weight and accuracy entries, including two `0.88` accuracy multipliers. The reviewed attachment loader and shot calculations do not apply those fields, so they do not give TD Grip a 12% accuracy bonus or a working weight penalty. [Profile][profile] · [Active recoil reader][recoil-reader] · [Shot values][ballistics]

There is **no direct grip-driven spread or damage bonus** in the reviewed shot path. Camera recoil does change the player's view direction, which can affect where subsequent shots are aimed. [Shot setup][shot] · [Shot values][ballistics] · [View rotation][camera]

The weapon model also has a separate firing animation; TD Grip does not provide a source-supported reduction of that animated kick. [Animation selection][animation-selection] · [Shoot animation][shoot-animation] · [Model animation][model-animation]

## Notes

* This item is part of the integrated TaCZ firearms system.
* In the reviewed first-person path, the attachment renderer uses the gun's `grip_pos` mount and hides `grip_default` if that default-grip node exists. These model rules are separate from installation and the camera modifiers. [Held-gun path][first-person] · [Grip mount][mount] · [Default-part visibility][visibility]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. This is source and bundled-resource review, not an in-game crafting, refitting, recoil, rendering or multiplayer test.

[profile]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/grip_td_data.json#L1-L9
[recoil-reader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L331
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/grip_td.json#L1-L21
[group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/grip_td.json#L1-L7
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[table-category]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L170
[crafting]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L105
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L130
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[refit-input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L47
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[slot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[fit-gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[fits]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[combined-modifiers]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L127-L148
[modifier-math]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L537
[recoil-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L124
[sampled-curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L432
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[shot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L179
[camera]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[animation-selection]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunAnimationTimings.java#L73-L102
[shoot-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L69-L74
[model-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L189-L211
[first-person]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L668-L695
[mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L523-L588
[visibility]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1138-L1172
