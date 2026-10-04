# M107 Sniper Rifle

The M107 is a SEMI-only TaCZ rifle using .50 BMG. Its ten-round base magazine can reach 20 rounds with Sniper Ammo Extended Mag III, but each reload still takes 86 game ticks. [Gun definition] · [Fire mode][mode] · [Reload timing][reload-timing]

## Obtaining

Craft **one M107 Sniper Rifle** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in **Sniper**, with these materials in your inventory. [Recipe][recipe] · [Recipe group][group] · [Group and output loading][loader]

| Material | Amount |
| --- | ---: |
| Diamonds | 18 |
| Gold Ingots | 64 |
| Netherite Ingots | 3 |
| Iron Ingots | 320 |
| Blaze Rods | 5 |

Follow [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the craft control, material consumption and Creative-mode requirements. The gun also appears in the Creative Menu and is registered as `minecraft:m107`. [Registration][registration] · [Creative entries][creative]

**The crafted gun starts loaded with 10 rounds and has no scope installed.** Its recipe lists a Contender scope preset, but the active output loader does not apply attachment presets. Acquire a compatible scope separately and install it through Refit; the gun can fire without one. Default Creative guns also start with 10 loaded rounds. [Output loader][loader] · [Initial Creative ammunition][initial-ammo] · [Unsaved ammunition default][magazine] · [Firing checks][fire-gate]

## Usage

The M107 uses [.50 BMG](50BMG.md). Hold the gun in your **main hand**: click **Shoot** (left mouse), hold **Aim** (right mouse), and press **R** to reload by default. See [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for rebinding and shared control limits. [Bindings][keys] · [Active input][input]

**SEMI is the only mode**; pressing **G** stays on SEMI. Each click of the default mouse Shoot binding requests one round, and holding it does not repeat. Its configured **400 RPM** produces a **3-tick trigger cooldown** (150 ms before tick rounding). This is a cooldown setting, not measured sustained fire: ammunition, clicks and game ticking still matter. See [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes), including the keyboard-repeat caveat. [Mode][mode] · [Cycling][cycle] · [Input][input] · [Cooldown calculation][cooldown] · [Server gate][server]

A reload completes after **86 game ticks**, or **4.30 seconds at 20 TPS**. This duration is the same for empty and partial reloads and for all accepted magazine tiers; it is the server's reload duration, not an imported animation or feed time. Keep holding the gun until completion. [Gun definition] · [Timing selection][reload-timing] · [Reload completion][reload-use]

Carry the matching cartridge in Survival. A completed reload adds rounds from the **first matching inventory stack only**, so a small first stack can leave space even when total reserve is sufficient. Follow [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) for combining stacks, Creative supply and completion rules. Firing still spends one loaded round in Creative. [Reload supply][reload-supply] · [Firing consumption][shot]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.50 BMG](50BMG.md) |
| Magazine size | 10 |
| Extended magazine sizes | 12, 14, 20 |
| Fire modes | SEMI |
| RPM | 400 |
| Damage | 55 |
| Pellets per shot | 1 |
| Bullet speed | 400 m/s |
| Lifetime | 1.1 seconds |
| Pierce | 5 |
| Headshot multiplier | 1.5 |
| Knockback | 0 |

[Gun definition] · [Fire mode][mode] · [Shot construction][shot]

**Damage 55 is the definition fallback, not the active close-range value of 50.** The bundled distance curve below takes priority. The headshot multiplier applies to the selected distance-band value before the target handles damage, so these values do not promise a particular health loss or a one-shot kill. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Active curve][ballistics] · [Bundled bands][damage-data] · [Hit handling][hit]

The **400 m/s** speed is the nominal **20-blocks-per-tick** launch setting multiplied by 20, treating one block as one metre. The listed **1.1-second** lifetime is **22 game ticks** at 20 TPS. Neither gives a guaranteed range: launch spread, shooter motion, drag, gravity and collisions affect flight. [Gun definition] · [Launch motion][launch] · [Flight and lifetime][flight]

