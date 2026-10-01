# Furnace

A Furnace processes **smelting recipes** using fuel. It has an input slot, fuel slot, and output slot. Its ID is `minecraft:furnace`.

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
