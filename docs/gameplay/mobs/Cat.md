# Cat

A **Cat** is a companion you can tame with **Raw Cod or Raw Salmon**, then order to sit or let follow you. Cats have **10 health points**, or **5 hearts**, and keep their coat when tamed. They are a separate mob from [Ocelots](Ocelot.md), whose fish interaction builds trust rather than ownership. [Cat interaction and attributes][cat] · [Active registration][entities] · [Attribute wiring][attributes] · [Food tag][cat-food]

## Finding Cats

Look around **villages and Swamp Huts**. The active Overworld Cat spawner periodically checks locations near players, subject to loaded chunks, valid ground placement, and mob-spawning settings. Its interval is **1,200 game ticks**, about **one minute** at the normal tick rate; a check does not guarantee a Cat. [Spawner logic][cat-spawner] · [Overworld installation][server] · [Mob-spawning gate and dispatch][chunk-cache] · [Custom-spawner loop][server-level]

For its village route, the candidate must be near a village, have **at least five occupied home/bed points within 48 blocks**, and have **fewer than five Cats** in the checked nearby area. That Cat count includes tame pets. Merely placing five unclaimed Beds is not the same as having five occupied home points. [Village checks][cat-spawner] · [Bed home-point registration][poi]

Swamp Hut generation separately creates a persistent Cat. The custom spawner can also attempt a Cat inside a tagged hut when its checked nearby area has no Cats. The bundled structure itself additionally has a Cat creature-spawn override, subject to the natural spawning checks for that route. These are different access routes; they do not establish a guaranteed replacement rate. [Generated Cat][hut-piece] · [Active hut piece construction][hut-structure] · [Custom hut route][cat-spawner] · [Hut tag][cat-structures] · [Structure spawn override][hut] · [Override lookup][generator] · [Natural placement checks][natural]

Use the [Cat Spawn Egg](../items/CatSpawnEgg.md) for Creative placement.

## Coat variants

There are ten ordinary coat variants: **Tabby, Black, Red, Siamese, British Shorthair, Calico, Persian, Ragdoll, White, and Jellie**. **All Black** is a separate eleventh variant. The bundled selectors normally choose equally among the eligible ordinary variants; All Black joins that pool when moon brightness is at least 0.9, which corresponds to the full-moon phase in the normal phase table. [Variant definitions][variants] · [All Black selectors][black-variant] · [Selection algorithm][variant-select] · [Moon check][moon-check] · [Phase brightness][moon-phases]

At a Swamp Hut location, the All Black structure selector has higher priority. However, the special village/hut replenishment routine chooses the new Cat's coat **before moving it to the intended location**. Because of that ordering, a replacement Cat at a hut is not guaranteed to evaluate that hut's All Black rule. The structure-generation path positions its Cat before selecting the variant. [Structure selector][black-variant] · [Tagged structure][black-structures] · [Variant selection call][cat] · [Replenishment ordering][cat-spawner] · [Structure-generation ordering][hut-piece]

Coat and collar color are saved separately. A collar Dye changes the collar, not the coat. Spawn-egg components and data packs can also supply different variant data. [Variant and collar persistence][cat] · [Variant registry loading][variant-loader]

## Taming and care

1. Hold [Raw Cod](../items/RawCod.md) or [Raw Salmon](../items/RawSalmon.md) and let a stray Cat approach
2. Stay still as it gets close; abrupt movement or looking around can interrupt its food-following behavior
3. Interact to feed it, and repeat if smoke appears
4. Hearts indicate taming; the Cat records you as owner and is ordered to sit

Each ordinary taming feed consumes one fish in Survival and has a **one-in-three chance** of success. The direct Cat taming interaction does not require its temptation goal to be running, but a calm approach makes reaching the animal easier. Cooked fish and other members of a broader fish tag do not qualify: the Cat food tag contains only Raw Cod and Raw Salmon. [Food interaction and taming roll][cat] · [Food-following behavior][tempt] · [Owner assignment][tamable] · [Accepted food][cat-food]

