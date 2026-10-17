# Mauser Kar98k Rifle

The Mauser Kar98k Rifle is a four-round TaCZ firearm using 8mm Mauser ammunition. Its only accepted optic is the Mauser 4x Light Sight, and installing it changes the empty reload time. [Gun definition][definition] · [Reload timing][reload-timing]

## Obtaining

Mauser Kar98k Rifle can be obtained from the Creative Menu. It is registered as `minecraft:kar98`. [Registration][registration] · [Creative entry][creative]

Craft **one rifle** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in the **Sniper** group. Carry the materials in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench); Creative crafting still requires the materials to be present. [Recipe][gun-recipe] · [Sniper grouping][gun-group] · [Group loading][craft-loader] · [Group names][group-names] · [Material checks][craft-inventory]

| Materials per craft | Output |
| --- | --- |
| 40 items matching `minecraft:logs` · 28 Iron Ingots · 4 Gold Ingots · 12 Lapis Lazuli | 1 × Mauser Kar98k Rifle |

The wood ingredient uses the exact `minecraft:logs` item tag, including its bundled burnable-log, crimson-stem and warped-stem groups. A wood-looking item must belong to that tag to count. [Accepted log groups][wood-tag]

A newly crafted rifle starts with **4 loaded rounds** and no installed attachments. The default Creative item is loaded too. [Craft output][craft-loader] · [Starting ammunition][loaded] · [Attachment storage][refit-storage]

For reserve ammunition, craft [8mm Mauser Bullet](8mmMauserBullet.md) at the [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table): 20 Copper Ingots and 6 Gunpowder make **48 rounds** under **Intermediate & Full-Power Rifle Cartridges**. The normal ammunition stack limit is 36, so leave room for more than one stack. [Ammo recipe][ammo-recipe] · [Table groups][bench-groups] · [Group names][group-names] · [Stack limit][ammo-definition]

## Usage

