# Regeneration

Regeneration periodically restores **one health point** (half a heart) while the target is below its maximum health. It is a status effect, separate from the natural healing controlled by hunger and the natural-regeneration game rule.

## Timing

| Effect level | Tick interval | At normal tick speed |
| --- | ---: | ---: |
| Regeneration I | 50 ticks | 2.5 seconds |
| Regeneration II | 25 ticks | 1.25 seconds |

The checked effect method tests current versus maximum health and heals; it does not check the food bar. This does not remove hunger's other consequences.

## Potions and removal

The brewing registry uses Ghast Tear with Awkward Potion for Regeneration. The potion definitions provide 900 ticks for base Regeneration, 1,800 for the extended form, and 450 for Regeneration II: **45 seconds, 90 seconds, and 22.5 seconds** before delivery-specific handling.

[Milk](../items/MilkBucket.md) clears this beneficial effect along with harmful effects. If Poison is the only effect you want to remove, the [Honey Bottle](../items/HoneyBottle.md) uses a Poison-specific removal action instead.

## Related pages

- [Hunger and natural healing](../mechanics/Hunger.md)
- [Brewing](../brewing/Brewing.md)
- [Effects](Effects.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game effect or consumption test was run.

- [Healing intervals](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/effect/RegenerationMobEffect.java)
- [Potion definitions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L60-L65)
- [Brewing mixes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java)
- [All-effects removal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java)
