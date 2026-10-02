# Combat effects

**Strength and Weakness change attack damage; Resistance reduces eligible incoming damage; Absorption supplies a temporary damage buffer; Health Boost raises the limit for ordinary health.** Choose the effect for the job. Extra maximum health starts unfilled, and neither attack attributes nor one defensive percentage determine every fight's final damage. [Attack modifiers][strength] [weakness] · [Health modifiers][health-absorption] · [Player attack][player-attack] · [Defensive order][damage-order]

Times use **20 game ticks per second**. An effect's level is its amplifier plus one: `0` means I, `1` means II, and `3` means IV. **Two health points equal one heart.** Durations describe a fresh application; delivery, replacement, removal, and running out of Absorption can shorten what you observe. [Amplifier storage][effect-limits] · [Level scaling][scaling] · [Replacement][effect-update] · [Absorption tick][absorption-consumer]

## Strength

Effect ID: `minecraft:strength`.

Strength adds **3 attack-damage attribute points per level**: +3 at I or +6 at II. Ordinary player melee reads this attribute before applying attack charge, item-specific bonuses, critical-hit processing, and the target's defenses. See [Combat](../mechanics/Combat.md#time-your-melee-attacks) for timing. The bonus is not a promise that every hit removes another 1.5 or 3 hearts. [Registration][strength] · [Scaling][scaling] · [Charge][player-attack] · [Later attack stages][attack-bonuses]

An ordinary arrow uses its own speed and projectile damage, while a thrown Trident begins from its separate 8-point hit value before enchantments. Those checked paths do not add the thrower's Strength bonus. Custom mob attacks can also use fixed damage: a Tiger's active paw attack, for example, chooses its own damage rather than reading the attack-damage attribute. Do not assume that giving any attacker Strength changes every attack it can perform. [Arrow impact][arrow-hit] · [Thrown Trident][trident-hit] · [Registered Tiger goal][tiger-goal] · [Tiger attack][tiger-fixed]

