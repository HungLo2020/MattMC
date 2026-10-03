# Tarantula Hawk

The **Tarantula Hawk** flies, hunts spiders and retaliates when hurt. Its sting changes server-side horizontal motion, with a player-movement limitation explained below. Use a spawn egg for a reliable encounter in the checked MattMC snapshot: its natural spawning, feeding ingredients and distinctive wing rewards are not fully connected in the bundled data. [Behavior][hawk]

## Obtaining

The [Tarantula Hawk Spawn Egg](../items/TarantulaHawkSpawnEgg.md) creates this mob. It is category-listed, so the [inventory item browser](../mechanics/InventoryBrowser.md) can insert the egg in **Survival as well as Creative**. Use it on a block with room for the mob; ordinary Survival use consumes the egg. [Item registration][hawk-items] · [Category entries][categories] · [Egg use][egg-use]

The checked biome JSON and structure spawn lists contain no Tarantula Hawk entry. Natural spawning draws from those loaded lists, and the hawk's separate spawn predicate is absent from the checked spawn-placement registrations. A desert, badlands or Nether location is therefore **not a verified natural source** here. The Nether-variant biome tag is declared but has no bundled membership. [World data loading][world-load] · [Resource loader][biome-load] · [Biome data][biome-data] · [Structure data][structure-data] · [Spawn selection][spawn-pick] · [Spawn lists][spawn-list] · [Placement table][spawn-placement] · [Variant code][hawk] · [Biome tags][biome-tags]

## Behavior

An adult that is not sitting targets **Spiders**, including the Cave Spider subclass. It also has retaliation and owner-defense goals, rather than a goal that hunts every nearby player. It flees [Roadrunners](Roadrunner.md). Its registered attributes give **18 health points (9 hearts)**, **4 armor points**, and **5 base attack damage** before combat modifiers. [Goals and attributes][hawk] · [Cave Spider inheritance][cave-spider] · [Attribute wiring][attributes]

### Sting and prey

Effect ID: `minecraft:debilitating_sting`.

A close attack applies **Debilitating Sting** for **30 seconds** to ordinary targets such as players, or **120 seconds** to entities in the bundled arthropod tag, at 20 ticks per second. That tag contains Bees, Endermites, Silverfish, Spiders and Cave Spiders. It does **not** include Tarantula Hawks or Cave Centipedes merely because they look like arthropods. [Attack handler][hawk] · [Exact membership][arthropods]

On each active **server effect tick**, the registered sting multiplies horizontal velocity by **0.1** and leaves vertical velocity unchanged. This damps existing horizontal motion; it is not a guaranteed 90% reduction in distance travelled, because movement input and other physics still apply. It does not itself disable attacks, add poison damage, or spawn offspring when it expires. A hawk is immune to damage sourced from a stung, tagged arthropod, a separate rule from the sting's motion change. [Effect behavior][custom-sting-tick] · [Tick dispatch][custom-sting-dispatch] · [Later movement][custom-sting-movement] · [Hawk immunity][hawk]

**Player-movement limit:** the sting's velocity change runs on the server, while the player client only advances the effect timer. The effect does not request the self-directed motion update needed to establish a sustained client-controlled slowdown. Damage and movement corrections can separately update a player, but the checked source does not establish a reliable player movement penalty from the sting alone. Its application, timer, and the hawk's arthropod checks are still active. [Client/server effect ticks][custom-sting-dispatch] · [Server player tick and position restoration][custom-sting-server-player] · [Velocity assignment][custom-sting-velocity] · [Tracker and self delivery][custom-sting-motion] · [Tracking excludes self][custom-sting-tracking] · [Incoming player movement][custom-sting-player-move]

A wild hawk can carry stung arthropod prey toward nearby sand and bury it. Keep pet spiders away from it. That prey-handling behavior is not evidence of a completed baby-hawk production route. [Combat and burial goals][hawk]

Ordinary stings apply **level I**. A hawk with the conditional breeding/burial flag can apply **level II**, but the effect's motion multiplier does not scale with level; the higher level is checked separately by the hawk's prey-handling goal. Repeated stings can refresh the same-level timer rather than add their durations. [Sting and level-dependent prey handling][custom-sting-hit] · [Motion rule][custom-sting-tick] · [Refresh comparison][custom-sting-refresh]

