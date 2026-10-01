# Elephant

The **Elephant** is a large, neutral animal from bundled Alex's Mobs content. Tusked adults can retaliate and charge, so give a herd room. The source includes riding, carpet decoration, and a large chest, but **the bundled food tags needed for ordinary taming and breeding are missing**. Treat those owner features separately from having a working way to tame a wild Elephant. [Goals][goals] · [Food tags][tags] · [Bundled item tags][item-tags]

## At a glance

- **Base health:** 85 points (42.5 hearts)
- **Base attack damage:** 10 points (5 hearts), before combat conditions
- **Tusked change:** switching from untusked to tusked sets maximum health to 110 points (55 hearts) and base attack damage to 15 points
- **Adult size:** 3 blocks wide × 3.5 blocks tall; tusked adults use 3.7 × 3.75 blocks
- **Base movement-speed attribute:** 0.35, not a blocks-per-second measurement
- **Entity ID:** `minecraft:elephant`

The base attributes are actively registered. The tusked values come from a separate state change, rather than a second entity type. That setter restores the base values on its other branch, including a repeated request to set an already-tusked Elephant to tusked; the higher values should not be treated as unconditional for every customized Elephant. [Attributes][attributes] · [Attribute registration][active-attributes] · [Tusked values][tusks] · [Registered size][registration] · [Tusked size][size]

## Obtaining

Use the [Elephant Spawn Egg](../items/ElephantSpawnEgg.md) in Creative, or `/summon minecraft:elephant` with command permission. Its egg is registered and listed in the Creative spawn-egg tab. [Egg][egg] · [Creative entry][creative]

**Natural spawning is not established in this snapshot.** No Elephant entry was found in the active biome spawn data or spawn-placement registrations. A spawn-roll setting in the animal's class does not establish a savanna encounter. A trader-themed Elephant and special chest are also represented in source, but a working trader encounter and its special chest-loot data were not established. [Biome data][biomes] · [Spawn placements][placements] · [Trader loot reference][trader] · [Bundled data][data]

## Taming, feeding, and breeding limits

The current class checks three item tags: `minecraft:elephant_foodstuffs`, `minecraft:elephant_tameables`, and `minecraft:elephant_breedables`. None has a bundled tag file in the checked data. **No specific taming, healing, or breeding food can therefore be recommended for an unmodified MattMC world.** Familiar foods from another version of Alex's Mobs do not establish a working route here. [Tag definitions][tags] · [Bundled item tags][item-tags]

If a custom data pack supplies those tags, the source-defined sequence is:

1. An Elephant accepts a foodstuff into its trunk, either through an interaction while its hand is empty or by picking up a dropped item
2. An item intended for taming must also belong to the tameables tag; the eating check allows an **untusked Elephant or a baby**, but excludes a wild tusked adult
3. Finishing an eligible item gives a **one-in-three taming chance**, using the feeding player or dropped item's thrower for ownership; eating a foodstuff also heals 10 health points

Breeding food is accepted only after taming. The offspring method creates another Elephant, with its tusked state selected using nearby tusked Elephants and a random check. These rules describe the conditional implementation; they do not replace the missing food definitions. [Feeding interaction][controls] · [Dropped food][pickup] · [Eating and taming][eating] · [Breeding condition][breeding] · [Offspring][offspring]

## Riding, decoration, and storage

These controls apply to an Elephant that already has an owner, such as one supplied by a map or customized world:

- **Ride:** the owner can interact with an adult using an empty hand; no saddle is required by the interaction. The riding code uses the player's movement and facing
- **Decorate:** the owner can apply a wool carpet. Applying a different color returns the old carpet
- **Add storage:** the owner can apply a Chest for a **54-slot inventory**, equivalent to six chest rows
- **Open storage:** dismount, then sneak-interact with a chested Elephant. **This opening interaction is not owner-locked**, so other players can access its contents
- **Remove equipment:** use Shears without sneaking. A worn carpet is removed first; a later use removes the chest and drops its contents

Chest contents have explicit save/load handling. No ordinary owner interaction for a wander/follow/sit command cycle is provided here. The source also contains a rider-charge helper, but no active caller was found, so no food or button is documented as a working rider charge control. [Interactions][controls] · [Inventory][inventory] · [Storage menu][menu] · [Saving and loading][saving] · [Riding][riding] · [Charge helper][charge]

## Behavior and drops

Tusked adults retaliate when hurt. Babies and untusked Elephants can panic and alert others; a tame Elephant also has owner-defense goals. Untusked Elephants can join a caravan behind a tusked adult, and the goal list includes avoiding nearby bees. Do not assume its size or browsing category makes it harmless. [Goals][goals] · [Retaliation and panic][retaliation] · [Caravans][caravans]

Leaf foraging depends on another missing tag, `minecraft:elephant_foodblocks`. The presence of leaf-breaking code is not evidence that Elephants currently harvest ordinary trees. No dedicated Elephant death-loot table was found. Equipment-drop handling returns an attached chest and its contents, plus a worn carpet on a non-trader Elephant; no tusk or meat reward is established. [Foraging][foraging] · [Bundled block tags][block-tags] · [Equipment drops][drops] · [Entity loot data][loot]

## Related pages

- [Elephant Spawn Egg](../items/ElephantSpawnEgg.md)
- [Kangaroo](Kangaroo.md)
- [Grizzly Bear](GrizzlyBear.md)
- [Mobs](Mobs.md)

## Verification scope

Source-reviewed against MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Entity and attribute registration, food tags, interaction paths, spawn wiring, and loot data were checked separately. No in-game spawning, taming, riding, storage, breeding, or combat test was run.

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L141-L143
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L156
[tusks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L820-L830
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L514-L519
[size]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L85
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1855
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2010
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[placements]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[trader]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L110
[data]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data
[tags]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L121-L124
[item-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item
[controls]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L517-L581
[pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L833-L853
[eating]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L317-L340
[breeding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L224-L227
[offspring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L642-L649
[inventory]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L173-L190
[menu]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L991-L1005
[saving]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L652-L727
[riding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L932-L960
[charge]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L1015-L1023
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L204-L222
[retaliation]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L1046-L1075
[caravans]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/ElephantAIFollowCaravan.java#L22-L99
[foraging]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/ElephantAIForageLeaves.java#L107-L129
[block-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block
[drops]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityElephant.java#L621-L640
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities
