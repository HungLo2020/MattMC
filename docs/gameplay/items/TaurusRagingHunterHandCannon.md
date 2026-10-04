# Taurus "Raging Hunter" Hand Cannon

Taurus "Raging Hunter" Hand Cannon is a five-round, SEMI-only TaCZ handgun. Its accepted attachments include scopes, compact lasers and four ammo modifiers; capacity remains five rounds. [Definition][definition] · [Fit checks][fit]

## Obtaining

Taurus "Raging Hunter" Hand Cannon can be obtained from the Creative Menu. It is registered as `minecraft:taurus500`. [Registration][registration] · [Creative listing][creative]

At a [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), choose **Pistol** and carry the following materials in your inventory:

**8 Diamonds + 65 Gold Ingots + 85 Iron Ingots + 4 Blaze Rods → one Taurus "Raging Hunter" Hand Cannon.** [Recipe][gun-recipe] · [Pistol group][gun-group]

The gun stacks to **1**. Crafted guns and default Creative instances start with **5 loaded rounds** and no stored attachments. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting controls and Creative material checks. [Registration][registration] · [Output parser][craft] · [Starting ammunition][loaded]

## Usage

Use [.500 Magnum](500Magnum.md) (`minecraft:500mag`). Hold the gun in your **main hand**: Shoot is left mouse, held Aim is right mouse, Reload is **R**, Fire Mode is **G**, and Refit is **Z** by default. See [TaCZ firearm controls](../mechanics/TaCZFirearms.md#controls) for the shared controls and remapping limits. [Controls][input] · [Bindings][keys]

**SEMI is the only supported mode and the default.** Each default mouse click requests one shot; holding it does not keep firing, and G leaves the mode unchanged. A successful shot immediately creates **one projectile** and spends **one loaded round**, including in Creative. Keyboard repeat can request further shots after a keyboard remap. [Allowed mode][allowed-modes] · [Mode selection][modes-cycle] · [Trigger and firing][cadence] · [Ammunition use][fire] · [Keyboard repeat][keyboard]

The configured **120 RPM** becomes an integer **500 ms** interval, then `max(1, round(500 / 50))` gives a **10-tick trigger cooldown**, enforced by the server. This is a cooldown calculation, not a measured sustained firing rate; clicks, reloading and game ticking still matter. No RPM or ballistic mode adjustment is applied for this gun. [Cooldown][cadence] · [Server checks][server] · [RPM adjustments][rpm-adjust] · [Ballistic inputs][ballistics] · [Gun data][gun-data]

Reload takes **46 ticks (2.30 nominal seconds at 20 TPS)**. The same duration applies to empty and partial reloads; topping up fewer rounds does not shorten it. [Definition][definition] · [Timing selection][timings] · [Reload checks][reload]

Keep holding the gun until completion: the duration is captured at the start, ammunition arrives at the end, and already loaded rounds are retained. Survival takes up to the missing capacity from the **first matching inventory stack only**. The normal ammo stack limit is **42**; a first stack containing at least **5** rounds can fill this gun from empty in one completed reload. A smaller first stack can leave it partly loaded even when other reserve stacks contain more. Creative refills missing capacity without reserve ammunition. [Shared reload behavior](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) · [Ammo stack limit][ammo-index] · [Supply][loaded] · [Duration capture][duration] · [Held-item continuity][continuity]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.500 Magnum](500Magnum.md) |
| Magazine size | 5 |
| Extended magazine sizes | 5, 5, 5 |
| Fire modes | SEMI |
| RPM | 120 |
| Damage | 40 |
| Pellets per shot | 1 |
| Bullet speed | 250 m/s |
| Lifetime | 0.85 seconds |
| Pierce | 3 |
| Headshot multiplier | 2 |
| Knockback | 0.5 |

Capacity is fixed at **5 rounds**. The **5, 5, 5** extended-magazine row is a reference array; this gun accepts no Extended Mag attachment. [Definition and fit][definition] · [Accepted attachment checks][fit]

The **Damage 40** row is the definition fallback and also the first bundled distance-band value. Hits farther away use the lower bands below. “Pellets per shot: 1” means one projectile for that round. [Curve conversion][ballistics] · [Bundled curve][curve] · [Shot creation][fire]

**Bullet speed 250 m/s** is the nominal conversion of **12.5 blocks/tick × 20**, treating one block as one metre. Lifetime is **17 game ticks**. Spread, shooter movement, friction, gravity, water and collisions affect flight, so speed × lifetime is not a promised range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

