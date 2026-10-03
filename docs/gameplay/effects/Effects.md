# Status effects

Status effects temporarily change an entity's behavior, attributes, health, or other rules. An effect's **level**, **duration**, and **application method** are different properties. Drinking, splashing, and lingering delivery can apply different durations even when the potion type is related.

## Reviewed effects

- [Absorption](CombatEffects.md#absorption): temporary damage buffer and refill/expiry limits
- [Blindness](VisionEffects.md#blindness): restricted fog, sprinting, and ordinary critical hits
- [Breath of the Nautilus](WaterAndFireEffects.md#breath-of-the-nautilus): mount-applied status and its current air limitation
- [Conduit Power](WaterAndFireEffects.md#conduit-power): air protection, mining assistance, and lighting inputs
- [Darkness](VisionEffects.md#darkness): blended fog, pulsing light, and source refresh rules
- [Debilitating Sting](../mobs/TarantulaHawk.md#sting-and-prey): sting duration, arthropod gates, and player-motion limits
- [Dolphin's Grace](MovementEffects.md#dolphins-grace): horizontal water-momentum retention
- [Fire Resistance](WaterAndFireEffects.md#fire-resistance): fire-tagged damage rejection and lava-fog behavior
- [Glowing](VisibilityEffects.md#glowing): team-colored outlines, Spectral Arrows, and native rendering limits
- [Haste](MiningEffects.md#haste): mining-speed bonus, attack recharge, and Conduit interaction
- [Health Boost](CombatEffects.md#health-boost): maximum ordinary health without immediate healing
- [Hunger](HungerAndSaturation.md#hunger): player exhaustion, food sources, and Husk-hit conditions
- [Instant Damage](InstantEffects.md#instant-damage): instant magic damage, inverted healing, and delivery limits
- [Instant Health](InstantEffects.md#instant-health): immediate healing, inverted damage, and delivery limits
- [Invisibility](VisibilityEffects.md#invisibility): AI detection, armor coverage, and selected body rendering
- [Jump Boost](MovementEffects.md#jump-boost): ground-jump power and safe-fall-distance contributions
- [Levitation](MovementEffects.md#levitation): upward air movement and the Elytra restriction
- [Luck](LuckAndUnluck.md#luck): player luck, fishing weights, and potion/browser access
- [Mining Fatigue](MiningEffects.md#mining-fatigue): level-specific mining penalties, attack recharge, and removal
- [Nausea](VisibilityEffects.md#nausea): food and mob sources, overlay settings, and the current distortion limitation
- [Night Vision](VisionEffects.md#night-vision): rendered brightness, sources, and near-expiry flicker
- [Orca's Might](../mobs/Orca.md#swimming-together-and-orcas-might): companion-granted attack speed and refresh rules
- [Poison](Poison.md): periodic damage with a low-health cutoff
- [Regeneration](Regeneration.md): periodic healing, separate from food-based healing
- [Resistance](CombatEffects.md#resistance): eligible damage reduction and bypasses
- [Saturation](HungerAndSaturation.md#saturation): repeated food replenishment and stew-duration limits
- [Slow Falling](MovementEffects.md#slow-falling): descending gravity and fall-distance handling
- [Slowness](MovementEffects.md#slowness): reduced movement-speed attribute
- [Speed](MovementEffects.md#speed): increased movement-speed attribute
- [Strength](CombatEffects.md#strength): added attack-damage attribute and hit limitations
- [Sunbird Blessing](../mobs/Sunbird.md#sunbird-blessing): nearby-player grant, fall-distance reset, and motion limits
- [Sunbird Curse](../mobs/Sunbird.md#sunbird-curse): retaliation, Phantom scorching, and player glide interruption
- [Tiger's Blessing](../mobs/Tiger.md#feeding-and-tigers-blessing): dropped-food chances and Tiger target protection
- [Unluck](LuckAndUnluck.md#unluck): reduced player luck, commands, and loot-context limits
- [Water Breathing](WaterAndFireEffects.md#water-breathing): ordinary underwater air protection and recovery
- [Weakness](CombatEffects.md#weakness): reduced attack-damage attribute, curing, and other sources
- [Wither](Wither.md): periodic damage without Poison's low-health cutoff

The [movement effects reference](MovementEffects.md) compares acquisition, movement rules, and effect interactions.

The [combat effects reference](CombatEffects.md) compares attack modifiers, damage reduction, and extra health.

The [water and fire effects reference](WaterAndFireEffects.md) compares fire damage, breathing, Conduit benefits, and the Nautilus effect limitation.

The [vision effects reference](VisionEffects.md) compares Night Vision, Blindness, Darkness, and their rendering and player-action limits.

The [Invisibility, Glowing, and Nausea reference](VisibilityEffects.md) compares detection, selected body rendering, outlines, and the current Nausea presentation limitation.

The [instant effects reference](InstantEffects.md) compares Healing and Harming, recipient inversion, and drinking, splash, cloud, and arrow delivery.

The [Hunger and Saturation effect guide](HungerAndSaturation.md) separates status-effect ticks from food values and compares their sources.

The [mining effects reference](MiningEffects.md) compares Haste and Mining Fatigue, their combined mining factors, and separate attack and swing timing.

The [Luck and Unluck reference](LuckAndUnluck.md) separates player luck, fishing enchantments, loot-table arithmetic, and acquisition routes.

The five mob-granted entries above, **Orca's Might, Debilitating Sting, Tiger's Blessing, Sunbird Blessing, and Sunbird Curse**, use distinct registered effect IDs. Their encounter routes and integration limits belong to the linked mob guides. The checked ordinary potion types and brewing recipes do not include these five; an effect registration alone is not a brewing recipe. [Distinct registrations][custom-effect-registry] · [Potion types][custom-effect-potions] · [Brewing registrations][custom-effect-brewing]

This is a growing reference, not a complete list of all registered effects. The [brewing guide](../brewing/Brewing.md) provides verified potion chains and selected effect durations.

## Clearing effects

[Milk](../items/MilkBucket.md) calls the all-effects removal path, so it can remove beneficial effects as well as harmful ones. A [Honey Bottle](../items/HoneyBottle.md) specifically removes Poison instead. Choose the remedy based on the effect rather than treating every drink as interchangeable.

Milk also removes these five mob-granted effects. Clearing Tiger's Blessing ends its targeting protection, and removing a Sunbird effect while still nearby can allow another blessing at a later check. [All-effects removal][custom-effect-clear] · [Tiger target check][custom-effect-tiger] · [Sunbird grant check][custom-effect-sunbird]

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

The five mob-granted entries and their shared removal/brewing limits were additionally source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on **2026-10-02**. No in-game effect, flight, movement, or consumption test was run.

[custom-effect-registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L129-L148
[custom-effect-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[custom-effect-brewing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[custom-effect-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L952
[custom-effect-tiger]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L430-L433
[custom-effect-sunbird]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L197-L207
