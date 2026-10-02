# Campfires

A Campfire cooks **up to four individual food items at once**, provides light, and supplies smoke for careful Bee-housing harvests. A Soul Campfire uses the same cooking system, with dimmer light and stronger contact damage. Once lit, either keeps burning without a continuing fuel supply. [Registered variants][blocks] · [Cooking and ticking][cooking] [ticker]

## Variants and crafting

Both registered blocks have matching inventory items and share the active Campfire block-entity type. Each recipe makes **one block** in a Crafting Table. [Items][items] · [Block-entity registration][type]

| Variant and exact ID | Recipe center | Lit light level | Base contact damage |
| --- | --- | ---: | ---: |
| [Campfire](../items/Campfire.md), `minecraft:campfire` | One Coal or Charcoal | 15 | 1 health point |
| [Soul Campfire](../items/SoulCampfire.md), `minecraft:soul_campfire` | One Soul Sand or Soul Soil | 10 | 2 health points |

For either recipe, put **one Stick in the top center**, **Stick–center ingredient–Stick in the middle row**, and **three logs across the bottom**. This uses three Sticks and three tagged logs, plus the single center ingredient. There is no recipe that converts an already crafted Campfire directly into a Soul Campfire. [Campfire recipe][recipe-campfire] · [Soul Campfire recipe][recipe-soul_campfire] · [Coal choices][coals] · [Soul material choices][soul-base]

The log ingredient is the full **logs tag**. It accepts the nine ordinary Overworld wood families, including their stripped log and wood forms, and Crimson/Warped stems, hyphae, and their stripped forms. The three inputs can be mixed. Bamboo blocks and Pewen logs are absent from this bundled tag. Use [Tree Logs and Roots](TreeLogsAndRoots.md) for the timber families and [Soul Sand, Soul Soil, and Magma](SoulSandSoilAndMagma.md) for the soul materials. [Logs tag][logs] [burnlogs] [crimson] [warped]

## Other ways to obtain them

An **Apprentice Fisherman** can offer one ordinary Campfire for a base price of **two Emeralds**. It is a possible offer from that level's pool, not a guaranteed offer from every Fisherman. Both variants are also available in Creative. [Fisherman pool][trade] · [Price constructor][trade-cost] · [Offer selection][trade-dispatch] · [Creative entries][creative]

With structure generation enabled, ordinary Campfires can occur in **Taiga village buildings**. For example, the medium-house-4 template contains a lit Campfire and is selected by the active Taiga housing pool. This route is connected through the village's town-center jigsaws, the village structure set, and the normal Overworld preset's Taiga biome; a particular village need not select that house. Use Silk Touch if you want to recover the intact block. [House template][house] · [Housing pool][houses] · [Town-center template and pool][meeting] [towns] · [Village and set][village] [villages] · [Eligible biome][village-biome] · [Normal preset and biome selection][normal] [biomes] [taiga] · [Active structure selection][structure-filter] [jigsaw] [jigsaw-placement]

## Placement, shape, and water

A normally placed Campfire is **already lit**. Placement in source Water instead makes it waterlogged and unlit. Its horizontal facing follows the player's facing and rotates the visible log/food arrangement. The collision shape occupies the full block footprint but is only **7/16 of a block high**. It has no continuing attachment-support requirement, so removing the block beneath does not by itself break it. [Placement, shape, and support updates][campfire] · [Inherited survival and collision][defaults]

Where Water can be placed, a Water Bucket can waterlog the block and extinguish it without breaking it. An empty Bucket can recover that stored Water, leaving an **unlit** Campfire to relight manually. A waterlogged Campfire cannot be lit through the ordinary lighting handlers. The placement and filling callbacks specifically test `Fluids.WATER`; do not treat every flowing-water state as the same placement case. [Water placement and lighting checks][campfire] · [Bucket pickup][water]

In an **ultrawarm dimension such as the Nether**, the bucket handler evaporates Water before reaching the Campfire filling callback. Use a Shovel or the thrown-Water-potion interaction there instead. [Bucket dimension check][bucket] · [Shovel][shovel] · [Thrown Water][potion] [dowse]

