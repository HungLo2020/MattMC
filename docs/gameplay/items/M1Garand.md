# M1 Garand

The M1 Garand is an eight-round, SEMI-only TaCZ firearm using .30-06 Springfield ammunition. It accepts the eight muzzles listed below and has no scope or extended-magazine slot. [Gun definition][definition] · [Supported mode][modes]

## Obtaining

M1 Garand can be obtained from the Creative Menu. It is registered as `minecraft:m1_garand`. [Registration][registration] · [Creative entry][creative]

Craft **one rifle** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in the **Sniper** group. Carry the materials in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench); Creative crafting still requires the materials to be present. [Recipe][gun-recipe] · [Sniper grouping][gun-group] · [Group loading][craft-loader] · [Group names][group-names] · [Material checks][craft-inventory]

| Materials per craft | Output |
| --- | --- |
| 32 Iron Ingots · 2 items matching `minecraft:planks` | 1 × M1 Garand |

The wood ingredient uses the exact `minecraft:planks` item tag. Use a listed plank item; a material merely resembling wood does not establish a match. [Accepted planks][wood-tag]

A newly crafted rifle starts with **8 loaded rounds** and no installed attachments. The default Creative item is loaded too. [Craft output][craft-loader] · [Starting ammunition][loaded] · [Attachment storage][refit-storage]

For reserve ammunition, craft [.30-06 Springfield Bullet](3006SpringfieldBullet.md) at the [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table): 20 Copper Ingots and 6 Gunpowder make **32 rounds** under **Large-Caliber Specialized**. The normal ammunition stack limit is 36. [Ammo recipe][ammo-recipe] · [Table groups][bench-groups] · [Group names][group-names] · [Stack limit][ammo-definition]

## Usage

Hold the rifle in the **main hand**. Default controls are **left mouse to shoot**, **hold right mouse to aim**, **R to reload**, **G for fire mode**, and **Z to refit**. It uses [.30-06 Springfield Bullet](3006SpringfieldBullet.md). See the shared [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for rebinding and control limits. [Input][input] · [Default keys][keys] · [Ammunition definition][definition]

**SEMI is its only fire mode.** Each default mouse click requests one round; holding the mouse button does not repeat fire, and G stays on SEMI. A keyboard Shoot binding can repeat through keyboard-repeat events. [Mode][modes] · [Input][input] · [Mode cycling][cycle-mode] · [Keyboard repeat][keyboard-repeat]

The configured **165 RPM** gives an integer **363 ms** interval and a **7-tick trigger cooldown** (0.35 nominal seconds). The server enforces that cooldown; clicks, reloads, ammunition and game ticking also limit actual firing. This is a configured limit, not a measured sustained rate. [Cooldown calculation][cooldown] · [Server check][server-actions] · [Definition][definition]

### Reload timing

Reloading takes **69 ticks / 3.45 nominal seconds** at 20 ticks per second, whether the eight-round magazine is empty or partially loaded. The active completion time comes from this gun's fixed reload duration; the client reload animation's length is a separate value. [Definition][definition] · [Duration selection][reload-timing] · [Completion][reload-use] · [Client animation][reload-animation]

Press **R** and keep holding the gun until completion. Already-loaded rounds stay loaded, and new rounds arrive at the end. Survival draws only from the first matching ammunition stack, up to the missing capacity: an empty Garand with a first stack of three rounds loads three even if another stack holds more. Combine small stacks or reload again. Creative can refill without carried ammunition, but shots still spend loaded rounds. A full magazine cannot start a reload. See the shared [reload rules](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reload checks and completion][reload-use] · [Supply][loaded] · [Shot consumption][shot]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.30-06 Springfield Bullet](3006SpringfieldBullet.md) |
| Magazine size | 8 |
| Extended magazine sizes | None |
| Fire modes | SEMI |
| RPM | 165 |
| Damage | 38 |
| Pellets per shot | 1 |
| Bullet speed | 470 m/s |
| Lifetime | 4.8 seconds |
| Pierce | 4 |
| Headshot multiplier | 2.2 |
| Knockback | 0 |

