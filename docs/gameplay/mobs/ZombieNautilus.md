# Zombie Nautilus

A **Zombie Nautilus** is a hostile aquatic mob that actively targets players. In this MattMC revision it **cannot be tamed, ridden or bred through ordinary player interactions**. Removing a rider does not turn it into the tameable [Nautilus](Nautilus.md). Its ID is `minecraft:zombie_nautilus`. [Registration][zombie-type] · [Goals and interaction restrictions][zombie]

## Where to find one

**The bundled biome spawn tables contain no Zombie Nautilus entry.** Its entity, attributes and water/darkness predicate are registered, and Java ocean-generation builders contain entries, but the running world's biome data is loaded from packaged JSON. Those generator entries do not supply natural spawns in the bundled tables. A data pack can add them. [Attribute registration][attributes-zombie] · [Placement registration][placements] · [Generator entries][builders] · [World loading][world-loader] · [Ocean data][biome-json]

The [Zombie Nautilus Spawn Egg](../items/ZombieNautilusSpawnEgg.md) is an ordinary listed [inventory-browser](../mechanics/InventoryBrowser.md) item that Creative players can request. Place it in water in a non-Peaceful world. Egg placement fails in Peaceful, and existing Zombie Nautiluses are removed there. The egg route does not establish natural spawning. [Egg listing][egg-list-zombie] · [Egg checks][egg-use] · [Peaceful registration][zombie-type] · [Despawn processing][despawn]

The active spawn chooser reads the loaded biome spawn list (or a structure override); the resource registries are created fresh from JSON. Neither the checked biome files nor structure spawn overrides add these mobs. [Registry kinds][loaded-registries] · [Fresh registry creation][fresh-registries] · [JSON loading][json-loading] · [Natural spawn lookup][natural-spawns] · [Biome/structure selection][biome-spawn-choice]

## Behavior and combat

Zombie Nautilus swims, seeks water when stranded, attacks players and retaliates when hurt. Its verified attributes are **15 health points, or 7½ hearts**, and **3 base melee damage points**, before applicable modifiers. Its movement-speed attribute is 1.1 compared with the ordinary Nautilus's 1.0; this is not a measured blocks-per-second speed. [Shared attributes][nautilus] · [Zombie attributes and goals][zombie]

It breathes underwater and rejects Poison. It inherits flopping on land but does **not** inherit the ordinary Nautilus class's drying-damage tick. Do not rely on stranding it to remove it. The undead tag includes it, but its implementation does not supply a sunlight-burning routine. [Shared water and land behavior][movement] · [Poison rejection][nautilus-other] · [Ordinary Nautilus-only drying][care] · [Zombie implementation][zombie] · [Undead tag][undead]

### Drowned riders

When this mob's normal spawn initialization runs for a reason **other than spawn-egg placement**, there is a **15% chance** to create a Drowned rider. That rider receives a Trident in its main hand with a guaranteed equipment-drop flag. This is a conditional initialization path, not evidence of naturally occurring jockeys in the current bundled world. Ordinary block/water use of a Zombie Nautilus Spawn Egg skips the rider roll. [Rider creation][jockey] · [Egg spawn reason][egg-use]

Treat the Drowned as a separate attacker and loot source. Killing only the mount is not the same as killing the rider; the mount's own loot table contains no Trident. A guaranteed equipment flag is still subject to ordinary death-loot rules such as mob loot being enabled and effects that prevent an equipment drop. [Rider equipment][jockey] · [Equipment drop processing][equipment-loot] · [Death gates][loot-age]

## Player interactions

- Pufferfish does not tame it, and fish feeding does not heal it through the ordinary Nautilus interaction
- It has no normal saddle-riding or breeding interaction; its offspring method returns no child
- The armor and saddle tags name Zombie Nautilus, but usable body/saddle slots require a tame adult. Its normal interaction cannot create that tame state, so the tag alone is not a player equipment route
- A Name Tag or Lead uses the general entity interaction path; the mob's rejected feeding interaction does not cancel every generic item interaction

Do not spend armor or fish trying to reproduce another edition's Zombie Nautilus taming behavior. [Explicit restrictions][zombie] · [Shared equipment gates][nautilus] · [Armor tag][armor-tag] · [Generic interactions][egg-interact] · [Name Tags][name-tag] · [Lead interaction][generic-lead]

## Temperate and coral appearances

The loaded variants are **temperate** and **warm/coral**. Normal initialization selects the warm/coral appearance in **Warm Ocean**, with temperate as the fallback elsewhere. These are appearance-selection rules for a spawned mob; they do not insert it into a biome's natural spawn list. [Temperate variant][variant-temperate] · [Warm variant][variant-warm] · [Warm Ocean tag][variant-biome] · [Selection during spawning][jockey]

## Drops

An eligible **adult player-credit kill** drops **0–3 Rotten Flesh**. Looting can add up to its level, giving a maximum of **6 with Looting III**. The mob's own table does **not** contain a Nautilus Shell; a separately spawned Drowned may have its own equipment. [Loot table][zombie-loot]

Because this implementation inherits the animal experience routine, eligible adult player-credit kills yield **1–3 experience**, with mob loot enabled. Normal baby death-table drops and experience are suppressed. This differs from assuming every monster-category entity inherits the standard Zombie reward. [Inherited animal reward][animal] · [Baby gates][loot-age] · [Experience processing][loot-xp]

Related: [Nautilus](Nautilus.md) · [Zombie Nautilus Spawn Egg](../items/ZombieNautilusSpawnEgg.md) · [Drowned](Drowned.md) · [Rotten Flesh](../items/RottenFlesh.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Checked active registration, attributes, goals, player-interaction overrides, loaded biome and variant data, rider initialization, environmental behavior and death loot. No combat, spawn, variant, daylight, rider or loot test was run in-game. Data packs and explicit entity data can change behavior or availability beyond these ordinary player routes.

[zombie-type]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/EntityType.java#L1566-L1574
[zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/ZombieNautilus.java#L54-L92
[attributes-zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L155-L155
[placements]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L89-L97
[builders]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java#L447-L463
[world-loader]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[biome-json]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[egg-list-zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2123-L2123
[egg-use]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[despawn]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L602-L629
[nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L71-L179
[movement]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L191-L250
[nautilus-other]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L265-L325
[care]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/Nautilus.java#L84-L106
[undead]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/entity_type/undead.json
[jockey]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/ZombieNautilus.java#L148-L176
[equipment-loot]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L813-L835
[loot-age]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/entity_type/can_wear_nautilus_armor.json
[egg-interact]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L1079-L1103
[name-tag]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L29
[variant-temperate]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/zombie_nautilus_variant/temperate.json
[variant-warm]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/zombie_nautilus_variant/warm.json
[variant-biome]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_coral_variant_zombie_nautilus.json
[zombie-loot]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/loot_table/entities/zombie_nautilus.json
[animal]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L174
[loot-xp]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
[loaded-registries]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[fresh-registries]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L401-L409
[json-loading]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[natural-spawns]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[biome-spawn-choice]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[generic-lead]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2174
