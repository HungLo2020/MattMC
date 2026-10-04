# Ray Gun

Ray Gun is a 20-round, automatic TaCZ firearm with a steep, stepped damage curve. Its current shots deal direct-hit damage; the imported explosion and ignition settings do not add those effects to the reviewed firing path. [Definition][definition] · [Damage selection][damage-curve] · [Shot creation][firing] · [Hit behavior][hit]

## Obtaining

Craft **one Ray Gun** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Pistol**. Carry these materials in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Group][gun-group] · [Group selection][recipe-loader] · [Table groups][bench-groups] · [Names][group-names]

| Materials per craft | Output |
| --- | --- |
| 78 Iron Ingots · 40 Diamonds · 32 Redstone Dust · 1 Nether Star · 60 Gold Ingots | 1 × Ray Gun |

Ray Gun can be obtained from the Creative Menu. It is registered as `minecraft:raygun_bo6`. It stacks to one, and both a newly crafted gun and its default Creative instance start with **20 loaded rounds**, without installed attachments. [Registration][registration] · [Creative entry][creative] · [Recipe output][recipe-loader] · [Initial ammunition][initial-ammo]

## Usage

Use [Ray Gun Ammo](RayGunAmmo.md) (`minecraft:raygun_ammo`). Hold the gun in your main hand; the default controls are **left mouse: Shoot**, **hold right mouse: Aim**, and **R: Reload**. See [TaCZ Firearms](../mechanics/TaCZFirearms.md) for the shared controls and ammunition rules. [Input][input] · [Key bindings][keys] · [Matching ammunition][definition]

Hold the default Shoot button to keep firing in **AUTO**, its only mode. Pressing **G** leaves the gun on its sole mode. Each successful firing event spends one loaded round and launches one projectile. The configured **150 RPM** gives a **400 ms / 8-tick** trigger cooldown, enforced by the server; this is a setting, not a measured sustained rate. Reloads and game tick rate still affect actual firing cadence. [Mode][modes] · [Mode selection][mode-cycle] · [Input][input] · [Cooldown][cadence] · [Server check][server-gate] · [Firing][firing]

### Reloading

An empty or partial reload takes **68 ticks**, nominally **3.40 seconds at 20 TPS**. Already loaded rounds are retained. Keep holding the gun until completion; switching to a different held item interrupts the use path. A full gun cannot start reloading. [Definition][definition] · [Active timing][reload-timing] · [Reload completion][reload] · [Held item][held-item]

In Survival, completion draws up to the missing capacity from the **first matching ammunition stack only**. A normal full stack holds 64, enough to fill the 20-round magazine, but an earlier small stack can leave it partly loaded even if more ammunition is carried. For example, an empty gun with 5 rounds in the first matching stack loads 5 after one completion. Creative reloads fill the missing capacity without reserve ammunition; firing still spends loaded rounds. The ammunition owner covers its Ammo Assembly Table recipe. [Supply and initial state][initial-ammo] · [Ammo stack][ammo-definition] · [Firing][firing]

### Direct hits and imported effects

The bundled data declares an explosion (damage 80, radius 2, delay 5), ignition, and `armor_ignore: 2.1`. These fields are not consumed by the reviewed shot, damage or impact code, so they do not supply splash damage, a fuse, fire, or an armor-bypass bonus. Registration routes this gun through the common firearm item, which creates an ordinary `TaczBullet`; it does not select a separate energy damage entity. Lifetime expiry discards the projectile. A block hit runs the normal block projectile callback and then discards it, so individual blocks can still react to being hit. [Imported fields][imported-bullet] · [Registration][registration] · [Firing][firing] · [Ballistic loader][loader] · [Flight][flight] · [Impact][hit] · [Block callback][block-callback]

There is a separate cosmetic particle path. The client checks gun-display particles first, then ammunition-display particles; this gun's display has no particle object, while its ammunition display names `glow_squid_ink`. Those visuals do not add server damage or explosions. Their in-game appearance has not been tested here. [Client hook][particle-hook] · [Selection][particles] · [Particle parser][particle-parser] · [Gun display][gun-display] · [Ammo display][ammo-display]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [Ray Gun Ammo](RayGunAmmo.md) |
| Magazine size | 20 |
| Extended magazine sizes | None |
| Fire modes | AUTO |
| RPM | 150 |
| Damage | 45 |
| Pellets per shot | 1 |
| Bullet speed | 260 m/s |
| Lifetime | 3.1 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.8 |
| Knockback | 0.8 |

