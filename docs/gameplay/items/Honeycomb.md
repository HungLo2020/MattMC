# Honeycomb

Honeycomb is a crafting and waxing item registered as `minecraft:honeycomb`. It is **not registered as food** in this snapshot; do not confuse it with a Honey Bottle.

## Harvesting

Use [Shears](Shears.md) on a [Beehive](Beehive.md) or [Bee Nest](BeeNest.md) at **honey level 5**. The harvest loot table produces **three Honeycomb**, the interaction uses one point of shears durability, and the hive's honey level resets to zero.

Provide campfire smoke at the hive before harvesting. If the location is not recognized as smoky, harvesting can anger nearby bees and trigger an emergency bee release. Smoke is a check on the hive's position, so a campfire merely somewhere nearby is not sufficient.

Grizzly Bears also raid honey-filled hives in their current behavior. Keep an apiary separated from bears rather than relying on that destructive interaction as your regular collection method.

## Waxing and crafting

- Use honeycomb on a supported unwaxed copper block to convert it to its matching waxed form while retaining its block-state properties. The mapping covers several copper families, including blocks, cut blocks, doors, trapdoors, grates, bulbs, chests, statues, lightning rods, bars, chains, and lanterns.
- Apply it to a sign to set its waxed state, preventing further ordinary text editing.
- Place one String directly above one Honeycomb to craft **one Candle**.
- Craft **one Beehive** with a row of three Honeycomb between two rows of three planks.
- Arrange four Honeycomb in a 2 × 2 square to craft **one Honeycomb Block**.

This is a useful selection of verified uses, not an exhaustive copper-recipe list.

## Related pages

- [Grizzly Bear](../mobs/GrizzlyBear.md)
- [Candle](Candle.md)
- [Honeycomb Block](HoneycombBlock.md)
- [Items](Items.md)

## Sources and verification

Reviewed against source snapshot `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. These are source-defined rules, not an in-game test.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L2462)
- [Harvest interaction and smoke check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L129-L189)
- [Harvest count](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/harvest/beehive.json)
- [Waxed-sign editing check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/SignBlock.java#L90-L125)
- [Waxing and signs](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/HoneycombItem.java)
- [Candle recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/candle.json)
- [Beehive recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/beehive.json)
- [Honeycomb block recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/honeycomb_block.json)
- [Bear hive interaction](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/ai/GrizzlyBearAIBeehive.java)
