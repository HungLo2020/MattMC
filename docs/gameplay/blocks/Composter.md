# Composter

A **Composter** (`minecraft:composter`) consumes accepted plants and food to build up compost, then produces **one Bone Meal** per completed batch. It also provides a level-based comparator output and a Farmer job site. It is a processing block with no inventory screen. [Block behavior][composter] · [Farmer registration][poi] [profession]

## Crafting, placement and recovery

Craft **7 wooden slabs → 1 Composter** in a U shape: slabs in the left and right slots of the top two rows, and all three bottom slots. Each slab must be in `minecraft:wooden_slabs`; accepted wood types can be mixed. Bamboo Mosaic Slabs are not members of that tag. [Recipe][recipe] · [Accepted slabs][slabs] · [Ingredient matching][ingredient] [pattern]

A normally crafted or recovered Composter places at **level 0**, with no facing to set. An unbroken axe, including Wood, mines it faster, but **a bare hand can recover the block** because no correct-tool requirement is registered. Hardness and blast resistance are both **0.6**. [Initial state][composter] · [Registry/properties][blocks] [properties] · [Axe rules and harvesting][axe] [tool][] [stack][] [player-tool][] [harvest][]

Ordinary mining drops **one Composter** at every level. At **level 8**, it also drops **one Bone Meal**. Partial compost and the waiting level-7 state do not add that Bone Meal drop, and their consumed ingredients are not returned. The item does not preserve progress; Silk Touch is unnecessary and Fortune adds nothing. The Composter-item loot applies explosion decay separately from the level-8 Bone Meal pool. [Exact loot][loot] · [Default placement][composter] [block]

## From ingredients to Bone Meal

