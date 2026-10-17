# Turtle

**Turtles** are coastal animals whose babies supply [Turtle Scutes](../items/TurtleScute.md) when they grow up. Use Seagrass to attract and breed them, then protect the eggs and hatchlings. Moving an adult does not reset its remembered home. Its ID is `minecraft:turtle`. [Entity][entities] · [Goals and home][turtle-goals] · [Growth reward][turtle-scute]

## Obtaining

The bundled natural spawn entry is in **Beach** (`minecraft:beach`), not Snowy Beach or Stony Shore. Its registered ground-spawn predicate requires a sand-tag block below, **raw brightness greater than 8**, and a position **below sea level plus 4**. With the normal Overworld sea level of 63, the height condition is **Y below 67**. Sand, Red Sand and Suspicious Sand are the bundled sand-tag members; ordinary ground-placement and collision checks also apply. [Beach entry][beach] · [Registered predicate][spawn-reg] · [Turtle predicate][turtle-goals] · [Brightness][bright] · [Sand lookup][turtle-egg] · [Sand tag][sand] · [Sea level][sea-level] · [Placement][placement] · [Spawn checks][spawn-check]

The [Turtle Spawn Egg](../items/TurtleSpawnEgg.md) is an ordinary listed item, so the [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Creative. This is separate from finding a natural Turtle or hatching a placed egg. [Listing][egg-list] · [Egg use][egg-use] · [Browser list][browser-list] · [Client][browser-client] · [Server][browser-server]

## Behavior

Turtles can live on land and in water; the bundled underwater-breathing tag includes them. They seek water, wander and can travel away from their home. They are not tameable companions and cannot be attached to a Lead or captured through the ordinary fish-bucket interaction. Holding **Seagrass** can attract them when higher-priority goals allow. [Goals, movement and leash restriction][turtle] · [Breathing tag][breathing-tag] · [Breathing check][breathing-check] · [Accepted food][turtle-food]

### Breeding near home

Feed Seagrass to two ready adults close together. The active mating goal marks one parent as carrying eggs, gives both parents a **6,000-tick cooldown** (five minutes), and does not immediately create a baby. The egg-carrying parent cannot enter love mode again while it still has eggs. [Food interaction][animal] · [Food and egg gate][turtle] · [Active mating][turtle-mate] · [Mating caller][breed-call]

That parent returns toward its remembered home and uses the registered laying goal to place **one to four eggs** near home on suitable sand. Do not expect a transported adult to adopt a new nesting beach merely because you fed it there. For precise laying, hatching, Silk Touch recovery and protection rules, use the existing [Turtle Egg guide](../blocks/AnimalEggs.md#turtle-eggs). [Remembered home][turtle-goals] · [Return goal][turtle-home] · [Laying][turtle-lay]

To establish a new nesting site, hatch collected [Turtle Eggs](../items/TurtleEgg.md) there: the hatch routine assigns that position as each baby's home. Keep the nest protected and provide reachable water. [Hatch home][turtle-egg]

### Raising babies for Scutes

A newly hatched baby starts at age **−24,000 ticks**, about 20 minutes to adulthood while ticking. Feeding Seagrass shortens roughly one tenth of its remaining baby growth time. At the transition to adulthood it drops **one Turtle Scute**, provided mob loot is enabled. This is a growth reward, not an adult death drop. [Hatch age][turtle-egg] · [Growth tick][growth] · [Feeding][animal] · [Age transition][age] · [Active reward][turtle-scute] · [Reward-table key][scute-key] · [One-Scute table][scute-loot]

Protect hatchlings on land: the checked Zombie and Skeleton target goals specifically include baby Turtles that are not in water. Water access helps with those particular target filters; it is not universal protection from hazards. [Baby selector][turtle] · [Zombie target][zombie-target] · [Skeleton target][skeleton-target]

Also separate hatchlings from [Foxes](Fox.md#hunting-sleep-and-protection), [Ocelots](Ocelot.md#other-animals-and-creepers), untamed [Cats](Cat.md#other-mobs-persistence-and-drops), and wild [Wolves](Wolf.md#protection-behavior-and-drops). Their prey-selection goals also choose baby Turtles outside water; the untamed requirement applies to new prey selection for Cats and Wolves. Trust does not disable Fox or Ocelot hunting, although newly bred Foxes have the [missing-prey-goals caveat before reload](Fox.md#hunting-sleep-and-protection). [Baby/out-of-water filter][care-baby-filter] · [Fox targets and installation][care-fox-targets] [care-fox-install] · [Ocelot targets][care-ocelot] · [Cat targets][care-cat] · [Wolf targets][care-wolf] · [Untamed selection gate][care-untamed]

## Drops

With mob loot enabled, an adult's death table gives **0–2 Seagrass**, with a randomized Looting bonus of up to one additional item per enchantment level. A death caused by lightning also supplies **one Bowl**. It does **not** supply a Turtle Scute. An eligible adult player-credit kill gives **1–3 experience**; babies do not give the ordinary death-table items or death experience. [Death table][turtle-loot] · [Looting][looting] · [Age and mob-loot gates][loot-gates] · [Experience amount][animal] · [Death processing][death]

## Notes

- **Health:** 30 points, or 15 hearts. [Registered attributes][attributes] · [Values][turtle-goals]
- Turtles inherit the ordinary Animal rule against distance despawning, but they can still wander away or die. [Persistence rule][animal] · [Travel][turtle-travel]
- Natural candidates are selected from loaded biome data before the registered predicate is checked. [World loading][world-load] · [JSON loading][registry-load] · [Biome/structure selection][spawn-choice]

## Related pages

- [Placed Turtle Eggs: acquisition, hatching and protection](../blocks/AnimalEggs.md#turtle-eggs)
- [Turtle Scute](../items/TurtleScute.md) and [Turtle Shell](../items/TurtleShell.md)
- [Terrapin](Terrapin.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `384aa3dfa1473af7753759569de95012d5bdc46f`. Checked loaded Beach candidates, the registered spawn predicate, food/goal dispatch, remembered home, egg caller chain, baby growth and the separate growth/death loot paths. Egg-block mechanics remain with the existing Animal Eggs owner. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed tags, recipes, biome entries and loot.

The added animal-predator separation guidance was source-reviewed on **2026-10-10** at `1b9b103398fd70d5b5152b93a1d0abc581fffc19` against active target registration, baby/out-of-water selection, Cat/Wolf untamed acquisition, and Fox prey-goal initialization. This is not an in-game enclosure trial. [Server goal registration][care-goal-dispatch] · [Target selection][care-target-selection]

[entities]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/EntityType.java#L1449-L1456
[turtle-goals]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L131-L160
[turtle-scute]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L259-L265
[beach]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/worldgen/biome/beach.json#L88-L106
[spawn-reg]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L150-L150
[bright]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[turtle-egg]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L90-L123
[sand]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/block/sand.json
[sea-level]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[placement]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L24-L41
[spawn-check]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2108-L2110
[egg-use]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[turtle]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[breathing-check]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L385
[turtle-food]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/item/turtle_food.json
[animal]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L158
[turtle-mate]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L296-L329
[breed-call]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L80
[turtle-home]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L333-L399
[turtle-lay]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L437-L483
[growth]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[age]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L97-L104
[scute-key]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L104-L104
[scute-loot]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/loot_table/gameplay/turtle_grow.json
[zombie-target]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L121-L121
[skeleton-target]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L84-L84
[turtle-loot]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/loot_table/entities/turtle.json
[looting]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L78
[loot-gates]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[death]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L261-L262
[turtle-travel]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L584-L648
[world-load]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[spawn-choice]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[care-baby-filter]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L74
[care-fox-targets]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Fox.java#L155-L162
[care-fox-install]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Fox.java#L322-L360
[care-ocelot]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Ocelot.java#L95-L106
[care-cat]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Cat.java#L96-L113
[care-wolf]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L125-L146
[care-untamed]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/ai/goal/target/NonTameRandomTargetGoal.java#L11-L24
[care-goal-dispatch]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Mob.java#L150-L153
[care-target-selection]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L84
