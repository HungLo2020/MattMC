# HK G3 Battle rifle

The **HK G3 Battle rifle** is a 20-round TaCZ rifle using .308 Winchester ammunition, with SEMI and AUTO fire. [Definition][definition] · [Fire modes][modes]

## Obtaining

Craft **one HK G3 Battle rifle** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Rifle**. Carry the materials in your inventory; one craft-control click makes the batch below. [Recipe][recipe] · [Recipe loading and output count][loader] · [Rifle index][index] · [Table groups][groups]

| Materials per craft | Output |
| --- | --- |
| 9 Gold Ingots · 72 Iron Ingots · 5 Nether Quartz | **1 × HK G3 Battle rifle** |

The registered item is `minecraft:hk_g3`, stacks to **1**, and also appears in Creative. The [inventory browser's Survival catalog](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant it to ordinary Survival players. See [workbench use](../blocks/TaCZWorkbenches.md#using-a-workbench) for the shared crafting controls and Creative restrictions. [Registration][registration] · [Creative entry][creative]

A freshly crafted gun starts with **20 loaded rounds**, **SEMI** selected, and **no installed attachments**. The plain recipe output has no saved ammunition override, so the active gun reads its base capacity; the Creative entry also starts at that base capacity. [Output construction][loader] · [Loaded rounds and initial mode][loaded] · [Creative stack construction][creative-stack] · [Attachment storage][refit-storage]

## Usage

Use [.308 Winchester Bullet](308WinchesterBullet.md), registered as `minecraft:308`. Other cartridges and Ammo Modifier attachments cannot supply this gun's reloads. [Gun definition][definition] · [Exact ammunition matching][ammo-supply]

Hold the gun in your **main hand**. With [default TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, **G** changes fire mode, and **Z** opens Refit. [Default keys][keys] · [Active controls][input]

The fresh mode is **SEMI**; G switches **SEMI → AUTO → SEMI**. With the default mouse binding, click for one SEMI shot or hold Shoot for AUTO. The configured RPM input is **350 in SEMI** and **700 in AUTO**. AUTO also changes damage, bullet speed, headshot multiplier, and spread as described below. See [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes) for cooldown and remapping behavior. [Modes][modes] · [Cycle][cycle] · [RPM adjustment][rpm] · [Mode data][mode-data] · [Active controls][input]

Reloading takes **54 game ticks** (2.7 seconds at 20 ticks per second) before ammunition is added. This is the same server reload duration whether empty or partly loaded, including with an accepted extended magazine; the separate imported empty/tactical animation values do not determine that completion time. Keep holding the gun until it finishes. [Definition][definition] · [Active reload timing][reload-timing] · [Reload completion][reload] · [Use counter][use-counter] · [Completion dispatch][use-completion]

The loaded count is the whole firing supply: there is **no additional chamber round** above the selected capacity. Reloading preserves rounds already loaded. A Survival reload uses only the first matching carried stack, so a small first stack can leave the gun partly filled. Creative reloads supply the missing rounds without requiring or consuming carried ammunition; firing still spends one loaded round per shot. See [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Ammo and capacity][loaded] · [Reload supply][ammo-supply] · [Shot consumption][shots]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.308 Winchester Bullet](308WinchesterBullet.md) |
| Magazine size | 20 |
| Extended magazine sizes | 30, 35, 40 |
| Fire modes | SEMI, AUTO |
| RPM | 350 |
| Damage | 12 |
| Pellets per shot | 1 |
| Bullet speed | 270 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 2 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

These are base definition values, before AUTO adjustments. In AUTO, the initial bullet speed becomes **260 m/s** and the headshot multiplier becomes **1.25**. [Mode data][mode-data] · [Applied adjustments][spread]

RPM is a timing input, not a measured sustained firing rate. Damage is the fallback value; the active shot uses the distance curve below. Bullet speed is the initial configured speed, and lifetime is finite, so the final “infinite” falloff entry does not give the projectile unlimited range. [Definition][definition] · [Timing input][trigger] · [Shot creation][shots] · [Projectile flight][flight]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 5 |
| Moving | 5.5 |
| Sneaking | 3 |
| Prone | 2 |
| Aiming down sights | 0.1 |

The table gives **SEMI** values. In **AUTO**, Standing / Moving / Sneaking / Prone become **5.5 / 6 / 3.5 / 2.5**, and aiming down sights becomes **0.25**. [Mode data][mode-data] · [Applied spread adjustment][spread]

These numbers are projectile-spread inputs: lower values produce less random spread, not a hit percentage or a camera-recoil score. The aimed row applies once the aim transition completes. “Prone” is the low-pose case, not evidence of a working Crawl key; see the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][accuracy-data] · [Pose selection][pose] · [Aim gate][input] · [Spread application][projectile-spread]

