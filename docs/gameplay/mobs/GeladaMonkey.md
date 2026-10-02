# Gelada Monkey

The **Gelada Monkey** (`minecraft:gelada_monkey`) accepts **Dead Bushes for breeding** and **Wheat for clearing nearby vegetation**. It has **18 health points (9 hearts)** and a base attack attribute of **4 damage points**. It can retaliate and target other Geladas, so a group should not be treated as a harmless work crew. [Attributes and goals][gelada]

## Availability in this snapshot

The entity, its attributes, and its [Gelada Monkey Spawn Egg](../items/GeladaMonkeySpawnEgg.md) are registered. No Gelada entry was found in the 68 bundled biome spawn files, and there is no Gelada spawn-placement registration. Its additional random spawn check does not supply a biome or a natural spawn entry. No natural mountain or savanna location is established by these sources; the verified setup route is a Creative egg or an administrator-provided mob. [Registration][registration] · [Attributes][attributes] · [Placements][placements] · [Natural spawn selection][natural]

## Feeding and breeding

Hold a [Dead Bush](../items/DeadBush.md) or [Wheat](../items/Wheat.md) to tempt a Gelada when its higher-priority behavior permits it. The two foods have different uses:

| Item used on the monkey | Current interaction |
| --- | --- |
| Dead Bush | Puts an eligible adult into love mode, or speeds a baby's growth |
| Wheat | Starts a vegetation-clearing period when its clearing timer is zero |

Dead Bush is the only member of the bundled breeding-food tag. Two eligible adults in love have a registered breeding goal and produce a baby Gelada when they can complete it. A baby takes **24,000 ticking game ticks**, about **20 minutes at 20 TPS**, to reach adulthood without feeding; successful parents receive a **6,000-tick**, roughly five-minute breeding cooldown. [Food tags][breed-food] · [Wheat tag][clear-food] · [Animal feeding and breeding][animal] · [Age handling][age] · [Breeding goal][breed-goal]

Food does not tame the Gelada or assign an owner, and neither food interaction is a direct health-restoration routine. There is no player riding or command-to-sit interaction in its implementation. Its ordinary sit and grooming behavior is autonomous. [Interactions][interact] · [Goals][gelada]

## Clearing plants with Wheat

Use one Wheat on a Gelada whose clearing timer has expired. The initiating monkey gets **300–599 ticks** of clearing time, roughly **15–30 seconds at 20 TPS**. It can give the same timer to up to **three or four nearby Geladas** within its 15-block expanded search box, excluding companions that are fighting, in love, or in their revenge state. Successful feeding consumes one Wheat outside Creative. [Clearing interaction and group selection][interact] · [Item consumption][consume]

During that period, the clearing goal searches nearby positions and destroys reachable blocks from this exact bundled list:

- Short Grass and Tall Grass
- Fern and Large Fern
- Dead Bush
- Glow Lichen

**Grass Blocks, crops, flowers, and trees are not on this list.** The search is local and opportunistic; it does not clear an entire field or promise a fixed number of plants. A plant already selected can be finished after the timer expires. Higher-priority combat can interrupt the work. [Plant tag][plants] · [Clearing search and goal][clear-goal] · [Priorities][gelada]

Clearing requests ordinary drops with an **empty tool**, so it is not a replacement for Shears. In particular, clearing a Dead Bush yields its possible **0–2 Sticks**, not a collectable Dead Bush for breeding. The current clearing call does **not** check `mobGriefing`; disabling that rule alone does not protect these plants. Normal block-drop rules still control whether resulting items appear. Keep valuable tagged decorations away from the work area. [Destruction call][clear-goal] · [Empty-tool drop path][destroy] · [Block-drop gate][drop-gate] · [Dead Bush loot][bush-loot]

## Groups, grooming, and fighting

Geladas can sit and groom another nearby Gelada. During uninterrupted close grooming, the **groomer** heals one health point every 50 grooming-goal ticks. This is not a player healing command or a guaranteed wall-clock healing rate. Combat, love mode, and revenge state can prevent a recipient from continuing to be groomed. [Grooming goal][groom] · [Recipient conditions][interact]

The leader form is visibly larger as an adult, but uses the same registered health and attack attributes. A normal individual egg spawn has a **25% leader flag chance**; offspring factories use **50%**, and the flag is only expressed as a leader after adulthood. [Leader checks and attributes][gelada] · [Offspring and spawn initialization][interact] · [Rendering scale][render]

**Other Geladas can be attack targets even when they are not leaders.** The current nearest-Gelada goal has no leader, adult, or love-mode filter. A player who hurts a Gelada can also trigger retaliation and alerts to nearby monkeys. Do not assume breeding hearts or a leader's chest display makes a shared enclosure safe. [Target registration][goals] · [Target selection][target] · [Retaliation][retaliation]

There are two combat integration limits. Ordinary melee calls the shared two-argument damage handler, bypassing the older one-argument swipe method; the zero-damage leader-sparring rule inside that animation therefore does not establish harmless normal fights. Also, taking damage from another Gelada sets a revenge counter that has no countdown in the checked class, preventing its melee goal from restarting while that state remains. These source findings limit predictable group behavior; they were not reproduced in-game. [Custom damage and swipe handling][gelada] · [Active melee caller][melee] · [Shared damage][damage]

## Persistence and drops

Geladas inherit the animal rule that prevents ordinary distance despawning. Their leader flag and clearing timer are saved. Their bundled death-loot file is an empty table, so it supplies **no item drops**; an eligible adult death can still award the inherited **1–3 XP** with normal player-credit and mob-loot conditions. There is no unique Gelada resource to farm from deaths. [Animal persistence and XP][animal] · [Saved state][save] · [Death loot][loot] · [Empty-pool codec][loot-codec] · [XP conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked active registrations, all 68 bundled biome files, food and plant tags, breeding and clearing callers, goal priorities, grooming, combat signatures, save data, and the loot codec. No in-game spawning, breeding, clearing, grooming, combat, or drop test was run. Server data can change tag contents and availability.

Related: [Gelada Monkey Spawn Egg](../items/GeladaMonkeySpawnEgg.md) · [Dead Bush](../items/DeadBush.md) · [Wheat](../items/Wheat.md) · [Gorilla](Gorilla.md) · [Mobs](Mobs.md)

[gelada]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L647-L649
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L170
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[breed-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/gelada_monkey_breedables.json
[clear-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/gelada_monkey_land_clearing_foods.json
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L227
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[interact]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L317-L397
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[plants]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/gelada_monkey_grass.json
[clear-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L400-L451
[destroy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Block.java#L378-L419
[bush-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dead_bush.json
[groom]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/GeladaAIGroom.java
[render]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/client/render/RenderGeladaMonkey.java#L25-L42
[goals]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L93-L122
[target]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L22-L75
[retaliation]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L33-L115
[melee]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L144
[damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1320
[save]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L124-L138
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/gelada_monkey.json
[loot-codec]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/storage/loot/LootTable.java#L38-L50
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
