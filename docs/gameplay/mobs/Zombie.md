# Zombie

The **Zombie** is a hostile melee mob that threatens players, villagers, and turtle nests. Keep village entrances secure, watch for faster babies, and do not rely on sunlight or a copper door to make an encounter safe. An ordinary adult has a **base maximum health of 20 points (10 hearts)**, **2 armor points** before equipment, and a body **0.6 blocks wide × 1.95 blocks tall**. Some spawn bonuses increase its maximum health. [Behavior and attributes][zombie] · [Default health][attributes] · [Registered attributes][defaults] · [Size][registration]

## Where to find it

The bundled **plains** and **forest** biome monster tables list ordinary zombies in groups of four. **Deserts** also list them, but with a lower spawn weight than [Husks](Husk.md). [Zombie Villagers](ZombieVillager.md) are separate spawn entries. **Deep Dark** and **Mushroom Fields** have no ordinary zombie entry in their normal biome spawn tables; that does not stop an existing zombie from entering them. [Plains][plains] · [Forest][forest] · [Desert][desert] · [Deep Dark][deep-dark] · [Mushroom Fields][mushroom-fields]

Normal ground spawning requires a non-Peaceful difficulty, suitable ground, and the monster darkness checks. In the bundled Overworld, **block light must be 0**, with additional sky/local-light tests. Lighting spawnable ground helps prevent new natural spawns. Group sizes describe spawn attempts, not a guaranteed number of mobs. [Spawn registration][placement] · [Monster rules][monster] · [Ground check][mob-spawn] · [Overworld settings][overworld]

Zombies can also come from **monster-room spawners**. The room generator chooses zombies in two of its four equally selected entries; plains generation includes that feature. [Room generator][monster-room] · [Generation wiring][plains]

In Creative, use the [Zombie Spawn Egg](../items/ZombieSpawnEgg.md). With command permission, use `/summon minecraft:zombie`.

## Fighting and protecting villagers

- **Keep room to retreat.** Zombies use melee attacks and a base follow-range attribute of **35 blocks**, which some spawn bonuses increase. Their base attack attribute is **3 health points**, but difficulty, held weapons, enchantments, and defenses affect actual damage.
- **Watch babies carefully.** A newly chosen spawn-group profile has a **5% chance** to be a baby profile. Babies have half-size bodies and a **50% movement-speed attribute bonus**. Some become chicken jockeys; the chicken is a separate mob.
- **Expect company on Hard.** A hurt zombie with a target can attempt to summon reinforcements while monster spawning is enabled. Successful reinforcements still need a valid, unobstructed spawn position away from nearby players. The chance varies by the zombie's attributes, so prolonged fights can grow.
- **Keep zombies away from villagers and turtle nests.** Their targets include villagers, iron golems, and baby turtles on land. They also have a turtle-egg destruction goal, enabled by `mobGriefing`.

If a zombie kills a villager, the code attempts [Zombie Villager](ZombieVillager.md) conversion **50% of the time on Normal** and **every time on Hard**; it does not perform that conversion on Easy. Do not expose a valued villager expecting a guaranteed recoverable outcome on Normal. [Targets, babies, reinforcements, and infection][zombie] · [Melee damage][melee] · [Player difficulty scaling][player-damage] · [Egg goal restrictions][egg-goal]

## Doors and daylight

Only door-capable zombies use the breaking goal. The ability is selected partly by local difficulty, and a rarer leader bonus also enables it. Breaking begins only on **Hard** with **`mobGriefing` enabled**, and the default uninterrupted breaking time is **240 ticks**, about **12 seconds at 20 TPS**. [Door capability][zombie] · [Breaking conditions and timer][break-door]

**Copper doors are vulnerable too.** The current door-interaction check accepts doors that can be opened by hand. That includes wooden and copper doors, despite the helper's name; iron doors are excluded. A closed iron door is therefore a better entrance barrier against this particular attack. [Door selection][door-goal] · [Door test][door-test] · [Copper and iron settings][door-types] · [Copper registration][copper]

