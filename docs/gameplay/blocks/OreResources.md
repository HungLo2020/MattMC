# Ores and Ancient Debris

Ores supply fuel, metals, gems and redstone. **Use a suitable pickaxe before breaking a vein:** a fast tool or Silk Touch does not bypass a harvesting requirement. This guide covers the eight Overworld ore families in both regular and Deepslate forms, Nether Gold Ore, Nether Quartz Ore, and Ancient Debris: **19 registered blocks**. [Block registration][blocks] · [Tool rules][tools]

## Bring a suitable pickaxe

All 19 blocks require the correct tool and belong to the pickaxe mining group. The table lists the ordinary unbroken pickaxe materials accepted by the bundled tags. [Harvest gate][player] · [Mining dispatch][mining] · [Pickaxe group][pickaxe] · [Tier groups][stone-tier] [iron-tier] [diamond-tier]

| Ore family, including its Deepslate form where present | Suitable pickaxes | Base ordinary drop | Base mining XP |
| --- | --- | --- | ---: |
| Coal | Wood, Stone, Copper, Iron, Gold, Diamond, Netherite | 1 Coal | 0–2 |
| Iron | Stone, Copper, Iron, Diamond, Netherite | 1 Raw Iron | 0 |
| Copper | Stone, Copper, Iron, Diamond, Netherite | 2–5 Raw Copper | 0 |
| Gold | Iron, Diamond, Netherite | 1 Raw Gold | 0 |
| Lapis Lazuli | Stone, Copper, Iron, Diamond, Netherite | 4–9 Lapis Lazuli | 2–5 |
| Redstone | Iron, Diamond, Netherite | 4–5 Redstone Dust | 1–5 |
| Diamond | Iron, Diamond, Netherite | 1 Diamond | 3–7 |
| Emerald | Iron, Diamond, Netherite | 1 Emerald | 3–7 |
| Nether Gold | Wood, Stone, Copper, Iron, Gold, Diamond, Netherite | 2–6 Gold Nuggets | 0–1 |
| Nether Quartz | Wood, Stone, Copper, Iron, Gold, Diamond, Netherite | 1 Nether Quartz | 2–5 |
| Ancient Debris | Diamond, Netherite | 1 Ancient Debris | 0 |

