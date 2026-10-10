# Fox

A **Fox** is an animal that carries items, gathers berries, and can produce offspring that trust the players who bred them. It has **10 health points**, or **5 hearts**. Trust removes its normal avoidance of those players; it does not add ownership, a collar, an owner-follow command, or a commanded sitting pose. [Fox behavior and attributes][fox] · [Active registration][entities] [attributes]

## Finding Foxes

Natural spawn tables include **Taiga, Snowy Taiga, Old Growth Pine Taiga, Old Growth Spruce Taiga, and Grove**, with configured groups of **2–4**. The spawn predicate requires **raw brightness above 8** and **Grass Block, a Snow layer, Snow Block, Podzol, or Coarse Dirt below**, alongside natural-spawn population and obstruction checks. [Biome tables][taiga] [snowy_taiga][] [old_growth_pine_taiga][] [old_growth_spruce_taiga][] [grove][] · [Registered spawn predicate][placements] · [Ground tag][fox-ground] · [Brightness helper][animal] · [Spawn dispatch][natural]

Foxes are **Snow** variants in Snowy Taiga and Grove, and **Red** variants in the other listed biomes. The variant selector uses a biome tag that also includes snowy biomes without Fox spawn entries; a tag membership alone does not add natural Fox spawning there. A group shares its variant, with the third and later members initialized as babies. [Variant and group initialization][fox] · [Snow-Fox tag][snow-fox] · [Shared group counting][age]

The [Fox Spawn Egg](../items/FoxSpawnEgg.md) provides a Creative route. Moving a Fox to another biome does not change its saved variant. [Egg registration][items] · [Variant persistence][fox]

## Breeding a trusting Fox

1. Bring two adult Foxes into a secure space where they can approach one another
2. Crouch while approaching wild Foxes, then feed each **Sweet Berries or Glow Berries** directly
3. Let them breed; the baby records the player who fed each parent, supporting up to two different trusted players
4. Use a [Lead](../items/Lead.md) if you want to move the baby away; trust does not make it follow you, and it can still follow an adult Fox

These steps are a source-based example, **not a tested enclosure or taming trial**. Wild Foxes normally avoid non-crouching Survival players within their search range. Feeding the adults does not make those parents trust you. There is no berry-holding temptation goal in the registered Fox goals, so do not rely on holding berries as a walking lure. [Avoidance, parent-following, and breeding goals][fox] · [Crouching state][entity] · [Food tag][fox-food]

Both accepted berries enter love mode for eligible adults and speed baby growth through the shared animal feeding handler. They do **not** provide a direct healing interaction. The parents receive a **6,000-game-tick** cooldown, normally **5 minutes**; the baby starts with **24,000 game ticks** of growth, normally **20 minutes**. Baby feeding removes approximately **10% of the remaining growth time**. [Shared feeding][animal] · [Fox birth and trust assignment][fox] · [Growth calculation][age]

A baby's variant is selected from either parent with equal probability. Trust is assigned from the current breeding players, rather than copied wholesale from the parents' trusted lists. A matching spawn egg used on an existing Fox separately gives the created offspring trust in the player using it. [Offspring variant, breeding trust, and egg callback][fox] · [Egg interaction dispatch][mob]

## Berry gathering

With **mobGriefing** enabled, an awake Fox can gather Sweet Berries from bushes at **growth stage 2 or 3**, or harvest Glow Berries from a berry-bearing Cave Vine. Other goals, pathfinding, and its waiting period can delay gathering; a nearby crop is not a promised collection rate. Foxes are exempt from the Sweet Berry Bush's slowing and thorn-damage callback. [Berry goal][fox] · [Bush collision behavior][berry-bush]

A Sweet Berry Bush yields **1–2 berries at stage 2**, or **2–3 at stage 3**, and resets to stage 1. If the Fox's mouth is empty, one berry goes into it; the remainder is dropped beside the bush. If it already holds an item, the entire harvest is dropped. Loose block drops also require **doTileDrops**. [Berry count, mouth handling, and reset][fox] · [Block-drop gate][block]

Harvesting a Cave Vine clears its berries and uses the bundled interaction loot table to drop **one Glow Berry**, subject to the block-drop rule. That path does not directly put the berry into the Fox's mouth, although the Fox may later pick it up. [Vine callback][cave-vines] · [Harvest loot][vine-loot] · [Block-drop gate][block]

## Items in the mouth

A Fox can pick up **one item** from a loose stack, leaving the rest behind. Ground-item pickup requires **mobGriefing** and an item whose pickup delay has expired. An empty mouth accepts items; after its eating counter advances, a food item can replace a held non-food item. A new non-food item does not ordinarily replace another held item, and food does not replace already held food. [Pickup and replacement rules][fox] · [Active pickup gate][mob]

To recover a non-food item, try dropping edible food nearby. On a successful replacement the Fox spits out its former item with a **40-game-tick pickup delay**, normally **2 seconds**. Wait before trying to collect it. This exchanges items in the world; there is no Fox inventory screen. [Spitting and pickup][fox]

For a routine exchange, choose ordinary dropped [Sweet Berries](../items/SweetBerries.md) or [Glow Berries](../items/GlowBerries.md): their bundled food has no consumption status effects. The pickup and replacement conditions above still apply; berries **cannot dislodge food already in the mouth**. [Berry registration][care-berries] · [Ordinary food components][care-food-components] · [Default consumable][care-food-effects]

