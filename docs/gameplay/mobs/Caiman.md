# Caiman

The **Caiman** is a small, semi-aquatic predator from bundled Alex's Mobs content. Adults hunt several small animals and can defend themselves or an owner. Ownership comes from being near an egg when it hatches, rather than feeding a wild adult. The egg route exists in source, but missing breeding-food data limits obtaining new eggs in an unmodified Survival world. [Targets][targets] · [Hatching and ownership][hatching] · [Food tags][tags]

## At a glance

- **Health:** 20 points (10 hearts)
- **Base attack damage:** 3 points (1.5 hearts), before combat modifiers
- **Armor:** 8 points
- **Adult size:** 1.2 blocks wide × 0.5 blocks tall
- **Base movement-speed attribute:** 0.2, not a blocks-per-second measurement
- **Entity ID:** `minecraft:caiman`

These attributes are wired into the active entity-attribute registry. [Attributes][attributes] · [Active registration][active-attributes] · [Entity size][registration]

## Obtaining

Creative provides the [Caiman Spawn Egg](../items/CaimanSpawnEgg.md) and the separate, placeable [Caiman Egg](../items/CaimanEgg.md). With command permission, `/summon minecraft:caiman` creates the mob directly. A spawn egg does not run the placed egg's nearby-player ownership procedure. [Items][items] · [Creative eggs][creative-eggs] · [Creative spawn egg][creative-spawn]

**Natural spawning is not established in this snapshot.** The class has a ground check for Mud, Muddy Mangrove Roots, or its custom spawn-ground tag, but no active spawn-placement registration or biome spawn entry was found. The custom ground tag also lacks bundled data. These checks do not establish a mangrove-swamp encounter. [Spawn checks][spawn] · [Spawn placements][placements] · [Biome data][biomes] · [Block tags][block-tags]

## Hatching an owned Caiman

If you already have a placed Caiman Egg, its hatching and ownership rules are separate from the missing breeding-food route:

1. Place it on **Sand, Red Sand, or Suspicious Sand**, the three entries in the bundled sand tag. The alternative crocodile-ground tag has no bundled definition
2. Keep the egg loaded and protected. Its registered block receives random ticks, advances through two cracking stages, and then creates one baby for each egg in the cluster. Growth also checks time of day and chance, so there is no fixed countdown to quote
3. At hatching, the **nearest non-spectator player within 20 blocks** becomes the owner. Stay closer than other players if the babies should belong to you. If no eligible player is nearby, the hatchlings are not assigned an owner by this code

The hatchlings start as babies and are set to sit when assigned an owner. This is a source-defined route, not an in-game hatch test. [Egg registration][egg-block] · [Habitat][habitat] · [Sand tag][sand] · [Hatching][hatching] · [Growth timing][growth]

Avoid walking or jumping on the eggs. Player trampling can remove an egg and provoke nearby Caimans; ones already owned by that player are excluded from this retaliation assignment. Clusters can hold up to four eggs. No dedicated Caiman Egg block-loot table was found, so do not assume breaking an egg, even with Silk Touch, safely retrieves it. [Trampling][trampling] · [Clustering][clustering] · [Block loot data][block-loot]

## Owner commands and care

Use an **empty hand** on your Caiman to cycle **wander → follow → sit → wander**. Command zero is wander; the next interaction selects follow. Food or another held item's interaction can take priority, which is why an empty hand is the clearest choice. Following supports both land and water and pauses during combat. [Interactions][interactions] · [Initial command][defaults] · [Follow condition][follow] · [Water-follow goal][follow-goal]

Provide both a water area and accessible land, with room to surface. Its active goals include breathing air, finding water, leaving water, and occasional bellowing at the surface. It is not a bucket-capturable animal in this implementation, and no player-riding or storage interaction is provided. [Goals][goals] · [Land/water behavior][water] · [Interactions][interactions]

The healing interaction requires the `minecraft:caiman_foodstuffs` tag and heals a tamed Caiman by **5 health points** per accepted item. That tag has **no bundled food definition**, so no normal healing item is verified here. Do not substitute a fish recommendation from another version of the mod. [Healing][interactions] · [Tag definitions][tags] · [Item tags][item-tags]

## Breeding and resources

Breeding likewise depends on the missing `minecraft:caiman_breedables` tag. No specific breeding food is established. If a custom data pack supplies it, the source's mating goal gives one parent an egg to lay; the laying goal places a **three-egg cluster** on suitable ground with empty space above. This is an egg-laying route, rather than the ordinary immediate-baby result. [Food check][food] · [Mating and laying][breeding]

No dedicated Caiman death-loot table or custom resource-drop method was found. Do not plan a meat, hide, or egg farm around unverified drops. [Entity loot data][loot] · [Caiman implementation][entity]

## Keeping other animals safe

Untamed adults target the bundled list of small prey, including Chickens, Rabbits, Frogs, Parrots, Cod, Salmon, Tropical Fish, Terrapins, and several other small integrated animals. Keep them out of fish ponds and mixed pet enclosures. The prey-selection goal is disabled for babies and tamed Caimans, but retaliation and owner-defense goals remain separate. Their special melee behavior pulls a target toward the jaws while dealing damage. [Prey tag][targets] · [Targeting conditions][goals] · [Melee behavior][melee]

## Related pages

- [Placed Caiman Eggs: acquisition, hatching and protection](../blocks/AnimalEggs.md#caiman-eggs)

- [Caiman Egg](../items/CaimanEgg.md)
- [Caiman Spawn Egg](../items/CaimanSpawnEgg.md)
- [Platypus](Platypus.md)
- [Crocodile](Crocodile.md)
- [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Entity and egg registration, active attributes, tag contents, ownership, breeding, spawn wiring, and loot were checked separately, searching both constant names and lowercase IDs. No in-game spawning, hatching, ownership, breeding, command, or combat test was run.

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L284-L291
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L124
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1161-L1163
[items]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L873-L874
[creative-eggs]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L950-L951
[creative-spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1980
[spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L139-L147
[placements]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[block-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block
[egg-block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4776-L4785
[habitat]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/block/BlockReptileEgg.java#L54-L60
[sand]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/sand.json
[hatching]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L81-L118
[growth]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L126-L133
[trampling]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L39-L79
[clustering]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L140-L148
[block-loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks
[interactions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L255-L282
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L79-L87
[follow]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L430-L433
[follow-goal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/TameableAIFollowOwnerWater.java
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L89-L137
[water]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L367-L388
[tags]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L96-L99
[item-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item
[food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L149-L151
[breeding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L435-L513
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities
[entity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCaiman.java
[targets]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/caiman_targets.json
[melee]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CaimanAIMelee.java#L32-L69
