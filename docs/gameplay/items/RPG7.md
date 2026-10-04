# RPG-7

RPG-7 is a single-shot TaCZ firearm with a fixed one-round capacity. Its current projectile deals direct-hit damage; the imported rocket explosion settings do not provide a blast or blast-driven block destruction. [Definition][definition] · [Shot creation][firing] · [Hit behavior][hit]

## Obtaining

Craft **one RPG-7** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Heavy Weapon**. Carry these materials in your inventory and follow [Using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Group][gun-group] · [Group selection][recipe-loader] · [Table groups][bench-groups] · [Names][group-names]

| Materials per craft | Output |
| --- | --- |
| 8 Gold Ingots · 70 Iron Ingots · 4 Lapis Lazuli · 10 items in `minecraft:logs` | 1 × RPG-7 |

The logs ingredient is the exact `minecraft:logs` item tag, which includes burnable-log groups and Crimson/Warped stems. Planks are not a substitute for that tag. [Accepted log groups][logs]

RPG-7 can be obtained from the Creative Menu. It is registered as `minecraft:rpg7`. It stacks to one, and both a newly crafted gun and its default Creative instance start with **1 loaded round**, without installed attachments. [Registration][registration] · [Creative entry][creative] · [Recipe output][recipe-loader] · [Initial ammunition][initial-ammo]

## Usage

Use [RPG-7 Rocket](RPG7Rocket.md) (`minecraft:rpg_rocket`). Hold the gun in your main hand; the default controls are **left mouse: Shoot**, **hold right mouse: Aim**, and **R: Reload**. See [TaCZ Firearms](../mechanics/TaCZFirearms.md) for the shared controls and ammunition rules. [Input][input] · [Key bindings][keys] · [Matching ammunition][definition]

Click the default Shoot button for one **SEMI** shot; reload before the next shot. Holding the mouse button does not enable automatic fire. Pressing **G** leaves the gun on its sole mode. Each successful firing event spends one loaded round and launches one projectile. The configured **150 RPM** gives a **400 ms / 8-tick** trigger cooldown, enforced by the server; this is a setting, not a measured sustained rate. The single-round capacity and reload time limit repeated shots. [Mode][modes] · [Mode selection][mode-cycle] · [Input][input] · [Cooldown][cadence] · [Server check][server-gate] · [Firing][firing]

### Reloading

Reload the empty launcher for **71 ticks**, nominally **3.55 seconds at 20 TPS**. The active override rounds 3.53333 × 20 to 71; the definition's 53-tick fallback is not the active reload duration. Keep holding the gun until completion; switching to a different held item interrupts the use path. With its one-round capacity, a loaded launcher cannot start another reload. [Active timing][reload-timing] · [Rounding][reload-rounding] · [Definition][definition] · [Reload completion][reload] · [Held item][held-item]

In Survival, each completed reload takes **one matching round** from the first matching inventory stack. A normal ammunition stack holds **6**. The recipe makes three rockets, so one batch supplies three completed single-round reloads. The ammunition owner gives the recipe under **Explosives & Projectiles** at the Ammo Assembly Table. Creative reloads supply the round without reserve ammunition, but shooting still empties the launcher. [Supply][initial-ammo] · [Ammo stack][ammo-definition] · [Ammo recipe][ammo-recipe] · [Firing][firing]

### Direct hits and imported effects

The bundled data declares an explosion with damage 120, radius 3, delay 30 and `destroy_block: true`. Those fields are not consumed by the reviewed shot, damage or impact code, so they do not supply a rocket blast, delayed detonation or blast-driven block destruction. Registration routes this gun through the common firearm item, which creates an ordinary `TaczBullet`; it does not select a separate rocket damage entity. Lifetime expiry discards the projectile. A block hit runs the normal block projectile callback and then discards it, so individual blocks can still react to being hit. [Imported fields][imported-bullet] · [Registration][registration] · [Firing][firing] · [Ballistic loader][loader] · [Flight][flight] · [Impact][hit] · [Block callback][block-callback]

