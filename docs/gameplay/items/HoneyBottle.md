# Honey Bottle

Honey Bottle is a food/drink registered as `minecraft:honey_bottle`. It restores hunger and specifically removes Poison, unlike Milk's all-effects removal.

## Obtaining

Use a Glass Bottle on a Beehive or Bee Nest at honey level 5. The hive resets its honey level; smoke and bee-release conditions still apply. See [Honeycomb harvesting](Honeycomb.md#harvesting) for the shared hive-safety context.

A shapeless recipe also combines **one Honey Block and four Glass Bottles** into **four Honey Bottles**.

## Drinking and crafting

- Food value: **6 hunger points** and **1.2 saturation** before caps
- Can be consumed even when the food bar is full
- Registered consumption duration: **2 seconds**
- Removes **Poison** specifically, not Wither or every harmful effect
- Converts to an empty Glass Bottle after ordinary consumption
- Stacks to **16**

One Honey Bottle in a shapeless recipe produces **three Sugar**. The item also carries a Glass Bottle crafting-remainder registration.

## Related pages

- [Poison](../effects/Poison.md)
- [Milk Bucket](MilkBucket.md)
- [Glass Bottle](GlassBottle.md)
- [Items](Items.md)

See [Bee housing](../blocks/BeeHousing.md#harvesting) for shared manual/dispenser harvesting and smoke conditions.

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game effect or consumption test was run.

- [Item/stack/remainder](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L2477-L2479)
- [Food properties](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/Foods.java)
- [Poison removal and consume duration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20)
- [Hive collection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L147-L189)
- [Bottling recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/honey_bottle.json)
- [Sugar recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/sugar_from_honey_bottle.json)