**Pierce 5** is a budget of successful entity-damage events, not block penetration or a guarantee of hitting that many different enemies. An ordinary block collision removes the bullet. **Knockback 0** means no extra bullet-specific push; it does not rule out other target movement. [Hit budget, collisions and extra push][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 5 |
| Moving | 5.5 |
| Sneaking | 3.5 |
| Prone | 2.5 |
| Aiming down sights | 0.05 |

These are spread inputs: lower values mean less random launch perturbation, not a hit percentage or camera-recoil measurement. Completed aiming selects **0.05**; otherwise the selection order is non-swimming low pose, sneaking, moving, then standing. The **Prone** row describes that low-pose input, not a working Crawl key; see [controls](../mechanics/TaCZFirearms.md#controls). [Gun spread data][accuracy-data] · [Spread calculation][ballistics] · [Pose selection][pose] · [Aim completion][input] · [Launch perturbation][launch]

## Damage Falloff

* 65 blocks: 50
* 120 blocks: 40
* infinite blocks: 35

Read these as **exclusive upper thresholds**: 50 damage below 65 blocks, 40 from 65 to below 120, and 35 at 120 or more, before headshot multiplication and target handling. Distance is measured straight from the shot origin to the hit location; equality advances to the next band. The final “infinite” entry only applies while the projectile survives. [Bundled bands][damage-data] · [Data loading][ballistics-loader] · [Distance selection][distance] · [Hit handling][hit] · [Lifetime][flight]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Contender 4x Scope](Contender4xScope.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [Scout 4-10x Scope](Scout410xScope.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md), [Vudu 1-6x Scope](Vudu16xScope.md)
* Muzzle: [Vulture .50 Cal Suppressor](Vulture50CalSuppressor.md)
* Extended Mag: [Sniper Ammo Extended Mag I](SniperAmmoExtendedMagI.md), [Sniper Ammo Extended Mag II](SniperAmmoExtendedMagII.md), [Sniper Ammo Extended Mag III](SniperAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

These lists require both the accepted category and the exact registered item. Vulture .50 Cal Suppressor is the only accepted muzzle. No Grip, Laser or Stock category is accepted. Use **Z** by default and follow [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for installing, replacing and unloading. [Gun definition] · [Live fit check][fit]

Sniper Ammo Extended Mag **I / II / III** select total capacities of **12 / 14 / 20 rounds**, respectively, in place of the base **10**. Only one tier occupies the slot; tiers do not stack and installation adds no ammunition. Reload to use the new space. The linked magazine pages give their recipes and explain the loose attachment's generic capacity tooltip. [Registered tiers][mag-levels] · [Capacity lookup][magazine] · [Tier selection][capacity-level] · [Attachment storage][storage]

**Use up excess loaded rounds before downsizing.** An M107 loaded to 20 with tier III, then changed to tier I (capacity 12), still stores 20 initially; the next shot leaves 12, firing one round and losing seven. Reload cannot start while the stored count is at or above the new capacity. The shared [refitting guide](../mechanics/TaCZFirearms.md#refitting-attachments) explains this loss and how to avoid it. [Refit storage][storage] · [Ammunition clamp][magazine] · [Next shot][shot] · [Reload gate][reload-use]

Compatibility does not establish every effect advertised by an attachment's imported profile. Consult the linked optic, muzzle and Ammo Modifier pages before choosing one; those pages cover their active behavior and current limits. Ammo Modifiers are installed attachments, not replacement reload cartridges. [Matching ammunition][reload-supply] · [Shot inputs][shot]

## Notes

* This item is part of the integrated TaCZ firearms system.

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The recipe/output loader, gun and attachment definitions, firing controls, reload completion, damage curve and projectile consumers were checked. This is **source review only**; no in-game crafting, reload, combat, optic-rendering or multiplayer test was run. Imported scope presets, bolt timings, armor-ignore values and explosion fields are not treated as proof of active gameplay behavior.

[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2713
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L169
[initial-ammo]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L258
[magazine]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L330
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[cycle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L333-L348
[cooldown]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L129
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2210-L2224
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L30
[reload-use]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-supply]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[shot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L250
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L76
[ballistics-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L158-L222
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[mag-levels]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L196-L198
[capacity-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L80
[Gun definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L41
[mode]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L32
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/m107.json#L1-L42
[group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/m107.json#L1-L8
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m107_data.json#L170-L176
[damage-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m107_data.json#L19-L32
[fire-gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L98
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
