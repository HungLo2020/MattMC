# Copper construction

Copper can change from orange to green while placed, or keep its current finish when waxed. For bulk construction, use a [Stonecutter](Stonecutter.md): its full-copper conversions produce substantially more building pieces per input than the corresponding crafting layouts. This guide covers **64 structural copper blocks**, their recipes, collection, oxidation, and controls. [Full blocks][registry-full] · [Building shapes][registry-shapes] · [Doors/trapdoors][registry-doors] · [Grates][registry-grates]

## Covered forms and IDs

All IDs below use the `minecraft:` namespace. **Each of these 32 unwaxed IDs also has a separate waxed ID formed by adding `waxed_` at the front**: for example, `minecraft:waxed_copper_block` and `minecraft:waxed_weathered_cut_copper_stairs`. This gives eight shapes × four oxidation stages × two wax states = **64 blocks**. Their block items are registered too. [Block registrations][registry-shapes] · [Door registrations][registry-doors] · [Grate registrations][registry-grates] · [Item registration][items]

| Shape | Unaffected | Exposed | Weathered | Oxidized |
| --- | --- | --- | --- | --- |
| Full Copper | `copper_block` | `exposed_copper` | `weathered_copper` | `oxidized_copper` |
| Cut Copper | `cut_copper` | `exposed_cut_copper` | `weathered_cut_copper` | `oxidized_cut_copper` |
| Chiseled Copper | `chiseled_copper` | `exposed_chiseled_copper` | `weathered_chiseled_copper` | `oxidized_chiseled_copper` |
| Copper Grate | `copper_grate` | `exposed_copper_grate` | `weathered_copper_grate` | `oxidized_copper_grate` |
| Cut Copper Slab | `cut_copper_slab` | `exposed_cut_copper_slab` | `weathered_cut_copper_slab` | `oxidized_cut_copper_slab` |
| Cut Copper Stairs | `cut_copper_stairs` | `exposed_cut_copper_stairs` | `weathered_cut_copper_stairs` | `oxidized_cut_copper_stairs` |
| Copper Door | `copper_door` | `exposed_copper_door` | `weathered_copper_door` | `oxidized_copper_door` |
| Copper Trapdoor | `copper_trapdoor` | `exposed_copper_trapdoor` | `weathered_copper_trapdoor` | `oxidized_copper_trapdoor` |

Copper Ores and Raw Copper Block, Bulbs, Chests, Golem Statues, and torches are outside this construction guide. Lightning Rods appear only where they affect oxidation removal. Use [ore resources](OreResources.md) and [Copper Ingots](../items/CopperIngot.md) for metal production; a copper name alone does not establish the same recipe, loot, or behavior.

## Crafting and Stonecutter yields

