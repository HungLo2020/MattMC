# UZI

UZI is a 9mm TaCZ submachine gun with AUTO as its only fire mode. It starts with a 20-round magazine and accepts Light Ammo Extended Mags I, II and III for 32, 40 and 50 rounds. [Gun definition][definition] · [Supported modes][modes]

## Obtaining

Craft **one UZI** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in **SMG**, using 32 [Iron Ingots](IronIngot.md). The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains the crafting controls and material handling. [Recipe][recipe] · [Recipe group][index] · [Active recipe loader][loader] · [Table groups][groups]

It is registered as `minecraft:uzi`, stacks to 1, and has a Creative Menu entry. A newly crafted gun has no attachments, starts in AUTO, and reads **20 loaded rounds**; the default Creative instance is loaded to that base capacity too. Catalog visibility in the [inventory browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant ordinary Survival items. [Registration][registration] · [Creative entry][creative] · [Crafted output][loader] · [Initial ammunition and mode][stored-ammo] · [Creative ammunition][reserve]

Use [9mm Bullet](9mmBullet.md), exactly `minecraft:9mm`, for reserve ammunition. Its [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table) recipe is in **Personal Defense Cartridges**: 10 Copper Ingots and 2 Gunpowder produce **50 bullets**. Other cartridges do not reload this gun. [Ammo recipe][ammo-recipe] · [Table groups][groups] · [Exact ammo lookup][reserve]

## Usage

Hold the gun in your **main hand**. With the default [TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, and **Z** opens Refit. [Default keys][keys] · [Input handling][input]

**AUTO only:** hold the default left mouse button to keep requesting shots. **G** leaves AUTO selected; there is no selectable SEMI or BURST mode. [Supported modes][modes] · [Mouse input][input]

Each accepted shot fires **one projectile and consumes one loaded round**, including in Creative. The configured **600 RPM** gives an integer interval of **100 ms**, which the server rounds to a **2-tick trigger cooldown**. RPM is a timing input, not a measured sustained firing rate. [Firing and timing][fire] · [Mode RPM handling][rpm] · [Round consumption][shot] · [Server admission][server]

Reloading takes **39 ticks** (**1.95 seconds at 20 TPS**). The server uses this same duration for empty and partial magazines, with or without an accepted magazine upgrade; imported feed/cooldown or animation timings do not replace it. Keep the gun held until use completes: ammunition is added at completion, not when R is pressed. Existing loaded rounds are retained. [Reload completion][reload] · [Duration selection][reload-timings]

In Survival, one completed reload consumes up to the missing capacity from the **first matching carried ammo stack only**. A small first stack can leave the magazine partly empty even with more reserve elsewhere. Creative fills the missing rounds without reserve items. The displayed loaded count is the entire supply, with no extra chamber round; a full or over-capacity gun cannot begin reloading. See [Magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reserve handling][reserve] · [Reload gate][reload]

## Properties

These are the base gun's values; damage uses the active distance bands below. [Gun definition][definition] · [Ballistic consumer][ballistics]

| Property | Value |
| --- | --- |
| Ammo | [9mm Bullet](9mmBullet.md) (`minecraft:9mm`) |
| Magazine size | 20 rounds |
| Extended magazine sizes | 32, 40, 50 rounds with Light I, II, III respectively |
| Fire modes | AUTO |
| RPM | 600 (configured input) |
| Damage | 6.5 (definition fallback; active curve below) |
| Pellets per shot | 1 projectile |
| Bullet speed | 180 m/s (nominal launch conversion) |
| Lifetime | 13 ticks (0.65 seconds at 20 TPS) |
| Pierce | 1 successful entity hit |
| Headshot multiplier | 1.25 |
| Knockback | 0 (extra bullet-specific push) |

The launch setting is **9 blocks/tick**, yielding the listed **180 m/s** at 20 TPS when one block is treated as one metre. This is an initial setting: spread, inherited movement, drag, gravity and water affect flight, and collisions or the finite lifetime can end it. It is not a constant travel speed or a guaranteed range. [Projectile creation][shot] · [Launch vector][launch] · [Flight and lifetime][flight] · [Default tick rate][tick-rate]

A successful entity hit exhausts the Pierce budget of 1; ordinary blocking collisions discard the projectile. The headshot multiplier is applied to the selected distance damage before the target's own damage handling, so these values do not guarantee health lost. Zero Knockback disables the bullet's extra push; it does not promise that the target can never move. [Entity damage][hits] · [Block collision and push][block-hit]

## Accuracy

These are **random projectile spread inputs**, not accuracy percentages or recoil scores; lower values reduce the launch perturbation. The selected state takes priority in this order: completed aim transition, non-swimming low pose, sneaking, moving, then standing. The Prone entry describes that low-pose state; it does not establish a working Crawl-key action. [Bundled values][accuracy-data] · [Spread selection][pose] · [Aim completion][input] · [Random launch][launch]

| Property | Value |
| --- | --- |
| Standing | 3.5 |
| Moving | 4 |
| Sneaking | 2 |
| Prone | 1 |
| Aiming down sights | 0.22 |

## Damage Falloff

Distance is the straight-line separation from the shot's origin to the hit position. Each finite threshold is an **exclusive upper boundary**: a hit exactly at a boundary uses the next row. These are damage points before headshots and the target's own damage handling. The bundled curve takes precedence over the definition's fallback Damage value. [Bundled curve][curve-data] · [Active curve loader][ballistic-loader] · [Curve selection][ballistics] · [Distance comparison][distance]

| Distance in blocks | Base damage |
| --- | ---: |
| Less than 24 | 6.5 |
| 24 to less than 50 | 5.5 |
| 50 or farther | 4 |

The final band has no further damage threshold, but still requires a live projectile; it does not mean unlimited range. [Projectile lifetime][flight]

## Attachments

Open **Z** with the gun held and carry the attachment you want to fit. Use the shared [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) guide for installation, replacement and removal. The list below names the exact accepted items; membership in a listed category alone does not ensure compatibility. [Type and ID checks][fit] · [Server refit][server]

Light I, II and III select **32, 40 and 50 rounds** respectively, replacing the base capacity of 20. Installing a larger magazine preserves the loaded count; reload with 9mm ammunition to fill the added space. Tiers do not stack or supply ammunition. [Capacity lookup][stored-ammo] · [Attachment levels][tiers] · [Capacity selection][capacity] · [Refit storage][refit-storage]

Before removing a magazine or switching to a smaller tier, use up rounds above the new capacity. Refitting initially leaves the loaded count unchanged; the next successful shot subtracts one and clamps the remainder to the new capacity, losing any excess without a refund. A gun at or above its capacity cannot start a reload. [Shot consumption][shot] · [Stored ammunition][stored-ammo] · [Reload gate][reload]

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Mirage Silencer](MirageSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [PO-2 "Ptilopsis" Silencer](PO2PtilopsisSilencer.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md), [Wraith Silencer](WraithSilencer.md)
* Extended Mag: [Light Ammo Extended Mag I](LightAmmoExtendedMagI.md), [Light Ammo Extended Mag II](LightAmmoExtendedMagII.md), [Light Ammo Extended Mag III](LightAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

## Notes

- This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md).
- Ammo Modifier attachments occupy their own slot and do not replace `minecraft:9mm` reserve ammunition. Their names and imported effect fields alone do not establish extra damage, armor penetration or ignition; see the individual attachment pages and [shared limitations](../mechanics/TaCZFirearms.md#current-limitations). [Exact ammo lookup][reserve]
- Attachment acceptance does not by itself verify every imported effect or visual feature. Follow the linked attachment owners for their documented behavior.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The registered item, bundled recipe and data, active crafting, input, firing, reload, projectile and refit consumers were checked. This is **source review**, not an in-game crafting, timing, reload, combat or attachment-rendering test.

[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2722
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L185
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L20
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[fire]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L79-L123
[shot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L32
[reserve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L305
[stored-ammo]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L348
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L80
[ballistic-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L155-L222
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[hits]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L163-L204
[block-hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L206-L250
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[rpm]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L5-L22
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L72
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L67
[tiers]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L139-L141
[capacity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L80
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/uzi.json#L1-L15
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/uzi.json#L1-L8
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/9mm.json#L1-L23
[curve-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/uzi_data.json#L12-L25
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/uzi_data.json#L126-L132
