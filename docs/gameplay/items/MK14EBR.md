# MK14 EBR

The **MK14 EBR** is a 10-round TaCZ rifle using [.308 Winchester Bullet](308WinchesterBullet.md). It offers SEMI and AUTO. [Definition][definition] · [Fire modes][modes]

## Obtaining

Craft **one MK14 EBR** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Rifle**. Carry the materials in your inventory; one craft-control click makes the batch below. [Recipe][recipe] · [Recipe loading and output count][loader] · [Rifle index][index] · [Table groups][groups]

| Materials per craft | Output |
| --- | --- |
| 84 Iron Ingots · 20 Gold Ingots · 4 Diamonds · 2 Blaze Rods | **1 × MK14 EBR** |

The registered item is `minecraft:mk14`, stacks to **1**, and also appears in Creative. The [inventory browser's Survival catalog](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant it to ordinary Survival players. See [workbench use](../blocks/TaCZWorkbenches.md#using-a-workbench) for shared crafting controls and Creative restrictions. [Registration][registration] · [Creative entry][creative]

A freshly crafted gun starts with **10 loaded rounds**, **SEMI** selected, and **no installed attachments**. The plain recipe output has no saved ammunition override, so the active gun reads its base capacity; the Creative entry also starts at that base capacity. [Output construction][loader] · [Loaded rounds and initial mode][loaded] · [Creative stack construction][creative-stack] · [Attachment storage][refit-storage]

## Usage

Use [.308 Winchester Bullet](308WinchesterBullet.md), registered as `minecraft:308`. Other cartridges and Ammo Modifier attachments cannot supply this gun's reloads. [Gun definition][definition] · [Exact ammunition matching][ammo-supply]

Hold the rifle in your **main hand**. With [default TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, **G** changes fire mode, and **Z** opens Refit. [Default keys][keys] · [Active controls][input]

The fresh mode is **SEMI**; G cycles **SEMI → AUTO → SEMI**. With the default mouse binding, click for one SEMI shot or hold Shoot for AUTO. The active RPM input is **300 in SEMI** and **680 in AUTO** (300 + 380); the server converts these to **4- and 2-game-tick trigger cooldowns**, respectively. These are configured timing limits, not measured firing rates. AUTO also changes initial bullet speed from **310 to 280 m/s** and the headshot multiplier from **1.75 to 1.5**, and lowers the damage bands below. [Modes][modes] · [Cycle][cycle] · [RPM adjustment][rpm-adjust] · [Timing and firing][shots] · [Active ballistic adjustments][spread] · [Gun data][gun-data]

See [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes) for shared interruption and keyboard-remapping behavior.

Reloading takes **39 game ticks** (1.95 seconds at 20 ticks per second) before ammunition is added. This is the same server reload duration whether empty or partly loaded, including with an accepted extended magazine; the imported empty/tactical animation values do not determine that completion time. Keep holding the gun until it finishes. [Definition][definition] · [Active reload timing][reload-timing] · [Reload completion][reload]

The loaded count is the whole firing supply: there is **no additional chamber round** above the selected capacity. Reloading preserves rounds already loaded. A Survival reload uses only the first matching carried stack, so a small first stack can leave the rifle partly filled; see [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Ammo and capacity][loaded] · [Reload supply][ammo-supply]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.308 Winchester Bullet](308WinchesterBullet.md) |
| Magazine size | 10 |
| Extended magazine sizes | 15, 18, 20 |
| Fire modes | SEMI, AUTO |
| RPM | 300 |
| Damage | 16 |
| Pellets per shot | 1 |
| Bullet speed | 310 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 3 |
| Headshot multiplier | 1.75 |
| Knockback | 0 |

These are **base definition values**; the mode-specific changes are described above and below. RPM is a timing input, not a measured sustained firing rate. Damage is the fallback value; the active shot uses the distance curve below. Bullet speed is the initial configured speed, and lifetime is finite, so the final “infinite” falloff entry does not give the projectile unlimited range. [Definition][definition] · [Shot creation][shots] · [Projectile flight][flight]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 5.5 |
| Moving | 6 |
| Sneaking | 3 |
| Prone | 2 |
| Aiming down sights | 0.05 |

The table gives **SEMI/base** spread inputs. AUTO adds **0.5** to each non-aimed row and **0.2** to the aimed row: **6 standing, 6.5 moving, 3.5 sneaking, 2.5 prone, and 0.25 aimed**. [Mode data][gun-data] · [Active adjustment][spread]

These numbers are projectile-spread inputs: lower values produce less random spread, not a hit percentage or a camera-recoil score. The aimed row applies once the aim transition completes. “Prone” is the low-pose case, not evidence of a working Crawl key; see the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Pose selection][pose] · [Aim gate][input] · [Spread application][projectile-spread]