Rain alone does **not** extinguish these Campfires. Their inherited precipitation callback does nothing, and the cooking ticker does not test weather. [Precipitation callback][rain] · [Campfire callbacks][campfire] · [Cooking ticker][cooking]

## Cooking four items

Hold an eligible raw ingredient and **use the block once per item**, without Sneak/Crouch. Each successful use takes one item and fills the first empty cooking slot. You can mix four different eligible foods; you do not need to aim at a particular visible corner. Food can be placed on an unlit Campfire, but it will not cook until the block is lit. Full slots prevent further insertion. Creative insertion keeps the held ingredient while placing a one-item copy. [Block use][food-use] · [First-empty-slot insertion][insert] · [One-item consumption][consume] · [Secondary-use routing][use]

All **twelve bundled campfire recipes** below produce one result after **600 game ticks**, about **30 seconds at 20 ticks per second**. The four occupied slots progress independently and simultaneously. Soul Campfires have no cooking-speed bonus. Timing depends on the block entity being actively ticked; unloaded or non-ticking areas do not accumulate ordinary cooking progress. This is a regular block-entity ticker, not a random-tick process. [Cooking loop][cooking] · [Ticker selection][ticker] · [Active tick dispatch][level-tick] [chunk-tick]

| Input | Output, one item | Bundled recipe |
| --- | --- | --- |
| Raw Beef | Steak | [Recipe][cook-beef] |
| Raw Chicken | Cooked Chicken | [Recipe][cook-chicken] |
| Raw Cod | Cooked Cod | [Recipe][cook-cod] |
| Dinosaur Chop | Cooked Dinosaur Chop | [Recipe][cook-dinosaur_chop] |
| Kelp | Dried Kelp | [Recipe][cook-kelp] |
| Lobster Tail | Cooked Lobster Tail | [Recipe][cook-lobster_tail] |
| Raw Mutton | Cooked Mutton | [Recipe][cook-mutton] |
| Raw Porkchop | Cooked Porkchop | [Recipe][cook-porkchop] |
| Potato | Baked Potato | [Recipe][cook-potato] |
| Raw Rabbit | Cooked Rabbit | [Recipe][cook-rabbit] |
| Raw Salmon | Cooked Salmon | [Recipe][cook-salmon] |
| Trilocaris Tail | Cooked Trilocaris Tail | [Recipe][cook-trilocaris_tail] |

The Dinosaur Chop recipe lives in a folder named `furnace`, but its actual recipe type is **campfire cooking**. The recipe loader and Campfire input set use that type; folder names do not make it a Furnace recipe. These twelve are the bundled campfire set, so do not assume every [Furnace](Furnace.md) input works here. Food pages retain the individual foods' nutrition and other cooking routes. [Recipe loading and input set][recipes] · [Campfire serializer][serializer] · [Dinosaur Chop recipe][cook-dinosaur_chop]

Finished food is **dropped into the world** and the slot becomes empty. There is no output inventory or experience payout in this cooking path, even though recipe data includes experience values. Throwing food items onto the block is not insertion: use it with the food in hand. There is no cooking menu, manual empty-hand retrieval handler, or fuel slot. [Insertion and completion][insert] [cooking] · [Block interaction][campfire]

Extinguishing keeps unfinished food on the block, but removes **two progress ticks per server tick**, down to zero. Relighting resumes from the remaining progress. Items and cooking progress are saved with the placed block entity. To recover unfinished ingredients immediately, break the block and collect its separately spilled contents. [Cooldown][cooking] · [Saving][save] · [Removal spill][spill]

A [Hopper](Hopper.md) cannot feed or extract the four cooking slots through the ordinary container-transfer path: this block entity does not implement that container interface. Finished food is a loose item and can be collected by item-entity collection where it is within reach. Redstone power does not toggle the fire or accelerate the cooking loop. [Campfire storage interface][entity] · [Hopper container lookup][hopper] · [Campfire callbacks][campfire]

## Lighting and extinguishing

| Action | Result |
| --- | --- |
| Use Flint and Steel on a dry, unlit Campfire | Lights it; normally uses one durability |
| Use a Fire Charge on a dry, unlit Campfire | Lights it; consumes one charge in Survival |
| Hit it with an on-fire projectile that is allowed to interact | Lights it if dry and unlit |
| Use a Shovel on its top or side | Extinguishes it; normally uses one durability |
| Fill it with a Water Bucket where Water placement is allowed | Extinguishes and waterlogs it |
| Hit the relevant block area with a thrown Water potion | Extinguishes without waterlogging |

