# Mastiff Shotgun Muzzle Brake

Mastiff Shotgun Muzzle Brake reduces both camera-recoil components on four specific shotguns. It is the recoil-reducing choice when comparing it with the [Shotgun Choke](ShotgunChoke.md).

## Obtaining

Craft **one Mastiff Shotgun Muzzle Brake** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using **18 [Iron Ingots](IronIngot.md)** and **4 [Gold Ingots](GoldIngot.md)**. No existing muzzle attachment is an ingredient. The custom recipe loader supplies one output and takes its group from this item's Muzzle index entry. [Recipe][recipe] · [Group][index] · [Recipe loader][loader] · [Table groups][groups]

Carry the ingredients in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the crafting controls. Crafting gives you the separate attachment item; it does not install it on a gun. [Craft transaction][craft]

It is registered as `minecraft:muzzle_brake_mastiff_sg`, stacks to **one**, and is also available through the Creative Menu. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can display it in Survival without granting ordinary Survival item insertion; use the table recipe to obtain it there. [Registration][registration] · [Creative entries][creative]

## Usage

With Mastiff in your inventory, hold a supported gun in your **main hand**, press **Z** for Refit by default, then choose **Muzzle** and the carried attachment. The [firearms refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) covers replacement, removal, and the different inventory-space limits for each. [Default key][keys] · [Opening Refit][refit-input] · [Selection][refit-screen] · [Server transaction][refit-server]

There is **one Muzzle slot per gun**. Installing Mastiff replaces another muzzle device, so it cannot stack with the Shotgun Choke or a shotgun silencer. Recoil modifiers from other attachment slots can still combine with it. [Slot storage][slot-storage] · [Modifier collection][collection]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M870](M870.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md) |

Only these **four guns** accept this exact attachment ID and the Muzzle category. Being a shotgun is insufficient: DB-2, DB-4, and Winchester 1897 are outside this fit set. [Attachment definition][definition] · [Gun definitions][gun-definitions] · [Fit check][fit]

## Behavior

Mastiff contributes **pitch ×0.65** and **yaw ×0.70** to the camera-recoil calculation. Both values reduce their respective vertical and horizontal components at the modifier stage. [Attachment profile][profile] · [Active profile reader][profile-loader] · [Evaluation][evaluation]

These are **modifier-stage contributions with other inputs held fixed**, not measured ratios of final camera travel. The gun's recoil curve, aiming/zoom, crawling, and modifiers from other installed slots also enter the calculation before sampling. The sampled result moves the player's view. [Calculation][collection] · [Evaluation][evaluation] · [Sampling][sampling] · [Camera movement][camera]

Its imported weight `0.25`, ADS addend `0.08`, hip-inaccuracy multiplier `1.25`, and aimed-inaccuracy multiplier `1.10` are **not working weight, aim-speed, or spread penalties** in this snapshot. The active attachment-profile reader consumes recoil fields; the aim transition and projectile calculations do not apply those imported values. There is no direct damage or pellet-dispersion bonus from the brake either. Camera movement can change where you aim a later shot, which is separate from changing the shot's dispersion. [Profile][profile] · [Active reader][profile-loader] · [Aim transition][aim-transition] · [Shot creation][server-shot] · [Ballistics][ballistics]

## Notes

The bundled display supplies an installed model and texture, and all four supported gun models have the `muzzle_pos` mount required by the rendering code. Only SPAS-12 has a `muzzle_default` part for the default-muzzle visibility rule to hide; the other three have no such node. [Display][display] · [Display reader][display-reader] · [Attachment rendering][attachment-rendering] · [Default-muzzle rule][default-muzzle] · [AA12 model][aa12-model] · [M1014 model][m1014-model] · [M870 model][m870-model] · [SPAS-12 model][spas_12-model]

Mastiff does not satisfy the renderer's attachment-ID check for `silencer`, so installing it does not suppress first-person muzzle flash through that check. Flash submission still depends on the gun's flash node, timing, and loaded flash data. [Flash conditions][flash]

This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md).

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. Shot feedback activates camera recoil and the frame renderer applies it to the view; the held-gun caller reaches the reviewed attachment-rendering path. Crafting, refitting, camera motion, model alignment, and appearance were **not tested in game**; this review makes no remote-player visibility claim. [Shot feedback][feedback] · [Frame application][frame] · [Held-gun renderer][held-renderer]

[registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[creative]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[craft]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[groups]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L184
[keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L22
[refit-input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L51
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L78-L112
[refit-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[slot-storage]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L37-L80
[fit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L11-L73
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[collection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[evaluation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L519-L537
[sampling]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L433
[camera]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L66
[feedback]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L127-L160
[frame]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L757-L763
[server-shot]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L175
[ballistics]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[display-reader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L127
[attachment-rendering]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L616
[default-muzzle]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1105-L1173
[flash]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[held-renderer]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L668-L696
[definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L144-L144
[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/muzzle_brake_mastiff_sg.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/muzzle_brake_mastiff_sg.json#L1-L6
[profile]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/muzzle_brake_mastiff_sg_data.json#L1-L20
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/muzzle_brake_mastiff_sg_display.json#L1-L9
[aa12-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/aa12_geo.json#L1-L6506
[m1014-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m1014_geo.json#L1-L7533
[m870-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m870_geo.json#L1-L3568
[spas_12-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/spas_12_geo.json#L1-L10036
