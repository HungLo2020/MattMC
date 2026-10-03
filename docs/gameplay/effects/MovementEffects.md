# Movement effects

**Speed, Slowness, Jump Boost, Slow Falling, Levitation, and Dolphin's Grace** affect different parts of movement. Choose an effect for the situation: a faster movement attribute does not provide flight, slower falling does not provide air underwater, and Levitation can prevent an Elytra escape. This guide follows ordinary player movement; individual mobs can override those rules. [Registered effects][registry] · [Movement dispatch][gravity] · [Glide restriction][glide-gate] · [Breathing check][water-breathing]

Times below use **20 game ticks per second**. An effect's level is its amplifier plus one: amplifier `0` is level I, `1` is II, and `3` is IV. Levels, remaining duration, and how the effect is delivered are separate properties. [Stored amplifier][effect-limits] · [Level-scaled modifiers][scaling] · [Potion delivery][duration-scale]

## Speed

Effect ID: `minecraft:speed`.

Speed adds a **+20% movement-speed attribute modifier per level**: +20% at I and +40% at II. The player uses this attribute in ground movement, but airborne controls, fluid travel, friction, sprinting, equipment, and existing velocity have their own rules. These percentages are **attribute contributions**, not a measured increase in blocks travelled per second in every situation. [Registration][speed-slow] · [Level scaling][scaling] · [Player speed][player-speed] · [Ground motion][ground-motion] · [Friction][friction] · [Air control][air-control] · [Water movement][water-motion]

