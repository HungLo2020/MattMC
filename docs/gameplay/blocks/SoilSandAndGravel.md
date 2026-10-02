# Soil, Sand, and Gravel

**Soil provides stable ground; Sand, Red Sand, and Gravel fall when their support becomes unsuitable.** Grass Block and Mycelium can spread to ordinary Dirt, while Coarse Dirt, Rooted Dirt, and Podzol keep their distinct surfaces until a relevant conversion changes them. Suspicious Sand and Gravel need a Brush rather than ordinary mining to recover their archaeology contents. This guide covers those twelve registered terrain blocks. [Soil registrations][soil-reg] · [Rooted Dirt][rooted-reg] · [Mycelium][mycelium-reg] · [Path][path-reg] · [Sand and Gravel registrations][sand-reg]

## Mining and moving terrain

All twelve blocks are in the **shovel-mineable tag**, and none of their checked registrations requires a correct tool for ordinary drops. An unbroken shovel speeds collection; hand mining still reaches the ordinary loot rules. Silk Touch and Fortune can change the selected loot without imposing a higher material tier. The table describes normal player mining with block drops enabled. [Mining tag][shovel-tag] · [Shovel speed rule][tool] · [Player harvest gate][player] · [Registrations][soil-reg] · [Rooted Dirt][rooted-reg] · [Mycelium][mycelium-reg] · [Dirt Path][path-reg] · [Falling terrain][sand-reg]

| Block | Hardness | Ordinary result | With Silk Touch |
| --- | ---: | --- | --- |
| [Dirt](../items/Dirt.md) | 0.5 | 1 Dirt | Same |
| [Grass Block](../items/GrassBlock.md) | 0.6 | 1 Dirt | 1 Grass Block |
| [Coarse Dirt](../items/CoarseDirt.md) | 0.5 | 1 Coarse Dirt | Same |
| [Rooted Dirt](../items/RootedDirt.md) | 0.5 | 1 Rooted Dirt | Same |
| [Podzol](../items/Podzol.md) | 0.5 | 1 Dirt | 1 Podzol |
| [Mycelium](../items/Mycelium.md) | 0.6 | 1 Dirt | 1 Mycelium |
| [Dirt Path](../items/DirtPath.md) | 0.65 | 1 Dirt | Still Dirt |
| [Sand](../items/Sand.md) | 0.5 | 1 Sand | Same |
| [Red Sand](../items/RedSand.md) | 0.5 | 1 Red Sand | Same |
| [Gravel](../items/Gravel.md) | 0.6 | 1 Gravel or 1 Flint | 1 Gravel |
| [Suspicious Sand](../items/SuspiciousSand.md) | 0.25 | No block-loot item | Still none |
| [Suspicious Gravel](../items/SuspiciousGravel.md) | 0.25 | No block-loot item | Still none |

[Dirt loot][loot-dirt] · [Grass][loot-grass_block] · [Coarse Dirt][loot-coarse_dirt] · [Rooted Dirt][loot-rooted_dirt] · [Podzol][loot-podzol] · [Mycelium][loot-mycelium] · [Path][loot-dirt_path] · [Sand][loot-sand] · [Red Sand][loot-red_sand] · [Gravel][loot-gravel] · [Suspicious Sand][loot-suspicious_sand] · [Suspicious Gravel][loot-suspicious_gravel]

### Gravel and Flint

Without Silk Touch, Gravel rolls for **one Flint instead of one Gravel**. Fortune changes the chance, not the item count:

| Fortune level | Flint chance |
| --- | ---: |
| None | 10% |
| I | About 14.29% |
| II | 25% |
| III | 100% |

Silk Touch selects Gravel before this Fortune branch. Choose it when moving a Gravel supply you want to keep. These are mining-loot probabilities; the falling-entity item-drop route below is separate. [Gravel loot branches][loot-gravel] · [Fortune-indexed chance][fortune]

## Stable soils and spreading surfaces

Dirt, Grass Block, Coarse Dirt, Rooted Dirt, Podzol, Mycelium, and Dirt Path do not use the falling-block behavior. Removing the block beneath them does not make them fall. Their upper-surface rules can still change Grass, Mycelium, or a Path. [Registrations][soil-reg] · [Rooted Dirt][rooted-reg] · [Mycelium][mycelium-reg] · [Path][path-reg] · [Default survival][base-survival]

### Grass Block and Mycelium

Both use the same random-tick spreading system. A surviving source with **raw brightness at least 9 above it** makes four random spread attempts. Each candidate is within one block horizontally and from **three blocks below to one block above** the source. Only **ordinary Dirt** can become the source's type; Grass does not directly replace Mycelium, and neither spreads directly onto Coarse Dirt, Rooted Dirt, or Podzol. There is no fixed spread time. [Shared spread callback][spread] · [Grass registration][soil-reg] · [Mycelium inheritance][mycelium-class]