**Pierce 3** is a budget of successful entity-damage events, not block penetration or a guarantee of that many different targets. Block impact invokes the ordinary block callback and discards the bullet. **Knockback 0.5** is extra horizontal bullet push, with a 0.03 upward component when a usable horizontal direction exists; it does not guarantee displacement. [Hit handling][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 3.5 |
| Moving | 5.5 |
| Sneaking | 2.25 |
| Prone | 1.5 |
| Aiming down sights | 0.1 |

These are spread inputs: lower values reduce random launch perturbation, not percentages or camera recoil. Selection priority is completed precision aim, non-swimming low pose, sneaking, moving, then standing. “Prone” names that low-pose input; it does not establish a working Crawl key. Attachment spread bonuses do not enter this spread calculation, and raw per-gun aim-time fields do not set the shared aim transition. See [shared controls](../mechanics/TaCZFirearms.md#controls). [Bundled inputs][spread] · [Pose selection][pose] · [Spread consumer][ballistics] · [Aim transition][aim] · [Launch][launch]

## Damage Falloff

Each finite distance below is an **exclusive upper threshold**, not damage exactly at that distance:

* 40 blocks: 40
* 55 blocks: 30
* 75 blocks: 20
* infinite blocks: 15

| Straight-line origin-to-hit distance | Damage per projectile before headshot/target handling |
| --- | --- |
| Below 40 blocks | 40 |
| 40 to below 55 blocks | 30 |
| 55 to below 75 blocks | 20 |
| 75 blocks or farther | 15 |

Equality moves to the next band; values are not interpolated. The terminal “infinite” entry still requires a projectile that survives its lifetime and collisions. [Bundled curve][curve] · [Distance selection][distance] · [Flight][flight]

The **2× headshot multiplier** applies to the selected band when a living target is hit at or above its Y position plus 0.85 × eye height. The target's normal damage handling follows, so these values are not guaranteed health loss. Imported `armor_ignore` does not add an armor-bypass bonus in this hit path. [Headshot and damage handling][hit] · [Loaded ballistic fields][parser]

## Attachments

Supported attachment categories: Ammo Modifier, Laser, Scope.

* Scope: [Contender 4x Scope](Contender4xScope.md), [Elcan 4x Scope](Elcan4xScope.md), [HAMR 3x Scope](HAMR3xScope.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [T1 red dot](T1reddot.md), [TA31 2x ACOG](TA312xACOG.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

Compatibility requires the accepted category **and exact attachment ID**; the list above is the complete set for this gun. Make attachments at the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), then follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments). Capacity is fixed at **5 rounds**. The **5, 5, 5** extended-magazine row is a reference array; this gun accepts no Extended Mag attachment. [Definition][definition] · [Fit checks][fit]

Accepted Ammo Modifiers do not change the reload cartridge or enable their imported damage, speed, armor, ignition or explosion effects in this reviewed shot path. Attachment fit does not mean every advertised field works; follow the individual owners for effect limits. Scope view and recoil can have active consumers. [Shot inputs][fire] · [Ballistic consumer][ballistics] · [Imported-field parser][parser] · [Recoil parser][recoil] · [Scope view][fov] The linked optic owners carry their existing scoped-view caveats where applicable; compatibility alone does not verify every scoped rendering setup.

## Notes

* This item is part of the integrated TaCZ firearms system.

The imported `charging` object does not add a charge-up requirement to the reviewed firing checks. Raw 2.28-second feed and 2.72-second cooldown fields do not replace the active 46-tick reload; `can_crawl` does not establish a working Crawl control. [Imported gun profile][gun-data] · [Imported reload fields][raw-reload] · [Firing checks][admission] · [Firing path][fire] · [Reload duration][reload] · [Timing selection][timings] · [Imported charging][charging]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. This page covers the bundled MattMC definitions, recipe parser, controls, reloads, projectile consumers and attachment fit. No in-game firing, timing, damage, refit or rendering test was run; upstream descriptions and imported metadata alone do not establish working behavior here.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L67
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2744
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/taurus500.json#L1-L33
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/taurus500.json#L1-L8
[craft]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[loaded]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L331
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[allowed-modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L61
[modes-cycle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[cadence]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L144
[fire]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[keyboard]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L560
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[rpm-adjust]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L1-L23
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[gun-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/taurus500_data.json#L1-L185
[timings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[ammo-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/ammo/500mag.json#L1-L6
[duration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3195-L3206
[continuity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3133
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/taurus500_data.json#L18-L35
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[ticks]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[spread]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/taurus500_data.json#L178-L184
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[aim]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[parser]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L151-L222
[recoil]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L331
[fov]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L86
[raw-reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/taurus500_data.json#L52-L62
[admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L99
[charging]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/taurus500_data.json#L63-L73
