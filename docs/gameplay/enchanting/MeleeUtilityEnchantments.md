# Looting, Fire Aspect and Knockback

**Use Looting for eligible mob rewards, Fire Aspect to ignite direct-hit targets, and Knockback to increase the primary hit's push.** These enchantments can share an ordinary sword in the bundled setup. Their effects have different conditions: extra loot is table-dependent, burning does not guarantee damage, and knockback strength is not a distance in blocks. [Looting][looting] · [Fire Aspect][fire-aspect] · [Knockback][knockback] · [Compatibility][compatibility]

For the separate **Sweeping Edge I–III** choice, use [Swords: what a sweep can hit](../mechanics/Swords.md#what-a-sweep-can-hit). That page owns the sweep's targets and damage calculation. [Sweeping Edge definition][sweeping]

## Equipment and obtaining the enchantments

| Enchantment | Ordinary maximum | Supported equipment | Direct Enchanting Table targets |
| --- | --- | --- | --- |
| Looting | III | Seven material Swords | Those Swords or a plain Book |
| Fire Aspect | II | Those Swords and the Mace | Those Swords or a plain Book |
| Knockback | II | Seven material Swords | Those Swords or a plain Book |

The sword set is **Wooden, Stone, Copper, Iron, Golden, Diamond and Netherite**. The nested support tags do not include Axes, Spears, the Trident or the separately registered Skelewag Sword. Fire Aspect's broader support tag adds the Mace, but its narrower primary tag keeps direct table selection on swords. All three definitions use the **main hand** as their active slot. [Sword support][sword-support] · [Exact sword set][swords] · [Fire Aspect support][fire-support] · [Primary-item rule][primary] · [Table filtering][selection]

- **Enchanting Table:** all three are in the ordinary table pool. Enchant a supported sword or plain Book; the random offers and level requirements do not guarantee a particular enchantment or its maximum level. See [Enchanting](Enchanting.md) for shelves, payment and rerolls. [Table pool][table-pool] · [Pool members][non-treasure]
- **Anvil:** transfer a compatible Enchanted Book to supported equipment, including a Fire Aspect book to a Mace. Equal levels combine one level higher up to the listed cap; unequal levels keep the higher, subject to that cap. Item support, compatibility and payment still apply. See [Anvil combining](../mechanics/AnvilMechanics.md#combining-enchantments). [Anvil consumer][anvil]
- **Ordinary Librarian trades:** these three belong to the tradeable pool. A generated book offer can select one and a level within its normal range, in exchange for Emeralds and a Book. A particular Librarian is not guaranteed to offer it. This describes the ordinary trade set; the optional trade rebalance selects different listings. See [Trading](../trading/Trading.md#optional-trade-rebalance). [Tradeable tag][tradeable] · [Librarian listings][librarian] · [Book selection][trade-book] · [Active trade-set choice][trade-choice]

These are selected acquisition routes, not a complete chest-loot or trading catalogue. A stored enchantment on a book is something to transfer, not an active melee bonus merely from holding the book. [Applied-enchantment reader][item-reader] · [Book transfer][anvil]

## Looting: check the reward, not just the creature

Looting has three distinct uses in the checked paths. A mob's ordinary items, a rare loot-table roll, and its equipped gear need not follow the same rule.

### Item counts and rare loot rolls

A loot entry using `enchanted_count_increase` adds **the rounded result of Looting level × that entry's sampled count value**. It can also set a limit. The count value belongs to the loot table, so “one extra item per level” is not a universal rule. [Count function][count-function]

| Bundled example | Without Looting | With Looting III, when its conditions pass |
| --- | --- | --- |
| Cow's Leather | 0–2 | 0–5; the added amount is rounded from level × a random value from 0 to 1 |
| Breeze Rods | 1–2, with player-kill credit | 4–8; this entry instead samples its added multiplier from 1 to 2 |
| Iron Golem's Iron Ingots | 3–5 | Still 3–5; this entry has no Looting function |

These ranges describe the named loot entries, not guaranteed drops from every encounter. The normal shared death-loot gate requires a nonbaby and enabled mob loot; subclasses and individual pools can impose their own rules. [Cow table][cow] · [Breeze table][breeze] · [Iron Golem table][iron-golem] · [Shared death-loot gate][loot-gate] · [Death-loot caller][death-loot]

Rare-drop chances use a separate condition. For example, the Wither Skeleton Skull's player-gated table roll is **2.5% without Looting, then 3.5%, 4.5% or 5.5% with I, II or III**. This is a chance to produce the table's one skull, not a multiplied stack of skulls. Other special drop routes remain their own rules. [Skull table][wither-skeleton] · [Enchanted chance condition][chance-condition]

### Attacker context and player-kill credit

For those count and rare-chance readers, Looting is taken from the **living attacker recorded in the killing damage source**, using its matching equipment slot at loot evaluation. With this definition that means the attacker's main hand, not an offhand sword or a sword elsewhere in the inventory. The shared readers do not substitute whichever player has kill credit. [Death-loot context][loot-context] · [Level lookup][level-reader] · [Matching slots][slot-reader] · [Count function][count-function] · [Chance condition][chance-condition]

Player-only pools separately test whether recent player-damage credit is present. The shared damage handler remembers a player hit for **100 game ticks** and can also credit a tamed Wolf's owner; the loot builder adds the player parameter only while that credit is live and the player resolves. A Wolf kill can therefore satisfy a player-only condition without reading Looting from its owner's sword. [Credit assignment][credit] · [Credit countdown][credit-tick] · [Context construction][loot-context] · [Player-kill condition][player-condition]

This also matters when using Fire Aspect: if ordinary lingering fire deals the killing damage, its normal damage source has no attacking entity. Recent player credit can still allow a player-gated pool, but it does not supply the missing Looting level. For the checked Looting bonuses, finish with damage whose attacker context actually supplies your enchanted main-hand weapon. [Fire tick][fire-tick] · [Unattributed fire source][fire-source] · [Damage-source constructor][damage-source] · [Loot context][loot-context]

### Equipped gear has its own drop chance

Looting's enchantment definition adds **1 percentage point per level** to eligible equipment-drop chances when the damage source's attacker is a **player**. For a slot using the shared default 8.5% chance, the resulting chance is 9.5%, 10.5% or 11.5%. This does not change ordinary item counts. [Looting equipment effect][looting] · [Default chances][drop-chances] · [Equipment-effect consumer][equipment-effect]

The mob equipment caller still requires a nonempty slot, a nonzero original drop chance, no prevent-equipment-drop enchantment on that item, and either recent player credit or a preserved-drop flag. A zero-chance slot is skipped before Looting is considered. Preserved equipment follows its own already-guaranteed branch. Looting cannot make every carried or worn item drop, and a mob-specific override can have different behavior. [Equipment caller][equipment-caller] · [Preserved threshold][drop-chances]

## Fire Aspect: ignition and cooked loot are separate

A qualifying direct hit requests **4 seconds at I or 8 seconds at II** of fire. The living target's burning-time attribute scales that request, and setting fire does not shorten a longer existing burn. See [Fire Protection's duration effect](ProtectionEnchantments.md#fire-duration-and-explosion-knockback). [Fire Aspect definition][fire-aspect] · [Ignition effect][ignite] · [Seconds and existing fire][ignite-time] · [Living duration multiplier][burning-time]

On the ordinary player path, Fire Aspect runs after an accepted attack through the attacker post-attack callback. It checks for a direct damage source and a nonbroken weapon. It has no separate full-charge requirement or recharge-based duration multiplier, but a miss, rejected hit or target damage cooldown can prevent the post-attack call. Successful secondary sweep hits also run post-attack effects. [Player attack][player-attack] · [Post-attack gate][post-helper] · [Effect target and condition][post-effect] · [Direct-source test][direct-test] · [Damage cooldown][hurt]

**A fire timer is not guaranteed health loss.** Fire-immune types such as Blaze and Wither Skeleton do not count as on fire and have their fire timer cleared; fire damage is also rejected by the shared immunity check. Fire Resistance separately rejects fire-tagged damage. Rain can extinguish a burning entity. Duration alone therefore does not establish an exact damage total. [Blaze immunity][blaze-type] · [Wither Skeleton immunity][wither-type] · [Fire state][on-fire] · [Fire tick][fire-tick] · [Base immunity][fire-immunity] · [Fire Resistance][hurt] · [Fire damage tag][fire-tag] · [Rain extinguishing][rain]

### Which drops can be cooked?

The inspected food entries below use **either** of two loot conditions: the dying entity is on fire, **or the direct attacker is a living entity whose main hand carries an applied enchantment in `#minecraft:smelts_loot`**. That tag contains Fire Aspect. Both levels qualify; the predicate does not demand level II. [Cow's complete condition][cow] · [Smelting enchantment tag][smelts-tag] · [Death-loot context][loot-context] · [Context entity mapping][entity-target] · [Main-hand predicate][equipment-predicate] · [Applied-enchantment predicate][enchantment-predicate] · [Positive-level check][level-predicate]

That second route is why a direct Fire Aspect melee kill can produce the relevant cooked food **even when the victim was not burning before the lethal hit**. Death loot can run inside the damage handler before the later ignition callback; the loot condition checks the weapon's enchantment independently. Holding Fire Aspect while a projectile is the direct attacker does not satisfy that living-attacker equipment test. [Damage and death ordering][death-order] · [Death loot][death-call] · [Post-hit ordering][player-attack] · [Equipment predicate][equipment-predicate]

| Loot tables containing that condition | Food entries it can smelt |
| --- | --- |
| [Chicken][chicken] | Chicken |
| [Cow][cow], [Mooshroom][mooshroom] | Beef |
| [Goat][goat], [Sheep][sheep] | Mutton |
| [Pig][pig], [Hoglin][hoglin] | Porkchop |
| [Rabbit][rabbit] | Rabbit |
| [Cod][cod], [Dolphin][dolphin] | Cod |
| [Salmon][salmon] | Salmon |
| [Polar Bear][polar-bear] | Cod or Salmon |
| [Guardian][guardian], [Elder Guardian][elder-guardian] | Cod and the result of their nested fishing-fish entry |
| [Zombie][zombie], [Husk][husk], [Zombie Villager][zombie-villager] | The Potato entry |

These are **17 bundled entity tables**, not a promise that every listed food roll succeeds or that every custom creature cooks its loot. Other pool conditions and random selections still apply. The smelting function also needs a matching Smelting recipe; without one it returns the original item. In particular, the Guardians' nested fish table can select fish other than Cod or Salmon, so reaching that function does not itself guarantee cooked fish. [Smelting function][smelt-function] · [Nested fish choices][fish-table] · [Beef recipe example][beef-recipe]

## Knockback: stronger push, not fixed travel distance

Knockback I adds **1** and II adds **2** to the attack's knockback value. On a successful ordinary player primary hit, that value starts with the attack-knockback attribute; a qualifying sprint hit adds another 1, then the living-target caller passes **half the total** to its knockback method. Thus the enchantment alone adds 0.5 or 1.0 to that method's strength input. These numbers are not blocks travelled. [Knockback definition][knockback] · [Attribute and enchantment lookup][knockback-lookup] · [Primary-hit consumer][primary-knockback]

The sprint addition requires sprinting with attack strength **above 0.9**. The enchantment modifier itself has no corresponding charge threshold or charge multiplier in this path, though the hit must still be accepted. The living target's knockback resistance reduces the passed strength, and its existing motion and grounded state affect the resulting velocity. This is also separate from the target's ordinary damage-reaction knockback. [Sprint condition][sprint] · [Enchantment modifier][knockback-helper] · [Velocity and resistance][knockback-motion] · [Ordinary hurt reaction][hurt-knockback]

Secondary sweep targets instead receive the sweep caller's fixed **0.4 strength input**, subject to their own knockback handling. Do not apply the primary-hit Knockback formula to every swept creature. See [Swords](../mechanics/Swords.md#what-a-sweep-can-hit) for the complete sweep rules and Sweeping Edge. [Sweep callback][sweep-call]

## Retained broken weapons

MattMC retains an ordinarily worn-out weapon as a broken stack. The checked Knockback modifier and Fire Aspect attacker post-attack path explicitly reject it, and the ordinary sword sweep also requires a nonbroken weapon. Repair it before relying on those combat effects. [Broken stack][broken] · [Knockback guard][knockback-helper] · [Post-attack guard][post-helper] · [Sweep guard][sweep-guard] · [Durability and repair](../mechanics/Durability.md)

Do not extend that result to every enchantment reader. The checked Looting count/chance lookup and equipment-effect iteration **do not reject a retained broken stack**; they can still read its saved main-hand enchantment. The separate Fire Aspect cooked-loot predicate likewise tests applied enchantments without a broken-item check. These are specific source-path distinctions, not a settled rule for every passive, custom item or loot table. [Looting level reader][level-reader] · [Slot reader][slot-reader] · [Equipment iteration][equipment-iteration] · [Equipment effect][equipment-effect] · [Cooked-loot equipment test][equipment-predicate] · [Item predicate][item-predicate] · [Applied enchantments][enchantment-predicate]

## Related pages

- [Enchanting](Enchanting.md), [Sharpness, Smite and Bane](MeleeDamageEnchantments.md), [Unbreaking and Mending](DurabilityEnchantments.md)
- [Swords](../mechanics/Swords.md), [Mace enchantments](../items/Mace.md#choosing-enchantments), [Combat timing](../mechanics/Combat.md#time-your-melee-attacks)
- [Cow](../mobs/Cow.md), [Breeze](../mobs/Breeze.md), [Wither Skeleton](../mobs/WitherSkeleton.md), [Iron Golem](../mobs/IronGolem.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The active definitions, nested item/enchantment tags, ordinary player attack and death-loot paths, selected table entries, Anvil rules and ordinary Librarian book route were checked. No in-game combat, loot-distribution, enchantment-roll or retained-broken-weapon test was run. Data packs, modified equipment and later builds can change these results. The described loot tables are selected through the entity's active loot-table route, with per-mob overrides still possible. [Default table mapping][default-loot] · [Entity table lookup][entity-loot] · [Mob override][mob-loot] · [Loaded table consumer][loot-context] · [Enchantment registry loading][registry]

[looting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/looting.json
[fire-aspect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/fire_aspect.json
[knockback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/knockback.json
[sweeping]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/sweeping_edge.json
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L162
[sword-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/sword.json
[swords]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/swords.json
[fire-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/fire_aspect.json
[primary]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L129
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L587-L600
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[table-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L175-L219
[tradeable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/tradeable.json
[librarian]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L338
[trade-book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1324
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L841
[item-reader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L127-L151
[count-function]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L61-L87
[chance-condition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java#L36-L48
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[death-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[loot-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[level-reader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L280-L292
[slot-reader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L101-L122
[credit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1339
[credit-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L464-L468
[player-condition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[fire-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L509-L520
[fire-source]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/damagesource/DamageSources.java#L46-L101
[damage-source]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/damagesource/DamageSource.java#L32-L66
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/DropChances.java#L10-L49
[equipment-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L342-L383
[equipment-caller]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[ignite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/effects/Ignite.java#L12-L19
[ignite-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L597-L607
[burning-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3690-L3693
[player-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L1135
[post-helper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L212-L248
[post-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L265-L293
[direct-test]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/damagesource/DamageSource.java#L32-L33
[hurt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1199
[blaze-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L319-L321
[wither-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1520-L1530
[on-fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L2520-L2523
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L2881-L2885
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[rain]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L847-L862
[smelts-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[entity-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootContext.java#L148-L166
[equipment-predicate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/advancements/critereon/EntityEquipmentPredicate.java#L58-L77
[enchantment-predicate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/predicates/EnchantmentsPredicate.java#L27-L55
[level-predicate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/advancements/critereon/EnchantmentPredicate.java#L31-L60
[death-order]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1198-L1239
[death-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1436
[smelt-function]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L28-L49
[fish-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[beef-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cooked_beef.json
[knockback-lookup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1497-L1500
[primary-knockback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1031-L1043
[sprint]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L979-L997
[knockback-helper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L202-L210
[knockback-motion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1583-L1597
[hurt-knockback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1200-L1228
[sweep-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1045-L1058
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[sweep-guard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1015-L1023
[equipment-iteration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L156
[item-predicate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/advancements/critereon/ItemPredicate.java#L27-L32
[default-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2063-L2066
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L397-L405
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L121-L128
[cow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/cow.json
[breeze]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/breeze.json
[iron-golem]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/iron_golem.json
[wither-skeleton]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/wither_skeleton.json
[chicken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/chicken.json
[mooshroom]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/mooshroom.json
[goat]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/goat.json
[sheep]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/sheep.json
[pig]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/pig.json
[hoglin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/hoglin.json
[rabbit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/rabbit.json
[cod]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/cod.json
[dolphin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/dolphin.json
[salmon]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/salmon.json
[polar-bear]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/polar_bear.json
[guardian]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/guardian.json
[elder-guardian]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json
[zombie]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/zombie.json
[husk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/husk.json
[zombie-villager]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/zombie_villager.json
