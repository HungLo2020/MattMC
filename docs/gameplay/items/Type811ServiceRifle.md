# Type 81-1 Service Rifle

The **Type 81-1 Service Rifle** is a 30-round TaCZ rifle using [7.62x39mm Bullet](762x39mmBullet.md). It offers AUTO and SEMI fire. [Definition][definition] · [Fire modes][modes]

## Obtaining

Craft **one Type 81-1 Service Rifle** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Rifle**. Carry the materials in your inventory; one craft-control click makes the batch below. [Recipe][recipe] · [Recipe loading and output count][loader] · [Rifle index][index] · [Table groups][groups]

| Materials per craft | Output |
| --- | --- |
| 28 Iron Ingots · 10 items in `minecraft:logs` | **1 × Type 81-1 Service Rifle** |

The registered item is `minecraft:type_81`, stacks to **1**, and also appears in Creative. The [inventory browser's Survival catalog](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant it to ordinary Survival players. See [workbench use](../blocks/TaCZWorkbenches.md#using-a-workbench) for shared crafting controls and Creative restrictions. [Registration][registration] · [Creative entry][creative]

A freshly crafted gun starts with **30 loaded rounds**, **AUTO** selected, and **no installed attachments**. The plain recipe output has no saved ammunition override, so the active gun reads its base capacity; the Creative entry also starts at that base capacity. [Output construction][loader] · [Loaded rounds and initial mode][loaded] · [Creative stack construction][creative-stack] · [Attachment storage][refit-storage]

The log ingredient is the **`minecraft:logs` item tag**, rather than planks. Its nested groups include burnable logs and the Crimson/Warped stem families. [Log tag][logs]

## Usage

Use [7.62x39mm Bullet](762x39mmBullet.md), registered as `minecraft:762x39`. Other cartridges and Ammo Modifier attachments cannot supply this gun's reloads. [Gun definition][definition] · [Exact ammunition matching][ammo-supply]

Hold the rifle in your **main hand**. With [default TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, **G** changes fire mode, and **Z** opens Refit. [Default keys][keys] · [Active controls][input]

The fresh mode is **AUTO**; G cycles **AUTO → SEMI → AUTO**. With the default mouse binding, hold Shoot for AUTO or click for a SEMI shot. AUTO uses **630 RPM** and a **2-game-tick server trigger cooldown**; SEMI uses **300 RPM** (630 − 330) and a **4-game-tick cooldown**. These are configured timing limits, not measured firing rates. SEMI also raises initial bullet speed from **245 to 265 m/s**, the headshot multiplier from **1.5 to 1.75**, and each damage band by **2**, while reducing spread. [Modes][modes] · [Cycle][cycle] · [RPM adjustment][rpm-adjust] · [Timing and firing][shots] · [Active ballistic adjustments][spread] · [Gun data][gun-data]

A three-round burst definition exists in the bundled data and Java burst table, but **BURST is not a supported or selectable mode for this gun**. That definition does not give Type 81-1 a working burst mode. [Supported modes][modes] · [Mode validation][cycle] · [Unused burst entry][burst]

See [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes) for shared interruption and keyboard-remapping behavior.

Reloading takes **45 game ticks** (2.25 seconds at 20 ticks per second) before ammunition is added. This is the same server reload duration whether empty or partly loaded, including with an accepted extended magazine; the imported empty/tactical animation values do not determine that completion time. Keep holding the gun until it finishes. [Definition][definition] · [Active reload timing][reload-timing] · [Reload completion][reload]

The loaded count is the whole firing supply: there is **no additional chamber round** above the selected capacity. Reloading preserves rounds already loaded. A Survival reload uses only the first matching carried stack, so a small first stack can leave the rifle partly filled; see [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Ammo and capacity][loaded] · [Reload supply][ammo-supply]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [7.62x39mm Bullet](762x39mmBullet.md) |
| Magazine size | 30 |
| Extended magazine sizes | 33, 36, 40 |
| Fire modes | AUTO, SEMI |
| RPM | 630 |
| Damage | 9 |
| Pellets per shot | 1 |
| Bullet speed | 245 m/s |
| Lifetime | 0.75 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

These are **base definition values**; the mode-specific changes are described above and below. RPM is a timing input, not a measured sustained firing rate. Damage is the fallback value; the active shot uses the distance curve below. Bullet speed is the initial configured speed, and lifetime is finite, so the final “infinite” falloff entry does not give the projectile unlimited range. [Definition][definition] · [Shot creation][shots] · [Projectile flight][flight]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 5 |
| Moving | 5.5 |
| Sneaking | 2.5 |
| Prone | 1.5 |
| Aiming down sights | 0.125 |

The table gives **AUTO/base** spread inputs. SEMI subtracts **0.5** from non-aimed rows and **0.05** from the aimed row: **4.5 standing, 5 moving, 2 sneaking, 1 prone, and 0.075 aimed**. [Mode data][gun-data] · [Active adjustment][spread]

These numbers are projectile-spread inputs: lower values produce less random spread, not a hit percentage or a camera-recoil score. The aimed row applies once the aim transition completes. “Prone” is the low-pose case, not evidence of a working Crawl key; see the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Pose selection][pose] · [Aim gate][input] · [Spread application][projectile-spread]

Camera recoil separately uses the bundled pitch/yaw curves, aim/zoom and low-pose scaling, and installed attachment recoil modifiers. That active camera path does not turn the spread table into a recoil measurement. [Recoil calculation][recoil] · [Shot-feedback trigger][recoil-trigger] · [Camera adjustment][camera] · [Render application][recoil-apply]

## Damage Falloff

* 25 blocks: 9
* 55 blocks: 7
* infinite blocks: 4.5

In **AUTO**, the thresholds mean **9 below 25 blocks, 7 from 25 to below 55, and 4.5 at 55 or more**. SEMI adds **2** to each band, giving **11, 9, and 6.5**, respectively. Values are before headshots and target damage handling. Equality moves to the next band; this is stepwise falloff. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Curve and mode data][gun-data] · [Active conversion][spread] · [Distance selection][distance] · [Hit handling][hit]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle.

* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

This is the exact registered attachment list accepted by the gun: both the category and the attachment ID must match. Use the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) to make the items and [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install them. Acceptance alone does not establish every imported attachment effect. [Accepted categories and IDs][definition] · [Acceptance check][acceptance] · [Server installation][install]

Heavy Ammo Extended Mag **I / II / III** gives **33, 36, and 40 rounds**, respectively. Installation adds capacity without adding ammunition; fitting tier I to a fresh rifle leaves **30/33** until reloaded. Use up ammunition above a smaller capacity before downsizing, because [the next shot can discard excess rounds](../mechanics/TaCZFirearms.md#refitting-attachments). [Capacity and loaded count][loaded] · [Attachment storage][refit-storage]

This gun accepts only the listed Muzzle, Extended Mag, and Ammo Modifier items. Scope, Stock, Grip, and Laser attachments are not accepted. [Accepted types and IDs][definition] · [Acceptance check][acceptance]

## Notes

* This item is part of the integrated TaCZ firearms system.
* Shared guides: [TaCZ firearms](../mechanics/TaCZFirearms.md), [TaCZ Workbenches](../blocks/TaCZWorkbenches.md), and [Items](Items.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registration, the dedicated workbench recipe loader, item defaults, ammunition matching, gun input and server handlers, reload completion, active ballistic and camera-recoil consumers, and attachment acceptance were checked. Imported recipe presets, weight, movement, ADS, and per-gun attachment-profile fields are not assumed to produce gameplay effects. This is **source review**, not an in-game crafting, reload, firing-rate, accuracy, recoil, damage, or multiplayer test.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2743
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[creative-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTab.java#L253-L265
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L178
[loaded]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L340
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L16-L82
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L20
[cycle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L333-L348
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[ammo-supply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L297
[shots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L181
[rpm-adjust]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L5-L23
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[pose]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L237
[projectile-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L179
[flight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L101-L134
[acceptance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L144
[camera]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L66
[recoil-trigger]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L142-L154
[recoil-apply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L754-L762
[logs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs.json
[burst]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L6-L43
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L70
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L65
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/type_81.json
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/type_81.json
[gun-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/type_81_data.json
