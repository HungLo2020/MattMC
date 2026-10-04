# Raging Bull

Raging Bull is a five-round, SEMI-only TaCZ handgun with no accepted attachments. Its reload time depends on how many rounds are still loaded. [Definition][definition] · [Reload timing][timings]

## Obtaining

Raging Bull can be obtained from the Creative Menu. It is registered as `minecraft:trs_bull`. [Registration][registration] · [Creative listing][creative]

At a [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), choose **Pistol** and carry the following materials in your inventory:

**38 Iron Ingots → one Raging Bull.** [Recipe][gun-recipe] · [Pistol group][gun-group]

The gun stacks to **1**. Crafted guns and default Creative instances start with **5 loaded rounds** and no stored attachments. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting controls and Creative material checks. [Registration][registration] · [Output parser][craft] · [Starting ammunition][loaded]

## Usage

Use [Raging Bull .454 Casull](RagingBull454Casull.md) (`minecraft:trs_bull_ammo`). Hold the gun in your **main hand**: Shoot is left mouse, held Aim is right mouse, Reload is **R**, Fire Mode is **G**, and Refit is **Z** by default. See [TaCZ firearm controls](../mechanics/TaCZFirearms.md#controls) for the shared controls and remapping limits. [Controls][input] · [Bindings][keys]

**SEMI is the only supported mode and the default.** Each default mouse click requests one shot; holding it does not keep firing, and G leaves the mode unchanged. A successful shot immediately creates **one projectile** and spends **one loaded round**, including in Creative. Keyboard repeat can request further shots after a keyboard remap. [Allowed mode][allowed-modes] · [Mode selection][modes-cycle] · [Trigger and firing][cadence] · [Ammunition use][fire] · [Keyboard repeat][keyboard]

The configured **80 RPM** becomes an integer **750 ms** interval, then `max(1, round(750 / 50))` gives a **15-tick trigger cooldown**, enforced by the server. This is a cooldown calculation, not a measured sustained firing rate; clicks, reloading and game ticking still matter. No RPM or ballistic mode adjustment is applied for this gun. [Cooldown][cadence] · [Server checks][server] · [RPM adjustments][rpm-adjust] · [Ballistic inputs][ballistics] · [Gun data][gun-data]

**Reload duration is selected from the loaded count when reloading starts:**

| Loaded rounds at start | Missing rounds | Reload ticks | Nominal seconds at 20 TPS |
| --- | --- | --- | --- |
| 0 | 5 | 61 | 3.05 |
| 1 | 4 | 79 | 3.95 |
| 2 | 3 | 97 | 4.85 |
| 3 | 2 | 72 | 3.60 |
| 4 | 1 | 74 | 3.70 |
| 5 | 0 | Cannot start: full | — |

The empty reload is shorter than every partial reload, and the partial times do not rise evenly with missing rounds. The lookup uses **loaded rounds**, not reserve size. The definition's **69 ticks** is only a fallback; the reachable reload states above use the override, rounded to whole ticks. [Timing lookup and rounding][bull-timings] · [Override][timings] · [Definition][definition] · [Start checks][reload]

Keep holding the gun until completion: the duration is captured at the start, ammunition arrives at the end, and already loaded rounds are retained. Survival takes up to the missing capacity from the **first matching inventory stack only**. The normal ammo stack limit is **64**; a first stack containing at least **5** rounds can fill this gun from empty in one completed reload. A smaller first stack can leave it partly loaded even when other reserve stacks contain more. Creative refills missing capacity without reserve ammunition. [Shared reload behavior](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) · [Ammo stack limit][ammo-index] · [Supply][loaded] · [Duration capture][duration] · [Held-item continuity][continuity]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [Raging Bull .454 Casull](RagingBull454Casull.md) |
| Magazine size | 5 |
| Extended magazine sizes | None |
| Fire modes | SEMI |
| RPM | 80 |
| Damage | 23 |
| Pellets per shot | 1 |
| Bullet speed | 570 m/s |
| Lifetime | 7.3 seconds |
| Pierce | 3 |
| Headshot multiplier | 1.7 |
| Knockback | 0.4 |

Capacity is fixed at **5 rounds**; there is no accepted Extended Mag slot. [Definition and fit][definition] · [Accepted attachment checks][fit]

The **Damage 23** row is the definition fallback. Normal hits use the bundled distance curve, starting at **45 below 17 blocks**, before headshot and target handling. “Pellets per shot: 1” means one projectile for that round. [Curve conversion][ballistics] · [Bundled curve][curve] · [Shot creation][fire]

**Bullet speed 570 m/s** is the nominal conversion of **28.5 blocks/tick × 20**, treating one block as one metre. Lifetime is **146 game ticks**. Spread, shooter movement, friction, gravity, water and collisions affect flight, so speed × lifetime is not a promised range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

**Pierce 3** is a budget of successful entity-damage events, not block penetration or a guarantee of that many different targets. Block impact invokes the ordinary block callback and discards the bullet. **Knockback 0.4** is extra horizontal bullet push, with a 0.03 upward component when a usable horizontal direction exists; it does not guarantee displacement. [Hit handling][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 2 |
| Moving | 2.5 |
| Sneaking | 1.5 |
| Prone | 0.7 |
| Aiming down sights | 0.2 |

These are spread inputs: lower values reduce random launch perturbation, not percentages or camera recoil. Selection priority is completed precision aim, non-swimming low pose, sneaking, moving, then standing. “Prone” names that low-pose input; it does not establish a working Crawl key. Attachment spread bonuses do not enter this spread calculation, and raw per-gun aim-time fields do not set the shared aim transition. See [shared controls](../mechanics/TaCZFirearms.md#controls). [Bundled inputs][spread] · [Pose selection][pose] · [Spread consumer][ballistics] · [Aim transition][aim] · [Launch][launch]

## Damage Falloff

Each finite distance below is an **exclusive upper threshold**, not damage exactly at that distance:

* 17 blocks: 45
* 20 blocks: 40
* 45 blocks: 35
* infinite blocks: 25

| Straight-line origin-to-hit distance | Damage per projectile before headshot/target handling |
| --- | --- |
| Below 17 blocks | 45 |
| 17 to below 20 blocks | 40 |
| 20 to below 45 blocks | 35 |
| 45 blocks or farther | 25 |

Equality moves to the next band; values are not interpolated. The terminal “infinite” entry still requires a projectile that survives its lifetime and collisions. [Bundled curve][curve] · [Distance selection][distance] · [Flight][flight]

The **1.7× headshot multiplier** applies to the selected band when a living target is hit at or above its Y position plus 0.85 × eye height. The target's normal damage handling follows, so these values are not guaranteed health loss. Imported `armor_ignore` does not add an armor-bypass bonus in this hit path. [Headshot and damage handling][hit] · [Loaded ballistic fields][parser]

## Attachments

Supported attachment categories: None.

* No attachments are listed for this firearm.

The active category and exact-ID checks accept no attachments, so refitting cannot extend capacity or add an optic. Capacity is fixed at **5 rounds**; there is no accepted Extended Mag slot. [Definition][definition] · [Fit checks][fit]

## Notes

* This item is part of the integrated TaCZ firearms system.

Its imported reload feed/cooldown values of 12.15/13.15 seconds do not set the live reload duration. The loaded-round timing table above comes from the active override. [Imported gun profile][gun-data] · [Imported reload fields][raw-reload] · [Firing checks][admission] · [Firing path][fire] · [Reload duration][reload] · [Timing selection][timings]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. This page covers the bundled MattMC definitions, recipe parser, controls, reloads, projectile consumers and attachment fit. No in-game firing, timing, damage, refit or rendering test was run; upstream descriptions and imported metadata alone do not establish working behavior here.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L38
[timings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2744
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/trs_bull.json#L1-L15
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/trs_bull.json#L1-L7
[craft]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[loaded]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L331
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[allowed-modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L64
[modes-cycle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[cadence]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L144
[fire]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[keyboard]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L560
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[rpm-adjust]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L1-L23
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[gun-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/trs_bull_data.json#L1-L69
[bull-timings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L80-L92
[reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[ammo-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/ammo/trs_bull_ammo.json#L1-L5
[duration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3195-L3206
[continuity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3133
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/trs_bull_data.json#L12-L17
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[ticks]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[spread]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/trs_bull_data.json#L62-L68
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[aim]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[parser]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L151-L222
[raw-reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/trs_bull_data.json#L31-L41
[admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L99
