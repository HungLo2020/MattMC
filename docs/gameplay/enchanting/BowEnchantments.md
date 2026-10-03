# Power, Punch, Flame and Infinity

**Use Power for stronger arrow hits, Punch to push targets away, Flame for burning arrows, and Infinity to conserve ordinary Arrows.** These four can coexist on one [Bow](../items/Bow.md). The important choice is **Infinity or Mending**: normal combinations cannot have both. Bow crafting, ammunition selection and drawing controls remain in the [Bow guide](../items/Bow.md). [Definitions][power] · [Punch][punch] · [Flame][flame] · [Infinity][infinity] · [Compatibility][compatibility]

## Supported items and ordinary levels

| Enchantment | ID | Ordinary maximum | Main benefit |
| --- | --- | --- | --- |
| Power | `minecraft:power` | V | Raises the damage term multiplied by arrow speed |
| Punch | `minecraft:punch` | II | Adds a separate push after an accepted hit |
| Flame | `minecraft:flame` | I | Ignites the launched projectile |
| Infinity | `minecraft:infinity` | I | Consumes no ordinary Arrow when firing |

All four use the bundled Bow-enchantable tag, whose **only item is `minecraft:bow`**. None supplies a narrower table-item list, so an eligible unenchanted Bow can receive all four kinds at a table. Crossbows and the ammunition items themselves are outside this support tag. A plain [Book](../items/Book.md) can store table-selected enchantments for later Anvil use; maximum levels and actual table offers are separate, as [explained below](#obtaining-and-combining-them). [Support tag][support-tag] · [Primary-item rule][primary] · [Book selection][selection] · [Bow and Book registrations][bow-registration] · [Book][book-registration]

**Power, Punch and Flame have no mutual exclusions; Infinity excludes Mending.** The exclusion is checked in both directions, including Book combinations. [Unbreaking](DurabilityEnchantments.md#unbreaking-a-chance-for-each-wear-point) can accompany either choice. See [Unbreaking and Mending](DurabilityEnchantments.md) for wear probabilities and XP repair, and [Anvil mechanics](../mechanics/AnvilMechanics.md) for costs. [Default exclusions][defaults] · [Bow exclusion group][exclusions] · [Mending][mending] · [Unbreaking][unbreaking] · [Compatibility][compatibility] · [Anvil checks][anvil]

## Power: damage depends on the hit

Power adds **0.5 × (level + 1)** to the arrow's ordinary damage coefficient of **2**:

| Power level | Added coefficient | Resulting coefficient |
| --- | ---: | ---: |
| I | +1 | 3 |
| II | +1.5 | 3.5 |
| III | +2 | 4 |
| IV | +2.5 | 4.5 |
| V | +3 | 5 |

These are calculation terms, **not a fixed number of hearts removed**. At impact, the arrow multiplies its actual speed by the modified coefficient, then rounds up. A critical arrow adds a random integer from zero through `floor(rounded damage / 2) + 1`. For example, if impact speed is exactly 3, Power V gives 15 before the critical addition, or **15–23 damage points** with that addition, before the target's damage handling. This is a conditional arithmetic example, not a guaranteed full-draw damage range. [Power definition][power] · [Linear calculation][linear] · [Add effect][add] · [Base coefficient][arrow-base] · [Impact and critical calculation][arrow-damage]

[Drawing fully](../items/Bow.md#drawing-and-firing) supplies the normal maximum launch-strength setting and the critical flag. Actual impact speed also depends on launch randomness, the shooter's movement and flight; Power does not replace the draw calculation or use the melee attack-recharge meter. Target defenses and recent-hit handling still apply: the ordinary living-target handler can reject a rapid hit or apply only the increase over its previous hit. Power does not bypass an [Enderman's](../mobs/Enderman.md) special projectile handling or the [Wither's](../mobs/Wither.md) powered arrow immunity. [Release and draw][bow-release] · [Critical flag][projectile-create] · [Launch motion][launch] · [Flight][flight] · [Target handling][target-damage] · [Enderman][enderman] · [Wither][wither]

Power and Punch require the direct attacker to be in the **arrow entity-type tag**, which contains ordinary Arrow and Spectral Arrow entities. [Tipped Arrows](../items/TippedArrow.md) create ordinary Arrow entities with their item data, so this requirement does not exclude them. Both bonuses use the copy of the firing Bow stored on that projectile, rather than another enchanted item you happen to hold later. [Arrow entity tag][arrow-entity-tag] · [Arrow creation][arrow-create] · [Tipped Arrow class][tipped-class] · [Spectral creation][spectral-create] · [Weapon copy][weapon-copy] · [Damage and knockback dispatch][damage-dispatch]

## Punch: an extra push, not extra damage

Punch adds a knockback value of **1 at level I** or **2 at level II**. After the arrow's hit is accepted on a living target, its separate horizontal push uses the arrow's horizontal direction and a magnitude of **0.6 × level × max(0, 1 − target knockback resistance)**. With zero resistance, those push inputs are 0.6 and 1.2; they are not distances in blocks. A nonzero horizontal push also adds 0.1 vertical motion. [Punch definition][punch] · [Accepted-hit dispatch][accepted-hit] · [Punch push][punch-push]

This extra push is separate from the target's ordinary hurt knockback. Its level term does not scale with draw charge or the critical damage roll, and it does not increase arrow damage. Full knockback resistance or a zero horizontal direction removes this particular push. A rejected hit does not reach it. [Punch push][punch-push] · [Ordinary hurt knockback][hurt-knockback] · [Rejected hit][rejected-hit]

## Flame: burning projectile and target are separate

Flame's spawn effect ignites the arrow for **100 seconds / 2,000 ticks**. The active spawn path runs the firing Bow's effect on the projectile. Ordinary Arrow and Spectral Arrow types are not fire-immune by default, so they can be burning projectiles; the arrow explicitly clears its fire while in water or rain. [Flame definition][flame] · [Spawn effect call][spawn] · [Enchantment dispatch][spawn-dispatch] · [Ignite effect][ignite] · [Seconds and ticks][ignite-ticks] · [Arrow registration][arrow-type] · [Spectral registration][spectral-type] · [Default immunity flag][type-default] · [Wet-arrow clearing][wet-arrow]

A still-burning arrow requests **5 seconds / 100 ticks** of fire on a hit target, except an Enderman. A living target scales that duration by its burning-time attribute, normally 1; [Fire Protection](ProtectionEnchantments.md#fire-duration-and-explosion-knockback) can shorten it. Ignition extends a shorter remaining timer rather than adding five seconds to every existing timer. If the arrow's damage call rejects the hit, the target's previous fire timer is restored. [Target ignition and rejection][arrow-fire] · [Living duration scale][burning-scale] · [Default burning time][burning-default] · [Timer extension][ignite-ticks]

**A fire timer is not a guarantee of five extra damage points.** Ordinary burning attempts 1 damage point every 20 remaining fire ticks, outside lava, through the target's damage handler. Fire-immune entity types do not count as on fire and clear the timer; [Fire Resistance](../effects/WaterAndFireEffects.md#fire-resistance) rejects fire-tagged damage without itself extinguishing the target. The separate arrow impact is not in that fire tag. The player's `fireDamage` rule and normal damage gates also matter. Flame does not apply a potion status effect or grant Fire Resistance. [Burning tick][burning-tick] · [Burning-state test][on-fire] · [Fire Resistance gate][target-damage] · [Fire damage types][fire-tag] · [Player fire-damage rule][player-fire]

## Infinity: which ammunition is saved?

In ordinary Survival firing:

| Selected ammunition | Consumption with Infinity | Fired-arrow pickup |
| --- | --- | --- |
| [Arrow](../items/Arrow.md), `minecraft:arrow` | 0 | Creative-only; not collectible into a Survival inventory |
| [Tipped Arrow](../items/TippedArrow.md), `minecraft:tipped_arrow` | 1 | Normal consumed-ammunition pickup rules |
| [Spectral Arrow](../items/SpectralArrow.md), `minecraft:spectral_arrow` | 1 | Normal consumed-ammunition pickup rules |

The Bow's accepted-ammunition **item tag** contains all three items. Infinity's consumption condition instead checks **the exact ordinary Arrow item**, not that whole tag, the projectile's entity type, or whether a tipped arrow's potion is useful. Offhand Tipped or Spectral Arrows are selected before ordinary inventory Arrows and will still be consumed. [Ammunition item tag][ammo-tag] · [Held selection][held-ammo] · [Inventory selection][player-ammo] · [Infinity definition][infinity] · [Ammo effect receives the selected ammunition][ammo-dispatch] · [Consumption][ammo-use]

**Keep at least one accepted arrow available in Survival.** The Bow selects ammunition before processing Infinity; an empty selection prevents the shot. Infinity does not create a missing arrow. With the ordinary Arrow selected, zero consumption marks the fired copy intangible, which makes its pickup Creative-only. Even there, touching it removes the projectile rather than adding an Arrow to inventory. Normal pickup additionally requires a settled/no-physics arrow with its shake time finished and, for an allowed pickup, inventory space. [Use and release gates][bow-release] · [Player fallback][player-ammo] · [Intangible copy][ammo-use] · [Pickup conversion][weapon-copy] · [Pickup handling][pickup]

**Creative is a separate rule.** The infinite-material ability already makes all selected ammunition cost zero, including Tipped and Spectral Arrows, and supplies an ordinary Arrow when none is available. It does not require Infinity. Those zero-consumption projectiles use the same Creative-only pickup restriction. [Creative ammunition handling][ammo-use] · [Empty-inventory fallback][player-ammo] · [Pickup handling][pickup]

## Wear and retained broken bows

Infinity saves ammunition, **not Bow durability**. An ordinary spawned shot still requests one wear point; Unbreaking and Creative can change whether that wear is applied. A release below the shooting threshold produces no projectile and no firing wear. MattMC retains the fully worn Bow, but the normal use, release and firing guards prevent another shot. Repair choices remain with [Bow repair](../items/Bow.md#broken-bows-and-repair), [Durability](../mechanics/Durability.md#choose-a-repair-method) and [Mending](DurabilityEnchantments.md#retained-broken-equipment-can-be-repaired). [Release guard][bow-release] · [Spawn before wear][shoot-wear] · [Stack use guard][use-guard] · [Wear and retention][wear]

The last permitted shot copies the firing Bow **before** applying that shot's wear. Reaching the durability limit afterward therefore does not erase that already-fired arrow's copied Power or Punch. This distinction follows the current source order; it does not allow another shot from the retained broken stack. [Weapon copy][weapon-copy] · [Spawn before wear][shoot-wear] · [Broken-copy damage checks][damage-dispatch]

## Obtaining and combining them

- **Enchanting Table:** all four are in the ordinary table pool for an eligible unenchanted Bow or plain Book. Follow [Enchanting](Enchanting.md) for shelves, displayed requirements and payment. In the bundled setup, direct table Power tops out at **IV**: Bow and Book both have enchantability 1, so the maximum ordinary level-30 offer reaches at most effective selection cost 36, below Power V's minimum of 41. Punch II, Flame I and Infinity I are within the reachable ranges; an eligible level is still not a guaranteed roll. [Table pool][table-pool] · [Pool members][non-treasure] · [Item eligibility][table-gate] · [Table caller][table-caller] · [Cost and shelf cap][table-cost] · [Selection adjustment][selection] · [Bow][bow-registration] · [Book][book-registration] · [Power][power] · [Punch][punch]
- **Anvil:** apply a compatible book to a Bow or combine compatible Bow enchantments. Equal Power IV entries can combine into **Power V**; ordinary merging stops at each enchantment's maximum. Infinity and Mending remain incompatible. Review the output and level cost using [Anvil mechanics](../mechanics/AnvilMechanics.md). [Anvil checks and cap][anvil]
- **Ordinary Librarians:** selected book offers in profession levels 1–4 can choose all four from the tradeable pool, including Power V and Punch II. They require Emeralds plus a Book; the listing and enchantment are selected, so a Librarian level does not guarantee the book you want. With the [optional trade-rebalance feature](../trading/Trading.md#optional-trade-rebalance), the common pools instead place Infinity with Desert Librarians, Power with Jungle, Punch with Plains and Flame with Taiga. These optional pools are separate from normal trading. [Ordinary listings][librarian] · [Trade selection][trade-selection] · [Book generation][book-trade] · [Tradeable pool][tradeable] · [Active table switch][trade-switch] · [Optional listings][rebalance-listings] · [Optional pools][rebalance-common] · [Desert][desert] · [Jungle][jungle] · [Plains][plains] · [Taiga][taiga]
- **Inventory item browser:** the ordinary category entries include **Power V, Punch II, Flame I and Infinity I Enchanted Books**. The [browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can insert them when the server player's infinite-materials ability is enabled, normally in Creative, subject to its cursor, available-space and later server checks. Ordinary Survival requests are skipped before the inventory handler. [Packet admission][browser-gate] · [Live server context][browser-context] · [Player ability][browser-ability] · [Mode assignment][browser-mode] Category generation supplies maximum-level books; separate search-tab-only lower levels are not the category entries this panel gathers. This route does not establish a crafting recipe or loot source. [Book category][category] · [Maximum-level generation][book-generation] · [Tab visibility][tab-visibility] · [Browser list][browser-list] · [Browser click][browser-click] · [Capacity][browser-capacity] · [Client request][browser-client] · [Later server checks][browser-server] · [Insertion][browser-insert]

These are verified acquisition routes, not an exhaustive survey of enchanted equipment, fishing, chests or mob loot. Data packs can change definitions, tags, costs and trade pools.

## Sources and verification

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The active registry resources, support and exclusion tags, player release/ammunition/spawn path, copied-weapon damage and knockback consumers, ignition and pickup handling, broken-item guards and listed acquisition routes were checked. [Registry registration][registry] · [Resource loading][resource-loader]

No in-game firing, damage, knockback, fire-duration, ammunition, pickup, wear, enchanting, trade or browser test was run. Numerical examples are source-derived inputs or possibilities, not measured combat results. Special targets, components, data packs and later builds can change outcomes.

Related: [Enchanting](Enchanting.md) · [Bow](../items/Bow.md) · [Unbreaking and Mending](DurabilityEnchantments.md) · [Protection enchantments](ProtectionEnchantments.md)

[power]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/power.json
[punch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/punch.json
[flame]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/flame.json
[infinity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/infinity.json
[mending]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/mending.json
[unbreaking]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/unbreaking.json
[support-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/bow.json
[exclusions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/bow.json
[primary]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L130
[defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L60-L70
[compatibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L162
[bow-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1289
[book-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1598
[linear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/LevelBasedValue.java#L131-L147
[add]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/AddValue.java#L13-L16
[arrow-base]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L49-L78
[arrow-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L376-L409
[bow-release]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/BowItem.java#L25-L98
[projectile-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L91-L99
[launch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L153
[flight]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L208-L250
[damage-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L180-L209
[arrow-entity-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/arrows.json
[arrow-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ArrowItem.java#L12-L19
[spectral-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpectralArrowItem.java#L12-L20
[tipped-class]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/TippedArrowItem.java#L8-L18
[weapon-copy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L84-L114
[accepted-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L415-L436
[punch-push]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L473-L484
[hurt-knockback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1200-L1228
[rejected-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L459-L469
[spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L205-L219
[spawn-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L312-L315
[ignite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/Ignite.java#L16-L19
[ignite-ticks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L597-L618
[arrow-type]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L272-L275
[spectral-type]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1307-L1315
[type-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2050-L2063
[wet-arrow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L182-L188
[arrow-fire]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L415-L460
[burning-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3690-L3693
[burning-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L24-L26
[burning-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L509-L519
[on-fire]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2520-L2523
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[player-fire]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L718
[ammo-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/arrows.json
[held-ammo]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L19-L38
[player-ammo]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1834-L1855
[ammo-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L96-L100
[ammo-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L101-L138
[pickup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L586-L616
[shoot-wear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L54-L85
[use-guard]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L396
[wear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[anvil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L175-L223
[target-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1196
[enderman]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/EnderMan.java#L355-L384
[wither]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L438-L450
[table-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[table-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[table-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L197
[table-cost]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L499-L513
[selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[librarian]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L339
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[book-trade]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1325
[tradeable]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/tradeable.json
[trade-switch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L841
[rebalance-listings]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L831-L866
[rebalance-common]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1134-L1146
[desert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/desert_common.json
[jungle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/jungle_common.json
[plains]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/plains_common.json
[taiga]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/taiga_common.json
[category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1953-L1956
[book-generation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2216-L2233
[tab-visibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTab.java#L227-L239
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-click]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L376-L390
[browser-capacity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L595-L630
[browser-client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1903
[browser-insert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2006-L2018
[registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L124-L128
[resource-loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L330
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[browser-ability]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[browser-mode]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
