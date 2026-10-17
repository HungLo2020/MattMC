# Springfield 1873 Trapdoor Rifle

The Springfield 1873 Trapdoor Rifle is a single-round TaCZ firearm using .45-70 ammunition. Every shot empties its normal capacity, so its 59-tick reload is part of each firing cycle. [Gun definition][definition] · [Shot consumption][shot] · [Active reload][reload-timing]

## Obtaining

Springfield 1873 Trapdoor Rifle can be obtained from the Creative Menu. It is registered as `minecraft:springfield1873`. [Registration][registration] · [Creative entry][creative]

Craft **one rifle** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in the **Sniper** group. Carry the materials in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench); Creative crafting still requires the materials to be present. [Recipe][gun-recipe] · [Sniper grouping][gun-group] · [Group loading][craft-loader] · [Group names][group-names] · [Material checks][craft-inventory]

| Materials per craft | Output |
| --- | --- |
| 10 items matching `minecraft:logs` · 3 Iron Ingots | 1 × Springfield 1873 Trapdoor Rifle |

The wood ingredient uses the exact `minecraft:logs` item tag, including its bundled burnable-log, crimson-stem and warped-stem groups. A wood-looking item must belong to that tag to count. [Accepted log groups][wood-tag]

A newly crafted rifle starts with **1 loaded round** and no installed attachments. The default Creative item is loaded too. [Craft output][craft-loader] · [Starting ammunition][loaded] · [Attachment storage][refit-storage]

For reserve ammunition, craft [.45-70 Bullet](4570Bullet.md) at the [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table): 30 Copper Ingots, 7 Gunpowder and 5 Lapis Lazuli make **36 rounds** under **Large-Caliber Specialized**. The normal ammunition stack limit is 48. [Ammo recipe][ammo-recipe] · [Table groups][bench-groups] · [Group names][group-names] · [Stack limit][ammo-definition]

## Usage

Hold the rifle in the **main hand**. Default controls are **left mouse to shoot**, **hold right mouse to aim**, **R to reload**, **G for fire mode**, and **Z to refit**. It uses [.45-70 Bullet](4570Bullet.md). See the shared [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for rebinding and control limits. [Input][input] · [Default keys][keys] · [Ammunition definition][definition]

**SEMI is its only fire mode.** Each default mouse click requests one round; holding the mouse button does not repeat fire, and G stays on SEMI. A keyboard Shoot binding can repeat through keyboard-repeat events. [Mode][modes] · [Input][input] · [Mode cycling][cycle-mode] · [Keyboard repeat][keyboard-repeat]

The configured **90 RPM** gives an integer **666 ms** interval and a **13-tick trigger cooldown** (0.65 nominal seconds). The server enforces that cooldown; clicks, reloads, ammunition and game ticking also limit actual firing. This is a configured limit, not a measured sustained rate. [Cooldown calculation][cooldown] · [Server check][server-actions] · [Definition][definition]

### Reload timing

The active reload takes **59 ticks / 2.95 nominal seconds** at 20 ticks per second. This overrides the gun definition's 42-tick fallback and is unchanged by its accepted scope or Ammo Modifier attachments. With one round loaded, the rifle is full and cannot reload; after firing, it needs one round. [Definition][definition] · [Active override][reload-timing] · [Reload checks][reload-use] · [Shot consumption][shot]

Press **R** and keep holding the gun until completion. The round becomes available at the end. Survival consumes one matching .45-70 round from the first matching inventory stack. Creative can refill without carried ammunition, but each shot still spends the loaded round. See the shared [reload rules](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Completion][reload-use] · [Supply][loaded] · [Shot consumption][shot]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.45-70 Bullet](4570Bullet.md) |
| Magazine size | 1 |
| Extended magazine sizes | 1, 1, 1 |
| Fire modes | SEMI |
| RPM | 90 |
| Damage | 35 |
| Pellets per shot | 1 |
| Bullet speed | 233 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0.25 |

[Gun values][definition] · [Mode][modes] · [Projectile construction][shot]

The **1, 1, 1** extended-magazine row is declared reference data only. Normal refitting accepts no Extended Mag attachment, so its reachable capacity stays **1**. [Accepted attachments][definition] · [Compatibility checks][fit]

The **Damage 35** row is the definition fallback. The bundled distance curve below supplies the active damage before the **1.5×** headshot multiplier and the target's own damage handling. [Fallback][definition] · [Curve selection][ballistics] · [Hit handling][hit]

**Bullet speed 233 m/s** is the nominal conversion of the **11.65-block-per-tick** launch setting at 20 ticks per second, treating a block as one metre. **Lifetime 0.8 seconds** represents the configured **16-tick** projectile lifetime. Spread and shooter motion affect launch; gravity, drag, water and collisions affect flight. These values do not guarantee a fixed travel distance or wall-clock speed. [Definition][definition] · [Shot][shot] · [Launch motion][launch] · [Flight and lifetime][flight] · [Tick rate][ticks]

**Pierce** counts the projectile's successful entity-damage budget; it is not a number of blocks penetrated or a guarantee of that many distinct enemies hit. Ordinary block impact discards the bullet. **Knockback 0.25** is the extra horizontal bullet push after a successful hit, with a **0.03** upward component when the projectile has usable horizontal motion; it is separate from other target movement. [Entity and block hits][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 3.25 |
| Moving | 3.5 |
| Sneaking | 1.75 |
| Prone | 1 |
| Aiming down sights | 0.21 |

These are spread inputs: **lower means less random launch spread**, not a hit percentage or camera-recoil amount. The aimed value applies after the aim transition completes. Selection then falls back through non-swimming low pose, sneaking, moving and standing. “Prone” is the low-pose value; it does not establish a working Crawl key. See the shared [control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][spread] · [Active loading][loader] · [Selection][pose] · [Aim transition][aim] · [Launch spread][launch]

## Damage Falloff

* 40 blocks: 35
* 64 blocks: 28
* 88 blocks: 19
* infinite blocks: 16

This means **35 below 40 blocks**, **28 from 40 to below 64**, **19 from 64 to below 88**, and **16 at 88 or more**. Each finite entry is an **exclusive upper boundary**: a hit exactly at that distance uses the next band. Distance is measured straight from the shot origin to the hit point, with no interpolation. “Infinite” names the final damage band while the bullet still exists; it does not grant unlimited flight range. [Bundled curve][curve] · [Curve consumer][ballistics] · [Distance selection][distance] · [Lifetime][flight]

## Attachments

Supported attachment categories: Ammo Modifier, Scope.

* Scope: [Vintage Springfield Scope](VintageSpringfieldScope.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

Only the four listed items fit. There is no Extended Mag, Muzzle, Grip, Laser or Stock slot; the Vintage Springfield Scope is the only accepted Scope. The Ammo Modifier items are installed attachments, not the cartridges used to reload. Their individual pages explain the limits of their imported effect descriptions. [Exact fit list][definition] · [Compatibility gate][fit]

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
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L66
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L59
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/springfield1873.json#L1-L21
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/springfield1873.json#L1-L8
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/45_70.json#L1-L29
[spread]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/springfield1873_data.json#L173-L179
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/springfield1873_data.json#L19-L36
[group-names]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8552
[wood-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/item/logs.json#L1-L7
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L86
