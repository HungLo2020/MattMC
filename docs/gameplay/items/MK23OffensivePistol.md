# MK23 Offensive Pistol

Under the truck, old friend. An echo of Shadow Moses.

## Obtaining

Make **one MK23 Offensive Pistol** from **4 Gold Ingots, 36 Iron Ingots, and 2 Lapis Lazuli** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in the **Pistol** group. Carry the materials in your inventory and follow the workbench guide for crafting controls. [Recipe][recipe] · [Pistol group][group] · [Recipe loading and output][recipe-loading]

It also appears in the Creative Menu and is registered as `minecraft:hk_mk23`. A visible [inventory-browser entry](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not provide ordinary Survival insertion. [Registration][registration] · [Creative entries][creative]

## Usage

Hold the gun in your **main hand**. With the default bindings, left-click to shoot, hold right-click to aim, and press **R** to reload using [.45 ACP Bullet](45ACPBullet.md). See [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for remapping and aiming limits. [Input][input] · [Bindings][keys]

The default **SEMI** mode fires one round per click with a **24-tick trigger cooldown** (1.2 seconds at 20 TPS), from its configured 50 RPM. Press **G** to switch to the mode labelled **BURST**: this gun still fires **one round per click**, with a **4-tick trigger cooldown** (200 ms at 20 TPS). Holding the default mouse button does not repeat either mode. The one-round BURST path fires directly, so it does not use multi-round task scheduling. [Modes][modes] · [Burst definition][burst] · [Trigger and scheduling][triggers]

BURST lowers the headshot multiplier from **1.75× to 1.5×** and increases spread, as shown below. The damage curve is unchanged. These are source-defined mode differences, not measured firing rates. [Mode adjustments][ballistics] · [Gun data][data]

Reloading takes **39 game ticks (1.95 seconds at 20 TPS)**; keep holding the gun until it completes. This duration applies to partial and empty reloads, in every supported mode and with all listed extended magazines. Firing spends loaded rounds in Survival and Creative; ammunition is added only at reload completion. Survival takes ammunition from the first matching inventory stack, so a small first stack can leave the magazine partly filled. See [magazine and reload rules](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) for reserve display and Creative supply. [Reload duration][reload] · [Timing selection][reload-timing] · [Ammunition supply][supply]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.45 ACP Bullet](45ACPBullet.md) |
| Magazine size | 12 |
| Extended magazine sizes | 16, 20, 24 |
| Fire modes | SEMI, BURST |
| RPM (configured base) | 50 |
| Damage (definition fallback) | 12 |
| Pellets per shot | 1 |
| Configured launch speed | 9.5 blocks/tick (190 blocks/second at 20 TPS) |
| Lifetime | 20 ticks (1 second at 20 TPS) |
| Successful entity-hit limit | 1 |
| Headshot multiplier | 1.75× in SEMI; 1.5× in BURST |
| Knockback | 0 |

The speed row is the configured launch input, not a measured constant speed; spread, shooter movement, drag, and gravity affect flight. [Launch movement][launch] · [Projectile motion][motion]

The definition's declared Pierce value is **1**; projectile creation sets a hit budget of at least one. For this gun, the bullet is discarded after its first successful entity hit or a block collision. This value does not describe wall or armor penetration. [Gun definition][definition] · [Shot creation][shot] · [Hit handling][hits] · [Block collision][blocks]

## Accuracy

These are the code-defined spread inputs, not hit percentages. The aimed value applies after the aim transition; otherwise the active pose/movement branch supplies the value. “Prone” refers to the lying-pose branch, not a working Crawl key; see [control limitations](../mechanics/TaCZFirearms.md#controls). [Gun data][data] · [Spread and mode adjustments][ballistics] · [Pose selection][pose]

| Property | SEMI (default) | BURST |
| --- | --- | --- |
| Standing | 2.25 | 2.75 |
| Moving | 3.25 | 3.75 |
| Sneaking | 1.25 | 1.75 |
| Prone | 0.75 | 1.25 |
| Aiming down sights | 0.05 | 0.1 |

## Damage Falloff

The active curve supplies the following damage per projectile before headshots and target damage handling. Distance is measured from the shot origin to the hit; each upper threshold is exclusive, with a step change rather than interpolation. [Damage curve][data] · [Curve conversion][ballistics] · [Distance selection][distance] · [Hit handling][hits]

* Below 20 blocks: **12**
* 20 to below 50 blocks: **11**
* 50 blocks or farther, while the projectile remains alive: **9**

The final band does not give unlimited range. Lifetime, drag, gravity, water, and collisions still limit the projectile. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Projectile motion][motion] · [Block collision][blocks]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Laser, Muzzle, Scope. Use **Z** by default to open [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) and install a compatible carried attachment.

* Scope: [Aimpoint ACRO P-1 Sight](AimpointACROP1Sight.md), [DeltaPoint Sight](DeltaPointSight.md), [FastFire Sight](FastFireSight.md), [PK06 Sight](PK06Sight.md), [RMR Mini Red Dot](RMRMiniRedDot.md), [SRO Mini Red Dot](SROMiniRedDot.md)
* Muzzle: [Mirage Silencer](MirageSilencer.md), [PO-2 "Ptilopsis" Silencer](PO2PtilopsisSilencer.md), [Wraith Silencer](WraithSilencer.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md), [PEQ6 ILLM](PEQ6ILLM.md)
* Extended Mag: [Light Ammo Extended Mag I](LightAmmoExtendedMagI.md), [Light Ammo Extended Mag II](LightAmmoExtendedMagII.md), [Light Ammo Extended Mag III](LightAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

The listed extended magazines set the capacities shown above; reload to fill added space. Compatibility establishes that an attachment can be installed, not that every imported damage, penetration, incendiary, explosive, spread, range, or silencing claim is active. Follow the shared Refit guide before reducing magazine capacity. [Compatibility][compatibility] · [Magazine capacity][magazine]

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. This review follows registered recipes, controls, firing, reload completion, ballistics, and attachment compatibility. No in-game crafting, combat, timing, attachment, or multiplayer test was performed. Imported animation and handling fields are not treated as working gameplay effects without an active consumer.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/hk_mk23.json#L1-L27
[group]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/hk_mk23.json#L1-L8
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2736
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L1-L75
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L23
[triggers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L143
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L76
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L32
[supply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L26
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[hits]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L204
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L206-L230
[data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/hk_mk23_data.json#L1-L166
[pose]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[motion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L101-L134
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L331
[launch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[burst]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L10
