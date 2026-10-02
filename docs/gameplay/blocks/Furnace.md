# Furnace, Blast Furnace and Smoker

A Furnace processes **smelting recipes** using fuel. It has an input slot, fuel slot, and output slot. Its ID is `minecraft:furnace`. The **Blast Furnace** and **Smoker** share the same three-slot fuel system but accept different recipe types; compare their construction, timing and placement [below](#blast-furnace-and-smoker).

## Crafting and mining

At a [Crafting Table](CraftingTable.md), surround an empty center with eight items from the stone-crafting-materials tag. The bundled tag accepts **Cobblestone, Blackstone, and Cobbled Deepslate**; ordinary Stone is not in that tag.

Use a suitable pickaxe when collecting the furnace. Its block registration requires the correct tool for drops and its mining tag identifies pickaxes. The block loot table returns the furnace item and preserves its custom name.

## Smelting

1. Put an item with a loaded smelting recipe in the input slot.
2. Add valid fuel to the fuel slot.
3. Leave room in the output slot for the recipe result.
4. Take the result when processing finishes.

Fuel starts burning only when the furnace can process the input, but already-lit fuel continues counting down even if input runs out or the output becomes blocked. Plan batches to avoid wasting fuel. Processing time comes from each recipe; not all recipes must take the same time.

Verified examples at normal 20-tick-per-second speed:

| Input | Output | Recipe time |
| --- | --- | --- |
| One Cobblestone | One [Stone](Stone.md) | 200 ticks / 10 seconds |
| One Raw Cod | One [Cooked Cod](../items/CookedCod.md) | 200 ticks / 10 seconds |
| One Trilocaris Tail | One [Cooked Trilocaris Tail](../items/CookedTrilocarisTail.md) | 200 ticks / 10 seconds |

## Fuel planning

The default fuel values below assume a standard furnace and uninterrupted 200-tick recipes:

| Fuel | Burn ticks | Maximum 200-tick recipes |
| --- | ---: | ---: |
| Coal or Charcoal | 1,600 | 8 |
| Blaze Rod | 2,400 | 12 |
| Coal Block | 16,000 | 80 |
| Lava Bucket | 20,000 | 100 |
| A plank in the fuel planks tag | 300 | 1.5 recipe-equivalents |

The fraction is fuel capacity, not a promise that one plank completes two items. Keep fuel and inputs available for partial progress. A wood appearance alone does not establish fuel-tag membership for integrated blocks.

## Experience and troubleshooting

Taking output as a player triggers the furnace's stored recipe/experience payout. Recipe XP values are not the same thing as immediate or device-independent payouts.

If processing stalls, check that the device accepts the recipe type, fuel is valid, and the output has room for the right result. A [Smoker](../items/Smoker.md) uses smoking recipes and a [Blast Furnace](../items/BlastFurnace.md) uses blasting recipes; neither accepts every furnace recipe merely because it cooks faster.

## Hopper automation

[Hoppers](Hopper.md) use the Furnace's sided inventory rules:

| Connection | Accessible role |
| --- | --- |
| Hopper feeding from above | Input slot |
| Hopper feeding from a horizontal side | Fuel slot |
| Hopper pulling from below | Output slot, plus the fuel slot only for an Empty Bucket or Water Bucket |

A Hopper below does not normally pull unburned Coal from the fuel slot. The inserted item must still pass that slot's validation, and the Furnace needs a valid recipe, fuel, and output space to process anything. Redstone-lock the appropriate Hoppers when you need to stop their own transfers.

Automation source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2`: [sided slots and item checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java), [Hopper transfer handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java). No automated setup was tested in-game.

## Blast Furnace and Smoker

| Device and ID | Accepted recipe type | Checked example | Fuel duration |
| --- | --- | --- | --- |
| Furnace, `minecraft:furnace` | Smelting | Raw Iron → Iron Ingot in 200 ticks | Default fuel value |
| <span id="blast-furnace"></span>Blast Furnace, `minecraft:blast_furnace` | Blasting | Raw Iron → Iron Ingot in 100 ticks | Half the default fuel value, rounded down |
| <span id="smoker"></span>Smoker, `minecraft:smoker` | Smoking | Raw Cod → Cooked Cod in 100 ticks | Half the default fuel value, rounded down |

Each device is wired to its matching block-entity/menu/ticker and recipe type. The current bundled inventory has 25 blasting and 12 smoking recipes, all with 100-tick processing times; the 76 smelting recipes specify 200 ticks. These are checked data values, not a rule that forces every added recipe to that duration. A familiar food or metal is accepted only when a matching recipe is loaded. [Device dispatch][family-blast-block] [family-smoker-block] · [Recipe types and fuel overrides][family-blast] [family-smoker] · [Shared recipe lookup/timing][family-engine] · [Raw Iron pair][family-iron-smelt] [family-iron-blast] · [Cod smoking][family-cod] · [Bundled recipes][family-recipes]

For those 100-tick recipes, the faster devices take half as long but **do not double fuel efficiency**. One Coal lasts 800 ticks in either, enough for eight uninterrupted 100-tick recipes, compared with eight 200-tick recipes in an ordinary Furnace. Already-burning fuel still counts down when the input runs out or the output blocks. Light means fuel remains; it does not prove an item is currently being processed. [Fuel table][family-fuel] · [Half-duration overrides][family-blast] [family-smoker] · [Shared burn loop][family-engine]

### Crafting the two devices

A **Blast Furnace** recipe uses **five Iron Ingots, one Furnace and three Smooth Stone**:

```text
Iron    Iron     Iron
Iron    Furnace  Iron
Smooth  Smooth   Smooth
```

Smooth Stone is the full block, not ordinary Stone, a slab, or Polished Blackstone. [Exact Blast Furnace recipe][family-blast-recipe] · [Stone processing](Stone.md)

A **Smoker** uses one Furnace with **four log-tag items**, one on each side in the crafting grid:

```text
        Log
Log     Furnace  Log
        Log
```

The four log ingredients can be mixed; each slot matches the tag separately. This is the logs tag, not the planks tag. It includes the Crimson and Warped stem families as well as the tagged Overworld logs/wood, including their stripped forms. Verify an imported wood's tag rather than assuming its name qualifies. [Exact Smoker recipe][family-smoker-recipe] · [Logs tag][family-logs] · [Burnable logs][family-logs-burn] · [Nether stems][family-crimson] [family-warped] · [Ingredient matching][family-pattern]

### Placement, collection and signals

Both devices have **hardness 3.5, blast resistance 3.5**, require an unbroken pickaxe to collect, and have no higher-tier mining requirement: Wood qualifies. Their loot returns **one matching device** and copies only its custom name. Silk Touch is unnecessary and Fortune does not add more. Their inventory contents are dropped separately when the block entity is removed; the mined item is not portable storage for input, fuel or output. [Properties][family-blocks] · [Pickaxe and tier tags][family-pickaxe] [family-wood][] [family-stone-tier][] [family-iron-tier][] [family-diamond-tier][] · [Loot][family-blast-loot] [family-smoker-loot] · [Container removal][family-blockentity]

Like the ordinary Furnace, the front faces toward the placer, using the opposite of the player's horizontal direction. These are full collision blocks, have no waterlogged state, and can be opened without chest-style space above. Lit variants emit **light level 13**; unlit variants emit no block light. A roof or wall against the front does not alter recipe selection. [Shared placement/state/menu behavior][family-block] · [Device interaction][family-blast-block] [family-smoker-block] · [Registration][family-blocks]

All three support the same [Hopper slot directions](#hopper-automation). They also provide a **comparator output based on the fullness of all three inventory slots**, not a cooking-progress percentage or remaining-burn timer. Applying redstone power does not pause the shared cooking loop; lock the relevant Hoppers when controlling transfers. Taking output manually follows the common result-slot experience path. Automated extraction does not itself collect those stored recipe rewards; block-entity removal has a separate experience payout. [Comparator callback][family-block] · [Fullness calculation][family-menu] · [Sided slots, processing and experience][family-engine] · [Shared result slot][family-result] [family-furnace-menu]

A Blast Furnace is the **Armorer** job-site block and a Smoker the **Butcher** job-site block. These registrations do not bypass the other [villager employment rules](../trading/Trading.md). [Job-site states][family-poi] · [Profession mapping][family-professions]

### Family verification

Expanded on **2026-10-02** against `60699a119c4728a7bcaf15196f3c839cfcfd69dc`. Checked both complete construction recipes and loot tables, all current smelting/blasting/smoking recipe definitions, nested material tags, active block/entity/menu/ticker dispatch, fuel overrides, sided inventory, comparator and removal paths. No in-game cooking, automation, experience, mining or villager test was run. The original standard-Furnace sections retain their earlier source pins where behavior is unchanged.

## Related pages

- [Smelting guide](../smelting/Smelting.md)
- [Furnace item](../items/Furnace.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/furnace.json)
- [Stone crafting tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json)
- [Registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1292-L1303)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/furnace.json)
- [Processing and fuel consumption](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java)
- [Default fuel values](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java)
- [Smelting recipe type](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java)
- [Player result-slot payout](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/FurnaceResultSlot.java)

[family-blast-block]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BlastFurnaceBlock.java
[family-smoker-block]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/SmokerBlock.java
[family-blast]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BlastFurnaceBlockEntity.java
[family-smoker]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/SmokerBlockEntity.java
[family-engine]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
[family-fuel]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[family-iron-smelt]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/smelting/iron_ingot_from_smelting_raw_iron.json
[family-iron-blast]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/blasting/iron_ingot_from_blasting_raw_iron.json
[family-cod]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/smoking/cooked_cod_from_smoking.json
[family-blast-recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/blast_furnace.json
[family-smoker-recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/smoker.json
[family-logs]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/item/logs.json
[family-logs-burn]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[family-crimson]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[family-warped]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/item/warped_stems.json
[family-pattern]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[family-blocks]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java
[family-pickaxe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[family-wood]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[family-stone-tier]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[family-iron-tier]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[family-diamond-tier]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[family-blast-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/blast_furnace.json
[family-smoker-loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/smoker.json
[family-blockentity]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java
[family-block]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/AbstractFurnaceBlock.java
[family-menu]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java
[family-result]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/FurnaceResultSlot.java
[family-furnace-menu]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/AbstractFurnaceMenu.java
[family-poi]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java
[family-professions]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java
[family-recipes]: https://github.com/HungLo2020/MattMC/tree/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe
