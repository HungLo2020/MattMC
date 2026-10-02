# Obsidian and Crying Obsidian

**Obsidian** and **Crying Obsidian** are two separate, hard-to-mine blocks. Ordinary Obsidian builds Nether portal frames; Crying Obsidian provides light and the material for a [Respawn Anchor](RespawnAnchor.md). They are not interchangeable in those uses. [Registrations][blocks] · [Portal frame test][portal] · [Anchor recipe][anchor-recipe]

## Obsidian

**ID:** `minecraft:obsidian`. Ordinary Obsidian has no light emission. Use it for a [Nether portal frame](../dimensions/Nether.md#building-and-using-a-portal), or as an ingredient for an [Enchanting Table](EnchantingTable.md), [Beacon](Beacon.md), or [Ender Chest](../items/EnderChest.md). The bundled recipes require ordinary Obsidian specifically; Crying Obsidian cannot fill those slots. [Registry][blocks] · [Recipes][enchanting-recipe] [beacon-recipe] [chest-recipe]

### Making ordinary obsidian

Bring **Water beside or above a Lava source**: the liquid contact check converts that source position to Obsidian. Non-source lava produces Cobblestone through this check instead. Lava flowing downward into Water follows a separate Stone-producing rule. The conversion consumes the lava at the converted position. See [Water and Lava](WaterAndLava.md#water-lava-and-building-stone) and the canonical [stone-conversion guide](Stone.md#water-and-lava) for the contact directions and related products. This does not make Crying Obsidian. [Contact and source check][liquid] · [Downward lava behavior][lava]

The normal Nether's Water Bucket placement evaporates the water, so plan this route in a dimension that permits ordinary water placement. A completed portal frame can be made in place without collecting each Obsidian block, but the [mining requirement](#mining-and-block-properties) still applies if you want the items. [Bucket placement][bucket] · [Nether rules][nether-type]

## Crying obsidian

**ID:** `minecraft:crying_obsidian`. Crying Obsidian emits **light level 10** and produces decorative dripping particles. Its placed block has no interaction that turns it into ordinary Obsidian or sets a respawn point. Craft a [Respawn Anchor](RespawnAnchor.md#crafting-and-recovering-an-anchor) to use it for respawning. [Block registration][blocks] · [Crying behavior][crying]

**Crying Obsidian is not a valid Nether portal frame block.** The active frame check accepts ordinary Obsidian only, so replace crying pieces on a required edge when repairing a ruined portal. Follow the existing [Nether guide](../dimensions/Nether.md#building-and-using-a-portal) for frame dimensions, lighting and travel. [Frame predicate and checks][portal]

Neither form has a bundled crafting, smelting or stonecutting recipe producing it, and there is no recipe converting one into the other. The checked recipes use ordinary Obsidian for the three items above and Crying Obsidian for the anchor. [Recipe loading][recipes] · [Recipes][enchanting-recipe] [beacon-recipe] [chest-recipe] [anchor-recipe]

## Finding both forms

### Piglin bartering

Give **one Gold Ingot** to an adult [Piglin](../mobs/Piglin.md) that is willing to admire it and let it finish. The active barter path chooses a random result: **1 Obsidian** or **1–3 Crying Obsidian** are possible returns, alongside other items. Neither is guaranteed for a particular ingot. This route gives items directly and does not need a mining tool. Babies do not perform this adult barter interaction. [Interaction and AI][piglin] [piglin-ai] · [Completed admiration][barter-finish] · [Barter loot][barter-loot]

### Ruined portals

Ruined portals provide placed Obsidian, and their generation processor can replace individual Obsidian blocks with Crying Obsidian. All thirteen checked portal templates contain ordinary Obsidian before processing; the processor's replacement chance is **15% per processed Obsidian block**, not a guarantee that every ruin contains Crying Obsidian. The structure definitions and placement set include Overworld and Nether variants. [Structure set][ruins-set] · [Standard and Nether definitions][ruins-standard] [ruins-nether] · [Template selection and processors][ruins-structure] [ruins-piece] · [Example template][ruins-template] · [Crying replacement][aging]

The templates also wire their chest to the ruined-portal loot table, where **1–2 ordinary Obsidian per selected entry** is possible. This is a random chest result, not a guaranteed chest total. Use the mining rules below to recover the frame itself. [Template][ruins-template] · [Chest loot][ruins-loot] · [Template placement][template] · [Container loot loading][container] [container-loot]

Ordinary Obsidian also forms the End's central-island spikes and arrival platform. The active End biome includes the spike feature, and entering the End rebuilds the platform. See the [End guide](../dimensions/End.md) for travel and arrival hazards; this page does not validate an Obsidian farm. [Normal preset and End biome source][normal] [end-biome-source] · [End biome and spike wiring][end-biome] [spike-placement] [spike-config] [spike] · [Entry and platform][end-portal] [end-platform]

## Mining and block properties

Use an **unbroken Diamond or Netherite Pickaxe** to collect either form in ordinary Survival mining. Both blocks require a correct tool, are pickaxe-mineable, and are in the diamond-tier requirement tag. A hand, Iron Pickaxe or lower-tier ordinary pickaxe does not produce the block drop. [Block properties][blocks] · [Tool tags and material rules][pickaxe] [diamond-tier] [iron-denials] [diamond-denials] [netherite-denials] [tool-material] · [Unbroken-tool and harvesting checks][stack] [player-tool] [break-dispatch]

Each drops **one matching block**. Silk Touch is unnecessary and Fortune does not increase the result. The explosion-survival condition is separate from the correct-tool check. [Ordinary loot][obsidian-loot] · [Crying loot][crying-loot]

| Block | Hardness | Blast resistance | Light |
| --- | ---: | ---: | ---: |
| Obsidian | 50 | 1,200 | 0 |
| Crying Obsidian | 50 | 1,200 | 10 |

These are registered properties, not measured breaking times. Pistons cannot push or pull either block. High blast resistance is not a guarantee against every block-destruction mechanic: consult the [Wither guide](../mobs/Wither.md#skull-and-terrain-hazards) before relying on an enclosure. [Properties][blocks] · [Piston exclusions][pistons]

## Sources and verification

Source-reviewed on **2026-10-02** at `60699a119c4728a7bcaf15196f3c839cfcfd69dc`. Checked registration, mining and loot, recipe inputs/outputs, active liquid conversion and barter dispatch, all thirteen ruined-portal templates and their generation processor, End acquisition wiring, and the portal frame predicate. No in-game mining, generation, bartering, crafting, portal or explosion test was run. Server data packs and later source changes can alter these results; this is not a complete chest-loot catalogue.

Related: [Obsidian item](../items/Obsidian.md) · [Crying Obsidian item](../items/CryingObsidian.md) · [Respawn Anchor](RespawnAnchor.md) · [Resource blocks](catalog/ores.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/Items.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[iron-denials]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[diamond-denials]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[netherite-denials]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/ToolMaterial.java
[stack]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/ItemStack.java
[player-tool]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[recipes]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[pistons]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L259
[portal]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L26-L179
[anchor-recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/respawn_anchor.json
[enchanting-recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/enchanting_table.json
[beacon-recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/beacon.json
[chest-recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/ender_chest.json
[liquid]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/LiquidBlock.java
[lava]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/material/LavaFluid.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/BucketItem.java
[nether-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/dimension_type/the_nether.json
[crying]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/CryingObsidianBlock.java
[piglin]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L251-L261
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java
[barter-finish]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/StopHoldingItemIfNoLongerAdmiring.java
[barter-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json
[ruins-set]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[ruins-standard]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/structure/ruined_portal.json
[ruins-nether]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_nether.json
[ruins-structure]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure.java
[ruins-piece]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalPiece.java
[ruins-template]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/structure/ruined_portal/portal_1.nbt
[aging]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/BlockAgeProcessor.java
[ruins-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/chests/ruined_portal.json
[template]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java
[container]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java
[container-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/RandomizableContainer.java
[normal]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[end-biome-source]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/biome/TheEndBiomeSource.java
[end-biome]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/biome/the_end.json
[spike-placement]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/placed_feature/end_spike.json
[spike-config]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/configured_feature/end_spike.json
[spike]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/levelgen/feature/SpikeFeature.java
[end-portal]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EndPortalBlock.java#L60-L108
[end-platform]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/levelgen/feature/EndPlatformFeature.java
[obsidian-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/obsidian.json
[crying-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/crying_obsidian.json
