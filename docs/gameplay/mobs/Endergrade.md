# Endergrade

The **Endergrade** is a passive flying animal with **20 health points (10 hearts)** and no armor. You can saddle and board one, but the checked MattMC data supplies **no steering item or breeding food**. Treat it as a flying animal to keep or observe, rather than a dependable transport mount in an unchanged game. [Attributes and goals][endergrade-basics] · [Active attributes][attributes] · [Food tags][tag-keys] · [Bundled data][data]

## Obtaining

Use the [Endergrade Spawn Egg](../items/EndergradeSpawnEgg.md) from Creative for deliberate placement. Using the matching egg on an existing Endergrade creates a baby through the shared spawn-egg interaction. [Egg registration][egg-registration] · [Creative entry][creative] · [Interaction dispatch][interaction] · [Baby creation][egg-baby] · [Offspring callback][offspring]

**No natural encounter is established in this snapshot.** No Endergrade entry was found in the checked biome/structure spawn lists or spawn-placement registrations. The mob has a helper that checks for a non-air block below it and a separate spawn-roll check, but those do not add it to the lists used to choose natural spawns. Do not rely on a trip to the End to find one. [Spawn helper][endergrade-basics] · [Spawn-roll check][offspring] · [Natural selection][natural] · [Spawn placements][placements] · [Biome generation][biomes] · [Bundled data][data]

## Behavior

Endergrades have no attack or retaliation goal. They float without gravity and wander toward nearby points above the ground, choosing a clear line of sight to their destination. Their direct navigation moves toward a target rather than planning a route around obstacles, so provide open space and a roofed enclosure if you want to keep one nearby. [Goals][goals] · [Gravity][gravity] · [Flight targets and movement][flight] · [Direct navigation][navigation]

### Saddling and riding

1. Use a [Saddle](../items/Saddle.md) on an unsaddled Endergrade. This consumes one Saddle outside Creative. No taming or adult-age requirement is checked
2. Interact again without sneaking, preferably empty-handed, to board it. If it is leashed to you, the shared interaction releases the Lead first
3. Only **one passenger** can board through the ordinary interaction. A successful interaction result does not override an occupied seat or the short reboarding cooldown after dismounting. [Saddle and mounting interaction][mounting] · [Lead interaction][lead-interaction] · [Shared boarding rules][boarding] · [Passenger limit][passenger-limit]

Saddling does not tame it, assign an owner or make it obey you. [Saddle interaction][mounting] · [Goals][goals]

Steering requires a held item in `minecraft:endergrade_follows`, in either hand. **That item tag is absent from bundled data**, so no normal steering item is established, including Chorus Fruit. Merely wearing a Saddle is insufficient: without a matching item, the player is not selected as the controlling passenger. [Controller selection][controller] · [Tag definitions][tag-keys] · [Tag loading][tag-loading] · [Bundled data][data]

If a server data pack supplies a steering item, the current shared riding dispatcher calls the Endergrade's movement controls. Hold forward or backward and look up or down to supply vertical movement; strafe input is included only while forward/backward input is nonzero. It turns with the rider while movement input is held. There is no separate ascent or descent key in these controls. A boost routine exists, but no player action calling it was found, so no boost-use instruction is established here. [Riding dispatch][riding-dispatch] · [Shared ridden movement][ridden-movement] · [Endergrade controls][controls] · [Boost routine][boost] · [Food-on-a-stick gate][stick]

### Feeding and breeding

The following behaviors depend on tags that are **not supplied by the checked bundled data**. A server data pack can provide them; the implementation does not substitute a default food or block. [Tag definitions][tag-keys] · [Tag namespace][tag-namespace] · [Tag loading][tag-loading] · [Bundled data][data]

