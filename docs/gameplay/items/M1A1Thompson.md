# M1A1 Thompson

M1A1 Thompson is a .45 ACP TaCZ submachine gun with AUTO/SEMI fire modes and a 20-round base magazine. Light Ammo Extended Mag I or II raises capacity to 30 rounds; **Light III instead selects only 1 round**. Check the [capacity warning](#attachments) before refitting. [Gun definition][definition] · [Supported modes][modes]

## Obtaining

Craft **one M1A1 Thompson** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in **SMG**, using 30 [Iron Ingots](IronIngot.md) and 5 [Gold Ingots](GoldIngot.md). The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains the crafting controls and material handling. [Recipe][recipe] · [Recipe group][index] · [Active recipe loader][loader] · [Table groups][groups]

It is registered as `minecraft:m1a1`, stacks to 1, and has a Creative Menu entry. A newly crafted gun has no attachments, starts in AUTO, and reads **20 loaded rounds**; the default Creative instance is loaded to that base capacity too. Catalog visibility in the [inventory browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant ordinary Survival items. [Registration][registration] · [Creative entry][creative] · [Crafted output][loader] · [Initial ammunition and mode][stored-ammo] · [Creative ammunition][reserve]

Use [.45 ACP Bullet](45ACPBullet.md), exactly `minecraft:45acp`, for reserve ammunition. Its [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table) recipe is in **Personal Defense Cartridges**: 10 Copper Ingots and 2 Gunpowder produce **30 bullets**. Other cartridges do not reload this gun. [Ammo recipe][ammo-recipe] · [Table groups][groups] · [Exact ammo lookup][reserve]

## Usage

Hold the gun in your **main hand**. With the default [TaCZ controls](../mechanics/TaCZFirearms.md#controls), left mouse shoots, held right mouse aims, **R** reloads, and **Z** opens Refit. [Default keys][keys] · [Input handling][input]

**AUTO → SEMI:** press **G** to cycle these two modes. AUTO repeats while the default left mouse button is held; SEMI requests one shot per mouse press. Keyboard repeat can request further SEMI shots if Shoot is rebound to a keyboard key; see [Firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes). **BURST is not selectable**: the imported burst settings do not add it to the supported mode list. [Imported settings][imported-burst] · [Selected-mode validation][stored-ammo] · [Supported modes][modes] · [Mouse input][input]

Each accepted shot fires **one projectile and consumes one loaded round**, including in Creative. The configured **600 RPM** gives an integer interval of **100 ms**, which the server rounds to a **2-tick trigger cooldown**. RPM is a timing input, not a measured sustained firing rate. [Firing and timing][fire] · [Mode RPM handling][rpm] · [Round consumption][shot] · [Server admission][server]

Reloading takes **55 ticks** (**2.75 seconds at 20 TPS**). The server uses this same duration for empty and partial magazines in either supported mode, with or without an accepted magazine upgrade; imported feed/cooldown or animation timings do not replace it. Keep the gun held until use completes: ammunition is added at completion, not when R is pressed. Existing loaded rounds are retained. [Reload completion][reload] · [Duration selection][reload-timings]

In Survival, one completed reload consumes up to the missing capacity from the **first matching carried ammo stack only**. A small first stack can leave the magazine partly empty even with more reserve elsewhere. Creative fills the missing rounds without reserve items. The displayed loaded count is the entire supply, with no extra chamber round; a full or over-capacity gun cannot begin reloading. See [Magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reserve handling][reserve] · [Reload gate][reload]

## Properties

These are the base gun's values; damage uses the active distance bands below. [Gun definition][definition] · [Ballistic consumer][ballistics]

| Property | Value |
| --- | --- |
| Ammo | [.45 ACP Bullet](45ACPBullet.md) (`minecraft:45acp`) |
| Magazine size | 20 rounds |
| Extended magazine sizes | 30, 30, **1** round with Light I, II, III respectively |
| Fire modes | AUTO, SEMI |
| RPM | 600 (configured input) |
| Damage | 8 (definition fallback; active curve below) |
| Pellets per shot | 1 projectile |
| Bullet speed | 550 m/s (nominal launch conversion) |
| Lifetime | 20 ticks (1 second at 20 TPS) |
| Pierce | 1 successful entity hit |
| Headshot multiplier | 1.5 |
| Knockback | 0 (extra bullet-specific push) |

The launch setting is **27.5 blocks/tick**, yielding the listed **550 m/s** at 20 TPS when one block is treated as one metre. This is an initial setting: spread, inherited movement, drag, gravity and water affect flight, and collisions or the finite lifetime can end it. It is not a constant travel speed or a guaranteed range. [Projectile creation][shot] · [Launch vector][launch] · [Flight and lifetime][flight] · [Default tick rate][tick-rate]

A successful entity hit exhausts the Pierce budget of 1; ordinary blocking collisions discard the projectile. The headshot multiplier is applied to the selected distance damage before the target's own damage handling, so these values do not guarantee health lost. Zero Knockback disables the bullet's extra push; it does not promise that the target can never move. [Entity damage][hits] · [Block collision and push][block-hit]

## Accuracy

These are **random projectile spread inputs**, not accuracy percentages or recoil scores; lower values reduce the launch perturbation. The selected state takes priority in this order: completed aim transition, non-swimming low pose, sneaking, moving, then standing. The Prone entry describes that low-pose state; it does not establish a working Crawl-key action. [Bundled values][accuracy-data] · [Spread selection][pose] · [Aim completion][input] · [Random launch][launch]

| Property | Value |
| --- | --- |
| Standing | 3 |
| Moving | 4 |
| Sneaking | 3 |
| Prone | 2.5 |
| Aiming down sights | 0.15 |

## Damage Falloff

Distance is the straight-line separation from the shot's origin to the hit position. Each finite threshold is an **exclusive upper boundary**: a hit exactly at a boundary uses the next row. These are damage points before headshots and the target's own damage handling. The bundled curve takes precedence over the definition's fallback Damage value. [Bundled curve][curve-data] · [Active curve loader][ballistic-loader] · [Curve selection][ballistics] · [Distance comparison][distance]

| Distance in blocks | Base damage |
| --- | ---: |
| Less than 10 | 8 |
| 10 to less than 25 | 6.5 |
| 25 to less than 50 | 5.5 |
| 50 or farther | 5 |

The final band has no further damage threshold, but still requires a live projectile; it does not mean unlimited range. [Projectile lifetime][flight]

## Attachments

Open **Z** with the gun held and carry the attachment you want to fit. Use the shared [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) guide for installation, replacement and removal. The list below names the exact accepted items; membership in a listed category alone does not ensure compatibility. [Type and ID checks][fit] · [Server refit][server]

**Light I and II each select 30 rounds; Light III selects only 1 round.** These are replacement capacities, not added ammunition. The Light III compatibility/capacity mismatch and related description limits remain tracked in [issue #809](https://github.com/HungLo2020/MattMC/issues/809); no corrected or intended capacity is assumed here. See [Light Ammo Extended Mag III behavior](LightAmmoExtendedMagIII.md#behavior). [Gun definition][definition] · [Attachment levels][tiers] · [Capacity selection][capacity]

**Reducing capacity can lose ammunition.** Installing, replacing or removing a magazine initially preserves the loaded count. The next successful shot subtracts one round and then clamps the remainder to the new capacity, discarding excess without a refund. With 20 loaded rounds, fitting Light III initially leaves 20; the next shot leaves **1**, after firing 1 and losing 18. Use up excess rounds before downsizing. Reload cannot start at or above the selected capacity. Increasing capacity supplies no rounds either; reload to fill the extra space. [Refit storage][refit-storage] · [Shot consumption][shot] · [Ammo clamp][stored-ammo] · [Reload gate][reload]

Supported attachment categories: Extended Mag, Grip, Muzzle.

* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Mirage Silencer](MirageSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [PO-2 "Ptilopsis" Silencer](PO2PtilopsisSilencer.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md), [Wraith Silencer](WraithSilencer.md)
* Grip: [Hera Arms CQR Grip](HeraArmsCQRGrip.md), [Koch Ranger Heavy Grip](KochRangerHeavyGrip.md), [Nagoma Military Standard Grip](NagomaMilitaryStandardGrip.md), [P-2 Grip](P2Grip.md), [RK-0 Grip](RK0Grip.md), [RK-1 B25U Grip](RK1B25UGrip.md), [RK-6 Grip](RK6Grip.md), [SE-5 Express Grip](SE5ExpressGrip.md), [SI Grip](SIGrip.md), [Talon AFG1 Handstop](TalonAFG1Handstop.md), [Talon SG2 Grip](TalonSG2Grip.md), [TD Grip](TDGrip.md)
* Extended Mag: [Light Ammo Extended Mag I](LightAmmoExtendedMagI.md), [Light Ammo Extended Mag II](LightAmmoExtendedMagII.md), [Light Ammo Extended Mag III](LightAmmoExtendedMagIII.md)

## Notes

- This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md).
- The active fit list accepts Light II and III as well as Light I, even though the imported allow-tag lists only Light I from that family. The accepted items above follow the server checks. [Imported tag][raw-allow] · [Gun definition][definition] · [Server refit][server]
- This gun has no accepted Scope slot. An imported gun-specific scope profile does not make that scope installable or establish an active scope bonus. [Gun definition][definition] · [Fit check][fit]
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
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L32
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L30
[tiers]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L139-L141
[capacity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L80
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/m1a1.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/m1a1.json#L1-L9
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/45acp.json#L1-L23
[curve-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m1a1_data.json#L34-L41
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m1a1_data.json#L137-L148
[raw-allow]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tacz_tags/attachments/allow_attachments/m1a1.json#L1-L5
[imported-burst]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m1a1_data.json#L103-L120
