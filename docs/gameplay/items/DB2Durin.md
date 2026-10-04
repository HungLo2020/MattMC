# DB-2 Durin

The DB-2 Durin holds two shells and creates sixteen projectiles from each shell. Its default BURST mode fires one shell per click; SEMI uses a shorter trigger cooldown. [Gun definition][definition] · [Firing][shot] · [Burst settings][burst] · [Cooldown][cooldown]

## Obtaining

Craft **one DB-2 Durin** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Shotgun**. Carry the materials in your inventory and use the craft control. [Recipe][recipe] · [Recipe loading][recipe-loader] · [Gun group][index] · [Workbench groups][workbench-groups] · [Crafting][craft]

| Materials per craft | Output |
| --- | --- |
| 8 Iron Ingots · 6 items in `minecraft:logs` | 1 × DB-2 Durin |

The wood ingredient accepts items in `minecraft:logs`; matching variants can be mixed across inventory stacks. Planks do not satisfy this ingredient. [Tag matching][material-tags] · [Counting materials][material-count] · [Logs tag][logs-tag]

It is also available from the Creative Menu and registered as `minecraft:db_short`. The [12 Gauge Bullet](12GaugeBullet.md#obtaining) page gives its ammunition recipe. [Registration][registration] · [Creative entries][creative]

## Usage

Hold the gun in your **main hand**. Default controls are **left mouse** to shoot, held **right mouse** to aim, **R** to reload, **G** to change fire mode and **Z** to refit. See [TaCZ firearms](../mechanics/TaCZFirearms.md#controls) for remapping and shared control limits. [Bindings][keys] · [Input handling][input]

**BURST** is the default, followed by **SEMI** when cycling modes. Both require a fresh default-mouse click, spend **one shell** and create the same **16-projectile batch**. BURST has a configured count of **one**, so it fires immediately through the single-round path; it does not schedule a second shell. [Modes][modes] · [Default selection][default-mode] · [Burst settings][burst] · [Trigger path][cooldown] · [Mouse input][input]

| Mode | Trigger cooldown | Timing source |
| --- | ---: | --- |
| BURST | 10 ticks | 500 ms minimum interval |
| SEMI | 8 ticks | 150 RPM → 400 ms interval |

The BURST profile's 600 BPM does not space another round because the count is one. These are configured cooldowns, not measured firing rates. [Burst conversion][burst-ticks] · [Cooldown calculation][cooldown]

**Reloading takes 37 ticks** (1.85 nominal seconds at 20 ticks/second), whether partly loaded or empty. Keep the gun held until completion: shells are added once at the end. Switching to a different item type cancels an unfinished reload and adds no shells. Existing loaded shells remain. Survival takes ammunition from the **first matching inventory stack only**, so a small first stack can leave the gun partly loaded even when the HUD shows more reserve. See [magazine, reserve and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) for Creative supply and partial reloads. [Definition][definition] · [Timing][reload-timing] · [Completion][reload-start] · [Interruption][stop-use] · [Shell consumption][reload-ammo]

## Properties

These values describe the checked game implementation. Seconds assume **20 game ticks per second**. The m/s convention treats one block as one metre; speed is a launch setting, affected by spread, shooter movement, drag and gravity. [Reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats) · [Definition][definition] · [Launch motion][launch] · [Flight][flight]

| Property | Value |
| --- | --- |
| Ammo | [12 Gauge Bullet](12GaugeBullet.md) |
| Magazine size | 2 shells |
| Extended magazine sizes | None; no compatible extended magazine |
| Fire modes | BURST, SEMI |
| RPM | 150 configured; see Usage for the trigger cooldown |
| Damage | 24 fallback **shot value**; see Damage Falloff for each projectile |
| Pellets per shot | 16 projectiles from one loaded shell |
| Bullet speed | 120 m/s nominal (6 blocks/tick × 20) |
| Lifetime | 0.3 nominal seconds (6 ticks) |
| Pierce | 1 successful entity-damage hit per projectile |
| Headshot multiplier | 1.25 × per-projectile damage |
| Knockback | 0.875 horizontal push strength, plus 0.03 upward |

The lifetime and collision rules limit travel; multiplying the speed and lifetime does not give a guaranteed range. In air, each tick retains 95% of velocity, then subtracts 0.005 blocks/tick from vertical velocity; water uses different values. A block hit ends the projectile, and Pierce 1 ends it after its first **successful** entity damage attempt. Pierce is a collision budget, not armor penetration. [Flight][flight] · [Hit handling][hits] · [Block collisions and push][blocks]

## Accuracy

Lower values mean tighter random spread, not a hit percentage or a radius in blocks. Completed aim takes priority, then the prone pose, sneaking, moving and standing. The **Prone** row means the game's swimming pose while the entity is not actually swimming; the registered crawl key has no active crawl action. [Spread selection][spread-state] · [Spread calculation][spread] · [Key registration][keys] · [Input handling][input]

| Property | Value |
| --- | --- |
| Standing | 4.5 |
| Moving | 4.75 |
| Sneaking | 4 |
| Prone | 4 |
| Aiming down sights | 4 |

[Bundled spread values][spread-values] · [Aim completion][input] · [Random projectile launch][launch]

## Damage Falloff

Each shell's shot value is divided among **16 projectiles**. The per-projectile numbers below are **health points before headshots and target defenses**; the shot value is not damage dealt by every pellet or a guaranteed total. [Shot creation][shot] · [Damage division][curve]

| Straight-line distance from launch to hit | Shot value | Damage per projectile |
| --- | ---: | ---: |
| 0 to below 5 blocks | 24 | 1.5 |
| 5 to below 9 blocks | 18 | 1.125 |
| 9 or more blocks | 12 | 0.75 |

At an exact threshold, the next band applies. Damage changes in steps; the final band does not remove the lifetime or collision limits. [Bundled curve][profile] · [Distance selection][distance]

A living-entity hit at or above **85% of its eye height above its base** receives the 1.25× headshot multiplier. Armor, armor toughness, effects and absorption can then change the damage received. Successful damage also applies the listed push and resets the target's damage-invulnerability timer; these values do not guarantee health loss or displacement. [Headshots and push][blocks] · [Damage handling][hits] · [Armor][armor] · [Target defenses][target]

## Attachments

Use [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for installation, removal and inventory-space cautions. The accepted categories and exact items are listed below. [Compatibility][fit] · [Accepted items][definition]

Supported attachment categories: Ammo Modifier, Stock.

* Stock: [Factory Issued Tactical Stock](FactoryIssuedTacticalStock.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md), [Shotgun Slug](ShotgunSlug.md)

Ammo Modifiers remain installed attachments; reload with ordinary [12 Gauge Bullets](12GaugeBullet.md). In particular, [Shotgun Slug](ShotgunSlug.md#behavior) keeps this gun's **16-projectile count** in the checked firing path. Compatibility does not establish the imported attachment damage, spread, speed or armor-ignore effects. See each attachment page for its implemented behavior. [Firing][shot] · [Reload matching][reload-ammo] · [Attachment profile reader][attachment-loader]

## Notes

- This item belongs to the integrated [TaCZ firearms](../mechanics/TaCZFirearms.md) system. The [workbench guide](../blocks/TaCZWorkbenches.md) covers making and using the tables.
- Bullets use ordinary armor handling. Imported armor-ignore and reload-animation fields do not override the active damage and reload paths described here. [Bullet damage type][bullet-source] · [Armor-bypass tag][armor-tag] · [Profile reader][profile-loader] · [Reload timing][reload-timing]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, controls, modes, reloads, damage, flight and compatibility were traced through their consumers. This is source review of the game, not an in-game crafting, combat or timing test.

[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L18
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[burst]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L6-L22
[cooldown]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L143
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/db_short.json#L1-L21
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/db_short.json#L1-L7
[workbench-groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L148
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[material-tags]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L207-L224
[material-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L79-L105
[logs-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs.json#L1-L7
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2721
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L29
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L7-L15
[default-mode]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L333-L340
[burst-ticks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L32-L43
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L30
[reload-start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[stop-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3131
[reload-ammo]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L318
[launch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[flight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L100-L133
[hits]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L203
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L206-L251
[spread-state]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L237
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L48
[spread-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/db_short_data.json#L152-L158
[curve]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L51-L61
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/db_short_data.json#L4-L33
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[armor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1786
[target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1830-L1845
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[attachment-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[bullet-source]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/damagesource/DamageSources.java#L247-L249
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L173-L221