- `minecraft:endergrade_breedables` items attract Endergrades and are hand-fed breeding food. With a supplied food, two eligible adults use ordinary animal breeding and produce a baby Endergrade; feeding a baby speeds growth. This does not tame either animal. [Goals][goals] · [Food test][mounting] · [Animal feeding][animal-feeding] · [Breeding][animal-breeding] · [Offspring][offspring]
- Feeding a breedable item to an Endergrade with **Poison** instead consumes one item outside Creative, heals **8 health points (4 hearts)** and removes Poison before normal breeding is considered. The code calls this effect “Ender Flu,” but MattMC maps that name to vanilla Poison. [Special feeding][mounting] · [Effect mapping][effect]
- `minecraft:endergrade_foodstuffs` items dropped nearby can be picked up, removing one from the dropped stack and healing **5 health points (2½ hearts)**. The mob puts that item in its main hand, and this goal refuses further pickups while that hand is occupied. Its own code has no routine that eats away or clears the held item, so do not assume repeatable dropped-food healing. [Pickup conditions and item transfer][pickup] · [Healing][healing]
- Adults can approach and break blocks in `minecraft:endergrade_breakables`, with normal block drops requested. No block is established by bundled data, including Chorus Flowers. This is block destruction, not crop fertilization. [Block-breaking goal][breaking]

### Keeping one

An Endergrade does not use ordinary distance-based despawning: it inherits the Animal rule that returns false for distance removal. Its saddle state is saved, and its goals contain no owner-following or sit command. A [Lead](../items/Lead.md) and an enclosed space are more useful for keeping it nearby than repeatedly trying foods. [Animal persistence][animal-persistence] · [Despawn checks][despawn] · [Saved saddle][saving] · [Goals][goals] · [Leash eligibility][leashing]

The mob bypasses its normal landing-fall check. This does not make it immune to every damage source or protect a player who dismounts in midair; land before getting off. [Endergrade fall handling][fall-methods] · [Shared fall damage][fall-dispatch]

## Notes

A saddled Endergrade drops **one Saddle on death** through its active equipment-drop callback. Its saddle is stored as a separate flag, not an equipped item stack; no ordinary interaction to remove it while alive is implemented, and the shared Shears equipment-removal path does not clear that flag. [Saddle drop][offspring] · [Death dispatch][death-dispatch] · [Saved saddle][saving] · [Saddle interaction][mounting] · [Shears equipment removal][shears]

No Endergrade death-loot table is supplied in bundled data, so no ordinary species-specific material drop, count or Looting bonus is established. The separate Saddle return does not depend on that missing table. [Default loot-table naming][entity-defaults] · [Death dispatch][death-dispatch] · [Bundled data][data]

Registered as `minecraft:endergrade`, using `MobCategory.CREATURE`, with a normal adult size of **1.2 × 1.2 blocks**. Its spawn egg is `minecraft:endergrade_spawn_egg`. This is bundled Alex's Mobs content integrated into MattMC; behavior from other versions is not evidence for this integration. [Entity registration][entity] · [Egg registration][egg-registration]

Related: [Endergrade Spawn Egg](../items/EndergradeSpawnEgg.md) · [Saddle](../items/Saddle.md) · [Lead](../items/Lead.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active registration, spawn wiring, tags, goals, food, shared interaction and riding dispatch, current method signatures, persistence and loot. No in-game spawn, mounting, steering, feeding, breeding, damage or drop test was run. Server data packs may supply missing tags or loot.

[endergrade-basics]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L51-L79
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L159
[tag-keys]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L27-L31
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[egg-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1866
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2013
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1104
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L185
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L301-L316
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/biome
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L101-L140
[gravity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L216-L218
[flight]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L359-L467
[navigation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/DirectPathNavigator.java#L23-L35
[mounting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L167-L197
[lead-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2178
[boarding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2283-L2329
[passenger-limit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2372-L2391
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L555-L561
[entity-defaults]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2050-L2073
[controller]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L142-L153
[tag-loading]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L142-L168
[riding-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2821-L2831
[ridden-movement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2422-L2442
[controls]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L318-L352
[boost]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L228-L237
[stick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/FoodOnAStickItem.java#L13-L37
[tag-namespace]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L250-L255
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[animal-breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[effect]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/effect/AMEffectRegistry.java#L13-L17
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/EndergradeAITargetItems.java#L51-L125
[healing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L287-L299
[breaking]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/EndergradeAIBreakFlowers.java#L22-L80
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L631
[saving]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L81-L91
[leashing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[fall-methods]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEndergrade.java#L265-L270
[fall-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1730
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1478
[shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2211-L2230
