# SPAS-12 Multi-purpose Shotgun

The SPAS-12 Multi-purpose Shotgun is a five-shell TaCZ shotgun with SEMI and BURST modes. Both spend one shell and create eight pellets per click; BURST changes damage, spread, and reload timing.

## Obtaining

Craft **one** at the [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), in the **Shotgun** group, from **110 Iron Ingots, 20 Gold Ingots, 2 Diamonds, and 5 Lapis Lazuli**. See the workbench guide for table recipes and crafting controls. [Recipe][recipe] · [Category][category] · [Output count][craft-output]

It also appears in the Creative Menu and is registered as `minecraft:spas_12`. Carry [12 Gauge Bullet](12GaugeBullet.md) for Survival reloads; see that page for the ammunition recipe. [Creative entries][creative] · [Registration][registration]

## Usage

Hold the gun in the **main hand**. With the default bindings, **left-click** fires, **hold right-click** aims, and **R** reloads. See [TaCZ firearms](../mechanics/TaCZFirearms.md#controls) for remapping, aiming transitions, and other controls. [Defaults][keys] · [Input handling][input]

It starts in **SEMI**; press **G** to switch to **BURST**. In either mode, each click spends **one shell** and creates **eight pellets**. Holding the default mouse Shoot binding does not repeat fire. BURST is configured with a count of one, so it fires immediately without queuing a multi-round burst; its name does not mean two shells are fired. [Modes][modes] · [Burst configuration][burst] · [Trigger and ammunition][trigger]

BURST subtracts **8 from the total shot damage curve** and adds **1.35 to every spread parameter**. The mode tables below show the resulting per-pellet damage and spread. Animation names such as `switch_semi` and `switch_pump` do not change these HUD mode labels or shell counts. [Mode adjustments][mode-adjust] · [Damage division][curve] · [Mode animations][animations]

### Reloading

Keep the gun selected until the reload finishes. All new shells are added **once at completion**; the shell-loading animation does not grant usable ammunition in stages. Switching to a different item cancels the reload without adding shells, while previously loaded shells remain. [Reload completion][reload-start] · [Stopping use][stop-use] · [Ammunition grant][reload-ammo]

| Starting load | Active reload duration |
| --- | --- |
| SEMI: one shell missing, four still loaded | 45 ticks / 2.25 s |
| SEMI: empty, base five-shell capacity | 133 ticks / 6.65 s |
| BURST: one shell missing, four still loaded | 49 ticks / 2.45 s |
| BURST: empty, base five-shell capacity | 141 ticks / 7.05 s |

For other loads, SEMI adds 0.6667 s per missing shell to a 0.7 s opening allowance (2.4333 s when empty) and 0.8667 s. BURST instead uses 0.8667 s per missing shell and a 0.7 s opening allowance (1.8667 s when empty), plus 0.8667 s. Each total rounds to game ticks; extended capacity can lengthen it. [Reload timing][reload-timing] Seconds here assume **20 game ticks per second**; they are not measured wall-clock times.

The timer uses **missing capacity**, even if the first carried ammunition stack contains fewer shells. Survival takes shells from that **first matching stack only**, so a completed reload can be partial despite a larger HUD reserve total. Combine stacks or reload again as needed. Creative supplies missing shells without reserve ammunition, but each shot still spends a loaded shell. See [magazine and reserve rules](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reload supply][reload-ammo] · [Shot consumption][shot]

## Properties

These are the gun's base values; damage and spread by mode and distance are explained below. [Gun definition][definition] · [Bundled profile][profile]

| Property | Value |
| --- | --- |
| Ammo | [12 Gauge Bullet](12GaugeBullet.md) |
| Magazine size | 5 shells |
| Extended magazine sizes | 6, 7, 8 with Shotgun Ammo Extended Mag I, II, III |
| Fire modes | SEMI, BURST (default: SEMI) |
| RPM | 200 configured in SEMI; both modes use a 6-tick trigger cooldown |
| Damage | 64 fallback shot value; first-band shot values are 64 SEMI / 56 BURST |
| Pellets per shot | 8 from one shell |
| Bullet speed | 150 m/s nominal: 7.5 blocks/tick at 20 ticks/s |
| Lifetime | 12 ticks / 0.6 nominal seconds |
| Pierce | 1 successful entity hit per pellet |
| Headshot multiplier | 1.33 per pellet |
| Knockback | 0.15 horizontal push strength, plus 0.03 upward |

The RPM setting is converted to a game-tick trigger cooldown; it is not a measured firing rate. The speed uses the wiki's one-block-to-one-metre convention. Random spread, shooter movement, drag, and gravity affect actual travel; lifetime and speed do not promise a maximum hit distance. [Cooldown][trigger] · [Launch motion][spread] · [Projectile motion][motion]

The three extended capacities correspond to Shotgun Ammo Extended Mag I, II, and III. Installing one adds capacity without supplying shells. Use the [Refit guide](../mechanics/TaCZFirearms.md#refitting-attachments) for installation and the ammunition-loss caution when reducing capacity. [Capacity][capacity]

## Accuracy

| Property | SEMI | BURST |
| --- | --- | --- |
| Standing | 2.85 | 4.2 |
| Moving | 2.9 | 4.25 |
| Sneaking | 2.75 | 4.1 |
| Prone | 2.6 | 3.95 |
| Aiming down sights | 2.6 | 3.95 |

Lower values mean tighter **random spread**, not a hit percentage or a radius in blocks. Aiming takes priority only after the aim transition; otherwise the pose selection checks Prone, Sneaking, Moving, then Standing. Prone means the non-swimming SWIMMING pose, and the registered C binding does not provide a crawl action in this snapshot. See the [shared control limits](../mechanics/TaCZFirearms.md#controls). [Spread values][accuracy-data] · [Pose selection][pose] · [Random launch spread][spread]

## Damage Falloff

| Distance from launch to hit | SEMI shot value | SEMI per pellet | BURST shot value | BURST per pellet |
| --- | --- | --- | --- | --- |
| 0 to less than 15 blocks | 64 | 8 | 56 | 7 |
| 15 to less than 30 blocks | 40 | 5 | 32 | 4 |
| 30 blocks or more | 24 | 3 | 16 | 2 |

Values are **health points before headshots and target defenses**, not hearts or guaranteed damage to one target. Each shot value is divided among its 8 pellets. Distance is measured in a straight line from launch to impact; equality enters the next band, with no interpolation. The last band does not remove the 12-tick lifetime or collision limits. [Damage division][curve] · [Distance selection][distance]

A living-entity hit at or above 85% of its eye height above its base receives the **1.33** headshot multiplier for that pellet. Each pellet stops after its first successful damage hit; a failed damage attempt does not consume that successful-hit budget. Blocks stop it as well. Successful hits apply the listed push and reset the target's invulnerability time. [Hit handling][hit]

Ordinary armor, armor toughness, effects, and absorption still apply. The imported armor-ignore field does not bypass them in this path; **Pierce 1 is not an armor-bypass percentage**. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats). [Bullet damage type][bullet-source] · [Armor-bypass tag][armor-tag] · [Armor calculation][armor-calculation] · [Target defenses][armor] · [Parsed adjustments][adjustments]

## Attachments

Supported attachment categories: Ammo Modifier, Extended Mag, Muzzle, Scope, Stock.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [T2 red dot](T2reddot.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md)
* Muzzle: [12 Gauge Silencer](12GaugeSilencer.md), [Mastiff Shotgun Muzzle Brake](MastiffShotgunMuzzleBrake.md), [Shotgun Choke](ShotgunChoke.md)
* Stock: [AK-12 Regular Stock](AK12RegularStock.md), [Carbon bone C5 Stock](CarbonboneC5Stock.md), [CMMG RipStock Stock](CMMGRipStockStock.md), [Franchi Heavy Stock](FranchiHeavyStock.md), [Franchi Tactical Stock](FranchiTacticalStock.md), [HK Slim Line Stock](HKSlimLineStock.md), [M4SS Stock](M4SSStock.md), [Magpul CTR stock](MagpulCTRstock.md), [Magpul MOE Stock](MagpulMOEStock.md), [Militech B5 Stock](MilitechB5Stock.md), [SBA3 Stock](SBA3Stock.md)
* Extended Mag: [Shotgun Ammo Extended Mag I](ShotgunAmmoExtendedMagI.md), [Shotgun Ammo Extended Mag II](ShotgunAmmoExtendedMagII.md), [Shotgun Ammo Extended Mag III](ShotgunAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [High Explosive Ammo](HighExplosiveAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md), [Shotgun Slug](ShotgunSlug.md)

## Notes

SEMI uses the configured 200 RPM interval of 300 ms; BURST uses its own 300 ms minimum trigger interval. Both convert to six ticks. BURST’s separate 180 BPM value has no second round to space because its count is one. [Trigger cooldown][trigger] · [Burst definition][burst]

Air motion retains 95% of the previous velocity before gravity is applied; water uses different drag and gravity. Active air gravity is 0.005 per tick. These are source-defined movement rules, not a measured range or accuracy result. [Projectile motion][motion] · [Definition][definition]

A fresh plain crafted stack defaults to the base loaded-shell count in the ammo-reading code. This is an inference from the output and ammo paths, not an in-game crafting test. No separate chamber-plus-one count is used here. [Craft output][craft-output] · [Loaded count and capacity][capacity]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The recipe, active firing and reload paths, damage curve, projectile handling, and accepted attachments were inspected. This is **source review**, not an in-game crafting, combat, timing, or multiplayer test.

[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/spas_12_data.json#L203-L209
[adjustments]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L365-L385
[animations]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunAnimationTimings.java#L220-L229
[armor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1830-L1848
[armor-calculation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1786
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[bullet-source]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/damagesource/DamageSources.java#L247-L249
[burst]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L6-L22
[capacity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L330
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/guns/spas_12.json#L1-L8
[craft-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L145-L151
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[curve]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L51-L61
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L64
[distance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L251
[input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L21
[mode-adjust]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/spas_12_data.json#L78-L91
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L57
[motion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L100-L133
[pose]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L237
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/guns/spas_12_data.json#L1-L40
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/gun/spas_12.json#L1-L33
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2708-L2713
[reload-ammo]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[reload-start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L67
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[stop-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3131
[trigger]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L181