These are the declared base values. **Damage** is the definition fallback; the bundled distance curve below takes priority on ordinary hits. **Pellets per shot: 1** means one projectile for each round fired. [Definition][definition] · [Damage selection][damage-curve] · [Firing][firing]

**Pierce 1** allows one successful entity-damage event, after which the bullet is discarded. It does not let the shot penetrate a wall. A qualifying headshot multiplies the selected distance-band damage by **1.8** before target damage handling. The head region starts at the target's Y plus 85% of its eye height and applies to living entities. The **0.8 knockback** is an extra push along the horizontal flight direction, with a 0.03 upward component when that direction is usable; it is not a guaranteed displacement. [Hit, headshot and push rules][hit]

**260 m/s** is the nominal conversion of the **13 blocks/tick launch setting** at 20 TPS, treating a block as a metre. Lifetime is **62 ticks**. Spread and shooter movement affect launch; gravity, friction, water and collisions alter flight. The active gravity/friction values are **0.0245 / 0.01**. Speed × lifetime is not a promised range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

Damage values are inputs to the target's normal damage handling, not guaranteed health lost. The bullet damage type is tagged as a projectile and is absent from the bundled bypass-armor tag. [Hit handling][hit] · [Projectile tag][projectile-tag] · [Armor tag][armor-tag]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 1.45 |
| Moving | 2.25 |
| Sneaking | 0.9 |
| Prone | 0.6 |
| Aiming down sights | 0.12 |

These are spread inputs: lower values reduce random launch deviation, not camera recoil or a percentage chance to hit. Selection prioritizes fully completed precision aim, then a low non-swimming pose, sneaking, moving and standing. The **Prone** row names the low-pose input; it does not establish a working Crawl key. [Values][inaccuracy] · [Pose selection][pose] · [Aim gate][input] · [Random launch][launch]

## Damage Falloff

* 8.5 blocks: 82
* 17.5 blocks: 75
* 32 blocks: 60
* infinite blocks: 55

The listed finite distances are **exclusive upper boundaries**, measured straight from the shot's origin to the hit. Actual bands are **below 8.5: 82**, **8.5 to below 17.5: 75**, **17.5 to below 32: 60**, and **32 or farther: 55**, before headshots and target handling. For example, exactly 8.5 blocks selects 75. Damage steps between bands without interpolation. The final “infinite” entry only supplies the last band while the projectile survives; it does not mean infinite reach. [Bundled curve][bands] · [Curve selection][damage-curve] · [Distance comparison][distance] · [Lifetime][flight]

## Attachments

Supported attachment categories: None.

* No attachments are listed for this firearm.

The live definition accepts no attachment categories or IDs, so the refit system offers no working magazine, optic or other attachment upgrade for this gun. Reachable capacity stays at **20 rounds**. [Definition][definition] · [Acceptance gate][fit]

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, initial ammunition, supported modes, reload timing, direct-hit behavior, accuracy, capacity and the separate cosmetic particle path were traced in that snapshot. This is source review, not an in-game crafting, firing, reload, visual-effects or multiplayer test. Imported data and upstream weapon names do not establish additional working behavior.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L39
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/raygun_bo6.json#L1-L39
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/raygun_bo6.json#L1-L7
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L70
[inaccuracy]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/raygun_bo6_data.json#L67-L73
[imported-bullet]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/raygun_bo6_data.json#L6-L35
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2744
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[bench-groups]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L185
[group-names]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8562
[initial-ammo]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L331
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[mode-cycle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[cadence]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L144
[server-gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[firing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[held-item]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3133
[damage-curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[block-callback]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L289-L292
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[ticks]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[loader]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L151-L222
[particles]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczAmmoParticleSpawner.java#L35-L99
[particle-parser]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczAmmoParticleSpawner.java#L105-L158
[particle-hook]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L29-L31
[ammo-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/ammo/raygun_ammo_display.json#L1-L37
[gun-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/guns/raygun_bo6_display.json#L1-L46
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L103
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json#L1-L13
[bands]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/raygun_bo6_data.json#L13-L18
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