## Damage Falloff

* 35 blocks: 12
* 80 blocks: 9
* infinite blocks: 7.5

In **SEMI**, these thresholds mean **12 below 35 blocks, 9 from 35 to below 80, and 7.5 at 80 or more**. **AUTO** subtracts 2 from each band, giving **10 / 7 / 5.5** over those same distances. Equality moves to the next band; this is stepwise falloff, before headshots and target damage handling. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Curve data][gun-data] · [Active curve conversion][spread] · [Distance selection][distance] · [Hit handling][hit]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Grip, Laser, Muzzle, Scope, Stock.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Contender 4x Scope](Contender4xScope.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [Scout 4-10x Scope](Scout410xScope.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md), [Vudu 1-6x Scope](Vudu16xScope.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Stock: [AK-12 Regular Stock](AK12RegularStock.md), [Carbon bone C5 Stock](CarbonboneC5Stock.md), [CMMG RipStock Stock](CMMGRipStockStock.md), [Factory Issued Heavy Stock](FactoryIssuedHeavyStock.md), [Factory Issued light Stock](FactoryIssuedlightStock.md), [Factory Issued Tactical Stock](FactoryIssuedTacticalStock.md), [HK Slim Line Stock](HKSlimLineStock.md), [M4SS Stock](M4SSStock.md), [Magpul CTR stock](MagpulCTRstock.md), [Magpul MOE Stock](MagpulMOEStock.md), [Militech B5 Stock](MilitechB5Stock.md), [SBA3 Stock](SBA3Stock.md)
* Grip: [Hera Arms CQR Grip](HeraArmsCQRGrip.md), [Koch Ranger Heavy Grip](KochRangerHeavyGrip.md), [Nagoma Military Standard Grip](NagomaMilitaryStandardGrip.md), [P-2 Grip](P2Grip.md), [RK-0 Grip](RK0Grip.md), [RK-1 B25U Grip](RK1B25UGrip.md), [RK-6 Grip](RK6Grip.md), [SE-5 Express Grip](SE5ExpressGrip.md), [SI Grip](SIGrip.md), [Talon AFG1 Handstop](TalonAFG1Handstop.md), [Talon SG2 Grip](TalonSG2Grip.md), [TD Grip](TDGrip.md)
* Laser: [laser_peq15](laserpeq15.md), [Lopro Tactical Laser](LoproTacticalLaser.md), [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

This is the exact registered attachment list accepted by the gun: both the category and the attachment ID must match. Use the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) to make the items and [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install them. Acceptance alone does not establish every imported attachment effect. [Accepted categories and IDs][definition] · [Acceptance check][acceptance] · [Server installation][install]

Heavy Ammo Extended Mag **I / II / III** gives **30, 35, and 40 rounds**, respectively. Installation adds capacity without adding ammunition; fitting tier I to a fresh rifle leaves **20/30** until reloaded. Use up ammunition above a smaller capacity before downsizing, because [the next shot can discard excess rounds](../mechanics/TaCZFirearms.md#refitting-attachments). [Capacity and loaded count][loaded] · [Attachment storage][refit-storage]

The G3 accepts the listed Stock attachments and the Contender 4x Scope; those options are not shared by every rifle. Use this gun's list when choosing parts. [Accepted attachments][definition]

## Notes

* This item is part of the integrated TaCZ firearms system.
* Shared guides: [TaCZ firearms](../mechanics/TaCZFirearms.md), [TaCZ Workbenches](../blocks/TaCZWorkbenches.md), and [Items](Items.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registration, the dedicated workbench recipe loader, item defaults, ammunition matching, gun input and server handlers, reload completion, projectile consumers, and attachment acceptance were checked. This is **source review**, not an in-game crafting, reload, accuracy, recoil, damage, or multiplayer test.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2722
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[creative-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTab.java#L253-L265
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L178
[loaded]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L340
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L20
[cycle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L333-L348
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[use-counter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3168-L3206
[use-completion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3270
[ammo-supply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L297
[shots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[trigger]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L128
[rpm]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L5-L21
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[pose]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L237
[projectile-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L179
[flight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L101-L134
[acceptance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L25
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L22
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/hk_g3.json
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/hk_g3.json
[gun-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/hk_g3_data.json
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/hk_g3_data.json#L141-L147
[mode-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/hk_g3_data.json#L67-L78
