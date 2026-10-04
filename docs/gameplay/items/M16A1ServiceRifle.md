# M16A1 Service Rifle

The **M16A1 Service Rifle** is a 20-round TaCZ rifle using 5.56x45mm ammunition, with AUTO and SEMI fire. [Definition][definition] · [Fire modes][modes]

## Obtaining

Craft **one M16A1 Service Rifle** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Rifle**. Carry the materials in your inventory; one craft-control click makes the output below. [Recipe][recipe] · [Recipe loading and output count][loader] · [Rifle index][index] · [Table groups][groups]

| Materials per craft | Output |
| --- | --- |
| 32 Iron Ingots · 6 Lapis Lazuli · 6 logs | **1 × M16A1 Service Rifle** |

The logs use the `minecraft:logs` item tag. You can combine matching kinds; Oak Logs and Stripped Oak Wood are examples. Planks do not satisfy this ingredient. [Tag matching][tag-match] · [Inventory counting and consumption][ingredients] · [Logs tag][logs] · [Oak members][oak-logs]

The registered item is `minecraft:m16a1`, stacks to **1**, and also appears in Creative. The [inventory browser's Survival catalog](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant it to ordinary Survival players. See [workbench use](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting controls and Creative restrictions. [Registration][registration] · [Creative entry][creative]

A fresh crafted or Creative gun starts with **20 loaded rounds**, **AUTO** selected, and **no installed attachments**. The plain recipe output has no saved ammunition override, so the gun reads its base capacity. Obtain and fit attachments separately. [Output construction][loader] · [Loaded rounds and initial mode][loaded] · [Creative stack construction][creative-stack] · [Attachment storage][refit-storage]

## Usage

Use [5.56x45mm Bullet](556x45mmBullet.md), registered as `minecraft:556x45` and carried in stacks of up to **60**. Other cartridges and Ammo Modifier attachments cannot supply this gun's reloads. [Gun definition][definition] · [Ammo stack size][ammo-definition] · [Exact ammunition matching][ammo-supply]

Hold the gun in your **main hand**. With [default TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, **G** changes fire mode, and **Z** opens Refit. [Default keys][keys] · [Active controls][input]

The fresh mode is **AUTO**; G switches **AUTO → SEMI → AUTO**. With the default mouse binding, hold Shoot for AUTO or click for one SEMI shot. [Modes][modes] · [Mode cycling][cycle] · [Active controls][input]

The stored three-round burst definition does **not** enable BURST: this gun's supported mode list contains only AUTO and SEMI. [Modes][modes] · [Unused burst definition][burst] · [Trigger selection][trigger]

The configured RPM timing input is **750 in both supported modes**; there is no hardcoded RPM adjustment for this gun. This is not a measured sustained firing rate. See [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes) for cooldown and keyboard-remapping behavior. [Definition][definition] · [RPM adjustments][rpm] · [Trigger timing][trigger]

Reloading takes **43 game ticks** (2.15 seconds at 20 ticks per second) before ammunition is added. The server duration is the same whether empty or partly loaded and with any accepted extended magazine. Imported empty/tactical feed and animation values do not replace this duration. Keep holding the gun until it finishes. [Definition][definition] · [Active reload timing][reload-timing] · [Reload completion][reload] · [Use counter][use-counter] · [Completion dispatch][use-completion]

The loaded count is the whole firing supply: there is **no additional chamber round** above the selected magazine capacity. Reloading preserves rounds already loaded. Survival uses only the **first matching carried stack** at completion, which can cause a partial reload despite further reserve. Creative reloads supply missing rounds without requiring or consuming carried ammunition; firing still spends **one loaded round per fired round**. See [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Ammo and capacity][loaded] · [Reload supply][ammo-supply] · [Shot consumption][shots]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [5.56x45mm Bullet](556x45mmBullet.md) |
| Magazine size | 20 |
| Extended magazine sizes | 24, 27, 30 |
| Fire modes | AUTO, SEMI |
| RPM | 750 |
| Damage | 8 |
| Pellets per shot | 1 |
| Bullet speed | 310 m/s |
| Lifetime | 0.75 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

These are base definition values. This gun has no hardcoded RPM adjustment or active bundled ballistic mode adjustment; switching modes does not change the listed bullet speed, headshot multiplier, or the spread and damage bands below. [RPM adjustment map][rpm] · [Bundled data][gun-data] · [Ballistic parser][ballistic-parser] · [Applied adjustments][spread]

Damage is the fallback value; the active shot uses the distance curve below. Bullet speed is the initial configured speed, and lifetime is finite, so the final “infinite” falloff entry does not give the projectile unlimited range. [Shot creation][shots] · [Projectile flight][flight]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 3.75 |
| Moving | 4.5 |
| Sneaking | 2.5 |
| Prone | 1.75 |
| Aiming down sights | 0.1 |

These values apply in every supported mode. They are projectile-spread inputs: lower values produce less random spread, not a hit percentage or a camera-recoil score. The aimed row applies once the aim transition completes. “Prone” describes the low-pose case, not a working Crawl key; see the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][accuracy-data] · [Applied adjustments][spread] · [Pose selection][pose] · [Aim gate][input] · [Spread application][projectile-spread]

## Damage Falloff

* 45 blocks: 8.5
* 70 blocks: 7
* infinite blocks: 5

These thresholds mean **8.5 below 45 blocks, 7 from 45 to below 70, and 5 at 70 or more** in every supported mode. The **Damage 8** property is a fallback; the bundled curve supplies **8.5**, rather than 8, in its first band. Equality moves to the next band; this is stepwise falloff, before headshots and target damage handling. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Curve data][gun-data] · [Curve conversion][spread] · [Distance selection][distance] · [Hit handling][hit]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle, Scope.

