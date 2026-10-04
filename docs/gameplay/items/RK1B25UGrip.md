# RK-1 B25U Grip

## Obtaining

Craft **one RK-1 B25U Grip** in the **Grip** group of the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) with **16 Iron Ingots and 5 Leather**. These are the exact item ingredients (`minecraft:iron_ingot` and `minecraft:leather`), carried in your inventory. [Recipe][recipe] · [Grip group][index] · [Recipe loading and output count][recipe-loading] · [Table groups][table-group]

Follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the craft control and inventory-space rules. Crafting makes the carried item; installing it is a separate step. [Crafting transaction][crafting]

Its registered ID is `minecraft:grip_rk1_b25u`. It is also available from the Creative inventory; that entry does not grant ordinary Survival players free copies. See the [inventory browser’s mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Definition][definition] · [Item registration][item-registration] · [Creative entry][creative]

## Usage

Carry the grip, hold one of the compatible guns below in your **main hand**, and press **Z** by default to open Refit. Select **Grip**, then **RK-1 B25U Grip**. You do not need to stand at a workbench to install it. [Key binding][refit-key] · [Opening Refit][open-refit] · [Compatible inventory choices][refit-screen]

Installing consumes one carried copy, including in Creative, and replaces the gun’s single **Grip** slot if occupied. It does not add a second grip. Read [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, unloading, and inventory-return limits. [Installation][refit-server] · [Stored slot][store-grip]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Grip |
| Attachment level | Not level-based |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M1A1 Thompson](M1A1Thompson.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [UMP45 SMG](UMP45SMG.md), [Vector SMG](VectorSMG.md) |
| Stack limit | 1 |
| Camera pitch factor (vertical) | 1× (no modifier) |
| Camera yaw factor (sideways) | 0.66× |

All **20** listed guns, including **Vector SMG**, accept this exact grip ID as well as the Grip category. Sharing an attachment category alone is insufficient. Uninstalled copies occupy separate inventory slots. [Accepted guns][accepted-guns] · [ID check][fit-gate] · [Category check][category-gate] · [Stack limit][item-registration]

## Behavior

RK-1 B25U Grip supplies a **0.66× yaw (sideways) factor** to camera recoil. Its profile has no pitch modifier, so its vertical contribution remains **1×**. Its active distinction is this sideways camera factor, rather than the accuracy and aiming-speed values also present in its imported file. [Grip profile][profile] · [Loaded fields and defaults][recoil-loader]

These values describe **this grip’s contribution to camera recoil**, not a measured reduction in final recoil. The game combines installed attachment modifiers with the gun’s recoil curves, aiming progress, zoom, and crawling state before applying the sampled camera movement. A **1×** contribution leaves that axis unchanged by this grip. [Installed modifiers][installed-modifiers] · [Combination][combine-modifiers] · [Recoil inputs][recoil-sampling] · [Curve sampling][sampled-curve]

The reviewed firing path gives this grip **no direct projectile-spread or damage bonus**. Camera recoil still changes the player’s view rotation, so it can change where later shots are aimed. [Shot values and direction][shot-values] · [Spread and damage][spread-damage] · [Posture selection][spread-posture] · [Shot feedback][shot-feedback] · [Camera rotation][camera] · [Render-loop application][render-loop]

The imported file includes weight, ADS, general inaccuracy, and aimed inaccuracy fields. The active attachment reader does not apply those fields, and the aiming controller does not read the grip’s ADS value. They establish neither a spread bonus or penalty nor faster or slower aiming here. [Imported profile][profile] · [Attachment reader][recoil-loader] · [Aim transition][aim-transition]

The separate weapon-model shoot animation does not apply these camera factors. A smaller camera factor therefore does not promise the same reduction in the weapon model’s animated kick, although changing the view can still affect its on-screen presentation. [Animation selection][model-selection] · [Shoot trigger][model-trigger] · [Animation snapshot][model-snapshot] · [Model rendering][model-render]

## Notes

* This item is part of the integrated TaCZ firearms system.
* The reviewed first-person renderer selects the held gun’s model and the installed grip’s display model, then draws the grip at `grip_pos`. If its display/model cannot load or the gun lacks that mount, the grip mesh is skipped; those visual checks do not themselves reject installation or remove stored camera modifiers. [Held-gun path][held-gun] · [Gun geometry][gun-model] · [Display reader][display-reader] · [Attachment model][attachment-model] · [Mount and drawing][draw-mount] · [Installation][category-gate] · [Recoil lookup][installed-modifiers]
* FN EVOLYS’s bundled geometry contains `grip_pos`. Its [documented laser-mount limitation](FNEVOLYSMachineGun.md#bundled-first-person-laser-mount) is specific to lasers and does not establish a missing grip mount. [FN EVOLYS grip mount][evolys-grip]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The recipe, fit list, active stat consumers, and rendering conditions above come from source and bundled resources. No in-game crafting, refitting, recoil, visual, or multiplayer test was performed.

[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/attachments/grip_rk1_b25u.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/attachments/grip_rk1_b25u.json#L1-L7
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L127
[profile]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/attachments/grip_rk1_b25u_data.json#L1-L17
[item-registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2724-L2741
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L182
[table-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L170
[crafting]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L105
[refit-key]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L26
[open-refit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L47
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L110
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[store-grip]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[fit-gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[category-gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L16-L34
[accepted-guns]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L331
[installed-modifiers]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L127-L148
[combine-modifiers]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L537
[recoil-sampling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L124
[sampled-curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L432
[shot-feedback]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L142-L149
[camera]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[render-loop]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L757-L762
[shot-values]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L179
[spread-damage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[spread-posture]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L14-L61
[model-trigger]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L69-L74
[model-snapshot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L96-L114
[model-selection]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunAnimationTimings.java#L73-L96
[model-render]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L185-L211
[held-gun]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L668-L695
[gun-model]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L163-L167
[draw-mount]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L523-L588
[attachment-model]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L596-L615
[display-reader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L125
[evolys-grip]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/geo_models/gun/fn_evolys_geo.json#L10829-L10835
