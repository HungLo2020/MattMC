# Panda

Bring **Bamboo** to find, move and breed Pandas. They are untamed animals with inherited personalities: some are slower or nervous, while aggressive Pandas can keep fighting and help another Panda that was hurt. A breeding area needs nearby **planted Bamboo stalks**, as well as food for both parents. [Active goals][panda-goals] · [Interaction][panda-use] · [Breeding check][panda-breed] · [Retaliation][panda-retaliate]

## Obtaining

### Finding Pandas

Search [Jungle or Bamboo Jungle](../biomes/JunglesAndSwamps.md). Both bundled creature lists request groups of **1–2 Pandas**, but Bamboo Jungle gives Panda weight **80**, compared with **1** in Jungle. These are candidate weights, not percentages or a guaranteed count. **Sparse Jungle has no Panda entry.** [Jungle list][jungle] · [Bamboo Jungle list][bamboo-jungle] · [Sparse Jungle list][sparse-jungle]

Ordinary natural spawning uses the animal check: **Grass Block below and raw brightness at least 9**, plus the caller's space and obstruction checks. The Panda placement registration uses no additional ground-placement restriction, but still calls that animal predicate. Walking into a dark Bamboo thicket does not override these conditions. [Registered predicate][panda-placement] · [Ground and light][animal-spawn] · [Ground tag][animal-ground] · [Natural caller][spawn-rules]

The [Panda Spawn Egg](../items/PandaSpawnEgg.md) is another route. Ordinary listed items can be inserted through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative; that is separate from wild spawning.

## Behavior

### Moving and keeping them

Hold **[Bamboo](../blocks/Bamboo.md)** to tempt a Panda. Pandas explicitly **cannot be attached to a Lead**, and feeding one does not tame it or create a follow-owner/sit command. Prepare a clear route and an enclosure before moving a pair. Sitting, eating, rolling, lying on its back or being frightened can interrupt its ordinary actions. [Temptation and installed goals][panda-goals] · [Food tag][panda-food-tag] · [Lead rejection][panda-lead] · [Action gate][panda-action]

Most Pandas have **20 health points (10 hearts)**; weak Pandas have **10 (5 hearts)**. Bamboo feeding and the eating animation do not contain a healing action. Prevent falls and other hazards instead of treating Bamboo as medicine. Pandas inherit the animal rule that prevents ordinary distance despawning. [Registered attributes][panda-parrot-attrs] · [Panda attributes][panda-stats] · [Shared animal attributes][animal-attrs] · [Mob attributes][mob-attrs] · [Health attribute][living-attrs] · [Default health][health-default] · [Weak adjustment][panda-variant-stats] · [Feeding][panda-use] · [Eating][panda-eating] · [Persistence][animal-persist]

### Breeding with Bamboo

1. Bring two adult Pandas together and plant **at least one actual Bamboo stalk** near their standing area
2. Feed each parent Bamboo while it is ready to breed
3. Leave enough room for them to approach each other; their breeding goal needs them within 3 blocks to finish

The special search accepts the first `minecraft:bamboo` block it finds within **7 blocks in each horizontal direction**, at the Panda's feet level or **one or two blocks above**. A Bamboo shoot is a different block and does not pass this check. The source does not count eight stalks. If the search fails, the Panda shows its unhappy response instead of starting the breeding goal. [Food interaction][panda-use] · [Bamboo search][panda-breed] · [Pairing and approach][breed-goal]

Successful breeding creates one cub and gives both parents a **6,000-tick cooldown**, about **5 minutes** while ticking. A cub normally takes **24,000 ticks**, about **20 minutes**, to mature. Feeding a cub Bamboo removes roughly **10% of its remaining growing time**, rounded to whole seconds. Feeding an adult that is already in love or on cooldown can instead start its seated eating behavior when it is not already sitting or in water. [Cub factory][panda-offspring] · [Breeding completion][animal-breed] · [Age progression][age] · [Panda feeding branches][panda-use]

### Personalities and cubs

| Visible personality | What matters when caring for it |
| --- | --- |
| Normal | Ordinary movement and retaliation behavior |
| Lazy | Slower movement; can lie on its back. Interacting makes a resting Panda get up before other food behavior is processed |
| Worried | Avoids players and monsters when able to act; thunderstorms frighten it, and it refuses the normal food interaction while scared |
| Playful | Can roll as an adult; cubs of other personalities can also roll. Keep rolling space away from drops |
| Brown | A recessive appearance, with no special care action in the checked goals |
| Weak | Half the usual maximum health; weak cubs have an additional sneeze opportunity |
| Aggressive | Can continue retaliation after a bite and join another Panda's retaliation |

[Personality selection][panda-genes] · [Attribute changes][panda-variant-stats] · [Resting][panda-lazy] · [Interaction order][panda-use] · [Avoidance][panda-avoid] · [Thunder response][panda-weather] · [Rolling][panda-roll] · [Sneezing][panda-sneeze] · [Retaliation][panda-retaliate]

