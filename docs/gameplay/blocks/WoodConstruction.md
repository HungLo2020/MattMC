# Wood construction

Use matching planks to make floors, roofs, railings, gates, doors, and trapdoors. This guide covers **12 vanilla wood materials and 87 registered blocks**, including Bamboo Mosaic and its two building shapes. The material changes recipes, appearance, sounds, fire behavior, and fuel eligibility; placement rules are shared by each shape. Tree harvesting and growth remain in [Oak](Oak.md), [Bamboo](Bamboo.md), and the separate [Pewen guide](Pewen.md). [Block registration][blocks] · [Item registration][items] · [Wood sounds][wood-types]

## Planks and materials

Each of these prefixes has all seven forms: `planks`, `slab`, `stairs`, `fence`, `fence_gate`, `door`, and `trapdoor`. Join the prefix and suffix with `_` after `minecraft:`; for example, `minecraft:pale_oak_fence_gate`. The recipe/loot matrix below checks every one of those **84 IDs**. [Registrations][blocks] · [Per-material evidence](#recipe-and-loot-matrix)

| Material and ID prefix | Plank conversion | Accepted input family |
| --- | --- | --- |
| [Oak](../items/OakPlanks.md) (`oak`) | See the canonical [Oak Planks recipe](../items/OakPlanks.md#crafting-oak-planks) | [Oak log-family tag][input-oak_logs] |
| [Spruce](../items/SprucePlanks.md) (`spruce`) | 1 accepted item → **4 planks** [recipe][recipe-spruce_planks] | [Spruce Log, Wood, or either stripped form][input-spruce_logs] |
| [Birch](../items/BirchPlanks.md) (`birch`) | 1 accepted item → **4 planks** [recipe][recipe-birch_planks] | [Birch Log, Wood, or either stripped form][input-birch_logs] |
| [Jungle](../items/JunglePlanks.md) (`jungle`) | 1 accepted item → **4 planks** [recipe][recipe-jungle_planks] | [Jungle Log, Wood, or either stripped form][input-jungle_logs] |
| [Acacia](../items/AcaciaPlanks.md) (`acacia`) | 1 accepted item → **4 planks** [recipe][recipe-acacia_planks] | [Acacia Log, Wood, or either stripped form][input-acacia_logs] |
| [Dark Oak](../items/DarkOakPlanks.md) (`dark_oak`) | 1 accepted item → **4 planks** [recipe][recipe-dark_oak_planks] | [Dark Oak Log, Wood, or either stripped form][input-dark_oak_logs] |
| [Mangrove](../items/MangrovePlanks.md) (`mangrove`) | 1 accepted item → **4 planks** [recipe][recipe-mangrove_planks] | [Mangrove Log, Wood, or either stripped form][input-mangrove_logs] |
| [Cherry](../items/CherryPlanks.md) (`cherry`) | 1 accepted item → **4 planks** [recipe][recipe-cherry_planks] | [Cherry Log, Wood, or either stripped form][input-cherry_logs] |
| [Pale Oak](../items/PaleOakPlanks.md) (`pale_oak`) | 1 accepted item → **4 planks** [recipe][recipe-pale_oak_planks] | [Pale Oak Log, Wood, or either stripped form][input-pale_oak_logs] |
| [Bamboo](../items/BambooPlanks.md) (`bamboo`) | 1 accepted block → **2 planks** [recipe][recipe-bamboo_planks] | [Block of Bamboo or Stripped Block of Bamboo][input-bamboo_blocks] |
| [Crimson](../items/CrimsonPlanks.md) (`crimson`) | 1 accepted item → **4 planks** [recipe][recipe-crimson_planks] | [Crimson Stem, Hyphae, or either stripped form][input-crimson_stems] |
| [Warped](../items/WarpedPlanks.md) (`warped`) | 1 accepted item → **4 planks** [recipe][recipe-warped_planks] | [Warped Stem, Hyphae, or either stripped form][input-warped_stems] |

These plank recipes are shapeless and use one input, so they fit the inventory crafting grid. The family tags are specific: a Spruce Log does not make Oak Planks, and raw Bamboo is not the Bamboo Planks recipe's direct input. See [Bamboo's selected conversions](Bamboo.md#selected-uses-and-fuel) for the raw plant route. All 12 plank items belong to the bundled generic **planks** ingredient tag; Bamboo Mosaic does not. [Plank ingredient tag][item-planks] · [Recipe matrix](#recipe-and-loot-matrix)

Planks are full blocks with no placement-facing or waterlogged state. Like the slabs, stairs, fences, gates, and trapdoors below, they remain placed when the original supporting block is removed. Doors have their own support rule. Ordinary item placement still needs a replaceable destination and an unobstructed placement shape. [Plank registrations][blocks] · [Default full-block shape][shape-default] · [Default survival rule][default-support] · [Placement checks][place-check]

## Crafting construction shapes

Use a [Crafting Table](CraftingTable.md) for these layouts. **Every plank slot must contain the exact material named by that variant's recipe**: these are not generic mixed-plank recipes. Sticks are ordinary [Sticks](../items/Stick.md). [All variant recipes](#recipe-and-loot-matrix)

| Shape | Exact arrangement | Result |
| --- | --- | ---: |
| Slab | 3 matching planks in one horizontal row | 6 slabs |
| Stairs | 6 matching planks: rows of 1, 2, then 3, aligned along one side | 4 stairs |
| Fence | 2 rows of plank–stick–plank: 4 matching planks and 2 sticks | 3 fences |
| Fence Gate | 2 rows of stick–plank–stick: 2 matching planks and 4 sticks | 1 gate |
| Door | 6 matching planks in 2 columns of 3 | 3 doors |
| Trapdoor | 6 matching planks in 2 rows of 3 | 2 trapdoors |

The stair pattern also accepts its horizontal mirror. The checked recipes producing these blocks are crafting recipes; the bundled recipe tree has no Stonecutter conversion producing any of these 87 outputs. [Shaped matching][shaped] · [Recipe matrix](#recipe-and-loot-matrix) · [Stonecutter](Stonecutter.md)

### Bamboo Mosaic

The three extra IDs are `minecraft:bamboo_mosaic`, `minecraft:bamboo_mosaic_slab`, and `minecraft:bamboo_mosaic_stairs`:

- **2 Bamboo Slabs vertically → 1 Bamboo Mosaic**; this fits the inventory grid
- **3 Bamboo Mosaic in a row → 6 Bamboo Mosaic Slabs**
- **6 Bamboo Mosaic in the stair layout → 4 Bamboo Mosaic Stairs**

The last two recipes use full Bamboo Mosaic blocks, not Bamboo Planks. Mosaic is not a substitute in the shape recipes above, and its slab/stairs are absent from the generic wooden-slab/wooden-stair item tags. They still use the shared placement behavior below and are explicitly included in the fuel table. [Mosaic recipe][recipe-bamboo_mosaic] · [Mosaic slab recipe][recipe-bamboo_mosaic_slab] · [Mosaic stair recipe][recipe-bamboo_mosaic_stairs] · [Planks tag][item-planks] · [Wooden slabs][item-wooden_slabs] · [Wooden stairs][item-wooden_stairs] · [Fuel][fuel]

## Mining and drops

An **unbroken axe** is the efficient tool for all 87 covered blocks. None requires a particular tool or material tier to receive its ordinary mining drop, so hand mining can collect them. A retained broken axe loses its normal mining-speed bonus; see [Mining](../mechanics/Mining.md) and [Axes and Hoes](../mechanics/AxesAndHoes.md) for the shared tool rules. [Axe tag][block-mineable-axe] · [Axe tool assignment][axe] · [Tool speed rules][tool] · [Broken tool speed][broken] · [Harvest gate][harvest] · [Active harvest dispatch][harvest-call]

| Placed form | Hardness | Blast resistance | Ordinary mining result |
| --- | ---: | ---: | --- |
| Planks, Bamboo Mosaic, stairs, fences, gates | 2 | 3 | 1 matching item |
| Single slab | 2 | 3 | 1 matching slab |
| Double slab | 2 | 3 | 2 matching slabs |
| Door | 3 | 3 | 1 door for the two-block assembly; the loot entry belongs to the lower half |
| Trapdoor | 3 | 3 | 1 matching trapdoor |

Silk Touch and Fortune do not change these bundled loot results. A double slab gives slabs, not a plank or a special double-slab item. Explosions add survival checks, or quantity decay for slabs, so this table is not a guaranteed explosion yield. [All loot tables](#recipe-and-loot-matrix) · [Block properties][blocks] · [Stair property inheritance][stair-copy] · [Copied properties][property-copy] · [Strength values][strength]

## Slabs

Item entries: [Acacia Slab](../items/AcaciaSlab.md) · [Bamboo Mosaic Slab](../items/BambooMosaicSlab.md) · [Bamboo Slab](../items/BambooSlab.md) · [Birch Slab](../items/BirchSlab.md) · [Cherry Slab](../items/CherrySlab.md) · [Crimson Slab](../items/CrimsonSlab.md) · [Dark Oak Slab](../items/DarkOakSlab.md) · [Jungle Slab](../items/JungleSlab.md) · [Mangrove Slab](../items/MangroveSlab.md) · [Oak Slab](../items/OakSlab.md) · [Pale Oak Slab](../items/PaleOakSlab.md) · [Spruce Slab](../items/SpruceSlab.md) · [Warped Slab](../items/WarpedSlab.md)

A single slab fills the lower or upper half of its block space. Clicking a top face normally places a bottom slab; clicking the underside places a top slab. On a side face, the clicked height selects the half. Place another **identical slab item** into the missing half to make a full-height double slab. Different materials, including regular Bamboo versus Bamboo Mosaic, cannot combine into one double slab. [Placement, replacement, and shapes][slab]

Single slabs can be waterlogged. Combining two slabs sets the result to **double and not waterlogged**, and double slabs reject bucket waterlogging. Mine a double slab to recover its two constituent slabs. [Slab water rules][slab] · [Slab loot checks](#recipe-and-loot-matrix)

## Stairs

Item entries: [Acacia Stairs](../items/AcaciaStairs.md) · [Bamboo Mosaic Stairs](../items/BambooMosaicStairs.md) · [Bamboo Stairs](../items/BambooStairs.md) · [Birch Stairs](../items/BirchStairs.md) · [Cherry Stairs](../items/CherryStairs.md) · [Crimson Stairs](../items/CrimsonStairs.md) · [Dark Oak Stairs](../items/DarkOakStairs.md) · [Jungle Stairs](../items/JungleStairs.md) · [Mangrove Stairs](../items/MangroveStairs.md) · [Oak Stairs](../items/OakStairs.md) · [Pale Oak Stairs](../items/PaleOakStairs.md) · [Spruce Stairs](../items/SpruceStairs.md) · [Warped Stairs](../items/WarpedStairs.md)

Stairs face in the player's horizontal placement direction. The clicked face and height choose bottom or upside-down placement using the same half-selection rule as slabs. Neighboring stairs can form inner or outer corners when their orientations and halves fit. **Different stair materials can form corners together**; compatible halves and directions are still required, and a neighboring parallel stair can prevent a corner. [Stair placement and corner checks][stairs]

Stairs keep their stair item when mined, regardless of their corner shape or half. All covered stair variants can be waterlogged. [Stair water state][stairs-water] · [Stair loot checks](#recipe-and-loot-matrix)

## Fences

Item entries: [Acacia Fence](../items/AcaciaFence.md) · [Bamboo Fence](../items/BambooFence.md) · [Birch Fence](../items/BirchFence.md) · [Cherry Fence](../items/CherryFence.md) · [Crimson Fence](../items/CrimsonFence.md) · [Dark Oak Fence](../items/DarkOakFence.md) · [Jungle Fence](../items/JungleFence.md) · [Mangrove Fence](../items/MangroveFence.md) · [Oak Fence](../items/OakFence.md) · [Pale Oak Fence](../items/PaleOakFence.md) · [Spruce Fence](../items/SpruceFence.md) · [Warped Fence](../items/WarpedFence.md)

The 12 covered wooden fences connect horizontally to one another, including Crimson and Warped. They also connect to a neighbor's sturdy side face unless that block is a connection exception, and to a correctly oriented Fence Gate. They do not join Nether Brick Fence through the same-fence rule: that fence is outside the wooden-fence tag. Leaves, pumpkins, melons, shulker boxes, and barriers are among the explicitly excluded sturdy-face connections. [Fence connection checks][fences] · [Wooden-fence tag][block-wooden_fences] · [Fence tag][block-fences] · [Connection exceptions][connection-exceptions]

Fence collision rises **1.5 blocks**, even though the visible post is one block tall. Removing a rail connection changes the shape, not the fence's existence. Fences can be waterlogged. [Fence dimensions and neighbor updates][fences] · [Collision and fluid implementation][cross]

## Fence Gates

A gate's placement direction determines the direction you walk through it. Fence rails attach across the perpendicular axis; rotating the gate can therefore fix an apparent missing connection. A gate does not require adjacent fences or continuing floor support. [Placement and connection direction][gates] · [Default survival rule][default-support]

Use a gate to open or close it by hand. Opening from its reverse side flips its facing so it opens away from that approach. **Closed collision is 1.5 blocks high; open collision is empty.** A wall-tagged neighbor along the rail axis sets the lower in-wall appearance, without reducing the closed collision height. Gates have no waterlogged state. [Shape, interaction, wall, and state definitions][gates]

## Doors

One door item places both a lower and an upper half, using two vertically adjacent spaces. The lower half needs a **sturdy upper face beneath it**, and the upper space must be replaceable and inside the build limit. Losing the floor or the other half removes the remaining unsupported door half. [Door placement and upper-half creation][door-placement] · [Support checks][door-use] · [Neighbor synchronization][doors] · [Unsupported-block removal][neighbor-destroy] · [Removal drops][destroy-drops] · [Door item placement][double-item]

Doors face in the player's horizontal placement direction. The hinge is chosen from neighboring full collision blocks, adjacent doors, and the click position; where those do not decide it, clicking a different side changes the hinge. Each door's panel is **3/16 of a block thick** and rotates to the side when opened. All 12 materials can open by hand. Doors have no waterlogged state. [Door shapes][doors] · [Hinge selection][door-placement] · [Hand interaction][door-use] · [Wood block-set types][sets]

## Trapdoors

Trapdoors are **3/16-block-thick panels**. Closed panels lie at the bottom or top of the block space; open panels stand vertically. A side-face placement uses that clicked face and height, while top/bottom placement uses the player's opposite horizontal direction and the clicked face to choose its half. Trapdoors can remain after their original attachment is removed; they have no continuing attachment check. [Shape and default state][trapdoors] · [Placement][trapdoor-use] · [Default survival rule][default-support]

All 12 materials open by hand and can be waterlogged. Opening or closing keeps the stored water and schedules its fluid update. [Interaction and water state][trapdoors] · [Toggle, power, and fluid callbacks][trapdoor-use]

## Waterlogging and power

**Single slabs, stairs, fences, and trapdoors** take their waterlogged state from an existing source-water block during ordinary placement; the check specifically compares against source Water, not Flowing Water. A Water Bucket can fill an eligible placed block, and an empty Bucket can collect its stored water. Normal bucket restrictions still apply: water evaporates in an ultrawarm dimension, and secondary use can redirect placement away from the clicked container. [Slabs][slab] · [Stairs][stairs] · [Fences][fences] · [Trapdoors][trapdoor-use] · [Water fluid registrations][fluids] · [Shared water storage][waterlogging] · [Bucket collection][bucket] · [Bucket placement and dimension checks][bucket-place]

Planks, full Bamboo Mosaic, double slabs, doors, and gates do not hold a waterlogged state through these placement paths. Waterlogging a trapdoor does not stop its hand or redstone interaction. [State and water definitions][slab] · [Door states][doors] · [Gate states][gates] · [Trapdoor states and updates][trapdoor-use]

Doors, trapdoors, and gates begin **open if powered when placed**. Later, a change in their detected power sets open to match it: power arriving opens them, and power leaving closes them. A door detects power at either half. Hand interaction toggles open without changing the stored powered value, so steady power is **not a hand-interaction lock**; the next detected power change applies the redstone state again. [Doors][door-placement] · [Door interaction and power][door-use] · [Trapdoors][trapdoor-use] · [Gates][gates]

## Fire and furnace fuel

For fire planning, distinguish the placed block, nearby lava ignition, and the item used as furnace fuel:

| Covered material/form | Ordinary fire spread can consume the placed block | Counts as a flammable neighbor for lava ignition | Default furnace burn ticks per item |
| --- | --- | --- | ---: |
| Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Pale Oak, Bamboo: planks, stairs, fences, gates | Yes | Yes | 300 |
| Those ten materials: slabs | Yes, while not waterlogged | Yes | 150 |
| Those ten materials: doors | No fire-consumption entry | Yes | 200 |
| Those ten materials: trapdoors | No fire-consumption entry | Yes | 300 |
| Bamboo Mosaic and Mosaic Stairs | Yes | Yes | 300 |
| Bamboo Mosaic Slab | Yes, while not waterlogged | Yes | 150 |
| Crimson and Warped: all seven covered forms | No fire-consumption entry | No | Not accepted as fuel |

The flammable planks, slabs, stairs, fences, and gates have the same registered fire odds, **5 to encourage ignition and 20 for burning**. These are algorithm inputs, not percentages or fixed burn times. Waterlogging makes both fire-odds lookups return zero for the waterlogged state, including stairs and fences. Lava's neighboring-block check reads a separate property, so a wooden door or trapdoor can help lava start nearby fire even without its own fire-consumption entry. [Fire registrations][fire-register] · [Fire odds and consumption][fire] · [Complete fire table][fire-all] · [Bootstrap activation][fire-boot] · [Lava ignition][lava] · [Material properties][blocks]

The fuel table explicitly adds Bamboo Mosaic and its shapes, then removes the **non-flammable wood** tag, including all covered Crimson/Warped forms. For continuous cooking and wasted-burn-time guidance, use [Furnace fuel planning](Furnace.md#fuel-planning); the existing [Oak Planks page](../items/OakPlanks.md#fuel) owns the log-to-plank efficiency example. [Default fuel entries][fuel] · [Fuel removal and tag expansion][fuel-build] · [Excluded items][item-non_flammable_wood] · [Server initialization][fuel-init] · [Furnace lookup][fuel-lookup]

## Scope and related pages

These shared rules apply to the exact 87 IDs described here. **Pewen is a separate imported family**: use its [construction and recipe caveats](Pewen.md#construction-and-recipes) instead of assuming that vanilla wood tags, recipes, axe behavior, or stripping apply to it. [Petrified Oak Slab](../items/PetrifiedOakSlab.md) is also outside this family. This guide does not cover tree generation/growth, signs, buttons, pressure plates, shelves, boats, or mob door-opening/breaking behavior.

Related: [Blocks](Blocks.md) · [Oak](Oak.md) · [Bamboo](Bamboo.md) · [Pewen](Pewen.md) · [Mining](../mechanics/Mining.md) · [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Furnace](Furnace.md) · [Items](../items/Items.md)

## Recipe and loot matrix

For each material row, the linked seven recipes and seven loot tables correspond to its `planks`, `slab`, `stairs`, `fence`, `fence_gate`, `door`, and `trapdoor` IDs. The Mosaic row lists only its three registered forms. All inputs, outputs, and drop branches were checked individually; the common layouts above are shared instructions for those exact variants.

| Material | Recipe definitions | Loot tables |
| --- | --- | --- |
| Oak | [Planks][recipe-oak_planks] · [Slab][recipe-oak_slab] · [Stairs][recipe-oak_stairs] · [Fence][recipe-oak_fence] · [Gate][recipe-oak_fence_gate] · [Door][recipe-oak_door] · [Trapdoor][recipe-oak_trapdoor] | [Planks][loot-oak_planks] · [Slab][loot-oak_slab] · [Stairs][loot-oak_stairs] · [Fence][loot-oak_fence] · [Gate][loot-oak_fence_gate] · [Door][loot-oak_door] · [Trapdoor][loot-oak_trapdoor] |
| Spruce | [Planks][recipe-spruce_planks] · [Slab][recipe-spruce_slab] · [Stairs][recipe-spruce_stairs] · [Fence][recipe-spruce_fence] · [Gate][recipe-spruce_fence_gate] · [Door][recipe-spruce_door] · [Trapdoor][recipe-spruce_trapdoor] | [Planks][loot-spruce_planks] · [Slab][loot-spruce_slab] · [Stairs][loot-spruce_stairs] · [Fence][loot-spruce_fence] · [Gate][loot-spruce_fence_gate] · [Door][loot-spruce_door] · [Trapdoor][loot-spruce_trapdoor] |
| Birch | [Planks][recipe-birch_planks] · [Slab][recipe-birch_slab] · [Stairs][recipe-birch_stairs] · [Fence][recipe-birch_fence] · [Gate][recipe-birch_fence_gate] · [Door][recipe-birch_door] · [Trapdoor][recipe-birch_trapdoor] | [Planks][loot-birch_planks] · [Slab][loot-birch_slab] · [Stairs][loot-birch_stairs] · [Fence][loot-birch_fence] · [Gate][loot-birch_fence_gate] · [Door][loot-birch_door] · [Trapdoor][loot-birch_trapdoor] |
| Jungle | [Planks][recipe-jungle_planks] · [Slab][recipe-jungle_slab] · [Stairs][recipe-jungle_stairs] · [Fence][recipe-jungle_fence] · [Gate][recipe-jungle_fence_gate] · [Door][recipe-jungle_door] · [Trapdoor][recipe-jungle_trapdoor] | [Planks][loot-jungle_planks] · [Slab][loot-jungle_slab] · [Stairs][loot-jungle_stairs] · [Fence][loot-jungle_fence] · [Gate][loot-jungle_fence_gate] · [Door][loot-jungle_door] · [Trapdoor][loot-jungle_trapdoor] |
| Acacia | [Planks][recipe-acacia_planks] · [Slab][recipe-acacia_slab] · [Stairs][recipe-acacia_stairs] · [Fence][recipe-acacia_fence] · [Gate][recipe-acacia_fence_gate] · [Door][recipe-acacia_door] · [Trapdoor][recipe-acacia_trapdoor] | [Planks][loot-acacia_planks] · [Slab][loot-acacia_slab] · [Stairs][loot-acacia_stairs] · [Fence][loot-acacia_fence] · [Gate][loot-acacia_fence_gate] · [Door][loot-acacia_door] · [Trapdoor][loot-acacia_trapdoor] |
| Dark Oak | [Planks][recipe-dark_oak_planks] · [Slab][recipe-dark_oak_slab] · [Stairs][recipe-dark_oak_stairs] · [Fence][recipe-dark_oak_fence] · [Gate][recipe-dark_oak_fence_gate] · [Door][recipe-dark_oak_door] · [Trapdoor][recipe-dark_oak_trapdoor] | [Planks][loot-dark_oak_planks] · [Slab][loot-dark_oak_slab] · [Stairs][loot-dark_oak_stairs] · [Fence][loot-dark_oak_fence] · [Gate][loot-dark_oak_fence_gate] · [Door][loot-dark_oak_door] · [Trapdoor][loot-dark_oak_trapdoor] |
| Mangrove | [Planks][recipe-mangrove_planks] · [Slab][recipe-mangrove_slab] · [Stairs][recipe-mangrove_stairs] · [Fence][recipe-mangrove_fence] · [Gate][recipe-mangrove_fence_gate] · [Door][recipe-mangrove_door] · [Trapdoor][recipe-mangrove_trapdoor] | [Planks][loot-mangrove_planks] · [Slab][loot-mangrove_slab] · [Stairs][loot-mangrove_stairs] · [Fence][loot-mangrove_fence] · [Gate][loot-mangrove_fence_gate] · [Door][loot-mangrove_door] · [Trapdoor][loot-mangrove_trapdoor] |
| Cherry | [Planks][recipe-cherry_planks] · [Slab][recipe-cherry_slab] · [Stairs][recipe-cherry_stairs] · [Fence][recipe-cherry_fence] · [Gate][recipe-cherry_fence_gate] · [Door][recipe-cherry_door] · [Trapdoor][recipe-cherry_trapdoor] | [Planks][loot-cherry_planks] · [Slab][loot-cherry_slab] · [Stairs][loot-cherry_stairs] · [Fence][loot-cherry_fence] · [Gate][loot-cherry_fence_gate] · [Door][loot-cherry_door] · [Trapdoor][loot-cherry_trapdoor] |
| Pale Oak | [Planks][recipe-pale_oak_planks] · [Slab][recipe-pale_oak_slab] · [Stairs][recipe-pale_oak_stairs] · [Fence][recipe-pale_oak_fence] · [Gate][recipe-pale_oak_fence_gate] · [Door][recipe-pale_oak_door] · [Trapdoor][recipe-pale_oak_trapdoor] | [Planks][loot-pale_oak_planks] · [Slab][loot-pale_oak_slab] · [Stairs][loot-pale_oak_stairs] · [Fence][loot-pale_oak_fence] · [Gate][loot-pale_oak_fence_gate] · [Door][loot-pale_oak_door] · [Trapdoor][loot-pale_oak_trapdoor] |
| Bamboo | [Planks][recipe-bamboo_planks] · [Slab][recipe-bamboo_slab] · [Stairs][recipe-bamboo_stairs] · [Fence][recipe-bamboo_fence] · [Gate][recipe-bamboo_fence_gate] · [Door][recipe-bamboo_door] · [Trapdoor][recipe-bamboo_trapdoor] | [Planks][loot-bamboo_planks] · [Slab][loot-bamboo_slab] · [Stairs][loot-bamboo_stairs] · [Fence][loot-bamboo_fence] · [Gate][loot-bamboo_fence_gate] · [Door][loot-bamboo_door] · [Trapdoor][loot-bamboo_trapdoor] |
| Crimson | [Planks][recipe-crimson_planks] · [Slab][recipe-crimson_slab] · [Stairs][recipe-crimson_stairs] · [Fence][recipe-crimson_fence] · [Gate][recipe-crimson_fence_gate] · [Door][recipe-crimson_door] · [Trapdoor][recipe-crimson_trapdoor] | [Planks][loot-crimson_planks] · [Slab][loot-crimson_slab] · [Stairs][loot-crimson_stairs] · [Fence][loot-crimson_fence] · [Gate][loot-crimson_fence_gate] · [Door][loot-crimson_door] · [Trapdoor][loot-crimson_trapdoor] |
| Warped | [Planks][recipe-warped_planks] · [Slab][recipe-warped_slab] · [Stairs][recipe-warped_stairs] · [Fence][recipe-warped_fence] · [Gate][recipe-warped_fence_gate] · [Door][recipe-warped_door] · [Trapdoor][recipe-warped_trapdoor] | [Planks][loot-warped_planks] · [Slab][loot-warped_slab] · [Stairs][loot-warped_stairs] · [Fence][loot-warped_fence] · [Gate][loot-warped_fence_gate] · [Door][loot-warped_door] · [Trapdoor][loot-warped_trapdoor] |
| Bamboo Mosaic | [Full block][recipe-bamboo_mosaic] · [Slab][recipe-bamboo_mosaic_slab] · [Stairs][recipe-bamboo_mosaic_stairs] | [Full block][loot-bamboo_mosaic] · [Slab][loot-bamboo_mosaic_slab] · [Stairs][loot-bamboo_mosaic_stairs] |

## Sources and verification

Source-reviewed on **2026-10-02** at `fb7d6979fb8d9773cfe05f084c6085f35feb885c`, using active registrations, placement/interaction callbacks, tags, **87 production recipes**, and **87 block loot tables**. No in-game crafting, mining, placement, water, power, or fire test was run. Data packs can change recipes, tags, and loot, and server rules affect fire. Tree growth, natural structure occurrence, and trades were not part of this review.

[blocks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/Items.java
[stair-copy]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7258
[property-copy]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1060-L1089
[strength]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[default-support]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L308-L311
[defaults]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1034
[slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L127
[stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/StairBlock.java#L33-L164
[stairs-water]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/StairBlock.java#L212-L220
[fences]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/FenceBlock.java#L28-L124
[cross]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java#L23-L80
[connection-exceptions]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/Block.java#L243-L251
[gates]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java#L40-L209
[doors]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L43-L108
[door-placement]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L141-L199
[door-use]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L201-L245
[trapdoors]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L40-L99
[trapdoor-use]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L113-L193
[wood-types]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/properties/WoodType.java#L16-L77
[sets]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L117-L216
[place]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L84
[place-check]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L142
[double-item]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/DoubleHighBlockItem.java#L10-L23
[shape-default]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L320-L327
[neighbor-destroy]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/Block.java#L213-L226
[destroy-drops]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/Level.java#L263-L281
[fluids]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/material/Fluids.java#L7-L11
[waterlogging]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bucket]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/BucketItem.java#L40-L85
[bucket-place]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L140
[axe]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[broken]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[harvest]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[shaped]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[fire-register]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/FireBlock.java#L303-L362
[fire]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/FireBlock.java#L223-L249
[fire-all]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/FireBlock.java
[fire-boot]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/server/Bootstrap.java#L42-L51
[lava]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L78-L136
[fuel]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-build]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L125-L148
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[fuel-lookup]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L266
[block-mineable-axe]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[block-wooden_fences]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/block/wooden_fences.json
[block-fences]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/block/fences.json
[item-planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/planks.json
[item-wooden_slabs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[item-wooden_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/wooden_stairs.json
[item-wooden_fences]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/wooden_fences.json
[item-fence_gates]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/fence_gates.json
[item-wooden_doors]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/wooden_doors.json
[item-wooden_trapdoors]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/wooden_trapdoors.json
[item-non_flammable_wood]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[recipe-oak_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_planks.json
[loot-oak_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_planks.json
[input-oak_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/oak_logs.json
[recipe-oak_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_slab.json
[loot-oak_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_slab.json
[recipe-oak_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_stairs.json
[loot-oak_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_stairs.json
[recipe-oak_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_fence.json
[loot-oak_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_fence.json
[recipe-oak_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_fence_gate.json
[loot-oak_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_fence_gate.json
[recipe-oak_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_door.json
[loot-oak_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_door.json
[recipe-oak_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_trapdoor.json
[loot-oak_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/oak_trapdoor.json
[recipe-spruce_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_planks.json
[loot-spruce_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_planks.json
[input-spruce_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/spruce_logs.json
[recipe-spruce_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_slab.json
[loot-spruce_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_slab.json
[recipe-spruce_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_stairs.json
[loot-spruce_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_stairs.json
[recipe-spruce_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_fence.json
[loot-spruce_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_fence.json
[recipe-spruce_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_fence_gate.json
[loot-spruce_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_fence_gate.json
[recipe-spruce_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_door.json
[loot-spruce_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_door.json
[recipe-spruce_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/spruce_trapdoor.json
[loot-spruce_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/spruce_trapdoor.json
[recipe-birch_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_planks.json
[loot-birch_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_planks.json
[input-birch_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/birch_logs.json
[recipe-birch_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_slab.json
[loot-birch_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_slab.json
[recipe-birch_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_stairs.json
[loot-birch_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_stairs.json
[recipe-birch_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_fence.json
[loot-birch_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_fence.json
[recipe-birch_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_fence_gate.json
[loot-birch_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_fence_gate.json
[recipe-birch_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_door.json
[loot-birch_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_door.json
[recipe-birch_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/birch_trapdoor.json
[loot-birch_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/birch_trapdoor.json
[recipe-jungle_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_planks.json
[loot-jungle_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_planks.json
[input-jungle_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/jungle_logs.json
[recipe-jungle_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_slab.json
[loot-jungle_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_slab.json
[recipe-jungle_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_stairs.json
[loot-jungle_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_stairs.json
[recipe-jungle_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_fence.json
[loot-jungle_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_fence.json
[recipe-jungle_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_fence_gate.json
[loot-jungle_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_fence_gate.json
[recipe-jungle_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_door.json
[loot-jungle_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_door.json
[recipe-jungle_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/jungle_trapdoor.json
[loot-jungle_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/jungle_trapdoor.json
[recipe-acacia_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_planks.json
[loot-acacia_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_planks.json
[input-acacia_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/acacia_logs.json
[recipe-acacia_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_slab.json
[loot-acacia_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_slab.json
[recipe-acacia_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_stairs.json
[loot-acacia_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_stairs.json
[recipe-acacia_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_fence.json
[loot-acacia_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_fence.json
[recipe-acacia_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_fence_gate.json
[loot-acacia_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_fence_gate.json
[recipe-acacia_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_door.json
[loot-acacia_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_door.json
[recipe-acacia_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/acacia_trapdoor.json
[loot-acacia_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/acacia_trapdoor.json
[recipe-dark_oak_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_planks.json
[loot-dark_oak_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_planks.json
[input-dark_oak_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/dark_oak_logs.json
[recipe-dark_oak_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_slab.json
[loot-dark_oak_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_slab.json
[recipe-dark_oak_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_stairs.json
[loot-dark_oak_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_stairs.json
[recipe-dark_oak_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_fence.json
[loot-dark_oak_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_fence.json
[recipe-dark_oak_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_fence_gate.json
[loot-dark_oak_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_fence_gate.json
[recipe-dark_oak_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_door.json
[loot-dark_oak_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_door.json
[recipe-dark_oak_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/dark_oak_trapdoor.json
[loot-dark_oak_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_trapdoor.json
[recipe-mangrove_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_planks.json
[loot-mangrove_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_planks.json
[input-mangrove_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/mangrove_logs.json
[recipe-mangrove_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_slab.json
[loot-mangrove_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_slab.json
[recipe-mangrove_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_stairs.json
[loot-mangrove_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_stairs.json
[recipe-mangrove_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_fence.json
[loot-mangrove_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_fence.json
[recipe-mangrove_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_fence_gate.json
[loot-mangrove_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_fence_gate.json
[recipe-mangrove_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_door.json
[loot-mangrove_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_door.json
[recipe-mangrove_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/mangrove_trapdoor.json
[loot-mangrove_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/mangrove_trapdoor.json
[recipe-cherry_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_planks.json
[loot-cherry_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_planks.json
[input-cherry_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/cherry_logs.json
[recipe-cherry_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_slab.json
[loot-cherry_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_slab.json
[recipe-cherry_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_stairs.json
[loot-cherry_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_stairs.json
[recipe-cherry_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_fence.json
[loot-cherry_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_fence.json
[recipe-cherry_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_fence_gate.json
[loot-cherry_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_fence_gate.json
[recipe-cherry_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_door.json
[loot-cherry_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_door.json
[recipe-cherry_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/cherry_trapdoor.json
[loot-cherry_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/cherry_trapdoor.json
[recipe-pale_oak_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_planks.json
[loot-pale_oak_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_planks.json
[input-pale_oak_logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/pale_oak_logs.json
[recipe-pale_oak_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_slab.json
[loot-pale_oak_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_slab.json
[recipe-pale_oak_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_stairs.json
[loot-pale_oak_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_stairs.json
[recipe-pale_oak_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_fence.json
[loot-pale_oak_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_fence.json
[recipe-pale_oak_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_fence_gate.json
[loot-pale_oak_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_fence_gate.json
[recipe-pale_oak_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_door.json
[loot-pale_oak_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_door.json
[recipe-pale_oak_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/pale_oak_trapdoor.json
[loot-pale_oak_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_trapdoor.json
[recipe-bamboo_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_planks.json
[loot-bamboo_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_planks.json
[input-bamboo_blocks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/bamboo_blocks.json
[recipe-bamboo_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_slab.json
[loot-bamboo_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_slab.json
[recipe-bamboo_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_stairs.json
[loot-bamboo_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_stairs.json
[recipe-bamboo_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_fence.json
[loot-bamboo_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_fence.json
[recipe-bamboo_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_fence_gate.json
[loot-bamboo_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_fence_gate.json
[recipe-bamboo_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_door.json
[loot-bamboo_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_door.json
[recipe-bamboo_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_trapdoor.json
[loot-bamboo_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_trapdoor.json
[recipe-crimson_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_planks.json
[loot-crimson_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_planks.json
[input-crimson_stems]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[recipe-crimson_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_slab.json
[loot-crimson_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_slab.json
[recipe-crimson_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_stairs.json
[loot-crimson_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_stairs.json
[recipe-crimson_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_fence.json
[loot-crimson_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_fence.json
[recipe-crimson_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_fence_gate.json
[loot-crimson_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_fence_gate.json
[recipe-crimson_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_door.json
[loot-crimson_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_door.json
[recipe-crimson_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crimson_trapdoor.json
[loot-crimson_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crimson_trapdoor.json
[recipe-warped_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_planks.json
[loot-warped_planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_planks.json
[input-warped_stems]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/warped_stems.json
[recipe-warped_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_slab.json
[loot-warped_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_slab.json
[recipe-warped_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_stairs.json
[loot-warped_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_stairs.json
[recipe-warped_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_fence.json
[loot-warped_fence]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_fence.json
[recipe-warped_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_fence_gate.json
[loot-warped_fence_gate]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_fence_gate.json
[recipe-warped_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_door.json
[loot-warped_door]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_door.json
[recipe-warped_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/warped_trapdoor.json
[loot-warped_trapdoor]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/warped_trapdoor.json
[recipe-bamboo_mosaic]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic.json
[loot-bamboo_mosaic]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_mosaic.json
[recipe-bamboo_mosaic_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic_slab.json
[loot-bamboo_mosaic_slab]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_mosaic_slab.json
[recipe-bamboo_mosaic_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic_stairs.json
[loot-bamboo_mosaic_stairs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/bamboo_mosaic_stairs.json
