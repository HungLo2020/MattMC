# Pickaxes and Shovels

**Pickaxes and shovels are registered in seven materials, including Copper.** Pickaxes target the pickaxe mining tag; shovels target the shovel tag and can also make paths or extinguish campfires. Each is a single-item equipment stack. Use [Mining tools and drops](Mining.md) for the shared material chart and tier rules, and [Durability and repair](Durability.md) for the retained broken-item system. [Registrations][items] · [Tool families][families] · [Stack size][item-stacksize]

## Choose and make a tool

The item pages give each tool's exact recipe, durability, speed, and repair material:

| Material | Pickaxe | Shovel | Accepted crafting and Anvil repair material |
| --- | --- | --- | --- |
| Wood | [Wooden Pickaxe](../items/WoodenPickaxe.md) | [Wooden Shovel](../items/WoodenShovel.md) | Planks in the checked tag |
| Stone | [Stone Pickaxe](../items/StonePickaxe.md) | [Stone Shovel](../items/StoneShovel.md) | Cobblestone, Blackstone, or Cobbled Deepslate |
| Copper | [Copper Pickaxe](../items/CopperPickaxe.md) | [Copper Shovel](../items/CopperShovel.md) | Copper Ingot |
| Iron | [Iron Pickaxe](../items/IronPickaxe.md) | [Iron Shovel](../items/IronShovel.md) | Iron Ingot |
| Gold | [Golden Pickaxe](../items/GoldenPickaxe.md) | [Golden Shovel](../items/GoldenShovel.md) | Gold Ingot |
| Diamond | [Diamond Pickaxe](../items/DiamondPickaxe.md) | [Diamond Shovel](../items/DiamondShovel.md) | Diamond |
| Netherite | [Netherite Pickaxe](../items/NetheritePickaxe.md) | [Netherite Shovel](../items/NetheriteShovel.md) | Netherite Ingot for repair; creation uses a Diamond tool and Smithing |

