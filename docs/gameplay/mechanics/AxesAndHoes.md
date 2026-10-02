# Axes and Hoes

**Axes work on their tagged building materials and strip or restore supported blocks. Hoes work on their own mining targets, prepare soil, and trigger MattMC's crop-area harvesting.** Both families have Wood, Stone, Copper, Iron, Gold, Diamond, and Netherite variants. Their shared durability and mining-speed chart remains in [Mining](Mining.md); use the item pages below for exact recipes and repair ingredients. [Registrations][items] · [Family properties][families]

## Material and combat choices

These are the default player's **main-hand attack attributes with an unbroken tool**, before enchantments and other modifiers. Damage is measured in damage points. Attack speed controls the ordinary attack-charge interval; these numbers are not guaranteed damage per hit or damage per second. Attack charge and the target's defenses still matter. [Item values][items] · [Material additions][materials] · [Player damage base][player-base] · [Speed base][speed-base] · [Attack charge][attack] · [Charge interval][attack-delay]

| Material | Axe | Axe damage / attack speed | Hoe | Hoe damage / attack speed |
| --- | --- | --- | --- | --- |
| Wood | [Wooden Axe](../items/WoodenAxe.md) | 7 / 0.8 | [Wooden Hoe](../items/WoodenHoe.md) | 1 / 1.0 |
| Stone | [Stone Axe](../items/StoneAxe.md) | 9 / 0.8 | [Stone Hoe](../items/StoneHoe.md) | 1 / 2.0 |
| Copper | [Copper Axe](../items/CopperAxe.md) | 9 / 0.8 | [Copper Hoe](../items/CopperHoe.md) | 1 / 2.0 |
| Iron | [Iron Axe](../items/IronAxe.md) | 9 / 0.9 | [Iron Hoe](../items/IronHoe.md) | 1 / 3.0 |
| Gold | [Golden Axe](../items/GoldenAxe.md) | 7 / 1.0 | [Golden Hoe](../items/GoldenHoe.md) | 1 / 1.0 |
| Diamond | [Diamond Axe](../items/DiamondAxe.md) | 9 / 1.0 | [Diamond Hoe](../items/DiamondHoe.md) | 1 / 4.0 |
| Netherite | [Netherite Axe](../items/NetheriteAxe.md) | 10 / 1.0 | [Netherite Hoe](../items/NetheriteHoe.md) | 1 / 4.0 |

An unbroken axe also supplies a **5-second blocking-disable value**. When a player's default Shield blocks an eligible direct melee hit, the checked callbacks can apply a **100-tick cooldown** and stop blocking. Hoes supply zero for this property; a broken axe also returns zero. See [Shields](../items/Shield.md) for blocking conditions. [Axe/hoe properties][families] · [Blocked-hit routing][shield-hit] · [Player callback][shield-player] · [Broken-weapon guard][shield-weapon] · [Default Shield scale][shield-item] · [Cooldown][shield-cooldown] · [Tick conversion][shield-ticks]

## Mining targets and drops

Axe speed applies to the current axe-mineable tag, including ordinary logs, planks, and many wooden building blocks. Hoe speed applies to its separate tag, including the checked Leaves, Hay Bale, Dried Kelp Block, Nether/Warped Wart Blocks, Shroomlight, Sponge/Wet Sponge, Moss/Pale Moss, Target, and listed Sculk blocks. Use [Oak](../blocks/Oak.md), [Bamboo](../blocks/Bamboo.md), and individual block guides for actual loot. A hoe's faster leaf mining does not itself grant a leaf-block drop. [Axe targets][axe-tag] · [Hoe targets][hoe-tag] · [Speed and drop rules][tool]

**Neither checked family tag intersects any of the seven material-exclusion tags.** Within a family, material therefore changes speed and durability without changing those current targets' material-tier eligibility. This is distinct from the [pickaxe tier restrictions](Mining.md#tier-restrictions): a Diamond Axe or Hoe does not become a pickaxe for tool-gated ore. A matching family rule, the block's correct-tool requirement, and its loot table remain separate checks. [Material rule order][materials] · [Wood exclusions][deny-wooden] · [Stone][deny-stone] · [Copper][deny-copper] · [Iron][deny-iron] · [Gold][deny-gold] · [Diamond][deny-diamond] · [Netherite][deny-netherite] · [Player check][player-mining]

## Using an axe on blocks

A successful axe conversion requests **1 durability**. The handler tries stripping, then a previous copper-weathering stage, then wax removal. It does not require air above or exclude the bottom face. The ordinary broken-item guard still applies. [Axe callback][axe-use] · [Use guard][use-guard]

### Stripping wood and bamboo

The checked map contains **23 input blocks**:

- Logs and Wood for Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, and Pale Oak
- Stems and Hyphae for Crimson and Warped fungi
- Block of Bamboo