1. Use an [accepted item](#complete-accepted-item-list) on the Composter
2. The **first accepted item in an empty Composter always adds a level**
3. At levels **1–6**, each further item has its listed chance to add **one level**
4. After reaching **level 7**, wait for the scheduled **20-game-tick** maturation step, nominally one second at 20 ticks per second
5. At **level 8**, interact to release **one Bone Meal item** and reset the Composter to level 0

Each accepted player insertion at levels 0–6 consumes **one item in Survival**, even if its random roll fails and the level stays unchanged. Infinite-material players do not lose the held item. These are chances to add one compost level, not chances to produce several Bone Meal or several levels at once. The minimum is seven successful additions for a batch, with the first guaranteed. [Player transaction and level progression][composter] · [Consumption][stack] · [Active interaction dispatch][use-dispatch]

Level 7 accepts no further compost progress: using a compostable item during the wait does not consume it. At level 8, even a held compostable item can route to collecting the Bone Meal instead of starting the next batch. The collected Bone Meal is spawned as a loose item near the top, not inserted directly into the player's inventory. Use an empty hand to make the collection action unambiguous. [Ready/waiting interactions and extraction][composter]

Maturation is a scheduled server block tick, not a real-time guarantee. The current compost level and scheduled block ticks are part of chunk save data; the block does not retain the original ingredients in recoverable item slots. Breaking and replacing it loses partial progress. [Tick scheduling and handler][composter] · [Active tick dispatch][server-level] · [Block-state/tick serialization][chunk-data]

## Hoppers and automation

| State | Hopper or Dropper input | Hopper extraction |
| --- | --- | --- |
| Levels 0–6 | Accepted items through the **top face only**, one per transfer | None |
| Level 7, waiting | None | None |
| Level 8, ready | None | **One Bone Meal through the bottom face**, resetting to level 0 |

Put a downward-pointing [Hopper](Hopper.md) above the Composter to feed it, and another Hopper below to collect the Bone Meal. A downward-facing [Dropper](DispenserAndDropper.md#dropper-transfer-and-ejection) above it can use the same top input. Sideways connections cannot insert ingredients or remove the product. Normal Hopper redstone locking still applies. [Compost input/output containers][composter] · [Hopper face checks][hopper] · [Dropper transfer][dropper]

The input is a temporary processing slot: once a transfer is accepted, the ingredient is spent even if compost does not rise. It is not stored for later retrieval. The ready output represents one Bone Meal, not a buffer of previous batches. Keep an empty slot or room in a Bone Meal stack in the output Hopper. A Hopper filled with partial stacks of other items can have no room for Bone Meal while still attempting extraction. The reviewed failed-transfer path can reset the ready Composter before the Hopper accepts its product; restoring the temporary output slot does not restore the world block. This potential loss case is tracked in [issue #796](https://github.com/HungLo2020/MattMC/issues/796) and has not been tested in-game. [One-item containers, roll and empty transition][composter] · [Destination transfer checks][hopper] · [Removal notification][simple-container]

Simply dropping loose plant items into the opening does not invoke the composting interaction. Use the player action or a supported container transfer. The Composter has no loose-item pickup callback. [Block callbacks][composter]

## Comparator and Farmer use

A comparator reads the **actual level, 0–8**: 0 when empty, 1–6 while filling, 7 during maturation, and 8 when Bone Meal is ready. This does not scale up to 15 like many inventory fullness signals. Emptying resets the reading to 0; powering the Composter itself does not add ingredients or instantly finish the batch. [Current analog-output callback and block behavior][composter] · [Comparator dispatch][comparator]

An unemployed adult Villager can claim an available, reachable Composter as a **Farmer** job site. All nine levels belong to that job-site type, with room for **one claimant**. It does not need existing compost to qualify. Normal baby, nitwit and existing-job restrictions still apply; see [Villager employment](../mobs/Villager.md#employment-and-changing-jobs) and [Trading](../trading/Trading.md). [POI and profession mapping][poi] [profession] · [Active acquisition][villager] [goals][] [acquire][] [assign]

A Farmer's own work routine can collect ready Bone Meal and compost surplus **Wheat Seeds and Beetroot Seeds**. It keeps at least ten of each seed type and attempts at most twenty insertions in one work call, stopping when the Composter reaches level 7. This AI routine does not try every item the player can compost. A Farmer working at your Composter can therefore change its level. [Farmer-specific work selection][goals] · [Actual work routine][farmer-work]

## Complete accepted item list

The inspected implementation explicitly registers **115 items**. The list below is complete for that source snapshot: there are **45 at 30%**, **11 at 50%**, **46 at 65%**, **11 at 85%**, and **2 at 100%**. These percentages apply to additions at levels 1–6; every listed item's first addition at level 0 is guaranteed. Plant appearance, a broad leaf/flower tag, or imported-mod origin does not automatically add an item to this list. [Bootstrapped item/chance map][composter] [bootstrap] · [Item and block ID registrations][items] [blocks]

### 30% chance

- Acacia Leaves, Acacia Sapling, Azalea Leaves, Beetroot Seeds, Birch Leaves, Birch Sapling, Bush, Cactus Flower, Cherry Leaves
- Cherry Sapling, Dark Oak Leaves, Dark Oak Sapling, Dried Kelp, Firefly Bush, Glow Berries, Hanging Roots, Jungle Leaves, Jungle Sapling
- Kelp, Leaf Litter, Mangrove Leaves, Mangrove Propagule, Mangrove Roots, Melon Seeds, Moss Carpet, Oak Leaves, Oak Sapling
- Pale Hanging Moss, Pale Moss Carpet, Pale Oak Leaves, Pale Oak Sapling, Pink Petals, Pitcher Pod, Pumpkin Seeds, Seagrass, Short Dry Grass
- Short Grass, Small Dripleaf, Spruce Leaves, Spruce Sapling, Sweet Berries, Tall Dry Grass, Torchflower Seeds, Wheat Seeds, Wildflowers

### 50% chance

- Cactus, Dried Kelp Block, Flowering Azalea Leaves, Glow Lichen, Melon Slice, Nether Sprouts, Sugar Cane, Tall Grass, Twisting Vines
- Vines, Weeping Vines

### 65% chance

- Allium, Apple, Azalea, Azure Bluet, Beetroot, Big Dripleaf, Blue Orchid, Brown Mushroom, Carrot
- Carved Pumpkin, Closed Eyeblossom, Cocoa Beans, Cornflower, Crimson Fungus, Crimson Roots, Dandelion, Fern, Large Fern
- Lilac, Lily Of The Valley, Lily Pad, Melon, Moss Block, Mushroom Stem, Nether Wart, Open Eyeblossom, Orange Tulip
- Oxeye Daisy, Pale Moss Block, Peony, Pink Tulip, Poppy, Potato, Pumpkin, Red Mushroom, Red Tulip
- Rose Bush, Sea Pickle, Shroomlight, Spore Blossom, Sunflower, Warped Fungus, Warped Roots, Wheat, White Tulip
- Wither Rose

### 85% chance

- Baked Potato, Bread, Brown Mushroom Block, Cookie, Flowering Azalea, Hay Bale, Nether Wart Block, Pitcher Plant, Red Mushroom Block
- Torchflower, Warped Wart Block

### 100% chance

- Cake, Pumpkin Pie

For harvesting, replanting and Bone Meal work, see [Farmer crop tending](../mobs/Villager.md#farmer-crop-tending).

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`. Checked the complete initialized compostable map and resolved all 115 item IDs, exact recipe/loot/tools, active player and sided-container transactions, first-item exception, failed-roll consumption, level-7 tick, level-8 collection/mining, comparator, chunk state/tick serialization and Farmer job/work paths. Natural generation was not surveyed. No in-game composting, automation, timing, harvesting, save/reload or Villager test was run. Later implementations can change the accepted-item map; server data can alter recipes and loot.

Related: [Composter item](../items/Composter.md) · [Bone Meal](../items/BoneMeal.md) · [Hopper](Hopper.md) · [Farmland](Farmland.md) · [Workstations](catalog/workstations.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java
[properties]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[tool]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ToolMaterial.java
[stack]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ItemStack.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[player-tool]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L343-L378
[recipes]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[dropper]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/DropperBlock.java
[comparator]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java
[poi]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java
[profession]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java
[villager]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/Villager.java
[goals]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java
[acquire]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java
[assign]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java
[composter]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/composter.json
[loot]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/composter.json
[slabs]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/Ingredient.java
[pattern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[axe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Block.java
[server-level]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerLevel.java
[chunk-data]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java
[farmer-work]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtComposter.java
[simple-container]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/SimpleContainer.java
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/Bootstrap.java