[Flint and Steel][flint] · [Fire Charge][charge] · [Projectile and Water callbacks][campfire] · [Shovel face restriction][shovel] · [Water-potion hit area and extinguishing][potion] [dowse]

Use serviceable tools: the normal item-use route rejects fully worn tools before their lighting or shovel action. A [Dispenser](DispenserAndDropper.md) facing the block also has an active Flint-and-Steel lighting action, so a redstone-controlled Dispenser can relight a dry, unlit Campfire. A signal wired directly to the Campfire has no corresponding on/off handler. [Broken-item guard][broken] · [Dispenser lighting][dispenser] · [Campfire behavior][campfire]

## Contact damage and immunity

While lit, either variant calls the fire-damage path for **living entities** touching its block space. The table's 1 or 2 health points are the base damage per accepted hit, not a damage-per-second guarantee; normal immunity, protection, and repeated-hit handling still apply. Dropped item entities are not targets of this contact callback. Extinguishing removes the Campfire's contact damage. [Contact callback][damage] · [Living-entity damage handling][living]

**Fire Resistance**, entity fire immunity, and the equipped **Frost Walker** enchantment can prevent this damage. Campfire damage belongs to the fire and burn-from-stepping tags used by those checks. Frost Walker must be on the equipment slot covered by its definition, the feet; carrying an enchanted item in hand is not the same protection. Sneaking alone is not an exemption in the Campfire callback. [Fire tag][fire-tag] · [Fire Resistance][living] · [Entity immunity][fire-immunity] · [Equipment immunity dispatch][immunity] [frost-dispatch] · [Frost Walker definition][frost] · [Stepping tag][stepping]

A **lit Soul Campfire** is also a repellent recognized by ordinary Piglin AI. Its unlit form fails that sensor's special lit-state check. This is a specific AI behavior, not protection from every Nether mob. [Repellent tag][repellent] · [Sensor][piglin] · [Avoidance behavior][piglin-ai]

## Smoke, Hay Bales, and Bees

Put a **Hay Bale directly beneath** either variant to select longer-lived signal-smoke particles. Only that immediately lower block is checked, and adding or removing it updates the smoke state. Both variants produce smoke; the ordinary Campfire's extra ember particles are a separate visual difference. The Hay Bale changes smoke appearance, not cooking time, light level, or contact damage. [Hay and particle selection][campfire] · [Smoke particle lifetimes][particles] · [Cooking and registration][cooking] [blocks]

