# Rabbit

A **Rabbit** is a small, normally passive animal that provides [Raw Rabbit](../items/RawRabbit.md), [Rabbit Hide](../items/RabbitHide.md), and occasionally a [Rabbit's Foot](../items/RabbitsFoot.md). It has **3 health points**, or **1½ hearts**. Bring **Carrots, Golden Carrots, or Dandelions** to attract and breed it; feeding does not create an owner or a sitting command. [Rabbit implementation][rabbit] · [Active entity and attributes][entities] [attributes] · [Food tag][rabbit-food]

## Finding Rabbits

The bundled natural spawn tables include these locations. Listed groups are configured ranges, not guaranteed encounters or numbers of adults.

| Biomes | Configured group | Ordinary coat selection |
| --- | --- | --- |
| Desert | 2–3 | Gold |
| Snowy Plains, Ice Spikes, Snowy Taiga, Snowy Slopes, Grove | 2–3 | 80% White, 20% White Splotched |
| Flower Forest, Taiga, Old Growth Pine Taiga, Old Growth Spruce Taiga | 2–3 | 50% Brown, 40% Salt, 10% Black |
| Meadow, Cherry Grove | 2–6 | 50% Brown, 40% Salt, 10% Black |
| Dry Midlands | 2–3 | 50% Brown, 40% Salt, 10% Black |

[Desert][desert] · [Snowy Plains][snowy_plains] · [Ice Spikes][ice_spikes] · [Snowy Taiga][snowy_taiga] · [Snowy Slopes][snowy_slopes] · [Grove][grove] · [Flower Forest][flower_forest] · [Taiga][taiga] · [Old Growth Pine Taiga][old_growth_pine_taiga] · [Old Growth Spruce Taiga][old_growth_spruce_taiga] · [Meadow][meadow] · [Cherry Grove][cherry_grove] · [Dry Midlands][dry_midlands]

Natural placement needs **Grass Block, a Snow layer, Snow Block, or ordinary Sand below**, **raw brightness above 8**, and the normal ground-placement and obstruction checks. Red Sand is not in the bundled Rabbit ground tag. Mob-spawning settings and population limits still apply. [Registered placement][placements] · [Rabbit predicate][rabbit] · [Ground tag][rabbit-ground] · [Brightness helper][animal] · [Natural spawn dispatch][natural]

[Dry Midlands](../biomes/DryMidlands.md) belongs to the Normal preset's [Primordial Caves](../dimensions/PrimordialCaves.md), which has no skylight. Its Rabbit entry therefore does not guarantee animals on unlit Sand; it still needs sufficient light. It is also absent from the gold-coat tag, despite its desert-like terrain. [Active preset][normal] · [Dimension lighting][primordial] · [Gold tag][gold]

A spawn group shares a coat. With the Rabbit group data, the first animal is adult and subsequent members become babies. Ordinary spawning never selects the hostile **Killer Bunny** variant. The [Rabbit Spawn Egg](../items/RabbitSpawnEgg.md) is a separate Creative route. [Group and variant initialization][rabbit] · [Group-age handling][age] · [Egg registration][items]

## Moving, breeding, and coats

Hold a **Carrot, Golden Carrot, or Dandelion** to lure Rabbits, or use a [Lead](../items/Lead.md). Their food-following goal has higher priority than their normal player-avoidance goal, but they can still panic or be interrupted. They ordinarily avoid players, Wolves, and monsters. [Goals and food predicate][rabbit] · [Shared interaction handling][mob] · [Leash interaction][entity]

Feed two adults that are outside their breeding cooldown, then allow them to approach each other. Each accepted feed consumes one item in ordinary Survival. A baby starts with **24,000 game ticks** of growth, normally **20 minutes**, and each parent receives a **6,000-game-tick** cooldown, normally **5 minutes**. Feeding a baby removes approximately **10% of its remaining growth time**. These interactions do not heal an injured Rabbit. [Shared feeding and breeding][animal] · [Growth][age] · [Breeding approach][breed]

A bred baby has a **95% chance to take a randomly chosen parent's variant**. The remaining **5%** uses the biome's ordinary coat-selection rule at the initiating parent. Moving an existing Rabbit to another biome does not recolor it. [Offspring and saved variant][rabbit] · [White-coat tag][white] · [Gold-coat tag][gold]

Naming a Rabbit **Toast** gives it a special visible coat. This is a renderer name check, so it does not replace the underlying variant used for breeding. The Killer Bunny is a separate data-selected variant with hostile targeting; naming an ordinary Rabbit does not select it. [Toast renderer and active registration][rabbit-renderer] [renderers] · [Killer Bunny variant behavior][rabbit]

## Protecting crops and animals

With **mobGriefing** enabled, a hungry Rabbit can seek a **fully grown Carrot crop on Farmland**. Its normal bite reduces the crop by one growth stage and starts a feeding cooldown. It does not hand you harvested Carrots or target every crop type. Keep breeding Rabbits away from a Carrot field you want to preserve. [Garden-raiding goal and server update][rabbit] · [AI goal dispatch][mob]

[Foxes](Fox.md), untamed [Cats](Cat.md), and wild [Wolves](Wolf.md) have Rabbit-targeting behavior. Keep them outside the enclosure. Rabbits inherit the animal rule that prevents ordinary distance despawning; their coat, age, and crop-feeding cooldown are saved. This does not protect them from predators or environmental damage. [Fox prey goals][fox] · [Cat prey goal][cat] · [Wolf prey selector][wolf] · [Persistence][animal] · [Rabbit save data][rabbit] · [Age save data][age]

A simple starting plan is to lead two Rabbits into an enclosed space away from crops and predators, feed each once, and leave room for them to approach. Check the enclosure for escape routes before breeding more. This is a source-based husbandry example, **not a tested enclosure or production-rate design**.

## Drops and Looting

For an adult with **doMobLoot** enabled:

| Drop | Without Looting | Effect of Looting |
| --- | --- | --- |
| Rabbit Hide | 0–1 | Up to one additional Hide per level; 0–4 at Looting III |
| Rabbit meat | 1 Raw Rabbit | Up to one additional item per level; 1–4 at Looting III |
| Rabbit's Foot | One at a 10% chance, requiring player kill credit | 13%, 16%, or 19% with Looting I, II, or III; still at most one |

[Bundled Rabbit loot][rabbit-loot] · [Looting count calculation][looting] · [Foot chance calculation][foot-chance] · [Player-credit condition][player-credit]

The meat becomes **Cooked Rabbit** if the Rabbit is burning when loot is evaluated, or the direct attacker's main-hand enchantments meet the bundled smelting-loot condition, currently **Fire Aspect**. Hide and Foot are unaffected by that conversion. [Meat conversion][rabbit-loot] · [Smelting enchantment tag][smelts]

The Foot check uses player kill credit, including the normal credited tame-Wolf route; it is not a guaranteed drop from every death. Adults can also award **1–3 experience** under the normal player-credit and gamerule checks. Babies provide neither the ordinary Rabbit loot table nor this death experience. [Credit, loot, and experience gates][living] · [Animal experience][animal]

## Related pages

- [Raw Rabbit and cooking](../items/RawRabbit.md#cooking), [Cooked Rabbit](../items/CookedRabbit.md)
- [Rabbit Hide](../items/RabbitHide.md), [Rabbit's Foot](../items/RabbitsFoot.md)
- [Fox](Fox.md), [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game test was run. Spawn conditions, data packs, gamerules, and later builds can change the result.

[rabbit]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Rabbit.java
[entities]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/EntityType.java
[rabbit-food]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/rabbit_food.json
[desert]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/desert.json
[snowy_plains]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/snowy_plains.json
[ice_spikes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/ice_spikes.json
[snowy_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/snowy_taiga.json
[snowy_slopes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/snowy_slopes.json
[grove]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/grove.json
[flower_forest]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/flower_forest.json
[taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[old_growth_pine_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/old_growth_pine_taiga.json
[old_growth_spruce_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/old_growth_spruce_taiga.json
[meadow]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/meadow.json
[cherry_grove]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/cherry_grove.json
[dry_midlands]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[placements]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[rabbit-ground]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/rabbits_spawnable_on.json
[animal]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Animal.java
[natural]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[normal]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[primordial]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[gold]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_gold_rabbits.json
[age]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/AgeableMob.java
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[mob]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Mob.java
[entity]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Entity.java
[breed]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[white]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_white_rabbits.json
[rabbit-renderer]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/client/renderer/entity/RabbitRenderer.java
[fox]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Fox.java
[cat]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Cat.java
[wolf]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java
[rabbit-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/rabbit.json
[looting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java
[foot-chance]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java
[player-credit]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java
[smelts]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[living]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java
