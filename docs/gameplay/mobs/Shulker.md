# Shulker

The **Shulker** (`minecraft:shulker`) guards [End Cities](../structures/EndCity.md) with homing bullets that can make you levitate. It has **30 health points (15 hearts)** and can drop a [Shulker Shell](../items/ShulkerShell.md). Fight from a position with cover and somewhere safe to land. [Attributes and behavior][shulker] · [Registration][registration]

## Where to find one

End City templates contain sentry markers that actively create Shulkers during structure placement. The bundled ship has three such markers; city room and tower pieces have their own. These are structure inhabitants: the checked End Highlands and End Midlands biome spawn lists contain Endermen, not recurring natural Shulker spawns. Clearing a room does not make it an ordinary Shulker spawning platform. [Structure placement][city] · [Ship template][ship] · [Highlands spawns][highlands] · [Midlands spawns][midlands]

Shulkers attach to block faces and stay in place rather than walking after you. If their current position cannot support them, they try another attachment face or teleport. Their inherited distance-despawn override returns false; simply moving far away is not a way to clear the city. [Attachment and movement][shulker] · [Distance persistence][golem]

## Bullets and Levitation

A targeting Shulker opens its shell and shoots while its target is less than **20 blocks** away. Its attack begins with a 20-tick delay, then uses intervals of 20–110 ticks in 10-tick steps. These are attack-goal timings, not a guarantee that every visible Shulker continuously fires on that schedule. The player-targeting and firing goals do not run in Peaceful. [Attack goals][shulker]

A bullet requests **4 damage points before difficulty and defenses**. If the hit is accepted, it gives a living target **200 ticks of Levitation**, or 10 seconds at 20 TPS. Bullets steer toward their target and can change direction around obstacles. A block impact destroys the bullet, and directly damaging a bullet destroys it too. Use solid cover and strike an approaching bullet when you can do so safely. [Projectile behavior][bullet] · [Damage scaling][damage]

Levitation can lift you above a staircase or roof and leave you with a dangerous fall afterward. **Elytra cannot start or continue gliding while Levitation is active.** Stay over a solid landing area rather than expecting wings to rescue you immediately. [Glide conditions][flight]

## Shell defenses and teleporting

The closing-shell handler applies a **20-point armor modifier**, which it removes when opening. A fully closed Shulker also rejects the arrow-family projectile class, including thrown Tridents. Wait for an opening if attacking with those projectiles. Its entity registration is fire-immune. These defenses do not establish immunity to every possible damage source. [Shell handling][shulker] · [Trident class][trident] · [Fire immunity][registration]

After an accepted hit leaves it below half health, a Shulker has a **1-in-4 chance to attempt teleporting**. A teleport attempt samples up to five positions, each offset by at most eight blocks on each axis. It needs empty space, a usable attachment face, room to open, and a position inside the world border. It can fail to find a destination; eight blocks is an axis limit, not a spherical radius. [Damage and teleport checks][shulker]

Recheck walls and ceilings when a wounded Shulker disappears. Removing its support is not a reliable way to keep it within reach.

## Shell drops

With mob loot enabled, the bundled death table rolls for **one shell or none**:

| Applicable Looting level | Chance of one shell |
| --- | ---: |
| None | 50% |
| I | 56.25% |
| II | 62.5% |
| III | 68.75% |

Looting increases the chance, not the number of shells in a successful drop. This table has no player-kill-only condition; its Looting predicate reads the attacking living entity's applicable equipment enchantment. The normal XP reward is **5 points** when the player-credit and mob-loot conditions are satisfied. [Shell table][loot] · [Looting predicate][looting] · [Death loot and XP][death]

Two shells and a Chest make one Shulker Box; the [shell item page](../items/ShulkerShell.md#crafting-a-shulker-box) gives the arrangement.

## Bullet-driven duplication

An **open** Shulker hit by a Shulker bullet can teleport and create another Shulker at its old position. This requires an accepted hit and a successful teleport, and nearby living Shulkers can suppress the new spawn. The low-health random-teleport branch is checked first, so not every qualifying-looking hit reaches duplication. There is no food-breeding interaction in this route. [Duplication branch][shulker]

This is a source-confirmed renewal mechanism, not a tested farm design or a guaranteed extra Shulker per bullet.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Structure markers, active entity/attribute registration, combat callbacks, projectile tags, and loot loading were traced. No in-game spawn, combat, teleport, duplication, drop-rate, or farm test was run. Custom mob data, game rules, enchantments, and data packs can alter results.

Related: [End City](../structures/EndCity.md) · [Shulker Shell](../items/ShulkerShell.md) · [Elytra](../items/Elytra.md) · [Mobs](Mobs.md)

[shulker]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/monster/Shulker.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1192-L1198
[city]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L369-L387
[ship]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/end_city/ship.nbt
[highlands]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json
[midlands]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/end_midlands.json
[golem]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/AbstractGolem.java
[bullet]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/ShulkerBullet.java
[damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/damage_type/mob_projectile.json
[flight]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2883-L2920
[trident]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L27
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/shulker.json
[looting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527

Additional wiring: [attribute registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L235), [projectile damage tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json), and [default entity-loot resource key](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066)
