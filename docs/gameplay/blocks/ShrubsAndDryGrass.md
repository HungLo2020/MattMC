# Shrubs and Dry Grass

**Bush, Short Dry Grass, Tall Dry Grass, and Firefly Bush** are small decorative plants with different harvesting and Bone Meal rules. **Tall Dry Grass occupies one block**, and Firefly Bush emits a small amount of block light independently of its visible firefly particles. [Dry plant and Bush registry] · [Firefly registry] · [Short dry growth] · [Tall dry growth]

The existing [Dead Bush guide](DeadBush.md) remains the owner of that plant's collection, pot decoration, and related [Gelada use](../items/DeadBush.md). It is included below to make the dry-plant differences clear. Ordinary [Short Grass](../items/ShortGrass.md), [Tall Grass](../items/TallGrass.md), and [flowers](Flowers.md) retain their own rules.

## Exact forms and harvest

All five are instant-breaking, noncolliding blocks with matching item IDs and no required mining-tool tier. Their **loot conditions** decide whether you recover the plant. The two dry-grass pages keep their existing filenames, but the actual IDs are `short_dry_grass` and `tall_dry_grass`, not `dry_short_grass` or `dry_tall_grass`. [Dry plant and Bush registry] · [Firefly registry] · [Bush item registry] · [Dry grass item registry] · [Block-derived item IDs] · [Tool gate]