Picked-up food can be consumed once the eating counter is **greater than 600 game ticks**, a little over **30 seconds**, provided the Fox is on the ground, awake, and has no attack target. The ground-pickup path resets that counter; directly gathered berries do not necessarily start a fresh 30-second wait. Food in the mouth follows item-consumption behavior, separately from berry feeding for love or growth. Ordinary food nutrition itself feeds players and does not heal a Fox. [Eating and pickup counter][fox] · [Consumption dispatch][consumable] · [Food listener][food-properties]

A swallowed food's separate effects can still help or harm the Fox: [Pufferfish](../items/Pufferfish.md) applies [Poison](../effects/Poison.md), [Poisonous Potato](../items/PoisonousPotato.md) can apply Poison, and a [Golden Apple](../items/GoldenApple.md) can heal it through [Regeneration](../effects/Regeneration.md). These effects require the Fox to actually eat the item under the conditions above; merely carrying it is not enough. [Configured food effects][care-food-effects] · [Application to the eater][care-apply-effects] · [Effect susceptibility][care-effect-gates] · [Regeneration healing][care-regen]

Spawn finalization also has a **20% chance** to give the Fox a carried item selected from Emerald, Egg, Rabbit's Foot, Rabbit Hide, Wheat, Leather, or Feather. These are occasional carried finds, not a fixed Fox death-loot table. [Spawn equipment][fox]

## Hunting, sleep, and protection

Initialized Fox prey goals target **Chickens, Rabbits, baby Turtles on land, and schooling fish**. Red Foxes prioritize the land prey goals; Snow Foxes prioritize fish. Foxes can stalk and pounce, so protect nearby livestock and leave room when planning an enclosure. Trust does not disable these prey goals. [Prey selectors, priorities, and pounce][fox]

**Current source limitation:** the fresh breeding path creates the baby without the spawn-finalization step that installs its prey-selection goals. Loading saved Fox data installs those goals. Do not infer reliable hunting behavior from a newly bred Fox before reload; this ordering was reviewed in code and was not tested in-game. [Goal installation and breeding path][fox] · [Entity factory versus finalized creation][entities] · [Entity-add dispatch][add-entity]

A Fox can react against an eligible attacker of a trusted entity through its defense goal, but this is conditional AI, not a guard command. It ordinarily avoids wild Wolves and Polar Bears when not defending. Wild Wolves themselves include Foxes in their prey selector. [Defense and avoidance][fox] · [Wolf targeting][wolf]

Foxes can sleep under shelter during the bright part of the day when no alerting entity is nearby; water, a target, or thunder wakes them. Their voluntary sitting and sleeping do not give you a sit toggle. Trust lists, variant, and pose state are saved, as is the carried mouth equipment. Foxes inherit the animal rule that prevents ordinary distance despawning. [Sleep, wake, and save handling][fox] · [Equipment persistence][living] · [Animal persistence][animal]

## Drops

The bundled Fox death-loot table contains **no ordinary item pool**. A Fox instead drops whatever is still in its mouth through a separate death handler; Looting does not multiply that mouth stack. This mouth-drop handler runs before the shared baby and **doMobLoot** gate, so it also covers a baby carrying an item or a world with that loot gamerule disabled. The dropped item can still be lost to the surrounding environment. [Fox loot table][fox-loot] · [Mouth drop][fox] · [Death dispatch and shared gates][living] · [Item-spawn helper][entity]

Adults can award **1–3 experience** with normal player kill credit and **doMobLoot** enabled; babies do not award that death experience. [Animal experience][animal] · [Experience gate][living]

## Related pages

- [Rabbit](Rabbit.md), [Cat](Cat.md), [Wolf](Wolf.md)
- [Sweet Berries](../items/SweetBerries.md), [Glow Berries](../items/GlowBerries.md), [Lead](../items/Lead.md)
- [Rabbit Hide](../items/RabbitHide.md), [Rabbit's Foot](../items/RabbitsFoot.md), [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game test was run. Spawn conditions, data packs, gamerules, and later builds can change the result.

The additional mouth-food care guidance was source-reviewed on **2026-10-10** at `1b9b103398fd70d5b5152b93a1d0abc581fffc19`, tracing the Fox's eating call through item consumption and status effects. Ordinary nutrition remains player-only; no in-game care trial was run. [Fox eating][care-eating] · [Stack dispatch][care-stack] · [Item dispatch][care-item] · [Consumption handler][care-consumption] · [Nutrition listener][care-nutrition]

[fox]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Fox.java
[entities]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/EntityType.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[snowy_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/snowy_taiga.json
[old_growth_pine_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/old_growth_pine_taiga.json
[old_growth_spruce_taiga]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/old_growth_spruce_taiga.json
[grove]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/grove.json
[placements]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[fox-ground]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/foxes_spawnable_on.json
[animal]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/Animal.java
[natural]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[snow-fox]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_snow_foxes.json
[age]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/AgeableMob.java
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[entity]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Entity.java
[fox-food]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/fox_food.json
[mob]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Mob.java
[berry-bush]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java
[block]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Block.java
[cave-vines]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/CaveVines.java
[vine-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/harvest/cave_vine.json
[consumable]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/Consumable.java
[food-properties]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/food/FoodProperties.java
[add-entity]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/ServerLevelAccessor.java
[wolf]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java
[living]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java
[fox-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/fox.json

[care-berries]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/Items.java#L2449-L2454
[care-food-components]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/Item.java#L366-L372
[care-food-effects]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L68
[care-apply-effects]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L31-L62
[care-effect-gates]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/LivingEntity.java#L978-L1012
[care-regen]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/effect/RegenerationMobEffect.java#L11-L24
[care-eating]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Fox.java#L199-L244
[care-stack]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L402
[care-item]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/Item.java#L194-L197
[care-consumption]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L92
[care-nutrition]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/food/FoodProperties.java#L40-L58
