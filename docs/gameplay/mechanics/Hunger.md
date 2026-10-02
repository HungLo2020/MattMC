# Hunger, saturation, and healing

Food manages two related resources: visible hunger and an additional saturation reserve. Better food can restore the same hunger while providing more reserve, and some foods also apply harmful effects.

## Food values

For the complete registered item comparison, see [Food reference](../items/FoodReference.md). This page owns the shared food caps, exhaustion and recovery rules.

Hunger is capped at **20 points** (ten hunger icons). When food adds saturation, the result is capped at the current hunger level. When a food uses a saturation modifier, its contribution is `nutrition × modifier × 2` before those caps.

Examples from MattMC's food registrations:

| Food | Hunger points | Saturation contribution before caps |
| --- | ---: | ---: |
| [Raw Cod](../items/RawCod.md) | 2 | 0.4 |
| [Cooked Cod](../items/CookedCod.md) | 5 | 6 |
| [Trilocaris Tail](../items/TrilocarisTail.md) | 2 | 1.2 |
| [Cooked Trilocaris Tail](../items/CookedTrilocarisTail.md) | 5 | 5 |
| [Bread](../items/Bread.md) | 5 | 6 |
| [Steak](../items/Steak.md) | 8 | 12.8 |

[Blobfish food](../items/Blobfish.md) also restores food but applies Poison in the current consumable definition. Hunger value alone does not establish that a food is safe.

## Exhaustion and sprinting

Actions can add exhaustion. When exhaustion exceeds 4, the food tick subtracts 4 exhaustion and spends one saturation point first. With no saturation, it spends one hunger point outside Peaceful difficulty.

The ordinary local-player sprint food check requires **more than 6 hunger points**. Passenger and flight-ability paths are exceptions; that threshold is not a promise that food is the only condition governing sprinting.

Peaceful recovery uses a separate saturation setter; see [Health](Health.md#recovering-safely). [Food-add cap](https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30) · [Peaceful recovery](https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L740-L758)

## Natural healing

With the natural-regeneration game rule enabled:

- At full hunger and with saturation remaining, an injured player uses the faster saturation-healing path, checked every 10 ticks.
- At hunger **18 or higher**, the ordinary healing path restores one health point every 80 ticks and adds exhaustion.

These timings assume the player is ticking normally. One health point is half a heart. Healing itself adds exhaustion, so a full food bar can drop while recovering.

## Starvation

At zero hunger, the starvation check runs every 80 ticks. Its difficulty conditions allow damage down to 10 health points on Easy, down to 1 on Normal, and potentially death on Hard. This table describes the starvation path, not every other source of damage or Peaceful's separate recovery behavior.

Carry food before exploring or fighting rather than waiting until you can no longer sprint. [Cooking](../smelting/Smelting.md) often improves the value of an existing food supply.

## Related pages

- [Health, damage, and recovery](Health.md)

- [Survival](../gamemodes/Survival.md)
- [Smelting and cooking](../smelting/Smelting.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Rules below describe checked code, not a running-world test or every server override.

- [Food caps, exhaustion, healing and starvation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/FoodData.java)
- [Food values](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/Foods.java)
- [Saturation formula](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/FoodConstants.java)
- [Sprinting food condition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1029-L1031)
- [Food consumption effects](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java)
