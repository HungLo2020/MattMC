# .357 Golden Deagle Long Barrel

The .357 Golden Deagle Long Barrel reduces both camera-recoil components on the Golden Deagle 357. Its imported range, accuracy, and silence settings do not provide those upgrades in the reviewed implementation.

## Obtaining

Craft **one .357 Golden Deagle Long Barrel** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in **Muzzle**. It does not consume a gun or another barrel. [Recipe][recipe] · [Group][index] · [Output loading][loader] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Gold Ingots](GoldIngot.md) | 30 |
| [Iron Ingots](IronIngot.md) | 10 |

The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) covers crafting controls and inventory handling. Crafting creates the attachment item; install it separately through Refit. [Craft transaction][craft]

It is registered as `minecraft:deagle_golden_long_barrel`, stacks to **one**, and also appears in the Creative Menu. The [inventory browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show its catalog entry in Survival without allowing ordinary Survival insertion. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the barrel and hold the Golden Deagle 357 in your **main hand**. Press **Z** by default to open **Refit**, select **Muzzle**, and choose the carried barrel. Installation consumes one attachment. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, Unload, and inventory-space limits. [Default binding][keys] · [Refit opening][input] · [Selection][refit-screen] · [Installation and removal][refit-server]

There is **one Muzzle slot**. The long barrel and the compatible [Phantom S1 Silencer](PhantomS1Silencer.md) are alternatives; their effects cannot stack in that slot. Recoil modifiers from other installed slots can combine with the chosen muzzle attachment. [Gun allowlist][gun-definition] · [Slot storage][slot-storage] · [Modifier collection][collection]

Choose this barrel for its active camera-recoil reduction. Its name and imported profile do not establish extra projectile range, tighter server dispersion, or quieter shots.

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [Golden Deagle 357](GoldenDeagle357.md) |

Only **`deagle_golden`** accepts this exact attachment ID. The ordinary Deagle (`deagle`) does not; sharing a pistol family or Muzzle slot is insufficient. [Both gun definitions][gun-definition] · [Type and exact-ID check][fit] · [Attachment definition][definition]

## Behavior

The barrel contributes these active camera-recoil multipliers. [Attachment profile][profile] · [Profile loading][profile-loader] · [Modifier evaluation][evaluation]

| Camera-recoil component | Multiplier | Effect at the modifier stage |
| --- | ---: | --- |
| Vertical (pitch) | ×0.80 | Reduced |
| Horizontal (yaw) | ×0.80 | Reduced |

With other inputs held fixed, each component is reduced at the modifier stage. These values are not a measured promise of exactly 20% less final camera motion: the gun's recoil curve, aiming/zoom, the crawl-state multiplier when applicable, and other installed modifiers also enter the calculation and sampling. Shot feedback triggers the recoil, which changes the player's view rotation when applied each frame. [Collection][collection] · [Sampling][sampling] · [Shot feedback][feedback] · [Camera motion][camera] · [Frame application][frame]

Camera movement can change where later shots are aimed, but this barrel does not directly reduce the server's bullet-dispersion parameter or increase projectile damage. Those values are calculated separately. [Shot creation][server-shot] · [Dispersion and damage][ballistics]

The other non-commented profile values remain **unused attachment settings** in the checked paths: weight `0.4`, ADS addend `0.04`, hip inaccuracy ×`0.90`, aimed inaccuracy ×`0.85`, effective-range addend `5`, and silence distance addend `-12` with `use_silence_sound: true`. They do not establish a weight penalty, slower aiming, improved accuracy/range, reduced sound radius, or a suppressed firing sound. Both client and server firing still select the gun's ordinary shooting sound. [Imported profile][profile] · [Active profile reader][profile-loader] · [Aim transition][aim] · [Ballistic calculation][ballistics] · [Client sound][feedback] · [Server sound][server-shot] · [Sound selection][sound]

The barrel also **does not suppress the first-person muzzle flash** through the reviewed check: that check requires an installed ID containing `silencer`, and `deagle_golden_long_barrel` does not match. Flash submission remains conditional on first-person rendering, a `muzzle_flash` node, available flash data, and the recent-shot timing window. [Flash conditions][flash]

The bundled barrel has model geometry and a texture reference. The Golden Deagle model has `muzzle_pos`, so the renderer can submit the barrel there when the display and geometry load and the mount path resolves. It also has `muzzle_flash`, but **no `muzzle_default` node**; the default-muzzle visibility rule therefore has no such node to hide on this gun. This does not establish where the flash or barrel appears in a running game. [Barrel display][display] · [Barrel geometry][attachment-geometry] · [Golden Deagle model][gun-model] · [Display loading][display-reader] · [Attachment submission][attachment-render] · [Default-muzzle visibility][default-muzzle]

## Notes

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The recipe, one-gun fit, refit storage, profile-to-camera recoil path, inactive imported fields, audio/flash checks, and model conditions were checked. This was **source review**, not an in-game crafting, refitting, recoil, sound, or visual test; model alignment and actual appearance were not verified. The inspected first-person caller connects the held gun to this renderer. [Held-gun renderer][first-person]

[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/deagle_golden_long_barrel.json
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/deagle_golden_long_barrel.json
[loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L184
[craft]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[creative]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L41
[input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L104
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L78-L112
[refit-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[gun-definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L19-L20
[slot-storage]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[collection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[fit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L118-L118
[profile]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/deagle_golden_long_barrel_data.json
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[evaluation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L519-L537
[sampling]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L405-L433
[feedback]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L127-L160
[camera]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L66
[frame]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L757-L763
[server-shot]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[ballistics]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[aim]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[sound]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L371-L377
[flash]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/deagle_golden_long_barrel_display.json
[attachment-geometry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/attachment/deagle_golden_long_barrel_geo.json
[gun-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/deagle_golden_geo.json
[display-reader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L127
[attachment-render]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L616
[default-muzzle]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1105-L1173
[first-person]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L668-L696
