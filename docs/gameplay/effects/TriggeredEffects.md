# Wind Charged, Weaving, Oozing, and Infested

**Wind Charged, Weaving, and Oozing add consequences to a death; Infested can release Silverfish when the affected creature is hurt.** Weaving also makes Cobweb movement less restrictive while the bearer is alive. Keep room around affected enemies, and clear your own unwanted effects before taking damage. All four are registered as harmful effects, even when their output is useful. [Registrations][register] · [Active hurt dispatch][hurt-dispatch] · [Active removal dispatch][removal] · [Instance callbacks][effect-dispatch] · [Cobweb movement][web-movement]

## Getting and applying the effects

Start with a **Water Bottle**, brew **Nether Wart** to make an **Awkward Potion**, then add the relevant ingredient:

- **Wind Charged:** [Breeze Rod](../items/BreezeRod.md)
- **Weaving:** [Cobweb](../blocks/Cobweb.md)
- **Oozing:** [Slime Block](../blocks/SlimeAndHoneyBlocks.md)
- **Infested:** ordinary [Stone](../blocks/Stone.md)

Adding any of these four ingredients directly to Water makes a **Mundane Potion**, not the intended effect. **Blaze Powder fuels the stand**; the Wind Charged ingredient is a **Breeze Rod**, not Blaze Powder or the throwable [Wind Charge](../items/WindCharge.md). The ordinary recipe registrations supply no Redstone extension or Glowstone upgrade for these four potions. See [Brewing](../brewing/Brewing.md) and [Brewing Stand](../blocks/BrewingStand.md) for the workflow. [Base recipes][brewing-start] · [Start-mix inputs][start-mix] · [Complete ordinary mix registration][brewing-all] · [Fuel slot][fuel-use] · [Bundled fuel tag][fuel-tag]

Each brewed potion supplies **level I for 3,600 ticks, or 3 minutes at 20 ticks per second**. Their checked trigger methods do not use the amplifier: higher command-supplied levels do not raise burst strength, Cobweb/Slime counts, or the Infested chance. Weaving's movement benefit also tests only whether the effect is present. [Potion definitions][potions] · [Default amplifier][amplifier] · [Wind trigger][wind] · [Weaving trigger][weaving] · [Oozing trigger][oozing] · [Infested trigger][infested] · [Movement check][web-movement]

The ordinary delivery choices are:

- **Drink:** applies the full 3-minute effect to the drinker through ordinary effect admission. [Consumption][drink] · [Effect application][drink-apply]
- **Splash:** Gunpowder changes the container. Duration decreases with distance between the impact box and the target's expanded box, reaching the full duration at zero separation; applications of **20 ticks or less are skipped**. A splash does not apply a fixed 3 minutes to every nearby target. [Container recipes][brewing-start] · [Splash distance and duration][splash]
- **Lingering:** Dragon's Breath changes a Splash Potion into a Lingering Potion. A qualifying cloud application gives **900 ticks, or 45 seconds**, from the item's quarter-duration factor. The ordinary thrown cloud waits **10 ticks**, checks recipients every **5 ticks**, and has a **20-tick per-recipient reapplication delay**; its radius shrinks over time and on use. Those cloud timings are separate from the effect's trigger rules. [Item duration factors][delivery-factors] · [Cloud creation][lingering] · [Cloud component transfer][cloud-components] · [Reapplication default][cloud-default] · [Cloud admission and refresh][cloud]
- **Tipped arrow:** surround the Lingering Potion with eight Arrows to obtain eight tipped arrows. The ordinary arrow's one-eighth factor gives **450 ticks, or 22.5 seconds** after a qualifying hit. The arrow's damage happens **before** it adds the effect: a newly applied Infested effect does not roll retroactively for that same hit. [Loaded recipe][arrows-data] · [Recipe][arrows-recipe] · [Item factor][delivery-factors] · [Hit ordering][arrow-hit] · [Effect application][arrow-effect]

Splash and cloud delivery also check whether the entity accepts potions, then ordinary effect admission can reject the particular effect. In the bundled immunity tags, **Silverfish reject Infested** and **Slimes reject Oozing**. This prevents those two same-species effect loops; it does not make their offspring friendly. [Splash admission][splash] · [Cloud admission][cloud] · [Effect admission][admission] · [Silverfish immunity][infested-immune] · [Slime immunity][oozing-immune]

### Ominous trials

