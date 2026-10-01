# Armadillo

The **Armadillo** is a passive animal and a renewable source of [Armadillo Scutes](../items/ArmadilloScute.md), used for [Wolf Armor](../items/WolfArmor.md). Approach without sprinting or riding another entity if you want it to stay relaxed. It curls up around threats, which interrupts feeding and breeding, although adult brushing remains a separate interaction. [Scute production][shedding] · [Threats][threats] · [Interactions][interactions]

## At a glance

- **Health:** 12 points (6 hearts)
- **Adult size:** 0.7 blocks wide × 0.65 blocks tall
- **Base movement-speed attribute:** 0.14, not a blocks-per-second measurement
- **Food:** Spider Eye
- **Entity ID:** `minecraft:armadillo`

The listed attributes are actively registered. [Attributes][attributes] · [Attribute registration][active-attributes] · [Entity size][registration] · [Food tag][food-tag]

## Finding one

Natural spawning is wired into both the placement registry and bundled biome populations:

- **Savanna, Savanna Plateau, and Windswept Savanna:** configured groups of 2–3
- **Badlands, Eroded Badlands, and Wooded Badlands:** configured groups of 1–2

The spawn predicate requires raw brightness above 8 and a block in the Armadillo-spawnable tag below it. That tag resolves to **Grass Block, Red Sand, Coarse Dirt, and the bundled badlands terracotta set**. These are spawn conditions and configured group sizes, not a promise that every suitable patch contains an Armadillo. [Biome data][biomes] · [Spawn registration][placement] · [Spawn check][spawn-check] · [Ground tag][ground] · [Grass entry][animal-ground] · [Terracotta entries][terracotta] · [Brightness][brightness]

Creative provides the [Armadillo Spawn Egg](../items/ArmadilloSpawnEgg.md). With command permission, `/summon minecraft:armadillo` creates one directly. [Spawn-egg item][egg] · [Creative entry][creative]

## Feeding and breeding

**[Spider Eye](../items/SpiderEye.md)** is the only item in the bundled Armadillo-food tag. Holding it can attract a relaxed animal; feeding it to two ready adults lets their mating behavior produce a baby. The parents then receive the normal **6,000-tick breeding cooldown**, about five ticking minutes at 20 TPS. Babies can follow adults, and feeding a relaxed baby uses the normal growth-speedup interaction. [Food check][food] · [Food tag][food-tag] · [Temptation and mating][idle-ai] · [Baby feeding][animal-food] · [Cooldown][breeding]

If an Armadillo is curled up or unrolling, wait until it relaxes before feeding. The frightened-state interaction refuses ordinary feeding, and rolling up clears its current breeding state. Giving food does not tame the animal or provide owner commands, and this class has no food-healing interaction like a Wolf's. Use an enclosure for keeping adults nearby. [Interaction priority][interactions] · [Rolling clears breeding][rolling]

## Rolling up and staying safe

Nearby **undead mobs, a recent attacker, and non-spectator players who are sprinting or riding another entity** can trigger fear. The threat check uses a box extending roughly seven blocks horizontally and two vertically from the animal. Walk rather than sprint near the pen, dismount before tending it, and keep hostile mobs away. [Threat check][threats] · [Active sensor][sensor]

Rolling stops its movement. It eventually unrolls when its recent-danger memory expires, but repeated threats can keep refreshing that memory. It cannot stay rolled up while panicking, in liquid, leashed, riding something, or carrying a passenger. Those restrictions make a dry, sheltered enclosure more reliable than expecting the shell to handle every situation. [Rolling][rolling] · [Rolling restrictions][restrictions] · [Fear-state behavior][fear-ai]

While scared, incoming damage is changed to **(damage − 1) ÷ 2** before the normal damage handler. For example, a five-point hit is passed onward as two points before any further handling. This is damage reduction, not invulnerability; avoid fire, suffocation, and other hazards. [Damage handling][damage]

Ordinary Spiders have an active avoidance goal for nearby **unfrightened** Armadillos. That is a useful behavior to know, but it does not make an enclosure a complete defense against hostile mobs. [Spider avoidance][spiders]

## Collecting scutes

Only **adults** provide scutes through the checked routes:

- **Brush:** interact with an adult using a [Brush](../items/Brush.md). The bundled interaction loot table produces one scute per successful brushing. The interaction is checked before the scared-state refusal, so curling does not itself block adult brushing
- **Natural shedding:** a living adult periodically drops one scute, then resets its timer to **6,000–11,999 ticking game ticks**, approximately five to ten minutes at 20 TPS. The remaining timer is saved; time while the animal is not ticking does not advance it

The brushing method has no animal-side cooldown and attempts to damage the Brush by **16 durability** per success. Read the [Brush durability warning](../items/Brush.md#fully-worn-brushes) before treating that as a fixed number of possible scutes: the fully worn Brush checks differ between mob and archaeology interactions. [Brushing][interactions] · [Brush loot][brush-loot] · [Shedding][shedding] · [Shed loot][shed-loot] · [Saved timer][saving]

The Armadillo death-loot table has no item pools. Keep the animal alive for renewable scutes instead of expecting a scute reward from killing it. [Death loot][death-loot]

## Related pages

- [Armadillo Scute](../items/ArmadilloScute.md)
- [Brush](../items/Brush.md)
- [Wolf Armor](../items/WolfArmor.md)
- [Spider Eye](../items/SpiderEye.md)
- [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Active attributes, biome spawn data, placement wiring, food tags, fear behavior, breeding, brushing, shedding, and death loot were checked separately. No in-game spawning, rolling, feeding, breeding, brushing, or damage test was run.

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L81-L83
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L116
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L266-L268
[food-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/armadillo_food.json
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L106
[spawn-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L223-L227
[ground]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/armadillo_spawnable_on.json
[animal-ground]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[terracotta]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/badlands_terracotta.json
[brightness]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L112-L114
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1795
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1974
[food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L218-L221
[idle-ai]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/ArmadilloAi.java#L117-L158
[animal-food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[breeding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L227
[interactions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L299-L331
[rolling]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L257-L273
[threats]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L229-L241
[sensor]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/sensing/SensorType.java#L31-L33
[restrictions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L324-L326
[fear-ai]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/ArmadilloAi.java#L160-L227
[damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L275-L297
[spiders]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Spider.java#L53-L60
[brush-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/brush/armadillo.json
[shedding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L139-L153
[shed-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/gameplay/armadillo_shed.json
[saving]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L244-L255
[death-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/armadillo.json
