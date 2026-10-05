# Timeless .50 Cal Brake

Timeless .50 Cal Brake is a dedicated muzzle attachment for Timeless .50 Z-Type. Its active recoil contribution reduces the vertical component more strongly than the horizontal component.

## Obtaining

Craft **one Timeless .50 Cal Brake** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using **15 [Iron Ingots](IronIngot.md)** and **5 [Redstone Dust](RedstoneDust.md)**. No existing muzzle attachment is an ingredient. The custom recipe loader supplies one output and takes its group from this item's Muzzle index entry. [Recipe][recipe] · [Group][index] · [Recipe loader][loader] · [Table groups][groups]

Carry the ingredients in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the crafting controls. Crafting gives you the separate attachment item; it does not install it on a gun. [Craft transaction][craft]

It is registered as `minecraft:muzzle_brake_timeless50`, stacks to **one**, and is also available through the Creative Menu. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can display it in Survival without granting ordinary Survival item insertion; use the table recipe to obtain it there. [Registration][registration] · [Creative entries][creative]

## Usage

With the brake in your inventory, hold a supported gun in your **main hand**, press **Z** for Refit by default, then choose **Muzzle** and the carried attachment. The [firearms refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) covers replacement, removal, and the different inventory-space limits for each. [Default key][keys] · [Opening Refit][refit-input] · [Selection][refit-screen] · [Server transaction][refit-server]

There is **one Muzzle slot per gun**. The Timeless brake occupies that slot by itself; another muzzle attachment cannot be added alongside it through normal refitting. Recoil modifiers from other attachment slots can still combine with it. [Slot storage][slot-storage] · [Modifier collection][collection]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [Timeless .50 Z-Type](Timeless50ZType.md) |

The fit list contains **one gun**, registered as `timeless50`. The .50-caliber name is not a compatibility rule: other .50-caliber firearms do not accept this brake merely because they share an ammunition size. [Attachment definition][definition] · [Gun definitions][gun-definitions] · [Fit check][fit]

## Behavior

The brake contributes **pitch ×0.35** and **yaw ×0.75**. Both reduce camera-recoil components at the modifier stage, with the larger reduction applied to pitch. It does not remove horizontal recoil. [Attachment profile][profile] · [Active profile reader][profile-loader] · [Evaluation][evaluation]

These are **modifier-stage contributions with other inputs held fixed**, not measured ratios of final camera travel. The gun's recoil curve, aiming/zoom, crawling, and modifiers from other installed slots also enter the calculation before sampling. The sampled result moves the player's view. [Calculation][collection] · [Evaluation][evaluation] · [Sampling][sampling] · [Camera movement][camera]

The profile also contains ADS addend `0.03`, inaccuracy multiplier `0.60`, and sneak-inaccuracy multiplier `0.50`. These are **unused imported fields**, not an implemented aim-speed penalty or accuracy bonus. Projectile dispersion and damage are calculated separately without this brake's imported modifiers. Reduced camera recoil may make subsequent aiming easier, but it does not directly tighten the server's projectile spread. [Profile][profile] · [Active reader][profile-loader] · [Aim transition][aim-transition] · [Shot creation][server-shot] · [Ballistics][ballistics]

## Notes

Its bundled display names a model and texture. The Timeless gun model provides both the `muzzle_pos` attachment mount and a `muzzle_default` part; with the display's default `show_muzzle` value of false, the reviewed rendering path can submit the brake and hide that default part. These are source conditions, not an in-game appearance or alignment check. [Display][display] · [Display reader][display-reader] · [Timeless model][timeless50-model] · [Attachment rendering][attachment-rendering] · [Default-muzzle rule][default-muzzle]

The brake's ID does not contain `silencer`, so it does not trigger the first-person flash-suppression check. Other flash requirements still apply. [Flash conditions][flash]

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
[definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L146-L146
[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/muzzle_brake_timeless50.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/muzzle_brake_timeless50.json#L1-L6
[profile]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/muzzle_brake_timeless50_data.json#L1-L19
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/muzzle_brake_timeless50_display.json#L1-L9
[timeless50-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/timeless50_geo.json#L1-L7278
