# Glock 17

The Glock 17 is a semi-automatic TaCZ pistol with a 17-round magazine and [9mm ammunition](9mmBullet.md).

## Obtaining

Make one Glock 17 from **16 Iron Ingots** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in the Pistol group. See that guide for table recipes and crafting controls. It also appears in the Creative Menu and is registered as `minecraft:glock_17`. [Gun recipe][glock-recipe] · [Creative entries][creative] · [Registration][gun-registration]

## Usage

Hold it in the main hand: left-click to fire, hold right-click to aim, and press **R** to reload with the default bindings. It supports SEMI only, so each click pulls the trigger once. Firing spends loaded rounds in Survival and Creative; Survival consumes carried [9mm Bullet](9mmBullet.md) when a reload completes. See [TaCZ firearms](../mechanics/TaCZFirearms.md) for remapping, partial reloads, reserve display, and refitting. [Controls][input] · [Supported mode][glock-mode] · [Firing][fire-round] · [Reload supply][reload-ammo]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [9mm Bullet](9mmBullet.md) |
| Magazine size | 17 |
| Extended magazine sizes | 20, 25, 30 |
| Fire modes | SEMI |
| RPM | 400 |
| Damage | 6 |
| Pellets per shot | 1 |
| Bullet speed | 150 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 2 |
| Moving | 2.5 |
| Sneaking | 1.5 |
| Prone | 0.75 |
| Aiming down sights | 0.2 |

## Damage Falloff

* 18 blocks: 7
* 45 blocks: 5
* infinite blocks: 4

The Damage row above is the definition fallback, **6**. The active bundled curve supplies **7 below 18 blocks**, **5 from 18 to below 45**, and **4 at 45 or more**, before headshot and target damage handling. The distances are exclusive upper thresholds, not interpolation points. See [how to read firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Glock data][glock-data] · [Curve selection][distance] · [Hit handling][hit]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Laser, Muzzle, Scope. Use the [Refit screen](../mechanics/TaCZFirearms.md#refitting-attachments), opened with **Z** by default, to install a compatible carried attachment.

* Scope: [Aimpoint ACRO P-1 Sight](AimpointACROP1Sight.md), [DeltaPoint Sight](DeltaPointSight.md), [FastFire Sight](FastFireSight.md), [PK06 Sight](PK06Sight.md), [RMR Mini Red Dot](RMRMiniRedDot.md), [SRO Mini Red Dot](SROMiniRedDot.md)
* Muzzle: [Mirage Silencer](MirageSilencer.md), [PO-2 "Ptilopsis" Silencer](PO2PtilopsisSilencer.md), [Wraith Silencer](WraithSilencer.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Extended Mag: [Light Ammo Extended Mag I](LightAmmoExtendedMagI.md), [Light Ammo Extended Mag II](LightAmmoExtendedMagII.md), [Light Ammo Extended Mag III](LightAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-03** at `cfa7057b6fe2b8dfa84e93f21932be2602eff749`. This is source review of the recipe, controls, ammunition rules, and damage interpretation, not an in-game test. The existing numeric tables remain the declared configuration; they do not promise damage delivered on a hit. [Gun definition][glock-definition]

[glock-recipe]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/recipes/gun/glock_17.json#L1-L15
[creative]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[gun-registration]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/Items.java#L2708-L2714
[input]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L35-L124
[fire-round]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L145-L181
[reload-ammo]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[glock-data]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/data/guns/glock_17_data.json#L1-L40
[distance]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[hit]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L179
[glock-definition]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L24
[glock-mode]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L21
