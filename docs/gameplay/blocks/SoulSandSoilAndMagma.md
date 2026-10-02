# Soul Sand, Soul Soil and Magma Blocks

Choose **Soul Sand** for a rising bubble column or Nether Wart bed, **Soul Soil** for a full-height floor without Soul Sand's ordinary slowdown, and **Magma Block** for a descending bubble column. They are three distinct registered blocks with different movement, farming and harvesting behavior. [Registration][blocks] · [Column bases][bubble] · [Wart support][wart]

## Comparing the blocks

| Block and registry ID | Hardness / blast resistance | Ordinary collection | Placed difference |
| --- | --- | --- | --- |
| <span id="soul-sand">Soul Sand, `minecraft:soul_sand`</span> | 0.5 / 0.5 | One block, including by hand; shovel is faster | Collision is 14/16 of a block high; movement speed factor 0.4 |
| <span id="soul-soil">Soul Soil, `minecraft:soul_soil`</span> | 0.5 / 0.5 | One block, including by hand; shovel is faster | Full-height collision; no special slowdown |
| <span id="magma-block">Magma Block, `minecraft:magma_block`</span> | 0.5 / 0.5 | One block with an unbroken pickaxe, including Wood | Full-height collision, light level 3, hot-floor damage callback |

Soul Sand and Soul Soil do not require a correct tool for drops. Magma Block does; it is in the pickaxe tag without a higher-tier requirement. The three loot tables have no Silk Touch or Fortune alternative, so neither enchantment multiplies their ordinary block drops. Explosion survival is a separate loot condition. [Properties][blocks] [properties] · [Tool tags][shovel] [pickaxe][] [wood][] [stone][] [iron][] [diamond] · [Harvest gate][player] [gate] · [Loot][loot-sand] [loot-soil][] [loot-magma][]

All three stay in place without support; none uses falling-sand behavior. Soul Sand's reduced collision height does not mean its top support shape is empty: it supplies a full support shape. Its name alone does not give Soul Soil the same column or crop behavior. [Soul Sand shapes][sand] · [Registered classes][blocks]

## Obtaining and making them

The normal world's Nether dimension selects the bundled Nether noise settings, whose **Soul Sand Valley** surface rules include both Soul Sand and Soul Soil. This is a source-defined terrain route, not a fixed-height deposit chart. See [The Nether](../dimensions/Nether.md) for expedition context. [Normal preset][preset] · [Surface rules][nether]

Adult [Piglin bartering](../mobs/Piglin.md) can supply **2–8 Soul Sand** as one possible result. The live barter method loads this loot table; an individual Gold Ingot does not guarantee Soul Sand. Soul Soil is not that barter entry. [Active barter][piglin] · [Barter loot][barter]

Craft **four Magma Cream in a 2 × 2 square → one Magma Block**. This fits the inventory crafting grid. See [Magma Cream](../items/MagmaCream.md) for the ingredient's other uses. [Exact recipe][cream]

There is also a conversion route from Soul Sand to Soul Soil: craft a **Soul Campfire** using three Sticks, three log-tag ingredients and one Soul Sand or Soul Soil, then break it **without Silk Touch** to receive one Soul Soil. Silk Touch instead preserves the Soul Campfire. This spends the other crafting materials; it is not a free one-for-one crafting-grid conversion. [Campfire pattern][campfire] · [Accepted central ingredient][campfire-tag] · [Campfire loot][campfire-loot]

## Walking and Soul Speed

Ordinary Soul Sand movement applies its **0.4 speed factor to horizontal motion**. That is a factor in the active movement calculation, not a promise of a particular walking speed or a fixed percentage change under every effect. Soul Soil has no corresponding custom factor. Living entities blend this block factor toward 1 using their movement-efficiency attribute. [Movement application][entity] · [Living movement efficiency][living]

Both blocks belong to **`soul_speed_blocks`**. The feet-slot Soul Speed enchantment adds movement speed and movement efficiency through its location-change effect, subject to the enchantment's movement and riding conditions. It also has a conditional durability-cost roll; the boost is not free permanent terrain. This guide does not derive a blocks-per-second promise from the attribute values. [Block tag][speed-tag] · [Enchantment definition][speed] · [Active enchantment handling][enchant]

## Farming, fire and construction uses

