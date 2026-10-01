# Wolf

The **Wolf** is a neutral animal that can become a companion, defend its owner, and wear [Wolf Armor](../items/WolfArmor.md). Tame a calm wild wolf with Bones, then carry food for healing. MattMC's bundled wolf-food tag includes fish and Rabbit Stew as well as meat, so check the current food list rather than assuming every version uses the same diet. [Taming and healing][interaction] · [Food tag][food-tag]

## At a glance

- **Wild health:** 8 points (4 hearts)
- **Tamed health:** 40 points (20 hearts); successful taming fills this health
- **Base attack damage:** 4 points (2 hearts), before combat modifiers
- **Adult size:** 0.6 blocks wide × 0.85 blocks tall
- **Base movement-speed attribute:** 0.3, not a blocks-per-second measurement
- **Entity ID:** `minecraft:wolf`

The base attributes are actively registered, and taming applies the higher maximum health. [Attributes][attributes] · [Attribute registration][active-attributes] · [Taming health][tame-health] · [Entity size][registration]

## Finding wolves and their variants

Natural spawning is wired: the active placement registry calls the Wolf spawn check, and the bundled biome files contain Wolf populations. The ground must be in the wolf-spawnable tag, currently **Grass Block, Snow, Snow Block, Coarse Dirt, or Podzol**, with raw brightness above 8. Other normal spawn checks still apply, so suitable terrain does not guarantee an immediate spawn. [Spawn registration][placement] · [Ground check][spawn-check] · [Ground tag][ground-tag] · [Brightness][brightness]

The checked biome spawn entries and matching appearance selectors give these places to look:

- **Taiga:** Pale
- **Forest:** Woods
- **Grove:** Snowy
- **Snowy Taiga:** Ashen
- **Old Growth Pine Taiga:** Black
- **Old Growth Spruce Taiga:** Chestnut
- **Savanna Plateau:** Spotted
- **Sparse Jungle:** Rusty
- **Wooded Badlands:** Striped

The appearance selectors for Spotted, Rusty, and Striped cover broader biome tags; the list above uses the actual bundled Wolf spawn entries, rather than treating every matching biome as a population. Natural packs share an appearance selected during spawning. These variants do not change the health or attack values listed here. [Biome spawn data][biomes] · [Appearance data][variants] · [Pack selection][pack]

Creative also provides the [Wolf Spawn Egg](../items/WolfSpawnEgg.md), and `/summon minecraft:wolf` is available with command permission. [Spawn-egg registration][egg]

## Taming and owner controls

1. Find a wolf that is **not angry**
2. Interact with it using a [Bone](../items/Bone.md). Each accepted bone gives a **one-in-three chance** of taming; there is no guaranteed number of bones
3. A successful attempt assigns ownership, clears its target, stops navigation, and orders it to sit

Food does not replace Bones for taming. Angry wolves reject this taming branch, and hitting one can alert others. [Taming][taming] · [Targeting][goals]

Once it is yours, use an **empty hand** to toggle between **sit** and **stand/follow**. Food, dyes, and equipment have their own interactions, so an empty hand avoids doing something else by accident. Use a dye on your wolf to change its collar color. [Commands and collar][interaction]

A standing wolf has an owner-follow goal and can try to teleport near its owner when far enough away, but it still needs a valid destination. Sitting, riding in another entity, or being leashed prevents that normal follow route. Do not count on teleporting to rescue it from every hazard or to retrieve an unloaded animal. A damage event can also cancel its sit order, so an enclosure is safer than a sit command alone. [Follow goal][follow] · [Teleport conditions][teleport] · [Damage and sitting][damage]

## Healing and food

Interacting with an injured tame wolf using an accepted food heals **twice that food's nutrition value**, capped at maximum health. Its lowering tail is a health cue; the tamed tail angle changes with missing health. [Healing][interaction] · [Tail][tail]

The bundled foods are:

- Raw or cooked **Beef, Chicken, Mutton, Porkchop, and Rabbit**
- **[Rotten Flesh](../items/RottenFlesh.md)**
- Raw or cooked **Cod and Salmon**, plus **Tropical Fish and Pufferfish**
- **[Rabbit Stew](../items/RabbitStew.md)**

Examples: Rotten Flesh heals **8 points (4 hearts)**, cooked Beef heals **16 points (8 hearts)**, and Rabbit Stew heals **20 points (10 hearts)**. Wolf feeding uses a direct healing interaction, so it does not apply the player's eating effects such as Rotten Flesh's Hunger effect. Food defined outside these tags is not automatically accepted just because its name sounds like meat. [Wolf-food tag][food-tag] · [Meat tag][meat] · [Nutrition values][nutrition] · [Healing path][interaction]

## Breeding and puppies

Heal two tame adults first, make sure they are standing and ready to breed, then feed each an accepted food. Healing takes priority while a tame wolf is injured. The mating check requires both wolves to be tamed and in love; a sitting partner is rejected. [Interaction priority][interaction] · [Mating rules][mating]

The puppy is tamed and inherits an owner from a parent. When the parents have different owners, ownership is taken from the parent whose breeding method creates the puppy, not selected by the nearest player. Appearance is chosen from either parent, and collar colors use the dye-mixing rule. The parents receive the normal **6,000-tick breeding cooldown**, about five ticking minutes at 20 TPS. Feeding a healthy puppy accepted food uses the normal growth-speedup interaction. [Offspring][offspring] · [Breeding cooldown][cooldown] · [Baby feeding][animal-food]

## Protection, behavior, and drops

Tame wolves have owner-defense and owner-target goals. Wild wolves can hunt Sheep, Rabbits, Foxes, and baby Turtles on land; wolves also target skeleton-type enemies. They do not attack every enemy their owner faces: the owner-assistance check excludes Creepers, Ghasts, and several friendly or protected targets. [Goals and prey][goals] · [Attack exclusions][targets]

Only the owner can normally equip an adult with [Wolf Armor](../items/WolfArmor.md), repair it while the wolf is sitting, or shear it off. Armor has damage-type exceptions and a MattMC-specific broken-item inconsistency; read the armor page before relying on it in combat. [Armor interactions][interaction] · [Armor absorption][armor]

The bundled Wolf death-loot table has no item pools. Wolves are not a source of meat or hides. Worn armor is handled separately as equipment, subject to normal loot rules and equipment conditions. [Wolf loot][loot] · [Equipment drops][equipment-drops]

## Related pages

- [Wolf Armor](../items/WolfArmor.md)
- [Bone](../items/Bone.md)
- [Rotten Flesh](../items/RottenFlesh.md)
- [Wolf Spawn Egg](../items/WolfSpawnEgg.md)
- [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Active attributes, actual food tags, ownership, breeding, biome populations, spawn placements, appearance selectors, and loot were checked separately. No in-game spawning, taming, healing, breeding, following, or combat test was run.

[interaction]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L449-L524
[food-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/wolf_food.json
[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L211-L213
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L271
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1535-L1542
[tame-health]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L434-L442
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L155
[spawn-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L667-L671
[ground-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/wolves_spawnable_on.json
[brightness]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L112-L114
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[variants]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/wolf_variant
[pack]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L253-L272
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java
[taming]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L505-L524
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L98-L145
[follow]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java#L35-L88
[teleport]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L239-L284
[damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L394-L402
[tail]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L539-L549
[meat]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/meat.json
[nutrition]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java
[mating]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L623-L636
[offspring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L595-L617
[cooldown]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L227
[animal-food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[armor]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L404-L432
[targets]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L642-L654
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/wolf.json
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L814-L839