The Wood tag accepts Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Pale Oak, Bamboo, Crimson, and Warped Planks. It does not currently include [Pewen Planks](../blocks/Pewen.md#recipes-and-tools-that-need-caution). Stone's three inputs do not include ordinary Stone. A Wooden or Stone Pickaxe can mix accepted materials across its three head slots because each ingredient slot tests the tag independently. [Wood materials][repair-wooden] · [Planks][planks] · [Stone materials][repair-stone] · [Pattern matching][pattern]

For Copper through Diamond, the same material tag used by the recipe supplies the Anvil repair ingredient. Netherite uses its own repair tag. Material repair works through the stack's repair component; it is separate from [crafting-grid, Grindstone, and Anvil donor repair](Durability.md#choose-a-repair-method). [Materials][materials] · [Copper][repair-copper] · [Iron][repair-iron] · [Gold][repair-gold] · [Diamond][repair-diamond] · [Netherite][repair-netherite] · [Repair lookup][repair-check] · [Repair matching][repairable] · [Anvil callback][anvil]

### Netherite upgrades

The [Smithing guide](../smithing/Smithing.md#example-upgrade-a-diamond-pickaxe) owns the Diamond Pickaxe upgrade instructions. The matching [Netherite Shovel recipe](../items/NetheriteShovel.md#obtaining) upgrades a Diamond Shovel. Both consume a Netherite Upgrade Template and Netherite Ingot with the base tool. Saved changes such as damage, names, and enchantments carry across; **the upgrade does not reset damage to zero**. See Smithing for component-preservation details. [Pickaxe recipe][netherite-pickaxe] · [Shovel recipe][netherite-shovel] · [Input consumption][smith-menu] · [Transform callback][smith-transform] · [Result application][transmute-apply] · [Saved component changes][stack-patch]

Dropped Netherite tools resist the checked fire-damage category, including lava. This follows their item property through the dropped-item damage check; it is not protection from every cause of item loss. [Registration][items] · [Fire property][fire-property] · [Stack check][fire-check] · [Resistance matching][fire-component] · [Fire damage types][fire-tag] · [Dropped-item callback][item-damage]

## Mining and collecting

The matching mining tag supplies the tool's material speed. Other blocks use its baseline **1.0** tool speed before player modifiers. Drop eligibility is checked separately, with material exclusions evaluated before the matching-family rule. A fast Golden Pickaxe still fails the tier check for Iron Ore. For ore choices and Silk Touch/Fortune branches, use [Ore resources](../blocks/OreResources.md), [Stone](../blocks/Stone.md), and the individual block guides. [Tool rules][materials] · [Speed and drop matching][tool] · [Player checks][player] · [Pickaxe targets][pickaxe-tag] · [Gold exclusions][deny-gold]

The checked shovel tag includes Dirt and related soils, Sand and Red Sand, Gravel, Clay, Snow, Soul Sand/Soil, Mud, Muddy Mangrove Roots, all 16 Concrete Powders, and suspicious Sand/Gravel. **None of its current entries occurs in any material's incorrect-tool tag.** Thus an unbroken Wooden Shovel and Netherite Shovel meet the same family/tier checks on those entries; they differ in speed and durability. This does not guarantee the same loot from every block. [Shovel targets][shovel-tag] · [Wood exclusions][deny-wooden] · [Stone][deny-stone] · [Copper][deny-copper] · [Iron][deny-iron] · [Gold][deny-gold] · [Diamond][deny-diamond] · [Netherite][deny-netherite]

For example, a full [Snow Block](../items/SnowBlock.md) requires a correct tool: an unbroken shovel gives **4 Snowballs**, or the Snow Block itself with Silk Touch. Conversely, suspicious Sand and Gravel have no normal block-loot pools. Use the [Brush's archaeology route](../items/Brush.md#archaeology-basics) to recover their contents rather than digging them up. [Snow requirement][snow-block] · [Snow loot][snow-loot] · [Suspicious Sand loot][suspicious-sand] · [Suspicious Gravel loot][suspicious-gravel]

## Making paths with a shovel

Use an unbroken shovel on the **top or a side** of one of these six blocks while the block immediately above is **air**:

- Grass Block
- Dirt
- Podzol
- Coarse Dirt
- Mycelium
- Rooted Dirt

The block becomes [Dirt Path](../items/DirtPath.md), and the successful Survival interaction requests **1 durability**. Clicking the bottom face does nothing through this handler. Tall grass, snow, water, or any other non-air block above prevents flattening even if it looks passable. Farmland and Mud are not entries in this conversion map. All seven shovel materials use the same handler. [Shovel conversion and wear][shovel] · [Broken-use guard][use-guard]

A path is 15/16 of a block high. If a later upper-neighbor update places a block considered solid above it, it schedules conversion back to Dirt; a Fence Gate is an explicit exception. Path survival is a separate rule from the stricter air-above requirement for making one with a shovel. [Path shape and survival][path]

## Extinguishing campfires

Use an unbroken shovel on the **top or a side of a lit [Campfire](../items/Campfire.md) or [Soul Campfire](../items/SoulCampfire.md)**. The shovel turns its lit state off and requests **1 durability** in Survival. This action does not require air above the campfire. An unlit campfire or a bottom-face click gives no shovel action or wear. [Both registrations][campfires] · [Extinguish callback][shovel] · [Item-use guard][use-guard]

The ordinary interaction reaches the shovel after the campfire's block handler declines the held item. Pickaxes are registered as ordinary Items with tool properties; their own block-use method has no equivalent path or extinguish action. [Campfire interaction][campfire-use] · [Server interaction routing][use-entry] · [Tool registrations][items] · [Default item use][pickaxe-use]

## Wear and broken tools

For ordinary Survival mining, each successfully mined block with nonzero hardness requests **1 durability**, including blocks outside the tool's preferred family. A zero-hardness block skips that mining wear. The normal post-hit weapon callback requests **2 durability** for either family. Enchantments and infinite-material abilities can alter these requests. [Tool wear values][materials] · [Mining callback][mining-wear] · [Hit callback][hit-mine] · [Player hit routing][player-hit] · [Damage processing][broken]

At maximum damage the normal path **retains the tool as a broken stack**. Its tool-speed accessor becomes 1.0, its correct-tool check fails, and a broken shovel cannot perform the two item-use actions above. Keep it for an appropriate [repair method](Durability.md). The last successful mining use checks eligibility and copies the loot tool before applying its wear, so reaching the limit on that use does not retroactively remove an otherwise eligible drop. [Broken state and retention][broken] · [Use and speed guards][use-guard] · [Correct-tool guard][hit-mine] · [Mining callback order][last-use]

## Recycling and fuel

These selected recipes consume **one tool** and return **one nugget**, regardless of whether the input is a pickaxe or shovel:

| Tool material | Output | Furnace | Blast Furnace | Recipe experience |
| --- | --- | ---: | ---: | ---: |
| Copper | [Copper Nugget](../items/CopperNugget.md) | 200 ticks | 100 ticks | 0.1 |
| Iron | [Iron Nugget](../items/IronNugget.md) | 200 ticks | 100 ticks | 0.1 |
| Gold | [Gold Nugget](../items/GoldNugget.md) | 200 ticks | 100 ticks | 0.1 |

[Smelt Copper][copper-smelting] · [Blast Copper][copper-blasting] · [Smelt Iron][iron-smelting] · [Blast Iron][iron-blasting] · [Smelt Gold][gold-smelting] · [Blast Gold][gold-blasting]

These ingredient checks match item identity, without a remaining-durability requirement; a retained broken tool is accepted too. Processing consumes the equipment rather than repairing it. [Ingredient matching][ingredient] · [Cooking match][cook-match] · [Input consumption][cook-consume]

A Wooden Pickaxe or Wooden Shovel instead supplies **200 default furnace fuel ticks**, including when damaged or broken because the fuel lookup uses item identity. See [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) and [experience handling](../blocks/Furnace.md#experience-and-troubleshooting) for the shared fuel and fractional-XP rules. [Default fuel lookup][fuel-default] · [Wooden tool entries][fuel-tools]

Related: [Mining](Mining.md) · [Durability](Durability.md) · [Smithing](../smithing/Smithing.md) · [Enchanting](../enchanting/Enchanting.md) · [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4efea14485328238a3a4981162c8a7e8400914b4`. No in-game crafting, mining, repair, block-use, or wear-out test was run. These claims cover the checked registrations, callbacks, recipes, and tags; data packs and later builds can change them.

[items]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Items.java#L1306-L1342
[families]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Item.java#L431-L449
[item-stacksize]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Item.java#L386-L390
[repair-wooden]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json
[planks]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/planks.json
[repair-stone]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[materials]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[repair-copper]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/copper_tool_materials.json
[repair-iron]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/iron_tool_materials.json
[repair-gold]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/gold_tool_materials.json
[repair-diamond]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json
[repair-netherite]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[repair-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[repairable]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/enchantment/Repairable.java#L14-L25
[anvil]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L133-L151
[netherite-pickaxe]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/smithing/netherite_pickaxe_smithing.json
[netherite-shovel]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/smithing/netherite_shovel_smithing.json
[smith-menu]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L70-L93
[smith-transform]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L33
[transmute-apply]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
[stack-patch]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[fire-property]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Item.java#L402-L403
[fire-check]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1103
[fire-component]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/component/DamageResistant.java#L12-L24
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[item-damage]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L282
[tool]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[player]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[deny-gold]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[deny-wooden]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[deny-stone]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[deny-copper]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
[deny-iron]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[deny-diamond]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[deny-netherite]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json
[snow-block]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/Blocks.java#L1943-L1945
[snow-loot]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/loot_table/blocks/snow_block.json
[suspicious-sand]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[suspicious-gravel]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/loot_table/blocks/suspicious_gravel.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ShovelItem.java#L20-L73
[use-guard]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[path]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/DirtPathBlock.java#L44-L74
[campfires]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/Blocks.java#L5423-L5446
[campfire-use]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L89-L106
[use-entry]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L387
[pickaxe-use]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Item.java#L164-L166
[mining-wear]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/Item.java#L236-L251
[hit-mine]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
[broken]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[last-use]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L291
[copper-smelting]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/smelting/copper_nugget_from_smelting.json
[copper-blasting]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/blasting/copper_nugget_from_blasting.json
[iron-smelting]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/smelting/iron_nugget_from_smelting.json
[iron-blasting]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/blasting/iron_nugget_from_blasting.json
[gold-smelting]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/smelting/gold_nugget_from_smelting.json
[gold-blasting]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/resources/data/minecraft/recipe/blasting/gold_nugget_from_blasting.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L60-L65
[cook-match]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/item/crafting/SingleItemRecipe.java#L33-L35
[cook-consume]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L237-L265
[fuel-default]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L26-L39
[fuel-tools]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L75-L81
[player-hit]: https://github.com/HungLo2020/MattMC/blob/4efea14485328238a3a4981162c8a7e8400914b4/src/main/java/net/minecraft/world/entity/player/Player.java#L1090-L1110
