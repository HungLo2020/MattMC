# Cockroach

The **Cockroach** is a passive animal with **6 health points (3 hearts)**. Living adults periodically produce [Cockroach Ootheca](../items/CockroachOotheca.md), which can be thrown to hatch more Cockroaches. Natural spawning and ordinary breeding-food data are incomplete in the checked MattMC snapshot. [Attributes][attributes] · [Goals and production][roach]

## Obtaining

Use the [Cockroach Spawn Egg](../items/CockroachSpawnEgg.md) from Creative for deliberate placement, or hatch a [Cockroach Ootheca](../items/CockroachOotheca.md). The mob and its egg projectile are registered, and both have renderer registrations. [Item registration][items] · [Creative egg entry][creative] · [Entity registration][entity] · [Renderers][renderers]

No Cockroach entry was found in the checked biome/structure spawn lists or spawn-placement registrations. Natural spawning selects from those lists. A cave/darkness/Y≤64 helper exists in the mob source, but no active caller was found; it does not establish a natural cave encounter. Do not search caves expecting a confirmed starting population. [Spawn helper][roach] · [Natural selection][natural] · [Spawn placements][placements] · [Biome generation][biomes] · [Bundled data][data]

## Behavior

- Cockroaches have no attack goal. Normally they flee players within **8 blocks** and seek darker shelter when their light-avoidance goal runs. The special "breaded" state disables those two avoidance goals. [Goals][goals] · [Light avoidance][light]
- Drop an edible item nearby to let a Cockroach seek it out. Eating one dropped item heals **5 health points**, up to its maximum; the item is consumed even when thrown by a Creative player. This is separate from hand-feeding for breeding. [Food targeting and healing][feeding] · [Dropped-item consumption][pickup]
- Use a Name Tag for a Cockroach you want to keep. Ordinary Cockroaches may despawn at a distance; a custom name, breaded state, dancing, Maracas or headless state requests persistence. Breaded does not mean owner-tamed. [Persistence][persistence]
- They resist fall, drowning, suffocation and explosion damage, plus damage identified as an anvil. They are not invulnerable to every source of damage. [Damage exclusions][damage]

## Ootheca and breeding

Every living adult has an egg timer of **24,000–47,999 ticks**, approximately **20–40 minutes while ticking at normal speed**. When it expires, the adult drops one ootheca and resets the timer. This needs no mate, food, Maraca or nest. See [Cockroach Ootheca](../items/CockroachOotheca.md) for throwing, the equal chances of **0–2 hatchlings**, growth time and the Dispenser limitation. [Egg timer][production]

Hand-fed breeding checks the `minecraft:cockroach_breedables` item tag; held-food attraction and the dropped-food breaded effect use `minecraft:cockroach_foodstuffs`. Neither tag is supplied in the checked bundled data. These tests use loaded tag membership, with no substitute food in the Cockroach code. Consequently, no ordinary breeding food, lure or food that creates the breaded state is established here. Dropped food can still heal through its food component. [Food tests][feeding] · [Breeding test][breed-food] · [Tag loading][tags] · [Bundled data][data]

The offspring callback does create a breaded Cockroach, so a data pack that supplies a breeding food can enable that path. Using the matching spawn egg on a Cockroach also calls this offspring routine and makes a breaded baby. Ootheca hatchlings use a different path and do not start breaded. [Offspring state][offspring] · [Spawn-egg offspring][egg-offspring] · [Mob interaction dispatch][interaction] · [Ootheca hatching][hatch]

## Maracas, dancing and shearing

Use a [Maraca](../items/Maraca.md) on a living Cockroach to equip it. This spends one in Survival; the normal player-interaction path restores the count in Creative. Interact empty-handed to retrieve the Maraca as a dropped item; if the Cockroach is leashed to you, the first interaction releases the Lead. [Maraca interaction][maraca] · [Creative restoration][player] · [Lead interaction][lead]

A Maraca-bearing Cockroach dances and encourages nearby Cockroaches to dance. Followers check that their tracked musician is alive, still equipped and within **10 blocks**, but the check does not clear dancing if the musician can no longer be found. Dancing suppresses travel. Nearby Jukebox playback also triggers the local dance animation, but the custom Maraca music event has no playback implementation in this snapshot. [Dance behavior][dance] · [Travel and empty music handler][roach] · [Jukebox notification][jukebox]

A [Dispenser](../blocks/DispenserAndDropper.md) using Shears can make a living adult headless when it still has a head. It **does not drop wings or fragments**. This callback is wired through the Dispenser; ordinary handheld Shears do not have a Cockroach-specific shearing interaction. Damage that leaves a Cockroach at 1 health point or less can also trigger the headless state. [Shearing and damage][roach] · [Dispenser shearing][shears] · [Handheld Shears][hand-shears]

## Notes

[Cockroach Wing](../items/CockroachWing.md) and [Cockroach Wing Fragment](../items/CockroachWingFragment.md) are plain registered items. No active wing shedding, shearing reward or bundled wing/fragment recipe was found. The mob selects its ordinary death-loot table or one of two Maraca-specific tables, but all three Cockroach tables are absent from bundled data. Killing Cockroaches is therefore not an established source of those materials. [Loot selection][loot] · [Item registration][items] · [Bundled data][data]

Registered as `minecraft:cockroach`, with a normal adult size of **0.7 × 0.4 blocks**. [Entity registration][entity]

Related: [Cockroach Ootheca](../items/CockroachOotheca.md) · [Cockroach Wing](../items/CockroachWing.md) · [Cockroach Wing Fragment](../items/CockroachWingFragment.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active registrations, callers, goals, food/breeding tags, egg production and hatching, loot, shearing, Creative consumption and bundled data. No in-game spawn, feeding, breeding, dance, drop or rendering test was run. Server data packs can supply missing food, recipes or loot.

[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L140
[roach]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1821-L1824
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1995
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L373-L386
[renderers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L120-L121
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[biomes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/biome
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L119-L158
[light]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AnimalAIFleeLight.java#L39-L81
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L403-L429
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L130-L146
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L103-L108
[damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L193-L196
[production]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L326-L333
[breed-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L157-L159
[tags]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L142-L168
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L375-L383
[egg-offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L185
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1048-L1104
[hatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroachEgg.java#L40-L57
[maraca]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L198-L214
[player]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L889
[lead]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2159
[dance]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L271-L347
[jukebox]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/LevelEventHandler.java#L679-L687
[shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L53-L67
[hand-shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ShearsItem.java
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L177-L183