An active ominous [Trial Spawner](../blocks/TrialSpawner.md#becoming-ominous) can launch lingering potions of all four effects from floating spawners above registered players or tracked mobs. They are **incoming potion hazards**, not bottles awarded to your inventory. The bundled pool chooses one of seven lingering-potion types when its choices are generated; the spawner caches the rolled items and subsequently weights them by stack count. Do not interpret this as an independent one-in-seven roll for a particular effect on every overhead attack. [Bundled potion pool][trial-potions] · [Default hazard table][trial-default] · [Cached selection][trial-cache] · [Active ominous branch][trial-active] · [Target and position selection][trial-position] · [Downward release][trial-release] · [Potion projectile construction][trial-potion-projectile]

The ordinary interval after a successful floating-spawner creation is **160 ticks**. The floating entity then waits **60–120 ticks** before release. Leaving a cloud matters: clearing an effect while still standing in an eligible cloud can allow it to be applied again. Effect-created Slimes and Silverfish are not added to the trial's tracked wave set by their spawn callbacks, so a completed wave does not prove that every new creature is gone. [Interval][trial-interval] · [Creation timer][trial-create] · [Release delay][trial-delay] · [Cloud refresh][cloud] · [Tracked wave additions][trial-active] · [Slime creation][oozing] · [Silverfish creation][infested]

## Wind Charged

Effect ID: `minecraft:wind_charged`.

On the killed-effect trigger, the bearer produces a burst centered **halfway up its body**. Its random strength parameter is **3 + 2 × a random float**, approximately 3–5; it does not scale with effect level or body size. This is an explosion parameter, not a guaranteed jump height or uniform block radius. Entity reach is calculated from twice that parameter, with distance falloff, obstruction exposure, and explosion-knockback resistance reducing the push. The bearer itself is excluded from that entity query. [Burst setup][wind] · [Entity selection and push][explosion-entities] · [Exposure][explosion-exposure]

The burst **does not deal explosion damage or start fires**, but displacement into a fall or another hazard can still hurt. The calculator gives an actively flying player zero knockback. It uses trigger-style block interaction: the default block handler skips ordinary blast destruction, while eligible handlers can still react, such as toggling a Lever or extinguishing a lit Candle. Follow [Wind Charge block reactions](../items/WindCharge.md#direct-hits-and-the-burst) for the shared mechanism examples. [Calculator wiring][wind-calculator] · [Damage and flying gates][calculator] · [Trigger conversion][explosion-mode] · [Default block handler][block-default] · [Lever][lever] · [Candle][candle]

The **affected living entity is the burst's source**, not the potion thrower or killer. Consequently, the Breeze-projectile-specific `mobGriefing` restriction on block triggers does not disable this death burst's triggers. Its push also **does not grant the thrown Wind Charge projectile's special fall-damage allowance**. Wind Charged is a status, not a throwable charge, and being hit by an ordinary Breeze shot is not an application of this status. [Source selection][wind] · [Living source attribution][explosion-source] · [Trigger gate][trigger-gate] · [Fall-allowance type check][fall-allowance] · [Ordinary projectile hit][projectile-hit]

## Weaving

Effect ID: `minecraft:weaving`.

On the killed-effect trigger, Weaving chooses a target of **2 or 3 Cobwebs** and samples **up to 15 candidate positions** in the 3 × 3 × 3 cube centered on the bearer's block position. It accepts distinct, replaceable positions with a sturdy upper face on the block immediately below. The result can be fewer webs or none; it needs suitable supporting blocks and does not create them. It places ordinary Cobweb blocks rather than dropping Cobweb items. [Requested count][register] · [Selection and placement][weaving] · [Cube sampling][cube]

For a **non-player bearer**, placement requires `mobGriefing` enabled. A **player bearer** bypasses that particular check. The effect does not run a player block-use permission check; ordinary world block-setting limits, including build height and debug-world rejection, still apply. Bring Shears to collect the resulting blocks, following the existing [Cobweb harvesting guide](../blocks/Cobweb.md#collecting-cobwebs). [Bearer gate and placement call][weaving] · [World placement limits][set-block]

While Weaving is present, the Cobweb callback requests movement multipliers of **0.5 horizontally and 0.25 vertically**, instead of **0.25 and 0.05**. This eases passage without removing the web. It does not improve with level, and entity-specific exceptions still apply; [Cobweb movement](../blocks/Cobweb.md#slowing-collision-and-falling) covers Spiders, active player flight, and fall-distance handling. [Presence check and factors][web-movement]

## Oozing

Effect ID: `minecraft:oozing`.

On the killed-effect trigger, Oozing requests **two medium Slimes, size 2**, at the bearer's X/Z position and **0.5 blocks above its feet**. Both start at the same position with random facing, full size-2 health (**4 points, two hearts**), and their ordinary Slime behavior. They are new entities: the callback does not copy the bearer's effects, equipment, target, or ownership. See [Slime](../mobs/Slime.md#sizes-attacks-and-splitting) for their attacks and later splitting. [Requested count][register] · [Creation and position][oozing] · [Size initialization][slime-size]

The `maxEntityCramming` rule also limits this output. With a positive value, Oozing counts **Slimes only** whose boxes intersect the bearer's box expanded by **2 blocks in every direction**, excluding the bearer itself, and makes at most enough new Slimes to reach that limit, capped at two. With the default **24**, 22 or fewer nearby Slimes permit two; 23 permits one; 24 or more permit none. A value below 1 removes this effect's count limit. Other mob types do not use up this particular allowance. [Count and query][oozing] · [Bounding-box intersection][nearby-boxes] · [Integer clamp][integer-clamp] · [Default rule][cramming]

## Infested

Effect ID: `minecraft:infested`.

Infested has a **10% chance per qualifying hurt callback** to request **1 or 2 Silverfish**. It does not roll every tick, require a player attacker, scale the chance with damage, or require the bearer to die. The callback has no separate cooldown and no Oozing-style nearby-count cap. [Chance and count][register] · [Trigger method][infested]

The active damage path determines whether it reaches that roll:

- Damage rejected by invulnerability, an already dying state, or Fire Resistance's fire gate does not reach it
- During the ordinary damage cooldown, a hit at or below the previous damage amount can be rejected; a stronger admitted hit can still reach the roll
- A fully item-blocked hit does not reach the callback. Armor, Resistance, or Absorption reducing actual health loss to zero is not the same gate: the callback uses the earlier admitted-damage path, so losing a red heart is not required

These are the checked living-entity damage rules; creature-specific damage overrides can impose additional restrictions. [Early rejection and cooldown][hurt-gates] · [Post-damage trigger gate][hurt-dispatch] · [Armor, magic, and Absorption processing][health-damage]

A lethal admitted hit can roll **after death processing** if Infested is still present. Successful ordinary Totem protection instead clears current effects before this callback, so it removes Infested before that hit's roll. [Death-before-callback ordering][hurt-dispatch] · [Totem activation][totem] · [Totem replacement effects][totem-effects]

New Silverfish appear at the bearer's X/Z position and **half its body height above its feet**. Their initial velocity follows the bearer's look direction, scaled by 0.3 with an extra 1.5 vertical factor and a random horizontal rotation within a half-turn fan. There is no search for clear landing space or assignment to attack the bearer's attacker. They use ordinary Silverfish AI. **The effect itself does not convert Stone into infested blocks**; the spawned Silverfish's later hiding and neighbor-waking behavior is separate. See [Silverfish containment](../mobs/Silverfish.md#hiding-and-containment) before using this near masonry. [Creation and launch][infested] · [Ordinary targeting][silverfish-goals] · [Later block conversion][silverfish-merge]

## Spawn and removal limits

Oozing and Infested create their mobs directly. These routes do **not** run natural biome, light, slime-chunk, spawn-placement, or natural-cap checks, and do not consult `doMobSpawning` or `mobGriefing`. They also do not test collision clearance before insertion. Entity-type availability and ordinary entity insertion still matter; the callbacks do not guarantee a safe or lasting spawn. Both types are disallowed in Peaceful and are discarded by the normal mob despawn check, so bypassing a natural spawn predicate does not make them persist in Peaceful. [Slime creation][oozing] · [Silverfish creation][infested] · [Type creation][type-create] · [Server insertion][entity-add] · [Entity-manager admission][manager-add] · [Slime registration][slime-type] · [Silverfish registration][silverfish-type] · [Despawn check][despawn]

The three death effects require the **killed-effect trigger while the effect is still active**. In the ordinary living-entity path, reaching zero health starts death processing, and removal normally follows after **20 death ticks**. Effects continue ticking meanwhile and can expire before removal. Their callbacks do not require player kill credit or the mob-loot rule. Ordinary discard, despawning, and clearing an effect do not substitute for the killed trigger. [Ordinary death processing][die] · [Death tick][death-tick] · [Tick order][tick-order] · [Expiry removal][expiry] · [Removal dispatch][removal]

**Creeper detonation is an explicit extra route:** it performs its ordinary explosion, copies its active effects into a lingering cloud, calls the killed-effect dispatcher, then discards itself. An affected exploding Creeper can therefore produce the relevant death-effect output as well as its cloud; it does not need the usual 20-tick death removal. Its cloud applies a quarter of the copied remaining effect duration, not necessarily a fresh 45 seconds. [Creeper detonation and cloud][creeper]

Drink [Milk](../items/MilkBucket.md) to clear all four effects, along with any beneficial effects you have. Expiry and Milk removal do not fire the death or hurt callbacks, and removing Weaving does not remove Cobwebs already placed; likewise, clearing Oozing or Infested does not remove creatures already spawned. Leave an applying cloud to avoid being affected again. [Milk definition][milk] · [Consumption dispatch][milk-clear] · [Effect removal][clear] · [Expiry][expiry] · [Cloud refresh][cloud]

When several death effects coexist, their dispatcher does not establish a fixed order between the burst, Cobweb placement, and mob creation. Do not design around one of those always happening first. [Effect collection][effect-map] · [Dispatch][removal]

## Sources and verification

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked registrations, active hurt/removal and Creeper paths, effect admission and clearing, brewing and potion delivery, ominous-item creation, direct mob initialization, count/placement gates, and wind-burst block/knockback handling. No in-game brewing, damage, death, spawning, block-placement, cloud, timing, or farm test was run. Counts and timings describe source rules, not measured output rates. Data packs, item components, game rules, custom entities, and later code can change the bundled behavior. This guide does not promise a safe farm layout or catalogue every entity-specific damage, potion, or explosion override.

Related: [Status effects](Effects.md) · [Brewing](../brewing/Brewing.md) · [Breeze](../mobs/Breeze.md) · [Wind Charge](../items/WindCharge.md) · [Cobweb](../blocks/Cobweb.md) · [Slime](../mobs/Slime.md) · [Silverfish](../mobs/Silverfish.md) · [Trial Spawner](../blocks/TrialSpawner.md)

[register]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L119-L126
[effect-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L267-L273
[hurt-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1231-L1266
[removal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L704-L727
[web-movement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/WebBlock.java#L26-L36
[brewing-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L127-L145
[start-mix]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L235
[brewing-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L191
[fuel-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L96-L121
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/brewing_fuel.json#L1-L5
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L77-L80
[amplifier]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L53-L72
[wind]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/WindChargedMobEffect.java#L17-L40
[weaving]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/WeavingMobEffect.java#L25-L52
[oozing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/OozingMobEffect.java#L29-L66
[infested]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/InfestedMobEffect.java#L27-L50
[drink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L232-L235
[drink-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L154-L165
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L74
[delivery-factors]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2242-L2255
[lingering]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java#L31-L45
[cloud-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L38-L58
[cloud]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L213-L263
[cloud-components]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L381-L397
[arrows-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TippedArrowRecipe.java#L14-L48
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L421-L436
[arrow-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[admission]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1013
[infested-immune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/immune_to_infested.json#L1-L5
[oozing-immune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/immune_to_oozing.json#L1-L5
[trial-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/items_to_drop_when_ominous.json#L1-L114
[trial-cache]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L273-L298
[trial-active]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L72-L102
[trial-position]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L169-L211
[trial-release]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/OminousItemSpawner.java#L71-L105
[trial-interval]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerConfig.java#L58-L60
[trial-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L149-L167
[trial-delay]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/OminousItemSpawner.java#L37-L62
[explosion-entities]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/ServerExplosion.java#L169-L204
[explosion-exposure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/ServerExplosion.java#L77-L108
[wind-calculator]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java#L28-L31
[calculator]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/SimpleExplosionDamageCalculator.java#L18-L50
[explosion-mode]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1167
[block-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L176-L200
[lever]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/LeverBlock.java#L79-L95
[candle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/AbstractCandleBlock.java#L101-L110
[explosion-source]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/Explosion.java#L12-L25
[trigger-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/ServerExplosion.java#L291-L300
[fall-allowance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1272-L1278
[projectile-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java#L72-L89
[cube]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/BlockPos.java#L263-L286
[set-block]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/Level.java#L201-L248
[slime-size]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Slime.java#L92-L106
[integer-clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/util/Mth.java#L98-L100
[cramming]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameRules.java#L108-L110
[hurt-gates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1196
[health-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1830-L1847
[totem]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1379
[totem-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L41
[silverfish-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L42-L56
[silverfish-merge]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L136-L176
[type-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1861-L1864
[entity-add]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L910-L952
[manager-add]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/entity/PersistentEntitySectionManager.java#L61-L93
[slime-type]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1223-L1231
[silverfish-type]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1199-L1207
[despawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L607-L630
[die]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1442
[death-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L553-L559
[tick-order]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L460-L483
[expiry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L824-L837
[creeper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L230-L257
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L61-L64
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L23
[clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[nearby-boxes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/entity/EntitySection.java#L40-L56
[arrows-data]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/tipped_arrow.json#L1-L4
[trial-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerConfig.java#L83-L95
[trial-potion-projectile]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/LingeringPotionItem.java#L35-L43
[effect-map]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L200-L204
