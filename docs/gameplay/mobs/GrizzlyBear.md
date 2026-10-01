# Grizzly Bear

The **Grizzly Bear** is a tameable bear from bundled Alex's Mobs content. Give wild adults space: their player-targeting goal can activate within **5 blocks**, without requiring you to hit them first. Being honeyed disables that proximity-targeting goal, but does not remove every reason to attack. [Targeting rules][targeting]

## At a glance

- **Health:** 55 points (27.5 hearts)
- **Base attack damage:** 8 points (4 hearts), before combat modifiers
- **Adult size:** 1.7 blocks wide and 1.95 blocks tall; standing height is 2.75 blocks
- **Entity ID:** `minecraft:grizzly_bear`

[Attributes and standing size][attributes] · [Entity registration][registration]

## Obtaining

Use the [Grizzly Bear Spawn Egg](../items/GrizzlyBearSpawnEgg.md) in Creative, or `/summon minecraft:grizzly_bear` with command permission. Natural spawning is **not established in this snapshot**: no grizzly entry was found in the bundled biome spawn tables or spawn-placement registrations. Do not rely on an upstream forest-biome guide to find one here. [Spawn egg registration][egg] · [Biome data][biomes] · [Spawn placements][placements]

## Taming and care

The current implementation uses **dropped fish**, rather than feeding a honey item directly:

1. Drop a fish and let the bear pick it up and finish eating. Eating takes just over 100 ticks, about 5 seconds at 20 ticks per second.
2. That fish heals 10 health points and gives the bear its honeyed state for 700 ticks, about 35 seconds.
3. Drop another fish yourself while it is still honeyed. The bear must already be honeyed **when it picks up** this fish to remember the thrower. Finishing that eligible fish gives a **30% taming chance**; repeat if needed and stay online to receive ownership.

Accepted fish are raw or cooked cod, raw or cooked salmon, pufferfish, and tropical fish. Despite the salmon-related variable name in the source, the check accepts the whole fish tag. [Eating and taming][eating] · [Pickup condition][pickup] · [Fish tag][fish]

Once you own an adult bear, interact with an empty hand to ride it; no saddle check is required. Sneak-interact with an empty hand to cycle **wander → follow → sit**. A tame bear has goals to defend its owner and target the owner's opponent. [Controls][controls] · [Follow condition][follow] · [Goals][goals]

Keep bears away from working beehives and bee nests. Adults seek hives containing honey, release their bees, drop honeycomb, and reset the hive's honey level to zero. [Hive behavior][hives]

## Breeding and drops

- Fish can put **tamed** bears into breeding mode. However, the current offspring method creates a **polar bear**, not a grizzly cub. Do not plan a grizzly breeding farm around this implementation. [Food condition][food] · [Offspring][offspring]
- A living, adult, tamed bear periodically drops one [Rabbit Hide](../items/RabbitHide.md). The timer is 24,000–47,999 ticking game ticks, approximately 20–40 minutes at normal tick speed; unloaded time does not advance it. [Hide timer][hide]
- No dedicated grizzly death-loot table was found in the bundled entity loot data. The periodic rabbit-hide drop above is the verified resource-producing behavior. [Entity loot data][loot]

## Verification scope

Checked against MattMC source and bundled data at commit `fffe4a073f0b8d867902b067a6dd022cda31926f`. These are source-defined behaviors, not a completed in-game taming, combat, or spawning test.

[attributes]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L87-L103
[registration]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/EntityType.java#L714-L720
[targeting]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L779-L795
[egg]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/item/Items.java#L1889
[biomes]: https://github.com/HungLo2020/MattMC/tree/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/worldgen/biome
[placements]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[eating]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L403-L446
[pickup]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L697-L714
[fish]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/tags/item/fishes.json
[controls]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L276-L322
[follow]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L724-L727
[goals]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L185-L209
[hives]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/ai/GrizzlyBearAIBeehive.java#L85-L129
[food]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L236-L239
[offspring]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L646-L650
[hide]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L525-L528
[loot]: https://github.com/HungLo2020/MattMC/tree/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/loot_table/entities