Zombies, including babies, can burn in exposed bright daylight. Water/rain and powder snow suppress the sun-burn check, and **any nonempty head slot prevents that trigger**. Damageable headgear wears down and can break. A burning zombie with an empty main hand can also ignite a target it successfully hits, with a chance and duration based on local difficulty. Keep your distance while waiting for daylight damage. [Sunlight and burning attacks][zombie] · [Sun-burn conditions][sun]

Newly finalized zombies with empty head slots also have an October 31 seasonal-headgear roll, using the server's local calendar. The chance is **25%**; a selected headpiece is usually a carved pumpkin, with a **10% chance** of a jack o'lantern instead. These seasonal headpieces have **zero drop chance**. [Seasonal equipment][zombie]

## Water conversion

An ordinary zombie with its **eyes in water** for roughly **30 seconds** starts turning into a [Drowned](Drowned.md). The initial threshold is **600 ticks**, followed by a **300-tick conversion counter** that completes when it falls below zero, approximately 15 more seconds at 20 TPS.

Leaving the water **before conversion starts** resets the exposure timer. **After conversion starts, leaving the water does not cancel it.** Both stages require the zombie to be ticking with AI enabled. This page describes ordinary zombies; other zombie-family mobs can override water-conversion behavior. [Conversion code][zombie]

## Drops

With mob loot enabled:

- **0–2 [Rotten Flesh](../items/RottenFlesh.md)**, with a possible maximum of **5 at Looting III**
- On a qualifying player-attributed kill, a **2.5% chance** of one extra item selected equally from an [Iron Ingot](../items/IronIngot.md), [Carrot](../items/Carrot.md), or [Potato](../items/Potato.md). This is the combined chance, not 2.5% for each item. Looting raises it by **1 percentage point per level**, reaching **5.5% at Looting III**
- The potato becomes a [Baked Potato](../items/BakedPotato.md) if the zombie is burning or the direct attacker's main-hand enchantment meets the smelts-loot condition; rotten flesh has no cooked replacement
- A **baby zombie still riding a chicken** on a qualifying player-attributed kill drops one [Lava Chicken Music Disc](../items/MusicDiscLavaChicken.md)

Naturally equipped weapons and armor have separate drop rules, normally **8.5% per equipped slot** on a qualifying player-attributed kill before enchantment changes. Dropped equipment may be badly worn. The base experience reward is **5 for an adult** or **12 for a baby**, before equipment bonuses. Baby zombies are not subject to passive-animal baby loot restrictions. [Loot table][loot] · [Equipment rules][equipment] · [Default equipment chance][drop-chances] · [Experience calculation][zombie] · [Mob rewards][monster] · [Equipment experience][equipment-xp] · [Reward conditions][experience]

A zombie killed by a **charged creeper** can produce a [Zombie Head](../items/ZombieHead.md), subject to the creeper's one-special-head limit. [Head table][head] · [Charged-creeper rules](Creeper.md)

## Verification scope

Source-reviewed on **2026-10-01** against active MattMC code and bundled data at commit `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game door-breaking, infection, conversion, or loot test was run for this page. Data packs can change spawn tables and loot; equipment, effects, difficulty, and entity data can change individual encounters.

Related: [Husk](Husk.md) · [Drowned](Drowned.md) · [Zombie Villager](ZombieVillager.md) · [Chicken](Chicken.md) · [Mobs](Mobs.md)

[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java
[plains]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/forest.json
[desert]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/desert.json
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json
[mushroom-fields]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[mob-spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L728-L740
[overworld]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/dimension_type/overworld.json
[monster-room]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java
[player-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L750
[experience]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[zombie]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Zombie.java
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1552-L1561
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L158
[melee]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1319
[egg-goal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/RemoveBlockGoal.java
[break-door]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/BreakDoorGoal.java
[door-goal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/DoorInteractGoal.java
[door-test]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L271-L277
[door-types]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java
[copper]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6239-L6270
[sun]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L1326-L1339
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/zombie.json
[equipment]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L814-L844
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/DropChances.java
[equipment-xp]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L290-L306
[head]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/charged_creeper/zombie.json
