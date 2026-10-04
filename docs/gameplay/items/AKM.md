# AKM

The **AKM** is a 30-round TaCZ rifle using 7.62x39mm ammunition, with AUTO and SEMI fire. [Definition][definition] · [Fire modes][modes]

## Obtaining

Craft **one AKM** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Rifle**. Carry the materials in your inventory; one craft-control click makes the batch below. [Recipe][recipe] · [Recipe loading and output count][loader] · [Rifle index][index] · [Table groups][groups]

| Materials per craft | Output |
| --- | --- |
| 38 Iron Ingots · 6 Lapis Lazuli · 10 logs | **1 × AKM** |

The logs are a `minecraft:logs` item-tag ingredient. You can combine matching kinds; Oak Logs and Stripped Oak Wood are examples. Planks do not satisfy this ingredient. [Tag matching][tag-match] · [Logs tag][logs] · [Oak members][oak-logs]

The registered item is `minecraft:ak47`, stacks to **1**, and also appears in Creative. The [inventory browser's Survival catalog](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant it to ordinary Survival players. See [workbench use](../blocks/TaCZWorkbenches.md#using-a-workbench) for the shared crafting controls and Creative restrictions. [Registration][registration] · [Creative entry][creative]

A freshly crafted gun starts with **30 loaded rounds**, **AUTO** selected, and **no installed attachments**. The plain recipe output has no saved ammunition override, so the active gun reads its base capacity; the Creative entry also starts at that base capacity. [Output construction][loader] · [Loaded rounds and initial mode][loaded] · [Creative stack construction][creative-stack] · [Attachment storage][refit-storage]

## Usage

Use [7.62x39mm Bullet](762x39mmBullet.md), registered as `minecraft:762x39`. Other cartridges and Ammo Modifier attachments cannot supply this gun's reloads. [Gun definition][definition] · [Exact ammunition matching][ammo-supply]

Hold the rifle in your **main hand**. With [default TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, **G** changes fire mode, and **Z** opens Refit. [Default keys][keys] · [Active controls][input]

The fresh mode is **AUTO**; G switches **AUTO → SEMI → AUTO**. With the default mouse binding, hold Shoot for AUTO or click for one SEMI shot. See [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes) for cooldown and remapping behavior. [Modes][modes] · [Cycle][cycle] · [Active controls][input]

Reloading takes **45 game ticks** (2.25 seconds at 20 ticks per second) before ammunition is added. This is the same server reload duration whether empty or partly loaded, including with an accepted extended magazine; the separate imported empty/tactical animation values do not determine that completion time. Keep holding the gun until it finishes. [Definition][definition] · [Active reload timing][reload-timing] · [Reload completion][reload]

The loaded count is the whole firing supply: there is **no additional chamber round** above the selected capacity. Reloading preserves rounds already loaded. A Survival reload uses only the first matching carried stack, so a small first stack can leave the rifle partly filled; see [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Ammo and capacity][loaded] · [Reload supply][ammo-supply]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [7.62x39mm Bullet](762x39mmBullet.md) |
| Magazine size | 30 |
| Extended magazine sizes | 34, 37, 40 |
| Fire modes | AUTO, SEMI |
| RPM | 600 |
| Damage | 9 |
| Pellets per shot | 1 |
| Bullet speed | 250 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

These are base definition values. RPM is a timing input, not a measured sustained firing rate. Damage is the fallback value; the active shot uses the distance curve below. Bullet speed is the initial configured speed, and lifetime is finite, so the final “infinite” falloff entry does not give the projectile unlimited range. [Definition][definition] · [Shot creation][shots] · [Projectile flight][flight]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 4.5 |
| Moving | 5 |
| Sneaking | 2.5 |
| Prone | 1.5 |
| Aiming down sights | 0.15 |

These numbers are projectile-spread inputs: lower values produce less random spread, not a hit percentage or a camera-recoil score. The aimed row applies once the aim transition completes. “Prone” is the low-pose case, not evidence of a working Crawl key; see the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][accuracy-data] · [Pose selection][pose] · [Aim gate][input] · [Spread application][projectile-spread]

## Damage Falloff

* 30 blocks: 9
* 60 blocks: 7.5
* infinite blocks: 6

The thresholds mean **9 below 30 blocks, 7.5 from 30 to below 60, and 6 at 60 or more**, before headshots and target damage handling. Equality moves to the next band; this is stepwise falloff. This gun has no fire-mode damage adjustment in its bundled data, so these bands apply in all its supported modes. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Curve data][gun-data] · [Active curve conversion][spread] · [Distance selection][distance] · [Hit handling][hit]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle, Scope, Stock.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [Scout 4-10x Scope](Scout410xScope.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md), [Vudu 1-6x Scope](Vudu16xScope.md)
* Muzzle: [6H3 Bayonet](6H3Bayonet.md), [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Stock: [AK-12 Regular Stock](AK12RegularStock.md), [Carbon bone C5 Stock](CarbonboneC5Stock.md), [CMMG RipStock Stock](CMMGRipStockStock.md), [Factory Issued Heavy Stock](FactoryIssuedHeavyStock.md), [Factory Issued light Stock](FactoryIssuedlightStock.md), [Factory Issued Tactical Stock](FactoryIssuedTacticalStock.md), [HK Slim Line Stock](HKSlimLineStock.md), [M4SS Stock](M4SSStock.md), [Magpul CTR stock](MagpulCTRstock.md), [Magpul MOE Stock](MagpulMOEStock.md), [Militech B5 Stock](MilitechB5Stock.md), [SBA3 Stock](SBA3Stock.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

This is the exact registered attachment list accepted by the gun: both the category and the attachment ID must match. Use the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) to make the items and [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install them. Acceptance alone does not establish every imported attachment effect. [Accepted categories and IDs][definition] · [Acceptance check][acceptance] · [Server installation][install]

Heavy Ammo Extended Mag **I / II / III** gives **34, 37, and 40 rounds**, respectively. Installation adds capacity without adding ammunition; fitting tier I to a fresh rifle leaves **30/34** until reloaded. Use up ammunition above a smaller capacity before downsizing, because [the next shot can discard excess rounds](../mechanics/TaCZFirearms.md#refitting-attachments). [Capacity and loaded count][loaded] · [Attachment storage][refit-storage]

AKM has no Grip or Laser slot. Its Muzzle list accepts the 6H3 Bayonet; an M9 Bayonet is not a substitute. [Accepted attachments][definition]

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
[ammo-supply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L297
[shots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L181
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L77
[pose]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L237
[projectile-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L179
[flight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L101-L134
[acceptance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L13
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L10
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/ak47.json
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/ak47.json
[gun-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/ak47_data.json
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/ak47_data.json#L133-L139
[tag-match]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L193-L225
[logs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs.json
[oak-logs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/oak_logs.json