For manual honey harvesting, follow [Bee Housing: Harvesting](BeeHousing.md#harvesting) and the [Bee guide](../mobs/Bee.md). A lit Campfire or Soul Campfire can satisfy the housing's vertical smoke check. A clear column checks up to **five blocks below the home**; an obstruction intersecting the central smoke-check column stops that search, with one additional check immediately beneath the obstruction. Visible particles and a fire merely nearby are insufficient. Hay Bales do **not** extend this check. Keep Bees from touching the hot block. [Shared campfire tag][campfires] · [Smoke algorithm][smoke] · [Manual harvest callback][bees] · [Contact damage][damage]

## Mining and moving

Both variants have **hardness 2** and are mined efficiently with an axe. Neither requires a particular tool to receive ordinary loot. The intact item needs **Silk Touch**, not simply an axe. [Registration][blocks] · [Axe tag][axe] · [Harvest gate][gate] [harvest]

| Block | Ordinary harvest without Silk Touch | Harvest with Silk Touch |
| --- | --- | --- |
| Campfire | **Two Charcoal** | One Campfire |
| Soul Campfire | **One Soul Soil**, even if crafted with Soul Sand | One Soul Campfire |

Fortune does not change these counts. Lit/unlit state and Hay Bale smoke state do not change the loot branches. The ordinary byproduct branches have explosion-survival conditions, and block loot follows `doTileDrops`. [Campfire loot][loot-campfire] · [Soul Campfire loot][loot-soul_campfire] · [Block-loot rule][drop-rule]

**Silk Touch does not pack the cooking contents into the dropped block.** Removing the block spills stored ingredients separately; its block loot does not copy the inventory or cooking timers. A recovered ordinary item is placed using the normal fresh placement rules, including starting lit unless waterlogged. [Removal dispatch][chunk-remove] · [Stored-item spill][spill] · [Loot][loot-campfire] [loot-soul_campfire] · [Placement][campfire]

## Small cooking setup

As a source-derived example, place one Campfire where you can reach it from the side, use it four times with eligible raw food, stay nearby, and collect the results after each item's 600-tick cooking interval. Keep a Shovel for extinguishing and Flint and Steel for relighting. Add a Hay Bale directly underneath if you want signal smoke. This setup has **not been tested in-game**. [Insertion and cooking][insert] [cooking] · [Shovel][shovel] · [Lighting][flint] · [Hay check][campfire]

## Verification scope

Source-reviewed at MattMC commit `96e5604a6abaec697de2004b1ba9775e303bfba7` on 2026-10-02. Both registrations, valid block-entity forms and tick dispatch, all bundled campfire recipes, crafting tags, interactions, damage/immunity, smoke, the Taiga acquisition example, trade selection, mining loot, and persistence were checked. No cooking, damage, smoke, Bee, Dispenser, or world-generation gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5423-L5447
[cooking]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L41-L100
[ticker]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L303-L325
[items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L2455-L2460
[type]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L223
[recipe-campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/campfire.json
[recipe-soul_campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/soul_campfire.json
[coals]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/coals.json
[soul-base]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/soul_fire_base_blocks.json
[logs]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/logs.json
[burnlogs]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[crimson]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[warped]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/warped_stems.json
[trade]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L119-L137
[trade-cost]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1483
[trade-dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[creative]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1080-L1081
[house]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/structure/village/taiga/houses/taiga_medium_house_4.nbt
[houses]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/houses.json
[meeting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/structure/village/taiga/town_centers/taiga_meeting_point_1.nbt
[towns]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/town_centers.json
[village]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/structure/village_taiga.json
[villages]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[village-biome]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_taiga.json
[normal]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[taiga]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L75-L80
[structure-filter]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java
[jigsaw-placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java
[campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L338
[water]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/BucketItem.java#L101-L130
[shovel]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/ShovelItem.java
[potion]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L49-L66
[dowse]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L110-L121
[rain]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L490-L491
[food-use]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L89-L106
[insert]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L168-L188
[consume]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1086
[use]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L400
[level-tick]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L440-L459
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L710-L783
[cook-beef]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_beef_from_campfire_cooking.json
[cook-chicken]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_chicken_from_campfire_cooking.json
[cook-cod]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_cod_from_campfire_cooking.json
[cook-dinosaur_chop]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/furnace/cooked_dinosaur_chop_campfire.json
[cook-kelp]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/dried_kelp_from_campfire_cooking.json
[cook-lobster_tail]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_lobster_tail_from_campfire_cooking.json
[cook-mutton]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_mutton_from_campfire_cooking.json
[cook-porkchop]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_porkchop_from_campfire_cooking.json
[cook-potato]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/baked_potato_from_campfire_cooking.json
[cook-rabbit]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_rabbit_from_campfire_cooking.json
[cook-salmon]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_salmon_from_campfire_cooking.json
[cook-trilocaris_tail]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_campfire.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L50-L88
[serializer]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L33-L35
[save]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L131-L150
[spill]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L200-L217
[entity]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[flint]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java
[charge]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/FireChargeItem.java
[broken]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[dispenser]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L209-L241
[damage]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L108-L117
[living]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1205
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/Entity.java#L2881-L2886
[immunity]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3699-L3701
[frost-dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L168
[frost]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/enchantment/frost_walker.json
[stepping]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/damage_type/burn_from_stepping.json
[repellent]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/piglin_repellents.json
[piglin]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L118-L127
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L280-L285
[particles]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/client/particle/CampfireSmokeParticle.java
[campfires]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/campfires.json
[smoke]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L235-L281
[bees]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L143-L189
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[loot-campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/campfire.json
[loot-soul_campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/soul_campfire.json
[drop-rule]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L411-L416
[chunk-remove]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L357
