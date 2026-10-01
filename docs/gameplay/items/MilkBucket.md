# Milk Bucket

Milk Bucket is a drinkable item registered as `minecraft:milk_bucket`. It stacks to **one** and clears active status effects through its registered consumption action.

## Obtaining

Use an empty Bucket on an adult [Cow](../mobs/Cow.md). The interaction returns a Milk Bucket; calves do not satisfy the milking check. This is a verified source, not an exhaustive list of every milk-producing entity.

## Drinking

Consumption calls `removeAllEffects`, so it removes beneficial effects as well as harmful effects through that path. It is not a selective Poison-only remedy. Use a [Honey Bottle](HoneyBottle.md) when the registered Poison-specific removal is what you need.

The item converts to an empty Bucket through its use-remainder behavior. Its registration supplies a consumable action without a food-value component, so this page does not describe milk as restoring hunger.

## Related pages

- [Status effects](../effects/Effects.md)
- [Cow](../mobs/Cow.md)
- [Honey Bottle](HoneyBottle.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game effect or consumption test was run.

- [Milking interaction](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/AbstractCow.java#L83-L94)
- [Item and remainder](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533)
- [Consumable definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java#L64)
- [Effect removal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java)
