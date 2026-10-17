# Timeless .50 Z-Type

The Timeless .50 Z-Type is a SEMI-only TaCZ pistol using [.50 AE ammunition](50AE.md). It holds **8 rounds** before magazine upgrades; its active damage curve starts at 16, despite a definition fallback of 15.

## Obtaining

Make **one Timeless .50 Z-Type** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Pistol**, using **1 Diamond, 6 Gold Ingots, and 48 Iron Ingots**. Carry the materials in your inventory and use the table's craft control. [Recipe][recipe] · [Pistol grouping][index] · [Recipe loading and output count][recipe-loader]

Survival crafting consumes the materials. Creative crafting still requires them to be present, but does not consume them; see [workbench controls and limits](../blocks/TaCZWorkbenches.md#using-a-workbench). [Craft payment][payment]

It also has a Creative Menu entry and is registered as `minecraft:timeless50`. A visible [inventory-browser entry](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant ordinary Survival insertion. [Creative entry][creative] · [Registration][registration]

## Usage

Hold the gun in your **main hand**. With the default controls, **left-click** fires, **hold right-click** aims, **R** reloads, and **Z** opens Refit. **SEMI is its only mode**: each mouse click requests one round, holding the default Shoot button does not repeat fire, and **G** stays on SEMI. See [shared controls](../mechanics/TaCZFirearms.md#controls) for remapping and [fire-mode behavior](../mechanics/TaCZFirearms.md#firing-and-fire-modes) for the keyboard-repeat caveat. [Input][controls] · [Bindings][keys] · [Supported mode][mode] · [Mode selection][mode-selection]

Reload with [.50 AE](50AE.md) and keep holding the gun until completion. The active reload takes **39 game ticks (1.95 seconds at 20 TPS)**, for either an empty or partly loaded magazine, including the listed extensions. This is the definition-based use duration, not the imported empty/tactical animation feed or cooldown numbers. [Definition][definition] · [Reload start and completion][reload-start] · [Timing selection][reload-time]

Each shot spends one loaded round in both Survival and Creative. Survival reloads consume carried matching ammunition; Creative reloads supply missing rounds without carried ammo. A small first matching stack can leave a Survival reload partly filled even with more reserve available. See [magazine and reload rules](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Firing][fire] · [Reload supply][reload-supply]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.50 AE](50AE.md) |
| Magazine size | 8 |
| Extended magazine sizes | 9, 11, 13 |
| Fire modes | SEMI |
| Nominal RPM | 300 |
| Damage (definition fallback) | 15 |
| Projectiles per shot | 1 |
| Launch-speed parameter | 8 blocks/tick (nominal 160 blocks/second at 20 TPS) |
| Configured lifetime | 20 ticks (1 second at 20 TPS) |
| Successful entity-hit budget (Pierce) | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

The numerical values above come from the [registered definition][definition] and feed the [active shot path][fire]. Nominal RPM is configuration: the firing gate rounds its interval to whole game ticks, so it is not a measured firing rate. Actual projectile motion also includes spread and shooter movement. The speed conversion assumes 20 TPS. [Launch calculation][launch] · [Projectile motion][motion] · [Tick rate][tick-rate]. Pierce 1 discards the projectile after one successful entity hit; a block collision also stops it. It does not promise armor bypass or wall penetration. Headshots multiply the distance-band damage for qualifying living-entity hits before the target handles that damage. [Firing gate][fire-gate] · [Hit handling][hit] · [Block collision][block-stop] · [Headshot test][headshot]

## Accuracy

These are the code-defined spread inputs for each pose or aim state, not hit percentages or verified angles. Aimed spread applies after the aim transition finishes. “Prone” names the lying-pose branch; it does not establish a working Crawl key. See [shared control limits](../mechanics/TaCZFirearms.md#controls). [Gun data][data] · [Pose selection][pose] · [Aiming check][controls]

| Property | Value |
| --- | --- |
| Standing | 3 |
| Moving | 4 |
| Sneaking | 2 |
| Prone | 1.5 |
| Aiming down sights | 0.1 |

## Damage Falloff

The active curve chooses the following damage for one projectile by straight-line distance from its starting position to the hit. An upper threshold is exclusive: hitting exactly at it uses the next band. There is no interpolation. Headshots and target damage handling can change the result. [Curve conversion][ballistics] · [Distance selection][distance] · [Hit handling][hit]

* **Below 20 blocks: 16**
* **20 to below 50 blocks: 14**
* **50 blocks or more, while the projectile remains active: 9**

The fallback **15** in Properties is not the active close-range value: the bundled curve supplies **16**, then **14**, then **9**. [Gun data][data] · [Curve selection][ballistics]

The final band does not imply unlimited range. Lifetime, drag, gravity, water, and collisions still limit the projectile; multiplying initial speed by lifetime is not a verified travel distance. [Projectile motion][motion] · [Block stop][block-stop]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Laser, Muzzle, Scope. Open [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) with **Z** to install a compatible carried item; matching the category alone is insufficient. [Compatibility][compatibility] · [Installation][attachment-server]

* Scope: [Aimpoint ACRO P-1 Sight](AimpointACROP1Sight.md), [DeltaPoint Sight](DeltaPointSight.md), [FastFire Sight](FastFireSight.md), [PK06 Sight](PK06Sight.md), [RMR Mini Red Dot](RMRMiniRedDot.md), [SRO Mini Red Dot](SROMiniRedDot.md)
* Muzzle: [Timeless .50 Cal Brake](Timeless50CalBrake.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Extended Mag: [Light Ammo Extended Mag I](LightAmmoExtendedMagI.md), [Light Ammo Extended Mag II](LightAmmoExtendedMagII.md), [Light Ammo Extended Mag III](LightAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

The Timeless .50 Cal Brake is its only accepted muzzle; the listed pistol-reflex sights remain separate Scope choices. [Accepted attachments][definition]

Light Ammo Extended Mag I, II, and III set capacity to the three listed extended sizes, respectively; reload to fill added space. Review the [capacity-reduction warning](../mechanics/TaCZFirearms.md#refitting-attachments) before removing or downgrading a loaded extension. [Magazine capacity][magazine]

Compatibility does not guarantee every imported attachment effect. The reviewed path supports magazine capacity and attachment recoil modifiers, but the list alone does not establish damage, penetration, incendiary/explosive, spread, range, or silencing effects. [Shot construction][fire] · [Attachment recoil][attachment-recoil] · [Camera application][camera-recoil]

## Notes

* This item is part of the integrated TaCZ firearms system. See [TaCZ firearms](../mechanics/TaCZFirearms.md) for shared behavior.
* The numbers describe MattMC's video-game implementation at the reviewed snapshot.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The registered definition, workbench recipe, controls, reload consumer, damage curve, pose spread, projectile hits, and attachment compatibility were checked. No in-game crafting, controls, reload timing, damage, range, or attachment test was performed. Imported fields are not treated as active behavior without a matching consumer.

[attachment-recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[attachment-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L76
[block-stop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L206-L230
[camera-recoil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L60
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[controls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/timeless50_data.json#L1-L142
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L69
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[fire-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L127
[headshot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L233-L251
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L204
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/timeless50.json#L1-L8
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L1-L75
[launch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L331
[mode]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L63
[mode-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L333-L348
[motion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L101-L134
[payment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L67
[pose]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/timeless50.json#L1-L27
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2736
[reload-start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-supply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[reload-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L32
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