Each Panda stores a main gene and a hidden gene. **Brown and weak appear only when both genes match that recessive personality**; a recessive main gene paired with another hidden gene appears normal. The other personalities follow the main gene. A cub receives one randomly selected gene from each parent, with a separate **1-in-32 mutation roll for each inherited gene**. Matching-looking parents therefore do not guarantee an identical cub. Genes are saved. [Visible-gene rule][panda-genes] · [Inheritance and mutations][panda-inherit] · [Saved genes][panda-gene-save]

### Eating dropped Bamboo and Cake

An adult's sitting goal can seek dropped **Bamboo or Cake**. Its pickup path takes the **whole dropped stack** into its hand, and completing the eating action clears that held stack. Drop single items if you want to avoid losing a stack. Ordinary pickup is gated by `mobGriefing`; direct Bamboo interaction uses a separate path. Cake is in the ground-eating tag, but not the breeding/tempting food tag. [Ground foods][panda-ground-food] · [Breeding food][panda-food-tag] · [Seek and sit][panda-sit] · [Whole-stack pickup][panda-pickup] · [Stack consumption][panda-eating] · [Pickup game rule][pickup-gate]

### Bites and sneezes

Hurting a Panda can provoke a bite with a **base attack attribute of 6**; final damage can change with combat rules and protection. A non-aggressive Panda ends its retaliation after a bite, while an aggressive one does not use that single-bite stop. Offering Bamboo during a target episode sets the flag that ends the retaliation goal, provided the interaction reaches its food branch. [Attack attribute][panda-stats] · [Bite flag][panda-lead] · [Retaliation stop][panda-retaliate] · [Bamboo response][panda-use]

Cubs can sneeze. Each completed sneeze executes the bundled gift table when `doMobLoot` is enabled: **one Slimeball with a 1-in-700 chance**, otherwise nothing. Nearby eligible adults may jump in response. This is a chance per sneeze, not a steady production rate. [Sneeze goal][panda-sneeze] · [Sneeze completion and gate][panda-sneeze-drop] · [Gift table][sneeze-loot] · [Default item weight][loot-weight]

## Notes

- Entity ID: `minecraft:panda`; registered adult size **1.3×1.25 blocks** [Registration][panda-reg]
- An adult's normal death table supplies **one Bamboo**, without a Looting multiplier; normal mob loot must be enabled. Cubs skip the ordinary death table [Loot][panda-loot] · [Adult/game-rule gate][loot-gate] · [Loaded death table][loot-load]
- Uneaten ground-picked items are marked for guaranteed equipment drops, separate from the one-Bamboo base table [Pickup][panda-pickup] · [Equipment drop path][equipment-drops]

## Related pages

- [Bamboo cultivation](../blocks/Bamboo.md)
- [Jungle and Bamboo Jungle](../biomes/JunglesAndSwamps.md)
- [Panda Spawn Egg](../items/PandaSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. Checked the active registrations, loaded biome and item data, relevant AI and interaction callers, shared breeding/retention rules, and loot resolution. No in-game spawning, feeding, breeding, transport, planting, combat, sound or drop test was run. Tick timings assume a ticking server at 20 ticks per second; data packs, settings and custom entity data can change the result. The biome inventory covered all 68 bundled biome definitions.

[panda-goals]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L260-L278
[panda-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L606-L645
[panda-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L785-L833
[panda-retaliate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L835-L859
[jungle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/jungle.json#L1-L226
[bamboo-jungle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/bamboo_jungle.json#L1-L226
[sparse-jungle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/sparse_jungle.json#L1-L213
[panda-placement]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L169-L169
[animal-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[animal-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json#L1-L5
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L253-L287
[panda-food-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/panda_food.json#L1-L5
[panda-lead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L313-L325
[panda-action]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L679-L690
[panda-parrot-attrs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L205-L206
[panda-stats]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L280-L282
[animal-attrs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L49-L51
[mob-attrs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L159-L161
[living-attrs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L325-L335
[health-default]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[panda-variant-stats]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L588-L596
[panda-eating]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L392-L417
[animal-persist]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L81
[panda-offspring]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L244-L256
[animal-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L163-L227
[age]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[panda-genes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L692-L754
[panda-lazy]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L861-L891
[panda-avoid]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L771-L783
[panda-weather]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L333-L348
[panda-roll]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L979-L1017
[panda-sneeze]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L1089-L1114
[panda-inherit]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L558-L586
[panda-gene-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L230-L242
[panda-ground-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/panda_eats_from_ground.json#L1-L6
[panda-sit]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L1025-L1086
[panda-pickup]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L524-L533
[pickup-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L439-L455
[panda-sneeze-drop]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Panda.java#L499-L522
[sneeze-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/gameplay/panda_sneeze.json#L1-L20
[loot-weight]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[panda-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1011-L1013
[panda-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/panda.json#L1-L23
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L568
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
