# sight_p90

sight_p90 is a craftable TaCZ optic for the **P90 PDW**, with a selected world-view zoom of **1.35×**. It occupies the gun’s Scope slot. [Definition][definition] · [Accepted guns][fits] · [Display][display] · [Selected values][selected-values]

## Obtaining

Craft **one** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Scope**, using these exact materials:

| Material | Count |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 6 |
| [Redstone Dust](RedstoneDust.md) | 1 |
| [Glass](Glass.md) | 1 |

The bundled recipe produces **1** `minecraft:sight_p90`. Its index assigns it to the **Scope** group. Carry the ingredients in your inventory and use the table’s craft control. Survival crafting consumes them; Creative crafting still checks for them but leaves them unconsumed. See [workbench controls and inventory limits](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Group][index] · [Recipe loading][recipe-loader] · [Table groups][workbench] · [Craft transaction][craft-transaction]

The item also appears in the Creative category catalog used by the [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits). Browsing the catalog in Survival does not permit item insertion: those requests require the server’s infinite-materials ability, normally supplied by Creative mode. [Creative entries][creative] · [Browser catalog][catalog] · [Packet gate][protocol-gate] · [Gated packet][packet-registration]

## Usage

Carry the optic, hold one of the compatible guns below in your **main hand**, and press **Refit** (Z by default). Select **Scope**, choose the optic from your carried items, then close the screen. Hold **Aim** (right mouse button by default) for first-person aiming. No workbench is needed to refit. [Bindings][keys] · [Held-gun controls][input] · [Refit screen][refit-screen] · [Server installation][refit-server]

The gun has **one Scope slot**. Installing this item consumes one carried optic, including in Creative, and replaces the optic already in that slot. Leave inventory space for the old optic or for **Unload**. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for selection limits and full-inventory behavior. [Slot storage][storage] · [Installation and removal][refit-server]

The imported P90 data names this optic as a built-in attachment, but the current gun recipe creates a bare gun stack and does not install that declaration. Obtain the optic separately and use refit to install it. [Imported declaration][gun-data] · [Gun recipe][gun-recipe] · [Recipe output][recipe-loader] · [Installed attachment storage][storage]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | Not level-based |
| Compatible guns | [P90 PDW](P90PDW.md) |

Compatibility requires both the gun’s Scope category and its exact accepted attachment ID. The optic’s maximum stack size is **1**. [Fit checks][compatibility] · [Accepted guns][fits] · [Registration][registration]

The display marks this item as a sight, while its registered attachment category is **Scope**. It therefore uses the same Scope slot and optical camera path as the other scopes. [Definition][definition] · [Display][display] · [Optical selection][scope-loader]

## Behavior

When fully aimed in first person, the local player’s main-hand gun uses **1.35× world-view zoom** and a separate **40° gun-model FOV**. World magnification increases with the aim transition; the held gun and optic’s model FOV transitions independently. The model FOV is not the camera’s world-view angle. [Display][display] · [Selected values][selected-values] · [Aim transition][aim] · [World and model FOV][scope-loader] · [Camera entry point][fov-entry]

The display supplies one zoom value and defaults to view 1, named `scope_view`. **That node exists in the bundled optic model**, and every gun in the fit list has the required `scope_pos` mount. The renderer combines those nodes for aiming placement. If the display, geometry, selected view or gun mount cannot be loaded, positioning falls back to the gun’s iron-sight view; the separate optical FOV path can still apply when the scope display is available. Node presence alone does not verify visible alignment, reticle, lens or successful rendering. [Display loading][scope-loader] · [Scope node][scope-node] · [P90 mount][mount-p90] · [Aiming placement][scope-position] · [Positioning fallback][position-fallback]

The registered **Zoom** binding (V by default) has no active zoom action in the reviewed implementation. The loader selects the first zoom and view entries; this item does not provide a working zoom/view cycle. See [controls and inactive bindings](../mechanics/TaCZFirearms.md#controls). [Bindings][keys] · [Active controls][input] · [Selected entries][selected-values]

Selected zoom also feeds the common aimed camera-recoil calculation, which depends on aim progress and the gun’s recoil data. This is camera feedback, separate from any imported attachment handling fields. [Camera recoil][camera-recoil] · [Zoom-dependent recoil calculation][recoil-zoom]

## Notes

- The imported weight and aim-time fields do not establish active movement-speed, aim-time or accuracy effects. The attachment-data reader only uses recoil fields, which this profile lacks; its selected optical zoom still affects the separate camera-recoil path described above. [Imported data][attachment-data] · [Attachment-data reader][attachment-parser] · [Aim update][aim]
- This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md)
- Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. Registration, recipe, fit list, controls, optical values, camera consumers and bundled model nodes were checked. No in-game crafting, refitting, multiplayer, alignment, reticle, lens or recoil test was run. Changed code or resources may alter these findings.

[registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[catalog]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[protocol-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[packet-registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L114-L118
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L179
[workbench]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L178
[craft-transaction]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[compatibility]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L109
[refit-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[storage]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L24-L81
[keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L58
[input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L105
[aim]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L62
[scope-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L126
[selected-values]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L179-L195
[fov-entry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L475-L504
[scope-position]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L729-L742
[position-fallback]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L846-L870
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[recoil-zoom]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[attachment-parser]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L319
[definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L187-L187
[fits]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L55-L55
[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/sight_p90.json#L1-L27
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/sight_p90.json#L1-L12
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/sight_p90_display.json#L1-L25
[attachment-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/sight_p90_data.json#L1-L6
[scope-node]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/attachment/sight_p90_geo.json#L304-L310
[mount-p90]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/p90_geo.json#L3472-L3476
[gun-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/guns/p90_data.json#L1-L185
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/gun/p90.json#L1-L33
