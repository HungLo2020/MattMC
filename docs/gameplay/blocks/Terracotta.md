# Terracotta and Glazed Terracotta

**Terracotta** provides an uncolored building block and **16 dyed colors**. Smelting a dyed color makes its matching **Glazed Terracotta**, which has four horizontal orientations and push-only piston behavior. All 33 blocks require an **unbroken pickaxe** for ordinary Survival collection. [Uncolored registration][base-block] · [Dyed registrations][dyed-blocks] · [Glazed registrations][glazed-blocks] · [Pickaxe tag][pickaxe]

## Forms and colors

[Uncolored Terracotta](../items/Terracotta.md) is `minecraft:terracotta`. The block and item share this ID. It is not White Terracotta: white is one of the dyed forms below. Each linked page identifies that color's checked recipe and loot table. [Uncolored item][base-item] · [Dyed items][dyed-items] · [Glazed items][glazed-items]

| Color | Dyed block and item | Glazed block and item |
| --- | --- | --- |
| White | [`minecraft:white_terracotta`](../items/WhiteTerracotta.md) | [`minecraft:white_glazed_terracotta`](../items/WhiteGlazedTerracotta.md) |
| Orange | [`minecraft:orange_terracotta`](../items/OrangeTerracotta.md) | [`minecraft:orange_glazed_terracotta`](../items/OrangeGlazedTerracotta.md) |
| Magenta | [`minecraft:magenta_terracotta`](../items/MagentaTerracotta.md) | [`minecraft:magenta_glazed_terracotta`](../items/MagentaGlazedTerracotta.md) |
| Light Blue | [`minecraft:light_blue_terracotta`](../items/LightBlueTerracotta.md) | [`minecraft:light_blue_glazed_terracotta`](../items/LightBlueGlazedTerracotta.md) |
| Yellow | [`minecraft:yellow_terracotta`](../items/YellowTerracotta.md) | [`minecraft:yellow_glazed_terracotta`](../items/YellowGlazedTerracotta.md) |
| Lime | [`minecraft:lime_terracotta`](../items/LimeTerracotta.md) | [`minecraft:lime_glazed_terracotta`](../items/LimeGlazedTerracotta.md) |
| Pink | [`minecraft:pink_terracotta`](../items/PinkTerracotta.md) | [`minecraft:pink_glazed_terracotta`](../items/PinkGlazedTerracotta.md) |
| Gray | [`minecraft:gray_terracotta`](../items/GrayTerracotta.md) | [`minecraft:gray_glazed_terracotta`](../items/GrayGlazedTerracotta.md) |
| Light Gray | [`minecraft:light_gray_terracotta`](../items/LightGrayTerracotta.md) | [`minecraft:light_gray_glazed_terracotta`](../items/LightGrayGlazedTerracotta.md) |
| Cyan | [`minecraft:cyan_terracotta`](../items/CyanTerracotta.md) | [`minecraft:cyan_glazed_terracotta`](../items/CyanGlazedTerracotta.md) |
| Purple | [`minecraft:purple_terracotta`](../items/PurpleTerracotta.md) | [`minecraft:purple_glazed_terracotta`](../items/PurpleGlazedTerracotta.md) |
| Blue | [`minecraft:blue_terracotta`](../items/BlueTerracotta.md) | [`minecraft:blue_glazed_terracotta`](../items/BlueGlazedTerracotta.md) |
| Brown | [`minecraft:brown_terracotta`](../items/BrownTerracotta.md) | [`minecraft:brown_glazed_terracotta`](../items/BrownGlazedTerracotta.md) |
| Green | [`minecraft:green_terracotta`](../items/GreenTerracotta.md) | [`minecraft:green_glazed_terracotta`](../items/GreenGlazedTerracotta.md) |
| Red | [`minecraft:red_terracotta`](../items/RedTerracotta.md) | [`minecraft:red_glazed_terracotta`](../items/RedGlazedTerracotta.md) |
| Black | [`minecraft:black_terracotta`](../items/BlackTerracotta.md) | [`minecraft:black_glazed_terracotta`](../items/BlackGlazedTerracotta.md) |

## Choosing an acquisition route

