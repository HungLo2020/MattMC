# Platypus

The **Platypus** is a small, semi-aquatic animal from bundled Alex's Mobs content. You can carry one in a Water Bucket and feed it fish, but its treasure-digging and egg-laying systems have important integration gaps. It does not hunt players, yet a successful direct hit from a living attacker can make that attacker receive Poison. [Interactions][interactions] · [Defensive poison][poison]

## At a glance

- **Health:** 10 points (5 hearts)
- **Adult size:** 0.8 blocks wide × 0.5 blocks tall
- **Base movement-speed attribute:** 0.2, not a blocks-per-second measurement
- **Entity ID:** `minecraft:platypus`
- **Bucket item:** `minecraft:platypus_bucket`

The health and movement attributes are actively registered. No ordinary attack-damage attribute or hunting goal is added by this class. [Attributes][attributes] · [Active registration][active-attributes] · [Entity size][registration] · [Goals][goals]

## Obtaining and moving one

Creative includes the [Platypus Spawn Egg](../items/PlatypusSpawnEgg.md), [Bucket of Platypus](../items/BucketOfPlatypus.md), and separate [Platypus Egg](../items/PlatypusEgg.md). With command permission, use `/summon minecraft:platypus`. [Creative spawn egg][creative-spawn] · [Creative bucket][creative-bucket] · [Creative egg][creative-egg]

**Natural spawning is not established in this snapshot.** A helper checks for a dirt-tag block below the mob and a position below sea level plus four, but no active spawn-placement caller or biome spawn entry was found. That helper alone does not establish a river or swamp population. [Spawn helper][spawn] · [Spawn placements][placements] · [Biome data][biomes]

Use a **Water Bucket**, not an empty Bucket, on a living Platypus to capture it. The filled bucket releases it through the normal mob-bucket flow. Released animals are marked as coming from a bucket, which suppresses their distance-despawning route. Read [Bucket of Platypus](../items/BucketOfPlatypus.md) before transporting a baby or an animal carrying an egg: age and special-state preservation are incomplete. The special-state component mismatch is tracked in [issue #782](https://github.com/HungLo2020/MattMC/issues/782); the separate age omission remains a limitation too. [Capture][capture] · [Release][release] · [Persistence][persistence]

Platypuses are not tameable through these interactions. Feeding, naming, or bucketing one does not give owner commands, a riding seat, or an inventory. [Entity and food behavior][food] · [Interactions][interactions]

## Fish, breeding, and eggs

The actual breeding-food tag accepts **raw or cooked Cod, raw or cooked Salmon, Pufferfish, and Tropical Fish**. Interact with ready adults using one of those fish to enter the normal food/breeding path. When not sensing, they are also attracted to held fish. This differs from Redstone's sensing interaction. [Food check][food] · [Fish tag][fish] · [Temptation goals][goals]

**A reliable egg-producing breeding loop is not established.** Two breeding goals are registered: a custom mating goal that marks an animal as carrying an egg, and an ordinary breeding goal whose offspring method creates another Platypus. The custom laying goal still clears the egg-carrying state and plays its laying effect, but the actual block-placement line is commented out. Do not assume breeding will reliably leave an egg or an immediate baby without an in-game test. [Goal registration][goals] · [Offspring][offspring] · [Custom mating][mating] · [Disabled placement][laying]

The **placed Platypus Egg block is registered**, despite that commented-out laying call. A Creative-supplied egg can follow its own hatching code:

- Place it on a block in the bundled **sand or dirt tags**. Sand, Red Sand, Dirt, Grass Block, and Mud are examples
- Keep the area clear of traffic. Other entities can trample the cluster; Platypuses themselves are excluded
- Random ticks advance two cracking stages, then create a baby for each egg in the cluster. An egg on unsuitable ground is destroyed on its random tick

Up to four eggs fit in one cluster. There is no fixed hatch-time guarantee, and no dedicated Platypus Egg block-loot table was found to establish safe recovery by breaking it. [Egg registration][egg-block] · [Egg behavior][egg-behavior] · [Sand tag][sand] · [Dirt tag][dirt] · [Block loot data][block-loot]

## Redstone and digging limits

Interacting with **[Redstone Dust](../items/RedstoneDust.md)** while the Platypus is not already sensing consumes one dust outside Creative and activates sensing. Holding Redstone can attract an unsensing Platypus. This is an active interaction, but it is not proof of a working treasure farm. [Redstone interaction][interactions] · [Temptation][goals]

The digging goal needs underwater ground in `minecraft:platypus_digables`. That block tag has **no bundled definition**. Its ordinary and supercharged reward tables, `alexsmobs:gameplay/platypus_reward` and `alexsmobs:gameplay/platypus_supercharged_reward`, are also absent from the checked data. No digging reward list or renewable resource output can therefore be promised. Direct Redstone feeding explicitly leaves the supercharged flag false. [Digging requirements and rewards][digging] · [Bundled block tags][block-tags] · [Bundled data][data]

Dropping Redstone is not an equivalent charging method in this snapshot: the item-targeting check selects fish, while the pickup callback's charging branch checks Redstone. Drop pickup can consume fish, but that callback does not heal the animal or put it into breeding mode. Use direct interactions for the verified food and sensing paths. [Pickup checks][pickup] · [Item consumption][item-goal]

## Care and drops

Provide water, accessible land, and an open surface. Its goals include breathing air, moving between land and water, and fleeing danger. Successfully hurting it with a direct living-entity attack applies **Poison for 100 ticks**, about five seconds at normal tick speed, to that attacker; it is not a general poison aura. [Goals][goals] · [Water behavior][water] · [Poison][poison]

No dedicated Platypus death-loot table was found. The wearable-fedora interaction and fedora equipment drop are commented out, so neither is a verified item route. [Entity loot data][loot] · [Disabled hat interaction][interactions] · [Disabled drop][drop]

## Related pages

- [Bucket of Platypus](../items/BucketOfPlatypus.md)
- [Platypus Egg](../items/PlatypusEgg.md)
- [Platypus Spawn Egg](../items/PlatypusSpawnEgg.md)
- [Caiman](Caiman.md)
- [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Registration, active attributes, actual food tags, bucket transfer, breeding goals, egg blocks, digging data, spawning, and loot were checked separately, including uppercase constants and lowercase IDs. No in-game spawning, breeding, hatching, digging, poison, or bucket round-trip test was run.

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L88-L90
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L212
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1059-L1065
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L168-L210
[creative-spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2062
[creative-bucket]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1454
[creative-egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L951
[spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L80-L86
[placements]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[capture]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L71-L88
[release]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/MobBucketItem.java#L43-L54
[persistence]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L359-L367
[food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L57-L94
[fish]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/fishes.json
[offspring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L464-L468
[mating]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L493-L528
[laying]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L530-L573
[egg-block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4786-L4796
[egg-behavior]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L48-L139
[sand]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/sand.json
[dirt]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/dirt.json
[block-loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks
[interactions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L144-L166
[digging]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/PlatypusAIDigForItems.java#L28-L65
[block-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block
[data]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data
[pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L470-L483
[item-goal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L130-L145
[water]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L444-L462
[poison]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L212-L224
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities
[drop]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L292-L299