There is a separate cosmetic particle path. The client checks gun-display particles first, then ammunition-display particles; this gun's display has no particle object, while its ammunition display names `campfire_signal_smoke`. Those visuals do not add server damage or explosions. Their in-game appearance has not been tested here. [Client hook][particle-hook] · [Selection][particles] · [Particle parser][particle-parser] · [Gun display][gun-display] · [Ammo display][ammo-display]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [RPG-7 Rocket](RPG7Rocket.md) |
| Magazine size | 1 |
| Extended magazine sizes | None |
| Fire modes | SEMI |
| RPM | 150 |
| Damage | 20 |
| Pellets per shot | 1 |
| Bullet speed | 80 m/s |
| Lifetime | 3 seconds |
| Pierce | 0 |
| Headshot multiplier | 1 |
| Knockback | 0 |

These are the declared base values. **Damage** is the definition fallback; with no configured distance curve, it supplies the active **20 direct-hit damage** before the target's normal damage handling. **Pellets per shot: 1** means one projectile for each round fired. [Definition][definition] · [Damage selection][damage-curve] · [Firing][firing]

**Pierce 0 is the declaration, not zero possible hits.** The projectile factory and constructor clamp the effective budget to **one successful entity-damage event**, then discard the bullet after that hit. It does not penetrate blocks. The **1× headshot multiplier** adds no headshot bonus, and **Knockback 0** means this bullet routine adds no extra push; it does not guarantee that the target cannot move. [Firing and clamp][firing] · [Constructor clamp][clamps] · [Hit and push rules][hit]

**80 m/s** is the nominal conversion of the **4 blocks/tick launch setting** at 20 TPS, treating a block as a metre. Lifetime is **60 ticks**. Spread and shooter movement affect launch; gravity, friction, water and collisions alter flight. The active gravity/friction values are **0.0005 / 0.005**. Speed × lifetime is not a promised range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

Damage values are inputs to the target's normal damage handling, not guaranteed health lost. The bullet damage type is tagged as a projectile and is absent from the bundled bypass-armor tag. [Hit handling][hit] · [Projectile tag][projectile-tag] · [Armor tag][armor-tag]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 3 |
| Moving | 4 |
| Sneaking | 2 |
| Prone | 1.5 |
| Aiming down sights | 0.25 |

These are spread inputs: lower values reduce random launch deviation, not camera recoil or a percentage chance to hit. Selection prioritizes fully completed precision aim, then a low non-swimming pose, sneaking, moving and standing. The **Prone** row names the low-pose input; it does not establish a working Crawl key. [Values][inaccuracy] · [Pose selection][pose] · [Aim gate][input] · [Random launch][launch]

## Damage Falloff

* None listed.

There is no configured distance curve. The fallback therefore remains **20 direct-hit damage** at any distance the projectile actually reaches, before target handling. “None listed” does not mean zero damage or supply a separate explosion value; lifetime and collisions still limit whether a shot reaches the target. [Bundled data][imported-bullet] · [Fallback selection][damage-curve] · [Flight][flight] · [Hit handling][hit]

## Attachments

Supported attachment categories: None.

* No attachments are listed for this firearm.

The live definition accepts no attachment categories or IDs, so the refit system offers no working magazine, optic or other attachment upgrade for this gun. Reachable capacity stays at **1 round**. [Definition][definition] · [Acceptance gate][fit]

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Recipes, initial ammunition, supported modes, reload timing, direct-hit behavior, accuracy, capacity and the separate cosmetic particle path were traced in that snapshot. This is source review, not an in-game crafting, firing, reload, visual-effects or multiplayer test. Imported data and upstream weapon names do not establish additional working behavior.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L59
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/rpg7.json#L1-L33
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/rpg7.json#L1-L7
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L52
[inaccuracy]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/rpg7_data.json#L117-L123
[imported-bullet]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/rpg7_data.json#L6-L28
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
[ammo-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/ammo/rpg_rocket_display.json#L1-L30
[gun-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/assets/minecraft/display/guns/rpg7_display.json#L1-L42
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L105
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/rpg_rocket.json#L1-L29
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json#L1-L13
[logs]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/item/logs.json#L1-L7