* Scope: [Retro 3x Scope](Retro3xScope.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [M9 Bayonet](M9Bayonet.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

This is the exact registered attachment list accepted by the gun: **both the category and the attachment ID must match**. Make parts at the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), then use [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install them. Installation consumes one carried attachment, including in Creative. Acceptance alone does not establish every imported attachment effect. [Accepted categories and IDs][definition] · [Acceptance check][acceptance] · [Server installation][install]

Heavy Ammo Extended Mag **I / II / III** selects **24, 27, and 30 rounds**, respectively. Installation adds capacity without adding ammunition; fitting tier I to a fresh rifle leaves **20/24** until reloaded. Use up ammunition above a smaller capacity before downsizing, because [the next shot can discard excess rounds](../mechanics/TaCZFirearms.md#refitting-attachments). [Capacity selection][capacity-selection] · [Loaded count][loaded] · [Attachment storage][refit-storage]

The **Retro 3x Scope is its only accepted optic**. The M9 Bayonet is accepted in the Muzzle category, but there is no Grip, Laser, or Stock slot. Compatibility with the bayonet does not make the listed TaCZ Melee binding a working action; see the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Accepted attachments][definition] · [Active controls][input]

## Notes

* This item is part of the integrated TaCZ firearms system.
* Shared guides: [TaCZ firearms](../mechanics/TaCZFirearms.md), [TaCZ Workbenches](../blocks/TaCZWorkbenches.md), and [Items](Items.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registration, dedicated workbench recipe loading, fresh item state, active input and server paths, ammunition matching, reload completion, mode adjustments, projectile consumers, and attachment acceptance were checked. This is **source review**, not an in-game crafting, firing-rate, reload, accuracy, recoil, damage, or multiplayer test.

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
[capacity-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[ballistic-parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L158-L221
[burst]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L6-L44
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L90-L98
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L42
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L33
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/m16a1.json
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/m16a1.json
[gun-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/m16a1_data.json
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/m16a1_data.json#L128-L134
[tag-match]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L193-L225
[ingredients]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L79-L105
[logs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs.json
[oak-logs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/oak_logs.json
