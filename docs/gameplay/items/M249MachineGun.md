# M249 Machine Gun

M249 is an AUTO-only TaCZ machine gun with a 75-round base magazine and Heavy magazine upgrades up to 200 rounds. [Definition][definition] · [Modes][modes]

## Obtaining

M249 Machine Gun can be obtained from the Creative Menu. It is registered as `minecraft:m249`. Guns stack to one. [Registration][registration] · [Creative entry][creative]

Craft one at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Machine Gun**. Carry the materials in your inventory; see [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the procedure. [Recipe][recipe] · [Group selection][gun-group] · [Group names][group-labels] · [Crafting rules][craft]

| Materials per craft | Output |
| --- | --- |
| 4 Diamonds · 16 Gold Ingots · 110 Iron Ingots · 2 Blaze Rods | **1 × M249 Machine Gun** |

A freshly crafted gun and a default Creative gun start with **75 loaded rounds** and no installed attachments. There is no extra chamber round beyond that capacity. [Output parsing][recipe-loader] · [Initial ammunition][ammo-state]

## Usage

Hold the gun in your main hand. It uses [5.56x45mm Bullet](556x45mmBullet.md) (`minecraft:556x45`); the ammunition page covers its crafting recipe and supply. Default controls are **left mouse to Shoot**, **hold right mouse to Aim**, **R to Reload**, **G for Fire Mode**, and **Z to Refit**. See [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for input conditions and remapping. [Controls][input] · [Default bindings][keys]

Its only mode is **AUTO**: holding Shoot repeats shot requests subject to the firing cooldown. Pressing G leaves it in AUTO; it has no SEMI or BURST mode. [Modes][modes] · [Mode cycling][cycle-mode] · [Input][input]

The configured **750 RPM** produces an integer **80 ms interval**, which rounds to a **2-tick trigger cooldown** enforced by the server. This is a configured timing input, not a measured sustained firing rate. Each successful shot fires **one projectile** and spends **one loaded round**, including in Creative. [Cooldown calculation][cadence] · [RPM selection][rpm-adjustments] · [Server gate][server] · [Firing][fire-round]

### Reloading and supply

A completed reload takes **128 game ticks (6.40 seconds at 20 TPS)**, whether the magazine is empty or partly loaded and at every accepted magazine tier. Keep holding the same gun until completion; switching held items stops the use path. Loaded rounds are retained, and ammunition is added at completion. A full or over-capacity gun cannot begin a reload. [Definition][definition] · [Active timing][reload-timing] · [Reload checks][reload-start] · [Held-item continuity][continuity] · [Supply][ammo-state]

In Survival, each completion draws only from the **first inventory stack matching `minecraft:556x45`**, up to the missing capacity. The normal ammunition stack is **60**. Later matching stacks are not combined into that reload, even when the HUD reserve total is large. [Ammo stack][ammo-index] · [Stack selection][ammo-state] · [Magazine and reserve](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading)

An empty base magazine therefore loads at most **60 of 75 rounds** from one normal full stack. Reload again to add the remaining 15 when another matching stack has enough ammunition. Even a normal full first stack cannot fill this empty gun in one completion. [Supply][ammo-state]

Creative reloads fill all missing capacity without requiring or consuming reserve ammunition, but firing still spends loaded rounds. The [capacity table below](#magazine-capacity) shows the minimum Survival reload completions for each magazine. [Creative supply][ammo-state] · [Firing][fire-round]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [5.56x45mm Bullet](556x45mmBullet.md) |
| Magazine size | 75 |
| Extended magazine sizes | 100, 150, 200 |
| Fire modes | AUTO |
| RPM | 750 |
| Damage | 7.5 |
| Pellets per shot | 1 |
| Bullet speed | 280 m/s |
| Lifetime | 1 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

These are the gun definition's values. **Damage 7.5 is the fallback**; normal hits use the [distance bands below](#damage-falloff). In particular, the near-distance band is 7, not the fallback 7.5. [Definition][definition] · [Damage selection][ballistics]

The listed **280 m/s** is the nominal conversion of **14 blocks/tick**, treating one block as one metre at 20 TPS. Lifetime is **20 ticks**. Spread, inherited shooter movement, gravity, friction, water and collisions affect flight; speed × lifetime is not a guaranteed range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

**Pierce 1** is the budget of successful entity-damage events; it does not grant block penetration or guarantee that many distinct targets. An ordinary block hit invokes the block's projectile callback and discards the bullet. **Knockback 0** adds no extra push from this bullet routine; it does not rule out all target movement. [Hit handling][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 5.25 |
| Moving | 5.5 |
| Sneaking | 3 |
| Prone | 2 |
| Aiming down sights | 0.175 |

These are spread inputs: lower values reduce random launch deviation. They are not percentages, guaranteed hit rates or camera recoil. Completed precision aim takes priority, then a non-swimming low pose (the “Prone” row), crouching, moving, and standing. The spread calculation does not apply attachment spread bonuses. The Prone row does not make the Crawl key functional; see [shared controls](../mechanics/TaCZFirearms.md#controls). [Bundled accuracy][accuracy] · [Spread calculation][ballistics] · [Pose selection][pose] · [Random launch][launch]

## Damage Falloff

* 35 blocks: 7
* 80 blocks: 5.5
* infinite blocks: 4.5

The entries above are exclusive upper thresholds: **7 below 35 blocks**, **5.5 from 35 to below 80**, and **4.5 at 80 or more**. Equality advances to the next band, with no interpolation. Distance is measured straight from the shot origin to the hit. “Infinite” names the last band; the projectile must still survive its lifetime and collisions. [Bundled curve][curve] · [Distance selection][distance]

A headshot multiplies the selected band by **1.5** before the target's normal damage handling, so these values do not guarantee that much health loss. The headshot check requires a living target and a hit at or above its Y position plus 85% of its eye height. [Hit and headshot handling][hit]

## Attachments

Use the [Refit screen](../mechanics/TaCZFirearms.md#refitting-attachments) to install carried attachments. Fit requires both the accepted category and the exact attachment ID below; names or caliber alone do not establish compatibility. M249 Machine Gun accepts Grip and Laser attachments, but has no Stock slot. Follow each linked item's page for its effect scope and limitations; accepted fit does not make every imported stat or effect active. [Accepted IDs][definition] · [Fit checks][fit]

Supported attachment categories: Ammo Modifier, Extended Mag, Grip, Laser, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Grip: [Hera Arms CQR Grip](HeraArmsCQRGrip.md), [Koch Ranger Heavy Grip](KochRangerHeavyGrip.md), [Nagoma Military Standard Grip](NagomaMilitaryStandardGrip.md), [P-2 Grip](P2Grip.md), [RK-0 Grip](RK0Grip.md), [RK-1 B25U Grip](RK1B25UGrip.md), [RK-6 Grip](RK6Grip.md), [SE-5 Express Grip](SE5ExpressGrip.md), [SI Grip](SIGrip.md), [Talon AFG1 Handstop](TalonAFG1Handstop.md), [Talon SG2 Grip](TalonSG2Grip.md), [TD Grip](TDGrip.md)
* Laser: [laser_peq15](laserpeq15.md), [Lopro Tactical Laser](LoproTacticalLaser.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

### Magazine capacity

Only **Heavy Ammo Extended Mag I, II and III** from the list above fit. Each tier selects a total capacity; tiers do not add together, and installing one supplies no rounds. Reload to fill the added space. [Definition][definition] · [Capacity selection][ammo-state] · [Level lookup][mag-level] · [Attachment storage][refit]

| Installed magazine | Total rounds | Minimum completed Survival reloads from empty |
| --- | --- | --- |
| None (base) | 75 | 2 |
| Heavy I | 100 | 2 |
| Heavy II | 150 | 3 |
| Heavy III | 200 | 4 |

These completion counts assume enough full normal **60-round stacks** and no firing between reloads. A smaller first stack can require more completions. [Reload supply][ammo-state]

**Use excess rounds before downsizing.** Replacing or removing an extended magazine leaves the stored loaded count unchanged initially. The next shot spends one round and then clamps the remainder to the new capacity, losing any excess without a refund. For example, changing a fully loaded Heavy III (200) to Heavy I (100) initially keeps 200 loaded; the next shot leaves 100, spending one and discarding 99 more. [Attachment changes][refit] · [Firing and consumption][fire-round] · [Ammunition clamp][ammo-state]

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The recipe, loaded state, reload supply, mode/cooldown, damage, accuracy and exact attachment fit were checked against the integrated sources. These are source findings; no in-game crafting, firing, reload, appearance or multiplayer test was performed.

[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2744
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[cadence]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L144
[fire-round]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[reload-start]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[ammo-state]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L331
[cycle-mode]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[rpm-adjustments]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L1-L23
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[ticks]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[refit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L16-L82
[mag-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[continuity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3133
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[craft]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[group-labels]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8562
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L45
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L37
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/m249.json#L1-L33
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/m249.json#L1-L8
[accuracy]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m249_data.json#L122-L128
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m249_data.json#L20-L33
[ammo-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/ammo/556x45.json#L1-L7
