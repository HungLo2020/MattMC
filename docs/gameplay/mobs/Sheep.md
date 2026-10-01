# Sheep

**Sheep** (`minecraft:sheep`) provide renewable wool for beds, carpets, and other crafts. Keep adults on a grazable floor and use [Shears](../items/Shears.md) to harvest repeatedly. They have **8 health points (4 hearts)** and use panic, feeding, and breeding goals rather than a player-attack goal. [Sheep behavior][sheep] · [Active attributes][attributes]

## Finding sheep

The bundled **plains, forest, savanna, and taiga** biome tables include sheep with a listed group size of four. Ordinary ground spawning requires **grass blocks underneath** and raw brightness **above 8**. These are confirmed examples and placement requirements, not a complete biome list or a promise that a grass patch will produce sheep. [Plains][plains] · [Forest][forest] · [Savanna][savanna] · [Taiga][taiga] · [Spawn registration][spawn] · [Animal spawn check][animal] · [Ground tag][ground]

Their registered adult size is **0.9 blocks wide × 1.3 blocks tall**. Creative players can use the [Sheep Spawn Egg](../items/SheepSpawnEgg.md). Ordinary sheep do not despawn merely because a player moves far away. [Registration][registration] · [Persistence][animal]

Newly spawned sheep use biome-dependent color rolls: **white** is the most common color in temperate areas, **brown** in warm-tagged biomes, and **black** in cold-tagged biomes. Each scheme also allows uncommon colors, including pink. Color-selection tags do not themselves create sheep spawns in otherwise unsuitable biomes. [Color selection][colors] · [Warm tag][warm] · [Cold tag][cold]

## Leading and breeding

- Hold **[Wheat](../items/Wheat.md)** to attract sheep. It is the only item in the bundled sheep-food tag
- Feed two ready adults one wheat each, and give them room to approach one another. After breeding, both parents wait **6,000 ticking game ticks**, about **5 minutes** at 20 ticks per second, before breeding again
- A newborn lamb starts with **24,000 ticks** until adulthood, about **20 minutes** while ticking. Feeding wheat removes approximately 10% of the remaining growth time, rounded down to whole seconds

Lambs follow adults. If a use interaction only detaches a lead held by you, use the food again after the lead interaction has finished. [Food tag][food] · [Goals and food check][sheep] · [Feeding and breeding][animal] · [Partner approach][breed-goal] · [Growth][age] · [Lead interaction][entity-interact]

## Shearing and regrowth

Use unbroken shears on a **living, adult, unsheared sheep**. The bundled shearing tables drop **1–3 wool of its current color**, then mark it sheared. A successful hand shearing costs **1 shears durability**. Lambs cannot be sheared, and the wool roll has no Fortune or Looting bonus. [Shearing interaction][shearing] · [Color routing][shear-loot] · [White-wool roll][white-shear]

**Shears cut attached leash connections before harvesting fleece.** That consumes the interaction, so a tethered sheep may need another use after the leads have dropped. A dispenser facing an eligible sheep can also shear it; its entity interaction checks leash cutting first as well. [Shared interaction order][mob-interact] · [Lead cutting][entity-interact] · [Dispenser registration][dispenser-registration] · [Dispenser behavior][dispenser]

Wool regrows when the sheep completes an eating action. It can eat **short grass, short dry grass, tall dry grass, or fern at its position**, or graze a **grass block directly below it**. With `mobGriefing` enabled, the plant is removed or the grass block becomes dirt. With that rule disabled, the block stays, but the eating callback still restores wool. **Feeding wheat does not directly regrow wool.** [Edible plants][edible] · [Eating goal][eating] · [Regrowth callback][regrowth] · [Food interaction][animal]

Keep access to grazing material for a renewable flock. Eating is an AI action with a random start check, so this guide does not promise a fixed interval between harvests. A lamb that finishes eating also advances its growth by **60 seconds**. [Eating goal][eating] · [Regrowth and lamb growth][regrowth]

## Dyeing and offspring color

Use a dye on a living **unsheared** sheep of a different color to change its wool color, consuming one dye in Survival. Dyeing is available for lambs too; a sheared sheep must regrow its fleece before this interaction succeeds. The stored color remains when wool regrows, making a dyed adult useful for repeated colored-wool harvests. [Dye interaction][dye] · [Stored color and regrowth][sheep]

A lamb's color uses the parents' dye colors as a two-ingredient crafting check. If the loaded recipes yield a dye, the lamb gets that result; otherwise it randomly inherits one parent's color. For example, the bundled red-dye plus white-dye recipe supports pink offspring from red and white parents. [Color mixing][mix] · [Pink-dye recipe][pink]

## Death drops and experience

With mob loot enabled, an adult drops **1–2 [Raw Mutton](../items/RawMutton.md)** and, if still unsheared, **one wool of its color**. Looting adds **0–1 mutton per level** as a rounded random bonus, up to **5 mutton at Looting III**; it does not increase the one-wool drop. Burning sheep, or a qualifying direct attacker's main-hand Fire Aspect enchantment, turn the meat into [Cooked Mutton](../items/CookedMutton.md). [Sheep loot][loot] · [White-wool death drop][white-death] · [Looting calculation][looting] · [Smelts-loot tag][fire-aspect]

Lambs do not drop ordinary death loot or experience. A qualifying player-attributed adult kill has a base reward of **1–3 experience**; breeding gives **1–7 experience** with mob loot enabled. [Baby restrictions][babies] · [Death XP conditions][death] · [Animal rewards][animal]

Related: [White Wool](../items/WhiteWool.md) · [Shears](../items/Shears.md) · [Cow](Cow.md) · [Chicken](Chicken.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game breeding, shearing, grazing, or dispenser test was run. Data packs can change food and biome tags, color-mixing recipes, and loot; the values above describe bundled behavior.

[animal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Animal.java
[age]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[plains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/plains.json
[savanna]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/savanna.json
[forest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/forest.json
[taiga]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[spawn]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[ground]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[attributes]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[warm]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_warm_variant_farm_animals.json
[cold]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_cold_variant_farm_animals.json
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[babies]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[fire-aspect]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[entity-interact]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Entity.java#L2107-L2209
[mob-interact]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1077
[sheep]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/sheep/Sheep.java
[registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L1176-L1178
[colors]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/sheep/SheepColorSpawnRules.java
[food]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/sheep_food.json
[shearing]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/sheep/Sheep.java#L137-L183
[shear-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/shearing/sheep.json
[white-shear]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/shearing/sheep/white.json
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[dispenser]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java
[edible]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/edible_for_sheep.json
[eating]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/goal/EatBlockGoal.java
[regrowth]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/sheep/Sheep.java#L282-L289
[dye]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/DyeItem.java#L25-L38
[mix]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/DyeColor.java#L115-L129
[pink]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_red_white_dye.json
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/sheep.json
[white-death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/sheep/white.json