Hold the rifle in the **main hand**. Default controls are **left mouse to shoot**, **hold right mouse to aim**, **R to reload**, **G for fire mode**, and **Z to refit**. It uses [8mm Mauser Bullet](8mmMauserBullet.md). See the shared [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for rebinding and control limits. [Input][input] · [Default keys][keys] · [Ammunition definition][definition]

**SEMI is its only fire mode.** Each default mouse click requests one round; holding the mouse button does not repeat fire, and G stays on SEMI. A keyboard Shoot binding can repeat through keyboard-repeat events. [Mode][modes] · [Input][input] · [Mode cycling][cycle-mode] · [Keyboard repeat][keyboard-repeat]

The configured **250 RPM** gives an integer **240 ms** interval and a **5-tick trigger cooldown** (0.25 nominal seconds). The server enforces that cooldown; clicks, reloads, ammunition and game ticking also limit actual firing. This is a configured limit, not a measured sustained rate. [Cooldown calculation][cooldown] · [Server check][server-actions] · [Definition][definition]

### Reload timing

The active reload depends on the loaded count **when the reload starts** and whether a scope is installed. These durations replace the definition's 70-tick fallback. Seconds assume 20 ticks per second. [Timing and scope check][reload-timing] · [Rounding][reload-rounding] · [Duration captured at start][use-start]

| Loaded rounds at start | Installed scope | Missing rounds | Reload duration |
| ---: | --- | ---: | --- |
| 0 | None | 4 | 69 ticks / 3.45 seconds |
| 0 | Mauser 4x Light Sight | 4 | 106 ticks / 5.30 seconds |
| 1 | Either | 3 | 76 ticks / 3.80 seconds |
| 2 | Either | 2 | 62 ticks / 3.10 seconds |
| 3 | Either | 1 | 48 ticks / 2.40 seconds |
| 4 | Either | 0 | Cannot start a reload |

**Fitting the Mauser sight makes an empty reload longer.** The partial-reload timings are the same with or without it. The timing counts missing magazine spaces, not available reserve rounds: an empty scoped rifle with only one carried round still takes 106 ticks and finishes with one loaded round. [Timing][reload-timing] · [Ammunition supply][loaded]

Press **R** and keep holding the gun until completion. All supplied rounds arrive at the end; the loading animation does not add usable rounds one at a time. Already-loaded rounds stay loaded. Survival draws only from the first matching ammunition stack, up to the missing capacity, and no separate clip item is needed. Creative can refill without carried ammunition, but shots still spend loaded rounds. See the shared [reload rules](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reload completion][reload-use] · [Supply and consumption][loaded] · [Shot consumption][shot]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [8mm Mauser Bullet](8mmMauserBullet.md) |
| Magazine size | 4 |
| Extended magazine sizes | 4, 4, 4 |
| Fire modes | SEMI |
| RPM | 250 |
| Damage | 26 |
| Pellets per shot | 1 |
| Bullet speed | 340 m/s |
| Lifetime | 0.9 seconds |
| Pierce | 2 |
| Headshot multiplier | 1.85 |
| Knockback | 0 |

[Gun values][definition] · [Mode][modes] · [Projectile construction][shot]

The **4, 4, 4** extended-magazine row is declared reference data only. Normal refitting accepts no Extended Mag attachment, so its reachable capacity stays **4**. [Accepted attachments][definition] · [Compatibility checks][fit]

The **Damage 26** row is the definition fallback. The bundled distance curve below supplies the active damage before the **1.85×** headshot multiplier and the target's own damage handling. [Fallback][definition] · [Curve selection][ballistics] · [Hit handling][hit]

**Bullet speed 340 m/s** is the nominal conversion of the **17-block-per-tick** launch setting at 20 ticks per second, treating a block as one metre. **Lifetime 0.9 seconds** represents the configured **18-tick** projectile lifetime. Spread and shooter motion affect launch; gravity, drag, water and collisions affect flight. These values do not guarantee a fixed travel distance or wall-clock speed. [Definition][definition] · [Shot][shot] · [Launch motion][launch] · [Flight and lifetime][flight] · [Tick rate][ticks]

**Pierce** counts the projectile's successful entity-damage budget; it is not a number of blocks penetrated or a guarantee of that many distinct enemies hit. Ordinary block impact discards the bullet. **Knockback 0** means no extra bullet-specific push; it does not rule out other causes of target movement. [Entity and block hits][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 6 |
| Moving | 6.5 |
| Sneaking | 3.5 |
| Prone | 2.5 |
| Aiming down sights | 0.1 |

These are spread inputs: **lower means less random launch spread**, not a hit percentage or camera-recoil amount. The aimed value applies after the aim transition completes. Selection then falls back through non-swimming low pose, sneaking, moving and standing. “Prone” is the low-pose value; it does not establish a working Crawl key. See the shared [control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][spread] · [Active loading][loader] · [Selection][pose] · [Aim transition][aim] · [Launch spread][launch]

## Damage Falloff

* 40 blocks: 26
* 80 blocks: 22
* infinite blocks: 15

This means **26 below 40 blocks**, **22 from 40 to below 80**, and **15 at 80 or more**. Each finite entry is an **exclusive upper boundary**: a hit exactly at that distance uses the next band. Distance is measured straight from the shot origin to the hit point, with no interpolation. “Infinite” names the final damage band while the bullet still exists; it does not grant unlimited flight range. [Bundled curve][curve] · [Curve consumer][ballistics] · [Distance selection][distance] · [Lifetime][flight]

## Attachments

Supported attachment categories: Ammo Modifier, Scope.

* Scope: [Mauser 4x Light Sight](Mauser4xLightSight.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

Only the four listed items fit. There is no Extended Mag, Muzzle, Grip, Laser or Stock slot; the Mauser sight is the only accepted Scope. The Ammo Modifier items are installed attachments, not the cartridges used to reload. Their individual pages explain the limits of their imported effect descriptions. [Exact fit list][definition] · [Compatibility gate][fit]

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
[reload-rounding]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L90-L92
[use-start]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3195-L3206
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
[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L29
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L26
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/kar98.json#L1-L33
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/kar98.json#L1-L8
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/792x57.json#L1-L23
[spread]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/kar98_data.json#L163-L169
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/kar98_data.json#L19-L32
[group-names]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8552
[wood-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/item/logs.json#L1-L7
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L101
