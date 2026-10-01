# Roadrunner

Roadrunner is a fast, breedable bird integrated from Alex's Mobs. It can retaliate when hurt and alert other roadrunners, while also using panic, wandering, and parent-following goals. Living adults periodically shed a special feather.

## At a glance

- Entity ID: `minecraft:roadrunner`
- Health: **8 points** (4 hearts)
- Registered size: **0.7 × 0.9 blocks**
- Base movement-speed attribute: **0.45**; this is an internal attribute, not a blocks-per-second measurement

The close-range attack animation applies 2 damage points in its current hit code, even though the registered attack-damage attribute is 1. Combat and ported attack-method integration have not been tested here, so these numbers are not a measured combat guarantee.

## Obtaining and breeding

Use the [Roadrunner Spawn Egg](../items/RoadrunnerSpawnEgg.md) in Creative or summon `minecraft:roadrunner` with command permission. Natural spawning is not established by the reviewed biome/spawn-registration data. Its standalone suitable-ground predicate does not prove placement in a desert or any other biome.

The bundled breeding-food tag accepts **Wheat Seeds, Beetroot Seeds, Melon Seeds, and Pumpkin Seeds**. Holding an accepted seed attracts the bird. Feeding ready adults uses the ordinary Animal breeding path, and the offspring method creates another Roadrunner. That path gives the parents a 6,000-tick breeding cooldown.

No owner-taming or riding interaction is established in this class. Breeding a bird and owning a tame companion are different mechanics.

## Feather collection

A living adult drops one [Roadrunner Feather](../items/RoadrunnerFeather.md) when its saved timer reaches zero, then resets it to **24,000–47,999 ticks**. That is approximately 20–40 minutes at normal tick speed while the animal is ticking. Unloaded time does not advance this timer.

This is a periodic living-mob drop. No dedicated Roadrunner death-loot table was found in the bundled entity loot directory, so killing it is not presented as the verified collection method.

## Name interaction

The current name check ignores case and formatting and looks for the substring **“meep”**. A matching name raises the movement-speed attribute from 0.45 to **1.0** and selects the special meep sound. Removing the match restores the ordinary speed. Use an enclosure when experimenting with the faster bird.

## Related pages

- [Roadrunner Feather](../items/RoadrunnerFeather.md)
- [Roadrunner Spawn Egg](../items/RoadrunnerSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Mob behavior, timer, breeding and name interaction](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityRoadrunner.java)
- [Active attributes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L222)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L1119-L1124)
- [Breeding foods](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/roadrunner_breedables.json)
- [Tag namespace](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/misc/AMTagRegistry.java)
- [Animal breeding cooldown](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/Animal.java)
- [Spawn-registration review](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
