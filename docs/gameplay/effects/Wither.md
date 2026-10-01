# Wither

Wither periodically attempts to deal one health point of wither damage. Unlike [Poison](Poison.md), its effect method has **no low-health cutoff**, so it can be lethal when damage applies.

## Timing

| Effect level | Tick interval | At normal tick speed |
| --- | ---: | ---: |
| Wither I | 40 ticks | 2 seconds |
| Wither II | 20 ticks | 1 second |

These are the effect method's intervals, not a guarantee of damage against an immune or otherwise protected entity.

## Verified source and removal

A successful Wither Skeleton melee hit applies a 200-tick Wither effect to a living target in the checked implementation: **10 seconds** at 20 ticks per second. That is one verified source, not an exhaustive list of every Wither application.

[Milk](../items/MilkBucket.md) invokes general effect removal. [Honey Bottles](../items/HoneyBottle.md) remove Poison specifically; their registered consumption effect is not a Wither cure.

## Related pages

- [Wither Skeleton](../mobs/WitherSkeleton.md)
- [Regeneration](Regeneration.md)
- [Effects](Effects.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game effect or consumption test was run.

- [Damage and intervals](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/effect/WitherMobEffect.java)
- [Skeleton application](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/WitherSkeleton.java#L94-L103)
- [Milk/Honey definitions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java)