[Gun values][definition] · [Mode][modes] · [Projectile construction][shot]

Capacity stays **8** through normal refitting: the Garand accepts no Extended Mag attachment. [Accepted attachments][definition] · [Compatibility checks][fit]

**Damage 38 is the definition fallback, while the active near-distance damage is 42.** The bundled curve below takes priority over the fallback. Its selected value is multiplied by **2.2** for a headshot before the target's own damage handling; neither 38 nor 42 is a guaranteed amount of health removed. [Fallback][definition] · [Curve selection][ballistics] · [Hit handling][hit]

**Bullet speed 470 m/s** is the nominal conversion of the **23.5-block-per-tick** launch setting at 20 ticks per second, treating a block as one metre. **Lifetime 4.8 seconds** represents the configured **96-tick** projectile lifetime. Spread and shooter motion affect launch; gravity, drag, water and collisions affect flight. These values do not guarantee a fixed travel distance or wall-clock speed. [Definition][definition] · [Shot][shot] · [Launch motion][launch] · [Flight and lifetime][flight] · [Tick rate][ticks]

**Pierce** counts the projectile's successful entity-damage budget; it is not a number of blocks penetrated or a guarantee of that many distinct enemies hit. Ordinary block impact discards the bullet. **Knockback 0** means no extra bullet-specific push; it does not rule out other causes of target movement. [Entity and block hits][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 8.5 |
| Moving | 11 |
| Sneaking | 6.5 |
| Prone | 4.5 |
| Aiming down sights | 0.05 |

These are spread inputs: **lower means less random launch spread**, not a hit percentage or camera-recoil amount. The aimed value applies after the aim transition completes. Selection then falls back through non-swimming low pose, sneaking, moving and standing. “Prone” is the low-pose value; it does not establish a working Crawl key. See the shared [control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][spread] · [Active loading][loader] · [Selection][pose] · [Aim transition][aim] · [Launch spread][launch]

## Damage Falloff

* 80 blocks: 42
* 160 blocks: 36
* infinite blocks: 26

This means **42 below 80 blocks**, **36 from 80 to below 160**, and **26 at 160 or more**. Each finite entry is an **exclusive upper boundary**: a hit exactly at that distance uses the next band. Distance is measured straight from the shot origin to the hit point, with no interpolation. “Infinite” names the final damage band while the bullet still exists; it does not grant unlimited flight range. [Bundled curve][curve] · [Curve consumer][ballistics] · [Distance selection][distance] · [Lifetime][flight]

## Attachments

Supported attachment categories: Muzzle.

* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)

Only the eight listed muzzle items fit. There is no Scope, Extended Mag, Ammo Modifier, Grip, Laser or Stock slot, even if another gun of the same caliber accepts one. Consult each muzzle's page for the supported effects and limitations; a silencer name alone does not establish a quieter firing sound. [Exact fit list][definition] · [Compatibility gate][fit]

Make attachment items at the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), then use the [shared refitting procedure](../mechanics/TaCZFirearms.md#refitting-attachments). The lists above describe accepted installations; they do not promise every effect described in imported attachment data.

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, initial loaded state, controls, reload timing, accepted attachments and the active projectile paths were checked against the integrated sources. This is source review, not an in-game crafting, reload, firing, attachment-rendering or multiplayer test.

[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2744
[craft-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[craft-inventory]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[bench-groups]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L185
[loaded]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L331
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[keyboard-repeat]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L560
[cycle-mode]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[cooldown]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L132
[server-actions]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[reload-use]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L39
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L16-L82
[shot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[ticks]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L151-L222
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[aim]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L37
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L29
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/m1_garand.json#L1-L21
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/m1_garand.json#L1-L7
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/30_06.json#L1-L23
[spread]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m1_garand_data.json#L59-L65
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m1_garand_data.json#L13-L17
[group-names]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8552
[wood-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/item/planks.json#L1-L16
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L81
[reload-animation]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGunAnimationTimings.java#L37
