# Ender Dragon

The **Ender Dragon** (`minecraft:ender_dragon`) is the boss of the central End island. It has **200 health points (100 hearts)**, flies between attacks, and can heal from nearby [End Crystals](../items/EndCrystal.md). The normal tracked fight opens the exit portal when the dragon dies. Prepare for the trip before entering the [End](../dimensions/End.md); a portal is not a promise of a safe retreat during the fight. [Attributes][dragon] · [Registration][registration] · [Fight rewards][fight]

## Reaching the fight

The normal Survival route is through a completed [Stronghold End portal](../structures/Stronghold.md). The server creates the fight manager only for the registered End dimension using the End dimension type. Its active-fight loop finds or creates the tracked dragon when the fight is not marked complete and the relevant arena/player conditions are met. This is separate from arbitrary command-spawned dragons, which should not be assumed to produce the tracked world's portal and trophy progression. [Fight initialization][server] · [Tracked dragon lifecycle][fight]

Bring food, armor, a usable ranged weapon and ammunition, a melee weapon, tools, and spare blocks for controlled movement. A ranged-only loadout is incomplete because perched phases reject arrows. Mark the arrival/return route, avoid the island edge, and plan for Endermen as well as the dragon. The [End guide](../dimensions/End.md) covers arrival, respawning, and the danger of trying to sleep there.

## Crystals and healing

While a selected crystal remains alive, the dragon restores **1 health point every 10 ticks** when below maximum health, about one heart per second at 20 TPS. It periodically chooses the nearest crystal within its surrounding 32-block-expanded search box. This does not mean every crystal heals it simultaneously. [Crystal selection and healing][dragon]

Destroying the crystal currently linked to the dragon sends a **10-point explosion-damage request to its head** through the dragon's damage handling. Crystals are explosive themselves, so attack from a position that keeps distance and cover between you and the crystal. Do not stand beside one or treat a pillar-top approach as safe merely because the dragon is elsewhere. [Linked-crystal damage][dragon] · [Crystal destruction][crystal]

Clear the crystals you can safely reach before spending long periods trying to outdamage repeated healing. The guide does not validate a particular climbing, water, or cage-breaking route; terrain, knockback, and explosion exposure still matter.

## Flight, perching, and damage

The dragon alternates among flight/approach attacks, landing and perched attacks, and takeoff. Watch its current position instead of relying on one fixed attack timetable.

- **Aim for the head when a safe opportunity exists.** After phase-specific handling, a hit to another body part is reduced to one quarter of the incoming amount plus up to one point
- **Use melee opportunities while it is perched.** The shared sitting-phase handler rejects arrow-family projectiles, including thrown tridents, and Wind Charge item projectiles. This is not a universal rule for every possible projectile or phase
- **Avoid the head, neck, and wings.** Its body-part interactions can damage or knock back nearby living entities; being thrown off the island is a separate danger from the direct hit
- **Move out of breath clouds.** Perched breath and dragon-fireball impacts create area-effect clouds with Instant Damage. They are not ordinary burning terrain, and a Fire Resistance potion does not make those clouds harmless

The damage gate accepts player-attributed damage and damage types in the always-hurts-dragons tag, currently explosion types. Its registration is fire-immune, and its effect-application override refuses status effects. Those checks are specific implementation rules, not a guarantee that every unusual weapon, potion, or custom damage source works as expected. [Body-part damage and gates][dragon] · [Perched-projectile check][sitting] · [Trident inheritance][trident] · [Allowed damage tag][damage-tag] · [Perched cloud][flaming] · [Fireball cloud][fireball]

The dragon's movement can remove non-protected blocks when `mobGriefing` permits. The protected tag includes End Stone, Obsidian, Bedrock, and Iron Bars. This is separate from the destructive pillar rebuilding used during respawning; do not generalize protection from one code path to every arena event. [Movement block removal][dragon] · [Protected blocks][immune] · [Respawn rebuilding][respawn]

## Collecting Dragon's Breath

Use a [Glass Bottle](../items/GlassBottle.md) near a live dragon-owned breath cloud to obtain [Dragon's Breath](../items/DragonsBreath.md). The bottle searches for such clouds within the player's bounding box expanded by two blocks, then reduces the selected cloud's radius by half a block. This collection does not make approaching the cloud safe. [Bottle interaction][bottle]

Dragon's Breath is used for the lingering-potion step described in [Brewing](../brewing/Brewing.md). It is an encounter resource, not a death-loot-table reward.

## Victory and rewards

For the dragon managed by the normal fight tracker:

- Its completed death sequence activates the central exit portal
- The first tracked kill places a [Dragon Egg](../blocks/DragonEgg.md) near the exit podium; subsequent tracked kills do not repeat that egg reward
- A gateway is added while an unused gateway slot remains. The default tracker starts with **20 slots**
- With mob loot enabled, the ordinary death animation distributes **12,000 experience on the first tracked kill**, or **500 afterward**. These are experience points, not player levels

The XP values belong to the ordinary death-animation path; commands and removal methods need not run that entire sequence. The egg is placed by the fight manager rather than dropped from a normal entity loot pool. MattMC also has a separate Vallumraptor breeding placeholder that uses the Dragon Egg block, so the first-kill reward does not establish a one-egg-per-world rule. [Death animation][dragon] · [Tracked rewards and gateway slots][fight] · [Vallumraptor limitation](Vallumraptor.md#owner-commands-and-breeding)

## Respawning the dragon

After the tracked dragon is defeated, place **four End Crystals** on the **four middle Bedrock rim positions around the exit portal**, one at each cardinal side. Relative to the recorded podium position, the fight checks crystal entities two blocks north, south, east, and west, one block above that position. A crystal on an unrelated Obsidian block elsewhere is not this arrangement. [Exact crystal search][fight]

The egg is not an ingredient in the respawn check. Collect and store trophies and move valuables away from the arena before starting. The sequence rebuilds the Obsidian pillars and their crystals, deliberately clears blocks around pillar tops, creates explosions, and finally consumes the summoning crystals. It also temporarily rebuilds the exit podium without the active exit portal. [Respawn setup][fight] · [Rebuilding and consumption][respawn]

Destroying a participating summoning crystal during the sequence aborts it and resets its state. Do not treat an interrupted sequence as a harmless way to preserve every placed crystal or nearby build. This guide describes the checked process; no respawn demonstration or arena design was tested.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game fight, damage, crystal, breath collection, reward, or respawn test was run. Timings assume 20 TPS; custom entity/fight data, tags, and game rules can alter outcomes.

Related: [End](../dimensions/End.md) · [End Crystal](../items/EndCrystal.md) · [Dragon Egg](../blocks/DragonEgg.md) · [Mobs](Mobs.md)

[dragon]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L547-L550
[fight]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/dimension/end/EndDragonFight.java
[server]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L289-L293
[crystal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/boss/enderdragon/EndCrystal.java
[sitting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/boss/enderdragon/phases/AbstractDragonSittingPhase.java
[trident]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L27
[damage-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/damage_type/always_hurts_ender_dragons.json
[flaming]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/boss/enderdragon/phases/DragonSittingFlamingPhase.java
[fireball]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/DragonFireball.java
[immune]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/dragon_immune.json
[respawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/dimension/end/DragonRespawnAnimation.java
[bottle]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BottleItem.java#L29-L46
