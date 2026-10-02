# Crocodile

The **Crocodile** (`minecraft:crocodile`) is an armored, semi-aquatic predator with active feeding and egg-laying behavior, but several imported systems still use placeholders. **Breeding lays a Turtle Egg, and maturing produces Turtle Scute rather than the separately registered Crocodile Scute.** This page describes those current routes rather than the upstream mod's intended progression. [Active entity implementation][croc] · [Item registration][items]

## Traits and availability

| Trait | Registered default |
| --- | --- |
| Health | 30 points, or 15 hearts |
| Armor | 8 points |
| Attack attribute | 10 points before difficulty and defenses |
| Follow-range attribute | 15 blocks |
| Adult size | 1.5 blocks wide × 0.8 blocks tall |

The entity and its attribute builder are actively registered. Its Spawn Egg is listed in Creative, and command permission allows `/summon minecraft:crocodile`. No Crocodile entry was found in the reviewed natural biome spawn data, biome-building code, or SpawnPlacements registrations. A standalone low-altitude Sand/Dirt predicate and random spawn-roll method do not establish a working natural habitat. [Entity registry][entity] · [Attributes][attributes] · [Creative egg][creative] · [Spawn placements][spawn] · [Biome data][biomes]

## Feeding and breeding

The food check accepts the bundled fish tag: **Raw Cod, Cooked Cod, Raw Salmon, Cooked Salmon, Pufferfish, and Tropical Fish**. These are loose fish items, not buckets. The ordinary animal interaction can put eligible adults into love mode and speed a baby's growth. [Food check][croc] · [Fish tag][fish] · [Animal feeding][animal]

Its active custom mating goal marks one partner as carrying an egg, clears both love states, and gives both parents a **6,000-tick breeding cooldown**. With mob loot enabled, mating supplies **1–7 experience**. The separate laying goal searches for an empty block above suitable habitat, including Sand-tag blocks, then places an ordinary **Turtle Egg** and clears its carrying flag. [Mating and laying goals][croc] · [Active base breeding dispatch][breed] · [Habitat helper][habitat]

That is not a Crocodile reproduction chain. The ordinary offspring-creation method also returns null. Do not assume the placed Turtle Egg will hatch a Crocodile or grant ownership. Current breeding, the registered mob, and a completed species-specific egg system are different things.

When an existing baby crosses into adulthood with mob loot enabled, the active age-boundary callback produces **one Turtle Scute**. The shared AgeableMob age transition invokes that callback. The code does not use the registered [Crocodile Scute](../items/CrocodileScute.md) for this reward. [Age reward][croc] · [Age-boundary dispatch][age]

## Wild and owned behavior

Wild adults can target players outside Peaceful and hunt tagged prey, including passive land animals, villagers, Kangaroos, Gelada Monkeys, Catfish, and Caimans. Babies refuse target assignment. Keep other animals apart from a wild adult; feeding it does not establish a permanent safe-pet relationship. [Target goals][croc] · [Prey tag][prey]

The class supports an existing tame/owner state, owner-defense goals, and attacks against non-Creeper monsters when tame. However, no normal taming interaction or Crocodile hatchling ownership route was established in the reviewed implementation. These controls are relevant to a prepared/custom-data animal, not a promised Survival taming method:

- Any player can feed an injured tame Crocodile an item with a food component to heal **10 health points**; this branch runs before ordinary breeding
- Its owner can use a suitable non-food interaction to toggle its custom sitting/forced-sitting state; an empty hand avoids consuming a food item
- Its travel path suppresses movement while the custom sitting state is set

The class writes a separate custom sitting field through its override instead of the superclass's ordinary ordered-to-sit flag. Do not assume every inherited sit/owner behavior follows that custom field consistently. It has no ordinary player-mounting interaction in this handler. [Interactions, sit override, and travel][croc] · [Inherited sit flag][tame] · [Sit goal][sit]

## Combat integration limits

The registered Crocodile melee goal inherits the current normal attack path, so the animal is not harmless: it can still request ordinary melee damage through the superclass implementation. Its extra lunge/grab/death-roll code needs careful distinction:

- The Crocodile's one-argument attack method is an old overload, while the current melee caller invokes the server-level/two-argument method
- The custom melee goal's two-argument reach callback is also an old overload; the current base goal calls its one-argument callback
- The lunge animation is only started by that old Crocodile attack overload in the checked source

Therefore the dramatic lunge/grab chain should not be advertised as a reliably triggered ordinary combat sequence. Tick-side animation and passenger-damage code still exists and can run if that state is established by another means. The distinction is between the active call chain and dormant triggers, not a claim of invulnerability or safe handling. [Custom goal][melee] · [Current melee dispatch][base-melee] · [Animation trigger and tick logic][croc]

## Resources and verification

No dedicated Crocodile death-loot table was found. The separate Crocodile Scute item has a Creative entry, but no checked recipe, loot award, or ordinary growth reward establishes a Survival supply. See its [item guide](../items/CrocodileScute.md).

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game spawning, feeding, egg laying, maturation, owner interaction, or combat test was run.

Related: [Caiman](Caiman.md) · [Crocodile Scute](../items/CrocodileScute.md) · [Mobs](Mobs.md)

[croc]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCrocodile.java
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1284
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L442-L444
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L146
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2001
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome
[fish]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/fishes.json
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[breed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[habitat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/block/BlockReptileEgg.java#L54-L60
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java#L97-L104
[prey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/crocodile_targets.json
[tame]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/TamableAnimal.java
[sit]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java
[melee]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/CrocodileAIMelee.java
[base-melee]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L121-L145