| Plant and exact block/item ID | Normal player harvest | Bone Meal result |
| --- | --- | --- |
| <span id="bush"></span>[Bush](../items/Bush.md), `minecraft:bush` | **1 Bush with Shears or Silk Touch I+**; otherwise nothing | One eligible adjacent Bush. [bush loot] · [Bush growth] |
| <span id="short-dry-grass"></span>[Short Dry Grass](../items/DryShortGrass.md), `minecraft:short_dry_grass` | **1 Short Dry Grass with Shears or Silk Touch I+**; otherwise nothing | Replaced in place by Tall Dry Grass. [short_dry_grass loot] · [Short dry growth] |
| <span id="tall-dry-grass"></span>[Tall Dry Grass](../items/DryTallGrass.md), `minecraft:tall_dry_grass` | **1 Tall Dry Grass with Shears or Silk Touch I+**; otherwise nothing | One eligible adjacent **Short** Dry Grass; original tall plant remains. [tall_dry_grass loot] · [Tall dry growth] |
| <span id="firefly-bush"></span>[Firefly Bush](../items/FireflyBush.md), `minecraft:firefly_bush` | **1 Firefly Bush**, including by hand | One eligible adjacent Firefly Bush. [firefly_bush loot] · [Firefly growth] |
| <span id="dead-bush"></span>[Dead Bush](DeadBush.md#finding-and-collecting), `minecraft:dead_bush` | **1 Dead Bush with Shears**; otherwise **0–2 Sticks** | No direct Bone Meal growth. [dead_bush loot] · [Dry plant support] |

None of these complete loot tables has a Fortune multiplier. In particular, Silk Touch works for Bush and the two dry grasses, while **Dead Bush still needs Shears** to recover its plant item. The checked dry-grass loot has no seed drop. [bush loot] · [short_dry_grass loot] · [tall_dry_grass loot] · [dead_bush loot] · [firefly_bush loot] · [Mining callback]

## Support and water

**Bush and Firefly Bush** use the ordinary vegetation ground rule: **Farmland or `#minecraft:dirt`**. The current dirt tag contains Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. They do not need nearby water, a wet soil state, or a particular light level. [Plant ground and survival] · [dirt tag] · [Bush growth] · [Firefly growth]

**Short Dry Grass, Tall Dry Grass, and Dead Bush** use `#minecraft:dry_vegetation_may_place_on`, which includes:

- The same ten-block dirt group and Farmland
- **Sand, Red Sand, and Suspicious Sand** through the sand tag
- **Uncolored Terracotta and all sixteen dyed Terracotta blocks**; the tag does not include Glazed Terracotta

These are support categories, not a hydration or desert-biome requirement. [Dry plant support] · [dry_vegetation_may_place_on tag] · [sand tag] · [terracotta tag] · [dirt tag]

Placement checks valid support, and neighboring changes recheck survival. None of these plants has a waterlogged state. **Incoming water can replace them and invoke their empty-tool loot:** Firefly Bush returns its item, Dead Bush rolls Sticks, and Bush/both dry grasses return no plant. Losing support likewise does not supply the Shears or Silk Touch needed by their recovery tables. [Item placement check] · [Plant ground and survival] · [Water occupancy] · [Water replacement] · [Water drops] · [Support destruction] · [bush loot] · [short_dry_grass loot] · [tall_dry_grass loot] · [dead_bush loot] · [firefly_bush loot]

The [Dead Bush decoration section](DeadBush.md#placement-and-decoration) owns its Flower Pot route. Do not infer that all shrubs use that pot interaction.

## Bone Meal and multiplication

These registrations have **no automatic random-tick growth or spread**, and there is no age counter. The following changes happen through Bone Meal callbacks. An accepted Survival application consumes **one Bone Meal**. [Dry plant and Bush registry] · [Firefly registry] · [Bone Meal use]

For **Bush and Firefly Bush**, Bone Meal first needs an empty cell in one of the **four horizontal directions at the same height**, with valid support for that plant. The spread action shuffles those directions, chooses the first valid one, and places **one new matching bush**, leaving the original. Diagonal, higher, or lower cells are not candidates in this helper. If none of the four cells works, the plant is not a valid direct Bone Meal target. [Adjacent spread helper] · [Bush growth] · [Firefly growth]

For dry grass, there is a two-step loop:

1. Bone Meal on **Short Dry Grass** changes that same cell into **Tall Dry Grass**. This is a single-block replacement, so it does not need a second block of headroom
2. Bone Meal on **Tall Dry Grass** uses the same four-neighbor helper to place **one Short Dry Grass** nearby, leaving the tall plant intact. It needs air and valid dry-plant support at the chosen neighboring cell

Recover the new plant with Shears or Silk Touch if you want an inventory item. Harvesting Tall Dry Grass returns the tall item, not two short plants. The checked code provides no direct tall-to-short conversion of the original block. Dead Bush has no Bone Meal implementation. [Short dry growth] · [Tall dry growth] · [Adjacent spread helper] · [short_dry_grass loot] · [tall_dry_grass loot] · [Dry plant support]

## Firefly light, particles, and sound

A placed **Firefly Bush emits block light level 2 at all times**. Its light registration does not depend on night, water, growth, or a lit state. The other four plants have no registered light output. [Firefly registry] · [Dry plant and Bush registry]

The fireflies around it come from the **client particle path**. An animation callback can create a particle when local brightness is **13 or lower**, with a roughly **70% roll per eligible callback**. That brightness calculation includes sky darkening. There is no separate night or nearby-water condition in the particle branch, so suitable shade can qualify too. Client animation samples nearby blocks; this probability is not a fixed particles-per-second rate. [Firefly effects] · [Local brightness] · [Client animation dispatch] · [Client block animation] · [Firefly particle registration]

The source picks particle positions within a ten-block-wide square centered on the block's X/Z coordinates and from zero to five blocks above its Y coordinate. Individual particles fade, wander, and disappear if they enter a non-air block; their chosen lifetime is **200–300 client ticks**. This is visual behavior, separate from the bush's constant light level 2. [Firefly effects] · [Firefly particles]

The idle sound uses another condition: a **1-in-30 animation-callback roll**, the world's moon-visible time window, and a `MOTION_BLOCKING_NO_LEAVES` heightmap no higher than the bush. The checked moon rule requires a **natural dimension** and day time **12,600–23,400 modulo 24,000**. Thus sound eligibility is narrower than particle eligibility. [Firefly effects] · [Moon visibility rule] · [Client block animation]

Both dry-grass sizes can also play ambient rustling. Their sound helper checks that **the two blocks directly beneath the plant** are in the ambient dry-vegetation sound tag: ordinary Sand, Red Sand, or Terracotta. This sound condition is narrower than the planting-support tag and does not add a growth requirement. [Short dry growth] · [Tall dry growth] · [Dry vegetation sounds] · [triggers_ambient_desert_dry_vegetation_block_sounds tag]

## Recipes, compost, and fuel

No recipe in the checked bundled recipe tree produces or consumes these five items, including through their item-tag memberships. Bone Meal changes placed plants through block interactions rather than a crafting recipe.

| Plant | Ordinary compost level-increase chance | Standard Furnace fuel |
| --- | --- | --- |
| Bush | **30%** | Not in the checked fuel table |
| Short Dry Grass | **30%** | **100 burn ticks** |
| Tall Dry Grass | **30%** | **100 burn ticks** |
| Firefly Bush | **30%** | Not in the checked fuel table |
| Dead Bush | Not in the checked compost list | **100 burn ticks**; existing [item uses](../items/DeadBush.md#uses) remain canonical |

The first accepted item in an empty [Composter](Composter.md) raises its level automatically. A dry-grass item provides half the fuel duration of a 200-tick Furnace recipe; the [Furnace guide](Furnace.md#fuel-planning) owns batch planning. The server builds the checked fuel list and the active Furnace consumes it. [Compost values] · [Composter success] · [Compost and fire bootstrap] · [Fuel table] · [Fuel tag application] · [Server fuel initialization] · [Furnace fuel use]

All five plants are registered as flammable in the placed-block fire system. Fuel eligibility and world-fire behavior are separate lists. [Dry flammability] · [Bush flammability] · [Flammability assignment] · [Fire consumption] · [Compost and fire bootstrap]

## Checked acquisition examples

These are selected active sources, not a complete biome or loot survey. The Normal Overworld preset includes the listed biomes, and generation runs their placed/configured-feature chains. [Normal preset] · [Overworld preset execution] · [Overworld selection tables] · [Swamp selection] · [Biome feature dispatch] · [Placed feature dispatch] · [Configured feature dispatch]

| Plant | Verified route |
| --- | --- |
| Bush | **Plains** includes `patch_bush`, whose configured patch chooses Bush and checks empty space and survival. [plains biome] · [patch_bush placement] · [patch_bush feature] · [Patch execution] · [Plant feature survival] |
| Both dry grasses | **Desert and Badlands** have separate placements that both lead to `patch_dry_grass`. The state provider gives short and tall forms equal weight for each choice; individual placements can still fail. [desert biome] · [badlands biome] · [patch_dry_grass_desert placement] · [patch_dry_grass_badlands placement] · [patch_dry_grass feature] · [Weighted plant choice] · [Plant feature survival] |
| Firefly Bush | **Plains** includes a near-water placement. Its initial patch origin must have suitable ground and Water or Flowing Water in one of four horizontal cells **one block below** it. The subsequent patch scatters plants around that origin, so the filter does not mean every final bush directly borders water. **Swamp** also has a separate Firefly Bush patch placement without that water-neighbor predicate. [plains biome] · [swamp biome] · [patch_firefly_bush_near_water placement] · [patch_firefly_bush_swamp placement] · [patch_firefly_bush feature] · [Placement filter] · [Water-neighbor predicate] · [Patch execution] |
| Dead Bush | Its existing [finding and collecting section](DeadBush.md#finding-and-collecting) covers checked natural patches; its item page owns the additional chest and mob-use details |

A [Wandering Trader](../mobs/WanderingTrader.md) can sell **1 Tall Dry Grass for 1 Emerald** or **1 Firefly Bush for 3 Emeralds**. Each listing allows **12 uses**. Both belong to a group from which five offers are selected at random, so either may be absent from an individual trader. A traded tall plant can start the dry-grass multiplication loop. [Trader plant offers] · [Offer construction] · [Trader group selection] · [Random offer selection]

## Sources and verification

Source-reviewed on **2026-10-02** at `053cd852a8609f4002234ce0d445d3a345b551ae`. Checked five registrations and complete loot tables, actual item IDs and display names, support tags, Bone Meal paths, water/support destruction, client particle/audio wiring, compost/fuel/fire lists, recipe absence, and the selected biome/trader routes. The Dead Bush block/item pages remain the existing owners. No in-game placement, harvesting, growth, particle, lighting, sound, fuel, trading, or world-generation test was run. Data packs can change recipes, loot, tags, and features.

[Dry plant and Bush registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L727-L783
[Firefly registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L6880-L6891
[Short dry growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ShortDryGrassBlock.java#L16-L52
[Tall dry growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/TallDryGrassBlock.java#L16-L53
[Bush item registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L290-L294
[Dry grass item registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L322-L323
[Block-derived item IDs]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2743-L2783
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[bush loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/bush.json
[Bush growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/BushBlock.java#L15-L47
[short_dry_grass loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/short_dry_grass.json
[tall_dry_grass loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/tall_dry_grass.json
[firefly_bush loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/firefly_bush.json
[Firefly growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireflyBushBlock.java#L47-L61
[dead_bush loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/dead_bush.json
[Dry plant support]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/DryVegetationBlock.java#L15-L41
[Mining callback]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[Plant ground and survival]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/dirt.json
[dry_vegetation_may_place_on tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/dry_vegetation_may_place_on.json
[sand tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/sand.json
[terracotta tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/terracotta.json
[Item placement check]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L141
[Water occupancy]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Water replacement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[Water drops]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Support destruction]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[Bone Meal use]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[Adjacent spread helper]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/BonemealableBlock.java#L20-L37
[Firefly effects]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireflyBushBlock.java#L16-L45
[Local brightness]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/LevelReader.java#L169-L176
[Client animation dispatch]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L481-L490
[Client block animation]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L508-L514
[Firefly particle registration]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/client/particle/ParticleResources.java#L177
[Firefly particles]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/client/particle/FireflyParticle.java#L13-L85
[Moon visibility rule]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/Level.java#L365-L372
[Dry vegetation sounds]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/sounds/AmbientDesertBlockSoundsPlayer.java#L32-L58
[triggers_ambient_desert_dry_vegetation_block_sounds tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/triggers_ambient_desert_dry_vegetation_block_sounds.json
[Compost values]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L110-L114
[Composter success]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[Compost and fire bootstrap]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/Bootstrap.java#L45-L52
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L109
[Fuel tag application]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L125-L150
[Server fuel initialization]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[Furnace fuel use]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L165-L177
[Dry flammability]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireBlock.java#L415-L417
[Bush flammability]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireBlock.java#L504-L505
[Flammability assignment]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireBlock.java#L303-L306
[Fire consumption]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireBlock.java#L223-L248
[Normal preset]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Overworld preset execution]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L106-L109
[Overworld selection tables]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L73-L95
[Swamp selection]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L439-L463
[Biome feature dispatch]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Placed feature dispatch]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[Configured feature dispatch]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
[plains biome]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/biome/plains.json
[patch_bush placement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/placed_feature/patch_bush.json
[patch_bush feature]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/configured_feature/patch_bush.json
[Patch execution]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java#L15-L38
[Plant feature survival]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L43
[desert biome]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/biome/desert.json
[badlands biome]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/biome/badlands.json
[patch_dry_grass_desert placement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/placed_feature/patch_dry_grass_desert.json
[patch_dry_grass_badlands placement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/placed_feature/patch_dry_grass_badlands.json
[patch_dry_grass feature]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/configured_feature/patch_dry_grass.json
[Weighted plant choice]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/feature/stateproviders/WeightedStateProvider.java#L30-L36
[swamp biome]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[patch_firefly_bush_near_water placement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/placed_feature/patch_firefly_bush_near_water.json
[patch_firefly_bush_swamp placement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/placed_feature/patch_firefly_bush_swamp.json
[patch_firefly_bush feature]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/configured_feature/patch_firefly_bush.json
[Placement filter]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/placement/BlockPredicateFilter.java#L26-L29
[Water-neighbor predicate]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/blockpredicates/MatchingFluidsPredicate.java#L27-L29
[Trader plant offers]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L824-L829
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Trader group selection]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Random offer selection]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L234