For a planned color palette, start with **uncolored Terracotta**: smelt [Clay blocks](../items/Clay.md#obtaining-and-use) or collect the uncolored material from Badlands terrain, then use the [dye and glazing recipes](#crafting-and-smelting). Collecting an already colored block saves that conversion only when it is the color you want. [Clay smelting][base-recipe] · [Badlands surface rules][badlands-surface] · [Band materials][band-materials]

In the normal Overworld, **Badlands, Eroded Badlands and Wooded Badlands** share terracotta-banding rules. The band palette contains **uncolored Terracotta plus Orange, Yellow, Brown, Red, White and Light Gray Terracotta**. These bands do not supply the other ten dyed colors or Glazed Terracotta. The exact exposed layers vary; this is a palette to look for, not a promise of every color in one cliff. Bring an [unbroken pickaxe](#mining-and-drops), and keep uncolored blocks separate if you intend to dye them later. See [Badlands and its variants](../biomes/DesertsBadlandsAndSavannas.md#badlands) for the terrain search. [Normal world preset][normal-preset] · [Loaded surface rules][badlands-surface] · [Band generation][band-materials] · [Active native band evaluation][native-bands]

A **Mason at trading level 4** offers another possible route to a desired dyed or glazed color. Its level-4 pool contains **one entry for each of the 16 dyed colors, one for each of the 16 glazed colors, and one Quartz-buying entry**. The villager selects **two entries** from that pool, so neither a particular color nor both finishes of that color are guaranteed. Each selected terracotta offer has a **base price of 1 Emerald for 1 block** and **12 uses before restocking**. It does not sell uncolored Terracotta. Check the actual offers before relying on this route; [Trading](../trading/Trading.md#stock-and-restocking) owns replenishment and [price changes](../trading/Trading.md#prices-can-change). [Mason listings][mason-offers] · [Active table and level selection][villager-offers] · [Random selection][offer-selection] · [Sale quantities and stock][sale-constructor]

The optional Trade Rebalance feature retains this Mason pool through its ordinary-table fallback. This is a selected acquisition guide, not an inventory of every structure containing terracotta. [Table fallback][villager-offers] · [Experimental profession tables][experimental-professions]

## Crafting and smelting

| Step | Exact input and arrangement | Output | Recipe time / XP |
| --- | --- | --- | --- |
| Make uncolored Terracotta | Smelt **1 [Clay block](../items/Clay.md)** in a fueled [Furnace](Furnace.md) | **1 Terracotta** | **200 ticks / 0.35 recipe XP** |
| Dye Terracotta | **8 uncolored Terracotta** around **1 target-color dye**, centered in the 3 × 3 [Crafting Table](CraftingTable.md) grid | **8 Terracotta of that color** | Crafting, no cooking timer |
| Glaze a dyed color | Smelt **1 dyed Terracotta** in a fueled Furnace | **1 Glazed Terracotta of that same color** | **200 ticks / 0.1 recipe XP** |

The smelting times are approximately **10 seconds at 20 ticks per second** while processing. Recipe XP is not an immediate payout; see [Furnace experience](Furnace.md#experience-and-troubleshooting). These are normal smelting recipes, not blasting recipes. [Clay-to-Terracotta recipe][base-recipe] · [Example dye recipe][red-recipe] · [Example glazing recipe][red-glaze] · [Furnace recipe type][furnace]

Every dye recipe names **uncolored `minecraft:terracotta`** as its input. Already dyed Terracotta, including White Terracotta, cannot substitute. Each glazing recipe names only its matching dyed input. The bundled recipe files provide **no recoloring, dye removal, or unglazing recipe** for this family. Choose the color and finish before converting a large batch; ordinary block collection retains the current color and finish. [White dye recipe][white-recipe] · [Red dye recipe][red-recipe] · [Red glazing recipe][red-glaze] · [Exact color sources](#forms-and-colors)

## Placement and facing

All forms use a full block shape and have no continuing support requirement after normal placement. They do not become falling blocks when support underneath is removed. [Default shape and survival][shape] · [Uncolored registration][base-block] · [Dyed registration][dyed-blocks] · [Glazed inheritance][glazed-facing]

Uncolored and dyed Terracotta have no directional placement state. **Glazed Terracotta has four horizontal facings**, chosen opposite the player's horizontal direction. For example, placing while facing **north** produces a block state facing **south**. Turn before placing the next block to change its orientation. For ordinary Survival adjustments, collect it with a pickaxe and place it again from the chosen direction. [Glazed placement][glazed-facing] · [Horizontal rotation][horizontal] · [Player direction][placement-direction]

The direction of the face you click does not replace this horizontal-facing rule. Decorative facing also does not decide which piston directions can push the block; those rules are separate below. [Placement callback][glazed-facing] · [Piston push check][push]

## Pistons and sticky blocks

| Material | Push/pull behavior under ordinary piston limits |
| --- | --- |
| Uncolored and dyed Terracotta | Normal movement: pistons can push; Sticky Pistons can pull; eligible Slime/Honey attachments can carry them |
| Glazed Terracotta, every color | Pistons can push it, but Sticky Pistons cannot directly pull it back; Slime/Honey adhesion does not carry it |

Glazed colors are registered with **`PUSH_ONLY`**. The active piston code rejects the pull and side/trailing-attachment checks but accepts a normal push. **Slime or Honey Blocks can still physically push Glazed Terracotta that lies directly in their movement path.** Do not treat the lack of adhesion as immunity to all movement. Rotating the glazed block does not change this behavior. [Glazed registrations][glazed-blocks] · [Sticky retraction and pushability][piston] · [Side, trailing, and forward movement][resolver]

Uncolored/dyed Terracotta retain the default normal push reaction. Use the [Piston guide](Pistons.md#what-can-move) for obstruction, movement-group limits, and circuit timing; this material guide does not override those shared restrictions. [Ordinary registrations][dyed-blocks] · [Uncolored registration][base-block] · [Default push reaction][default-push]

## Mining and drops

Use an **unbroken pickaxe** for every form. A Wooden Pickaxe is sufficient under the bundled tags: all 33 blocks are pickaxe-mineable and none belong to its expanded incorrect-tool set. Hand breaking or using an unsuitable tool does not satisfy the correct-tool drop gate. A broken pickaxe also fails that gate. [Pickaxe tag][pickaxe] · [Wooden restrictions][wood-incorrect] · [Tool-material rules][tools] · [Player drop gate][player-drops] · [Broken-tool handling][broken-tool]

| Form | Registered hardness | Matching ordinary drop |
| --- | ---: | --- |
| Uncolored and dyed Terracotta | **1.25** | **1 block item**, preserving its color |
| Glazed Terracotta | **1.4** | **1 glazed block item**, preserving its color and glazed form |

Every checked loot table has one matching item and an **explosion-survival condition**. There is no Silk Touch requirement or Fortune multiplier; explosion recovery is not guaranteed. Mining glazed blocks does not restore their unglazed ingredients. The individual item pages link all 33 exact loot tables. [Uncolored loot][base-loot] · [Example dyed loot][red-loot] · [Example glazed loot][red-glazed-loot] · [Normal mining dispatch][break-dispatch]

Related: [Terracotta item](../items/Terracotta.md) · [Clay](../items/Clay.md) · [Furnace](Furnace.md) · [Pistons](Pistons.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked all 33 block/item registrations, recipes, and loot tables; expanded mining/tool-tier tags; and traced current placement and piston movement callbacks. All bundled recipe JSON files were scanned for this family's outputs before describing conversion limits. No in-game crafting, smelting, pattern placement, mining, piston, or Slime/Honey mechanism test was run. Data packs can change recipes, tags, and loot.

Acquisition additions reviewed on **2026-10-10** at `f86206767dadde696adfed4e04c5ee97cd0d0885`: followed the normal Overworld preset, loaded Badlands surface rules and native band evaluation; checked Mason tables, offer selection, constructor amounts and the optional-table fallback; and rechecked the family recipes and matching block loot. The earlier placement, piston and collection review remains pinned above. No in-game terrain search or trading test was run.

[base-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3274-L3277
[dyed-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2907-L3030
[glazed-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4430-L4589
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[base-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L709
[dyed-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L635-L650
[glazed-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L823-L838
[base-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/terracotta.json
[red-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_terracotta.json
[red-glaze]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/red_glazed_terracotta.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[white-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/white_terracotta.json
[shape]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[glazed-facing]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/GlazedTerracottaBlock.java
[horizontal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/HorizontalDirectionalBlock.java
[placement-direction]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/context/UseOnContext.java#L70-L71
[push]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L258
[piston]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L193-L270
[resolver]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java
[default-push]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1019
[wood-incorrect]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[tools]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[player-drops]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[base-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/terracotta.json
[red-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/red_terracotta.json
[red-glazed-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/red_glazed_terracotta.json
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L278-L302
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L13
[badlands-surface]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L691-L993
[band-materials]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L326-L374
[native-bands]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/world/level/levelgen/surface/evaluator.rs#L198-L211
[mason-offers]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L646-L711
[villager-offers]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[sale-constructor]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1479
[experimental-professions]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L831-L1128
