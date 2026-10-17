# Shotgun Choke

Shotgun Choke currently increases both camera-recoil components and provides **no implemented pellet-spread tightening**. Do not spend materials on it expecting the accuracy benefit its name suggests.

## Obtaining

Craft **one Shotgun Choke** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using **20 [Iron Ingots](IronIngot.md)** and **6 [Gold Ingots](GoldIngot.md)**. No existing muzzle attachment is an ingredient. The custom recipe loader supplies one output and takes its group from this item's Muzzle index entry. [Recipe][recipe] · [Group][index] · [Recipe loader][loader] · [Table groups][groups]

Carry the ingredients in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the crafting controls. Crafting gives you the separate attachment item; it does not install it on a gun. [Craft transaction][craft]

It is registered as `minecraft:muzzle_choke_sg`, stacks to **one**, and is also available through the Creative Menu. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can display it in Survival without granting ordinary Survival item insertion; use the table recipe to obtain it there. [Registration][registration] · [Creative entries][creative]

## Usage

With the choke in your inventory, hold a supported gun in your **main hand**, press **Z** for Refit by default, then choose **Muzzle** and the carried attachment. The [firearms refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) covers replacement, removal, and the different inventory-space limits for each. [Default key][keys] · [Opening Refit][refit-input] · [Selection][refit-screen] · [Server transaction][refit-server]

There is **one Muzzle slot per gun**. The choke replaces a fitted brake or silencer; it cannot be combined with [Mastiff Shotgun Muzzle Brake](MastiffShotgunMuzzleBrake.md) in a second muzzle slot. Recoil modifiers from other attachment slots can still combine with it. [Slot storage][slot-storage] · [Modifier collection][collection]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M870](M870.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md) |

This is the complete **four-gun** fit set, matching Mastiff. Each gun accepts the Muzzle category and this exact item ID. DB-2, DB-4, and Winchester 1897 are not compatible just because they are shotguns. [Attachment definition][definition] · [Gun definitions][gun-definitions] · [Fit check][fit]

## Behavior

The active profile contributes **pitch ×1.45** and **yaw ×1.45**, increasing both vertical and horizontal camera-recoil components at the modifier stage. For reducing those components on the same guns, choose Mastiff, whose contributions are pitch ×0.65 and yaw ×0.70. [Choke profile][profile] · [Active reader][profile-loader] · [Evaluation][evaluation] · [Mastiff guide](MastiffShotgunMuzzleBrake.md#behavior)

These are **modifier-stage contributions with other inputs held fixed**, not measured ratios of final camera travel. The gun's recoil curve, aiming/zoom, crawling, and modifiers from other installed slots also enter the calculation before sampling. The sampled result moves the player's view. [Calculation][collection] · [Evaluation][evaluation] · [Sampling][sampling] · [Camera movement][camera]

The apparent spread benefit comes from imported inaccuracy fields: hip/standing, aimed, sneaking, and lying values are all `×0.65`. The active attachment reader and projectile-spread calculation do **not** apply them. The imported ADS addend `0.05` is likewise not a working aim-speed penalty. There is no implemented tighter pellet grouping, direct damage increase, or effective-range improvement from this choke. Its camera-recoil change can affect later aiming, but it does not change the server's dispersion parameter. [Imported profile][profile] · [Active reader][profile-loader] · [Aim transition][aim-transition] · [Shot creation][server-shot] · [Ballistics][ballistics]

## Notes

The bundled display supplies an installed model and texture, and all four supported gun models have the `muzzle_pos` mount required by the rendering code. Only SPAS-12 has a `muzzle_default` part for the default-muzzle visibility rule to hide; the other three have no such node. [Display][display] · [Display reader][display-reader] · [Attachment rendering][attachment-rendering] · [Default-muzzle rule][default-muzzle] · [AA12 model][aa12-model] · [M1014 model][m1014-model] · [M870 model][m870-model] · [SPAS-12 model][spas_12-model]

The choke does not pass the attachment-ID check for `silencer`, so it does not suppress first-person flash through that check. A flash still requires the normal gun node, timing, and flash data. [Flash conditions][flash]

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
[definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L148-L148
[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/muzzle_choke_sg.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/muzzle_choke_sg.json#L1-L6
[profile]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/muzzle_choke_sg_data.json#L1-L25
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/muzzle_choke_sg_display.json#L1-L9
[aa12-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/aa12_geo.json#L1-L6506
[m1014-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m1014_geo.json#L1-L7533
[m870-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m870_geo.json#L1-L3568
[spas_12-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/spas_12_geo.json#L1-L10036
