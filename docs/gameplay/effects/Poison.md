# Poison

Poison periodically attempts to deal one health point of magic damage while the target's health is **above 1 point**. It does not make that damage attempt at health 1 or below. This threshold is distinct from a universal guarantee about every other source of damage.

## Timing

| Effect level | Tick interval in the checked method | At normal tick speed |
| --- | ---: | ---: |
| Poison I | 25 ticks | 1.25 seconds |
| Poison II | 12 ticks | 0.6 seconds |

The interval is calculated with integer shifting, so Poison II's interval is 12 rather than 12.5 ticks. Individual susceptibility and damage handling can still affect the result.

## Verified sources and removal

Eating [Blobfish food](../items/Blobfish.md) applies Poison I for 120 ticks. The brewing registry also contains Poison potions; the base Poison potion's duration is 900 ticks (45 seconds) before delivery-specific handling.

Drink a [Honey Bottle](../items/HoneyBottle.md) to remove Poison specifically, or [Milk](../items/MilkBucket.md) to clear all removable current effects through the general removal path. Milk also clears beneficial effects.

For hostile delivery routes, see [Witch splash potions](../mobs/Witch.md#which-potion-it-throws) and [Bogged arrows](../mobs/Bogged.md#poison-arrows-and-fighting); their delivery rules and durations differ.

## Related pages

- [Wither](Wither.md), which has a different low-health rule
- [Effects](Effects.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game effect or consumption test was run.

- [Damage cutoff and intervals](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/effect/PoisonMobEffect.java)
- [Blobfish and Honey consumption](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java)
- [Potion durations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L57-L59)
- [Targeted effect removal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/consume_effects/RemoveStatusEffectsConsumeEffect.java)
