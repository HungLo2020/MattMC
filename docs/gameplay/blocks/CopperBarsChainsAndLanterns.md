# Copper Bars, Chains, and Lanterns

Use Copper Bars for narrow connected partitions, Copper Chains for straight decorative runs, and Copper Lanterns for steady **light level 15**. Each family has four oxidation stages and a waxed version of every stage: **24 registered blocks with 24 matching inventory items**. These are separate from Iron Bars, Iron Chain, and ordinary/Soul Lanterns. [Bars and Chain registration][reg-bars-chain] · [Lantern registration][reg-lantern] · [Eight-form registration][family] · [Items][items]

## Getting the pieces

The three base recipes use a Crafting Table. Ingredients name specific items, so Iron materials or an ordinary Torch do not substitute:

| Make | Layout | Output |
| --- | --- | ---: |
| Copper Bars | Six Copper Ingots in two full rows | 16 [recipe][recipe-copper_bars] |
| Copper Chain | Copper Nugget above a Copper Ingot, with another Copper Nugget below | 1 [recipe][recipe-copper_chain] |
| Copper Lantern | Eight Copper Nuggets around one Copper Torch | 1 [recipe][recipe-copper_lantern] |

For the torch ingredient, follow [Copper Torches](CopperLighting.md#copper-torches). Each of the twelve waxed forms has its own shapeless recipe: **one matching unwaxed item plus one Honeycomb makes one waxed item at the same oxidation stage**. The [variant tables](#exact-variants-and-resource-evidence) link these recipes. Exposed, Weathered, and Oxidized unwaxed pieces can result from the [placed aging process](#aging-and-preserving-a-finish); the reviewed recipes do not directly craft those nine unwaxed variants. [Weathering map][weather]

All 24 are ordinary category-listed inventory entries. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can insert them in **Survival as well as Creative**, subject to enabled features, an empty cursor, and available inventory space. This browser route does not establish a natural source or make an unsupported placement valid. See the [inventory family page](../items/CopperFixtures.md) for the item names. [Building-category entries][building-list] · [Functional-category entries][functional-list]

## Copper Bars: connections and support

Copper Bars connect horizontally to other blocks using the bar implementation. That includes Iron/Copper Bars and glass panes, walls, and eligible sturdy block faces. The sturdy-face route excludes leaves, barriers, pumpkins, melons, and Shulker Boxes. A neighbor change updates the corresponding connection; it does not create an opening control. See [Iron Bars](IronFixtures.md#iron-bars) for the shared connection layout. [Bar placement and updates][bars] · [Connection exceptions][exceptions]

Their collision follows the narrow center post and connected arms. They need no continuing floor or side support: removing an adjacent block changes the connection where applicable, rather than causing the bars to fall. Neither the base bar class nor its Copper subclass replaces the default survival rule. Bars and Chain emit no block light. [Collision implementation][cross] · [Default survival/light][defaults] · [Copper bar subclass][bars-aging] · [Registration][reg-bars-chain]

## Copper Chain: orientation and support

Place a Copper Chain against a top/bottom face for a vertical chain, or a side face for a horizontal chain along that face's axis. Its block state has an **axis**, rather than the four directional connections of bars. It is a narrow solid piece; it does not automatically join into branching shapes. See [Iron Chain](IronFixtures.md#iron-chain) for the shared layout. [Chain placement and shape][chain] · [Clicked-face axis][axis]

Copper Chain has no continuing support check. Removing the block you placed it against does not, by itself, make it fall. This applies to all four weathering stages and their waxed forms because they share the same placement and survival path. [Chain implementation][chain] · [Copper chain subclass][chain-aging] · [Default survival][defaults]

## Copper Lantern: floor or ceiling light

A Copper Lantern can stand on a block with **center support on its top face**, or hang beneath a block with **center support on its bottom face**. It has no horizontal wall-mounted form. The support check excludes the underside of blocks in the unstable-bottom-center tag, currently Fence Gates. Removing its valid floor or ceiling makes it break on the corresponding support-side update. [Lantern placement and updates][lantern] · [Center-support test][support] · [Support exclusion tag][unstable]

Every Copper Lantern variant emits **15**, including Oxidized, waxed, hanging, and waterlogged forms. Oxidation changes its finish without dimming this light. There is no fuel slot or redstone-controlled lit state in this implementation. Its body and handle have a small solid collision shape. For a light that responds to power, use a [Copper Bulb](CopperLighting.md#copper-bulbs) or [Redstone Lamp](RedstoneLamp.md). [Constant light registration][reg-lantern] · [Lantern states and shape][lantern]

The [ordinary/Soul Lantern guide](Lanterns.md#floor-and-ceiling-placement) explains the shared support mechanics; its Iron Nugget recipes and Librarian trade do not describe Copper Lantern acquisition.

## Waterlogging

All 24 forms can be waterlogged. Placement detects **source Water**, specifically `Fluids.WATER`; it does not treat every flowing-water fluid type as the same condition. A Water Bucket can fill a placed fixture, and an empty Bucket can take out that stored source. These operations retain the block while its applicable support conditions remain valid. Waterlogged blocks schedule normal water updates. [Bars][bars] · [Bars' water state][cross] · [Chain][chain] · [Lantern][lantern] · [Bucket interactions][water]

Oxidation, waxing, and axe conversions copy shared block-state properties. That preserves bar connections, Chain axis, Lantern hanging position, and waterlogging across the matching family variants. A mined item does not store those placement properties; placing it again selects its state normally. [State copying][copy] · [Conversion maps][weather] · [Waxing][wax] · [Axe conversion][axe] · [Per-variant loot](#exact-variants-and-resource-evidence)

## Aging and preserving a finish

Unwaxed pieces advance from **Copper → Exposed → Weathered → Oxidized** through the normal Copper weathering path. All three families are explicitly included in that path; only a variant with a later stage receives its weathering random ticks. Waxed forms use the non-weathering base classes. For the shared neighbor and spacing rules, see [Copper oxidation](CopperConstruction.md#oxidation-and-spacing). There is no fixed elapsed-time guarantee for a particular fixture. [Family maps][weather] · [Bars ticking][bars-aging] · [Chain ticking][chain-aging] · [Lantern ticking][lantern-aging] · [Aging selection][aging]

Use **one Honeycomb** on an unwaxed placed piece to wax its current stage. Waxing does not remove existing oxidation. Alternatively, use the matching [waxing recipe](#exact-variants-and-resource-evidence) before placing it. [Wax maps and interaction][wax]

Use an **unbroken axe** to change the finish: [Block-use guard][broken-use]

- On a waxed piece, one successful use removes the wax and keeps its oxidation stage
- On an unwaxed Exposed, Weathered, or Oxidized piece, one successful use moves back one stage
- On an unwaxed base Copper piece, there is no earlier stage to scrape

A successful axe conversion requests **one durability**, before enchantment and infinite-material handling. If an offhand blocking item intercepts main-hand axe use, hold secondary use, usually sneak, and try again. The [shared waxing and scraping guide](CopperConstruction.md#waxing-and-scraping) covers that interaction. [Axe routing and durability][axe] · [Durability handling][durability] · [Previous-stage map][weather]

Lightning's cleaning handler also recognizes the unwaxed Copper subclasses. When one is the block at the strike position, it resets to the base Copper stage and can start randomized nearby cleaning steps; nearby pieces are not guaranteed to change. Waxed forms are outside that weathering-class test. This is the active source path, not a promise that a Lightning Rod will clean a chosen fixture. See [lightning and Copper](CopperConstruction.md#lightning-cleaning). [Lightning cleaning][lightning] · [Weathering state conversion][weather]

## Collecting and moving fixtures

For ordinary Survival mining, use an **unbroken pickaxe of any standard material** on Copper Bars and Copper Chain, including Wooden or Golden Pickaxes. Those two families require a correct tool for their block drop. All eight variants of each family are in the pickaxe tag through their respective Bars/Chains tags, and none is in a bundled tier-exclusion tag. Hand mining or an unsuitable/broken tool does not meet this gate. [Registrations][reg-bars-chain] · [Nested mining tag][pickaxe] · [Bars tag][bars-tag] · [Chains tag][chains-tag] · [Tool rules][tools] · [Broken-tool guard][broken] · [Harvest gate][harvest-gate]

**Copper Lanterns can be recovered by hand.** Their registration omits the correct-tool requirement. All eight remain pickaxe-tagged through the Lanterns tag, so a pickaxe can speed collection; its speed role is separate from permission to get the drop. [Registration][reg-lantern] · [Lanterns tag][lanterns-tag] · [Player harvest check][harvest-gate]

Each of the 24 loot tables returns **one matching oxidation/wax variant** for normal eligible mining. Silk Touch is unnecessary and does not bypass a tool gate; Fortune adds no extra yield. Explosive destruction has an explosion-survival condition, so it is not guaranteed recovery. Item spawning still follows `doTileDrops`. [Per-variant tables](#exact-variants-and-resource-evidence) · [Mining dispatch][harvest] · [Drop spawning][drops]

The tier check includes [Wooden][tier-wooden], [Stone][tier-stone], [Copper][tier-copper], [Golden][tier-gold], [Iron][tier-iron], [Diamond][tier-diamond], and [Netherite][tier-netherite] tool exclusions and their nested tags. These findings are specific to these fixtures, not a rule for every Copper block. [Tool materials][tool-materials]

## Exact variants and resource evidence

Every ID below is both a block ID and its matching item ID in the `minecraft:` namespace. Standing/hanging Lantern positions, Chain axes, connections, and waterlogging are states, not additional IDs. Each row has a stable link for this variant and its own checked loot resource. **Oxidation** means placed aging rather than a direct recipe for that unwaxed stage. [Block helper][family] · [Item helper][item-family]

### Bars variants

| Block and item ID | Checked acquisition route | Matching block loot |
| --- | --- | --- |
| <a id="copper-bars"></a>`minecraft:copper_bars` | [Base recipe][recipe-copper_bars] | [Loot][loot-copper_bars] |
| <a id="exposed-copper-bars"></a>`minecraft:exposed_copper_bars` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-exposed_copper_bars] |
| <a id="oxidized-copper-bars"></a>`minecraft:oxidized_copper_bars` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-oxidized_copper_bars] |
| <a id="waxed-copper-bars"></a>`minecraft:waxed_copper_bars` | [Waxing recipe][recipe-waxed_copper_bars] | [Loot][loot-waxed_copper_bars] |
| <a id="waxed-exposed-copper-bars"></a>`minecraft:waxed_exposed_copper_bars` | [Waxing recipe][recipe-waxed_exposed_copper_bars] | [Loot][loot-waxed_exposed_copper_bars] |
| <a id="waxed-oxidized-copper-bars"></a>`minecraft:waxed_oxidized_copper_bars` | [Waxing recipe][recipe-waxed_oxidized_copper_bars] | [Loot][loot-waxed_oxidized_copper_bars] |
| <a id="waxed-weathered-copper-bars"></a>`minecraft:waxed_weathered_copper_bars` | [Waxing recipe][recipe-waxed_weathered_copper_bars] | [Loot][loot-waxed_weathered_copper_bars] |
| <a id="weathered-copper-bars"></a>`minecraft:weathered_copper_bars` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-weathered_copper_bars] |

### Chain variants

| Block and item ID | Checked acquisition route | Matching block loot |
| --- | --- | --- |
| <a id="copper-chain"></a>`minecraft:copper_chain` | [Base recipe][recipe-copper_chain] | [Loot][loot-copper_chain] |
| <a id="exposed-copper-chain"></a>`minecraft:exposed_copper_chain` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-exposed_copper_chain] |
| <a id="oxidized-copper-chain"></a>`minecraft:oxidized_copper_chain` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-oxidized_copper_chain] |
| <a id="waxed-copper-chain"></a>`minecraft:waxed_copper_chain` | [Waxing recipe][recipe-waxed_copper_chain] | [Loot][loot-waxed_copper_chain] |
| <a id="waxed-exposed-copper-chain"></a>`minecraft:waxed_exposed_copper_chain` | [Waxing recipe][recipe-waxed_exposed_copper_chain] | [Loot][loot-waxed_exposed_copper_chain] |
| <a id="waxed-oxidized-copper-chain"></a>`minecraft:waxed_oxidized_copper_chain` | [Waxing recipe][recipe-waxed_oxidized_copper_chain] | [Loot][loot-waxed_oxidized_copper_chain] |
| <a id="waxed-weathered-copper-chain"></a>`minecraft:waxed_weathered_copper_chain` | [Waxing recipe][recipe-waxed_weathered_copper_chain] | [Loot][loot-waxed_weathered_copper_chain] |
| <a id="weathered-copper-chain"></a>`minecraft:weathered_copper_chain` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-weathered_copper_chain] |

### Lantern variants

| Block and item ID | Checked acquisition route | Matching block loot |
| --- | --- | --- |
| <a id="copper-lantern"></a>`minecraft:copper_lantern` | [Base recipe][recipe-copper_lantern] | [Loot][loot-copper_lantern] |
| <a id="exposed-copper-lantern"></a>`minecraft:exposed_copper_lantern` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-exposed_copper_lantern] |
| <a id="oxidized-copper-lantern"></a>`minecraft:oxidized_copper_lantern` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-oxidized_copper_lantern] |
| <a id="waxed-copper-lantern"></a>`minecraft:waxed_copper_lantern` | [Waxing recipe][recipe-waxed_copper_lantern] | [Loot][loot-waxed_copper_lantern] |
| <a id="waxed-exposed-copper-lantern"></a>`minecraft:waxed_exposed_copper_lantern` | [Waxing recipe][recipe-waxed_exposed_copper_lantern] | [Loot][loot-waxed_exposed_copper_lantern] |
| <a id="waxed-oxidized-copper-lantern"></a>`minecraft:waxed_oxidized_copper_lantern` | [Waxing recipe][recipe-waxed_oxidized_copper_lantern] | [Loot][loot-waxed_oxidized_copper_lantern] |
| <a id="waxed-weathered-copper-lantern"></a>`minecraft:waxed_weathered_copper_lantern` | [Waxing recipe][recipe-waxed_weathered_copper_lantern] | [Loot][loot-waxed_weathered_copper_lantern] |
| <a id="weathered-copper-lantern"></a>`minecraft:weathered_copper_lantern` | [Oxidation](#aging-and-preserving-a-finish) | [Loot][loot-weathered_copper_lantern] |


## Sources and verification

Source-reviewed at `f9e4a4c96fcf7fb744bce869c085cdfab189810e` on **2026-10-02**. Traced the helper-created registrations into the active base-class placement/support callbacks, weathering/wax/axe/lightning maps, inventory category entries, and each variant's recipe, mining-tag, and loot data. No client placement, crafting, mining, waterlogging, oxidation timing, support-removal, or multiplayer browser test was run. Natural generation is not established by this review, and the presence of resource files alone does not certify successful runtime loading.

The 15 recipe advancement rewards use bare IDs while the corresponding recipe resources load with the `crafting/` prefix. This is an inactive unlock-data inconsistency: the current server's recipe-award method is a no-op. It does not demonstrate a manual-crafting problem. [Example unlock][unlock-example] · [Recipe loading][recipe-loader] · [Resource IDs][recipe-ids] · [Disabled recipe awards][recipe-awards]

Related: [Blocks](Blocks.md) · [Copper catalog](catalog/copper.md) · [Copper construction](CopperConstruction.md) · [Copper lighting](CopperLighting.md) · [Iron fixtures](IronFixtures.md) · [Lanterns and Soul Lanterns](Lanterns.md) · [Inventory family](../items/CopperFixtures.md)

[reg-bars-chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Blocks.java#L2310-L2328
[reg-lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Blocks.java#L5409-L5422
[family]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/WeatheringCopperBlocks.java#L14-L47
[item-family]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/WeatheringCopperItems.java#L12-L23
[items]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/Items.java
[building-list]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L450-L541
[functional-list]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1047
[bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/IronBarsBlock.java
[exceptions]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Block.java#L243-L250
[cross]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[bars-aging]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java
[chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/ChainBlock.java
[chain-aging]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/WeatheringCopperChainBlock.java
[axis]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L53-L56
[lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/LanternBlock.java
[lantern-aging]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/WeatheringLanternBlock.java
[support]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Block.java#L323-L328
[unstable]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/unstable_bottom_center.json
[water]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[copy]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Block.java#L512-L522
[weather]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[aging]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/ChangeOverTimeBlock.java
[wax]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/HoneycombItem.java#L27-L114
[axe]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L116
[lightning]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[bars-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/bars.json
[chains-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/chains.json
[lanterns-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/lanterns.json
[broken]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[drops]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Block.java#L358-L414
[tools]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[tool-materials]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L29
[harvest-gate]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L271-L294
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L84
[recipe-ids]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/FileToIdConverter.java#L23-L38
[json-loader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener.java#L59-L74
[tier-wooden]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[tier-stone]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[tier-copper]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
[tier-gold]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[tier-iron]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[tier-diamond]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[tier-netherite]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json
[recipe-copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/copper_bars.json#L1-L15
[loot-copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/copper_bars.json#L1-L21
[loot-exposed_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_bars.json#L1-L21
[loot-oxidized_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_bars.json#L1-L21
[recipe-waxed_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_bars_from_honeycomb.json#L1-L13
[loot-waxed_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_bars.json#L1-L21
[recipe-waxed_exposed_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_bars_from_honeycomb.json#L1-L13
[loot-waxed_exposed_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_bars.json#L1-L21
[recipe-waxed_oxidized_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_bars_from_honeycomb.json#L1-L13
[loot-waxed_oxidized_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_bars.json#L1-L21
[recipe-waxed_weathered_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_bars_from_honeycomb.json#L1-L13
[loot-waxed_weathered_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_bars.json#L1-L21
[loot-weathered_copper_bars]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_bars.json#L1-L21
[recipe-copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/copper_chain.json#L1-L17
[loot-copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/copper_chain.json#L1-L21
[loot-exposed_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_chain.json#L1-L21
[loot-oxidized_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_chain.json#L1-L21
[recipe-waxed_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_chain_from_honeycomb.json#L1-L13
[loot-waxed_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_chain.json#L1-L21
[recipe-waxed_exposed_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_chain_from_honeycomb.json#L1-L13
[loot-waxed_exposed_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_chain.json#L1-L21
[recipe-waxed_oxidized_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_chain_from_honeycomb.json#L1-L13
[loot-waxed_oxidized_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_chain.json#L1-L21
[recipe-waxed_weathered_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_chain_from_honeycomb.json#L1-L13
[loot-waxed_weathered_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_chain.json#L1-L21
[loot-weathered_copper_chain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_chain.json#L1-L21
[recipe-copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/copper_lantern.json#L1-L17
[loot-copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/copper_lantern.json#L1-L21
[loot-exposed_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_lantern.json#L1-L21
[loot-oxidized_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_lantern.json#L1-L21
[recipe-waxed_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_lantern_from_honeycomb.json#L1-L13
[loot-waxed_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_lantern.json#L1-L21
[recipe-waxed_exposed_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_lantern_from_honeycomb.json#L1-L13
[loot-waxed_exposed_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_lantern.json#L1-L21
[recipe-waxed_oxidized_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_lantern_from_honeycomb.json#L1-L13
[loot-waxed_oxidized_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_lantern.json#L1-L21
[recipe-waxed_weathered_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_lantern_from_honeycomb.json#L1-L13
[loot-waxed_weathered_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_lantern.json#L1-L21
[loot-weathered_copper_lantern]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_lantern.json#L1-L21
[unlock-example]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/advancement/recipes/decorations/copper_bars.json#L1-L32
[broken-use]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L365
[durability]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L473
[recipe-awards]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1470-L1486
