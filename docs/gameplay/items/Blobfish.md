# Blobfish food item

The Blobfish item is a food registered as `minecraft:blobfish`. It is separate from the live [Blobfish mob](../mobs/Blobfish.md) and its carrying bucket.

## Eating

Eating it supplies **3 hunger points** (1.5 hunger icons) and **2.4 saturation** before the player's food caps. Its consumption definition also applies **Poison I for 120 ticks** (6 seconds at 20 ticks per second), with a probability of 1.0 in the effect constructor.

It is therefore a hazardous food choice despite restoring hunger. The effect is part of the item's registered consumable, not merely an upstream description.

## Obtaining

The item is present in the registry and Creative food inventory. No dedicated blobfish death-loot table or cooking recipe was found in the checked active data. Do not assume killing a blobfish supplies this food, or that cooking it removes the poison.

## Related pages

- [Live Blobfish](../mobs/Blobfish.md)
- [Bucket of Blobfish](BucketOfBlobfish.md)
- [Cooked Cod](CookedCod.md)
- [Items](Items.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1667)
- [Food value](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/Foods.java#L9)
- [Poison consumable](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java#L23-L25)
- [Default effect probability](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java)
- [Creative inventory](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