The source-light requirement is separate from survival. The survival test accepts a single Snow layer above, otherwise rejects a full-strength fluid above, and otherwise requires the cover's computed light-blocking/occlusion value to be below 15. A failed survival check turns the surface into Dirt on a random tick. **Darkness alone is not the tested death condition.** A spread destination must pass that cover test and must have no water-tag fluid above it; the callback does not make a separate brightness-at-least-9 check at the destination. [Survival and destination tests][spread] · [Occlusion calculation][light]

Bone Meal used on Grass Block requires air directly above it and attempts to place nearby vegetation, using grass placement and the current biome's flower features. This grows plants rather than directly extending the Grass Block surface. Bone Meal consumption is handled by the active item callback; the placement attempts do not guarantee a fixed plant yield. [Grass Bone Meal callback][grass-bonemeal] · [Item dispatch][bone-use] · [Grass placement][grass-placement] · [Placed grass state][grass-feature]

### Podzol and mushroom support

Podzol uses the Snowy Dirt block class, not the spreading class, and its registration does not enable random ticks. It does not use the Grass/Mycelium spread or cover-to-Dirt callback. Its snowy appearance is updated from the block above. [Podzol registration][soil-reg] · [Snowy Dirt class][snowy] · [Default random-tick callback][base-tick]

**Podzol and Mycelium support ordinary Brown and Red Mushrooms without the usual below-13 light requirement.** They are explicit members of the mushroom-growing support tag. This is a support rule for the small mushroom blocks, not a guarantee of giant-mushroom growth in a confined space. [Mushroom registrations][mushroom-reg] · [Support predicate][mushroom-support] · [Support tag][mushroom-tag]

## Crafting and converting soils

### Coarse Dirt recipe

Arrange **2 Dirt and 2 Gravel in an alternating 2 × 2 square** to make **4 Coarse Dirt**. The two Dirt slots are diagonal from each other. This fits the inventory crafting grid and uses the exact ordinary Dirt and Gravel items. Coarse Dirt cannot be directly overtaken by the Grass/Mycelium spread callback. [Recipe][coarse-recipe] · [Spread target][spread]

### Hoe, shovel, and water-bottle actions

| Action | Accepted soil | Result and conditions |
| --- | --- | --- |
| Use a hoe | Dirt, Grass Block, Dirt Path | Farmland; air above and a top/side click |
| Use a hoe | Coarse Dirt | Dirt first; air above and a top/side click |
| Use a hoe | Rooted Dirt | Dirt plus 1 Hanging Roots; any clicked face, no air-above requirement |
| Use a shovel | Dirt, Grass Block, Coarse Dirt, Rooted Dirt, Podzol, Mycelium | Dirt Path; air above and a top/side click |
| Use a water bottle | Dirt, Coarse Dirt, Rooted Dirt | Mud; top/side click, no air-above requirement |

Successful hoe/shovel conversions request one durability; ordinary broken tools cannot run their item-use handlers. The hoe's Hanging Roots item drop follows the block-drops rule. See [Axes and Hoes](../mechanics/AxesAndHoes.md#tilling-with-a-hoe), [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md#making-paths-with-a-shovel), and [Farmland](Farmland.md) for the canonical tool controls and crop-soil care. [Hoe map][hoe] · [Shovel map][shovel] · [Broken-item guard][use-guard] · [Root drop dispatch][roots-drop]

