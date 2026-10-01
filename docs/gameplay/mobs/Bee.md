# Bee

A **Bee** is a neutral flying animal that visits flowers and returns nectar to a [Bee Nest or Beehive](../blocks/BeeHousing.md). Keep flowers, housing, and safe flight space close together to support honey production. Bees can be bred and tempted with suitable flowers, but do not have a pet-owner or command system. [Active goals][goals] · [Food interaction][food-use] · [Housing entry][enter]

## Basic traits

- **Health:** 10 points, or five hearts
- **Base sting damage:** 2 points, before the target's damage handling
- **Registered size:** 0.7 blocks wide and 0.6 blocks tall
- **Movement attributes:** 0.6 flying speed and 0.3 ground movement speed; these are attributes, not measured blocks per second

The Bee's attribute builder is registered in the active default-attribute table. A [Bee Spawn Egg](../items/BeeSpawnEgg.md) is also registered and available in Creative. [Attributes][attributes] · [Attribute wiring][attribute-wiring] · [Entity registration][entity] · [Spawn egg][egg] · [Creative entry][creative]

## Finding Bees

Look for **occupied Bee Nests attached to trees**. The active tree decorator places a nest only when it finds suitable open space, then stores **two or three Bees** inside it. Those occupants emerge through the nest's normal release behavior. This is a verified world-generation route, rather than an assumption based on the spawn egg. [Nest generation][nest-generation] · [Tree decorator callsite][tree-call] · [Occupant release][release]

**Cherry Grove** is one source-backed place to look: its biome features run the cherry-tree placement, which uses a tree configuration with a 5% nest-decorator probability. **Meadow** also selects tree configurations with nests. Tree density, successful tree placement, and nest-placement space are separate conditions, so neither means that every tree or every patch of the biome contains Bees. [Cherry Grove features][cherry-biome] · [Cherry placement][cherry-placement] · [Cherry nest configuration][cherry-config] · [Meadow features][meadow-biome] · [Meadow tree selection][meadow-config]

You can also grow **Oak, Birch, or Cherry saplings near a flower** to select the bee-bearing tree route. The flower search covers a five-by-five horizontal area centered on the sapling, from one block below to one above. These flower-selected configurations have a **5% chance to attempt nest decoration**; they do not guarantee a nest, and the decorator still needs space. A [Dandelion](../items/Dandelion.md) or [Poppy](../items/Poppy.md) is an uncomplicated flower choice. Natural growth and [Bone Meal](../items/BoneMeal.md) both reach the same sapling-growth method. [Tree choices][tree-choices] · [Flower-dependent selection][tree-selection] · [Flower search][tree-flowers] · [Flower tag][flowers] · [Oak configuration][oak-config] · [Birch configuration][birch-config] · [Sapling callbacks][sapling]

## Luring and breeding

Hold an item in the Bee food tag to tempt Bees toward you. Feed a suitable item to each of two adult Bees ready to breed, and the ordinary animal breeding system can produce a baby. Feeding a baby a suitable item speeds its growth. After breeding, each parent's cooldown is **6,000 ticks**, about five minutes at normal tick speed. Feeding flowers is breeding/growth interaction, not an owner-taming roll. [Tempt and breed goals][goals] · [Food tag][food] · [Adult and baby feeding][animal-food] · [Offspring][offspring] · [Breeding cooldown][breeding]

The accepted tag is broader than just small flowers. It includes Dandelion, Poppy, tulips and other flowers, plus items such as Flowering Azalea, Flowering Azalea Leaves, Cherry Leaves, Pink Petals, Wildflowers, and Cactus Flower. Honeycomb and Honey Bottles are not in this food tag. [Bundled Bee food][food]

**Avoid Open Eyeblossom and Wither Rose for Bee care.** Both appear in the food tag, but their special flower interaction takes precedence over breeding: feeding an Open Eyeblossom applies Poison to the Bee, and feeding a Wither Rose applies Wither. Their placed blocks can also harm Bees under their contact-effect conditions. Use ordinary safe flowers for an apiary. [Special feeding path][food-use] · [Eyeblossom effect][eyeblossom] · [Wither Rose effect][wither-rose]

## Flowers and pollination

Pollination uses a separate **block** tag from the held-item food tag. Many entries overlap, but the Bee still needs to reach the planted block. The source rejects waterlogged candidates and uses the upper half of a Sunflower. Its nearby flower search checks up to five blocks in each direction and requires a reachable path. [Attractive blocks][attractive] · [Block checks][attract-check] · [Flower search][flower-search]

A Bee with no nectar can begin pollinating when its search cooldown permits and it is not raining. Successful pollination requires **more than 400 successful pollination ticks**, roughly twenty seconds at normal tick speed, before stopping the goal grants nectar. Travel, interruptions, weather, and later hive work add time, so this is not a guaranteed honey-production interval. Taking damage interrupts pollination. [Pollination conditions][pollination] · [Completion][pollination-stop] · [Damage interruption][hurt]

