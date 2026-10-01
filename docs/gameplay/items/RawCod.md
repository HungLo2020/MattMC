# Raw Cod

Raw Cod is a food item registered as `minecraft:cod`. It can be eaten, cooked, or used in the current [Grizzly Bear](../mobs/GrizzlyBear.md) fish interactions.

## Obtaining

The [Cod](../mobs/Cod.md) loot table provides one cod item. It is converted to cooked cod when the fish is on fire or its direct attacker's held item meets the smelts-loot enchantment condition. This page covers that verified source, rather than claiming a complete list of fishing, trading, and chest-loot routes.

Raw Cod is also listed in the Creative food inventory.

## Eating and cooking

Eating Raw Cod supplies **2 hunger points** (one hunger icon) and **0.4 saturation** before the player's food caps. Cooking it improves both values; see [Cooked Cod](CookedCod.md) for furnace, smoker, and campfire recipes.

## Grizzly Bear use

Both raw and cooked cod belong to MattMC's fish tag. Grizzlies are attracted by this tag, and dropped fish are used in their current taming sequence. Tamed bears also accept it as breeding food, but their offspring behavior is currently incorrect. Follow the [Grizzly Bear guide](../mobs/GrizzlyBear.md#taming-and-care) for the important timing and breeding caveats.

This is a verified use, not a complete catalogue of every animal interaction with cod.

## Related pages

- [Cooked Cod](CookedCod.md)
- [Cod mob](../mobs/Cod.md)
- [Items](Items.md)

## Sources and verification

Reviewed against source snapshot `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. These are source-defined rules, not an in-game test.

- [Cod drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/cod.json)
- [Food registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/Foods.java#L14-L17)
- [Saturation formula](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/FoodConstants.java)
- [Fish tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/fishes.json)
- [Grizzly interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java)
- [Creative foods](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
