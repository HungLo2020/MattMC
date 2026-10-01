# Gazelle

Gazelle is a passive herd animal integrated from Alex's Mobs. It has panic, wandering, temptation, breeding, and parent-following goals, but no attack goal in the checked goal list. Damaging one can make nearby gazelles flee as well.

## At a glance

- Entity ID: `minecraft:gazelle`
- Health: **8 points** (4 hearts)
- Registered size: **0.9 × 1.3 blocks**
- Ordinary movement-speed attribute: **0.25**
- Running movement-speed attribute: **0.475**

These speed values are internal attributes, not direct travel-speed measurements.

## Obtaining

Use the [Gazelle Spawn Egg](../items/GazelleSpawnEgg.md) in Creative or summon `minecraft:gazelle` with command permission. Natural biome placement was not established from the active biome/spawn-registration sources checked here. A registered animal and a general spawn method do not establish a particular savanna population.

## Herd behavior

When damage is successfully applied, the code sets a fleeing cooldown for gazelles in a nearby region extending 15 blocks horizontally and 7.5 vertically around the damaged animal. The cooldown is **100–249 ticks**, roughly 5–12.45 seconds at normal tick speed. While running, the movement-speed attribute rises to 0.475; after the cooldown it returns to 0.25.

Give a herd space and avoid using damage to move it into a pen. Grass-eating and ear/tail-flick animations are present, but the inspected grass animation does not establish crop harvesting or a special resource production mechanic.

## Breeding limitation

The breeding and temptation predicates refer to `minecraft:gazelle_breedables`. No bundled item-tag file defining that tag was found in this snapshot. Therefore a working accepted food is **not verified**; do not assume Wheat from an upstream guide is valid here.

The offspring method itself creates another Gazelle, but that does not complete the missing food route. No taming, riding, or dedicated death-loot table was established in this review.

## Related pages

- [Gazelle Spawn Egg](../items/GazelleSpawnEgg.md)
- [Emu](Emu.md), another herd animal with different breeding and combat behavior
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game spawning, breeding, combat, egg-throwing, or food test was run.

- [Gazelle behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityGazelle.java)
- [Registry and dimensions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L654-L660)
- [Active attributes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L169)
- [Food-tag key and namespace](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/misc/AMTagRegistry.java)
- [Spawn-placement review](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
