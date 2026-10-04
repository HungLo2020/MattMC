# M320 Grenade Launcher

M320 Grenade Launcher is a single-shot TaCZ firearm using 40mm ammunition. In the reviewed implementation, aim for a direct hit: its imported grenade explosion settings do not produce timed or radius damage. [Definition][definition] · [Shot creation][firing] · [Hit behavior][hit]

## Obtaining

Craft **one M320 Grenade Launcher** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Heavy Weapon**. Carry these materials in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Group][gun-group] · [Group selection][recipe-loader] · [Table groups][bench-groups] · [Names][group-names]

| Materials per craft | Output |
| --- | --- |
| 15 Gold Ingots · 50 Iron Ingots · 6 Lapis Lazuli | 1 × M320 Grenade Launcher |

M320 Grenade Launcher can be obtained from the Creative Menu. It is registered as `minecraft:m320`. It stacks to one, and both a newly crafted gun and its default Creative instance start with **1 loaded round**, without installed attachments. [Registration][registration] · [Creative entry][creative] · [Recipe output][recipe-loader] · [Initial ammunition][initial-ammo]

## Usage

Use [40mm Grenade](40mmGrenade.md) (`minecraft:40mm`). Hold the gun in your main hand; the default controls are **left mouse: Shoot**, **hold right mouse: Aim**, and **R: Reload**. See [TaCZ Firearms](../mechanics/TaCZFirearms.md) for the shared controls and ammunition rules. [Input][input] · [Key bindings][keys] · [Matching ammunition][definition]

Click the default Shoot button for one **SEMI** shot; reload before the next shot. Holding the mouse button does not enable automatic fire. Pressing **G** leaves the gun on its sole mode. Each successful firing event spends one loaded round and launches one projectile. The configured **150 RPM** gives a **400 ms / 8-tick** trigger cooldown, enforced by the server; this is a setting, not a measured sustained rate. The single-round capacity and reload time limit repeated shots. [Mode][modes] · [Mode selection][mode-cycle] · [Input][input] · [Cooldown][cadence] · [Server check][server-gate] · [Firing][firing]

### Reloading

Reload the empty launcher for **61 ticks**, nominally **3.05 seconds at 20 TPS**. The active override rounds 3.04 × 20 to 61; the definition's 36-tick fallback is not the active reload duration. Keep holding the gun until completion; switching to a different held item interrupts the use path. With its one-round capacity, a loaded launcher cannot start another reload. [Active timing][reload-timing] · [Rounding][reload-rounding] · [Definition][definition] · [Reload completion][reload] · [Held item][held-item]

In Survival, each completed reload takes **one matching round** from the first matching inventory stack. A normal ammunition stack holds **6**. The six-grenade recipe batch therefore supplies six completed single-round reloads. The ammunition owner gives the recipe under **Explosives & Projectiles** at the Ammo Assembly Table. Creative reloads supply the round without reserve ammunition, but shooting still empties the launcher. [Supply][initial-ammo] · [Ammo stack][ammo-definition] · [Ammo recipe][ammo-recipe] · [Firing][firing]

### Direct hits and imported effects

The bundled data declares an explosion with damage 50, radius 6 and delay 30. Those fields are not consumed by the reviewed shot, damage or impact code, so they do not supply a timed grenade detonation or splash damage. Registration routes this gun through the common firearm item, which creates an ordinary `TaczBullet`; it does not select a separate grenade damage entity. Lifetime expiry discards the projectile. A block hit runs the normal block projectile callback and then discards it, so individual blocks can still react to being hit. [Imported fields][imported-bullet] · [Registration][registration] · [Firing][firing] · [Ballistic loader][loader] · [Flight][flight] · [Impact][hit] · [Block callback][block-callback]

