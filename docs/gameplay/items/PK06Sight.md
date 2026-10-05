# PK06 Sight

## Obtaining

Craft **one PK06 Sight** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Scope**, from **4 Iron Ingots and 1 Redstone Dust**. Carry the materials and follow the [workbench controls](../blocks/TaCZWorkbenches.md#using-a-workbench). The recipe's output count defaults to one, and its index places it in the Scope group accepted by that table. [Recipe][recipe] · [Group][index] · [Recipe loading][recipe-loading] · [Table groups][workbench-groups]

It is also available from the Creative Menu as `minecraft:sight_pk06_pistol`. Its maximum stack size is **1**. [Definition][registration] · [Item registration][registered-items] · [Creative entry][creative]

## Usage

Hold a compatible gun in your **main hand**, carry this sight, and press **Refit** (**Z** by default). Select **Scope**, then choose the carried sight. Only the guns in the Properties table accept this exact attachment; sharing the Scope category is insufficient. This fit list includes P90 and Timeless .50; it does not cover every pistol. [Controls][keys] · [Held-gun input][input] · [Refit screen][refit-screen] · [Accepted guns][fit] · [Compatibility check][fit-check]

A gun has **one Scope slot**. Installing this sight consumes one carried item and replaces the existing Scope attachment; it does not add a second optic. Keep inventory space for the returned attachment, and see [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement and unloading behavior. [Slot storage][storage] · [Server installation][refit-server]

After closing refit, hold **Aim** (**right mouse button** by default) to aim; release it to stop. See the [shared controls](../mechanics/TaCZFirearms.md#controls) for remapping and current control limits. [Defaults][keys] · [Aim input][input]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Scope |
| Attachment level | Not level-based |
| Compatible guns | [B93R](B93R.md), [CZ 75](CZ75.md), [Glock 17](Glock17.md), [M9A4](M9A4.md), [MK23 Offensive Pistol](MK23OffensivePistol.md), [P320](P320.md), [P90 PDW](P90PDW.md), [Timeless .50 Z-Type](Timeless50ZType.md) |

## Behavior

With the gun in the main hand and the local camera in first person, the world view transitions toward **2× magnification** as aiming progresses. The held gun and sight separately transition toward a **40° model FOV**; that number is not the world-view angle. The world angle depends on the base FOV and aim progress. These optical values come from the installed sight's display file. [Display][display] · [FOV calculation][loader] · [First-person camera use][fov]

The current loader selects the first zoom and model-FOV values. This display omits a view list, so it selects **view 1**, named `scope_view`, which exists in the bundled sight model. The renderer combines that view with the gun's `scope_pos` mount for aimed model positioning. Every gun in the fit list has the checked mount. If required display, model, or positioning data cannot be loaded, the positioning path falls back to the gun's `iron_view`; the optical FOV path is separate. [Selection][selection] · [Sight view node][view-node] · [Positioning][view-position] · [Fallback][aim-fallback]

Installing a Scope attachment also selects the gun model's mounted-sight visibility rules. This sight leaves `show_mount` enabled by default and requests no adapter. Those rules apply to named model parts where present; they do not establish how every gun will look in play. [Display defaults][loader] · [Model visibility][model-visibility]

Selected zoom also feeds the common camera-recoil calculation, together with aim progress and the gun's recoil data. This is distinct from a separate attachment recoil modifier. The imported weight, aim-time, and aimed-accuracy fields are not active handling bonuses in this snapshot, and this sight's data contains no recoil modifier consumed by the checked attachment parser. [Camera recoil][camera-recoil] · [Zoom contribution][recoil-zoom] · [Imported data][imported-data] · [Attachment parser][attachment-parser]

## Notes

* This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md).
* **Zoom (V)** is listed in the bindings, but the active controls do not implement zoom or view cycling. This sight uses the selected values above. [Bindings][keys] · [Active input][input] · [First-value selection][selection]
* Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. Model and mount presence are source checks, not an in-game crafting, refitting, alignment, lens, reticle, stencil, or camera test.

Checked gun mount sources: [B93R][mount-b93r] · [CZ 75][mount-cz75] · [Glock 17][mount-glock_17] · [MK23][mount-hk_mk23] · [M9A4][mount-m9a4] · [P320][mount-p320] · [P90][mount-p90] · [Timeless .50][mount-timeless50].

[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/sight_pk06_pistol.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/sight_pk06_pistol.json#L1-L8
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[workbench-groups]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L178
[registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L188-L188
[registered-items]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1661-L1670
[fit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L15-L69
[fit-check]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L58
[input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L105
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L52-L109
[refit-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[storage]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L12-L81
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/sight_pk06_pistol_display.json#L1-L19
[loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L126
[selection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L128-L194
[fov]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L475-L504
[view-position]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L729-L742
[aim-fallback]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L846-L870
[model-visibility]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1100-L1148
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L65
[recoil-zoom]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[imported-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/sight_pk06_pistol_data.json#L1-L9
[attachment-parser]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L319
[view-node]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/attachment/sight_pk06_pistol_geo.json#L598-L600
[mount-b93r]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/b93r_geo.json#L1240-L1244
[mount-cz75]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/cz75_geo.json#L5974-L5978
[mount-glock_17]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/glock_17_geo.json#L3669-L3673
[mount-hk_mk23]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/hk_mk23_geo.json#L5102-L5106
[mount-m9a4]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m9a4_geo.json#L3255-L3259
[mount-p320]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/p320_geo.json#L3179-L3183
[mount-p90]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/p90_geo.json#L3472-L3476
[mount-timeless50]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/timeless50_geo.json#L3205-L3209
