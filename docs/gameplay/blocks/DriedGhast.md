# Dried Ghast

**Dried Ghast** (`minecraft:dried_ghast`) is a small waterloggable block that progresses through hydration stages and attempts to hatch a **baby [Happy Ghast](../mobs/HappyGhast.md)**, also called a ghastling. The hatch path creates `minecraft:happy_ghast`; it does not create the hostile [Ghast](../mobs/Ghast.md), `minecraft:ghast`. [Block registration][ghast-block] · [Hatch callback][ghast-hatch] · [Distinct entity registrations][entity-ids]

## Obtaining

Craft **1 Dried Ghast** in a Crafting Table with **8 [Ghast Tears](../items/GhastTear.md)** around **1 [Soul Sand](../items/SoulSand.md)**: fill all eight outer cells with Ghast Tears and put Soul Sand in the center. The bundled shaped recipe uses Soul Sand, not Soul Soil. Its file resolves to recipe ID `minecraft:crafting/dried_ghast`; the result item ID is `minecraft:dried_ghast`. [Exact recipe][ghast-recipe] · [Recipe loading][recipe-load] · [Resource-to-ID conversion][recipe-id] · [Crafting result caller][crafting]

Two other source-verified routes are:

- **Adult Piglin bartering.** Offer a [Gold Ingot](../items/GoldIngot.md) to an eligible adult [Piglin](../mobs/Piglin.md#bartering). Its completed barter response evaluates the bundled bartering loot table, which includes **1 Dried Ghast** with weight **10**. This is a possible result, not a guaranteed return for each ingot. [Currency][barter-currency] · [Interaction eligibility][barter-interact] · [Core behavior][barter-core] · [Completion caller][barter-finish] · [Adult response][barter-adult] · [Loot evaluation][barter-call] · [Dried Ghast entry][barter-loot]
- **Nether Fossil placement in Soul Sand Valley.** The bundled structure set selects the Nether Fossil structure, whose biome tag names `minecraft:soul_sand_valley`. After a fossil piece is placed, it makes a **50% placement attempt** for one Dried Ghast at a chosen position along the bottom of the fossil bounds. That cell must also be air and inside the current placement bounds, so this is not a promise that half of all visible fossils contain a Dried Ghast. [Structure set][fossil-set] · [Structure data][fossil-data] · [Biome tag][fossil-biome] · [Fossil creation][fossil-create] · [Placement checks][fossil-place]

The fossil route is connected to the normal chunk structure-start and structure-piece placement chain. It requires structure generation, eligible terrain and successful placement; it does not establish that a fossil exists near any particular player. [Generation gate][structure-starts] · [Selection][structure-selection] · [Creation][structure-generation] · [Decoration][structure-decorate] · [Piece dispatch][piece-dispatch]

The registered item is an ordinary block item in the **Natural Blocks** category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) also provides a separate insertion route in Creative. This listing is independent of crafting, bartering and world generation. [Item registration][ghast-item] · [Block-item helper][item-helper] · [Category owner][natural-category] · [Category entry][ghast-category]

## Placing and adding water

A fresh block starts at **hydration 0**, facing horizontally opposite the placing player's direction. Its outline and normal collision are a centered **10/16 × 10/16-block footprint, 10/16 block tall**. It stores `facing`, `hydration` (**0–3**) and `waterlogged`. [Default state][ghast-state] · [Hydration property][ghast-hydration] · [Placement facing][ghast-water] · [Shape][ghast-water-tick] · [Shape dimensions][ghast-shape-size] · [Collision default][shapes]

Place it into a **Water source**, or use a **[Water Bucket](../items/WaterBucket.md)** on it, to set `waterlogged=true`. The placement and fill checks compare the fluid type with `Fluids.WATER`; merely putting Water beside a dry block is not the hydration condition. The waterlogged block exposes a source-Water fluid state and schedules Water updates on neighbor changes. Bucket placement still follows dimension restrictions: in an ultra-warm dimension, the bucket's evaporation branch runs before waterlogging. [Placement and filling][ghast-water] · [Water updates][ghast-water-tick] · [Bucket targeting][bucket-dispatch] · [Evaporation before fill][bucket-heat]

An **[Empty Bucket](../items/Bucket.md)** can recover its stored Water. The shared pickup handler clears `waterlogged` while retaining the hydration value; it does not instantly dry the block back to hydration 0. Remove any surrounding refill source if you intend to keep it dry. [Bucket dispatch][bucket-dispatch] · [Water pickup][water-pickup] · [Water fill handler][ghast-water]

## Hydration and drying

The block changes only when its scheduled block check executes. The current state at that check determines the result:

| Current state | Scheduled-check result |
| --- | --- |
| Waterlogged, hydration 0 | Hydration becomes **1** |
| Waterlogged, hydration 1 | Hydration becomes **2** |
| Waterlogged, hydration 2 | Hydration becomes **3** |
| Waterlogged, hydration 3 | Removes the block and attempts to spawn a **baby Happy Ghast** |
| Dry, hydration 1–3 | Hydration decreases by **1** |
| Dry, hydration 0 | No hydration change |

[State transitions and readiness test][ghast-cycle] · [Hatching][ghast-hatch] · [Server fetches the current block state][scheduled-dispatch]