[Milk](../items/MilkBucket.md) clears the sting and any beneficial effects you also carry. A [Honey Bottle](../items/HoneyBottle.md) only removes Poison; Debilitating Sting is a separate registered effect. Avoid another sting after clearing it. [Drink definitions][custom-sting-drinks] · [Milk definition][custom-sting-milk] · [Distinct registration][custom-sting-registry]

### Taming, commands and breeding

**No default feeding item is verified.** Taming, healing and breeding each check a dedicated item tag, but none of those three tags has a bundled data file in this snapshot. Do not spend Spider Eyes or flowers expecting an upstream recipe to work. [Required tags][hawk-tags] · [Bundled item tags][item-tags] · [Interaction checks][hawk]

If a server data pack supplies the relevant items, the implemented interactions are:

- Feed a wild hawk a taming-tag item: taming can start succeeding from the **15th feeding**, with a **1-in-6** roll, and is guaranteed on the **26th**
- Feed a wounded tame hawk a healing-tag item to restore **5 health points**
- As its owner, interact with an empty hand without sneaking to cycle **wander → follow → sit**; repeat to cycle again. Sneak-interaction gives it one held item or drops the item it already holds
- Breeding-tag food works only on tame hawks, but the breeding callback marks a burial state and returns no ordinary baby. The checked sting effect contains no hatch step, so successful feeding/breeding does not establish a working offspring farm

These are conditional code paths, not a claim that the missing default foods are available. [Interaction and breeding code][hawk]

### Drops and materials

There is no bundled Tarantula Hawk death-loot table or wing-producing growth callback in the checked implementation. [Wing](../items/TarantulaHawkWing.md) and [Wing Fragment](../items/TarantulaHawkWingFragment.md) inventory entries therefore do not establish drops from killing, breeding or raising a hawk. Their pages explain the verified browser route and current use limits. [Entity implementation][hawk] · [Bundled entity loot][loot-data]

## Notes

- Registered as `minecraft:tarantula_hawk`, with egg `minecraft:tarantula_hawk_spawn_egg`
- It is a creature-category mob with registered dimensions **1.2 × 0.9 blocks**
- Its absence from the default arthropod tag also excludes the default **Bane of Arthropods** bonus
- The named [Tarantula Hawk Elytra](../items/TarantulaHawkElytra.md) is not configured as a working glider in this snapshot

[Entity registration][hawk-registry] · [Item registration][hawk-items] · [Arthropod tag][arthropods] · [Bane target tag][bane-tag] · [Enchantment condition][bane]

Related: [Spider](Spider.md) · [Cave Spider](CaveSpider.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[hawk]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/entity/EntityTarantulaHawk.java
[hawk-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1971-L1981
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[egg-use]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L48-L99
[world-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/WorldLoader.java#L34-L44
[biome-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[biome-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/biome
[structure-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure
[spawn-pick]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[spawn-list]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biome-tags]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/worldgen/biome
[cave-spider]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/CaveSpider.java#L20-L24
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[arthropods]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/entity_type/arthropod.json
[sting-registry]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/effect/MobEffects.java#L133-L135
[sting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/effect/EffectDebilitatingSting.java
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L235
[hawk-tags]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L211-L216
[item-tags]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item
[loot-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities
[hawk-registry]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L1356-L1363
[bane-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_bane_of_arthropods.json
[bane]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json

Debilitating Sting details and the player-motion qualification additionally source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. No in-game movement, network, or sting test was run.

[custom-sting-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTarantulaHawk.java#L709-L782
[custom-sting-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/effect/EffectDebilitatingSting.java#L7-L23
[custom-sting-refresh]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L147
[custom-sting-drinks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[custom-sting-milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[custom-sting-registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L133-L148
[custom-sting-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L238
[custom-sting-movement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2773-L2830
[custom-sting-server-player]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L307-L314
[custom-sting-velocity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L3640-L3653
[custom-sting-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerEntity.java#L166-L216
[custom-sting-tracking]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ChunkMap.java#L1321-L1372
[custom-sting-player-move]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1023-L1115
