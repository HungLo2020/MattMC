# Resource Storage Blocks

Pack resources into blocks for compact storage, building, fuel or a specific device. **Bring the right pickaxe before moving a placed block:** all ten forms here require a correct tool, and some need a higher tier than others. Refined metal blocks, raw-metal blocks and ore blocks have separate recipes and uses. [Registrations][blocks] · [Harvest gate][harvest]

This guide covers Coal, Iron, Gold, Lapis Lazuli, Diamond, Emerald, Netherite, Raw Iron, Raw Gold and Redstone blocks. For Copper forms, use [Copper construction](CopperConstruction.md) and [Raw Copper storage](RawCopperStorage.md). For the resources themselves, ore drops and processing, start with [Ores and Ancient Debris](OreResources.md).

## Exact blocks and storage recipes

Every row uses the same quantities: **nine of the named resource fill a 3 × 3 crafting grid to make one block; one block unpacks shapelessly into nine of that resource**. Packing needs a Crafting Table; unpacking fits the personal crafting grid. The ingredients are exact items: Coal does not accept Charcoal, Lapis uses Lapis Lazuli rather than Blue Dye, and raw metal does not accept ingots. Each row links both complete recipes and its block loot table.

| Block and exact block/item ID | Resource packed, 9 per block | Pack / unpack | Block loot |
| --- | --- | --- | --- |
| <span id="coal-block"></span>[Block of Coal](../items/BlockOfCoal.md) · `minecraft:coal_block` | [Coal](../items/Coal.md) · `minecraft:coal` | [9 → 1][pack-coal_block] / [1 → 9][unpack-coal_block] | [1 matching block][loot-coal_block] |
| <span id="iron-block"></span>[Block of Iron](../items/BlockOfIron.md) · `minecraft:iron_block` | [Iron Ingots](../items/IronIngot.md) · `minecraft:iron_ingot` | [9 → 1][pack-iron_block] / [1 → 9][unpack-iron_block] | [1 matching block][loot-iron_block] |
| <span id="gold-block"></span>[Block of Gold](../items/BlockOfGold.md) · `minecraft:gold_block` | [Gold Ingots](../items/GoldIngot.md) · `minecraft:gold_ingot` | [9 → 1][pack-gold_block] / [1 → 9][unpack-gold_block] | [1 matching block][loot-gold_block] |
| <span id="lapis-block"></span>[Block of Lapis Lazuli](../items/BlockOfLapisLazuli.md) · `minecraft:lapis_block` | [Lapis Lazuli](../items/LapisLazuli.md) · `minecraft:lapis_lazuli` | [9 → 1][pack-lapis_block] / [1 → 9][unpack-lapis_block] | [1 matching block][loot-lapis_block] |
| <span id="diamond-block"></span>[Block of Diamond](../items/BlockOfDiamond.md) · `minecraft:diamond_block` | [Diamonds](../items/Diamond.md) · `minecraft:diamond` | [9 → 1][pack-diamond_block] / [1 → 9][unpack-diamond_block] | [1 matching block][loot-diamond_block] |
| <span id="emerald-block"></span>[Block of Emerald](../items/BlockOfEmerald.md) · `minecraft:emerald_block` | [Emeralds](../items/Emerald.md) · `minecraft:emerald` | [9 → 1][pack-emerald_block] / [1 → 9][unpack-emerald_block] | [1 matching block][loot-emerald_block] |
| <span id="netherite-block"></span>[Block of Netherite](../items/BlockOfNetherite.md) · `minecraft:netherite_block` | [Netherite Ingots](../items/NetheriteIngot.md) · `minecraft:netherite_ingot` | [9 → 1][pack-netherite_block] / [1 → 9][unpack-netherite_block] | [1 matching block][loot-netherite_block] |
| <span id="raw-iron-block"></span>[Block of Raw Iron](../items/BlockOfRawIron.md) · `minecraft:raw_iron_block` | [Raw Iron](../items/RawIron.md) · `minecraft:raw_iron` | [9 → 1][pack-raw_iron_block] / [1 → 9][unpack-raw_iron_block] | [1 matching block][loot-raw_iron_block] |
| <span id="raw-gold-block"></span>[Block of Raw Gold](../items/BlockOfRawGold.md) · `minecraft:raw_gold_block` | [Raw Gold](../items/RawGold.md) · `minecraft:raw_gold` | [9 → 1][pack-raw_gold_block] / [1 → 9][unpack-raw_gold_block] | [1 matching block][loot-raw_gold_block] |
| <span id="redstone-block"></span>[Block of Redstone](../items/BlockOfRedstone.md) · `minecraft:redstone_block` | [Redstone Dust](../items/RedstoneDust.md) · `minecraft:redstone` | [9 → 1][pack-redstone_block] / [1 → 9][unpack-redstone_block] | [1 matching block][loot-redstone_block] |