For your injured Cat, either accepted raw fish restores **2 health points**, or **1 heart**, up to its maximum. This owner-healing action happens before breeding food handling, so feed again after it is fully healed when you want love mode. The amount comes from each fish's current food component, not from a universal healing value for every possible tagged item. [Owner healing][cat] · [Raw-fish nutrition values][foods] · [Registered food components][items]

A tamed Cat does not use the stray Cat's food-attraction goal; use its owner-following behavior or a [Lead](../items/Lead.md) to move it. Taming does not give a riding or cargo interaction. [Tame-goal changes and temptation gate][cat]

## Sitting, following, and collars

As the owner, interact with an **empty hand** to toggle the sitting order. Food or a different Dye color takes its own action first. Using a different Dye consumes one in ordinary Survival and changes the collar; new Cats default to a red collar. Other players do not receive the owner's sit-toggle or collar-recolor controls. Leash interactions can take priority over the Cat's own interaction, so release your Lead before using an ordinary interaction to change its order. [Owner interaction and collar default][cat] · [Leash priority][entity]

A Cat allowed to follow starts its owner-follow goal at about **10 blocks** away and stops that goal within **5 blocks**. At **12 blocks or more**, it can try teleporting near the owner instead of finding a walking path. Teleport candidates need walkable, collision-free space, and the checked ground cannot be Leaves. This is not guaranteed relocation through an enclosed or unsafe destination. [Follow distances and dispatch][follow] · [Teleport threshold and placement][tamable]

An ordered sit, riding in another entity, leash state, or a spectator owner blocks the ordinary follow/teleport route. The teleport method moves within the Cat's current level; it is not a cross-dimension travel command. Keep the Cat near you while changing dimensions and check it after travel. [Movement restrictions and teleport action][tamable]

Cats can also choose to lie on Beds or sit on an unopened ordinary Chest, a lit Furnace, or a Bed's foot when there is space above. **Sitting by choice is different from an owner-issued sitting order.** A sitting Cat directly above a Chest can block its normal opening and container access. Guide it away before using the Chest. [Bed goal][bed-goal] · [Block-sitting goal][block-goal] · [Chest obstruction][chest]

## Sleep and morning gifts

A tame Cat that is not ordered to sit can approach its sleeping owner on a Bed when within **10 blocks**. Another nearby Cat already lying or relaxing at the target can prevent it from using that space. Allow room beside the Bed and let the Cat move freely if you want to observe this behavior. [Owner-relax goal][cat]

When that goal ends, the gift check requires the owner's sleep counter to be at least 100, the world's time-of-day value to be in its narrow morning window, and a **70% random roll** to succeed. This is not a guaranteed gift every night or a blanket 70% chance whenever you touch a Bed. The Cat may reposition nearby and drops the selected item into the world rather than directly into your inventory. [Gift conditions and drop position][cat] · [Player sleep counter][player] · [Gift-loot dispatch][living]

The bundled gift table makes one selection:

| Possible gift | Relative weight |
| --- | ---: |
| Rabbit Hide | 10 |
| Rabbit's Foot | 10 |
| Raw Chicken | 10 |
| Feather | 10 |
| Rotten Flesh | 10 |
| String | 10 |
| Phantom Membrane | 2 |

A Phantom Membrane is therefore **1 of 31 weighted outcomes after a gift is awarded**. These gifts use their own loot table; they are separate from death drops. No overnight gift trial or production-rate test was run. [Morning gift table][gift]

## Breeding

Bring two **tame adults** together and feed each Raw Cod or Raw Salmon when outside the breeding cooldown. Let them move so they can approach each other. For your injured Cat, heal it first because its owner-feeding action takes priority. The Cat mating check requires tame Cats in love; it does not impose the Horse family's full-health parent rule. [Cat pairing and feeding][cat] · [Shared love and breeding][animal] · [Approach goal][breed]

The kitten takes its coat from one parent at random. It is tame and receives the **owner of the parent whose breeding action creates it**. Its collar uses the parents' dye-mixing crafting result when one exists; otherwise it chooses a parent's color. Use two Cats owned by you if you want predictable ownership. [Offspring creation][cat] · [Collar-color mixing][dyes]