The drop counts above are **without Fortune or Silk Touch**. Each matching regular/Deepslate pair uses the same resource branch. The [exact ID and loot table list](#registered-forms-and-loot) below covers every variant. XP is the unmodified mining award from the active block class, not Furnace experience. It requires the ordinary successful harvest path and block drops; enchantment effects can change it. [XP registrations][blocks] · [Ore XP callback][xp] · [Redstone XP callback][redstone] · [XP processing and game rule][block]

A **Golden Pickaxe cannot harvest Overworld Gold Ore**, despite the name. Copper has the same listed ore access as Stone here; it cannot harvest Gold, Redstone, Diamond, Emerald or Ancient Debris. A broken pickaxe retained by MattMC loses its correct-tool ability. Use [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) for recipes, repairs, upgrades and speed values. [Material restrictions][tools] · [Broken tool handling][itemstack]

## Fortune versus Silk Touch

For all listed ores except Ancient Debris, **Silk Touch selects one matching ore block** before the ordinary resource branch. Regular ore stays regular and Deepslate ore stays Deepslate. The bundled Silk Touch enchantment also sets block experience to zero. It does not remove the tool-tier requirement. Ancient Debris already drops itself without Silk Touch. [Loot tables](#registered-forms-and-loot) · [Silk Touch experience effect][silk] · [Harvest gate][player]

Most ore resource branches use the ore-drop Fortune formula: at Fortune level L above zero, the base resource count is multiplied by a random factor from **1 through L+1**, with factor 1 having two of the L+2 equally likely draws. Thus Fortune III can multiply the base roll by 1, 2, 3 or 4; it does not guarantee four times the yield. [Active Fortune formulas][bonus]

**Redstone uses a different formula:** add a random integer from **0 through the Fortune level** to its base 4–5 Dust. Fortune III therefore gives 4–8 Dust, rather than multiplying the base roll by four. Ancient Debris's loot has no Fortune function. [Redstone loot][loot-redstone_ore] · [Deepslate Redstone loot][loot-deepslate_redstone_ore] · [Debris loot][loot-ancient_debris] · [Bonus formulas][bonus]

| Resource | Possible Fortune III count range per correctly mined ore |
| --- | ---: |
| Coal, Raw Iron, Raw Gold, Diamond, Emerald, Nether Quartz | 1–4 |
| Raw Copper | 2–20 |
| Lapis Lazuli | 4–36 |
| Redstone Dust | 4–8 |
| Gold Nuggets from Nether Gold Ore | 2–24 |
| Ancient Debris | 1 |

These ranges are not uniform probability distributions or explosion yields. Most ordinary resource branches include explosion decay; Ancient Debris has a different direct-item loot table. See [Mining](../mechanics/Mining.md) for shared block/tool conditions. [Loot definitions](#registered-forms-and-loot)

### Gold ores and Piglins

Mining Gold Ore, Deepslate Gold Ore or Nether Gold Ore can anger nearby idle Piglins. These three ores belong to the guarded-block group, and the player-breaking callback is independent of the chosen Silk Touch or resource loot branch. [Gold ore tag](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/gold_ores.json) · [Guarded blocks](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json) · [Breaking callback](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487) · [Anger routine](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533)

## Processing raw metal

Smelt or blast **one Raw Iron, Raw Copper or Raw Gold into one matching ingot**. Furnace recipes take **200 ticks**; Blast Furnace recipes take **100 ticks**, nominally 10 and 5 seconds at 20 ticks per second. Both require fuel and operating conditions. Recipe experience is **0.7** for Iron/Copper and **1.0** for Gold; collection and rounding are handled by the device. [Iron recipes][iron_ingot_from_smelting_raw_iron] [iron_ingot_from_blasting_raw_iron] · [Copper recipes][copper_ingot_from_smelting_raw_copper] [copper_ingot_from_blasting_raw_copper] · [Gold recipes][gold_ingot_from_smelting_raw_gold] [gold_ingot_from_blasting_raw_gold]

Ore blocks collected with Silk Touch also have processing recipes. Each operation consumes **one ore block** and makes **one output item**:

| Ore input | Output | Recipe XP |
| --- | --- | ---: |
| Coal / Deepslate Coal | 1 Coal | 0.1 |
| Iron / Deepslate Iron | 1 Iron Ingot | 0.7 |
| Copper / Deepslate Copper | 1 Copper Ingot | 0.7 |
| Gold / Deepslate Gold | 1 Gold Ingot | 1.0 |
| Lapis / Deepslate Lapis | 1 Lapis Lazuli | 0.2 |
| Redstone / Deepslate Redstone | 1 Redstone Dust | 0.7 |
| Diamond / Deepslate Diamond | 1 Diamond | 1.0 |
| Emerald / Deepslate Emerald | 1 Emerald | 1.0 |
| Nether Gold | 1 Gold Ingot | 1.0 |
| Nether Quartz | 1 Nether Quartz | 0.2 |
| Ancient Debris | 1 Netherite Scrap | 2.0 |

Every row has a 200-tick smelting route and a 100-tick blasting route in the [processing source matrix](#processing-recipes). **Processing Copper, Lapis or Redstone Ore gives only one output**, losing the multiple pieces from ordinary mining. Nether Gold Ore instead cooks into an entire ingot rather than its usual nugget drops. Pick the resource route deliberately; recipe XP is separate from mining XP.

Pack **nine raw metal pieces into one matching raw-metal block**, and unpack it back to nine pieces before the individual-raw-item recipes above. This is a crafting storage conversion, not a recipe that smelts a whole block into nine ingots. [Raw Iron recipes][raw_iron_block] [raw_iron] · [Raw Copper recipes][raw_copper_block] [raw_copper] · [Raw Gold recipes][raw_gold_block] [raw_gold]

### Ancient Debris to Netherite

Mine Ancient Debris with a Diamond or Netherite Pickaxe, process each block into one Netherite Scrap, then combine **4 Netherite Scraps and 4 Gold Ingots, shapeless, into 1 Netherite Ingot**. Fortune does not increase the debris count. Equipment upgrading is a later, separate [Smithing Table](SmithingTable.md) operation with its own template and input requirements. [Debris loot][loot-ancient_debris] · [Smelting][netherite_scrap] · [Blasting][netherite_scrap_from_blasting] · [Ingot recipe][netherite_ingot]

## Placed properties and Redstone Ore light

Regular Overworld ores, Nether Gold Ore and Nether Quartz Ore have **hardness 3 and blast resistance 3**. Deepslate ores have **hardness 4.5 and resistance 3**. Ancient Debris has **hardness 30 and resistance 1,200**. Hardness is not a mining time in seconds; tool speed and conditions matter. All are full placed blocks that remain when their supporting block is removed. [Registrations and copied properties][blocks]

Redstone Ore and its Deepslate variant have a temporary **lit state with light level 9**. Attacking the block, stepping on it without stepping carefully, or the item-on-block interaction can light it and produce particles. A later random tick turns it off, so there is no fixed seconds-long timer. The lit state does not change its loot branches, and the block uses no redstone signal-output override; it is not a replacement for a power source. [Interaction, step and random-tick callbacks][redstone] · [Light emission registration][blocks] · [Default signal behavior](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L224-L226)

## Storage and availability

Use [Resource Storage Blocks](ResourceStorageBlocks.md) for the non-Copper blocks' exact packing/unpacking recipes, harvesting tiers and placed uses.

Raw-metal storage recipes are described under [processing](#processing-raw-metal). Storage blocks and ore blocks are separate items; processing rules for one do not imply processing rules for the other.

### Generation examples and limits

In the normal preset, the bundled **Forest** feature list includes Coal, Iron, Copper, Gold, Redstone, Diamond and Lapis ore placements. **Windswept Hills** additionally provides an Emerald ore route. Their configured features target the stone/deepslate replacement groups to choose the corresponding ore form. These are checked examples, not a complete biome or best-height chart. [Normal preset wiring][presets] · [Forest features][forest] · [Windswept Hills features][hills] · [Emerald targets][configured-ore_emerald] · [Ore placement implementation][ore-feature]

**Nether Wastes** includes placed Nether Gold, Nether Quartz and two Ancient Debris features. The Debris features use the Nether base-stone target group and discard exposed candidates; the presence of a feature is not a guarantee of finding a vein in every chunk. [Nether biome][nether] · [Nether Gold placement][placed-ore_gold_nether] [configured-ore_gold_nether] · [Quartz placement][placed-ore_quartz_nether] [configured-ore_quartz_nether] · [Large Debris][placed-ore_ancient_debris_large] [configured-ore_ancient_debris_large] · [Small Debris][placed-ore_debris_small] [configured-ore_debris_small] · [Scattered-ore implementation][scattered]

Custom dimensions/presets need their own generation verification. The [Dry Midlands](../biomes/DryMidlands.md) stone/deepslate target IDs were corrected by [#781 / PR #792](https://github.com/HungLo2020/MattMC/pull/792), with deterministic registry and native-placement tests. Its separate magma/gravel target limitation remains; the correction is not a complete natural-terrain yield survey or a retrofit of existing chunks. A registered ore or familiar block appearance does not establish working ore generation there.

## Registered forms and loot

The following are the exact covered block IDs and item forms. Resource blocks, Amethyst, Gilded Blackstone, and other minerals are separate topics.

| Block item | ID | Bundled loot |
| --- | --- | --- |
| [Coal Ore](../items/CoalOre.md) | `minecraft:coal_ore` | [Loot][loot-coal_ore] |
| [Iron Ore](../items/IronOre.md) | `minecraft:iron_ore` | [Loot][loot-iron_ore] |
| [Copper Ore](../items/CopperOre.md) | `minecraft:copper_ore` | [Loot][loot-copper_ore] |
| [Gold Ore](../items/GoldOre.md) | `minecraft:gold_ore` | [Loot][loot-gold_ore] |
| [Lapis Lazuli Ore](../items/LapisLazuliOre.md) | `minecraft:lapis_ore` | [Loot][loot-lapis_ore] |
| [Redstone Ore](../items/RedstoneOre.md) | `minecraft:redstone_ore` | [Loot][loot-redstone_ore] |
| [Diamond Ore](../items/DiamondOre.md) | `minecraft:diamond_ore` | [Loot][loot-diamond_ore] |
| [Emerald Ore](../items/EmeraldOre.md) | `minecraft:emerald_ore` | [Loot][loot-emerald_ore] |
| [Deepslate Coal Ore](../items/DeepslateCoalOre.md) | `minecraft:deepslate_coal_ore` | [Loot][loot-deepslate_coal_ore] |
| [Deepslate Iron Ore](../items/DeepslateIronOre.md) | `minecraft:deepslate_iron_ore` | [Loot][loot-deepslate_iron_ore] |
| [Deepslate Copper Ore](../items/DeepslateCopperOre.md) | `minecraft:deepslate_copper_ore` | [Loot][loot-deepslate_copper_ore] |
| [Deepslate Gold Ore](../items/DeepslateGoldOre.md) | `minecraft:deepslate_gold_ore` | [Loot][loot-deepslate_gold_ore] |
| [Deepslate Lapis Lazuli Ore](../items/DeepslateLapisLazuliOre.md) | `minecraft:deepslate_lapis_ore` | [Loot][loot-deepslate_lapis_ore] |
| [Deepslate Redstone Ore](../items/DeepslateRedstoneOre.md) | `minecraft:deepslate_redstone_ore` | [Loot][loot-deepslate_redstone_ore] |
| [Deepslate Diamond Ore](../items/DeepslateDiamondOre.md) | `minecraft:deepslate_diamond_ore` | [Loot][loot-deepslate_diamond_ore] |
| [Deepslate Emerald Ore](../items/DeepslateEmeraldOre.md) | `minecraft:deepslate_emerald_ore` | [Loot][loot-deepslate_emerald_ore] |
| [Nether Gold Ore](../items/NetherGoldOre.md) | `minecraft:nether_gold_ore` | [Loot][loot-nether_gold_ore] |
| [Nether Quartz Ore](../items/NetherQuartzOre.md) | `minecraft:nether_quartz_ore` | [Loot][loot-nether_quartz_ore] |
| [Ancient Debris](../items/AncientDebris.md) | `minecraft:ancient_debris` | [Loot][loot-ancient_debris] |

## Processing recipes

Each linked definition specifies its exact input, one-item output, time and recipe XP. Furnace behavior and fuel are documented in [Furnace](Furnace.md).

| Ore | Smelting | Blasting |
| --- | --- | --- |
| Coal Ore | [Recipe][coal_from_smelting_coal_ore] | [Recipe][coal_from_blasting_coal_ore] |
| Iron Ore | [Recipe][iron_ingot_from_smelting_iron_ore] | [Recipe][iron_ingot_from_blasting_iron_ore] |
| Copper Ore | [Recipe][copper_ingot_from_smelting_copper_ore] | [Recipe][copper_ingot_from_blasting_copper_ore] |
| Gold Ore | [Recipe][gold_ingot_from_smelting_gold_ore] | [Recipe][gold_ingot_from_blasting_gold_ore] |
| Lapis Lazuli Ore | [Recipe][lapis_lazuli_from_smelting_lapis_ore] | [Recipe][lapis_lazuli_from_blasting_lapis_ore] |
| Redstone Ore | [Recipe][redstone_from_smelting_redstone_ore] | [Recipe][redstone_from_blasting_redstone_ore] |
| Diamond Ore | [Recipe][diamond_from_smelting_diamond_ore] | [Recipe][diamond_from_blasting_diamond_ore] |
| Emerald Ore | [Recipe][emerald_from_smelting_emerald_ore] | [Recipe][emerald_from_blasting_emerald_ore] |
| Deepslate Coal Ore | [Recipe][coal_from_smelting_deepslate_coal_ore] | [Recipe][coal_from_blasting_deepslate_coal_ore] |
| Deepslate Iron Ore | [Recipe][iron_ingot_from_smelting_deepslate_iron_ore] | [Recipe][iron_ingot_from_blasting_deepslate_iron_ore] |
| Deepslate Copper Ore | [Recipe][copper_ingot_from_smelting_deepslate_copper_ore] | [Recipe][copper_ingot_from_blasting_deepslate_copper_ore] |
| Deepslate Gold Ore | [Recipe][gold_ingot_from_smelting_deepslate_gold_ore] | [Recipe][gold_ingot_from_blasting_deepslate_gold_ore] |
| Deepslate Lapis Lazuli Ore | [Recipe][lapis_lazuli_from_smelting_deepslate_lapis_ore] | [Recipe][lapis_lazuli_from_blasting_deepslate_lapis_ore] |
| Deepslate Redstone Ore | [Recipe][redstone_from_smelting_deepslate_redstone_ore] | [Recipe][redstone_from_blasting_deepslate_redstone_ore] |
| Deepslate Diamond Ore | [Recipe][diamond_from_smelting_deepslate_diamond_ore] | [Recipe][diamond_from_blasting_deepslate_diamond_ore] |
| Deepslate Emerald Ore | [Recipe][emerald_from_smelting_deepslate_emerald_ore] | [Recipe][emerald_from_blasting_deepslate_emerald_ore] |
| Nether Gold Ore | [Recipe][gold_ingot_from_smelting_nether_gold_ore] | [Recipe][gold_ingot_from_blasting_nether_gold_ore] |
| Nether Quartz Ore | [Recipe][quartz] | [Recipe][quartz_from_blasting] |
| Ancient Debris | [Recipe][netherite_scrap] | [Recipe][netherite_scrap_from_blasting] |

## Related pages

- [Raw Iron](../items/RawIron.md), [Raw Copper](../items/RawCopper.md), [Raw Gold](../items/RawGold.md)
- [Mining](../mechanics/Mining.md), [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md), [Furnace](Furnace.md)
- [Blocks](Blocks.md), [Ores and minerals catalog](catalog/ores.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`: all 19 registrations and loot tables, recursive tool tags, active XP/redstone callbacks, 38 ore-processing recipes, raw-metal conversions, and 21 biome-to-placed-to-configured generation routes. No in-game mining, smelting, light-timing, or generation test was run. Data packs and game rules can change results. Generation examples do not constitute a full world-distribution survey.

[blocks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java
[tools]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ToolMaterial.java
[player]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/player/Player.java
[mining]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[itemstack]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ItemStack.java
[bonus]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java
[xp]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/DropExperienceBlock.java
[block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Block.java
[redstone]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/RedStoneOreBlock.java
[silk]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/enchantment/silk_touch.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[forest]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/biome/forest.json
[hills]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/biome/windswept_hills.json
[nether]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[ore-feature]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java
[scattered]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/ScatteredOreFeature.java
[presets]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java
[loot-coal_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/coal_ore.json
[loot-iron_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/iron_ore.json
[loot-copper_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/copper_ore.json
[loot-gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/gold_ore.json
[loot-lapis_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/lapis_ore.json
[loot-redstone_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/redstone_ore.json
[loot-diamond_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/diamond_ore.json
[loot-emerald_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/emerald_ore.json
[loot-deepslate_coal_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_coal_ore.json
[loot-deepslate_iron_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_iron_ore.json
[loot-deepslate_copper_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_copper_ore.json
[loot-deepslate_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_gold_ore.json
[loot-deepslate_lapis_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_lapis_ore.json
[loot-deepslate_redstone_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_redstone_ore.json
[loot-deepslate_diamond_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_diamond_ore.json
[loot-deepslate_emerald_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/deepslate_emerald_ore.json
[loot-nether_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/nether_gold_ore.json
[loot-nether_quartz_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/nether_quartz_ore.json
[loot-ancient_debris]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/ancient_debris.json
[coal_from_blasting_coal_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/coal_from_blasting_coal_ore.json
[coal_from_smelting_coal_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/coal_from_smelting_coal_ore.json
[iron_ingot_from_blasting_iron_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/iron_ingot_from_blasting_iron_ore.json
[iron_ingot_from_smelting_iron_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/iron_ingot_from_smelting_iron_ore.json
[copper_ingot_from_blasting_copper_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_copper_ore.json
[copper_ingot_from_smelting_copper_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_copper_ore.json
[gold_ingot_from_blasting_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_gold_ore.json
[gold_ingot_from_smelting_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_gold_ore.json
[lapis_lazuli_from_blasting_lapis_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/lapis_lazuli_from_blasting_lapis_ore.json
[lapis_lazuli_from_smelting_lapis_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/lapis_lazuli_from_smelting_lapis_ore.json
[redstone_from_blasting_redstone_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/redstone_from_blasting_redstone_ore.json
[redstone_from_smelting_redstone_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/redstone_from_smelting_redstone_ore.json
[diamond_from_blasting_diamond_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/diamond_from_blasting_diamond_ore.json
[diamond_from_smelting_diamond_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/diamond_from_smelting_diamond_ore.json
[emerald_from_blasting_emerald_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/emerald_from_blasting_emerald_ore.json
[emerald_from_smelting_emerald_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/emerald_from_smelting_emerald_ore.json
[coal_from_blasting_deepslate_coal_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/coal_from_blasting_deepslate_coal_ore.json
[coal_from_smelting_deepslate_coal_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/coal_from_smelting_deepslate_coal_ore.json
[iron_ingot_from_blasting_deepslate_iron_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/iron_ingot_from_blasting_deepslate_iron_ore.json
[iron_ingot_from_smelting_deepslate_iron_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/iron_ingot_from_smelting_deepslate_iron_ore.json
[copper_ingot_from_blasting_deepslate_copper_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_deepslate_copper_ore.json
[copper_ingot_from_smelting_deepslate_copper_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_deepslate_copper_ore.json
[gold_ingot_from_blasting_deepslate_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_deepslate_gold_ore.json
[gold_ingot_from_smelting_deepslate_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_deepslate_gold_ore.json
[lapis_lazuli_from_blasting_deepslate_lapis_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/lapis_lazuli_from_blasting_deepslate_lapis_ore.json
[lapis_lazuli_from_smelting_deepslate_lapis_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/lapis_lazuli_from_smelting_deepslate_lapis_ore.json
[redstone_from_blasting_deepslate_redstone_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/redstone_from_blasting_deepslate_redstone_ore.json
[redstone_from_smelting_deepslate_redstone_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/redstone_from_smelting_deepslate_redstone_ore.json
[diamond_from_blasting_deepslate_diamond_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/diamond_from_blasting_deepslate_diamond_ore.json
[diamond_from_smelting_deepslate_diamond_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/diamond_from_smelting_deepslate_diamond_ore.json
[emerald_from_blasting_deepslate_emerald_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/emerald_from_blasting_deepslate_emerald_ore.json
[emerald_from_smelting_deepslate_emerald_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/emerald_from_smelting_deepslate_emerald_ore.json
[gold_ingot_from_blasting_nether_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_nether_gold_ore.json
[gold_ingot_from_smelting_nether_gold_ore]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_nether_gold_ore.json
[quartz_from_blasting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/quartz_from_blasting.json
[quartz]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/quartz.json
[netherite_scrap_from_blasting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/netherite_scrap_from_blasting.json
[netherite_scrap]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/netherite_scrap.json
[iron_ingot_from_smelting_raw_iron]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/iron_ingot_from_smelting_raw_iron.json
[iron_ingot_from_blasting_raw_iron]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/iron_ingot_from_blasting_raw_iron.json
[copper_ingot_from_smelting_raw_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_raw_copper.json
[copper_ingot_from_blasting_raw_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_raw_copper.json
[gold_ingot_from_smelting_raw_gold]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_raw_gold.json
[gold_ingot_from_blasting_raw_gold]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_raw_gold.json
[netherite_ingot]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot.json
[raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/raw_iron_block.json
[raw_copper_block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/raw_copper_block.json
[raw_gold_block]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/raw_gold_block.json
[raw_iron]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/raw_iron.json
[raw_copper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/raw_copper.json
[raw_gold]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/raw_gold.json
[configured-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_emerald.json
[placed-ore_gold_nether]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_nether.json
[configured-ore_gold_nether]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_nether_gold.json
[placed-ore_quartz_nether]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_quartz_nether.json
[configured-ore_quartz_nether]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_quartz.json
[placed-ore_ancient_debris_large]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_ancient_debris_large.json
[configured-ore_ancient_debris_large]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_ancient_debris_large.json
[placed-ore_debris_small]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_debris_small.json
[configured-ore_debris_small]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_ancient_debris_small.json