There is a separate cosmetic particle path. The client checks gun-display particles first, then ammunition-display particles; this gun's display has no particle object, while its ammunition display names `smoke`. Those visuals do not add server damage or explosions. Their in-game appearance has not been tested here. [Client hook][particle-hook] · [Selection][particles] · [Particle parser][particle-parser] · [Gun display][gun-display] · [Ammo display][ammo-display]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [40mm Grenade](40mmGrenade.md) |
| Magazine size | 1 |
| Extended magazine sizes | None |
| Fire modes | SEMI |
| RPM | 150 |
| Damage | 10 |
| Pellets per shot | 1 |
| Bullet speed | 60 m/s |
| Lifetime | 5 seconds |
| Pierce | 0 |
| Headshot multiplier | 1 |
| Knockback | 0 |

These are the declared base values. **Damage** is the definition fallback; with no configured distance curve, it supplies the active **10 direct-hit damage** before the target's normal damage handling. **Pellets per shot: 1** means one projectile for each round fired. [Definition][definition] · [Damage selection][damage-curve] · [Firing][firing]

**Pierce 0 is the declaration, not zero possible hits.** The projectile factory and constructor clamp the effective budget to **one successful entity-damage event**, then discard the bullet after that hit. It does not penetrate blocks. The **1× headshot multiplier** adds no headshot bonus, and **Knockback 0** means this bullet routine adds no extra push; it does not guarantee that the target cannot move. [Firing and clamp][firing] · [Constructor clamp][clamps] · [Hit and push rules][hit]

**60 m/s** is the nominal conversion of the **3 blocks/tick launch setting** at 20 TPS, treating a block as a metre. Lifetime is **100 ticks**. Spread and shooter movement affect launch; gravity, friction, water and collisions alter flight. The active gravity/friction values are **0.005 / 0.05**. Speed × lifetime is not a promised range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

Damage values are inputs to the target's normal damage handling, not guaranteed health lost. The bullet damage type is tagged as a projectile and is absent from the bundled bypass-armor tag. [Hit handling][hit] · [Projectile tag][projectile-tag] · [Armor tag][armor-tag]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 1.5 |
| Moving | 2.5 |
| Sneaking | 1 |
| Prone | 0.5 |
| Aiming down sights | 0.2 |

These are spread inputs: lower values reduce random launch deviation, not camera recoil or a percentage chance to hit. Selection prioritizes fully completed precision aim, then a low non-swimming pose, sneaking, moving and standing. The **Prone** row names the low-pose input; it does not establish a working Crawl key. [Values][inaccuracy] · [Pose selection][pose] · [Aim gate][input] · [Random launch][launch]

## Damage Falloff

* None listed.

There is no configured distance curve. The fallback therefore remains **10 direct-hit damage** at any distance the projectile actually reaches, before target handling. “None listed” does not mean zero damage or supply a separate explosion value; lifetime and collisions still limit whether a shot reaches the target. [Bundled data][imported-bullet] · [Fallback selection][damage-curve] · [Flight][flight] · [Hit handling][hit]

## Attachments

Supported attachment categories: None.

* No attachments are listed for this firearm.

The live definition accepts no attachment categories or IDs, so the refit system offers no working magazine, optic or other attachment upgrade for this gun. Reachable capacity stays at **1 round**. [Definition][definition] · [Acceptance gate][fit]

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, initial ammunition, supported modes, reload timing, direct-hit behavior, accuracy, capacity and the separate cosmetic particle path were traced in that snapshot. This is source review, not an in-game crafting, firing, reload, visual-effects or multiplayer test. Imported data and upstream weapon names do not establish additional working behavior.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L46
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/m320.json#L1-L27
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/m320.json#L1-L7
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L38
[inaccuracy]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m320_data.json#L113-L119
[imported-bullet]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/m320_data.json#L6-L27
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
[reload-rounding]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L90-L92
[held-item]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3133
[damage-curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[clamps]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L59-L73
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
[ammo-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/ammo/40mm_display.json#L1-L34
[gun-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/guns/m320_display.json#L1-L43
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L85
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/40mm.json#L1-L29
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json#L1-L13