A kitten starts with **24,000 game ticks** of growth, normally **20 minutes**, and the parents receive a **6,000-game-tick** cooldown, normally **5 minutes**. Feeding a baby through the shared growth handler removes approximately **10% of its remaining growth time** per accepted feed. Owner healing still takes priority if the baby is injured. [Birth and cooldown][animal] · [Baby growth calculation][age] · [Healing priority][cat]

## Other mobs, persistence, and drops

Creepers register avoidance of both Cats and Ocelots within a six-block search range. Cats also cause a nearby Phantom's checked swoop to end, using a periodic search around the Phantom. These are avoidance behaviors, not an impenetrable shield: a Creeper's swelling goal has higher priority than avoidance, and pathfinding and timing still matter. [Creeper goal priorities][creeper] · [Avoidance requirements][avoid] · [Phantom swoop check][phantom]

Untamed Cats can select Rabbits and baby Turtles on land as prey. Taming prevents those goals from selecting new prey; it does not add Wolf-style owner-defense goals. Cats are included in the bundled fall-damage-immunity tag, but can still suffer other environmental damage. [Cat goals][cat] · [Untamed target gate][prey] · [Attack dispatch][attack] · [Fall tag][fall-tag] · [Damage calculation][living]

Tame Cats do not use ordinary distance despawning. An untreated stray becomes eligible for the ordinary distance checks after **2,400 ticks**, about **two minutes** of entity ticking; this is not a timer that removes every stray at two minutes. Feeding a stray for a taming attempt marks persistence even if that attempt fails. Ownership, sitting order, coat, and collar are saved. [Cat persistence and interaction][cat] · [Distance-despawn gates][mob] · [Owner and order save data][tamable]

With normal mob loot enabled, an adult's base death loot is **0–2 String**. The table has no Looting count bonus. Babies do not produce that base death loot; eligible adult player-attributed deaths use the inherited **1–3 XP** reward. Morning gifts are the nonlethal item-producing interaction described above. [Death loot][cat-loot] · [Adult and XP gates][living] · [Animal experience][animal]

## Related pages

- [Ocelot](Ocelot.md)
- [Cat Spawn Egg](../items/CatSpawnEgg.md)
- [Raw Cod](../items/RawCod.md)
- [Raw Salmon](../items/RawSalmon.md)
- [Chest](../blocks/Chest.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Active spawning and goal dispatch, variant selection order, taming/food, owner controls, teleport checks, Bed/Chest behavior, gifts, offspring, loot, and persistence were inspected. No in-game spawn, tame, teleport, breeding, sleep, gift, or protective-layout test was run. Data packs, modified item components, and server rules can differ.

[cat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Cat.java
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[cat-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/cat_food.json
[cat-spawner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/CatSpawner.java
[server]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java
[chunk-cache]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerChunkCache.java
[server-level]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java
[poi]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java
[hut-piece]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java
[hut-structure]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutStructure.java
[cat-structures]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/structure/cats_spawn_in.json
[hut]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/swamp_hut.json
[generator]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[variants]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/CatVariants.java
[black-variant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/cat_variant/all_black.json
[variant-select]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/variant/PriorityProvider.java
[moon-check]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/variant/MoonBrightnessCheck.java
[moon-phases]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/dimension/DimensionType.java
[black-structures]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/structure/cats_spawn_as_black.json
[variant-loader]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/resources/RegistryDataLoader.java
[tempt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/TemptGoal.java
[tamable]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/TamableAnimal.java
[foods]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/food/Foods.java
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java
[follow]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java
[bed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/CatLieOnBedGoal.java
[block-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/CatSitOnBlockGoal.java
[chest]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ChestBlock.java
[player]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[gift]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/gameplay/cat_morning_gift.json
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[breed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[dyes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/DyeColor.java
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
[creeper]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/monster/Creeper.java
[avoid]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/AvoidEntityGoal.java
[phantom]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/monster/Phantom.java
[prey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/NonTameRandomTargetGoal.java
[attack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/OcelotAttackGoal.java
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
[cat-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/cat.json
