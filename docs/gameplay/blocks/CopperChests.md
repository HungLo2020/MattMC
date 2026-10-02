# Copper Chests

Copper Chests provide ordinary persistent storage and act as **pickup containers for Copper Golem item sorting**. All eight oxidation/wax variants can be opened and used for storage. Their connection rules can change a double chest's finish, so join the pair before choosing its final wax/oxidation treatment. [Copper Chest behavior][copper-chest] · [Storage block entity][chest-be-reg] · [Sorting source predicate][golem-ai]

## Obtaining

Surround **1 ordinary Chest with 8 Copper Ingots** in a Crafting Table to make **1 unaffected, unwaxed Copper Chest**. Other unwaxed stages have no direct crafting recipe in this checked set. They can be obtained through placed oxidation or the construction interaction below. Each of the four stages has a shapeless recipe combining **1 unwaxed Copper Chest + 1 Honeycomb → 1 matching waxed Copper Chest**. [Chest recipe][r-copper_chest] · [All variant recipes](#exact-variants-recipes-and-loot) · [Oxidation](CopperConstruction.md#oxidation-and-spacing)

### Creating a chest with a Copper Golem

Place a **Carved Pumpkin or Jack o'Lantern last above one full copper block**. The accepted body tag contains the ordinary, Exposed, Weathered, and Oxidized full Copper blocks and their four waxed counterparts; it does not include Cut Copper, Grates, or Raw Copper Block. Completing the pattern consumes the head and replaces the copper body with a Copper Chest while creating a Copper Golem. [Head placement and spawn callback][pumpkin] · [Exact two-block pattern and chest replacement][copper-pattern] · [Accepted body tag][copper]

The golem and initially selected chest retain the body's oxidation stage, but **wax on the body is not inherited**. The chest-selection map explicitly returns an unwaxed chest for both waxed and unwaxed inputs. An adjacent compatible chest can then affect the resulting joined chest's finish through the normal connection rules. Use [Carved Pumpkin](../items/CarvedPumpkin.md) for obtaining the head, and [Copper Golem Statues](CopperGolemStatues.md#obtaining-a-statue) for the statue route. [Stage selection][pumpkin] · [Chest conversion map and connection adjustment][copper-chest] · [Golem stage initialization][golem-spawn]

## Storage and joining

A single Copper Chest has **27 slots**. Two compatible adjacent Copper Chests join into a **54-slot double chest**. All eight copper variants are connection candidates; ordinary and Trapped Chests are not part of that copper connection tag. Facing, the side of the neighboring single chest, and secondary-use placement determine whether a pair joins. See [ordinary Chest placement](Chest.md#placement-and-access) for the shared placement controls. [27-slot storage][chest-be] · [Combined container][compound] · [Placement checks][chest-place] · [Copper connection override][copper-chest] · [Exact Copper Chest tag][copper_chests]

Joining has two important finish rules:

- **Different oxidation stages:** the connected pair adopts the **less-oxidized stage**
- **Different wax states:** the resulting pair becomes **unwaxed**. If both inputs are waxed, the pair stays waxed at the less-oxidized stage

For example, joining Waxed Oxidized Copper Chest to an unwaxed Exposed Copper Chest produces an unwaxed Exposed pair. Connecting two waxed chests of those stages produces a waxed Exposed pair. The connected neighbor updates to the chosen block type. [Placement normalization, unwaxing, and neighbor synchronization][copper-chest]

Keep the lid area clear. The shared opening check rejects a chest with a redstone-conducting block immediately above or a sitting cat in the space above it; a double chest checks both halves. Waterlogging is supported and does not itself prevent storage access. [Container combination][chest-access] · [Obstruction checks][chest-blocked] · [Water state][chest-place] · [Stored water][water]

### Piglins nearby

Opening a Copper Chest can anger nearby idle Piglins that can see the player. Breaking one uses the guarded-block anger path, which does not make that same visibility check. All eight Copper Chest variants are in the guarded group, including waxed ones. Silk Touch does not remove the guarded-block callback. [Opening callback](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L249-L255) · [Breaking callback](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487) · [Guarded tag](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json) · [Piglin selection and anger](https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533)

### Aging, waxing, and inventory retention

Unwaxed Copper Chests use the shared [oxidation neighbor rules](CopperConstruction.md#oxidation-and-spacing), with extra checks: a **right half of a double chest does not run the aging attempt**, and the active half only tries while its block entity has **no current openers**. A player or a golem opening the container therefore pauses its aging attempts. The other half follows a changed stage. [Chest aging callback][chest-aging] · [Opener tracking][chest-openers] · [Both halves' open calls][compound] · [Golem opening/closing][golem-reached] · [Partner update][copper-chest]

Waxing or scraping one half of a connected pair changes the partner through that same synchronization. One successful placed waxing interaction consumes one Honeycomb. **Inventory and block-entity data are retained across Copper Chest variant changes**, including oxidation, waxing, scraping, and connection-driven finish changes. Hold secondary use when applying Honeycomb or an axe so opening the chest does not consume the interaction first. [Wax interaction][wax] · [Variant preservation][copper-chest] · [Active block-entity retention][chunk-preserve] · [Interaction order][server-use]

### Hoppers and Comparator

Copper Chests use the ordinary chest container interfaces, so [Hoppers](Hopper.md) can insert/extract and a [Comparator](RedstoneComparator.md) reads container fullness. The hopper lookup deliberately requests the chest container with the lid-obstruction check bypassed; that is different from the player and golem access path. Use the Hopper guide for transfer timing and power locking. [Hopper container lookup][hopper] · [Chest combination flag][chest-access] · [Comparator fullness callback][chest-blocked]

## Copper Golem sorting

The active Copper Golem brain runs the container-transport behavior. Its **sources are exactly the Copper Chest tag**; its **destinations are ordinary Chests and Trapped Chests**. All eight Copper Chest variants qualify as sources. Barrels, Ender Chests, and another Copper Chest are not destination types in this behavior. [Brain wiring][golem-brain] · [Active source/destination predicates and behavior installation][golem-ai] · [Source tag][copper_chests]

A practical sorting setup is:

1. Put incoming items in a Copper Chest
2. Place reachable ordinary or Trapped Chests nearby, with a sample item in each destination you want reserved for that item type
3. Leave room for the golem to approach and see a chest side, and keep the lids clear
4. Leave the working golem unleashed; transport stops while leashed or panicking

When empty-handed, the golem takes **up to 16 items from the first nonempty source slot**. A destination accepts a deposit if it is **empty or already contains the same item ID**. Empty chests can therefore absorb any carried type. The matching decision ignores item components, but **merging into an existing stack requires the same item and components**; differently named or otherwise customized stacks may need an empty slot. A full destination can fail to take the carried stack, leaving the golem to try elsewhere. [Pickup, destination matching, and insertion][transport-items] · [Leash/panic checks][transport-timing]

The ordinary search scans loaded chest block entities in a box extending **32 blocks horizontally and 8 vertically** around the golem's block. A riding golem uses a much smaller **1-block search distance** in each direction. It chooses the closest eligible unvisited container position, then checks the contents when interacting; the nearest chest need not accept the carried item. Locked targets, obstructed lids, inaccessible paths, and inability to see a target side can prevent a route. [Search and validation][transport-search] · [Source/destination selection][transport] · [Side visibility][transport-access] · [Combined-container lookup][transport-container]

A reached-container interaction lasts **60 ticks** before its pickup/deposit attempt, excluding travel and waiting. Golems can queue for a container already being used. When no target is available, or its search limits force a reset, the behavior applies a **140-tick cooldown** and clears the search memories. These are callback timings, not a guaranteed items-per-second throughput. [Interaction timing][transport-timing] · [Open/close and queue condition][golem-reached] · [Search limits][transport-cooldown] · [Retry cooldown][transport-retry]

The [statue lifecycle](CopperGolemStatues.md#obtaining-a-statue) can eventually stop an unwaxed working golem. Waxing the living golem prevents its normal aging/statue path; this is separate from waxing the input chest. [Living golem Honeycomb interaction][golem-mob-use] · [Living aging/statue guard][golem-statue]

## Mining and relocation

Use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** for the block drop. Wooden/Golden Pickaxes, hand mining, and broken pickaxes do not satisfy these blocks' bundled tool gate. They have **hardness 3 and blast resistance 6**, and ordinary loot returns one matching oxidation/wax variant; Fortune and Silk Touch add no extra quantity or alternate block result. Explosions add a survival condition. [Pickaxe targets][mineable-pickaxe] · [Tier tag][needs_stone_tool] · [Wood exclusions][incorrect_for_wooden_tool] · [Gold exclusions][incorrect_for_gold_tool] · [Tool materials][tool] · [Broken drop guard][broken-drop] · [Harvest gate][harvest] · [Active harvest][harvest-call] [Copper Chest properties][chest-reg]

A mined chest item preserves its custom name through loot, but it does **not** carry its filled inventory as a portable container. Removing a chest block spills that block entity's contents through the container-removal path; the correct-tool gate determines the chest block item separately. Empty storage before relocating it. [Named chest loot][loot-copper_chest] · [Active removal dispatch][chunk-preserve] · [Container-content drops][be-removal]

## Exact variants, recipes, and loot

| Registry ID | Recipes | Block loot |
| --- | --- | --- |
| `minecraft:copper_chest` | [Craft][r-copper_chest] | [Loot][loot-copper_chest] |
| `minecraft:exposed_copper_chest` | No direct production recipe | [Loot][loot-exposed_copper_chest] |
| `minecraft:oxidized_copper_chest` | No direct production recipe | [Loot][loot-oxidized_copper_chest] |
| `minecraft:waxed_copper_chest` | [Wax][r-waxed_copper_chest_from_honeycomb] | [Loot][loot-waxed_copper_chest] |
| `minecraft:waxed_exposed_copper_chest` | [Wax][r-waxed_exposed_copper_chest_from_honeycomb] | [Loot][loot-waxed_exposed_copper_chest] |
| `minecraft:waxed_oxidized_copper_chest` | [Wax][r-waxed_oxidized_copper_chest_from_honeycomb] | [Loot][loot-waxed_oxidized_copper_chest] |
| `minecraft:waxed_weathered_copper_chest` | [Wax][r-waxed_weathered_copper_chest_from_honeycomb] | [Loot][loot-waxed_weathered_copper_chest] |
| `minecraft:weathered_copper_chest` | No direct production recipe | [Loot][loot-weathered_copper_chest] |

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. All eight chest variants, five recipes, eight loot tables, inventory retention, placement, hopper/comparator access, and the active golem sorting route were checked. The scope does not cover general golem combat, natural spawning, or every container system. No in-game test was run. Recipes, tags, loot, server rules, and later code changes can alter these results.

Related: [Blocks](Blocks.md) · [Copper construction](CopperConstruction.md) · [Copper catalog](catalog/copper.md) · [Items](../items/Items.md)

[chest-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L6391-L6439
[harvest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wax]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[server-use]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L394
[water]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[copper-chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CopperChestBlock.java#L25-L141
[chest-aging]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopperChestBlock.java#L15-L58
[chest-place]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L199-L240
[chest-access]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L269-L299
[chest-blocked]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L328-L360
[chest-be]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L30-L96
[chest-openers]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L129-L146
[chest-be-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L27-L39
[chunk-preserve]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[be-removal]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[hopper]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L344-L388
[golem-ai]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L38-L118
[golem-reached]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L121-L173
[golem-brain]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L148-L195
[transport]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java
[transport-items]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L493-L574
[transport-search]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L277-L407
[transport-timing]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L101-L197
[transport-cooldown]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L429-L449
[transport-retry]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L584-L601
[transport-access]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L473-L490
[transport-container]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L629-L651
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L47-L124
[copper-pattern]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L204-L230
[golem-mob-use]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L211-L258
[golem-statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L261-L305
[golem-spawn]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L363-L366
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[incorrect_for_wooden_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[incorrect_for_gold_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[copper_chests]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/copper_chests.json
[copper]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/copper.json
[loot-copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/copper_chest.json
[r-copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/copper_chest.json
[loot-exposed_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_chest.json
[loot-oxidized_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_chest.json
[loot-waxed_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_chest.json
[r-waxed_copper_chest_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_chest_from_honeycomb.json
[loot-waxed_exposed_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_chest.json
[r-waxed_exposed_copper_chest_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_chest_from_honeycomb.json
[loot-waxed_oxidized_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_chest.json
[r-waxed_oxidized_copper_chest_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_chest_from_honeycomb.json
[loot-waxed_weathered_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_chest.json
[r-waxed_weathered_copper_chest_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_chest_from_honeycomb.json
[loot-weathered_copper_chest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_chest.json
[compound]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/CompoundContainer.java#L13-L82
