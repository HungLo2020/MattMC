# Sharpness, Smite and Bane of Arthropods

**Choose Sharpness for broad extra melee damage, Smite for the bundled undead targets, or Bane of Arthropods for its five tagged targets and slowing effect.** These three enchantments have ordinary levels I–V and cannot normally share one weapon. Their extra damage is separate from the weapon's base attack attribute. [Sharpness][sharpness] · [Smite][smite] · [Bane][bane] · [Compatibility][compatibility]

## Comparing the damage bonus

These are damage points added by the enchantment consumer when its target condition matches, before the player's attack-charge scaling and the victim's defenses:

| Enchantment | I | II | III | IV | V | Target condition |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Sharpness | 1 | 1.5 | 2 | 2.5 | 3 | No species restriction in this definition |
| Smite | 2.5 | 5 | 7.5 | 10 | 12.5 | Target in the Smite-sensitive tag |
| Bane of Arthropods | 2.5 | 5 | 7.5 | 10 | 12.5 | Target in the Bane-sensitive tag |

Sharpness uses `1 + 0.5 × (level − 1)`; Smite and Bane use `2.5 × level`. Smite/Bane add no damage when their tag fails. The checked damage context binds the tested entity to the victim, not the attacker. [Definitions][sharpness] · [Smite formula][smite] · [Bane formula][bane] · [Damage consumer and context][context]