Start with the [Copper Ingot page's storage-block recipe](../items/CopperIngot.md#selected-uses), and follow its [packing/unpacking guidance](../items/CopperIngot.md#obtaining) for converting between ingots and the storage block. Both the ordinary **Block of Copper** and **Waxed Block of Copper** unpack directly into **9 Copper Ingots**. Exposed, Weathered, and Oxidized full blocks must be scraped back to the unaffected stage first, removing any wax before scraping; Cut and Chiseled Copper do not unpack into ingots. [Packing recipe][r-copper_block] · [Ordinary unpacking input][unpack] · [Waxed unpacking input][unpack-waxed]

### Crafting layouts

The first five rows work in **all four oxidation stages, either unwaxed or waxed**, using exactly matching inputs. Each recipe names the input item directly: do not mix stages or wax states. The Door and Trapdoor ingot recipes create only the unaffected, unwaxed forms. [Complete per-variant recipes](#recipe-and-loot-evidence)

| Output | Exact ingredients and layout | Yield |
| --- | --- | ---: |
| Cut Copper | 4 matching full copper blocks in a 2 × 2 square | 4 |
| Cut Copper Slabs | 3 matching Cut Copper in a horizontal row | 6 |
| Cut Copper Stairs | 6 matching Cut Copper in rows of 1, 2, then 3 along one side | 4 |
| Chiseled Copper | 2 matching Cut Copper Slabs vertically | 1 |
| Copper Grates | 4 matching full copper blocks at top-center, middle-left, middle-right, and bottom-center of a 3 × 3 grid | 4 |
| Copper Doors | 6 Copper Ingots in 2 columns of 3 | 3 |
| Copper Trapdoor | 4 Copper Ingots in a 2 × 2 square | 1 |

Horizontal mirroring is accepted for the stair layout. Use a Crafting Table for the three-wide or three-tall layouts; the 2 × 2 and two-slab conversions fit the inventory grid. [Shaped matching][shaped] · [Door recipe][r-copper_door] · [Trapdoor recipe][r-copper_trapdoor]

There are **no direct production recipes for the unwaxed Exposed/Weathered/Oxidized full Copper blocks, Doors, or Trapdoors** in this checked set. Let their placed unwaxed forms oxidize, or scrape a later stage back to the desired one. By contrast, an already-aged full block can be crafted or stonecut into its matching building shapes. [Oxidation pairs][weather] · [Recipe matrix](#recipe-and-loot-evidence)

### Stonecutter conversions

Each result below consumes **one** input. Every listed conversion exists for all four stages and both wax states, preserving the input's stage and wax status. Here, “full copper” means the plain Copper Block/Exposed Copper/Weathered Copper/Oxidized Copper family, not Cut Copper. [Result-slot consumption][cutter-menu] · [All 64 stonecutting recipes](#recipe-and-loot-evidence)

| Output | From 1 matching full copper block | From 1 matching Cut Copper |
| --- | ---: | ---: |
| Cut Copper | 4 | — |
| Chiseled Copper | 4 | 1 |
| Copper Grate | 4 | — |
| Cut Copper Slab | 8 | 2 |
| Cut Copper Stairs | 4 | 1 |

This makes the Stonecutter especially useful before large builds: crafting 4 full blocks gives 4 Cut Copper or 4 Grates, while stonecutting those 4 full blocks gives **16** of either. Waxing a full block before stonecutting also spreads that one block's wax across all its outputs. These yields come from the checked recipes. [Cut crafting][r-cut_copper] · [Cut stonecutting][r-cut_copper_from_copper_block_stonecutting] · [Grate crafting][r-copper_grate] · [Grate stonecutting][r-copper_grate_from_copper_block_stonecutting] · [Waxed slab yield][r-waxed_cut_copper_slab_from_waxed_copper_block_stonecutting]

## Oxidation and spacing

Unwaxed construction proceeds **Unaffected → Exposed → Weathered → Oxidized**, one stage at a time. Fully Oxidized blocks stop advancing. The changing block retains its shared state, such as slab half/double state, stair facing/shape, door hinge/open state, or waterlogging. Waxed registrations use implementations without this weathering callback. [Stage map and property preservation][weather] · [Property copying][copy-state] · [Unwaxed callbacks][aging-full] · [Waxed registrations][registry-shapes] · [Doors][registry-doors] · [Grates][registry-grates]

Oxidation is driven by **random block ticks**, not a fixed timer. These callbacks do not require daylight, an open sky, rain, air exposure, or contact with water; waterlogging does not stop them. The block still needs to receive random ticks. [Server tick dispatch][random-dispatch] · [Full block][aging-full] · [Slab][aging-slab] · [Stair][aging-stair] · [Trapdoor][aging-trapdoor] · [Grate][aging-grate]

On an eligible random tick:

1. An initial roll succeeds with probability **0.05688889**, about **5.69%**
2. The scan considers other weathering blocks using the same copper-stage system within **Manhattan distance 4**: add the absolute X, Y, and Z offsets
3. **Any less-oxidized neighbor cancels that attempt**. Otherwise, same-stage neighbors slow it; more-oxidized neighbors increase the relative chance
4. With `older` more-oxidized neighbors and `same` equal-stage neighbors, the second-roll chance is `((older + 1) / (older + same + 1))² × modifier`. The modifier is **0.75** for Unaffected copper and **1** for Exposed or Weathered copper

Both rolls must pass. For an isolated full block with no counted copper neighbors, that is about **4.27% per selected random tick** for Unaffected copper, or **5.69%** for the next two stages. These are not per-game-tick rates or promised completion times. [Exact scan and rolls][neighbor-aging] · [Manhattan scan order][manhattan] · [Stage modifier][weather]

For a batch you want to age independently, keep the blocks at least **5 apart by Manhattan distance**; in a straight line that leaves four spaces between block positions. Intervening walls do not hide neighbors from this scan. Other unwaxed shapes count, including fully Oxidized copper; the waxed forms covered here do not count. A copper door attempts aging only from its lower half, but its upper half still counts as an equal-stage neighbor, so do not apply the isolated-full-block example directly to doors. [Neighbor scan][neighbor-aging] · [Door callback][aging-door] · [Waxed implementations][registry-shapes] · [Door implementations][registry-doors] · [Grate implementations][registry-grates]

## Waxing and scraping

Use **1 Honeycomb** on any covered unwaxed placed block to wax its current stage. The successful Survival interaction consumes one Honeycomb and preserves shared state. Alternatively, combine **1 matching unwaxed item + 1 Honeycomb shapelessly → 1 waxed item**; all 32 such recipes are checked. Waxing does not remove existing oxidation. [Wax map and use callback][wax] · [State copying][copy-state] · [Waxing recipes](#recipe-and-loot-evidence)

A placed **double slab** is one block state, so one Honeycomb waxes the pair and the waxed double-slab loot still returns two slabs. A door's other half follows its changed material through the shared door-neighbor update. [Wax callback][wax] · [Slab-state preservation][copy-state] · [Waxed slab loot][loot-waxed_cut_copper_slab] · [Door synchronization][door-neighbor]

For an existing finish, use an **unbroken axe**:

- On a waxed variant, one use removes the wax and keeps the oxidation stage
- On an unwaxed Exposed/Weathered/Oxidized variant, one use moves back one stage
- An ordinary Unaffected, unwaxed block has no earlier stage to scrape

The operation retains shared state and requests **1 axe durability**. Wax removal returns no Honeycomb. See the canonical [axe conversion guidance](../mechanics/AxesAndHoes.md#scraping-copper-and-removing-wax) for tool rules and offhand Shield precedence. [Previous-stage map][weather] · [Wax inverse][wax] · [Axe callback][axe] · [Broken-item guard][use-guard]

**Hold secondary use, normally sneak, when waxing or scraping a Door or Trapdoor.** Its normal block interaction can otherwise open/close it before the item-use action runs. The same control also bypasses an offhand blocking item's interception of main-hand axe use. [Default block-use routing][block-use] · [Server interaction order][server-use] · [Axe offhand check][axe]

## Lightning cleaning

At the beginning of a server-side lightning strike, the cleaning handler checks the **block at the strike position**. If it is unwaxed weathering copper, that block resets directly to its first, Unaffected stage, keeping shared properties. Waxed construction does not enter this path. [Active strike callback][lightning-start] · [Strike position and reset][lightning] · [First-state conversion][weather] · [Natural lightning creation][thunder-spawn]

The strike then starts **3–5 random walks**, each with **1–8 steps**. At a step it samples up to 10 positions in the surrounding 3 × 3 × 3 cube, chooses the first eligible weathering-copper block, and removes one stage if a previous stage exists. A walk stops if it finds none. It can revisit blocks, and an already-Unaffected block can still be selected, so this is not a uniform radius-based cleanup or a guaranteed count of changed blocks. Waxed blocks are not eligible selections. [Random cleaning walks][lightning]

When lightning targets an unwaxed rod, **the rod itself** is the initial weathering block. The guaranteed reset is not redirected to its supporting copper block; nearby construction is reached only through the random walks. A **waxed rod** still has the rod's strike response but does not start this copper-cleaning routine. Wax a finished copper build if you want its color protected from these cleaning paths. [Rod target position][rod-target] · [Rod registrations][rod-register] · [Unwaxed rod weathering type][rod-weather] · [Rod strike response][rod-power] · [Actual cleaning target][lightning]

## Full blocks

Plain full Copper, Cut Copper, and Chiseled Copper use full-block collision, with no directional placement state and no waterlogging property. They remain placed without a supporting block. Oxidation and wax change their finish/variant, not their shape. [Registrations][registry-full] · [Cut/chiseled implementations][registry-shapes] · [Default shape and survival][collision]

## Slabs and stairs

Cut Copper Slabs use bottom, top, or double states. Combining requires the **identical slab item**, including oxidation stage and wax state: a waxed slab cannot merge with an unwaxed one, nor an Exposed slab with a Weathered slab. The resulting double slab clears waterlogging and rejects further waterlogging. Slab oxidation, waxing, or scraping preserves whether it is single or double. [Slab placement/replacement][slabs] · [Slab weathering][aging-slab] · [State preservation][copy-state]

Cut Copper Stairs use the shared clicked-face/height placement and inner/outer-corner rules. Different stages or wax states can form corners together because the corner test uses compatible stair directions and halves, not matching material. For the basic placement controls, see [slabs](WoodConstruction.md#slabs) and [stairs](WoodConstruction.md#stairs). [Active stair placement and corner check][stairs] · [Copper stair implementation][aging-stair]

All covered single slabs and stairs can be waterlogged. Placement into source Water sets the stored state; a Water Bucket can fill an eligible placed block. Bucket water evaporates in an ultrawarm dimension. Scraping or waxing preserves the stored water. [Slab water rules][slabs] · [Stair water rules][stairs-water] · [Water storage][water] · [Water registrations][fluids] · [Bucket restrictions][bucket]

## Grates

Copper Grates have **full-block collision**: the visible holes do not let entities pass through. They use the transparent-block light behavior, allow skylight through, and are registered with no light emission. They are explicitly **not redstone-conducting blocks**; do not use one in place of a solid conductor in a circuit. All eight variants share these properties. [Grate registrations][registry-grates] · [Transparent behavior][transparent] · [Default collision][collision] · [Light-block calculation][light] · [Default emission][defaults] · [Signal lookup][signal]

Grates can hold water and remain placed without continuing support. Placement recognizes source Water, and bucket filling/collection follows the shared waterlogged-block handler. Oxidation and wax preserve the waterlogged state. [Waterlogged grate base][water-grate] · [Copper grate callback][aging-grate] · [Water handling][water] · [State preservation][copy-state]

## Doors and trapdoors

**Every oxidation stage, waxed or unwaxed, opens by hand and responds to redstone.** Fully Oxidized doors/trapdoors do not lose those controls. The current Copper block-set type enables both hand interaction and Wind Charge opening. [Copper block-set settings][copper-type] · [All registrations][registry-doors]

| Form | Placement/support | Water |
| --- | --- | --- |
| Door | 1 item places a two-block-high door; its bottom needs a sturdy upper face below, and the upper destination must be replaceable. Removing support or either half removes the remaining unsupported half. Facing/hinge follow the shared door placement rules | No waterlogged state |
| Trapdoor | A 3/16-block-thick panel, horizontal when closed and vertical when open; clicked face and height select its placement. No continuing attachment support is required | Can be waterlogged; toggling preserves its water |

[Door placement and hinge][door-place] · [Door shape][door-shape] · [Door support/use][door-use] · [Door synchronization][door-neighbor] · [Trapdoor placement and water updates][trapdoor] · [Default support][collision]

Power at placement opens the block. Later changes in detected power set open to match the new power state; Doors check both halves. Manual use toggles open without rewriting the stored powered flag, so a steady signal does not prevent hand operation. The next detected power change reapplies the power-driven state. These controls work across wax and oxidation conversions because shared properties are copied. [Door interaction/power][door-use] · [Trapdoor interaction/power][trapdoor] · [Conversion state][copy-state]

A Wind Charge burst can toggle an **unpowered** copper Door or Trapdoor that it affects; the Door callback acts only through its lower half. Powered blocks reject that burst-toggle path. This is the active trigger-explosion route, separate from ordinary mining or destructive-explosion loot. [Wind Charge explosion][wind] · [Burst calculator][wind-calc] · [Block resistance handling][wind-resistance] · [Immune blocks][wind-immune] · [Server trigger mapping][wind-server] · [Block-hit dispatch][explosion-hit] · [Trigger conditions][trigger] · [Door burst callback][door-wind] · [Trapdoor burst callback][trapdoor-wind]

## Mining and collection

All 64 blocks have **hardness 3 and blast resistance 6**, and all are in the pickaxe-mineable tag. **Doors are the exception to the drop-tool requirement**:

| Form | Ordinary Survival collection requirement | Result |
| --- | --- | --- |
| Full/Cut/Chiseled Copper, Grate, Stairs, Trapdoor | Unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe | 1 matching current variant |
| Single Cut Copper Slab | Same pickaxe requirement | 1 matching slab |
| Double Cut Copper Slab | Same pickaxe requirement | 2 matching slabs |
| Copper Door | No correct-tool requirement; a pickaxe is still the efficient tool | 1 matching door for the assembly, from the lower-half loot entry |

Wooden and Golden Pickaxes do not satisfy the other forms' bundled tier requirement. A broken retained pickaxe also fails their correct-tool check. Holding an axe for scraping does not make it the harvesting tool for those blocks. See [Mining](../mechanics/Mining.md) for the shared speed/drop system. [Full properties][registry-full] · [Shape properties][registry-shapes] · [Door/trapdoor properties][registry-doors] · [Grate properties][registry-grates] · [Pickaxe tag][mineable-pickaxe] · [Stone-tier targets][needs_stone_tool] · [Wood exclusions][incorrect_for_wooden_tool] · [Gold exclusions][incorrect_for_gold_tool] · [Material assignment][tool] · [Pickaxe family][pickaxe] · [Broken drop guard][broken-drop] · [Harvest eligibility][harvest] · [Active harvest][harvest-call]

The loot keeps the current oxidation and wax variant. Silk Touch and Fortune add no alternative result or extra yield to these 64 tables, and Silk Touch does not bypass the block's tool gate. Slabs have explosion quantity decay; other tables use explosion-survival conditions. Normal mining yields above therefore do not promise the same result from explosions. [Exact loot tables](#recipe-and-loot-evidence) · [Harvest gate][harvest]

## Recipe and loot evidence

The checked inventory contains **43 shaped recipes, 32 shapeless waxing recipes, 64 stonecutting recipes, and 64 loot tables**. A dash means no bundled production recipe for that exact unwaxed variant; its placed-state conversion is described above. “Stone: full” uses the matching plain full-copper family; “Stone: cut” uses matching Cut Copper.

??? info "Exact recipes and loot for all 64 structural copper IDs"

    | Exact ID | Production recipes | Loot |
    | --- | --- | --- |
    | `minecraft:copper_block` | [Craft][r-copper_block] | [Loot][loot-copper_block] |
    | `minecraft:cut_copper` | [Craft][r-cut_copper] · [Stone: full][r-cut_copper_from_copper_block_stonecutting] | [Loot][loot-cut_copper] |
    | `minecraft:chiseled_copper` | [Craft][r-chiseled_copper] · [Stone: full][r-chiseled_copper_from_copper_block_stonecutting] · [Stone: cut][r-chiseled_copper_from_cut_copper_stonecutting] | [Loot][loot-chiseled_copper] |
    | `minecraft:copper_grate` | [Craft][r-copper_grate] · [Stone: full][r-copper_grate_from_copper_block_stonecutting] | [Loot][loot-copper_grate] |
    | `minecraft:cut_copper_slab` | [Craft][r-cut_copper_slab] · [Stone: full][r-cut_copper_slab_from_copper_block_stonecutting] · [Stone: cut][r-cut_copper_slab_from_cut_copper_stonecutting] | [Loot][loot-cut_copper_slab] |
    | `minecraft:cut_copper_stairs` | [Craft][r-cut_copper_stairs] · [Stone: full][r-cut_copper_stairs_from_copper_block_stonecutting] · [Stone: cut][r-cut_copper_stairs_from_cut_copper_stonecutting] | [Loot][loot-cut_copper_stairs] |
    | `minecraft:copper_door` | [Craft][r-copper_door] | [Loot][loot-copper_door] |
    | `minecraft:copper_trapdoor` | [Craft][r-copper_trapdoor] | [Loot][loot-copper_trapdoor] |
    | `minecraft:exposed_copper` | — | [Loot][loot-exposed_copper] |
    | `minecraft:exposed_cut_copper` | [Craft][r-exposed_cut_copper] · [Stone: full][r-exposed_cut_copper_from_exposed_copper_stonecutting] | [Loot][loot-exposed_cut_copper] |
    | `minecraft:exposed_chiseled_copper` | [Craft][r-exposed_chiseled_copper] · [Stone: cut][r-exposed_chiseled_copper_from_exposed_cut_copper_stonecutting] · [Stone: full][r-exposed_chiseled_copper_from_exposed_copper_stonecutting] | [Loot][loot-exposed_chiseled_copper] |
    | `minecraft:exposed_copper_grate` | [Craft][r-exposed_copper_grate] · [Stone: full][r-exposed_copper_grate_from_exposed_copper_stonecutting] | [Loot][loot-exposed_copper_grate] |
    | `minecraft:exposed_cut_copper_slab` | [Craft][r-exposed_cut_copper_slab] · [Stone: full][r-exposed_cut_copper_slab_from_exposed_copper_stonecutting] · [Stone: cut][r-exposed_cut_copper_slab_from_exposed_cut_copper_stonecutting] | [Loot][loot-exposed_cut_copper_slab] |
    | `minecraft:exposed_cut_copper_stairs` | [Craft][r-exposed_cut_copper_stairs] · [Stone: cut][r-exposed_cut_copper_stairs_from_exposed_cut_copper_stonecutting] · [Stone: full][r-exposed_cut_copper_stairs_from_exposed_copper_stonecutting] | [Loot][loot-exposed_cut_copper_stairs] |
    | `minecraft:exposed_copper_door` | — | [Loot][loot-exposed_copper_door] |
    | `minecraft:exposed_copper_trapdoor` | — | [Loot][loot-exposed_copper_trapdoor] |
    | `minecraft:weathered_copper` | — | [Loot][loot-weathered_copper] |
    | `minecraft:weathered_cut_copper` | [Craft][r-weathered_cut_copper] · [Stone: full][r-weathered_cut_copper_from_weathered_copper_stonecutting] | [Loot][loot-weathered_cut_copper] |
    | `minecraft:weathered_chiseled_copper` | [Craft][r-weathered_chiseled_copper] · [Stone: cut][r-weathered_chiseled_copper_from_weathered_cut_copper_stonecutting] · [Stone: full][r-weathered_chiseled_copper_from_weathered_copper_stonecutting] | [Loot][loot-weathered_chiseled_copper] |
    | `minecraft:weathered_copper_grate` | [Craft][r-weathered_copper_grate] · [Stone: full][r-weathered_copper_grate_from_weathered_copper_stonecutting] | [Loot][loot-weathered_copper_grate] |
    | `minecraft:weathered_cut_copper_slab` | [Craft][r-weathered_cut_copper_slab] · [Stone: cut][r-weathered_cut_copper_slab_from_weathered_cut_copper_stonecutting] · [Stone: full][r-weathered_cut_copper_slab_from_weathered_copper_stonecutting] | [Loot][loot-weathered_cut_copper_slab] |
    | `minecraft:weathered_cut_copper_stairs` | [Craft][r-weathered_cut_copper_stairs] · [Stone: cut][r-weathered_cut_copper_stairs_from_weathered_cut_copper_stonecutting] · [Stone: full][r-weathered_cut_copper_stairs_from_weathered_copper_stonecutting] | [Loot][loot-weathered_cut_copper_stairs] |
    | `minecraft:weathered_copper_door` | — | [Loot][loot-weathered_copper_door] |
    | `minecraft:weathered_copper_trapdoor` | — | [Loot][loot-weathered_copper_trapdoor] |
    | `minecraft:oxidized_copper` | — | [Loot][loot-oxidized_copper] |
    | `minecraft:oxidized_cut_copper` | [Craft][r-oxidized_cut_copper] · [Stone: full][r-oxidized_cut_copper_from_oxidized_copper_stonecutting] | [Loot][loot-oxidized_cut_copper] |
    | `minecraft:oxidized_chiseled_copper` | [Craft][r-oxidized_chiseled_copper] · [Stone: cut][r-oxidized_chiseled_copper_from_oxidized_cut_copper_stonecutting] · [Stone: full][r-oxidized_chiseled_copper_from_oxidized_copper_stonecutting] | [Loot][loot-oxidized_chiseled_copper] |
    | `minecraft:oxidized_copper_grate` | [Craft][r-oxidized_copper_grate] · [Stone: full][r-oxidized_copper_grate_from_oxidized_copper_stonecutting] | [Loot][loot-oxidized_copper_grate] |
    | `minecraft:oxidized_cut_copper_slab` | [Craft][r-oxidized_cut_copper_slab] · [Stone: cut][r-oxidized_cut_copper_slab_from_oxidized_cut_copper_stonecutting] · [Stone: full][r-oxidized_cut_copper_slab_from_oxidized_copper_stonecutting] | [Loot][loot-oxidized_cut_copper_slab] |
    | `minecraft:oxidized_cut_copper_stairs` | [Craft][r-oxidized_cut_copper_stairs] · [Stone: full][r-oxidized_cut_copper_stairs_from_oxidized_copper_stonecutting] · [Stone: cut][r-oxidized_cut_copper_stairs_from_oxidized_cut_copper_stonecutting] | [Loot][loot-oxidized_cut_copper_stairs] |
    | `minecraft:oxidized_copper_door` | — | [Loot][loot-oxidized_copper_door] |
    | `minecraft:oxidized_copper_trapdoor` | — | [Loot][loot-oxidized_copper_trapdoor] |
    | `minecraft:waxed_copper_block` | [Wax][r-waxed_copper_block_from_honeycomb] | [Loot][loot-waxed_copper_block] |
    | `minecraft:waxed_cut_copper` | [Craft][r-waxed_cut_copper] · [Wax][r-waxed_cut_copper_from_honeycomb] · [Stone: full][r-waxed_cut_copper_from_waxed_copper_block_stonecutting] | [Loot][loot-waxed_cut_copper] |
    | `minecraft:waxed_chiseled_copper` | [Wax][r-waxed_chiseled_copper_from_honeycomb] · [Craft][r-waxed_chiseled_copper] · [Stone: cut][r-waxed_chiseled_copper_from_waxed_cut_copper_stonecutting] · [Stone: full][r-waxed_chiseled_copper_from_waxed_copper_block_stonecutting] | [Loot][loot-waxed_chiseled_copper] |
    | `minecraft:waxed_copper_grate` | [Wax][r-waxed_copper_grate_from_honeycomb] · [Craft][r-waxed_copper_grate] · [Stone: full][r-waxed_copper_grate_from_waxed_copper_block_stonecutting] | [Loot][loot-waxed_copper_grate] |
    | `minecraft:waxed_cut_copper_slab` | [Craft][r-waxed_cut_copper_slab] · [Wax][r-waxed_cut_copper_slab_from_honeycomb] · [Stone: cut][r-waxed_cut_copper_slab_from_waxed_cut_copper_stonecutting] · [Stone: full][r-waxed_cut_copper_slab_from_waxed_copper_block_stonecutting] | [Loot][loot-waxed_cut_copper_slab] |
    | `minecraft:waxed_cut_copper_stairs` | [Craft][r-waxed_cut_copper_stairs] · [Wax][r-waxed_cut_copper_stairs_from_honeycomb] · [Stone: full][r-waxed_cut_copper_stairs_from_waxed_copper_block_stonecutting] · [Stone: cut][r-waxed_cut_copper_stairs_from_waxed_cut_copper_stonecutting] | [Loot][loot-waxed_cut_copper_stairs] |
    | `minecraft:waxed_copper_door` | [Wax][r-waxed_copper_door_from_honeycomb] | [Loot][loot-waxed_copper_door] |
    | `minecraft:waxed_copper_trapdoor` | [Wax][r-waxed_copper_trapdoor_from_honeycomb] | [Loot][loot-waxed_copper_trapdoor] |
    | `minecraft:waxed_exposed_copper` | [Wax][r-waxed_exposed_copper_from_honeycomb] | [Loot][loot-waxed_exposed_copper] |
    | `minecraft:waxed_exposed_cut_copper` | [Craft][r-waxed_exposed_cut_copper] · [Wax][r-waxed_exposed_cut_copper_from_honeycomb] · [Stone: full][r-waxed_exposed_cut_copper_from_waxed_exposed_copper_stonecutting] | [Loot][loot-waxed_exposed_cut_copper] |
    | `minecraft:waxed_exposed_chiseled_copper` | [Craft][r-waxed_exposed_chiseled_copper] · [Wax][r-waxed_exposed_chiseled_copper_from_honeycomb] · [Stone: cut][r-waxed_exposed_chiseled_copper_from_waxed_exposed_cut_copper_stonecutting] · [Stone: full][r-waxed_exposed_chiseled_copper_from_waxed_exposed_copper_stonecutting] | [Loot][loot-waxed_exposed_chiseled_copper] |
    | `minecraft:waxed_exposed_copper_grate` | [Craft][r-waxed_exposed_copper_grate] · [Wax][r-waxed_exposed_copper_grate_from_honeycomb] · [Stone: full][r-waxed_exposed_copper_grate_from_waxed_exposed_copper_stonecutting] | [Loot][loot-waxed_exposed_copper_grate] |
    | `minecraft:waxed_exposed_cut_copper_slab` | [Wax][r-waxed_exposed_cut_copper_slab_from_honeycomb] · [Craft][r-waxed_exposed_cut_copper_slab] · [Stone: full][r-waxed_exposed_cut_copper_slab_from_waxed_exposed_copper_stonecutting] · [Stone: cut][r-waxed_exposed_cut_copper_slab_from_waxed_exposed_cut_copper_stonecutting] | [Loot][loot-waxed_exposed_cut_copper_slab] |
    | `minecraft:waxed_exposed_cut_copper_stairs` | [Wax][r-waxed_exposed_cut_copper_stairs_from_honeycomb] · [Craft][r-waxed_exposed_cut_copper_stairs] · [Stone: cut][r-waxed_exposed_cut_copper_stairs_from_waxed_exposed_cut_copper_stonecutting] · [Stone: full][r-waxed_exposed_cut_copper_stairs_from_waxed_exposed_copper_stonecutting] | [Loot][loot-waxed_exposed_cut_copper_stairs] |
    | `minecraft:waxed_exposed_copper_door` | [Wax][r-waxed_exposed_copper_door_from_honeycomb] | [Loot][loot-waxed_exposed_copper_door] |
    | `minecraft:waxed_exposed_copper_trapdoor` | [Wax][r-waxed_exposed_copper_trapdoor_from_honeycomb] | [Loot][loot-waxed_exposed_copper_trapdoor] |
    | `minecraft:waxed_weathered_copper` | [Wax][r-waxed_weathered_copper_from_honeycomb] | [Loot][loot-waxed_weathered_copper] |
    | `minecraft:waxed_weathered_cut_copper` | [Craft][r-waxed_weathered_cut_copper] · [Wax][r-waxed_weathered_cut_copper_from_honeycomb] · [Stone: full][r-waxed_weathered_cut_copper_from_waxed_weathered_copper_stonecutting] | [Loot][loot-waxed_weathered_cut_copper] |
    | `minecraft:waxed_weathered_chiseled_copper` | [Wax][r-waxed_weathered_chiseled_copper_from_honeycomb] · [Craft][r-waxed_weathered_chiseled_copper] · [Stone: full][r-waxed_weathered_chiseled_copper_from_waxed_weathered_copper_stonecutting] · [Stone: cut][r-waxed_weathered_chiseled_copper_from_waxed_weathered_cut_copper_stonecutting] | [Loot][loot-waxed_weathered_chiseled_copper] |
    | `minecraft:waxed_weathered_copper_grate` | [Wax][r-waxed_weathered_copper_grate_from_honeycomb] · [Craft][r-waxed_weathered_copper_grate] · [Stone: full][r-waxed_weathered_copper_grate_from_waxed_weathered_copper_stonecutting] | [Loot][loot-waxed_weathered_copper_grate] |
    | `minecraft:waxed_weathered_cut_copper_slab` | [Craft][r-waxed_weathered_cut_copper_slab] · [Wax][r-waxed_weathered_cut_copper_slab_from_honeycomb] · [Stone: cut][r-waxed_weathered_cut_copper_slab_from_waxed_weathered_cut_copper_stonecutting] · [Stone: full][r-waxed_weathered_cut_copper_slab_from_waxed_weathered_copper_stonecutting] | [Loot][loot-waxed_weathered_cut_copper_slab] |
    | `minecraft:waxed_weathered_cut_copper_stairs` | [Craft][r-waxed_weathered_cut_copper_stairs] · [Wax][r-waxed_weathered_cut_copper_stairs_from_honeycomb] · [Stone: cut][r-waxed_weathered_cut_copper_stairs_from_waxed_weathered_cut_copper_stonecutting] · [Stone: full][r-waxed_weathered_cut_copper_stairs_from_waxed_weathered_copper_stonecutting] | [Loot][loot-waxed_weathered_cut_copper_stairs] |
    | `minecraft:waxed_weathered_copper_door` | [Wax][r-waxed_weathered_copper_door_from_honeycomb] | [Loot][loot-waxed_weathered_copper_door] |
    | `minecraft:waxed_weathered_copper_trapdoor` | [Wax][r-waxed_weathered_copper_trapdoor_from_honeycomb] | [Loot][loot-waxed_weathered_copper_trapdoor] |
    | `minecraft:waxed_oxidized_copper` | [Wax][r-waxed_oxidized_copper_from_honeycomb] | [Loot][loot-waxed_oxidized_copper] |
    | `minecraft:waxed_oxidized_cut_copper` | [Wax][r-waxed_oxidized_cut_copper_from_honeycomb] · [Craft][r-waxed_oxidized_cut_copper] · [Stone: full][r-waxed_oxidized_cut_copper_from_waxed_oxidized_copper_stonecutting] | [Loot][loot-waxed_oxidized_cut_copper] |
    | `minecraft:waxed_oxidized_chiseled_copper` | [Craft][r-waxed_oxidized_chiseled_copper] · [Wax][r-waxed_oxidized_chiseled_copper_from_honeycomb] · [Stone: cut][r-waxed_oxidized_chiseled_copper_from_waxed_oxidized_cut_copper_stonecutting] · [Stone: full][r-waxed_oxidized_chiseled_copper_from_waxed_oxidized_copper_stonecutting] | [Loot][loot-waxed_oxidized_chiseled_copper] |
    | `minecraft:waxed_oxidized_copper_grate` | [Craft][r-waxed_oxidized_copper_grate] · [Wax][r-waxed_oxidized_copper_grate_from_honeycomb] · [Stone: full][r-waxed_oxidized_copper_grate_from_waxed_oxidized_copper_stonecutting] | [Loot][loot-waxed_oxidized_copper_grate] |
    | `minecraft:waxed_oxidized_cut_copper_slab` | [Wax][r-waxed_oxidized_cut_copper_slab_from_honeycomb] · [Craft][r-waxed_oxidized_cut_copper_slab] · [Stone: cut][r-waxed_oxidized_cut_copper_slab_from_waxed_oxidized_cut_copper_stonecutting] · [Stone: full][r-waxed_oxidized_cut_copper_slab_from_waxed_oxidized_copper_stonecutting] | [Loot][loot-waxed_oxidized_cut_copper_slab] |
    | `minecraft:waxed_oxidized_cut_copper_stairs` | [Craft][r-waxed_oxidized_cut_copper_stairs] · [Wax][r-waxed_oxidized_cut_copper_stairs_from_honeycomb] · [Stone: full][r-waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_copper_stonecutting] · [Stone: cut][r-waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_cut_copper_stonecutting] | [Loot][loot-waxed_oxidized_cut_copper_stairs] |
    | `minecraft:waxed_oxidized_copper_door` | [Wax][r-waxed_oxidized_copper_door_from_honeycomb] | [Loot][loot-waxed_oxidized_copper_door] |
    | `minecraft:waxed_oxidized_copper_trapdoor` | [Wax][r-waxed_oxidized_copper_trapdoor_from_honeycomb] | [Loot][loot-waxed_oxidized_copper_trapdoor] |

## Sources and verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`. The scope includes every selected registry entry, all 139 production recipes, all 64 loot tables, relevant tool tags, 32 waxing pairs, 24 oxidation steps, and the active placement, random-tick, tool-use, and lightning callbacks. No in-game crafting, aging, scraping, lightning, waterlogging, power, or mining test was run. Data packs can change recipes, tags, and loot; random ticks, server rules, and placement context affect results. Structures, trades, copper mobs, and the excluded copper devices were not reviewed as acquisition or use guides.

The two unaffected full-block unpacking recipes were rechecked on **2026-10-04** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; the waxed recipe already exists at the original source pin. This is a documentation correction, not a gameplay change.

Related: [Blocks](Blocks.md) · [Copper catalog](catalog/copper.md) · [Stonecutter](Stonecutter.md) · [Copper Ingot](../items/CopperIngot.md) · [Honeycomb](../items/Honeycomb.md) · [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Mining](../mechanics/Mining.md) · [Wood construction](WoodConstruction.md)

[registry-full]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L6097-L6116
[registry-shapes]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L6125-L6238
[registry-doors]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L6239-L6313
[registry-grates]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L6314-L6354
[items]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/Items.java
[weather]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[neighbor-aging]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/ChangeOverTimeBlock.java#L9-L55
[manhattan]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/core/BlockPos.java#L294-L345
[random-dispatch]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L510
[copy-state]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Block.java#L512-L525
[defaults]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1034
[aging-full]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java
[aging-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopperSlabBlock.java
[aging-stair]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopperStairBlock.java
[aging-door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopperDoorBlock.java
[aging-trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopperTrapDoorBlock.java
[aging-grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringCopperGrateBlock.java
[wax]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[axe]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[use-guard]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L365
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[server-use]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L394
[block-use]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
[harvest]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/Item.java#L435-L437
[cutter-menu]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L61-L77
[shaped]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[slabs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L127
[stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/StairBlock.java#L33-L164
[stairs-water]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/StairBlock.java#L212-L220
[door-place]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L141-L199
[door-neighbor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L87-L108
[door-use]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L201-L245
[door-shape]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L43-L85
[trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L40-L193
[copper-type]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L47-L64
[water-grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WaterloggedTransparentBlock.java#L19-L66
[transparent]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/TransparentBlock.java#L12-L37
[collision]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[light]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L296-L301
[signal]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/SignalGetter.java#L65-L81
[water]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[fluids]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/material/Fluids.java#L7-L11
[bucket]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L140
[lightning-start]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/LightningBolt.java#L69-L99
[lightning]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/LightningBolt.java#L148-L210
[thunder-spawn]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerLevel.java#L519-L548
[rod-target]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerLevel.java#L588-L604
[rod-register]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L6485-L6519
[rod-weather]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WeatheringLightningRodBlock.java#L11-L40
[rod-power]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L84-L99
[wind]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java#L55-L72
[wind-calc]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java#L22-L25
[wind-resistance]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/SimpleExplosionDamageCalculator.java#L25-L38
[wind-immune]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/blocks_wind_charge_explosions.json
[wind-server]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1167
[explosion-hit]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/ServerExplosion.java#L207-L217
[trigger]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/ServerExplosion.java#L291-L299
[door-wind]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L110-L121
[trapdoor-wind]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L102-L110
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[incorrect_for_wooden_tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[incorrect_for_gold_tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[loot-copper_block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/copper_block.json
[r-copper_block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/copper_block.json
[loot-chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/chiseled_copper.json
[r-chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/chiseled_copper.json
[r-chiseled_copper_from_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_copper_from_copper_block_stonecutting.json
[r-chiseled_copper_from_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_copper_from_cut_copper_stonecutting.json
[loot-copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/copper_door.json
[r-copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/copper_door.json
[loot-copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/copper_grate.json
[r-copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/copper_grate.json
[r-copper_grate_from_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/copper_grate_from_copper_block_stonecutting.json
[loot-copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/copper_trapdoor.json
[r-copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/copper_trapdoor.json
[loot-cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/cut_copper.json
[r-cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/cut_copper.json
[r-cut_copper_from_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_from_copper_block_stonecutting.json
[loot-cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/cut_copper_slab.json
[r-cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/cut_copper_slab.json
[r-cut_copper_slab_from_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_slab_from_copper_block_stonecutting.json
[r-cut_copper_slab_from_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_slab_from_cut_copper_stonecutting.json
[loot-cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/cut_copper_stairs.json
[r-cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/cut_copper_stairs.json
[r-cut_copper_stairs_from_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_stairs_from_copper_block_stonecutting.json
[r-cut_copper_stairs_from_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_stairs_from_cut_copper_stonecutting.json
[loot-exposed_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_chiseled_copper.json
[r-exposed_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/exposed_chiseled_copper.json
[r-exposed_chiseled_copper_from_exposed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_chiseled_copper_from_exposed_cut_copper_stonecutting.json
[r-exposed_chiseled_copper_from_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_chiseled_copper_from_exposed_copper_stonecutting.json
[loot-exposed_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper.json
[loot-exposed_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_door.json
[loot-exposed_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_grate.json
[r-exposed_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/exposed_copper_grate.json
[r-exposed_copper_grate_from_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_copper_grate_from_exposed_copper_stonecutting.json
[loot-exposed_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_trapdoor.json
[loot-exposed_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_cut_copper.json
[r-exposed_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/exposed_cut_copper.json
[r-exposed_cut_copper_from_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_cut_copper_from_exposed_copper_stonecutting.json
[loot-exposed_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_cut_copper_slab.json
[r-exposed_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/exposed_cut_copper_slab.json
[r-exposed_cut_copper_slab_from_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_cut_copper_slab_from_exposed_copper_stonecutting.json
[r-exposed_cut_copper_slab_from_exposed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_cut_copper_slab_from_exposed_cut_copper_stonecutting.json
[loot-exposed_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/exposed_cut_copper_stairs.json
[r-exposed_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/exposed_cut_copper_stairs.json
[r-exposed_cut_copper_stairs_from_exposed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_cut_copper_stairs_from_exposed_cut_copper_stonecutting.json
[r-exposed_cut_copper_stairs_from_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/exposed_cut_copper_stairs_from_exposed_copper_stonecutting.json
[loot-oxidized_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_chiseled_copper.json
[r-oxidized_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/oxidized_chiseled_copper.json
[r-oxidized_chiseled_copper_from_oxidized_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_chiseled_copper_from_oxidized_cut_copper_stonecutting.json
[r-oxidized_chiseled_copper_from_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_chiseled_copper_from_oxidized_copper_stonecutting.json
[loot-oxidized_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper.json
[loot-oxidized_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_door.json
[loot-oxidized_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_grate.json
[r-oxidized_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/oxidized_copper_grate.json
[r-oxidized_copper_grate_from_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_copper_grate_from_oxidized_copper_stonecutting.json
[loot-oxidized_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_trapdoor.json
[loot-oxidized_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_cut_copper.json
[r-oxidized_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/oxidized_cut_copper.json
[r-oxidized_cut_copper_from_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_from_oxidized_copper_stonecutting.json
[loot-oxidized_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_cut_copper_slab.json
[r-oxidized_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/oxidized_cut_copper_slab.json
[r-oxidized_cut_copper_slab_from_oxidized_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_slab_from_oxidized_cut_copper_stonecutting.json
[r-oxidized_cut_copper_slab_from_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_slab_from_oxidized_copper_stonecutting.json
[loot-oxidized_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/oxidized_cut_copper_stairs.json
[r-oxidized_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/oxidized_cut_copper_stairs.json
[r-oxidized_cut_copper_stairs_from_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_stairs_from_oxidized_copper_stonecutting.json
[r-oxidized_cut_copper_stairs_from_oxidized_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_stairs_from_oxidized_cut_copper_stonecutting.json
[loot-waxed_copper_block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_block.json
[r-waxed_copper_block_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_block_from_honeycomb.json
[loot-waxed_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_chiseled_copper.json
[r-waxed_chiseled_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_chiseled_copper_from_honeycomb.json
[r-waxed_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_chiseled_copper.json
[r-waxed_chiseled_copper_from_waxed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_chiseled_copper_from_waxed_cut_copper_stonecutting.json
[r-waxed_chiseled_copper_from_waxed_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_chiseled_copper_from_waxed_copper_block_stonecutting.json
[loot-waxed_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_door.json
[r-waxed_copper_door_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_door_from_honeycomb.json
[loot-waxed_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_grate.json
[r-waxed_copper_grate_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_grate_from_honeycomb.json
[r-waxed_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_grate.json
[r-waxed_copper_grate_from_waxed_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_copper_grate_from_waxed_copper_block_stonecutting.json
[loot-waxed_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_trapdoor.json
[r-waxed_copper_trapdoor_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_trapdoor_from_honeycomb.json
[loot-waxed_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_cut_copper.json
[r-waxed_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper.json
[r-waxed_cut_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper_from_honeycomb.json
[r-waxed_cut_copper_from_waxed_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_from_waxed_copper_block_stonecutting.json
[loot-waxed_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_cut_copper_slab.json
[r-waxed_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper_slab.json
[r-waxed_cut_copper_slab_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper_slab_from_honeycomb.json
[r-waxed_cut_copper_slab_from_waxed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_slab_from_waxed_cut_copper_stonecutting.json
[r-waxed_cut_copper_slab_from_waxed_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_slab_from_waxed_copper_block_stonecutting.json
[loot-waxed_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_cut_copper_stairs.json
[r-waxed_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper_stairs.json
[r-waxed_cut_copper_stairs_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper_stairs_from_honeycomb.json
[r-waxed_cut_copper_stairs_from_waxed_copper_block_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_stairs_from_waxed_copper_block_stonecutting.json
[r-waxed_cut_copper_stairs_from_waxed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_stairs_from_waxed_cut_copper_stonecutting.json
[loot-waxed_exposed_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_chiseled_copper.json
[r-waxed_exposed_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_chiseled_copper.json
[r-waxed_exposed_chiseled_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_chiseled_copper_from_honeycomb.json
[r-waxed_exposed_chiseled_copper_from_waxed_exposed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_chiseled_copper_from_waxed_exposed_cut_copper_stonecutting.json
[r-waxed_exposed_chiseled_copper_from_waxed_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_chiseled_copper_from_waxed_exposed_copper_stonecutting.json
[loot-waxed_exposed_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper.json
[r-waxed_exposed_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_from_honeycomb.json
[loot-waxed_exposed_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_door.json
[r-waxed_exposed_copper_door_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_door_from_honeycomb.json
[loot-waxed_exposed_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_grate.json
[r-waxed_exposed_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_grate.json
[r-waxed_exposed_copper_grate_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_grate_from_honeycomb.json
[r-waxed_exposed_copper_grate_from_waxed_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_copper_grate_from_waxed_exposed_copper_stonecutting.json
[loot-waxed_exposed_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_trapdoor.json
[r-waxed_exposed_copper_trapdoor_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_trapdoor_from_honeycomb.json
[loot-waxed_exposed_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_cut_copper.json
[r-waxed_exposed_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper.json
[r-waxed_exposed_cut_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_from_honeycomb.json
[r-waxed_exposed_cut_copper_from_waxed_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_from_waxed_exposed_copper_stonecutting.json
[loot-waxed_exposed_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_cut_copper_slab.json
[r-waxed_exposed_cut_copper_slab_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_slab_from_honeycomb.json
[r-waxed_exposed_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_slab.json
[r-waxed_exposed_cut_copper_slab_from_waxed_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_slab_from_waxed_exposed_copper_stonecutting.json
[r-waxed_exposed_cut_copper_slab_from_waxed_exposed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_slab_from_waxed_exposed_cut_copper_stonecutting.json
[loot-waxed_exposed_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_cut_copper_stairs.json
[r-waxed_exposed_cut_copper_stairs_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_stairs_from_honeycomb.json
[r-waxed_exposed_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_stairs.json
[r-waxed_exposed_cut_copper_stairs_from_waxed_exposed_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_stairs_from_waxed_exposed_cut_copper_stonecutting.json
[r-waxed_exposed_cut_copper_stairs_from_waxed_exposed_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_stairs_from_waxed_exposed_copper_stonecutting.json
[loot-waxed_oxidized_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_chiseled_copper.json
[r-waxed_oxidized_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_chiseled_copper.json
[r-waxed_oxidized_chiseled_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_chiseled_copper_from_honeycomb.json
[r-waxed_oxidized_chiseled_copper_from_waxed_oxidized_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_chiseled_copper_from_waxed_oxidized_cut_copper_stonecutting.json
[r-waxed_oxidized_chiseled_copper_from_waxed_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_chiseled_copper_from_waxed_oxidized_copper_stonecutting.json
[loot-waxed_oxidized_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper.json
[r-waxed_oxidized_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_from_honeycomb.json
[loot-waxed_oxidized_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_door.json
[r-waxed_oxidized_copper_door_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_door_from_honeycomb.json
[loot-waxed_oxidized_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_grate.json
[r-waxed_oxidized_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_grate.json
[r-waxed_oxidized_copper_grate_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_grate_from_honeycomb.json
[r-waxed_oxidized_copper_grate_from_waxed_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_copper_grate_from_waxed_oxidized_copper_stonecutting.json
[loot-waxed_oxidized_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_trapdoor.json
[r-waxed_oxidized_copper_trapdoor_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_trapdoor_from_honeycomb.json
[loot-waxed_oxidized_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_cut_copper.json
[r-waxed_oxidized_cut_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper_from_honeycomb.json
[r-waxed_oxidized_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper.json
[r-waxed_oxidized_cut_copper_from_waxed_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_from_waxed_oxidized_copper_stonecutting.json
[loot-waxed_oxidized_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_cut_copper_slab.json
[r-waxed_oxidized_cut_copper_slab_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper_slab_from_honeycomb.json
[r-waxed_oxidized_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper_slab.json
[r-waxed_oxidized_cut_copper_slab_from_waxed_oxidized_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_slab_from_waxed_oxidized_cut_copper_stonecutting.json
[r-waxed_oxidized_cut_copper_slab_from_waxed_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_slab_from_waxed_oxidized_copper_stonecutting.json
[loot-waxed_oxidized_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_cut_copper_stairs.json
[r-waxed_oxidized_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper_stairs.json
[r-waxed_oxidized_cut_copper_stairs_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper_stairs_from_honeycomb.json
[r-waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_copper_stonecutting.json
[r-waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_cut_copper_stonecutting.json
[loot-waxed_weathered_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_chiseled_copper.json
[r-waxed_weathered_chiseled_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_chiseled_copper_from_honeycomb.json
[r-waxed_weathered_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_chiseled_copper.json
[r-waxed_weathered_chiseled_copper_from_waxed_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_chiseled_copper_from_waxed_weathered_copper_stonecutting.json
[r-waxed_weathered_chiseled_copper_from_waxed_weathered_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_chiseled_copper_from_waxed_weathered_cut_copper_stonecutting.json
[loot-waxed_weathered_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper.json
[r-waxed_weathered_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_from_honeycomb.json
[loot-waxed_weathered_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_door.json
[r-waxed_weathered_copper_door_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_door_from_honeycomb.json
[loot-waxed_weathered_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_grate.json
[r-waxed_weathered_copper_grate_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_grate_from_honeycomb.json
[r-waxed_weathered_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_grate.json
[r-waxed_weathered_copper_grate_from_waxed_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_copper_grate_from_waxed_weathered_copper_stonecutting.json
[loot-waxed_weathered_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_trapdoor.json
[r-waxed_weathered_copper_trapdoor_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_trapdoor_from_honeycomb.json
[loot-waxed_weathered_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_cut_copper.json
[r-waxed_weathered_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper.json
[r-waxed_weathered_cut_copper_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper_from_honeycomb.json
[r-waxed_weathered_cut_copper_from_waxed_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_cut_copper_from_waxed_weathered_copper_stonecutting.json
[loot-waxed_weathered_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_cut_copper_slab.json
[r-waxed_weathered_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper_slab.json
[r-waxed_weathered_cut_copper_slab_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper_slab_from_honeycomb.json
[r-waxed_weathered_cut_copper_slab_from_waxed_weathered_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_cut_copper_slab_from_waxed_weathered_cut_copper_stonecutting.json
[r-waxed_weathered_cut_copper_slab_from_waxed_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_cut_copper_slab_from_waxed_weathered_copper_stonecutting.json
[loot-waxed_weathered_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_cut_copper_stairs.json
[r-waxed_weathered_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper_stairs.json
[r-waxed_weathered_cut_copper_stairs_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper_stairs_from_honeycomb.json
[r-waxed_weathered_cut_copper_stairs_from_waxed_weathered_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_cut_copper_stairs_from_waxed_weathered_cut_copper_stonecutting.json
[r-waxed_weathered_cut_copper_stairs_from_waxed_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_cut_copper_stairs_from_waxed_weathered_copper_stonecutting.json
[loot-weathered_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_chiseled_copper.json
[r-weathered_chiseled_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/weathered_chiseled_copper.json
[r-weathered_chiseled_copper_from_weathered_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_chiseled_copper_from_weathered_cut_copper_stonecutting.json
[r-weathered_chiseled_copper_from_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_chiseled_copper_from_weathered_copper_stonecutting.json
[loot-weathered_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper.json
[loot-weathered_copper_door]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_door.json
[loot-weathered_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_grate.json
[r-weathered_copper_grate]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/weathered_copper_grate.json
[r-weathered_copper_grate_from_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_copper_grate_from_weathered_copper_stonecutting.json
[loot-weathered_copper_trapdoor]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_trapdoor.json
[loot-weathered_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_cut_copper.json
[r-weathered_cut_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/weathered_cut_copper.json
[r-weathered_cut_copper_from_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_from_weathered_copper_stonecutting.json
[loot-weathered_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_cut_copper_slab.json
[r-weathered_cut_copper_slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/weathered_cut_copper_slab.json
[r-weathered_cut_copper_slab_from_weathered_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_slab_from_weathered_cut_copper_stonecutting.json
[r-weathered_cut_copper_slab_from_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_slab_from_weathered_copper_stonecutting.json
[loot-weathered_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/weathered_cut_copper_stairs.json
[r-weathered_cut_copper_stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/weathered_cut_copper_stairs.json
[r-weathered_cut_copper_stairs_from_weathered_cut_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_stairs_from_weathered_cut_copper_stonecutting.json
[r-weathered_cut_copper_stairs_from_weathered_copper_stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_stairs_from_weathered_copper_stonecutting.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/copper_ingot.json

[unpack-waxed]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/copper_ingot_from_waxed_copper_block.json