Camera recoil separately uses the bundled pitch/yaw curves, aim/zoom and low-pose scaling, and installed attachment recoil modifiers. That active camera path does not turn the spread table into a recoil measurement. [Recoil calculation][recoil] · [Shot-feedback trigger][recoil-trigger] · [Camera adjustment][camera] · [Render application][recoil-apply]

## Damage Falloff

* 60 blocks: 16
* 120 blocks: 13.5
* infinite blocks: 10.5

In **SEMI**, the thresholds mean **16 below 60 blocks, 13.5 from 60 to below 120, and 10.5 at 120 or more**. AUTO subtracts **5** from each band, giving **11, 8.5, and 5.5**, respectively. Values are before headshots and target damage handling. Equality moves to the next band; this is stepwise falloff. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Curve and mode data][gun-data] · [Active conversion][spread] · [Distance selection][distance] · [Hit handling][hit]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Grip, Laser, Muzzle, Scope, Stock.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Contender 4x Scope](Contender4xScope.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [Scout 4-10x Scope](Scout410xScope.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md), [Vudu 1-6x Scope](Vudu16xScope.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Stock: [AK-12 Regular Stock](AK12RegularStock.md), [Carbon bone C5 Stock](CarbonboneC5Stock.md), [CMMG RipStock Stock](CMMGRipStockStock.md), [HK Slim Line Stock](HKSlimLineStock.md), [M4SS Stock](M4SSStock.md), [Magpul CTR stock](MagpulCTRstock.md), [Magpul MOE Stock](MagpulMOEStock.md), [Militech B5 Stock](MilitechB5Stock.md), [SBA3 Stock](SBA3Stock.md)
* Grip: [Hera Arms CQR Grip](HeraArmsCQRGrip.md), [Koch Ranger Heavy Grip](KochRangerHeavyGrip.md), [Nagoma Military Standard Grip](NagomaMilitaryStandardGrip.md), [P-2 Grip](P2Grip.md), [RK-0 Grip](RK0Grip.md), [RK-1 B25U Grip](RK1B25UGrip.md), [RK-6 Grip](RK6Grip.md), [SE-5 Express Grip](SE5ExpressGrip.md), [SI Grip](SIGrip.md), [Talon AFG1 Handstop](TalonAFG1Handstop.md), [Talon SG2 Grip](TalonSG2Grip.md), [TD Grip](TDGrip.md)
* Laser: [laser_peq15](laserpeq15.md), [Lopro Tactical Laser](LoproTacticalLaser.md), [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

This is the exact registered attachment list accepted by the gun: both the category and the attachment ID must match. Use the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) to make the items and [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install them. Acceptance alone does not establish every imported attachment effect. [Accepted categories and IDs][definition] · [Acceptance check][acceptance] · [Server installation][install]

Heavy Ammo Extended Mag **I / II / III** gives **15, 18, and 20 rounds**, respectively. Installation adds capacity without adding ammunition; fitting tier I to a fresh rifle leaves **10/15** until reloaded. Use up ammunition above a smaller capacity before downsizing, because [the next shot can discard excess rounds](../mechanics/TaCZFirearms.md#refitting-attachments). [Capacity and loaded count][loaded] · [Attachment storage][refit-storage]

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
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L53
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L46
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/mk14.json
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/mk14.json
[gun-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/mk14_data.json
