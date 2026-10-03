# Zombie Villager

A **Zombie Villager** is a hostile Zombie-family mob that you can cure into a [Villager](Villager.md). Apply **Weakness**, then interact with it using an **ordinary Golden Apple**. Keep it protected and contained while the cure completes; it remains dangerous during the wait. [Curing interaction and tick][zv] · [Active registration][entities] [attributes]

<span id="obtaining"></span>

## Finding one

Zombie Villagers have their **own natural spawn-table entries** across many Overworld biomes. For example, Plains and Forest list weight 5 with individual spawns, Desert lists weight 1, and Old Growth Pine Taiga lists weight 25. These are relative monster-list weights, not a fixed percentage of every Zombie spawn. Natural spawning uses the normal hostile ground, darkness, population, and non-Peaceful checks. [Example tables][plains] [forest][] [desert][] [old_growth_pine_taiga] · [Registered predicate][placements] · [Monster checks][monster] · [Natural dispatch][natural]

Two checked structure routes are:

- **Igloo basements:** the generator chooses a basement half the time. Its template contains a persistent Zombie Villager, a Villager, and a Brewing Stand holding a Splash Potion of Weakness. The assigned basement chest loot includes one guaranteed Golden Apple. Igloo eligibility covers Snowy Plains, Snowy Taiga, and Snowy Slopes; terrain and generation still matter
- **Abandoned village pieces:** the checked Plains village pools connect zombie houses to persistent Zombie Villager templates. This is a structure route, not an ordinary village-wide infection event

[Igloo selection and chest assignment][igloo] · [Igloo structure wiring][igloo-structure] [igloo-definition][] [igloo-biomes][] · [Basement entities and potion][igloo-template] · [Chest loot][igloo-loot] · [Plains village wiring][village-definition] [village-start][] [village-center][] [village-streets][] [village-street][] [village-houses] · [House connector][village-house] · [Resident pool and entity][village-people] [village-person] · [Active template entity placement][single-pool] [template][] [settings][]

A Zombie-family attacker can also infect a Villager it kills: the conversion roll succeeds **50% on Normal**, **100% on Hard**, and is not used on Easy. The conversion carries Villager data, saved offers, gossip, and Villager experience into the Zombie Villager. An unsuccessful conversion means that Villager is lost. [Kill callback and conversion data][zombie]

The ordinary listed [Zombie Villager Spawn Egg](../items/ZombieVillagerSpawnEgg.md) supplies a separate spawning route through MattMC's [inventory browser](../mechanics/InventoryBrowser.md), in Creative. [Egg category](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2124) [Egg registration][items]

<span id="behavior"></span>

## Curing step by step

1. Contain the Zombie Villager under a roof, separated from other hostile mobs and vulnerable Villagers
2. Apply Weakness, for example with a **Splash Potion of Weakness**; see [Brewing](../brewing/Brewing.md) for its preparation and splash conversion
3. While Weakness is active, interact with the Zombie Villager using an **ordinary [Golden Apple](../items/GoldenApple.md)**
4. After the curing sound, keep it safe and keep its area ticking until it becomes a Villager

This is a source-based procedure, **not an in-game curing trial**. Eating the Apple yourself or dropping it nearby does not perform the interaction. **Enchanted Golden Apple is a different item and does not satisfy this cure check.** One Apple is consumed in ordinary Survival. Without Weakness, the Apple interaction does not start curing or consume the Apple through this branch. [Exact interaction][zv] · [Splash effect application][splash] · [Brewing route][brewing]

Starting the cure removes Weakness and applies **Strength I** on non-Peaceful difficulties. The mob remains hostile while converting, so preserve your barrier. It starts with an inclusive **3,600–6,000-game-tick** countdown, normally **3–5 minutes** at 20 ticks per second before acceleration. Keep the chunk ticking; this is not an offline wall-clock timer. [Start and active curing tick][zv]

Nearby **Iron Bars and Bed blocks** can slightly accelerate the countdown. On a 1% per-tick check, the routine considers up to 14 matching blocks in its nearby eight-block-wide search box; each has a 30% chance to add one extra tick of progress. These blocks are **optional**, not cure ingredients, and this random boost does not guarantee a fixed faster completion time. [Conversion progress calculation][zv]

A Zombie Villager disables ordinary water conversion, so submerging it does not cure it or turn it into Drowned. Curing state and remaining time are saved. The curing tick itself does not have the ordinary Zombie water timer's NoAI guard. [Water override, saved timer, and curing tick][zv] · [Zombie water timer][zombie]

## What survives the cure