Bees carrying nectar can help compatible plants below them grow. The active behavior checks plants one or two blocks below, uses the Bee-growable tag, and handles ordinary crops, stems, Sweet Berry Bushes, and Cave Vines. This is occasional growth assistance, not an instant mature-crop effect or a guaranteed yield per trip. [Growth behavior][growth] · [Growable tag][growables] · [Crop tag][crops]

## Housing and safe care

Each nest or hive holds **three Bees**. Bees looking for a home search nearby Bee-home points of interest within 20 blocks and prefer available space. A Bee wants to enter when carrying nectar, when the relevant night/rain condition applies, or after an extended unsuccessful nectar search. Angry Bees, Bees that have stung, and Bees avoiding a nearby fire do not follow the ordinary entry path. [Home search][home-search] · [Entry conditions][entry-conditions] · [Entry and capacity][enter]

Keep the entrance clear, provide safe flowers, and keep Bees away from water and exposed campfires. A Bee in water for more than twenty consecutive ticks starts taking drowning damage. Lit campfires also damage living entities touching them even though their smoke is useful for harvesting. See [Bee housing](../blocks/BeeHousing.md) for smoke placement, harvesting, storage of Bees, and moving an occupied hive. [Water damage][water] · [Campfire contact][campfire]

## Anger and stinging

Attacking a Bee can alert other Bees. Disturbing occupied housing or harvesting without recognized smoke can also make Bees target nearby players. Smoke protects the relevant housing interactions; it is not a general command to clear every already-angry Bee's target. [Retaliation goals][goals] · [Alert behavior][alert] · [Housing anger][hive-anger] · [Harvest response][harvest]

A successful sting can add **Poison I for ten seconds on Normal or eighteen seconds on Hard**. The sting callback adds no Poison on Easy. Once it has stung successfully, the Bee clears its anger, stops using the attack goal, and enters a post-sting death routine. The death check is probabilistic over subsequent AI ticks; do not treat feeding flowers as a cure or a fixed wall-clock survival guarantee. [Sting][sting] · [One-sting attack check][attack] · [Post-sting routine][water]

## Drops

The bundled Bee death-loot table has **no item pools**. Honeycomb and Honey Bottles come from harvesting full housing, not from killing Bees. Preserve the colony and collect its products through [Bee housing](../blocks/BeeHousing.md#harvesting). [Bee loot][loot] · [Harvest handler][harvest]

## Related pages

- [Bee housing](../blocks/BeeHousing.md)
- [Beehive](../items/Beehive.md)
- [Bee Nest](../items/BeeNest.md)
- [Honeycomb](../items/Honeycomb.md)
- [Honey Bottle](../items/HoneyBottle.md)
- [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Active attributes, occupied-tree-nest routes, sapling growth, food and pollination tags, breeding, flower effects, crop growth, hive entry, retaliation, stinging, and loot were inspected. No in-game spawning, breeding, pollination, farming, or combat test was run.

[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L175-L197
[food-use]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L569-L590
[enter]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L750-L778
[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L540-L546
[attribute-wiring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L122
[entity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L304-L306
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1802
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1978
[nest-generation]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L39-L67
[tree-call]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L157-L160
[release]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L189-L244
[cherry-biome]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/cherry_grove.json
[cherry-placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/placed_feature/trees_cherry.json
[cherry-config]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/configured_feature/cherry_bees_005.json
[meadow-biome]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/meadow.json
[meadow-config]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/configured_feature/meadow_trees.json
[tree-choices]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L27-L63
[tree-selection]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L105-L118
[tree-flowers]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L195-L203
[flowers]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/flowers.json
[oak-config]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/configured_feature/oak_bees_005.json
[birch-config]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/configured_feature/birch_bees_005.json
[sapling]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L45-L73
[food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/bee_food.json
[animal-food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L157
[offspring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L616-L619
[breeding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[eyeblossom]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/EyeblossomBlock.java#L101-L117
[wither-rose]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/WitherRoseBlock.java#L74-L89
[attractive]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[attract-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L678
[flower-search]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1234-L1253
[pollination]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1105-L1141
[pollination-stop]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1160-L1169
[hurt]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L640-L647
[growth]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L950-L1006
[growables]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/bee_growables.json
[crops]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/crops.json
[home-search]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1027-L1063
[entry-conditions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L334-L345
[water]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L364-L388
[campfire]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L108-L117
[alert]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1009-L1024
[hive-anger]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L89-L127
[harvest]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L143-L189
[sting]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L225-L250
[attack]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L707-L720
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/bee.json
