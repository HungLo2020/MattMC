# Prismarine construction

**Prismarine, Prismarine Bricks and Dark Prismarine** are building materials that can be mined from Ocean Monuments or made from [Prismarine Shards](../items/PrismarineShard.md). Each finish has stairs and slabs; ordinary Prismarine also has a wall. Keep full blocks when preparing a [Conduit frame](Conduit.md#build-a-valid-frame), because its check excludes every stair, slab and wall. [Registrations][blocks] [Wall registration][wall-registration] · [Frame materials][conduit]

## Registered forms

This family has **ten registered block IDs and ten matching block-item forms**. A slab's top/bottom/double setting, stair corner, wall connection or waterlogged setting is a placed state, not another registered block or a separate inventory item. [Blocks][blocks] [Wall registration][wall-registration] · [Items][items] [Slab items][slab-items] [Wall item][wall-item] · [Shape states][slabs] [Stairs][stairs] [Wall placement][wall-placement]

### Prismarine forms

| Form | Exact block ID | Item guide |
| --- | --- | --- |
| Prismarine | `minecraft:prismarine` | [Prismarine](../items/Prismarine.md) |
| Prismarine Stairs | `minecraft:prismarine_stairs` | [Stairs](../items/PrismarineStairs.md) |
| Prismarine Slab | `minecraft:prismarine_slab` | [Slab](../items/PrismarineSlab.md) |
| Prismarine Wall | `minecraft:prismarine_wall` | [Wall](../items/PrismarineWall.md) |

### Prismarine Brick forms

| Form | Exact block ID | Item guide |
| --- | --- | --- |
| Prismarine Bricks | `minecraft:prismarine_bricks` | [Prismarine Bricks](../items/PrismarineBricks.md) |
| Prismarine Brick Stairs | `minecraft:prismarine_brick_stairs` | [Stairs](../items/PrismarineBrickStairs.md) |
| Prismarine Brick Slab | `minecraft:prismarine_brick_slab` | [Slab](../items/PrismarineBrickSlab.md) |

### Dark Prismarine forms

| Form | Exact block ID | Item guide |
| --- | --- | --- |
| Dark Prismarine | `minecraft:dark_prismarine` | [Dark Prismarine](../items/DarkPrismarine.md) |
| Dark Prismarine Stairs | `minecraft:dark_prismarine_stairs` | [Stairs](../items/DarkPrismarineStairs.md) |
| Dark Prismarine Slab | `minecraft:dark_prismarine_slab` | [Slab](../items/DarkPrismarineSlab.md) |

There is no registered Prismarine Brick Wall or Dark Prismarine Wall in the checked family. [Sea Lantern](LuminousBlocks.md#sea-lantern) is a separate light-emitting block with different recovery rules; it is not one of these ten IDs.

## Obtaining and mining

### Ocean Monument route

Ocean Monuments provide all **three full-block finishes**. Their active building includes a core room whose construction places Prismarine, Prismarine Bricks and Dark Prismarine. The shape recipes below then turn collected full blocks into the desired stairs, slabs or wall. This verifies a natural source for the full blocks without promising a fixed harvest quantity or a naturally placed supply of every shaped variant. [Material definitions][monument-blocks] · [Included core room][monument-building] · [Placed full blocks][monument-core]

In the bundled normal world, eligible Monument starts use **Deep Ocean, Deep Cold Ocean, Deep Lukewarm Ocean and Deep Frozen Ocean**. The structure set selects `minecraft:monument`; generation also checks surrounding biomes against the ocean/river tag and requires structures to be enabled. An eligible biome is not a guarantee of a nearby Monument. See [Ocean exploration](../biomes/Oceans.md#structures-and-exploration) for the broader route. [Structure set][monument-set] · [Definition][monument-data] · [Biome tags][monument-tag] [Deep ocean][deep-ocean] [Surrounding][surrounding] · [Active selection][structure-sets] [Monument][monument] · [World option][structure-setting]

Expect [Guardians](../mobs/Guardian.md), whose candidate spawn list replaces the monster list inside the Monument's full bounding box, and [Elder Guardians](../mobs/ElderGuardian.md) placed by its rooms. Guardian spawning still has water, difficulty and obstruction checks. Elder Guardians can apply **Mining Fatigue III** nearby; submerged and off-ground mining also have their own penalties. Plan a route back to air and do not use the dry-land hardness value as an underwater mining-time estimate. [Spawn override][monument-data] [Spawn override][spawn-override] · [Guardian checks][spawn-rules] · [Elder room inclusion][elder-rooms] [Elder wing][elder-wing] [Elder penthouse][elder-penthouse] [Elder placement][elder-placement] · [Fatigue][elder-fatigue] · [Mining modifiers][mining-effects]

For ingredient-based construction, both Guardian and Elder Guardian death tables provide **0–2 Shards before Looting**. They can separately select Crystals, but Crystals are not an ingredient in these three full-block finishes. Use the [Shard acquisition guide](../items/PrismarineShard.md#obtaining) and [Crystal guide](../items/PrismarineCrystals.md#obtaining) for those distinct drops. [Guardian loot][guardian-loot] · [Elder Guardian loot][elder-loot]

### Recovering placed blocks

Use an **unbroken pickaxe, including Wood**, to recover every family member. All ten require a correct tool; the nine base/stair/slab IDs appear directly in the pickaxe tag, while Prismarine Wall enters through the nested walls tag. None has a higher material-tier gate. Breaking by hand does not collect the block. [Required tools][blocks] [Wall registration][wall-registration] [Stair copy][stair-copy] [Property copy][property-copy] · [Pickaxe and wall tags][mineable-pickaxe] [Walls][walls] · [Wood exclusions and tier tags][incorrect_for_wooden_tool] [Needs stone tool][needs_stone_tool] [Needs iron tool][needs_iron_tool] [Needs diamond tool][needs_diamond_tool] · [Tool rules][tool] [Pickaxe tool][pickaxe-tool] [Tool rules][tool-rules] · [Player, durability and harvest checks][player-tool] [Broken tool][broken-tool] [Harvest][harvest]

| Mined form | Ordinary correct-tool result |
| --- | --- |
| Any full block | 1 matching full-block item |
| Any stairs or Prismarine Wall | 1 matching shape item |
| A single slab | 1 matching slab |
| A double slab | 2 matching slabs |

Silk Touch is unnecessary, and Fortune does not multiply these building-block drops. Mining does not turn a block back into Shards, and a double slab does not become its full-block crafting ingredient. Explosion survival/decay is handled separately. [Full-block loot][loot-prismarine] [Loot prismarine bricks][loot-prismarine_bricks] [Loot dark prismarine][loot-dark_prismarine] · [Stairs and wall][loot-prismarine_stairs] [Loot prismarine brick stairs][loot-prismarine_brick_stairs] [Loot dark prismarine stairs][loot-dark_prismarine_stairs] [Loot prismarine wall][loot-prismarine_wall] · [Slabs][loot-prismarine_slab] [Loot prismarine brick slab][loot-prismarine_brick_slab] [Loot dark prismarine slab][loot-dark_prismarine_slab]

## Crafting full blocks

| Ingredients | Arrangement | Result |
| --- | --- | --- |
| 4 Prismarine Shards | 2 × 2 square | 1 Prismarine |
| 9 Prismarine Shards | Shapeless; uses all nine crafting slots | 1 Prismarine Bricks |
| 8 Prismarine Shards + 1 Black Dye | Dye in the center, Shards around it | 1 Dark Prismarine |

[Prismarine recipe][craft-prismarine] · [Brick recipe][craft-prismarine_bricks] · [Dark recipe][craft-dark_prismarine]

Only the ordinary Prismarine recipe fits the inventory's 2 × 2 grid. The other two require a [Crafting Table](CraftingTable.md). **Black Dye** is the dark recipe's exact item: an Ink Sac is not a direct substitute. Make the chosen finish from Shards or collect it already placed; the checked recipes do not dye ordinary Prismarine into Dark Prismarine, cut ordinary Prismarine into Bricks, or unpack any full block into Shards. These limits describe the bundled recipe set; data packs can change recipes. [Dark ingredients][craft-dark_prismarine] · [Recipe loading][recipes]

## Crafting stairs, slabs and walls

Use the [shared shape patterns](Stone.md#crafting-yields): six full blocks in a stair pattern, three in one row for slabs, or two rows of three for a wall. Each recipe takes the exact full-block finish shown; the finishes cannot be mixed.

| Full-block input | Stairs | Slabs | Walls |
| --- | --- | --- | --- |
| Prismarine | [6 → 4][craft-prismarine_stairs] | [3 → 6][craft-prismarine_slab] | [6 → 6][craft-prismarine_wall] |
| Prismarine Bricks | [6 → 4][craft-prismarine_brick_stairs] | [3 → 6][craft-prismarine_brick_slab] | None |
| Dark Prismarine | [6 → 4][craft-dark_prismarine_stairs] | [3 → 6][craft-dark_prismarine_slab] | None |

## Stonecutting

The [Stonecutter](Stonecutter.md) consumes **one full block per operation**. These are all seven bundled family conversions. [Input consumption][stonecutter]

| One input | Available results per operation |
| --- | --- |
| Prismarine | [1 Prismarine Stairs][cut-prismarine_stairs]; [2 Prismarine Slabs][cut-prismarine_slab]; [1 Prismarine Wall][cut-prismarine_wall] |
| Prismarine Bricks | [1 Prismarine Brick Stairs][cut-prismarine_brick_stairs]; [2 Prismarine Brick Slabs][cut-prismarine_brick_slab] |
| Dark Prismarine | [1 Dark Prismarine Stairs][cut-dark_prismarine_stairs]; [2 Dark Prismarine Slabs][cut-dark_prismarine_slab] |

Choose the desired finish before cutting. In particular, **brick shapes require Prismarine Bricks**, even though their recipe filenames say “from prismarine.” Stairs, slabs and walls are not inputs for a reverse conversion in this checked set. [Brick stair ingredient][cut-prismarine_brick_stairs] · [Brick slab ingredient][cut-prismarine_brick_slab]

For example, **4 full blocks make 4 matching stairs** with the Stonecutter; crafting those four stairs uses six blocks. Slab and wall material yields are the same by either method, while stonecutting permits smaller batches. This is a source-derived planning example, not a tested crafting session.

## Placement, water and support

The full blocks have no facing or waterlogged state. Place them for solid surfaces, reserve stairs for steps/corners, use slabs for half-height surfaces and use the ordinary Prismarine Wall for a narrow barrier. [Registrations][blocks] [Wall registration][wall-registration]

- **Slabs:** the top face or lower half of a side places a bottom slab; the underside or upper half places a top slab. A second slab of the same item can fill the empty half to form a double slab. Mixing finishes in one block is not supported. Combining slabs clears waterlogging, and double slabs cannot be bucket-filled. [Placement and fluid rules][slabs]
- **Stairs:** player direction sets the facing; click face/height chooses upright or upside-down placement. Suitable neighboring stairs form inner/outer corners, including other stair materials when the half/facing rules fit. [Placement and corner checks][stairs]
- **Walls:** connections use other wall-tagged blocks, eligible sturdy faces, Iron Bars-class blocks and suitably aligned Fence Gates. The block above can change side height and the center post. Collision reaches **1.5 blocks high**, even where the visible side is lower. [Connections and collision][wall-shape] · [Neighbor updates][wall-placement] [Wall post][wall-post]

Single slabs, stairs and walls can hold water. Placing them into still Water sets waterlogging; their placement checks name the still Water fluid type, rather than accepting every water-tagged flowing state. A Water Bucket can fill an eligible dry shape, and an empty bucket can remove its stored water. None of these blocks needs permanent support beneath it or falls when that support is removed. [Placement fluid checks][slabs] [Stairs][stairs] [Wall placement][wall-placement] · [Bucket handling][water-bucket] · [Base survival][support]

All ten have **hardness 1.5**, **blast resistance 6**, **light emission 0**, and the **bass-drum note-block instrument**. The stairs and wall inherit the full block's properties. Hardness is not a measured break time, and the palette's blue/green color does not make it a light source. Use [Sea Lanterns](LuminousBlocks.md#sea-lantern) for the separate lighting option. [Properties][blocks] [Wall registration][wall-registration] [Stair copy][stair-copy] [Properties][properties] [Property copy][property-copy]

## Pistons

These blocks have normal piston behavior: they can be pushed and can be pulled by a Sticky Piston, subject to the ordinary movement limits, world boundary and obstruction checks. A double slab is one moved block. The ordinary movement-completion path updates neighboring shapes and clears a moved block's waterlogged state; check/refill the water after relocating a wet stair, slab or wall. This is source-reviewed behavior, not a tested underwater piston design. [Default/copy properties][properties] [Property copy][property-copy] · [Piston checks][piston] · [Push limit][piston-limit] · [Completion and water][piston-water]

## Conduit frames and other uses

**Only the three full blocks count toward a Conduit frame**, alongside Sea Lanterns. Stairs, walls and even double slabs fail its exact block-ID check. The [Conduit guide](Conduit.md#build-a-valid-frame) owns the water volume, frame layout, activation thresholds, effect range and attack rules. [Accepted materials][conduit] · [Frame check][conduit-frame]

Ordinary Prismarine also supplies the center block when [duplicating a Tide Armor Trim Smithing Template](../items/SmithingTemplateTideArmorTrim.md): **1 existing template + 7 Diamonds + 1 Prismarine → 2 templates**, with the template at top center, Prismarine at center and Diamonds in the other slots. Neither Prismarine Bricks nor Dark Prismarine is the named ingredient. See [Smithing](../smithing/Smithing.md) for applying trims. [Duplication recipe][tide-recipe]

## Sources and verification

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. Checked all ten block/item registrations and loot tables, nested mining tags and correct-tool dispatch, ten family crafting recipes, seven stonecutting recipes, the two additional literal-material recipes, active Monument generation and Guardian ingredient loot, shape/fluid/support handlers, piston checks and the exact Conduit material test. No in-game mining, crafting, placement, structure-generation, loot or piston test was run. The guide describes bundled data; server data packs and world settings can change available routes.

Related: [Prismarine Shard](../items/PrismarineShard.md) · [Prismarine Crystals](../items/PrismarineCrystals.md) · [Sea Lantern](LuminousBlocks.md#sea-lantern) · [Conduit](Conduit.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L3149-L3178
[wall-registration]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L5272-L5272
[conduit]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42
[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L750-L755
[slab-items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L410-L412
[wall-item]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L608-L608
[slabs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
[wall-placement]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WallBlock.java#L111-L155
[monument-blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1402-L1409
[monument-building]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L167-L173
[monument-core]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L729-L755
[monument-set]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/structure_set/ocean_monuments.json#L1-L15
[monument-data]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/structure/monument.json#L1-L26
[monument-tag]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_monument.json#L1-L5
[deep-ocean]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/worldgen/biome/is_deep_ocean.json#L1-L8
[surrounding]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/worldgen/biome/required_ocean_monument_surrounding.json#L1-L6
[structure-sets]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L63
[monument]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentStructure.java#L29-L55
[structure-setting]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[spawn-override]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L294-L305
[elder-rooms]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L200-L206
[elder-wing]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1847-L1853
[elder-penthouse]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1393-L1399
[elder-placement]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1534-L1543
[elder-fatigue]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/monster/ElderGuardian.java#L63-L73
[mining-effects]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L650
[guardian-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/guardian.json#L1-L176
[elder-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json#L1-L205
[stair-copy]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L7256-L7258
[property-copy]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1088
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L1-L395
[walls]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/walls.json#L1-L30
[incorrect_for_wooden_tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json#L1-L82
[needs_iron_tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json#L1-L16
[needs_diamond_tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json#L1-L9
[tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[pickaxe-tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Item.java#L435-L437
[tool-rules]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/component/Tool.java#L49-L56
[player-tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[loot-prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine.json#L1-L21
[loot-prismarine_bricks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine_bricks.json#L1-L21
[loot-dark_prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/dark_prismarine.json#L1-L21
[loot-prismarine_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine_stairs.json#L1-L21
[loot-prismarine_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine_brick_stairs.json#L1-L21
[loot-dark_prismarine_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/dark_prismarine_stairs.json#L1-L21
[loot-prismarine_wall]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine_wall.json#L1-L21
[loot-prismarine_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine_slab.json#L1-L35
[loot-prismarine_brick_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine_brick_slab.json#L1-L35
[loot-dark_prismarine_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/dark_prismarine_slab.json#L1-L35
[craft-prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine.json#L1-L15
[craft-prismarine_bricks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_bricks.json#L1-L19
[craft-dark_prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/dark_prismarine.json#L1-L17
[recipes]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[craft-prismarine_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_stairs.json#L1-L16
[craft-prismarine_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_slab.json#L1-L14
[craft-prismarine_wall]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_wall.json#L1-L15
[craft-prismarine_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_brick_stairs.json#L1-L16
[craft-prismarine_brick_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_brick_slab.json#L1-L14
[craft-dark_prismarine_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/dark_prismarine_stairs.json#L1-L16
[craft-dark_prismarine_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/dark_prismarine_slab.json#L1-L14
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L61-L77
[cut-prismarine_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_stairs_from_prismarine_stonecutting.json#L1-L8
[cut-prismarine_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_slab_from_prismarine_stonecutting.json#L1-L8
[cut-prismarine_wall]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_wall_from_prismarine_stonecutting.json#L1-L8
[cut-prismarine_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_brick_stairs_from_prismarine_stonecutting.json#L1-L8
[cut-prismarine_brick_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_brick_slab_from_prismarine_stonecutting.json#L1-L8
[cut-dark_prismarine_stairs]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/dark_prismarine_stairs_from_dark_prismarine_stonecutting.json#L1-L8
[cut-dark_prismarine_slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/stonecutting/dark_prismarine_slab_from_dark_prismarine_stonecutting.json#L1-L8
[wall-shape]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WallBlock.java#L54-L108
[wall-post]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WallBlock.java#L195-L231
[water-bucket]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L20-L49
[support]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1021
[piston]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L195-L259
[piston-limit]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L72-L103
[piston-water]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java#L312-L341
[conduit-frame]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L128-L162
[tide-recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/tide_armor_trim_smithing_template.json#L1-L18
