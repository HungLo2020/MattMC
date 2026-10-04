# AA12 Shotgun

The AA12 Shotgun holds eight shells and supports SEMI and AUTO fire. Each shell creates ten projectiles; extended magazines can increase its capacity to 32 shells. [Gun definition][definition] · [Firing][shot]

## Obtaining

Craft **one AA12 Shotgun** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **Shotgun**. Carry the materials in your inventory and use the craft control. [Recipe][recipe] · [Recipe loading][recipe-loader] · [Gun group][index] · [Workbench groups][workbench-groups] · [Crafting][craft]

| Materials per craft | Output |
| --- | --- |
| 16 Gold Ingots · 80 Iron Ingots · 2 Diamonds · 4 Blaze Rods | 1 × AA12 Shotgun |

It is also available from the Creative Menu and registered as `minecraft:aa12`. The [12 Gauge Bullet](12GaugeBullet.md#obtaining) page gives its ammunition recipe. [Registration][registration] · [Creative entries][creative]

## Usage

Hold the gun in your **main hand**. Default controls are **left mouse** to shoot, held **right mouse** to aim, **R** to reload, **G** to change fire mode and **Z** to refit. See [TaCZ firearms](../mechanics/TaCZFirearms.md#controls) for remapping and shared control limits. [Bindings][keys] · [Input handling][input]

**SEMI** is the default: click once for each shot. In **AUTO**, holding Shoot repeats while ammunition and cooldown allow. Both use the configured 350 RPM setting, converted to an integer 171 ms interval and then a **3-tick cooldown**. This is a code-defined cooldown, not a measured firing rate. [Modes][modes] · [Default selection][default-mode] · [Held input][input] · [Cooldown calculation][cooldown]

**Reloading takes 60 ticks** (3 nominal seconds at 20 ticks/second), whether partly loaded or empty. Keep the gun held until completion: shells are added once at the end. Switching to a different item type cancels an unfinished reload and adds no shells. Existing loaded shells remain. Survival takes ammunition from the **first matching inventory stack only**, so a small first stack can leave the gun partly loaded even when the HUD shows more reserve. See [magazine, reserve and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) for Creative supply and partial reloads. [Definition][definition] · [Timing][reload-timing] · [Completion][reload-start] · [Interruption][stop-use] · [Shell consumption][reload-ammo]

## Properties

These values describe the checked game implementation. Seconds assume **20 game ticks per second**. The m/s convention treats one block as one metre; speed is a launch setting, affected by spread, shooter movement, drag and gravity. [Reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats) · [Definition][definition] · [Launch motion][launch] · [Flight][flight]

| Property | Value |
| --- | --- |
| Ammo | [12 Gauge Bullet](12GaugeBullet.md) |
| Magazine size | 8 shells |
| Extended magazine sizes | 16, 24, 32 with Heavy Ammo Extended Mag I, II, III respectively |
| Fire modes | SEMI, AUTO |
| RPM | 350 configured; see Usage for the trigger cooldown |
| Damage | 30 fallback **shot value**; see Damage Falloff for each projectile |
| Pellets per shot | 10 projectiles from one loaded shell |
| Bullet speed | 130 m/s nominal (6.5 blocks/tick × 20) |
| Lifetime | 0.6 nominal seconds (12 ticks) |
| Pierce | 1 successful entity-damage hit per projectile |
| Headshot multiplier | 1.33 × per-projectile damage |
| Knockback | 0.15 horizontal push strength, plus 0.03 upward |

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

Each shell's shot value is divided among **10 projectiles**. The per-projectile numbers below are **health points before headshots and target defenses**; the shot value is not damage dealt by every pellet or a guaranteed total. [Shot creation][shot] · [Damage division][curve]

| Straight-line distance from launch to hit | Shot value | Damage per projectile |
| --- | ---: | ---: |
| 0 to below 15 blocks | 30 | 3 |
| 15 to below 30 blocks | 25 | 2.5 |
| 30 or more blocks | 15 | 1.5 |

At an exact threshold, the next band applies. Damage changes in steps; the final band does not remove the lifetime or collision limits. [Bundled curve][profile] · [Distance selection][distance]

A living-entity hit at or above **85% of its eye height above its base** receives the 1.33× headshot multiplier. Armor, armor toughness, effects and absorption can then change the damage received. Successful damage also applies the listed push and resets the target's damage-invulnerability timer; these values do not guarantee health loss or displacement. [Headshots and push][blocks] · [Damage handling][hits] · [Armor][armor] · [Target defenses][target]

## Attachments

Use [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for installation, removal and inventory-space cautions. The accepted categories and exact items are listed below. [Compatibility][fit] · [Accepted items][definition]

Supported attachment categories: Ammo Modifier, Extended Mag, Grip, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md)
* Muzzle: [12 Gauge Silencer](12GaugeSilencer.md), [Mastiff Shotgun Muzzle Brake](MastiffShotgunMuzzleBrake.md), [Shotgun Choke](ShotgunChoke.md)
* Grip: [Hera Arms CQR Grip](HeraArmsCQRGrip.md), [Koch Ranger Heavy Grip](KochRangerHeavyGrip.md), [Nagoma Military Standard Grip](NagomaMilitaryStandardGrip.md), [P-2 Grip](P2Grip.md), [RK-0 Grip](RK0Grip.md), [RK-1 B25U Grip](RK1B25UGrip.md), [RK-6 Grip](RK6Grip.md), [SE-5 Express Grip](SE5ExpressGrip.md), [SI Grip](SIGrip.md), [Talon AFG1 Handstop](TalonAFG1Handstop.md), [Talon SG2 Grip](TalonSG2Grip.md), [TD Grip](TDGrip.md)
* Extended Mag: [Heavy Ammo Extended Mag I](HeavyAmmoExtendedMagI.md), [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md), [Heavy Ammo Extended Mag III](HeavyAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md), [Shotgun Slug](ShotgunSlug.md)

Heavy Ammo Extended Mag I/II/III select capacities of **16/24/32 shells**. Installing one adds no loaded shells; reload to fill the extra space. Before reducing capacity, follow the [ammunition-loss caution](../mechanics/TaCZFirearms.md#refitting-attachments). [Capacity lookup][magazine]

Ammo Modifiers remain installed attachments; reload with ordinary [12 Gauge Bullets](12GaugeBullet.md). In particular, [Shotgun Slug](ShotgunSlug.md#behavior) keeps this gun's **10-projectile count** in the checked firing path. Compatibility does not establish the imported attachment damage, spread, speed or armor-ignore effects. See each attachment page for its implemented behavior. [Firing][shot] · [Reload matching][reload-ammo] · [Attachment profile reader][attachment-loader]

## Notes

- This item belongs to the integrated [TaCZ firearms](../mechanics/TaCZFirearms.md) system. The [workbench guide](../blocks/TaCZWorkbenches.md) covers making and using the tables.
- Bullets use ordinary armor handling. Imported armor-ignore and reload-animation fields do not override the active damage and reload paths described here. [Bullet damage type][bullet-source] · [Armor-bypass tag][armor-tag] · [Profile reader][profile-loader] · [Reload timing][reload-timing]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, controls, modes, reloads, damage, flight and compatibility were traced through their consumers. This is source review of the game, not an in-game crafting, combat or timing test.

[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L11
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/aa12.json#L1-L33
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/aa12.json#L1-L8
[workbench-groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L148
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2721
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L29
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L7-L15
[default-mode]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L333-L340
[cooldown]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L143
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
[spread-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/aa12_data.json#L135-L141
[curve]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L51-L61
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/aa12_data.json#L4-L33
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[armor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1786
[target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1830-L1845
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L330
[attachment-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[bullet-source]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/damagesource/DamageSources.java#L247-L249
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L173-L221
