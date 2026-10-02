# Cauldrons

Cauldrons hold a measured amount of **water, lava, or Powder Snow** for bucket collection and other interactions. They have no item-storage inventory. Four registered block IDs share the one [Cauldron item](../items/Cauldron.md); filling changes the placed block state rather than creating a different inventory item. The interaction maps are initialized by the active bootstrap and selected by each cauldron class. [Registration][blocks] · [Item mapping][items] [item-map] · [Interaction dispatch][base] · [Bootstrap][bootstrap]

## Crafting, placement, and collection

Craft **seven Iron Ingots** in a U shape, with three across the bottom and two on each side, to make **one Cauldron**. Place the item normally to get an empty cauldron. There is no facing setting or special support requirement in these classes, and the block does not fall when its supporting block is removed. [Recipe][recipe] · [Empty block][empty] · [Shared shape][base] · [Default placement and survival][behaviour]

All four forms have hardness and blast resistance **2** and require a correct tool for ordinary Survival item drops. Use an **unbroken pickaxe**; all standard material tiers, including Wood, qualify. Hand mining loses the item. See [Mining](../mechanics/Mining.md) for the shared tool rules. [Properties][blocks] · [Copied tool requirement][behaviour] · [Pickaxe tag][pickaxe-tag] [cauldron-tag] · [Tool rules][tool] [tool-component] · [Broken-tool handling][broken-tool] · [Harvest gate][harvest] · [Active harvest call][harvest-call]

Each form drops **one empty Cauldron item**, not its contents. Silk Touch does not preserve water, lava, Powder Snow, or the fill level; Fortune adds nothing. Collect the contents with a bucket or bottle before moving it. Explosion drops have a survives-explosion condition, and normal item spawning requires `doTileDrops`. [Empty loot][loot-empty] · [Water loot][loot-water] · [Lava loot][loot-lava] · [Powder Snow loot][loot-snow] · [Drop spawning][drops]

