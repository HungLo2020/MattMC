# Komodo Dragon

Komodo Dragon is a predator with poisonous bites and an active passive-production route for [Komodo Spit](../items/KomodoSpit.md). Owner commands and saddle riding are implemented, but **the bundled taming and breeding food tags are missing**, so this guide does not promise a normal Survival route to those features.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:komodo_dragon` |
| Health | 30 points (15 hearts) |
| Base attack damage | 4 points (2 hearts), before modifiers |
| Armor | 0 points |
| Registered size | 1.35 blocks wide × 0.85 blocks tall |

A successful bite adds **Poison I** for 5 seconds on Easy, 10 seconds on Normal, or 20 seconds on Hard. The Komodo Dragon's current effect check rejects Poison applied to itself.

## Obtaining

Use a [Komodo Dragon Spawn Egg](../items/KomodoDragonSpawnEgg.md), explicitly listed in Creative, or summon `minecraft:komodo_dragon` with command permission.

**Natural spawning is not established.** No entry was found in the reviewed biome spawn data or spawn-placement registrations. A standalone predicate checks bright conditions and a named spawn-ground tag, but that tag's bundled file was not found either. These checks are not evidence of an available natural population.

## Food is not necessarily taming food

Wild Komodo Dragons are attracted by **[Rotten Flesh](../items/RottenFlesh.md)** through a directly configured temptation goal. Separately, they can collect dropped items with food components. The item-collection goal consumes one item and heals the animal by **10 health points**. Neither behavior tames it, and food can be consumed even when healing is unnecessary.

The actual player-taming and breeding interactions depend on `minecraft:komodo_dragon_tameables` and `minecraft:komodo_dragon_breedables`. These tag names are declared, but **no matching item-tag data files were found** in the bundled resources. Rotten Flesh's attraction role does not fill that gap.

If a data pack supplies a taming tag, the existing interaction has an important cost: it compares the held count against a random threshold of 58–72, then **discards the entire held stack**, whether taming succeeds or fails. Do not interpret the code as a gradual one-item-at-a-time taming system.

## Predation and keeping animals safe

Its goals target players and tag-selected prey, including many passive land animals, Villagers, Wandering Traders, Kangaroos, and Gelada Monkeys. Another goal targets Komodo Dragons that are babies or at **70% health or below**. Babies have a goal to flee nearby adults.

These prey and player target goals are not gated on the animal being untamed. Normal owner/alliance checks still matter, but do not assume that ownership alone makes every nearby animal safe. An additional same-owner-pet alliance helper in this class is not called by the current alliance API.

Keep Komodos separate from vulnerable livestock and young Komodos. Adult jostling is also implemented; it is not a substitute for checking actual combat targets.

## Existing owned animals: commands and riding

These controls apply only after ownership already exists, for example in a prepared test world or a world with appropriate custom data:

- With an **empty hand**, sneak-interact as the owner to cycle **wander (0) → follow (1) → sit (2)**
- Following requires command 1 and can be suspended during nearby combat
- The owner can apply a Saddle. On a saddled adult, ordinary empty-hand interaction without sneaking mounts it
- The current movement path calls its riding-input handlers. Sideways input is used only while moving forward or backward, and backward input is reduced

The ridden animal can attack a nearby creature its rider recently hurt, with a 20-tick rider-attack cooldown. Riding, collision, and dismount behavior remain untested here.

The Shears saddle-removal and rider-autoattack branches cast the world directly to a server world without client-side guards. These are source-identified test risks, not verified safe interactions. Its current death-equipment method separately drops an equipped Saddle.

## Breeding and spit

The breeding goal can produce **two or three live babies**, either with a partner or from a single animal already in love. However, the normal food route requires a tame animal and the missing breeding tag. Offspring creation does not assign an owner, so do not assume the babies inherit ownership. This is source-defined behavior, not a demonstrated farm procedure.

Alive adults produce one **Komodo Spit** item after **24,000–35,999 ticking game ticks**, then reset that random timer. That is about 20 to just under 30 minutes at 20 ticks per second. It works for wild or tame adults; babies do not count the timer down. The timer is saved with the animal.

No dedicated Komodo Dragon death-loot table was found. Spit is established by the living animal's timer, not by assuming a death drop.

## Related pages

- [Komodo Spit](../items/KomodoSpit.md)
- [Rotten Flesh](../items/RottenFlesh.md)
- [Rhinoceros](Rhinoceros.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, taming, breeding, riding, poison, saddle-removal, or spit-production test was run. Data packs can change tags and availability.

- [Stats, targeting, taming, owner controls, poison, and spit timer](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKomodoDragon.java)
- [Tag names and namespace](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/misc/AMTagRegistry.java)
- [Bundled item tags](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item)
- [Bundled block tags](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block)
- [Komodo prey tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/komodo_dragon_targets.json)
- [Passive-land-animal prey](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/passive_land_animals.json)
- [Wild-only Rotten Flesh temptation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/TameableAITempt.java)
- [Dropped-food consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java)
- [Partner and single-parent breeding goal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/KomodoDragonAIBreed.java)
- [Ordinary offspring creation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L229)
- [Current alliance dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2631-L2643)
- [Inherited tame-animal alliance checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L198-L211)
- [Current riding dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2422-L2442)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java)
- [Attribute registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java)
- [Creative spawn eggs](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Spawn-placement registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome)
- [Entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