For an ordinary player melee attack, the server calculates the enchantment difference separately and multiplies it by the current attack-strength fraction. The base attack uses a different recharge curve. The falling-critical multiplier changes the base part before the enchantment part is added; it does not simply multiply this whole bonus table by 1.5. Use [Combat](../mechanics/Combat.md#time-your-melee-attacks) for recharge and [Swords](../mechanics/Swords.md#what-a-sweep-can-hit) for sweep selection. Each secondary sweep victim gets its own target-sensitive enchantment calculation. [Player attack][attack] · [Server dispatcher][server] · [Weapon damage helper][damage]

These values are not guaranteed health loss. Armor, protection, immunity and target-specific handlers still apply. They do not establish the behavior of every custom weapon, special spear contact, projectile or modified stack.

## Which creatures qualify?

**Smite** follows `#minecraft:sensitive_to_smite`, which expands through the bundled undead, skeleton and zombie tags to these fifteen types:

- Skeleton, Stray, Wither Skeleton, Skeleton Horse and Bogged
- Zombie, Zombie Villager, Zombie Horse, Zombified Piglin, Zoglin, Drowned, Husk and Zombie Nautilus
- Wither and Phantom

Zombie Nautilus occurs through two tag paths but is one target type. A creature's name, appearance or imported-mod origin does not add it to this list. [Sensitive tag][smite-tag] · [Undead][undead] · [Skeleton types][skeletons] · [Zombie types][zombies]

**Bane of Arthropods** follows `#minecraft:sensitive_to_bane_of_arthropods`, whose bundled arthropod list contains **Bee, Endermite, Silverfish, Spider and Cave Spider**. Do not assume every insect-like custom mob receives this bonus. [Sensitive tag][bane-tag] · [Exact arthropod list][arthropod]

Modified tags can change those sets. Sharpness has no corresponding species filter, though an attack must still reach the damage path and be accepted by its target.

## Bane's slowing effect

A qualifying direct hit can also apply **Slowness IV** to a tagged living victim. Its definition fixes the amplifier at 3 and chooses a duration from these bounds:

| Bane level | Requested duration range at 20 ticks/second |
| --- | --- |
| I | 1.5 seconds |
| II | 1.5–2 seconds |
| III | 1.5–2.5 seconds |
| IV | 1.5–3 seconds |
| V | 1.5–3.5 seconds |

The active effect callback samples the duration and rounds the result to game ticks. This is a post-attack effect with both the species condition and a direct-damage-source condition. On the ordinary player path, the post-attack call follows an accepted hit; merely swinging nearby does not apply it. Existing stronger or longer effects still use the shared [effect replacement rules](../effects/CombatEffects.md#duration-clearing-and-commands), so the table is not a promise to reset every affected creature to exactly that duration. [Bane effect][bane] · [Post-attack target/conditions][post] · [Duration conversion][apply] · [Accepted-hit caller][attack]

See [Slowness](../effects/MovementEffects.md#slowness) for the attribute change. The numerical level is not an assertion that every target becomes completely immobile.

## Equipment, exclusions and obtaining them

The supported equipment differs:

| Enchantment | Bundled supported equipment | Direct table selection |
| --- | --- | --- |
| Sharpness | Seven material Swords and seven Axes: 14 types | Swords, or a plain Book |
| Smite and Bane | Those Swords and Axes, seven Spears and the Mace: 22 types | Swords, or a plain Book |

The material families are Wood, Stone, Copper, Iron, Gold, Diamond and Netherite. Support is defined by tags; the separately named Skelewag Sword is not an ordinary tagged sword. All three definitions use swords as their narrower primary list. The table's candidate filter uses that primary list, with its plain-Book exception; an eligible axe, spear or Mace can instead receive a compatible book at an Anvil. [Sharp weapons][sharp-tag] · [Broader weapon set][weapon-tag] · [Swords][swords] · [Axes][axes] · [Spears][spears] · [Primary definition][primary] · [Primary filtering][selection] · [Anvil eligibility][anvil]

All three are in the ordinary Enchanting Table pool. Use [Enchanting](Enchanting.md) for actual shelves, costs, eligibility and rerolls. Random offers and a displayed level requirement do not guarantee your preferred enchantment or level V. [Table pool][table] · [Included definitions][non-treasure]

They share an exclusion group with **Impaling, Density and Breach**. Ordinary table selection and Anvil combining respect compatibility; an Enchanted Book does not bypass it, and the Anvil's Creative support exemption does not remove its compatibility loop. Different weapons can of course carry different choices. [Exclusion group][exclusive] · [Symmetric check][compatibility] · [Anvil combination][anvil]

A compatible book transfers through the Anvil; equal levels can advance one level up to V, while differing levels keep the higher subject to the cap. See [Anvil mechanics](../mechanics/AnvilMechanics.md) for costs and prior work. Ordinary category entries also include level V books for these registered enchantments through the [inventory item browser](../mechanics/InventoryBrowser.md), whose documented cursor and capacity checks apply. That insertion route is separate from table enchanting. These are selected verified routes, not an exhaustive loot or trading inventory. [Combining][anvil] · [Category entries][book-category] · [Maximum-level book generation][books]

## Retained broken weapons

The server damage-modification helper returns the original damage for a broken weapon, and the attacker post-attack branch requires a nonbroken item. Thus these checked bonus-damage and Bane-slowing paths stop working on a retained broken weapon. This does not forbid ordinary unarmed-style attacks, and does not make a universal statement about unrelated armor passives or custom effects. Repair using [Durability and repair](../mechanics/Durability.md). [Broken damage gate][damage] · [Broken post-attack gate][post-helper]

## Related pages

- [Enchanting](Enchanting.md), [Swords](../mechanics/Swords.md), [Axes and Hoes](../mechanics/AxesAndHoes.md)
- [Spears](../mechanics/Spears.md), [Mace](../items/Mace.md), [Combat](../mechanics/Combat.md)
- [Unbreaking and Mending](DurabilityEnchantments.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The three definitions, nested target/support tags, ordinary player damage/post-hit callers, table/Anvil filtering and listed book route were checked. No combat, enchantment-roll, slowing, inventory or repair gameplay test was run. Data packs and modified item components can change these rules.

[sharpness]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/sharpness.json
[smite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/smite.json
[bane]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json
[compatibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L162
[context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L372-L394
[attack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L1136
[server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2140-L2143
[damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L180-L188
[smite-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_smite.json
[undead]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/undead.json
[skeletons]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/skeletons.json
[zombies]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/zombies.json
[bane-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_bane_of_arthropods.json
[arthropod]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/arthropod.json
[post]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L264-L294
[apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/ApplyMobEffect.java#L35-L47
[sharp-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/sharp_weapon.json
[weapon-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/weapon.json
[swords]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/swords.json
[axes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/axes.json
[spears]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/spears.json
[selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L587-L603
[anvil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L175-L215
[table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[exclusive]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/damage.json
[books]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2216-L2230
[post-helper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L212-L249

[primary]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L120-L130
[book-category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1953-L1956