Empty and filled cauldron states are also registered as the **Leatherworker job site**. Filling an existing cauldron keeps it within that same job-site type. See [Villager professions](../mobs/Villager.md#professions-and-job-sites) for employment rules. [All cauldron job-site states][job-site] · [Profession mapping][profession]

## Empty Cauldron

`minecraft:cauldron` is the empty form. Its comparator output is **0**. It accepts the filled-bucket transactions below, a Water Bottle, suitable precipitation, or a compatible dripstone transfer. There is no level-zero Water/Powder Snow state: using the last layer returns to this empty block. [Empty behavior][empty] · [Default analog output][behaviour] · [Layer removal][layered] · [Transactions][interactions]

## Water Cauldron

`minecraft:water_cauldron` has levels **1, 2, and 3**; level 3 is full. A comparator reads exactly the current level. Water can be transferred one layer at a time with bottles, or all three layers with a bucket when full. This form also performs the cleaning interactions below. Its contents are stored as a cauldron state, not a world Water fluid block. [Layered behavior][layered] · [Interactions][interactions] · [Default fluid state][behaviour]

Water Bucket use on a cauldron follows its direct fill handler, with **no ultra-warm evaporation check**. It therefore differs from pouring open water in the Nether. The cauldron classes also have no temperature-based water freezing or random evaporation callback. See [Water and Lava](WaterAndLava.md) for open-fluid behavior. [Cauldron fill][interactions] · [Ordinary bucket evaporation][bucket] · [Cauldron callbacks][base] [layered]

## Lava Cauldron

`minecraft:lava_cauldron` is always full, has no partial-level property, gives comparator strength **3**, and emits **light level 15**. An empty Bucket collects one Lava Bucket and leaves an empty cauldron. Its entity-contact effect burns and damages susceptible entities; this does not consume the lava. Rain does not dilute it, and dripstone does not add further layers. [Lava behavior][lava] · [Light registration][blocks] · [Bucket transaction][interactions]

## Powder Snow Cauldron

`minecraft:powder_snow_cauldron` has levels **1–3**, with matching comparator outputs. A full level-3 cauldron supplies one Powder Snow Bucket; partial levels cannot be collected with a bucket or bottle. Snowfall can add layers, and a filled Powder Snow Bucket sets it straight to level 3. Rain and dripstone do not fill this form. [Layered behavior][layered] · [Transactions][interactions]

Its contact callback extinguishes burning entities. It does **not** use placed Powder Snow's freezing or leather-boot collision mechanics. Extinguishing a burning entity converts any remaining layers to water, as described below. See [Snow](Snow.md#powder-snow-buckets-and-collision) for the separate placed block and bucket resource. [Cauldron contact][layered] · [Applied effects][effects]

## Bucket and bottle transactions

Use the item on the cauldron normally. Secondary use while holding an item bypasses the block's interaction map, so it can attempt ordinary bucket/block placement instead. The following table describes **Survival** exchanges. Successful transactions replace one used container; when a held stack remains, the returned container goes into the inventory or is dropped if it cannot fit. [Server interaction order][server-use] · [Container exchange helper][item-utils]

| Used item and starting contents | Resulting contents | Returned item |
| --- | --- | --- |
| Water Bucket on any cauldron | Water, level 3 | Empty Bucket |
| Lava Bucket on any cauldron, without water above it | Lava, full | Empty Bucket |
| Powder Snow Bucket on any cauldron, without water above it | Powder Snow, level 3 | Empty Bucket |
| Empty Bucket on level-3 Water | Empty | Water Bucket |
| Empty Bucket on Lava | Empty | Lava Bucket |
| Empty Bucket on level-3 Powder Snow | Empty | Powder Snow Bucket |
| Water Bottle on Empty | Water, level 1 | Glass Bottle |
| Water Bottle on level-1 or level-2 Water | Water, one level higher | Glass Bottle |
| Glass Bottle on any Water level | One level lower, or Empty after level 1 | Water Bottle |

These handlers are registered for the exact container items. The filled-bucket defaults exist on **all four maps**, so a filled bucket can replace existing contents, including a full cauldron. The previous contents are lost rather than mixed into stone, ice, or another product. Collect anything you want to keep before overwriting it. [Complete interaction maps and callbacks][interactions]

Lava Bucket and Powder Snow Bucket filling checks the **fluid state immediately above the cauldron**. Any water-tagged fluid there, including flowing or waterlogged water, blocks that fill. The handler consumes the interaction, **not the held bucket**, and leaves the contents unchanged. Water Bucket filling has no equivalent restriction. [Underwater test and fill handlers][interactions]

Empty Buckets do not remove partial Water/Powder Snow. Water Bottles do not add to a full Water Cauldron, Lava, or Powder Snow. The accepted bottle is the ordinary Potion item with Water potion contents and no custom effects; other potions and splash/lingering bottles have no cauldron-fill entry. This implementation does not store potion effects or tipped-arrow batches. Use the [Brewing Stand](BrewingStand.md) for brewing. [Bottle handlers][interactions] · [Water-potion check][potions]

Creative keeps its held bucket/bottle under the infinite-materials exchange rule, but the **cauldron still fills or empties**. The exchange helper may add a missing result container to the inventory. Creative is not an exemption from changing the cauldron's contents. [Exchange helper][item-utils] · [Consumption rule][consume] · [State changes][interactions]

## Washing equipment, banners, and shulker boxes

Use the held item on **Water** at any nonzero level. Each successful operation removes **one water level**; the third operation from a full cauldron leaves it empty. No dye is added to the water, and loose Dye items have no interaction entry for coloring cauldron contents. [Water interaction registrations and cleaning callbacks][interactions]

| Held item | What one use does |
| --- | --- |
| Dyed Leather Helmet, Chestplate, Leggings, or Boots | Removes the dyed-color component |
| Dyed Leather Horse Armor or Wolf Armor | Removes the dyed-color component |
| Any colored Banner with patterns | Removes **only the last pattern layer** from one banner; its base color remains |
| Any of the 16 colored Shulker Box items | Returns one uncolored Shulker Box, preserving its stored components, including contents |

The six equipment items must both belong to the dyeable tag and actually have a dyed-color component. An undyed item does not consume water. A banner without patterns also does nothing; repeated washing removes patterns one at a time. The uncolored Shulker Box is not registered for this wash operation. Water use is not skipped in Creative. [Registered cleaning items][interactions] · [Dyeable tag][dyeable] · [Component-preserving shulker conversion][item-transmute]

For a stack of banners, the cleaned single banner is returned through the inventory exchange rather than stripping a pattern from every banner at once. The armor callback instead changes the held item's color component directly. See [Shulker Boxes](ShulkerBox.md) for their storage and recovery rules, and [Leather Horse Armor](../items/LeatherHorseArmor.md) or [Wolf Armor](../items/WolfArmor.md) for equipment use. [Cleaning implementation][interactions] · [Inventory exchange][item-utils]

In Creative, banner and colored-Shulker-Box washing keeps the original held item and adds the washed copy to the inventory, dropping it if necessary. Equipment washing still changes the held item's dyed-color component directly. Both paths consume a water layer. [Cleaning and exchange choice][interactions] · [Creative result handling][item-utils] · [Non-consumption rule][consume]

## Rain and snowfall

Leave the cauldron exposed to precipitation. The server samples a column and calls the precipitation handler on the block at its upper motion-blocking surface while the world is raining. The biome's precipitation type at that height determines whether the callback is rain or snow. This is a sampled weather process, not a fill every game tick. [Weather selection][weather] · [Precipitation dispatch][precipitation] · [Biome precipitation][biome]

- **Rain:** each selected rain callback has a **5%** fill chance. Empty becomes level-1 Water; existing Water gains one level up to 3.
- **Snow:** each selected snow callback has a **10%** fill chance. Empty becomes level-1 Powder Snow; existing Powder Snow gains one level up to 3.
- Existing layered contents accept only their matching precipitation type. Rain does not turn Powder Snow into water, and snow does not turn a Water Cauldron into Powder Snow. Lava has no precipitation fill behavior.

These probabilities apply to the cauldron callback after the server's weather sampling, so they do not promise a fixed completion time. [Empty precipitation][empty] · [Matching layered precipitation][layered] · [Lava class][lava]

In this source, `randomTickSpeed` controls the chunk's weather-selection loop as well as block random ticks. Setting it to **0** stops new natural precipitation-fill attempts and new dripstone random-tick attempts. By contrast, `snowAccumulationHeight=0` only disables the separate ground-snow placement branch: it does **not** skip cauldron precipitation handling. [Game-rule dispatch][random-dispatch] · [Weather sampling][weather] · [Independent snow/precipitation branches][precipitation] · [Rules][rules]

## Filling from Pointed Dripstone

A practical arrangement is a contained **Water or Lava source above a supporting block**, a downward Pointed Dripstone attached below that block, and a compatible cauldron directly underneath the tip. Use a short stalactite and a clear vertical gap. The ordinary fill path reads the actual source fluid above the support; decorative drip particles alone do not prove there is a usable source. [Transfer path][drip-random] · [Source and particle rules][dripstone]

The source checks impose several useful limits:

- The cauldron must be **1–10 blocks below the tip**, meaning at most nine intervening blocks. Clear air works. Other intervening blocks must be dry, not solid-rendering, and leave the narrow central drip path unobstructed.
- The scheduled recheck needs a **downward, unwaterlogged, unmerged tip**. Its source lookup must find the supporting root within ten blocks above the tip, then read source Water or Lava one block above that root. Flowing fluid is not accepted as the source type.
- Water can start an empty cauldron or add **one level** to a Water Cauldron, up to 3. Lava can fill an **empty** cauldron in one transfer. These drip paths do not replace another content type. Powder Snow cannot be collected by dripping.

[Tip conditions and root search][dripstone] · [Vertical search and obstruction checks][drip-search] · [Empty receiving][empty] · [Water receiving][layered] · [Default drip rejection][base]

Transfer begins on an eligible stalactite root's random tick: Water has a **17.578125%** attempt chance and Lava **5.859375%**. A successful path schedules the cauldron for **50 plus the tip-to-cauldron distance in game ticks**, or 51–60 game ticks here. That is roughly 2.55–3 seconds at 20 ticks per second **after** the random wait, not the total production interval. [Active random-tick registration][drip-registration] · [Probability and scheduling][drip-random]

When the scheduled tick runs, the current cauldron block, tip, source, and receiving type are checked again. Changing the setup can prevent the fill. The source is not consumed, so a maintained Lava-source setup can refill an emptied cauldron repeatedly. `randomTickSpeed=0` stops new attempts but does not cancel an already queued scheduled callback. The separate Mud branch converts Mud to Clay instead of scheduling an ordinary cauldron fill; it is not the source setup described here. [Scheduled dispatch][scheduled-dispatch] · [Cauldron recheck][base] · [Transfer and Mud branch][drip-random]

## Entity contact, fire, and freezing

Filled cauldrons use block-contact effects rather than ordinary world-fluid collision. The checked contact shape includes the cauldron's shell and its contents up to the fill height. In the basin, Water/Powder Snow levels reach **9/16, 12/16, and 15/16** of the block; Lava reaches **15/16**. The entity intersection path then applies the queued effects. [Layered shapes][layered] · [Lava shape][lava] · [Active intersection dispatch][collision-dispatch] · [Effect collection][effect-collector]

Water and Powder Snow apply **extinguish**. If the entity was burning and is allowed to interact with the position, the server also removes one layer before extinguishing it. A burning entity in Powder Snow changes the remainder to Water: level 3 becomes level-2 Water, level 2 becomes level-1 Water, and level 1 becomes Empty. An entity that was not burning does not use a layer. These classes do not apply the placed Powder Snow freezing effect. [Contact and layer conversion][layered] · [Effect meanings][effects] · [Default entity interaction permission][may-interact]

Lava clears freezing, ignites non-fire-immune entities for **15 seconds**, and invokes the ordinary lava damage routine for **4 health points before defenses**. Damage handling and immunity still apply; this is not a guaranteed damage-per-second rate. The cauldron's lava is not reduced by this contact. [Lava cauldron effects][lava] · [Ignition and damage routine][lava-hurt]

## Comparator use and automation limits

Put a [Comparator](RedstoneComparator.md#placement-and-input-directions) with its **rear input facing the cauldron**. Its raw reading is 0 for Empty, 1–3 for Water/Powder Snow, and 3 for Lava. A full cauldron therefore gives **3, not 15**, and the signal alone does not identify which full content it holds. Compare/subtract mode and side inputs can further change the comparator's final output. [Analog source][base] · [Layer levels][layered] · [Lava output][lava] · [Comparator read][comparator]

Cauldrons do not offer an item inventory or a container menu. The bucket/bottle/cleaning map is a **player interaction**. The checked Dispenser bucket code instead uses world-fluid placement or `BucketPickup`; cauldrons implement neither that pickup interface nor the liquid-container placement interface. A Dispenser does not perform these cauldron bucket exchanges. Use the [Dispenser guide](DispenserAndDropper.md#some-verified-dispenser-actions) for its separate item behaviors. [Cauldron class and interaction][base] · [Dispenser bucket branches][dispenser] · [Fluid bucket placement][bucket] · [Solid-bucket placement][solid-bucket]

## A small collection setup

For a source-based, **untested** Lava collection example, use this vertical stack from top to bottom: a safely contained Lava source, one full supporting block, one downward Pointed Dripstone, one air block, and an empty Cauldron. Put a comparator beside the cauldron with its rear input toward it and no side input. After a successful random transfer and scheduled recheck, the cauldron reads 3. Collect its Lava Bucket manually to return the reading to 0 and allow another fill. Keep the source contained and stay clear of the filled basin.

For a water supply, use Water in the same short arrangement. It takes three successful one-layer transfers to fill an empty cauldron for bucket collection; a Glass Bottle can draw from a partial level. General source-fluid behavior belongs in [Water and Lava](WaterAndLava.md), [Bucket](../items/Bucket.md), [Water Bucket](../items/WaterBucket.md), and [Lava Bucket](../items/LavaBucket.md). Powder Snow collection and placement are covered in [Snow](Snow.md) and [Powder Snow Bucket](../items/PowderSnowBucket.md).

## Sources and verification

Reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Source and bundled-data review only; no in-game filling, cleaning, weather, dripstone, entity-contact, or redstone test.

[blocks]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/Blocks.java#L2528-L2543
[items]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L1791
[item-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L2764-L2771
[base]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/AbstractCauldronBlock.java
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/Bootstrap.java#L42-L59
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/recipe/crafting/cauldron.json
[empty]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CauldronBlock.java
[behaviour]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[cauldron-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/cauldrons.json
[tool]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ToolMaterial.java
[tool-component]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/component/Tool.java
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L282-L297
[loot-empty]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/cauldron.json
[loot-water]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/water_cauldron.json
[loot-lava]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/lava_cauldron.json
[loot-snow]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/powder_snow_cauldron.json
[drops]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/Block.java
[job-site]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java
[profession]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L115-L118
[layered]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/LayeredCauldronBlock.java
[interactions]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/BucketItem.java
[lava]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/LavaCauldronBlock.java
[effects]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/InsideBlockEffectType.java
[server-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L398
[item-utils]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemUtils.java#L16-L41
[potions]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L79-L81
[consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[dyeable]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/dyeable.json
[item-transmute]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L637
[weather]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L475-L511
[precipitation]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L555-L585
[biome]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/Biome.java
[random-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L378-L404
[rules]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/GameRules.java
[drip-random]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L182-L236
[dripstone]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java
[drip-search]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L523-L614
[drip-registration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/Blocks.java#L6521-L6535
[scheduled-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[collision-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1225
[effect-collector]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/InsideBlockEffectApplier.java
[may-interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L3873-L3875
[lava-hurt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L574-L607
[comparator]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L98-L116
[dispenser]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L165-L211
[solid-bucket]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SolidBucketItem.java
