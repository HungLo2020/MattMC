# Jerboa

The **Jerboa** (`minecraft:jerboa`) is a small, hopping animal with **4 health points (2 hearts)**. Befriend it with seeds to prevent ordinary distance despawning. Once befriended and healthy, its seed interaction can grant **Speed I**. Ordinary food breeding is blocked by a bundled tag error in this snapshot. [Attributes, interactions, and persistence][jerboa]

## Availability in this snapshot

The entity, attributes, and [Jerboa Spawn Egg](../items/JerboaSpawnEgg.md) are registered. No Jerboa spawn entry was found in the 68 bundled biome files, and its sky/light spawn helpers are not registered with the active spawn-placement system. They do not establish natural desert or nighttime spawning. Use a Creative egg or an administrator-provided Jerboa to access the behavior below. [Registration][registration] · [Attributes][attributes] · [Placements][placements] · [Natural spawn selection][natural]

Its registered body is **0.5 × 0.5 blocks**. Give it a protected enclosure: its health is low, and befriending does not make it immune to predators. [Registration][registration]

## Seeds, befriending, and healing

The bundled begging-food tag accepts:

- Wheat Seeds
- Beetroot Seeds
- Melon Seeds
- Pumpkin Seeds
- Torchflower Seeds
- Pitcher Pods

Holding one in either hand can attract the Jerboa and make it beg nearby. Its begging goal checks the nearest player within 32 blocks, so another closer player without suitable food can affect whether it approaches you. [Begging-food tag][beg-food] · [Seed contents][seeds] · [Begging goal][beg]

Use an accepted item directly on the Jerboa:

| Jerboa state | Result |
| --- | --- |
| Not yet befriended, or below maximum health | Consumes one item outside Creative, sets its befriended flag, and heals 4 health points up to its maximum |
| Already befriended and at full health | Tries the Speed reward described below; this branch does not consume the held seed/pod |

The first successful feeding has no random taming roll. With its normal four-point maximum, that healing restores a surviving injured Jerboa to full health. Befriending is a saved flag, **not ownership assigned to one player**: it stops starting its normal player-avoidance goal for everyone and provides no owner-follow, sit-command, or player riding interaction. [Interaction][interaction] · [Saved state and avoidance][goals-save]

## Speed reward

Use a begging-food item on a full-health, befriended Jerboa. The current interaction has an approximately **30% chance** to give the interacting player **12,000 ticks of Speed I**, about **10 minutes at 20 TPS**. A failed roll can be tried again, and this reward branch contains neither item consumption nor a reward cooldown. It also wakes the Jerboa. [Reward interaction][interaction] · [Default effect level][effect-instance]

The source calls this reward “Fleet Footed,” but that name currently resolves to the ordinary registered **Speed** effect. Speed I supplies a **+20% movement-speed attribute modifier**. No separate custom Fleet Footed effect is established by this alias. [Effect alias][alias] · [Speed modifier][speed]

Do not hit the Jerboa to wake it. While it remembers a living attacker, it removes **Speed** from that attacker during its server update. Because the check uses the ordinary Speed effect, it can remove Speed obtained from another source too. The general attacker memory is normally cleared after 100 ticks, or when the attacker dies. [Attack response][attack-response] · [Attacker memory][memory]

## Breeding limitation and babies

**The six begging foods do not currently establish ordinary Jerboa breeding.** Its separate breeding tag contains the required item ID `minecraft:seeds`, which is not a registered item. It does not contain the `#minecraft:seeds` tag reference used by the working begging list. The tag loader rejects unresolved required entries, leaving no supplied breeding-food match. Feeding these seeds therefore uses the befriending/healing/reward interactions instead of love mode. [Breeding tag][breed-food] · [Food check][food] · [Item registry][items] · [Required entry handling][tag-entry] · [Tag loader][tag-loader]

A breeding goal and an actual Jerboa offspring factory do exist, but the normal food trigger is incomplete. Using a matching [spawn egg on an existing Jerboa](../items/JerboaSpawnEgg.md#using-on-a-jerboa) is a separate active route: it calls that offspring factory and creates a **befriended baby**. The factory's befriending flag also applies if server data restores an ordinary breeding route. [Goal list][goals-save] · [Offspring factory][offspring] · [Egg helper][egg-baby]

## Rest, threats, and persistence

Jerboas can fall asleep during the daytime portion of their world clock. Begging or a recent attacker wakes them; sleeping suppresses their normal movement routine. Their normal landing callback also skips the usual fall-processing path. These behaviors do not protect them from other damage. [Sleep timing][sleep] · [Movement and landing][movement]

They avoid **Cats and Ocelots**, and their active goal list has no attack goal. **Rattlesnakes hunt Jerboas** regardless of the Jerboa's befriended flag. Jerboas also appear in the active Bald Eagle prey tag and the untamed adult Caiman prey tag; a mixed enclosure is unsafe when those predators can acquire them. [Avoidance][goals-save] · [Snake prey][snake] · [Eagle prey goal][eagle-goal] · [Eagle tag][eagle-tag] · [Caiman prey goal][caiman-goal] · [Caiman tag][caiman-tag]

An ordinary unbefriended Jerboa can despawn with distance. Befriending makes it require custom persistence, and the flag survives saving and loading. This prevents normal distance despawning, but does not keep the animal inside an enclosure or prevent death. [Persistence and saved flag][goals-save] · [Shared despawn checks][despawn]

## Drops

No dedicated bundled Jerboa death-loot table or unique item drop was found. The default entity-loot key uses the empty fallback when server data supplies no table. Eligible adult deaths can still award the inherited **1–3 XP** with normal player-credit and mob-loot conditions. The useful repeatable reward here is its living interaction. [Loot key][loot-key] · [Missing-table fallback][fallback] · [Animal XP][animal-xp] · [Death conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registrations, all 68 bundled biome files, food tags and tag-loader behavior, interaction order, effect alias and removal, offspring dispatch, sleep and movement callbacks, predator goals, persistence, and loot fallback. No in-game spawning, feeding, Speed, breeding, predation, fall, or drop test was run. Data packs and custom entity data can alter the documented defaults.

Related: [Jerboa Spawn Egg](../items/JerboaSpawnEgg.md) · [Rattlesnake](Rattlesnake.md) · [Roadrunner](Roadrunner.md) · [Wheat Seeds](../items/WheatSeeds.md) · [Mobs](Mobs.md)

[jerboa]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L783-L788
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L187
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[beg-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/jerboa_begs_for.json
[seeds]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/seeds.json
[beg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/JerboaAIBeg.java#L26-L74
[interaction]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L218-L245
[goals-save]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L86-L121
[effect-instance]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L53-L63
[alias]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/effect/AMEffectRegistry.java#L19-L22
[speed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/effect/MobEffects.java#L19-L25
[attack-response]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L247-L255
[memory]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L474-L480
[breed-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/jerboa_breedables.json
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L266-L268
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[tag-entry]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/tags/TagEntry.java#L13-L79
[tag-loader]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/tags/TagLoader.java#L94-L135
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L422-L430
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[sleep]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L182-L189
[movement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L270-L409
[snake]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L62-L74
[eagle-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L132-L139
[eagle-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/bald_eagle_targets.json
[caiman-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L132-L136
[caiman-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/caiman_targets.json
[despawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L598-L629
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[animal-xp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L128
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