Each eligible **random tick** schedules a block check **5,000 game ticks later**, but only if no check is already queued. Eligibility means the block is waterlogged or still has hydration above 0. Neither adding Water nor the hydration callback immediately queues the next hydration check; later random ticks supply that scheduling step. [Scheduling condition][ghast-schedule] · [Fill handler][ghast-water] · [Transition handler][ghast-cycle] · [Random-tick caller][random-ticks] · [Scheduled-tick caller][scheduled-ticks] · [State dispatch][state-dispatch]

From hydration 0, uninterrupted waterlogging needs **four scheduled checks**, including the final hatch check after reaching 3. At the default 20 ticks per second, each 5,000-tick delay represents **4 minutes 10 seconds** of ticking, and the four delays total **16 minutes 40 seconds**, **plus the waits for random ticks and any ticking delays**. This is source arithmetic, not a measured hatch time or an exact 20-minute timer. Disabling random ticks prevents new checks from being queued; an already scheduled check can still execute. [Four-stage cycle][ghast-cycle] · [Queueing][ghast-schedule] · [Random ticking][random-ticks] · [Default tick rate][tick-rate]

Drying uses the same scheduling mechanism, one hydration level per dry check. Rewetting before a queued check runs makes that check take the waterlogged branch using the remaining hydration. A dry hydration-0 block no longer needs additional checks under this condition. [Current-state dispatch][scheduled-dispatch] · [Dry and wet branches][ghast-cycle] · [Schedule eligibility][ghast-schedule]

## Hatching and collecting the block

The hatch callback removes the Dried Ghast first, creates a Happy Ghast with the breeding spawn reason, marks it as a baby, positions it at the block's bottom center facing the block's stored direction, and attempts to add it to the world. The removed waterlogged block leaves its **Water** behind through the normal fluid-preserving removal path. No Dried Ghast item is awarded by this hatch callback. [Hatch sequence][ghast-hatch] · [Water fluid state][ghast-water] · [Removal preserves fluid][fluid-removal]

The baby starts with age **−24,000**. The ordinary server age path moves that value toward zero while the living mob ticks; that is a separate growth phase from the block's hydration. See [Happy Ghast](../mobs/HappyGhast.md) for the resulting mob and [Ghast](../mobs/Ghast.md) for the separate hostile entity. [Baby age and aging][baby-age] · [Entity identities][entity-ids]

To recover the block before it hatches, ordinary mining by hand or with a tool yields **1 Dried Ghast**. The block is registered for instant breaking, with no correct-tool requirement; Silk Touch is unnecessary and Fortune does not increase this single drop. Its loot has an explosion-survival condition and requires block drops to be enabled. The loot copies no hydration or facing state into the item, so normal collection and replacement restarts hydration at **0**. [Block properties][ghast-block] · [Default tool requirement][default-properties] · [Player gate][tool-gate] · [Mining caller][mining] · [Loot][ghast-loot] · [Drop rule][block-drops] · [Default placement][default-placement] · [Initial hydration][ghast-state]

Related: [Dried Ghast item](../items/DriedGhast.md) · [Happy Ghast](../mobs/HappyGhast.md) · [Ghast Tear](../items/GhastTear.md) · [Water and Lava](WaterAndLava.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked block/item registration, category, recipe loading and exact ingredients, adult barter caller and loot, the active Nether Fossil placement chain, waterlogging and bucket handling, hydration/drying schedules, current-state dispatch, hatch identity and baby age, and harvesting. No in-game crafting, bartering, world-generation, hydration, bucket, hatch, growth or harvesting test was run. Data packs, ticking conditions and later source changes can alter results.

[ghast-block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4825-L4829
[ghast-hatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L115-L127
[entity-ids]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/EntityType.java#L661-L680
[ghast-recipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/dried_ghast.json#L1-L18
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L83
[recipe-id]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/resources/FileToIdConverter.java#L23-L37
[crafting]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L55-L68
[barter-currency]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L77-L83
[barter-interact]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L535-L550
[barter-core]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L134-L149
[barter-finish]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/piglin/StopHoldingItemIfNoLongerAdmiring.java#L8-L20
[barter-adult]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L399
[barter-call]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L445
[barter-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L94-L105
[fossil-set]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/structure_set/nether_fossils.json#L1-L14
[fossil-data]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/structure/nether_fossil.json#L1-L16
[fossil-biome]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/nether_fossil.json#L1-L5
[fossil-create]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFossilStructure.java#L30-L59
[fossil-place]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFossilPieces.java#L88-L104
[structure-starts]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[structure-selection]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L491
[structure-generation]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L554-L574
[structure-decorate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L345
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L101
[ghast-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L881-L881
[item-helper]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2750-L2781
[natural-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L751-L759
[ghast-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L953-L961
[ghast-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L38-L59
[ghast-hydration]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java#L128-L128
[ghast-water]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L169-L194
[ghast-water-tick]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L61-L81
[ghast-shape-size]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L158-L182
[shapes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L331
[bucket-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/BucketItem.java#L55-L83
[bucket-heat]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L130
[water-pickup]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L38-L49
[ghast-cycle]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L88-L113
[scheduled-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[ghast-schedule]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java#L161-L167
[random-ticks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L503
[scheduled-ticks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L355-L362
[state-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L767-L776
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/TickRateManager.java#L7-L21
[fluid-removal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/Level.java#L256-L260
[baby-age]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L164
[default-properties]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1001
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[ghast-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/dried_ghast.json#L1-L21
[block-drops]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[default-placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