These are resource conversions, not containers with a menu or saved inventory. The item registrations share their blocks' IDs. [Block items][items] [lapis-item][] [emerald-item][] [redstone-item] · [Default interaction][shape-support]

**Unpack Raw Iron or Raw Gold before smelting or blasting.** The bundled recipes process individual raw pieces; they do not process an entire storage block into nine ingots. Follow [raw-metal processing](OreResources.md#processing-raw-metal) for device times and XP. [Raw Iron unpacking][unpack-raw_iron_block] · [Raw Gold unpacking][unpack-raw_gold_block]

## Recovering a placed block

Use an **unbroken pickaxe** from the matching row. Hand breaking and other ordinary tools do not recover these blocks. Every complete loot table above returns one matching block with no Fortune multiplier or Silk Touch branch: **Silk Touch is unnecessary and cannot bypass the tool requirement**. These plain block classes provide no ordinary mining XP. [Pickaxe group][pickaxe] · [Tool dispatch][pickaxe-property] [tool-material][] [tool-rule][] · [Player check][player-tool] [harvest] · [Broken guard][broken] · [No block XP callback][no-xp]

| Placed block | Suitable pickaxe materials | Hardness | Blast resistance |
| --- | --- | ---: | ---: |
| [Block of Coal](#coal-block) | Wood, Stone, Copper, Iron, Gold, Diamond, Netherite | 5 | 6 |
| [Block of Iron](#iron-block) | Stone, Copper, Iron, Diamond, Netherite | 5 | 6 |
| [Block of Gold](#gold-block) | Iron, Diamond, Netherite | 3 | 6 |
| [Block of Lapis Lazuli](#lapis-block) | Stone, Copper, Iron, Diamond, Netherite | 3 | 3 |
| [Block of Diamond](#diamond-block) | Iron, Diamond, Netherite | 5 | 6 |
| [Block of Emerald](#emerald-block) | Iron, Diamond, Netherite | 5 | 6 |
| [Block of Netherite](#netherite-block) | Diamond, Netherite | 50 | 1,200 |
| [Block of Raw Iron](#raw-iron-block) | Stone, Copper, Iron, Diamond, Netherite | 5 | 6 |
| [Block of Raw Gold](#raw-gold-block) | Iron, Diamond, Netherite | 5 | 6 |
| [Block of Redstone](#redstone-block) | Wood, Stone, Copper, Iron, Gold, Diamond, Netherite | 5 | 6 |

The material requirements come from the bundled tier and exclusion tags, not the tool's name or mining speed. In particular, **Wooden and Golden Pickaxes recover Redstone Blocks but not Redstone Ore**. A Golden Pickaxe cannot recover Gold Blocks. Copper has the same access as Stone for this family. [Tier tags][stone-tier] [iron-tier][] [diamond-tier][] · [Wood/Gold exclusions][incorrect-wooden] [incorrect-gold] · [Stone/Copper exclusions][incorrect-stone] [incorrect-copper] · [Iron/Diamond/Netherite exclusions][incorrect-iron] [incorrect-diamond][] [incorrect-netherite][]

Hardness is not a time in seconds. The values above are registered block properties; tool speed and conditions still affect mining. Follow [Mining](../mechanics/Mining.md), [Pickaxes and Shovels](../mechanics/PickaxesAndShovels.md) and [Durability](../mechanics/Durability.md) for the shared rules. [Properties][blocks]

All ten loot tables have an explosion-survival condition. Normal successful mining supplies no explosion radius, so that condition passes; explosion destruction can lose the item. High blast resistance does not promise a guaranteed explosion drop. [Explosion loot condition][explosion-loot]

## Placement, fire and dropped items

All ten are full solid construction blocks with no facing, axis or waterlogged state. They remain in place when support is removed and do not use falling-block behavior. They emit **no block light**, including the Redstone Block. [Plain block registration][plain-registry] · [Placement and state defaults][place] [no-properties] · [Shape and survival][shape-support] · [Fluid state][empty-fluid] · [Light defaults][defaults] · [Redstone subclass][powered]

**Keep placed Coal Blocks away from spreading fire.** Coal is the only member of this ten-block set registered as flammable; active fire can replace or remove it. The other nine have no flammability entry in this fire table. Burning is separate from furnace fuel use and from blast resistance. [Coal flammability][coal-fire] · [Full fire table][fire-all] · [Active fire behavior][fire] · [Fire initialization][fire-init]

Netherite has **hardness 50 and blast resistance 1,200**, compared with resistance 3 for Lapis and 6 for the other eight. The explosion calculation reads the placed block's resistance. [Netherite properties][netherite-properties] · [Block explosion resistance][explosion]

The **Netherite Block item** also has a separate fire-damage resistance component. A dropped Netherite Block resists the bundled fire damage group, which includes fire, lava and hot-floor damage. This does not grant general damage or explosion immunity, and normal dropped-item despawning still applies. The other nine block items have no such default component. [Netherite item registration][items] · [Fire-resistance component][resistant] · [Covered damage types][fire-damage] · [Component check][damage-tag-check] [stack-damage] · [Item entity damage][item-damage] · [Despawn check][despawn]

## Coal fuel and Charcoal

One Coal Block supplies **16,000 default Furnace burn ticks**, enough for **80 uninterrupted 200-tick recipes**. Nine loose Coal supply 14,400 ticks, enough for 72 such recipes. Packing therefore saves fuel for a long, continuous batch, but a lit Furnace keeps spending its remaining burn time while processing is stalled. [Default fuel values][fuel] · [Server fuel initialization][fuel-init] · [Consumption and burn loop][fuel-use] · [Furnace duration][fuel-duration]

[Charcoal](../items/Charcoal.md) supplies the same 1,600 ticks as loose [Coal](../items/Coal.md), but the Coal Block recipe specifically requires Coal. None of the other nine storage blocks is a default Furnace fuel. Follow [Furnace fuel planning](Furnace.md#fuel-planning) for operation and the faster devices. [Fuel table][fuel-all] · [Exact Coal ingredient][pack-coal_block]

## Beacons, golems and useful crafting

**Iron, Gold, Diamond, Emerald and Netherite Blocks are the five accepted Beacon base materials.** They can be mixed in the same base. Coal, Lapis, Redstone and the two raw-metal blocks do not qualify. The check accepts the base tag uniformly; using Netherite does not buy an extra level. The [Beacon guide](Beacon.md#build-the-base) owns the full geometry, beam clearance and effect rules. [Base tag][beacon-tag] · [Active base check][beacon-base]

Base blocks are separate from **Beacon payment items**. Unpack the appropriate block if you need an ingot or gem for the payment slot; none of these ten block items is a payment item. [Payment tag][payment] · [Beacon payment](Beacon.md#select-and-pay-for-effects)

Use **four Iron Blocks** for the [Iron Golem construction pattern](../mobs/IronGolem.md#build-an-iron-golem), placing the carved head last. The successful construction consumes those blocks. Raw Iron Blocks do not match the body pattern. Iron Blocks also supply the three-block top row of the [Anvil recipe](Anvil.md); use that guide for the full recipe and operation. [Exact golem material][golem] · [Head placement and creation][golem-trigger] · [Material removal][golem-consumes] · [Anvil recipe][anvil]

## Gold and Piglins

Breaking either a **Gold Block or Raw Gold Block** invokes the guarded-block anger route for nearby eligible idle [Piglins](../mobs/Piglin.md). This runs before the normal tool/drop check, so using the wrong pickaxe or Silk Touch does not avoid that callback. Anger still depends on the Piglin's target eligibility and game rules. [Guarded tag][guarded] · [Breaking order][harvest] [piglin-break] · [Anger search][piglin-anger] [piglin-target]

Both block items are also in the Piglin-loved item tag, but **neither is barter currency**: that check names Gold Ingots. Unpack a Gold Block when you need ingots; unpacked Raw Gold still needs processing. [Loved items][loved] · [Pickup checks][piglin-loved] · [Barter currency][currency] [currency-test] · [Barter branch][piglin-barter]

## Redstone power

A placed **Redstone Block emits strength 15 toward every neighboring direction continuously**. It has no on/off state and does not need fuel or a player click. Remove or move the source to stop that local output. It is useful beside components that check neighboring power; it does not become a light source. [Output callback][powered] · [Neighbor signal checks][signal] · [Registered properties][redstone-properties] [defaults]

Its solid shape does not make it an ordinary redstone conductor: its registration explicitly disables that property, and it keeps the default direct-signal output of zero. Do not treat it as strongly powering a neighboring solid block to relay power through that block. Place [Redstone Dust](RedstoneDust.md) or the intended component beside the source, and use the [Redstone guide](../redstone/Redstone.md) for circuit basics. [Conductor override][redstone-properties] · [Signal propagation][signal] · [Direct output default][direct-signal]

## Sources and verification

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. All ten block and item registrations, complete block loot tables, twenty packing/unpacking recipes, tool-tier tags, and the selected active use paths were checked. The full bundled recipe scan found no whole Raw Iron/Raw Gold Block smelting or blasting route. Acquisition examples here are the checked crafting and mining routes, not a structure, chest-loot or world-generation inventory. No gameplay crafting, mining, fire, explosion, Beacon, golem or redstone test was run. Data packs and later source changes can alter these rules.

[blocks]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L169-L180
[lapis-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L283
[emerald-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L589
[redstone-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L999
[tool-material]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[tool-rule]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/component/Tool.java#L49-L57
[pickaxe-property]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Item.java#L435-L437
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[player-tool]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[defaults]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1029
[shape-support]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[empty-fluid]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L224-L234
[plain-registry]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L7291-L7293
[place]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Block.java#L435-L442
[no-properties]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
[no-xp]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L357-L358
[powered]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/PoweredBlock.java#L18-L30
[signal]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/SignalGetter.java#L48-L91
[direct-signal]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L372-L374
[redstone-properties]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L2860-L2869
[netherite-properties]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L5766-L5769
[fuel]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L48
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L341
[fuel-use]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L142-L194
[fuel-duration]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L273
[fire-all]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/FireBlock.java
[fuel-all]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[fire]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/FireBlock.java#L222-L249
[coal-fire]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/FireBlock.java#L460-L463
[fire-init]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/Bootstrap.java#L42-L52
[resistant]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[damage-tag-check]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/component/DamageResistant.java#L21-L23
[stack-damage]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[item-damage]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L291
[despawn]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L161-L176
[explosion]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/ExplosionDamageCalculator.java#L11-L25
[explosion-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/storage/loot/predicates/ExplosionCondition.java#L27-L35
[beacon-base]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[golem]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L191-L201
[golem-trigger]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L60-L89
[golem-consumes]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L115-L134
[piglin-break]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487
[piglin-anger]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533
[piglin-target]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L676-L688
[piglin-loved]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L452-L476
[piglin-barter]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L385
[currency]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L80
[currency-test]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L801-L803
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[beacon-tag]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[payment]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[guarded]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[loved]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/piglin_loved.json
[fire-damage]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[incorrect-wooden]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[incorrect-stone]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[incorrect-copper]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
[incorrect-iron]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[incorrect-gold]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[incorrect-diamond]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[incorrect-netherite]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json
[pack-coal_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/coal_block.json
[loot-coal_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/coal_block.json
[pack-iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/iron_block.json
[loot-iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/iron_block.json
[pack-gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/gold_block.json
[loot-gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/gold_block.json
[pack-lapis_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/lapis_block.json
[loot-lapis_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/lapis_block.json
[pack-diamond_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/diamond_block.json
[loot-diamond_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/diamond_block.json
[pack-emerald_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/emerald_block.json
[loot-emerald_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/emerald_block.json
[pack-netherite_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/netherite_block.json
[loot-netherite_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/netherite_block.json
[pack-raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/raw_iron_block.json
[loot-raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/raw_iron_block.json
[pack-raw_gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/raw_gold_block.json
[loot-raw_gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/raw_gold_block.json
[pack-redstone_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/redstone_block.json
[loot-redstone_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/redstone_block.json
[unpack-coal_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/coal.json
[unpack-iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/iron_ingot_from_iron_block.json
[unpack-gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/gold_ingot_from_gold_block.json
[unpack-lapis_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/lapis_lazuli.json
[unpack-diamond_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/diamond.json
[unpack-emerald_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/emerald.json
[unpack-netherite_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot_from_netherite_block.json
[unpack-raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/raw_iron.json
[unpack-raw_gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/raw_gold.json
[unpack-redstone_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/redstone.json
[anvil]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/anvil.json
