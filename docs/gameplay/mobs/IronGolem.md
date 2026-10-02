# Iron Golem

An **Iron Golem** (`minecraft:iron_golem`) is a large melee defender with **100 health points, or 50 hearts**. You can build one from Iron Blocks and a carved head, or encounter golems created through village and structure systems. Its relationship with players depends on how it was created and its current targets.

## Build an Iron Golem

Prepare **four [Blocks of Iron](../items/BlockOfIron.md)** and one [Carved Pumpkin](../items/CarvedPumpkin.md) or Jack o'Lantern. Use full Iron Blocks, not Raw Iron Blocks or ingots. Four blocks require 36 Iron Ingots if crafted from scratch.

Build this upright pattern, viewed from the front:

| Left | Center | Right |
| --- | --- | --- |
| Air | Carved Pumpkin or Jack o'Lantern | Air |
| Iron Block | Iron Block | Iron Block |
| Air | Iron Block | Air |

Place the four Iron Blocks first, then place the head last. The marked corner spaces must actually be **air**; plants or Snow layers there do not satisfy the pattern. The head's facing is not part of the match. Leave space around the finished body.

A successful match consumes the body and head and creates a golem with its **player-created flag enabled**. It does not preserve those building materials as a recoverable inventory. This is a source-derived creation layout, not an in-game-tested build.

For carving and obtaining the head, follow [Pumpkin farming and carving](../blocks/PumpkinAndMelon.md#carving-a-pumpkin). The ordinary uncarved Pumpkin is not a matching head until it is carved.

## Other verified creation routes

**Villagers can summon golems** through their active gossip and panic behavior. A qualifying villager must have slept within the last **24,000 game ticks** and lack recent-golem memory. The nearby-villager search expands ten blocks around the initiating villager; the gossip path needs five qualifying villagers and the panic path needs three.

A qualifying group only permits an attempt. The summoning routine still needs a suitable nearby floor and space for the golem. Detecting a golem, or successfully summoning one, records recent-golem memory; the sensor can refresh that memory while a golem remains nearby. Beds, villagers, or a hostile mob alone do not prove a particular iron-farm design will run.

**Some Pillager Outpost cages contain a golem.** The bundled cage1 structure stores an Iron Golem, the Outpost feature pool can select it, and template placement includes entities. Other cage/feature selections exist, so an Outpost does not guarantee one. The saved cage golem is not marked player-created.

[Iron Golem Spawn Eggs](../items/IronGolemSpawnEgg.md) provide a Creative route. The pumpkin-pattern creation flag should not be assumed for every golem obtained by another method. These are verified routes, not a claim of ordinary random spawning in every village biome.

## Defense and player relations

Its active goals include melee attacks, moving toward targets, returning toward village areas, village wandering, and flower offering. It targets eligible hostile `Enemy` mobs but explicitly excludes **Creepers** from its ordinary attack-type rules. Do not depend on it to guard against Creepers.

For normal AI targeting, a pattern-built, player-created golem excludes **players** as targets. Other golems can retaliate and can defend villagers against players with sufficiently bad reputation; the checked village-defense threshold is **−100 or lower**. This uses villager reputation and combat-target conditions, not a universal declaration that every nearby golem is friendly.

Building or repairing a golem does not give it an owner-follow command. It can roam according to its goals. Its melee hit can throw targets upward, so give a village defender room around paths and platforms.

## Repair and care

Use an **Iron Ingot** on a damaged golem to restore up to **25 health points, or 12.5 hearts**, capped at its maximum. A successful ordinary Survival repair consumes one ingot; a full-health golem does not consume it through this handler. Cracks reflect its health state.

Repairing changes health, not ownership or anger state. It is not an automatic way to clear an existing hostile relationship. The golem's underwater air-supply handler does not decrease its air, but that does not make it immune to every other damage source.

The shared golem class disables ordinary distance-based despawning. Death and other removal paths still apply.

## Drops

With normal mob loot enabled, the bundled death table gives **3–5 Iron Ingots** and **0–2 Poppies**. Neither pool adds a Looting bonus or requires a player-attributed kill. The death return is much smaller than the four Iron Blocks used to build one.

## Related pages

- [Snow Golem](SnowGolem.md)
- [Block of Iron](../items/BlockOfIron.md), [Iron Ingot](../items/IronIngot.md), and [Carved Pumpkin](../items/CarvedPumpkin.md)
- [Villagers](Villager.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Entity/attribute registration, pumpkin-pattern dispatch, AI target checks, healing, villager brain callers, Outpost template contents and entity placement, and loot were checked. No in-game construction, village summoning, combat, repair, structure search, or farm test was run. Data packs, custom entity data, and later code changes can alter outcomes.

- [Entity registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java)
- [Active attributes](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java)
- [Patterns, material consumption, and player-created flag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java)
- [Carved Pumpkin and Jack o'Lantern block registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Placement callback dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java)
- [Combat, health, repair, anger, and air handling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/IronGolem.java)
- [Combat targeting respects entity-type exclusions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java)
- [Village-defense reputation check](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/DefendVillageTargetGoal.java)
- [Gossip, recent sleep, and summoning attempt](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/Villager.java)
- [Gossip caller](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/TradeWithVillager.java)
- [Panic summoning caller](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerPanicTrigger.java)
- [Active villager behavior packages](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java)
- [Recent-golem sensing and memory](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/sensing/GolemSensor.java)
- [Summoning position and obstruction checks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/util/SpawnUtil.java)
- [Outpost structure start pool](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json)
- [Outpost pool wiring](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/PillagerOutpostPools.java)
- [Outpost cage feature selection](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/features.json)
- [Bundled cage with golem entity](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/pillager_outpost/feature_cage1.nbt)
- [Pool placement includes entities](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java)
- [Template entity loading](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java)
- [Distance despawning rule](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/AbstractGolem.java)
- [Death loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/iron_golem.json)
- [Mob-loot gate](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Iron-block recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/iron_block.json)
