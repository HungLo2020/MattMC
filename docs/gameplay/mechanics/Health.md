# Health, damage, and recovery

An ordinary player has **20 health points, shown as ten hearts**. One health point is half a heart. Health, armor, hunger, and absorption are separate resources: a full armor or food bar does not mean your hearts are full. Modified attributes can change maximum health. [Player attributes][player-attributes] · [Default maximum][attributes] · [Heart display][hearts]

## Reading damage correctly

The number attached to an attack is not necessarily the number of health points you lose. The ordinary player path checks immunity and difficulty, then blocking and damage-specific rules, followed by armor, protective effects/enchantments, absorption, and remaining health loss. Some damage types bypass particular defenses. [Player damage][damage] · [Blocking and hit handling][living-damage] · [Protection stages][protection]

- [Armor](Armor.md) reduces eligible damage while functional equipment is worn
- [Shields](DefensiveItems.md) require active blocking and have direction, delay, and bypass limits
- Absorption is a separate reserve spent before ordinary health at the final player damage stage
- A held [Totem of Undying](../items/TotemOfUndying.md) can prevent a qualifying lethal hit; merely carrying one elsewhere does not provide that protection

For example, if **5 damage points reach the absorption stage** and you have **3 absorption points**, those three are spent and **2 health points, or one heart**, are lost. This example starts after the other defenses have already been evaluated. [Absorption subtraction][absorption] · [Lethal-hit protection][totem]

## Difficulty and repeated hits

Difficulty changes only attacks whose damage source uses difficulty scaling. For a scaled **6-point hit**, the player difficulty stage produces **4 on Easy, 6 on Normal, and 9 on Hard**, before later defenses; Peaceful reduces that scaled hit to zero. Do not apply this rule to every environmental hazard. Separate game rules control the player's drowning, fall, fire, and freezing immunity checks. [Difficulty and rule gates][damage]

An ordinary hit also starts a short repeated-hit cooldown. While its remaining timer is above ten ticks, a later non-bypassing hit at or below the previous comparison value is rejected; a stronger one sends only the difference into the damage calculation. Damage types can bypass that cooldown. This is a reason two rapid hits need not remove two full attacks' worth of health, not a safe interval for remaining in danger. [Repeated-hit comparison][living-damage]

## Recovering safely

Move out of the hazard before trying to recover. Food-driven healing needs both a living, injured player and the **naturalRegeneration** game rule, which defaults to enabled. The [Hunger guide](Hunger.md#natural-healing) owns the exact food thresholds and timing: full hunger with saturation uses the faster path, while at least 18 hunger points supports the slower path. Healing spends exhaustion, so continuing recovery can reduce your food reserve. [Food/healing loop][food] · [Rule default][rules]

**Peaceful has an additional recovery path** under the same natural-regeneration rule: it attempts one health point every 20 player ticks, increases hunger by one every 10 ticks when needed, and replenishes saturation separately. That direct health recovery does not require the food bar to be full. These are ticking intervals, not measured wall-clock guarantees. [Active player regeneration call][regen-call] · [Peaceful recovery][peaceful]

Healing is capped by maximum health. Ordinary healing does not revive a player already at zero health. Use [Regeneration](../effects/Regeneration.md) and the other [effect guides](../effects/Effects.md) for effect-specific recovery and removal rules, and [Death and respawn](DeathAndRespawn.md) for what follows a failed recovery. [Health clamp and living check][health]

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. The active server player, food tick, damage, and health paths were checked; no running-world combat, healing, timing, or HUD test was performed. Custom attributes, damage tags, effects, game rules, and later builds can change these outcomes. This guide does not replace the item-specific armor, Shield, Totem, food, or effect guides.

Related: [Survival](../gamemodes/Survival.md) · [Hunger](Hunger.md) · [Defensive items](DefensiveItems.md) · [Mechanics](Mechanics.md)

[player-attributes]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L235
[attributes]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L54-L59
[hearts]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/client/gui/Gui.java#L996-L1008
[damage]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L750
[living-damage]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1239
[protection]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1828
[absorption]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L789-L811
[food]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/food/FoodData.java#L32-L71
[rules]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/GameRules.java#L69-L71
[regen-call]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L461-L468
[peaceful]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L740-L758
[totem]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1379
[health]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1144
