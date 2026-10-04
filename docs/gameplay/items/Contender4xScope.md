# Contender 4x Scope

The Contender 4x Scope is a craftable TaCZ optic accepted by **17 registered firearms**. Its current first-person world-view zoom is **4.25×**, selected from its display settings. [Definition][attachment-definition] · [Compatible guns][gun-definitions] · [Display][display] · [Selected zoom][first-values] Its bundled aiming-view mismatch is described under [Behavior](#behavior).

## Obtaining

Craft **one** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Scope**, with these exact items:

| Material | Count |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 10 |
| [Amethyst Shards](AmethystShard.md) | 2 |

The registered attachment’s bundled recipe produces one scope; its index places it in the Scope group. Keep the materials in your inventory and use the table’s craft control. Survival crafting consumes them; Creative crafting checks that they are present but does not consume them. See [workbench controls and full-inventory limits](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Group][index] · [Recipe loading][recipe-loader] · [Table groups][workbench] · [Craft transaction][craft-transaction]

It is registered as `minecraft:scope_contender` and has a Creative category entry feeding the combined [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits). The catalog can be browsed in Survival, but ordinary Survival insertion requests are rejected by the server’s infinite-materials gate; visibility is not an acquisition shortcut. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the scope, hold a compatible firearm in your **main hand**, and press **Refit** (Z by default). Select **Scope**, choose this attachment from the carried items, then close the screen. Installation does not require standing at a workbench. Hold **Aim** (right mouse button by default) for first-person aiming. [Bindings][keys] · [Held-gun controls][input] · [Refit screen][refit-screen] · [Server handler][refit-server]

Installing consumes one carried scope, including in Creative, and replaces the gun’s current Scope attachment. Leave inventory space for the old attachment or for **Unload**. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for the eight-entry display limit and the different full-inventory outcomes when replacing or unloading. [Installation and removal][refit-transaction]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | Not level-based |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [Accuracy International AWM](AccuracyInternationalAWM.md), [Deagle 50](Deagle50.md), [Golden Deagle 357](GoldenDeagle357.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-MP5A5](HKMP5A5.md), [M107 Sniper Rifle](M107SniperRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M700 Sniper Rifle](M700SniperRifle.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md), [MK14 EBR](MK14EBR.md), [P90 PDW](P90PDW.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Taurus "Raging Hunter" Hand Cannon](TaurusRagingHunterHandCannon.md), [UMP45 SMG](UMP45SMG.md), [Vector SMG](VectorSMG.md) |
| Stack size | 1 |
| Fully aimed world-view zoom | 4.25× |
| Fully aimed gun-model FOV | 35° |
| Selected aiming-view node | `scope_view_2` |

The fit list is checked against each gun’s accepted attachment IDs **and** its supported Scope category. Model FOV affects the held gun and optic’s presentation; it is separate from world-view FOV. The first display entries select view 2, named `scope_view_2`, and a 35° gun-model FOV. [Compatibility][compatibility] · [Gun definitions][gun-definitions] · [Registration and stack size][registration] · [Display][display] · [Display defaults][display-loader] · [Selected entries][first-values]

## Behavior

### Aiming and magnification

While aiming in first person, the local player’s main-hand camera narrows the world-view FOV toward the **4.25×** setting as the aim transition progresses. The held gun and optic separately transition toward a **35°** model FOV. [Aim transition][aim] · [World and model FOV][scope-loader] · [Camera entry point][fov-entry]

The display supplies one zoom setting. The registered **Zoom** binding (V by default) has no action in the current input handler. See the shared [controls and inactive bindings](../mechanics/TaCZFirearms.md#controls). [Display][display] · [Selected entries][first-values] · [Bindings][keys] · [Active handler][input]

### Model alignment and rendering

**This optic has a source-visible aiming-view mismatch:** the display selects `scope_view_2`, but its bundled model defines only `scope_view`. The selected node is therefore missing, and the renderer falls back to the gun’s **iron-sight positioning**. World-view zoom and gun-model FOV still use the values above through their separate camera path. This establishes a source mismatch, not an observed in-game appearance or a verified fix. Tracked in [issue #811](https://github.com/HungLo2020/MattMC/issues/811). [Display][display] · [Complete model geometry][geometry] · [Model parser][geometry-parser] · [Selected-view lookup][render-view] · [Positioning fallback][render-fallback] · [Independent FOV path][scope-loader]

The first-person renderer loads the installed scope’s display and geometry, mounts the attachment at the gun’s scope mount, and submits its optical geometry through a stencil path. These are source-traced rendering connections, not a runtime check of lens appearance or optical performance. [Main-hand rendering][first-person-entry] · [Attachment loading and mounting][render-attachment] · [Optical submission][render-optics]

### Handling limits

The imported profile declares weight 1.6 and an aim-time addend of 0.05. The reviewed aiming path uses the shared **0.16-second aim target**, without applying this attachment’s addend; these imported values do not establish a working movement-speed or aim-time penalty. [Attachment data][data] · [Aim timing and update][aim] The profile’s `aim_inaccuracy` multiplier of **0.85** is also absent from the active shot-spread calculation, which uses the gun, fire mode and aiming/pose state. Do not treat it as a working 15% spread reduction. [Shot creation][shot-spread] · [Inaccuracy calculation][inaccuracy]

The selected zoom does feed the gun’s aimed camera-recoil calculation. Camera feedback is separate from projectile spread or damage. [Camera feedback][camera-recoil] · [Recoil scaling][recoil-scaling] · [Shot creation][shot-spread]

## Notes

- This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md)
- [TaCZ Workbenches](../blocks/TaCZWorkbenches.md) and [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registration, exact recipe and group, gun allowlists, refitting, active controls, selected display entries, camera consumers, and this optic’s model-node path were inspected. This is **source review only**; no in-game crafting, multiplayer, optical-rendering, alignment or aim/recoil test was run. The findings describe the bundled snapshot; changed code or resources may alter them.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[attachment-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L161-L173
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L179
[workbench]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[craft-transaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L32-L111
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[refit-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2202
[refit-transaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[scope-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L86
[display-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L126
[first-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L179-L195
[fov-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L475-L504
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L58
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L104
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[shot-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L175
[inaccuracy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L49
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L41
[recoil-scaling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L143
[first-person-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L502-L512
[render-attachment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L615
[render-view]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L729-L741
[render-fallback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L846-L859
[render-optics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L264-L293
[geometry-parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L757-L843
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/scope_contender.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/scope_contender.json#L1-L7
[display]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/display/attachments/scope_contender_display.json#L1-L23
[data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/scope_contender_data.json#L1-L7
[geometry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/geo_models/attachment/scope_contender_geo.json#L1-L2089