Obtain Strength from the [potion chain below](#brewing-and-delivery), or select it at an active [Beacon](../blocks/Beacon.md#select-and-pay-for-effects). The normal Beacon menu unlocks Strength at **three complete base levels**; a four-level base can upgrade that selected primary to **Strength II**. Its refreshes maintain the effect rather than adding more Strength levels. [Beacon tiers][beacon-tiers] · [Menu choices][beacon-menu] · [Effect application][beacon-apply]

## Weakness

Effect ID: `minecraft:weakness`.

Weakness subtracts **4 attack-damage attribute points per level**. It changes the attacker's attribute, not the victim's armor. The attribute is clamped to a nonnegative value, and separate attack bonuses or custom attack paths can still matter; Weakness is not a universal way to make a mob harmless. [Registration][weakness] · [Level scaling][scaling] · [Attribute range][attack-range] · [Clamp][range-clamp] · [Attack gate and bonuses][attack-bonuses]

Strength and Weakness can coexist. Their additive contributions are **+3 × Strength level − 4 × Weakness level** before other attribute operations and the final attribute clamp. At level I of both, their combined contribution is **−1**, rather than cancellation of the two effects. Other damage modifiers and the later hit calculations remain separate. [Distinct modifiers][strength] [weakness] · [Attribute operation order][attribute-order]

Use a **Splash Potion of Weakness** to prepare a [Zombie Villager cure](../mobs/ZombieVillager.md#curing-step-by-step), then interact with the mob using an ordinary [Golden Apple](../items/GoldenApple.md). Eating the Apple yourself does not start the cure. Starting conversion removes Weakness and grants the converting mob Strength, so keep it contained during the wait. [Cure interaction][zombie-cure] · [Conversion effects][zombie-strength]

Other checked ways to receive Weakness include:

- **Tulip Suspicious Stew:** Red, Orange, White, and Pink Tulip recipes each store **140 ticks of Weakness I**, or **7 seconds**, on the eater. Use [Suspicious Stew](../items/SuspiciousStew.md) for preparation and the brown-Mooshroom route. [Red recipe][stew-red] · [Orange][stew-orange] · [White][stew-white] · [Pink][stew-pink] · [Consumption][stew-eat] · [Level I entry][stew-level]
- **Witch attacks:** the close-range attack branch can choose a Weakness splash when its target lacks Weakness, but earlier potion choices take priority and the branch has a random gate. Standing close does not guarantee a Weakness throw. [Potion selection and actual throw][witch-shot]
- **Tiger pursuit:** its active melee goal applies **100 ticks of Weakness I**, or **5 seconds**, when its target is within 20 blocks and differs from the last target it scared. The source's “Fear” holder is an alias for Weakness. See [Tiger](../mobs/Tiger.md) for the encounter. [Goal installation][tiger-goal] · [Pursuit callback][tiger-fear] · [Alias][fear-alias]
- **Tamed [Tremorsaurus](../mobs/Tremorsaurus.md) roar:** during its roar/scattering callback, a tamed individual can refresh **200 ticks of Weakness I**, or **10 seconds**, on nearby non-allied living entities that are not in its roar-resistance tag. This effect branch specifically requires the Tremorsaurus to be tamed. [Installed goal][tremor-goal] · [Roar caller][tremor-call] · [Roar start][tremor-start] · [Active tick][tremor-tick] · [Target filters and effect][tremor-effect]

## Resistance

Effect ID: `minecraft:resistance`.

Resistance reduces damage reaching its eligible calculation stage by **20% per level**: I leaves 80%, II leaves 60%, III leaves 40%, and IV leaves 20%. At V or above, that eligible stage reaches zero. This is a limit of one calculation, not general invulnerability. [Registration][resistance] · [Calculation and exclusions][resistance-stage]

In the ordinary player damage path, accepted damage passes through **armor → Resistance → applicable protection enchantments → Absorption → ordinary health**. Blocking and other damage gates occur earlier. These defenses act on the damage left by the preceding stage; their percentages are not added together. [Earlier gates][damage-gates] · [Player order][damage-order] · [Armor stage][armor-stage] · [Resistance before enchantments][resistance-stage]

The bundled exclusions are important:

- **Starvation** bypasses the effects-reduction stage, so Resistance does not reduce it. [Effects-bypass tag][bypass-effects]
- **Void (`out_of_world`) and `generic_kill` damage** bypass Resistance directly. [Resistance-bypass tag][bypass-resistance]
- **Sonic Boom bypasses armor and protection enchantments, but still reaches Resistance.** An armor bypass alone does not bypass Resistance. [Armor-bypass tag][bypass-armor] · [Enchantment-bypass tag][bypass-enchants] · [Separate checks][resistance-stage]

For example, if **10 damage points reach Resistance II**, 6 remain after that stage. With no later enchantment reduction and 4 Absorption points available, those 4 points are spent and **2 ordinary health points** are lost. This is a calculation example under those conditions, not a tested damage result for a particular weapon. [Resistance calculation][resistance-stage] · [Absorption and health subtraction][damage-order]

Acquire Resistance through a **Turtle Master potion**, which also imposes substantial [Slowness](MovementEffects.md#slowness), or by eating an **Enchanted Golden Apple**, which grants **Resistance I for 6,000 ticks / 5 minutes** along with its other effects. A normal Beacon menu offers Resistance from **two base levels**, with Resistance II available through the four-level primary upgrade. [Turtle Master effects][turtle-durations] · [Apple effects][food-effects] · [Beacon tiers][beacon-tiers] · [Menu][beacon-menu] · [Application][beacon-apply]

## Absorption

Effect ID: `minecraft:absorption`.

Absorption adds **4 maximum Absorption points per level**, and a new application fills the available pool to at least that effect's **4 × level** amount, subject to the current cap. Thus I normally supplies **4 points / 2 extra hearts**, II supplies **8 / 4 hearts**, and IV supplies **16 / 8 hearts**. These points are a separate buffer consumed after the checked damage reductions, before ordinary health. [Registration][health-absorption] · [Start callback][absorption-consumer] · [Pool clamp][absorption-clamp] · [Damage subtraction][damage-order]

**Healing does not replenish this buffer.** The ordinary healing method changes health, while a fresh Absorption application can replenish Absorption. Reapplying it does not add another pool on top: the start callback takes the larger of the remaining pool and the incoming effect's amount. It runs even when the existing effect's level/duration is not replaced, so a refill does not necessarily mean a longer timer. A weaker incoming application only offers its own smaller refill amount. [Healing][heal] · [Every accepted application][effect-apply] · [Refill rule][absorption-consumer] · [Level/duration comparison][effect-update]

When the pool reaches zero, **the effect ends on its next server effect tick**, even if its timer had time left. On normal expiry or removal, the extra maximum is removed and any remaining pool is clamped to the resulting maximum, normally zero. A surviving weaker hidden effect can take over on timed expiry, but that transition is not a fresh refill; exhausting the pool instead causes the whole entry to be removed. [Per-tick check][absorption-consumer] · [Tick and hidden-effect order][effect-tick] · [Expiry removal][effect-expiry] · [Modifier removal and clamp][attribute-removal]

The checked ordinary sources are:

| Source | Absorption granted | Fresh duration |
| --- | --- | --- |
| Eat a [Golden Apple](../items/GoldenApple.md) | I: 4 points / 2 hearts | 2,400 ticks / 2 minutes |
| Eat an [Enchanted Golden Apple](../items/EnchantedGoldenApple.md) | IV: 16 points / 8 hearts | 2,400 ticks / 2 minutes |
| Qualifying [Totem of Undying](../items/TotemOfUndying.md#activation) activation | II: 8 points / 4 hearts | 100 ticks / 5 seconds |

Apple effects run when consumption completes. A Totem must satisfy its lethal-damage hand check; carrying it in an ordinary inventory slot does not activate it. Its sequence clears existing effects before granting its own, so it can replace stronger or longer protection. None of these sources grants Health Boost. [Apple registrations][food-items] · [Apple effect definitions][food-effects] · [Consumption callback][food-callback] · [Status-effect application][food-apply] · [Totem check][totem-caller] · [Totem sequence][totem-effects]

## Health Boost

Effect ID: `minecraft:health_boost`.

Health Boost adds **4 maximum ordinary health points per level**, equivalent to two hearts of capacity. **It does not heal you when applied.** For an otherwise ordinary player, level I raises the maximum from 20 to 24 points while leaving current health unchanged. Fill the new capacity through normal healing, subject to its own conditions. [Health modifier][health-absorption] · [Default maximum][health-ranges] · [Modifier installation][effect-install] · [Generic effect has no healing callback][generic-effect] · [Healing clamp][heal]

[Hunger-based healing](../mechanics/Hunger.md) checks current health against the current maximum, so it can fill that capacity when food and game-rule conditions allow. [Regeneration](Regeneration.md) is a separate healing effect. When Health Boost expires, is removed, or is replaced by a lower level, current health above the new maximum is clamped down to that maximum; it does not remain as spare hearts. [Hurt check][hurt-check] · [Food healing][food-heal] · [Removal and maximum-health clamp][attribute-removal]

There is **no ordinary Health Boost potion or Beacon choice in the checked registrations**, and none of the food sources above grants it. Absorption also has no ordinary potion type. Their registered effect IDs can be used by permitted commands or custom content, but registration does not create a matching bottle in the item browser. Maximum-health and Absorption attributes also have their own caps, so indefinitely scaling the displayed level does not provide unlimited capacity. [Potion registry][potions] · [Food definitions][food-effects] · [Beacon choices][beacon-tiers] · [Attribute limits][health-ranges] · [Attribute clamp][range-clamp]

## Brewing and delivery

Use [Brewing](../brewing/Brewing.md) for the stand, fuel, bottles, and base mixtures. These recipes are loaded into the server brewing registry and used by the active stand. Times in this table describe the ordinary **drinkable** potion. [Server setup][brew-server] · [Stand mixing][brew-stand]

| Potion | Starting mixture | Ordinary result | Add Redstone to ordinary result | Add Glowstone to ordinary result |
| --- | --- | --- | --- | --- |
| Strength | Awkward + Blaze Powder | Strength I, 3:00 | Strength I, 8:00 | Strength II, 1:30 |
| Weakness | **Water Bottle** + Fermented Spider Eye | Weakness I, 1:30 | Weakness I, 4:00 | No registered mix |
| Turtle Master | Awkward + Turtle Shell | Resistance III + Slowness IV, 0:20 | Same levels, 0:40 | Resistance IV + Slowness VI, 0:20 |

**Weakness skips Nether Wart.** Blaze Powder added straight to Water instead makes Mundane Potion; use Awkward for Strength. No registered recipe combines the long and strong versions in this table. Turtle Master is a paired effect: its strong protection comes with stronger Slowness. [Strength/Weakness recipes][brew-strength-weak] · [Start-mix distinction][brew-start] · [Turtle Master recipes][brew-turtle] · [Strength/Weakness durations][strength-weak-durations] · [Turtle Master durations][turtle-durations] · [Complete mix list][brew-all]

Gunpowder converts a drinkable potion to splash, and Dragon's Breath converts splash to lingering. Splash duration depends on distance from impact and can be omitted if too short. The ordinary lingering item applies these timed effects at **one-quarter duration**, while an ordinary tipped arrow uses **one-eighth**, through their respective active effect handlers. A cloud's lifetime is not the duration of the effect it grants. See [Brewing](../brewing/Brewing.md) and [movement-effect delivery](MovementEffects.md#delivery-changes-duration) for shared details. [Container recipes][brew-all] · [Splash calculation][splash] · [Item scales][item-scales] · [Cloud scale][cloud-components] · [Cloud application][cloud-apply] · [Arrow scale][arrow-scale] · [Arrow application][arrow-effect]

The category-listed potion variants and ordinary listed source items can also be obtained through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival and Creative**. Browser insertion is separate from gathering ingredients, crafting, loot, and brewing. Its potion entries enumerate enabled registered potion types; they do not include a Health Boost or Absorption bottle absent from that registry. [Potion listing][browser-potions] · [Enumeration][browser-enumeration] · [Potion types][potions]

## Duration, clearing, and commands

Repeated applications of the same effect do not add levels or simply add time. A stronger application takes precedence; an equal-level longer application replaces the remaining duration. A weaker, longer effect may wait as a hidden effect. Finite hidden durations keep counting down; an infinite duration stays infinite. Different effect IDs can coexist, including Strength with Weakness and Absorption with Health Boost. [Effect map][effect-apply] · [Replacement rules][effect-update] · [Hidden duration][effect-tick] · [Infinite-duration handling][effect-infinite] · [Modifier installation][effect-modifiers]

**Milk clears beneficial effects as well as Weakness.** Losing Absorption removes its extra pool capacity; losing Health Boost can lower current health to the restored maximum. Honey only removes Poison and does not clear these five effects. Effects can be applied again by a continuing source, such as a nearby active Beacon. [Milk definition][milk] · [Clear callback][milk-clear] · [All-effect removal][clear-all] · [Attribute adjustment][attribute-removal] · [Honey][honey] · [Beacon refresh][beacon-pulse]

With command permission level 2, examples include:

```mcfunction
/effect give @s minecraft:strength 60 0
/effect give @s minecraft:health_boost 60 0
/effect clear @s minecraft:weakness
```

Here `60` is seconds and amplifier `0` means level I. Clearing a specific effect removes its active entry and any hidden continuation. These commands are a permission-gated route, separate from ordinary item-browser access. Stored amplifiers are clamped to 0–255; an effect's actual consumer and attribute caps determine what higher levels do. [Permission gate][command-gate] · [Arguments][command-args] · [Seconds conversion][command-convert] · [Specific clear][command-clear] · [Entry removal][clear-one] · [Stored limit][effect-limits]

Do not assume a splash or command works on every mob. The ordinary base immunity checks do not single out these five effects, but the Wither and Ender Dragon reject ordinary effect application entirely. Other entities can implement their own checks or damage methods. [Base checks][immunity] · [Wither][wither-immune] · [Ender Dragon][dragon-immune]

## Related pages

- [Status effects](Effects.md), [Movement effects](MovementEffects.md), and [Regeneration](Regeneration.md)
- [Combat](../mechanics/Combat.md), [Armor](../mechanics/Armor.md), and [Hunger and healing](../mechanics/Hunger.md)
- [Brewing](../brewing/Brewing.md), [Beacon](../blocks/Beacon.md), and [Totem of Undying](../items/TotemOfUndying.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked registered modifiers, active player damage and health consumers, bypass tags, effect refresh/removal, and the described potion, consumption, Beacon, and mob callbacks. This is a bounded effect reference; linked owners retain equipment, hunger, curing, and acquisition details. **No in-game combat, potion, food, Beacon, command, or timing test was run.** Data packs, modified components, attributes, target-specific handlers, and server timing can change outcomes.

[strength]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L45-L49
[weakness]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L75-L79
[resistance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L58-L60
[health-absorption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L82-L91
[scaling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L201-L204
[attribute-order]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/AttributeInstance.java#L150-L167
[attack-range]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L10-L14
[health-ranges]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L54-L59
[range-clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/RangedAttribute.java#L30-L32
[player-attack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L983
[attack-bonuses]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L989-L1031
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L377-L408
[trident-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L110-L121
[tiger-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L156-L174
[tiger-fixed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L624-L645
[beacon-tiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L52-L58
[beacon-menu]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L73-L111
[beacon-pulse]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L172-L181
[beacon-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L257
[zombie-cure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L136-L152
[zombie-strength]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L169-L175
[witch-shot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Witch.java#L209-L236
[stew-red]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_red_tulip.json#L1-L23
[stew-orange]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_orange_tulip.json#L1-L23
[stew-white]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_white_tulip.json#L1-L23
[stew-pink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_pink_tulip.json#L1-L23
[stew-eat]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L42
[stew-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L58-L72
[tiger-fear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L592-L618
[fear-alias]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/effect/AMEffectRegistry.java#L42-L45
[tremor-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java#L91-L109
[tremor-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexscaves/server/entity/ai/TremorsaurusMeleeGoal.java#L24-L44
[tremor-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java#L369-L374
[tremor-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java#L165-L178
[tremor-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java#L252-L284
[damage-order]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L789-L808
[damage-gates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1195
[armor-stage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1786
[resistance-stage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1789-L1825
[bypass-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json#L1-L5
[bypass-resistance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json#L1-L6
[bypass-armor]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L22
[bypass-enchants]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json#L1-L5
[absorption-consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/AbsorptionMobEffect.java#L6-L25
[effect-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1000
[absorption-clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3092-L3101
[attribute-removal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1076-L1114
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L260
[effect-expiry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L824-L835
[food-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1411-L1418
[food-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L29-L45
[food-callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L91
[food-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L35-L60
[totem-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L40
[totem-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1375
[heal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[generic-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L82-L95
[food-heal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/food/FoodData.java#L44-L58
[hurt-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1516-L1518
[effect-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1046-L1051
[effect-modifiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L159-L176
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L10-L80
[strength-weak-durations]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L67-L71
[turtle-durations]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L38-L45
[brew-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[brew-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L234
[brew-strength-weak]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L184-L188
[brew-turtle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L160-L162
[brew-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[brew-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L183
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L68
[item-scales]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2242-L2255
[arrow-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[arrow-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L39-L45
[cloud-components]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L380-L395
[cloud-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L213-L237
[browser-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[browser-enumeration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[effect-limits]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L69-L76
[effect-infinite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L168-L188
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L170
[clear-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[clear-one]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1031-L1044
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L22
[honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[immunity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1013
[wither-immune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L488-L491
[dragon-immune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L818-L821
[command-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L48
[command-args]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L70-L90
[command-convert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L176
[command-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L226-L232