The water bottle is a [Potion item](../items/Potion.md) containing Water. In Survival, the conversion consumes one filled bottle and returns an empty [Glass Bottle](../items/GlassBottle.md). The ingredient condition checks the Water potion and the three-block conversion tag. For the next material step, see [Mud-to-Clay conversion](ClayAndBricks.md#turning-mud-into-clay). [Potion registration][potion-reg] · [Water-potion callback][potion] · [Mud conversion tag][mud-tag] · [Bottle exchange][bottle-result]

### Rooted Dirt and Hanging Roots

Use Bone Meal on Rooted Dirt with **air immediately below it** to place Hanging Roots beneath it. The Rooted Dirt stays in place. To collect those placed Hanging Roots as an item, their loot table requires **Shears**. This differs from hoeing the Rooted Dirt, which replaces it with Dirt and directly drops a Hanging Roots item. [Rooted Dirt callback][rooted] · [Bone Meal dispatch][bone-use] · [Hanging Roots loot][hanging-loot] · [Hoe conversion][hoe]

### Dirt Path placement and cover

A Dirt Path is **15/16 of a block high**. Make the placed path with a shovel; mining it does not return a Dirt Path item, even with Silk Touch. If a later upper-neighbor update finds a block considered solid above, the Path schedules conversion to Dirt after one tick. A Fence Gate is an explicit exception to that cover rule. Placing the registered Path item where the Path cannot survive produces Dirt instead. [Path shape, placement, and survival][path] · [Path loot][loot-dirt_path]

## Sand, Red Sand, and Gravel falling

Placement and neighbor updates schedule a falling check after **two ticks**. The block falls if the block below is air, in the fire tag, liquid, or replaceable. The source block becomes a falling entity; water is therefore not ordinary support for these materials. [Sand inheritance][sand-class] · [Gravel inheritance][colored-falling] · [Falling check][falling] · [Entity creation][fall-spawn]

At an ordinary landing, the entity can place its block only when the destination is replaceable, the falling block can survive there, and the space below is no longer considered free. If placement is unsuitable, the checked path can drop the block's item when entity drops are enabled. **For Gravel, that item is Gravel directly; this path does not roll the mined block's Flint loot.** Keep an existing support beneath loose terrain while excavating if you need it to remain in place. [Landing and item-drop branches][fall-land]

### Selected material recipes

Four Sand in a **2 × 2 square** make **1 Sandstone**. Four Red Sand in that layout make **1 Red Sandstone**. Each recipe names its own sand item; their colors cannot be mixed for either output. [Sandstone recipe][sandstone] · [Red Sandstone recipe][red-sandstone]

Both Sand and Red Sand are accepted by the [canonical Glass smelting recipe](../items/Glass.md#obtaining). Ordinary Sand and Gravel also supply [Concrete Powder recipes](Concrete.md#crafting-and-color-choices); those recipes name ordinary Sand and do not accept Red Sand. The linked guides own the complete processing recipes and conditions. [Glass ingredient tag][glass-tag] · [Glass recipe][glass-recipe] · [Checked powder example][white-powder]

## Suspicious Sand and Suspicious Gravel

Keep suspicious terrain **supported and in place**, then use the [Brush archaeology instructions](../items/Brush.md#archaeology-basics). Their normal block-loot tables have no item pools, so ordinary mining and Silk Touch do not recover the suspicious block or its hidden contents. [Suspicious Sand loot][loot-suspicious_sand] · [Suspicious Gravel loot][loot-suspicious_gravel]

They also schedule a two-tick support check, but falling sets a special cancellation flag. On ordinary landing, that flag discards the entity and calls its break effect instead of placing the block or dropping a recoverable block item. This is why the ordinary falling-Sand collection route is unsuitable for archaeology. [Brushable support and fall][brush-fall] · [Cancellation flag][cancel-fall] · [Landing cancellation branch][fall-land]

Successful brushing emits any stored loot and replaces Suspicious Sand with Sand, or Suspicious Gravel with Gravel. The stored loot depends on the particular block's configured data; this guide does not promise an artifact from every suspicious block or inventory structure loot tables. [Brush call][brush-use] · [Completion and stored item][brush-finish] · [Replacement registrations][sand-reg]

Related: [Blocks](Blocks.md) · [Mining](../mechanics/Mining.md) · [Farmland](Farmland.md) · [Clay and Bricks](ClayAndBricks.md) · [Concrete](Concrete.md) · [Glass](GlassAndPanes.md) · [Brush](../items/Brush.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game mining, spread-timing, falling, conversion, or archaeology test was run. The guide covers checked placed-block behavior and selected recipes; it does not inventory all world-generation locations, trades, or structure loot. Game rules and data packs can change drops and recipes.

[soil-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L127-L134
[rooted-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L6649-L6651
[mycelium-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2440-L2442
[path-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L4281-L4285
[sand-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L313-L347
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L45
[player]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656
[loot-dirt]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/dirt.json
[loot-grass_block]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/grass_block.json
[loot-coarse_dirt]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/coarse_dirt.json
[loot-rooted_dirt]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/rooted_dirt.json
[loot-podzol]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/podzol.json
[loot-mycelium]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/mycelium.json
[loot-dirt_path]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/dirt_path.json
[loot-sand]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/sand.json
[loot-red_sand]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/red_sand.json
[loot-gravel]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/gravel.json
[loot-suspicious_sand]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[loot-suspicious_gravel]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/suspicious_gravel.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[base-survival]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L310
[spread]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/SpreadingSnowyDirtBlock.java#L19-L55
[mycelium-class]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/MyceliumBlock.java
[light]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L50-L58
[grass-bonemeal]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L89
[bone-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L74
[grass-placement]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/placed_feature/grass_bonemeal.json
[grass-feature]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/configured_feature/single_piece_of_grass.json
[snowy]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/SnowyDirtBlock.java
[base-tick]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L341-L342
[mushroom-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1060-L1083
[mushroom-support]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L77-L88
[mushroom-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mushroom_grow_block.json
[coarse-recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/coarse_dirt.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/HoeItem.java#L23-L88
[shovel]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ShovelItem.java#L20-L73
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[roots-drop]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Block.java#L395-L415
[potion-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1770-L1778
[potion]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/PotionItem.java#L34-L68
[mud-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/convertable_to_mud.json
[bottle-result]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L38
[rooted]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/RootedDirtBlock.java#L24-L36
[hanging-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/hanging_roots.json
[path]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/DirtPathBlock.java#L18-L74
[sand-class]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/SandBlock.java
[colored-falling]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/ColoredFallingBlock.java
[falling]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/FallingBlock.java#L28-L65
[fall-spawn]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L92-L102
[fall-land]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L180-L232
[sandstone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/sandstone.json
[red-sandstone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/red_sandstone.json
[glass-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/smelts_to_glass.json
[glass-recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/glass.json
[white-powder]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/white_concrete_powder.json
[brush-fall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L62-L98
[cancel-fall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L311-L313
[brush-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/BrushItem.java#L68-L98
[brush-finish]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L117-L145