- **Nether Wart grows on Soul Sand only.** Soul Soil fails the crop's exact block check. The [Nether Wart guide](NetherWart.md) owns planting, growth and harvest details. [Crop support][wart]
- **Both support Soul Fire** through the soul-fire base tag. Soul Fire is a separate hazardous block; the two ground blocks are not themselves burning entities merely because they can support it. [Fire support][fire] [fire-tag]
- **Both are valid Wither construction bases** through the summon-base tag. Follow the [Wither guide](../mobs/Wither.md) for the complete pattern and danger; one ground block alone does not summon anything. [Pattern caller][wither] [wither-tag]
- **Soul Soil participates in making ordinary Basalt with Lava and Blue Ice.** Follow the precise [Basalt layout](BlackstoneAndBasalt.md#making-basalt-with-lava); Soul Sand is not an interchangeable ingredient.

## Bubble-column bases

Soul Sand makes a column push **up**; Magma Block makes it pull **down**. Their placement/upper-neighbor callbacks schedule a column check after **20 game ticks**, nominally one second at 20 ticks per second. The column builder requires actual source-water blocks or an existing column. Flowing/falling water and merely waterlogged building blocks do not make a complete elevator. [Base scheduling][sand] [magma] · [Column construction][bubble]

Use the canonical [Bubble Columns guide](BubbleColumns.md) for water conversion, propagation, breathing and motion. **Soul Soil does not start a rising column**, and imported [Primal Magma](PrimalMagma.md) is separate from ordinary Magma Block. A descending column does not protect you from the Magma floor when you reach it.

## Magma-floor safety

When a **living entity steps on Magma Block without stepping carefully**, the callback requests **1 health point of hot-floor damage**. For an ordinary player, stepping carefully uses the sneak state. The callback does not ignite the entity, and this number is not a guarantee of damage every game tick: immunity, damage handling and contact determine the result. [Floor callback][magma] · [Careful stepping][entity] · [Damage handling][living]

Hot-floor damage is in the fire-damage tag. **Fire Resistance**, inherent fire immunity, and **Frost Walker on equipped feet armor** have active immunity paths for this damage. Frost Walker's protection comes from its burn-from-stepping damage predicate, not from freezing the Magma Block. Other nearby hazards remain separate. [Fire damage tag][fire-damage] · [Fire Resistance][living] · [Inherent immunity][entity] · [Frost Walker immunity][frost] [burn] · [Enchantment immunity dispatch][enchant]

Magma Block's step callback targets living entities; it is not Lava's item-burning contact behavior. Its light level is **3**, and it has no spreading fire or lava-fluid callback. Do not use the glowing texture as a measure of safe standing or useful room illumination. [Active class][magma] · [Light and registration][blocks]

## Sources and verification

Source-reviewed on **2026-10-02** at `be4ac3081f175ffb862938ab118b20e6457110d8`. Registrations, active movement/contact and column callbacks, harvest tags, loot, crafting inputs, barter dispatch, Nether surface selection and enchantment immunity were checked. No in-game mining, growth, speed, damage, conversion or elevator test was run. Recipes, tags, enchantments and server settings can be changed by data packs.

Related: [Blocks](Blocks.md) · [Water and Lava](WaterAndLava.md) · [Bubble Columns](BubbleColumns.md) · [Nether Wart](NetherWart.md) · [Primal Magma](PrimalMagma.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java
[sand]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/SoulSandBlock.java
[magma]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/MagmaBlock.java
[entity]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/Entity.java
[living]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/LivingEntity.java
[shovel]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[gate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[player]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/player/Player.java
[loot-sand]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/soul_sand.json
[loot-soil]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/soul_soil.json
[loot-magma]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/magma_block.json
[barter]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json
[piglin]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java
[preset]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[nether]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json
[cream]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/magma_block.json
[campfire]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/soul_campfire.json
[campfire-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/item/soul_fire_base_blocks.json
[campfire-loot]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/soul_campfire.json
[bubble]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BubbleColumnBlock.java
[wart]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/NetherWartBlock.java
[speed]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/enchantment/soul_speed.json
[speed-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/soul_speed_blocks.json
[fire]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/SoulFireBlock.java
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/soul_fire_base_blocks.json
[wither]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/WitherSkullBlock.java
[wither-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/wither_summon_base_blocks.json
[frost]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/enchantment/frost_walker.json
[burn]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/damage_type/burn_from_stepping.json
[fire-damage]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[enchant]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java
[properties]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
