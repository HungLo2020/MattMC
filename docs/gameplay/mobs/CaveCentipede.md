# Cave Centipede

The **Cave Centipede** is a long, hostile mob whose head can poison you and drop [Centipede Legs](../items/CentipedeLeg.md). Keep your distance and aim for the head when collecting legs; the body and tail have separate empty loot tables. Its natural cave encounter is **not wired into the checked bundled spawn lists**. [Head behavior][centipede] · [Head loot][head-loot] · [Body loot][body-loot] · [Tail loot][tail-loot]

## Obtaining

Use the [Cave Centipede Spawn Egg](../items/CaveCentipedeSpawnEgg.md) to create one. The egg is in an ordinary category, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can insert it in **Creative**. Use the egg on a block with space for the mob, and expect ordinary Survival egg use to consume it. This is separate from finding naturally spawned centipedes. [Egg registration][centipede-egg] · [Category entries][categories] · [Egg use][egg-use]

The checked biome JSON and structure spawn lists do not include this mob. Natural spawning selects from those loaded lists; a method describing a dark location at or below Y=0 does not add the mob to them, and that centipede predicate is not registered in the checked spawn-placement table. There is no verified biome or depth to search for a natural encounter in this snapshot. Server data packs can change these lists. [Loaded data][world-load] · [Biome resource loading][biome-load] · [Biome data][biome-data] · [Structure data][structure-data] · [Spawn selection][spawn-pick] · [Spawn lists][spawn-list] · [Placement registrations][spawn-placement]

## Behavior

- It targets players, villagers and [Cockroaches](Cockroach.md), and retaliates when hurt. Its head has **35 health points (17.5 hearts)**, **6 armor points**, and a base attack-damage attribute of **8** before combat modifiers. [Goals and attributes][centipede] · [Attribute wiring][attributes]
- A successful head melee hit adds **Poison II** for **3 seconds on Easy, 10 on Normal, or 20 on Hard**, at 20 ticks per second. Blocking or avoiding the hit matters more than relying on its size to keep it away. [Attack callback][centipede] · [Melee caller][melee-caller]
- When it has no target, it can seek a darker spot from brightness **5 or above**. This is occasional movement behavior, not a guarantee that a torch repels an attacking centipede. [Goal priority][centipede] · [Light avoidance][flee-light]
- A newly finalized head creates **5–8 trailing segments**, including a tail. The segments follow the head, but the checked body callback does not forward attack damage to it. Do not assume hitting any segment is equivalent to hitting the head. [Segment creation][centipede] · [Body handling][body]

There is no taming or breeding interaction in this mob's checked implementation. Despite its appearance, neither its head nor its segments is in the bundled **arthropod** tag, so the default **Bane of Arthropods** bonus does not apply to them. [Implementation][centipede] · [Arthropod membership][arthropods] · [Bane target tag][bane-tag] · [Enchantment condition][bane]

### Drops

With mob loot enabled, the head's death-loot table supplies **1–3 Centipede Legs**, with **up to one extra leg per Looting level** (up to **6 total with Looting III**). The table has no player-kill requirement and no cooking-on-fire conversion. Body and tail tables are empty. Use **seven legs** for [Centipede Leggings](../items/CentipedeLeggings.md#obtaining). [Head table][head-loot] · [Body table][body-loot] · [Tail table][tail-loot] · [Death-loot caller][death-loot] · [Looting count calculation][looting] · [Loot loading][loot-load]

## Notes

- The head is registered as `minecraft:cave_centipede_head`; its egg is `minecraft:cave_centipede_spawn_egg`
- The head is a monster with registered dimensions **0.9 × 0.9 blocks**; the trailing entities make the complete mob much longer
- This is bundled Alex's Mobs content. Its current MattMC wiring, rather than an upstream spawn description, determines availability

[Entity registration][centipede-registry] · [Egg registration][centipede-egg]

Related: [Centipede Leg](../items/CentipedeLeg.md) · [Centipede Leggings](../items/CentipedeLeggings.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[centipede]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/entity/EntityCentipedeHead.java
[head-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/cave_centipede_head.json
[body-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/cave_centipede_body.json
[tail-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/cave_centipede_tail.json
[centipede-egg]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1806
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[egg-use]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L48-L99
[world-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/WorldLoader.java#L34-L44
[biome-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[biome-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/biome
[structure-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure
[spawn-pick]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[spawn-list]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[melee-caller]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L132
[flee-light]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/entity/ai/AnimalAIFleeLight.java
[body]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/entity/EntityCentipedeBody.java
[arthropods]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/entity_type/arthropod.json
[bane-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_bane_of_arthropods.json
[bane]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[looting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L65-L80
[loot-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L39-L72
[centipede-registry]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L1167-L1175
