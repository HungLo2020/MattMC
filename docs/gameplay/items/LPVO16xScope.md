# LPVO 1-6x Scope

The LPVO 1-6x Scope is a craftable TaCZ optic for 23 registered firearms, including the P90. Its current first-person aiming setting is **6.25×**; the 1-6x name does not mean the player can switch magnification in this source snapshot. [Definition][attachment-definition] · [Compatible guns][gun-definitions] · [Display][display] · [Current selection][first-values]

## Obtaining

Craft **one** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Scope**, using these exact items:

| Material | Count |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 14 |
| [Amethyst Shards](AmethystShard.md) | 4 |
| [Glowstone Dust](GlowstoneDust.md) | 8 |
| [Redstone Dust](RedstoneDust.md) | 6 |

The recipe produces one scope and belongs to the Attachment Table’s Scope group. [Recipe][recipe] · [Group][index] · [Recipe loading][recipe-loader] · [Table groups][workbench]

Keep the ingredients in your inventory and use the table’s Craft button. Survival crafting consumes them; Creative crafting still checks that they are present but does not consume them. See [workbench controls and inventory limits](../blocks/TaCZWorkbenches.md#using-a-workbench) before crafting with a full inventory. [Craft transaction][craft-transaction]

It is registered as `minecraft:scope_lpvo_1_6` and also appears in the Creative inventory. Catalog visibility in the [inventory browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not provide an ordinary Survival acquisition shortcut. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the scope, hold a compatible firearm in your **main hand**, and press **Refit** (Z by default). Select **Scope**, then this attachment from the carried items. Refitting does not require standing at a workbench. Close the screen and hold **Aim** (right mouse button by default) for first-person aiming. [Bindings][keys] · [Held-gun controls][input] · [Refit screen][refit-screen] · [Server handler][refit-server]

Installing spends one carried scope, including in Creative, and replaces any item in that gun’s Scope slot. Leave inventory space for the old attachment or for using **Unload**. For the full-inventory exceptions and the screen’s eight-entry limit, see [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments). [Installation and removal][refit-transaction]

The attachment level of 6 is registry metadata; it is neither a player-level requirement nor the value used for scope magnification. Gun compatibility checks the scope category and accepted ID, while zoom comes from the display file. [Compatibility checks][compatibility] · [Display loader][display-loader]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | 6 |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [Accuracy International AWM](AccuracyInternationalAWM.md), [AKM](AKM.md), [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [Gewehr 43](Gewehr43.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [M1 Carbine](M1Carbine.md), [M107 Sniper Rifle](M107SniperRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M700 Sniper Rifle](M700SniperRifle.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md), [MK14 EBR](MK14EBR.md), [P90 PDW](P90PDW.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md) |
| Stack size | 1 |
| Fully aimed world-view zoom | 6.25× |
| Fully aimed gun-model FOV | 30° |

The fit list is the live gun-definition allowlist, checked by both attachment category and ID. The zoom and gun-model FOV are the **first entries actually selected** from the display settings. Model FOV controls the held gun and optic’s presentation; it is not the world-view FOV. [Definitions][gun-definitions] · [Compatibility][compatibility] · [Registration and stack size][registration] · [Display][display] · [Selected entries][first-values] · [FOV application][scope-loader]

## Behavior

### Aiming and magnification

While aiming in first person, the world-view FOV narrows with aim progress toward the 6.25× setting. The held gun and optic separately transition toward a 30° model FOV. These changes apply through the local player’s main-hand camera path. [Aim transition][aim-transition] · [World and model FOV][scope-loader] · [Camera entry point][fov-entry]

The display also declares a 1.25× setting, but the active accessors always choose the first zoom, model-FOV and view entries. The registered **Zoom** binding (V by default) has no action in the current input handler, so there is no working switch to that lower setting. [Display settings][display] · [Selected entries][first-values] · [Bindings][keys] · [Active controls][input]

### Handling and rendering limits

Its bundled data declares weight 1.3 and an aim-time addend of 0.03. Those fields are not applied by the reviewed aim-transition path, which uses a shared 0.16-second target. Do not use those imported numbers to predict a movement-speed or aim-time penalty. [Attachment data][data] · [Aim timing][aim] · [Aim update][aim-transition]

The selected zoom does feed the gun’s aimed camera-recoil calculation. That is separate from a change to projectile spread or damage. [Camera feedback][camera-recoil] · [Recoil scaling][recoil-scaling] · [Shot creation][shot-spread]

The first-person gun renderer loads the installed scope’s display and geometry, attaches it at the gun’s scope mount, and looks for the first selected scope-view node to align the weapon. Optical geometry has a separate stencil submission path. These are source-traced rendering connections, not an in-game check of reticle alignment, lens appearance, or every compatible gun. [Main-hand rendering][first-person-entry] · [Attachment loading][render-attachment] · [View alignment][render-view] · [Optical submission][render-optics]

**LPVO alignment has a source-visible mismatch:** the selected view asks for `scope_view_2`, but the bundled model contains only `scope_view`. The renderer therefore falls back to the gun’s iron-sight positioning, while the FOV zoom still applies independently. The resulting appearance has not been checked in game. This selected-view mismatch is tracked in [issue #811](https://github.com/HungLo2020/MattMC/issues/811); it is not yet a verified rendering fix. [Display selection][display] · [Model nodes][lpvo-geometry] · [Missing-view check][render-view] · [Positioning fallback][render-fallback] · [Independent FOV path][scope-loader]

## Notes

- This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md)
- Compare [Vudu 1-6x Scope](Vudu16xScope.md), [Scout 4-10x Scope](Scout410xScope.md), and [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md) for cost, fit, and the zoom setting currently used
- [TaCZ Workbenches](../blocks/TaCZWorkbenches.md) and [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The recipe loader, table group, registration, compatible-gun allowlists, refit packet path, active input handler, FOV consumers, and first-person attachment renderer were inspected. This is **source review only**; no in-game crafting, multiplayer, optical-rendering, or aim/recoil test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[attachment-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L161-L173
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L70
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[workbench]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[craft-transaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L32-L111
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[refit-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2202
[refit-transaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[scope-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L86
[display-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L126
[first-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L165-L195
[fov-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L475-L504
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L58
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L104
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L14-L16
[aim-transition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L38-L61
[shot-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L175
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L41
[recoil-scaling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L143
[first-person-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L502-L512
[render-attachment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L615
[render-view]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L729-L741
[render-fallback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L846-L859
[render-optics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L264-L293
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/scope_lpvo_1_6.json#L1-L33
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/scope_lpvo_1_6.json#L1-L7
[display]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/display/attachments/scope_lpvo_1_6_display.json#L1-L35
[data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/scope_lpvo_1_6_data.json#L1-L4
[lpvo-geometry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/geo_models/attachment/scope_lpvo_1_6_geo.json#L1-L3120
