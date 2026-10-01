# Cooked Cod

Cooked Cod is a food registered as `minecraft:cooked_cod`. Cooking raw cod raises its hunger restoration from 2 to **5 points** and its saturation contribution from 0.4 to **6**, before food caps.

## Cooking

Each recipe turns one [Raw Cod](RawCod.md) into one Cooked Cod:

| Device | Recipe time | Time at 20 ticks per second |
| --- | --- | --- |
| Furnace | 200 ticks | 10 seconds |
| Smoker | 100 ticks | 5 seconds |
| Campfire | 600 ticks | 30 seconds |

Each recipe declares 0.35 experience. The recipe field alone does not mean every cooking device pays experience in the same way.

A cod killed while on fire, or by a direct attacker whose held item matches the smelts-loot enchantment condition, also has its cod drop furnace-smelted by the loot table.

## Uses

Eat it to restore hunger, or use it in the [Grizzly Bear fish interactions](RawCod.md#grizzly-bear-use). Cooking does not remove cod from the fish tag used by those interactions. Those bear interactions currently accept several fish, so reserve cooked food for yourself when raw fish are available.

## Related pages

- [Raw Cod](RawCod.md)
- [Grizzly Bear](../mobs/GrizzlyBear.md)
- [Items](Items.md)

## Sources and verification

Reviewed against source snapshot `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. These are source-defined rules, not an in-game test.

- [Furnace recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smelting/cooked_cod.json)
- [Smoker recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smoking/cooked_cod_from_smoking.json)
- [Campfire recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_cod_from_campfire_cooking.json)
- [Cod drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/cod.json)
- [Food values](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/Foods.java#L14-L17)
- [Fish tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/fishes.json)
