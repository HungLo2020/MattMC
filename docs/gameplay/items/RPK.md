# RPK

RPK is a TaCZ machine gun with AUTO and SEMI modes, a 40-round base magazine, and Heavy magazine upgrades up to 75 rounds. [Definition][definition] · [Modes][modes]

## Obtaining

RPK can be obtained from the Creative Menu. It is registered as `minecraft:rpk`. Guns stack to one. [Registration][registration] · [Creative entry][creative]

Craft one at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Machine Gun**. Carry the materials in your inventory; see [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the procedure. [Recipe][recipe] · [Group selection][gun-group] · [Group names][group-labels] · [Crafting rules][craft]

| Materials per craft | Output |
| --- | --- |
| 56 Iron Ingots · 8 Gold Ingots · 8 Nether Quartz · 12 items in the `minecraft:logs` tag | **1 × RPK** |

A freshly crafted gun and a default Creative gun start with **40 loaded rounds** and no installed attachments. There is no extra chamber round beyond that capacity. [Output parsing][recipe-loader] · [Initial ammunition][ammo-state]

The log ingredient uses the exact `minecraft:logs` tag, including its Nether stem groups; planks do not satisfy it. The recipe lists a Factory Issued Heavy Stock preset, but the current output parser does not install it. Obtain and refit a compatible stock separately using the [attachments below](#attachments). [Log tag][logs] · [Recipe][recipe] · [Output parsing][recipe-loader]

## Usage

Hold the gun in your main hand. It uses [7.62x39mm Bullet](762x39mmBullet.md) (`minecraft:762x39`); the ammunition page covers its crafting recipe and supply. Default controls are **left mouse to Shoot**, **hold right mouse to Aim**, **R to Reload**, **G for Fire Mode**, and **Z to Refit**. See [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for input conditions and remapping. [Controls][input] · [Default bindings][keys]

It starts in **AUTO**; G cycles **AUTO → SEMI → AUTO**. With the default mouse binding, AUTO repeats shot requests while held and SEMI requests one shot per click. Both modes share the same damage, speed, spread, headshot, knockback and firing cooldown. There is no BURST mode. [Modes][modes] · [Mode cycling][cycle-mode] · [Input][input] · [Ballistic inputs][ballistics] · [Bundled data][gun-data]

The configured **630 RPM** produces an integer **95 ms interval**, which rounds to a **2-tick trigger cooldown** enforced by the server. This is a configured timing input, not a measured sustained firing rate. Each successful shot fires **one projectile** and spends **one loaded round**, including in Creative. [Cooldown calculation][cadence] · [RPM selection][rpm-adjustments] · [Server gate][server] · [Firing][fire-round]

### Reloading and supply

A completed reload takes **59 game ticks (2.95 seconds at 20 TPS)**, whether the magazine is empty or partly loaded and at every accepted magazine tier. Keep holding the same gun until completion; switching held items stops the use path. Loaded rounds are retained, and ammunition is added at completion. A full or over-capacity gun cannot begin a reload. [Definition][definition] · [Active timing][reload-timing] · [Reload checks][reload-start] · [Held-item continuity][continuity] · [Supply][ammo-state]

In Survival, each completion draws only from the **first inventory stack matching `minecraft:762x39`**, up to the missing capacity. The normal ammunition stack is **60**. Later matching stacks are not combined into that reload, even when the HUD reserve total is large. [Ammo stack][ammo-index] · [Stack selection][ammo-state] · [Magazine and reserve](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading)

One full 60-round ammunition stack can fill the empty 40-round base magazine, or Heavy I and II capacities. **Heavy III holds 75**, so filling it from empty needs at least two completions with normal stacks. Smaller first stacks can also leave the base magazine partly filled. [Supply][ammo-state] · [Capacity][definition]

Creative reloads fill all missing capacity without requiring or consuming reserve ammunition, but firing still spends loaded rounds. The [capacity table below](#magazine-capacity) shows the minimum Survival reload completions for each magazine. [Creative supply][ammo-state] · [Firing][fire-round]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [7.62x39mm Bullet](762x39mmBullet.md) |
| Magazine size | 40 |
| Extended magazine sizes | 50, 60, 75 |
| Fire modes | AUTO, SEMI |
| RPM | 630 |
| Damage | 10 |
| Pellets per shot | 1 |
| Bullet speed | 270 m/s |
| Lifetime | 0.9 seconds |
| Pierce | 2 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

These are the gun definition's values. **Damage 10 is the fallback**; normal hits use the [distance bands below](#damage-falloff). [Definition][definition] · [Damage selection][ballistics]

The listed **270 m/s** is the nominal conversion of **13.5 blocks/tick**, treating one block as one metre at 20 TPS. Lifetime is **18 ticks**. Spread, inherited shooter movement, gravity, friction, water and collisions affect flight; speed × lifetime is not a guaranteed range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

**Pierce 2** is the budget of successful entity-damage events; it does not grant block penetration or guarantee that many distinct targets. An ordinary block hit invokes the block's projectile callback and discards the bullet. **Knockback 0** adds no extra push from this bullet routine; it does not rule out all target movement. [Hit handling][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 4 |
| Moving | 4.55 |
| Sneaking | 3.25 |
| Prone | 2 |
| Aiming down sights | 0.15 |

These are spread inputs: lower values reduce random launch deviation. They are not percentages, guaranteed hit rates or camera recoil. Completed precision aim takes priority, then a non-swimming low pose (the “Prone” row), crouching, moving, and standing. The spread calculation does not apply attachment spread bonuses. The Prone row does not make the Crawl key functional; see [shared controls](../mechanics/TaCZFirearms.md#controls). [Bundled accuracy][accuracy] · [Spread calculation][ballistics] · [Pose selection][pose] · [Random launch][launch]

## Damage Falloff

* 36 blocks: 10
* 64 blocks: 7
* infinite blocks: 6

The entries above are exclusive upper thresholds: **10 below 36 blocks**, **7 from 36 to below 64**, and **6 at 64 or more**. Equality advances to the next band, with no interpolation. Distance is measured straight from the shot origin to the hit. “Infinite” names the last band; the projectile must still survive its lifetime and collisions. [Bundled curve][curve] · [Distance selection][distance]

A headshot multiplies the selected band by **1.5** before the target's normal damage handling, so these values do not guarantee that much health loss. The headshot check requires a living target and a hit at or above its Y position plus 85% of its eye height. [Hit and headshot handling][hit]

## Attachments

Use the [Refit screen](../mechanics/TaCZFirearms.md#refitting-attachments) to install carried attachments. Fit requires both the accepted category and the exact attachment ID below; names or caliber alone do not establish compatibility. RPK accepts Stock attachments, but has no Laser or Grip slot. Follow each linked item's page for its effect scope and limitations; accepted fit does not make every imported stat or effect active. [Accepted IDs][definition] · [Fit checks][fit]

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle, Scope, Stock.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [Scout 4-10x Scope](Scout410xScope.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md), [Vudu 1-6x Scope](Vudu16xScope.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Stock: [AK-12 Regular Stock](AK12RegularStock.md), [Carbon bone C5 Stock](CarbonboneC5Stock.md), [CMMG RipStock Stock](CMMGRipStockStock.md), [Factory Issued Heavy Stock](FactoryIssuedHeavyStock.md), [Factory Issued light Stock](FactoryIssuedlightStock.md), [Factory Issued Tactical Stock](FactoryIssuedTacticalStock.md), [HK Slim Line Stock](HKSlimLineStock.md), [M4SS Stock](M4SSStock.md), [Magpul CTR stock](MagpulCTRstock.md), [Magpul MOE Stock](MagpulMOEStock.md), [Militech B5 Stock](MilitechB5Stock.md), [SBA3 Stock](SBA3Stock.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

### Magazine capacity

Only **Heavy Ammo Extended Mag I, II and III** from the list above fit. Each tier selects a total capacity; tiers do not add together, and installing one supplies no rounds. Reload to fill the added space. [Definition][definition] · [Capacity selection][ammo-state] · [Level lookup][mag-level] · [Attachment storage][refit]

| Installed magazine | Total rounds | Minimum completed Survival reloads from empty |
| --- | --- | --- |
| None (base) | 40 | 1 |
| Heavy I | 50 | 1 |
| Heavy II | 60 | 1 |
| Heavy III | 75 | 2 |

These completion counts assume enough full normal **60-round stacks** and no firing between reloads. A smaller first stack can require more completions. [Reload supply][ammo-state]

**Use excess rounds before downsizing.** Replacing or removing an extended magazine leaves the stored loaded count unchanged initially. The next shot spends one round and then clamps the remainder to the new capacity, losing any excess without a refund. For example, changing a fully loaded Heavy III (75) to Heavy I (50) initially keeps 75 loaded; the next shot leaves 50, spending one and discarding 24 more. [Attachment changes][refit] · [Firing and consumption][fire-round] · [Ammunition clamp][ammo-state]

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
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L60
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L53
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/rpk.json#L1-L36
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/rpk.json#L1-L9
[accuracy]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/rpk_data.json#L131-L137
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/rpk_data.json#L19-L32
[ammo-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/ammo/762x39.json#L1-L7
[logs]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/item/logs.json#L1-L7
[gun-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/rpk_data.json
