# Status effects

Status effects temporarily change an entity's behavior, attributes, health, or other rules. An effect's **level**, **duration**, and **application method** are different properties. Drinking, splashing, and lingering delivery can apply different durations even when the potion type is related.

## Reviewed effects

- [Dolphin's Grace](MovementEffects.md#dolphins-grace): horizontal water-momentum retention
- [Jump Boost](MovementEffects.md#jump-boost): ground-jump power and safe-fall-distance contributions
- [Levitation](MovementEffects.md#levitation): upward air movement and the Elytra restriction
- [Poison](Poison.md): periodic damage with a low-health cutoff
- [Regeneration](Regeneration.md): periodic healing, separate from food-based healing
- [Slow Falling](MovementEffects.md#slow-falling): descending gravity and fall-distance handling
- [Slowness](MovementEffects.md#slowness): reduced movement-speed attribute
- [Speed](MovementEffects.md#speed): increased movement-speed attribute
- [Wither](Wither.md): periodic damage without Poison's low-health cutoff

The [movement effects reference](MovementEffects.md) compares acquisition, movement rules, and effect interactions.

This is a growing reference, not a complete list of all registered effects. The [brewing guide](../brewing/Brewing.md) provides verified potion chains and selected effect durations.

## Clearing effects

[Milk](../items/MilkBucket.md) calls the all-effects removal path, so it can remove beneficial effects as well as harmful ones. A [Honey Bottle](../items/HoneyBottle.md) specifically removes Poison instead. Choose the remedy based on the effect rather than treating every drink as interchangeable.

Item sources can have side effects: [Blobfish food](../items/Blobfish.md), for example, restores hunger but also applies Poison. Food values alone are not an effect description.

## Timing and limits

The detailed pages use game ticks; 20 ticks equal one second at normal speed. Actual damage/healing still passes through entity health, damage, and immunity rules. Tables describe the checked effect method, not guaranteed damage against every creature or modified server.

- [Hunger and natural healing](../mechanics/Hunger.md)
- [Brewing](../brewing/Brewing.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game effect or consumption test was run.

- [Effect instances and duration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/effect/MobEffectInstance.java)
- [Consumption effect definitions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Consumables.java)
- [Potion effects](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/Potions.java)
