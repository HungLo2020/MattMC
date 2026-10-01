# Glass Bottle

Glass Bottle is an empty container registered as `minecraft:glass_bottle`. It can collect water for brewing, honey from a full hive, or qualifying dragon-breath clouds.

## Crafting and filling with water

Place three Glass blocks in a V-shaped arrangement: two separated across the upper row and one centered beneath them. The recipe produces **three Glass Bottles**.

Use a bottle while targeting a water source. The item checks interaction permission and water-tag fluid, then creates a Water Bottle. That is the ordinary starting container for [brewing](../brewing/Brewing.md).

## Other collection uses

- Use a Glass Bottle on a Beehive or Bee Nest at honey level 5 to collect a Honey Bottle. Hive harvesting has bee-release and smoke conditions; see [Honeycomb](Honeycomb.md#harvesting) for the shared hive-safety context.
- Near a living area-effect cloud owned by an Ender Dragon, the bottle can create Dragon's Breath and reduce the cloud's radius by 0.5. This is a specific owner/type check, not a way to bottle every lingering-effect cloud.

The item checks for qualifying nearby dragon clouds before its water-targeting branch. These behaviors are source-reviewed; this page does not promise safe access to a dragon encounter.

## Related pages

- [Brewing Stand](../blocks/BrewingStand.md)
- [Nether Wart](NetherWart.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game brewing, growth, or collection test was run.

- [Bottle recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/glass_bottle.json)
- [Water and dragon-cloud collection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/BottleItem.java)
- [Honey collection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L147-L189)