The result receives the saved **Villager data, offers when present, gossip, and Villager experience**, then refreshes its Villager behavior. A naturally spawned Zombie Villager does not carry a player's prior trading history merely because it has a profession appearance. An inexperienced cured Villager may later change profession through normal job-site rules; previously established trades and experience have their own preservation path. [Infection data copy][zombie] · [Cure restoration][zv] · [Villager initialization][villager] · [Inexperienced profession reset][reset-profession]

Baby state survives conversion, so curing a baby produces a baby Villager. Custom name and relevant persistence state also transfer. The newly cured Villager receives **Nausea for 200 game ticks**, normally **10 seconds**. [Common conversion state][conversion] · [Cure completion][zv]

Equipment is handled separately. Picked-up equipment marked for preservation is normally dropped during curing. Ordinary naturally generated gear is not all handed back, and equipment with the armor-removal-prevention effect, such as Curse of Binding, takes a special transfer path into the Villager's internal inventory. Do not treat curing as a guaranteed recovery method for every equipped item. [Cure equipment callback][zv] · [Preserved-equipment helper][mob] · [Binding effect][binding] · [Villager inventory slots][villager-inventory]

If the player who began the cure is found in that server level when it finishes, the completion path grants the cure advancement and a positive reputation event with the cured Villager. Reputation can lower that player's offer prices, but it does not make every offer cost one Emerald. The major-positive cure gossip is capped at 20 and minor-positive gossip at 25, so repeating cures does not stack those values without limit. [Completion credit][zv] · [Reputation and price handling][villager] · [Gossip caps][gossip-types] [gossip]

## Survival, drops, and persistence

Before and during curing, keep the mob out of exposed sunlight. It inherits Zombie sunlight and combat behavior, including babies and conditional door-breaking. A roof and a barrier remain useful even after Weakness has been applied. [Shared Zombie behavior][zombie]

A converting Zombie Villager, or one with stored Villager experience above zero, does not use ordinary distance despawning. Other unprotected Zombie Villagers can despawn; ordinary naming/persistence rules still apply. **Peaceful can remove it even while it is being cured**, because the hostile Peaceful-removal check comes first. [Distance exception][zv] · [Despawn order][mob] · [Entity exclusion][entities]

If killed instead of cured, its own table gives **0–2 Rotten Flesh**, up to five with Looting III, and the same **2.5% player-credit-gated combined Iron Ingot/Carrot/Potato roll**, rising to 5.5% with Looting III, as the ordinary Zombie resource pools. Its table does not contain the ordinary Zombie's baby-jockey music-disc pool. Equipment and experience follow the shared [Zombie rules](Zombie.md#drops). [Zombie Villager loot][zv-loot] · [Monster loot gate][monster] · [Equipment handling][mob]

## Related pages

- [Golden Apple](../items/GoldenApple.md), [Brewing](../brewing/Brewing.md), [Villager](Villager.md), [Trading](../trading/Trading.md)
- [Zombie](Zombie.md), [Husk](Husk.md), [Drowned](Drowned.md), [Mobs](Mobs.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game combat, conversion, curing, loot, or crafting test was run. Data packs, entity state, difficulty, gamerules, and later builds can change these results.

[zv]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java
[entities]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/EntityType.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[plains]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/forest.json
[desert]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/desert.json
[old_growth_pine_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/old_growth_pine_taiga.json
[placements]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[monster]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Monster.java
[natural]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[igloo]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooPieces.java
[igloo-structure]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooStructure.java
[igloo-definition]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/structure/igloo.json
[igloo-biomes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/igloo.json
[igloo-template]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/structure/igloo/bottom.nbt
[igloo-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/chests/igloo_chest.json
[village-definition]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/structure/village_plains.json
[village-start]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/town_centers.json
[village-center]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/structure/village/plains/zombie/town_centers/plains_fountain_01.nbt
[village-streets]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/zombie/streets.json
[village-street]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/structure/village/plains/zombie/streets/corner_01.nbt
[village-houses]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/zombie/houses.json
[village-house]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/structure/village/plains/zombie/houses/plains_small_house_1.nbt
[village-people]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/zombie/villagers.json
[village-person]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/structure/village/plains/zombie/villagers/unemployed.nbt
[single-pool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java
[template]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java
[settings]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings.java
[zombie]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Zombie.java
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[splash]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java
[brewing]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[villager]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/Villager.java
[reset-profession]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/behavior/ResetProfession.java
[conversion]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ConversionType.java
[mob]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Mob.java
[binding]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/binding_curse.json
[villager-inventory]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java
[gossip-types]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/gossip/GossipType.java
[gossip]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/gossip/GossipContainer.java
[zv-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/zombie_villager.json