Each becomes its matching stripped block and retains its axis. The map does not include Pewen or Ancient wood; use the [Pewen guide](../blocks/Pewen.md#recipes-and-tools-that-need-caution) for its integration caveats. This is an in-place conversion, without a separate bark item in the handler. [Exact stripping pairs][axe-strip] · [Axis preservation][axe-axis] · [Conversion callback][axe-use]

### Scraping copper and removing wax

On a supported **unwaxed** copper block, each use reverses **one oxidation stage**. For example, Oxidized Copper becomes Weathered Copper, then Exposed Copper, then an ordinary Copper Block through three uses. Shared state properties are retained. The active maps also cover other registered copper shapes; they are not a rule for any block merely named “copper.” [Weathering pairs and previous-state lookup][copper] · [Grouped copper mappings][copper-groups]

On a supported **waxed** copper block, the first use removes the wax while retaining its oxidation stage. A later use can scrape that now-unwaxed block. Wax removal does not return Honeycomb. Neither operation produces a Copper item through this handler. [Wax map and inverse][wax] · [Axe conversion order][axe-use]

### When axe use is intercepted

With an axe in the main hand, an offhand item carrying the blocking component, such as a Shield, makes the axe yield unless **secondary use** is active. This check looks for the component, not whether that offhand item is currently raised or broken. Hold secondary use, normally the sneak control, or move the blocking item out of the offhand to reach the axe action. Secondary use also skips a block's ordinary use handler, which can otherwise consume the click. [Offhand test][axe-use] · [Server routing][server-use] · [Client routing][client-use]

## Tilling with a hoe

All seven hoes use the same conversion map. The [Farmland guide](../blocks/Farmland.md#making-farmland) owns soil preparation and watering advice; these are the input and control distinctions:

| Clicked block | Result | Conditions |
| --- | --- | --- |
| Grass Block, Dirt, Dirt Path | Farmland | Air immediately above; click a top or side face |
| Coarse Dirt | Dirt | Air immediately above; click a top or side face |
| Rooted Dirt | Dirt plus 1 Hanging Roots | Any clicked face; no air-above requirement |

A successful conversion requests **1 durability**. Coarse Dirt and Rooted Dirt need another suitable use on the resulting Dirt to reach Farmland. Rooted Dirt's item appears from the clicked face when block drops are enabled. Podzol and Mycelium are not in this hoe map. A broken hoe cannot perform these item-use conversions. [Hoe map, predicates, and wear][hoe] · [Root drop dispatch][roots-drop] · [Broken-use guard][use-guard]

## MattMC crop-area harvesting

Use a hoe normally on a **mature** Wheat, Carrot, Potato, or Beetroot crop. All seven materials select the same **3 × 3 horizontal area**: the clicked crop and mature plants of that exact crop type at the same height. Clicking an immature crop does not start the area harvest. For planting, mature loot, resetting to age 0, and Fortune behavior, use the canonical [Wheat controls](../blocks/Wheat.md#mattmc-harvesting-controls) and [Root crop controls](../blocks/RootCrops.md#mattmc-harvesting-controls). [Harvest callback][crop] · [Wheat registration][wheat] · [Root registrations][roots] · [Beetroot registration][beetroot] · [Carrot inheritance][carrot-class] · [Potato inheritance][potato-class] · [Beetroot inheritance][beetroot-class]

**Do not hold secondary use for this action:** it skips the crop's block handler. The hoe's own soil-tilling handler has no crop entry. This is a block-use harvest, separate from breaking a crop with the hoe. [Interaction routing][server-use] · [Hoe targets][hoe]

### Fully damaged hoes still reach this harvest path

The current crop handler checks only that the held item is a `HoeItem` and the clicked crop is mature. It runs **before** `ItemStack.useOn`, so the usual broken-item guard does not protect this path. In the inspected source, a retained broken hoe can still perform ordinary crop-area harvesting even though it cannot till or use its normal tool speed. Both client and server route the crop handler first. [Crop condition][crop] · [Server order][server-use] · [Client order][client-use] · [Generic guard][use-guard]

The handler passes the actual hoe into crop loot and applies durability only **after the whole batch**, equal to the number harvested. It does not stop partway through because the hoe has little durability left. Already-broken stacks skip additional ordinary wear. These are source-level exceptions, not an in-game test or a promise that other blocks treat broken tools the same way. [Loot context][crop-loot] · [Batch and wear order][crop] · [Broken-stack damage handling][broken]

## Wear, repair, and upgrading

Ordinary nonzero-hardness mining requests **1 durability**, including off-family blocks; zero-hardness mining skips that cost. The normal post-hit callback requests **2 durability** for either family. Enchantments and infinite-material abilities can alter ordinary wear. Tool conversions use their own one-point requests, while crop harvesting uses the batch count above. [Tool values][materials] · [Mining callback][mine-wear] · [Hit guard and wear][mine-guard] · [Player hit routing][hit-route]

At the durability limit, the normal damage path retains a broken stack. Its tool-speed accessor becomes 1.0 and its correct-tool check fails. Normal equipment updates omit its combat attributes, and the break callback removes its equipped modifiers. Keep useful tools for [repair](Durability.md#choose-a-repair-method); the crop exception above is a separate block handler. [Retention][broken] · [Speed guard][use-guard] · [Drop guard][mine-guard] · [Equipment checks][equipment] · [Break dispatch][break-route] · [Modifier removal][break-effects]

The item pages list accepted Anvil materials: tagged planks for Wood; Cobblestone, Blackstone, or Cobbled Deepslate for Stone; the matching ingot or gem for the other materials. The full repair formulas and component-retention rules remain in [Durability and repair](Durability.md). [Assigned repair tags][materials] · [Repair lookup][repair-check] · [Matching][repair-match] · [Anvil callback][anvil]

[Netherite Axe](../items/NetheriteAxe.md#obtaining) and [Netherite Hoe](../items/NetheriteHoe.md#obtaining) have verified Diamond-to-Netherite recipes. Follow [Smithing](../smithing/Smithing.md) for menu operation, template consumption, and preservation of saved damage and other changes. The variant pages own these two exact recipes.

## Recycling and fuel

Copper, Iron, and Golden Axes and Hoes are accepted by the corresponding **one-nugget recycling recipes**. Furnace recipes take 200 ticks and Blast Furnace recipes 100 ticks; each defines 0.1 experience. Processing consumes the whole tool, including a retained broken one because the ingredient test uses item identity. See [Furnace experience handling](../blocks/Furnace.md#experience-and-troubleshooting) for payout rules. [Smelt Copper][copper-smelting] · [Blast Copper][copper-blasting] · [Smelt Iron][iron-smelting] · [Blast Iron][iron-blasting] · [Smelt Gold][gold-smelting] · [Blast Gold][gold-blasting] · [Ingredient matching][ingredient] · [Cooking match][cook-match] · [Input consumption][cook-use]

A Wooden Axe or Wooden Hoe supplies **200 default furnace fuel ticks**. The lookup uses item identity, so wear does not reduce that value. Burning consumes the tool; see [Furnace fuel planning](../blocks/Furnace.md#fuel-planning). [Default lookup][fuel-default] · [Wooden tool entries][fuel-tools]

Related: [Mining](Mining.md) · [Durability](Durability.md) · [Farmland](../blocks/Farmland.md) · [Wheat](../blocks/Wheat.md) · [Root crops](../blocks/RootCrops.md) · [Smithing](../smithing/Smithing.md) · [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, conversion, crop-harvesting, or wear-out test was run. The guide describes checked source paths and bundled data; item components, game rules, data packs, and later builds can change behavior.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1309-L1348
[families]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L439-L445
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[attack]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L982
[attack-delay]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1723-L1731
[shield-hit]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1294-L1297
[shield-player]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L753-L760
[shield-weapon]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3665-L3672
[shield-item]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L2259-L2276
[shield-cooldown]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/BlocksAttacks.java#L87-L109
[shield-ticks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/BlocksAttacks.java#L124-L126
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[hoe-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[deny-wooden]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[deny-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[deny-copper]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
[deny-iron]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[deny-gold]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[deny-diamond]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[deny-netherite]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json
[player-mining]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656
[axe-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L117
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[axe-strip]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[axe-axis]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L131
[copper]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L18-L78
[copper-groups]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/WeatheringCopperBlocks.java#L49-L55
[wax]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/HoneycombItem.java#L25-L80
[server-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L387
[client-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L331-L375
[hoe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/HoeItem.java#L23-L88
[roots-drop]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Block.java#L395-L415
[crop]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/CropBlock.java#L208-L250
[wheat]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1270-L1277
[roots]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2714-L2723
[beetroot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L4276-L4279
[carrot-class]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/CarrotBlock.java
[potato-class]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/PotatoBlock.java
[beetroot-class]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java
[crop-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Block.java#L353-L383
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[mine-wear]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L236-L251
[mine-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
[hit-route]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1090-L1106
[equipment]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2668-L2683
[break-route]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L504-L508
[break-effects]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3553
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[repair-match]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/Repairable.java#L14-L25
[anvil]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L133-L151
[copper-smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/copper_nugget_from_smelting.json
[copper-blasting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/blasting/copper_nugget_from_blasting.json
[iron-smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/iron_nugget_from_smelting.json
[iron-blasting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/blasting/iron_nugget_from_blasting.json
[gold-smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/gold_nugget_from_smelting.json
[gold-blasting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/blasting/gold_nugget_from_blasting.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L60-L65
[cook-match]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/SingleItemRecipe.java#L33-L35
[cook-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L237-L265
[fuel-default]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L26-L39
[fuel-tools]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L77-L81