Obtain it from **Potions of Swiftness** using the [brewing table below](#brewing-these-effects). An active [Beacon](../blocks/Beacon.md) offers Speed as a primary from the first pyramid tier; a four-tier Beacon can upgrade that selected primary to II. Repeated refreshes maintain an effect rather than stacking extra Speed levels. [Beacon selection][beacon-menu] · [Tier list][beacon-tiers] · [Application][beacon-apply]

MattMC also has a **[Jerboa reward](../mobs/Jerboa.md#speed-reward)**: interacting with accepted seeds or a Pitcher Pod when it is befriended and healthy can grant **12,000 ticks of Speed I**, about **10 minutes**. Its source calls this “Fleet Footed,” but that alias resolves to ordinary Speed. While a Jerboa remembers its attacker, it can remove that attacker's Speed regardless of where the effect came from. Use the linked care guide for the exact interaction. [Reward and removal][jerboa] · [Effect alias][speed-alias] · [Accepted tag][jerboa-food] · [Tag members][seeds]

## Slowness

Effect ID: `minecraft:slowness`.

Slowness adds a **−15% movement-speed attribute modifier per level**. Common examples are I at −15%, IV at −60%, and VI at −90%. It does not slow every action: mining, attack timing, gravity, and all forms of motion are not replaced by this one attribute. At sufficiently high levels the attribute can reach its zero lower bound, but momentum, airborne controls, water movement, and external pushes can still move an entity. [Modifier][speed-slow] · [Level scaling][scaling] · [Attribute limits][attribute-ranges] · [Clamping][clamp] · [Air control][air-control] · [Water motion][water-motion]

**Speed and Slowness coexist and multiply their separate modifiers.** Speed I and Slowness I contribute a combined multiplier of approximately `1.20 × 0.85 = 1.02`, applied before the attribute's final range clamp. They neither remove each other nor combine by simply subtracting 15 from 20. This example is source arithmetic, not a travel-speed test. [Distinct modifier IDs][speed-slow] · [Multiplication order][attribute-product]

Besides brewed Slowness, a **Potion of the Turtle Master** applies Slowness alongside Resistance. The regular form gives Slowness IV for **20 seconds**, the extended form IV for **40 seconds**, and the strong form VI for **20 seconds**. Its simultaneous Resistance levels are III, III, and IV respectively. Brew Awkward Potion with a **Turtle Shell**; Redstone or Glowstone modifies the regular result. [Turtle Master mixtures][brew-movement] · [Both stored effects][potion-speed-slow]

Other active ways to receive or apply Slowness include:

- **Stray arrows:** the ordinary attack adds **600 ticks of Slowness I**, or **30 seconds**, to its arrow. Modified ammunition components can change delivery scaling. [Stray addition][stray-shot] · [Attack caller][skeleton-fire] · [Default ammunition][mob-ammo] · [Arrow application][arrow-hit]
- **Witch attacks:** a Witch can choose a Slowness splash potion against a sufficiently distant target without that effect. Splash duration depends on impact distance. [Potion selection and launch][witch-shot] · [Splash application][splash]
- **Mudskipper mud projectiles:** the active ranged goal fires a projectile whose living-target hit applies **60 ticks of Slowness I**, or **3 seconds**. See [Mudskipper](../mobs/Mudskipper.md) for its taming and attack behavior. [Registered goal][mud-goals] · [Projectile creation][mud-fire] · [Collision dispatch][mud-tick] · [Impact callback][mud-impact] · [Effect addition][mud-slow]
- **Bane of Arthropods:** qualifying direct attacks apply **Slowness IV** to tagged arthropods for a short, enchantment-level-dependent duration. This is not a general Slowness attack against every creature. [Loaded enchantment data][bane] · [Enchantment registry loader][enchantment-loader] · [Player attack dispatch][enchanted-hit] · [Equipment dispatch][post-attack] · [Effect application][apply-effect]

## Jump Boost

Effect ID: `minecraft:jump_boost`.

Jump Boost adds **0.1 per level to the normal ground-jump power calculation**, after the jump-strength and block-factor contribution. It also adds **1 per level to the safe-fall-distance attribute**. Thus level I contributes +0.1 jump power and +1 safe-fall distance; II contributes +0.2 and +2. Ceiling clearance, starting velocity, gravity, surfaces, and later movement still determine the actual arc. These numbers are **not guaranteed jump heights**. [Jump calculation][jump-motion] · [Safe-fall modifier][jump-register] · [Level scaling][scaling] · [Fall-damage consumer][fall-damage]

It does not make arbitrary falls safe. The ordinary deeper-fluid upward input uses a separate fixed amount, so do not expect the same boost from every swimming rise. Also allow for the effect expiring before you land. [Fluid upward input][fluid-jump] · [Fall calculation][fall-damage] · [Effect expiry][effect-tick]

Sources include **Potions of Leaping**, a [Beacon](../blocks/Beacon.md) with Jump Boost selected from the second pyramid tier onward, and the four-tier primary-II upgrade. [Potion definitions][potion-jump] · [Beacon selection][beacon-menu] · [Beacon application][beacon-apply]

For a short food route, **Cornflower Suspicious Stew** supplies Jump Boost I for **100 ticks, or 5 seconds**. Craft the serving using the [Suspicious Stew recipe](../items/SuspiciousStew.md#obtaining), or obtain the Cornflower serving from a brown [Mooshroom](../mobs/Mooshroom.md#bowls-milk-and-flower-servings). A level-4 Farmer's possible stew offers include a different **160-tick, or 8-second**, Jump Boost serving; that offer is selected from a pool and is not guaranteed. [Crafted serving][stew-recipe] · [Cornflower effect][cornflower] · [Flower duration][flower-ticks] · [Mooshroom feeding][stew-feed] · [Bowl result][stew-bowl] · [Farmer pool][farmer-pool] · [Trade selection][trade-select] · [Stew consumption and level][stew-consume] · [Level-I instance][stew-level]

## Slow Falling

Effect ID: `minecraft:slow_falling`.

When vertical velocity is zero or downward, Slow Falling caps ordinary effective gravity at **0.01** if gravity would otherwise be larger. While rising, that gravity helper is unchanged. The effect also resets accumulated fall distance during each AI movement tick. It changes how you descend; it does not give free vertical steering or immunity to every source of damage. [Gravity helper and movement dispatch][gravity] · [Fall-distance reset][fall-reset]

**Higher levels do not strengthen the checked Slow Falling movement rule**: it tests whether the effect is present, not its amplifier. The gravity helper also participates in fluid and Elytra movement, but this is not a guaranteed increase in flight range. Elytra wall-collision damage is a separate path. [Fluid consumer][water-motion] · [Gliding and collision][glide-motion]

Brew Awkward Potion with **Phantom Membrane** for **1 minute 30 seconds**, or extend the result with Redstone to **4 minutes**. There is no registered Glowstone-strengthened Slow Falling mixture. [Registered recipes][brew-fall] · [Durations][potion-fall] · [Complete brewing list][brew-all]

An owned, tame **[Sugar Glider](../mobs/SugarGlider.md)** riding its player owner refreshes Slow Falling I for **100 ticks**, or **5 seconds**, while that riding state continues. To pick up an already-tamed one, the owner can Sneak-interact with an empty hand while carrying no other passengers. Its riding callback allows dismounting when the pickup cooldown has elapsed and the owner is sneaking. This is a specific pet interaction, not an effect from merely standing nearby. [Owner pickup][sugar-pickup] · [Riding refresh and dismount][sugar-ride]

## Levitation

Effect ID: `minecraft:levitation`.

In ordinary air movement, Levitation adjusts vertical velocity toward **`0.05 × effect level`** with a per-tick adjustment factor of **0.2**, replacing the usual gravity-subtraction branch. Vertical damping follows, so that target is not a guaranteed final ascent speed. Levitation also resets fall distance while active. Its upward adjustment is not used by the ordinary fluid-travel branch. [Air calculation][levitation-air] · [Movement-mode selection][gravity] · [Fluid motion][water-motion] · [Fall reset][fall-reset]

**Levitation prevents Elytra gliding from starting or continuing.** Keep cover and a landing plan when fighting [Shulkers](../mobs/Shulker.md). An accepted Shulker-bullet hit applies **200 ticks of Levitation I**, or **10 seconds**. Removing or outlasting the effect can leave you high above the ground with normal falling rules restored. [Glide eligibility][glide-gate] · [Active-glide update][glide-stop] · [Bullet damage and effect][shulker-bullet]

With Levitation and Slow Falling together, Levitation's branch controls the ordinary-air vertical adjustment rather than subtracting the reduced gravity. Slow Falling can still remain after Levitation ends if its own duration has time left. Jump Boost can contribute its initial ground-jump impulse; it is not erased merely by having these other effects. [Air branch order][levitation-air] · [Jump impulse][jump-motion] · [Separate effect entries][effect-map]

There is **no registered ordinary Levitation potion or brewing recipe** in this bundle. The effect ID exists for mob effects and permitted commands; its registration alone does not make a potion appear in the item browser. [Effect registration][other-register] · [Registered potion types][potions-all] · [Browser potion generation][browser-types]

## Dolphin's Grace

Effect ID: `minecraft:dolphins_grace`.

Dolphin's Grace reduces the loss of horizontal momentum in water by setting the water-movement damping factor to **0.96**. The rule does not read amplifier, so a higher stored level does not strengthen that consumer. It leaves the separate vertical damping and water-acceleration calculations in place. This is not a universal land-speed modifier. [Water movement][water-motion]

Swim near a **[Dolphin](../mobs/Dolphin.md#swimming-with-dolphins)**. Its active assistance goal can choose a swimming player within **10 blocks**, gives **100 ticks**, or **5 seconds**, of the effect, and periodically refreshes it while accompanying that swimmer. The continuing follow condition allows a distance below **16 blocks**. Feeding is not required for this goal. [Registered goal][dolphin-goal] · [Initial range][dolphin-range] · [Conditions and refresh][dolphin-grace]

**Dolphin's Grace does not supply underwater breathing.** Keep access to air or use the appropriate breathing equipment/effects. The checked drowning helper looks for Water Breathing or Conduit Power, not Dolphin's Grace. No ordinary potion or brewing recipe grants Dolphin's Grace in the registered potion list. [Drowning path][drowning] · [Breathing effects][water-breathing] · [Potion list][potions-all]

## Brewing these effects

Use the [Brewing guide](../brewing/Brewing.md) for bottles, fuel, Awkward Potion, and container conversion. The durations here describe **ordinary drinkable** potions with unchanged components. All listed ingredient mixes are consumed through the active server brewing registry and Brewing Stand handler. [Server initialization][brew-bootstrap] · [Stand execution][brew-stand] · [Base and container mixes][brew-base]

| Effect/potion | Starting mixture | Ordinary result | Redstone on ordinary result | Glowstone on ordinary result |
| --- | --- | --- | --- | --- |
| Speed / Swiftness | Awkward + Sugar | I, 3:00 | I, 8:00 | II, 1:30 |
| Slowness | Swiftness or Leaping + Fermented Spider Eye | I, 1:30 | I, 4:00 | **IV**, 0:20 |
| Jump Boost / Leaping | Awkward + Rabbit's Foot | I, 3:00 | I, 8:00 | II, 1:30 |
| Slow Falling | Awkward + Phantom Membrane | I, 1:30 | I, 4:00 | No registered mix |

Extended Swiftness or Leaping can also be corrupted directly into extended Slowness. The registered list has no such fermentation from Swiftness II or Leaping II, and no recipe combining the extended and strengthened forms above. Follow the actual input state instead of treating modifier ingredients as universally interchangeable. [Movement mixes][brew-movement] · [Slow Falling mixes][brew-fall] · [Awkward start handling][start-mix] · [Leaping durations][potion-jump] · [Other durations][potion-speed-slow] · [Slow Falling durations][potion-fall]

The ordinary potion variants are also category-listed, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can supply them in **Survival and Creative**. That insertion route is separate from collecting ingredients and brewing. It lists enabled registered potion types, which does not add missing Levitation or Dolphin's Grace potion types. [Potion category generation][browser-potions] · [Enabled-type enumeration][browser-types]

### Delivery changes duration

- **Drinking:** applies the stored duration at the item's normal scale. [Consumption callback][drink-callback] · [Server application][drink]
- **Splash:** duration falls with impact distance, is rounded, and very short results of 20 ticks or less are omitted. It is not automatically the full drinkable duration for everyone nearby. [Splash consumer][splash]
- **Lingering:** the ordinary item uses a **one-quarter** duration scale when its cloud applies these effects. Repeated cloud contact can refresh an effect subject to the cloud's own checks; a cloud's lifetime is not the same as the duration it grants. [Item scale][duration-components] · [Cloud creation][cloud-create] · [Transferred scale][cloud-components] · [Cloud application][cloud-effects]
- **Tipped Arrows:** the ordinary item uses a **one-eighth** scale, applied by its effect-bearing arrow on a hit. Custom arrow components and mob-created arrows can use different scales. [Item scale][duration-components] · [Arrow scale lookup][arrow-scale] · [Hit application][arrow-hit] · [Duration scaling][duration-scale]

## Refreshing, clearing, and limits

The same effect does **not** add its levels or durations together. A stronger application can replace a weaker active one; an equal-level longer duration replaces the remaining time. A weaker, longer effect may wait behind a stronger effect. Finite hidden durations keep counting down; an infinite duration stays infinite. A hidden effect can return only with its remaining duration when the stronger effect ends. Separate types such as Speed and Slowness can coexist. [Per-type application][effect-map] · [Replacement rules][effect-update] · [Hidden and active timers][effect-tick] · [Infinite-duration handling][effect-infinite] · [Modifier replacement][modifier-install]

Drink **[Milk](../items/MilkBucket.md)** to clear these effects, including beneficial ones and any hidden continuation of the same effect. A **Honey Bottle removes Poison only**, so it is not a substitute for removing Slowness or Levitation. Successful ordinary [Totem of Undying](../items/TotemOfUndying.md) protection also clears current effects before applying its own replacement effects. Clearing Slow Falling or Levitation in midair removes their ongoing fall-distance reset, so secure a landing before treating Milk as a rescue plan. [Milk registration][milk-item] · [Milk consumption][milk-consumable] · [Clear-all call][milk-clear] · [Removal][clear-all] · [Honey specificity][honey] · [Totem activation][totem-caller] · [Replacement effects][totem-effects] · [Fall reset][fall-reset]

Clearing an effect does not disable its source. A Beacon, accompanying Dolphin, or Sugar Glider riding its owner can apply the effect again; leave the relevant refresh condition if you want it to stay cleared. [Beacon refresh][beacon-pulse] · [Dolphin refresh][dolphin-grace] · [Sugar Glider refresh][sugar-ride]

With **command permission level 2**, `/effect give @s minecraft:slow_falling 30 0` requests Slow Falling I for 30 seconds, and `/effect clear @s minecraft:slowness` removes only Slowness. Finite command durations accept **1–1,000,000 seconds**; `infinite` is a separate supported choice. Amplifiers accept **0–255**, but higher numbers do not strengthen presence-only rules such as Slow Falling or Dolphin's Grace. These are source-checked examples, not executed commands. [Permission and clear syntax][command-gate] · [Finite bounds][command-finite] · [Infinite syntax][command-infinite] · [Seconds conversion][command-convert] · [Targeted clearing][command-clear]

Ordinary application still passes through the recipient's effect checks. The base poison/regeneration immunity tag does not by itself reject these six effects, but the **Ender Dragon and Wither reject ordinary effect additions entirely**. Creative flight and custom mob movement can also bypass the expected player movement consequences without meaning that the status effect failed to apply. [Base acceptance][effect-immunity] · [Dragon rejection][dragon-immune] · [Wither rejection][wither-immune] · [Player airborne controls][air-control]

## Related pages

- [Mobility enchantments](../enchanting/MobilityEnchantments.md): Depth Strider, Frost Walker, Soul Speed and Swift Sneak equipment choices

- [Status effects](Effects.md)
- [Brewing](../brewing/Brewing.md)
- [Beacon](../blocks/Beacon.md)
- [Elytra](../items/Elytra.md)
- [Dolphin](../mobs/Dolphin.md), [Shulker](../mobs/Shulker.md), and [Jerboa](../mobs/Jerboa.md)
- [Milk](../items/MilkBucket.md) and [Suspicious Stew](../items/SuspiciousStew.md)

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Checked effect/potion registration, level-scaled attributes and active player movement/fall consumers, brewing and delivery, selected mob/food/Beacon sources, reapplication, clearing, and immunity. No game, movement-speed, jump-height, fall, brewing, command, pet, projectile, or timing test was run. Numbers describe checked source contributions and durations, not measured movement performance. The acquisition examples are not an exhaustive loot-location or equipment catalog; modified resources, item components, attributes, or entity code can change them.

[registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/registries/BuiltInRegistries.java#L163-L170
[gravity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2247-L2265
[glide-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2908-L2919
[water-breathing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L43-L45
[effect-limits]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L69-L76
[scaling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L201-L204
[duration-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L93-L102
[speed-slow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L19-L32
[player-speed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1337-L1340
[ground-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2458-L2467
[friction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2502-L2507
[air-control]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1928-L1935
[water-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2313-L2356
[beacon-menu]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L73-L111
[beacon-tiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L52-L58
[beacon-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L257
[jerboa]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L218-L255
[speed-alias]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/effect/AMEffectRegistry.java#L19-L22
[jerboa-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/jerboa_begs_for.json#L1-L6
[seeds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/seeds.json#L1-L10
[attribute-ranges]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L66-L74
[clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/RangedAttribute.java#L30-L32
[attribute-product]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/AttributeInstance.java#L150-L167
[brew-movement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L153-L167
[potion-speed-slow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L32-L45
[stray-shot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Stray.java#L58-L66
[skeleton-fire]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L189-L206
[mob-ammo]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L139-L147
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[witch-shot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Witch.java#L209-L235
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L68
[mud-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L122-L145
[mud-fire]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/MudskipperAIAttack.java#L32-L59
[mud-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityMobProjectile.java#L56-L66
[mud-impact]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityMobProjectile.java#L169-L179
[mud-slow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityMudBall.java#L60-L66
[bane]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json#L26-L65
[enchantment-loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L124-L128
[enchanted-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1096-L1103
[post-attack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L224-L241
[apply-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/ApplyMobEffect.java#L35-L44
[jump-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2204-L2228
[jump-register]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L52-L56
[fall-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1724-L1735
[fluid-jump]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2231-L2237
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L260
[potion-jump]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L23-L25
[stew-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_cornflower.json#L1-L23
[cornflower]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Blocks.java#L1027-L1029
[flower-ticks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L32-L42
[stew-feed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L127-L166
[stew-bowl]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L94-L118
[farmer-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L103-L112
[trade-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[stew-consume]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L42
[stew-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L58-L72
[fall-reset]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2817-L2825
[glide-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2386-L2418
[brew-fall]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L189-L190
[potion-fall]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L73-L76
[brew-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[sugar-pickup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L501-L508
[sugar-ride]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L322-L345
[levitation-air]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2288-L2310
[glide-stop]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2883-L2888
[shulker-bullet]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ShulkerBullet.java#L282-L293
[effect-map]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1000
[other-register]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L94-L107
[potions-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L10-L80
[browser-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[dolphin-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Dolphin.java#L152
[dolphin-range]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Dolphin.java#L69
[dolphin-grace]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Dolphin.java#L455-L488
[drowning]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L432
[brew-bootstrap]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[brew-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L183
[brew-base]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L127-L145
[start-mix]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L234
[browser-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[drink-callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L232-L235
[drink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L154-L164
[duration-components]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2242-L2255
[cloud-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java#L31-L44
[cloud-components]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L380-L395
[cloud-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L213-L237
[arrow-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L39-L45
[effect-infinite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L186-L188
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L170
[modifier-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L159-L176
[milk-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533
[milk-consumable]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L22
[clear-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[totem-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1375
[totem-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L40
[beacon-pulse]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L172-L181
[command-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L48
[command-finite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L70-L90
[command-infinite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L109-L121
[command-convert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L176
[command-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L226-L232
[effect-immunity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1013
[dragon-immune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L818-L821
[wither-immune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L488-L491
