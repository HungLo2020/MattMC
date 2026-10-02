# Protection, Fire Protection, Blast Protection and Projectile Protection

Choose **Protection** for broad damage coverage, or one of the three specialized enchantments for a larger contribution against its matching damage types. All four have ordinary levels **I–IV**. They reduce damage in an enchantment stage separate from the armor and toughness supplied by the equipment itself. [Level bounds][levels] · [Definitions][protection] · [Fire][fire_protection] · [Blast][blast_protection] · [Projectile][projectile_protection] · [Damage stages][stages]

## Choosing an enchantment

Each matching piece contributes the following protection points. These are **enchantment points**, not the armor points shown on the armor bar.

| Enchantment and exact ID | Levels | Points at I / II / III / IV | Which damage qualifies? |
| --- | --- | --- | --- |
| Protection, `minecraft:protection` | I–IV | 1 / 2 / 3 / 4 | Broad coverage, subject to the bypass rules below |
| Fire Protection, `minecraft:fire_protection` | I–IV | 2 / 4 / 6 / 8 | Damage in `#minecraft:is_fire` |
| Blast Protection, `minecraft:blast_protection` | I–IV | 2 / 4 / 6 / 8 | Damage in `#minecraft:is_explosion` |
| Projectile Protection, `minecraft:projectile_protection` | I–IV | 2 / 4 / 6 / 8 | Damage in `#minecraft:is_projectile` |

The values come from each definition's additive level formula. Specialized protection contributes nothing to the damage calculation when its damage tag does not match. All four reject damage tagged `bypasses_invulnerability`; additional stage-wide bypasses are listed below. [Protection][protection] · [Fire][fire_protection] · [Blast][blast_protection] · [Projectile][projectile_protection] · [Level formula][linear] · [Addition][add]

You can use **only one of these four on the same piece** through ordinary enchanting and Anvil combining. Different pieces can carry different choices, so a mixed set is valid. The exclusion is also checked when combining Enchanted Books; Creative's supported-item exemption does not remove that compatibility check. [Exclusive set][exclusive] · [Compatibility][compatibility] · [Anvil checks][anvil]

## Adding pieces and reaching the cap

The server adds the contributions from matching equipped enchantments, then clamps the total to **20 points**. Each effective point removes **4% of the damage entering this enchantment stage**, so its maximum reduction is **80%**. The calculation is `incoming stage damage × (1 − min(total points, 20) / 25)` for these positive contributions. [Equipped accumulation][iterator] · [Conditional application][protection-consumer] · [Cap and calculation][cap]

For damage that reaches and qualifies for this stage:

| Worn combination | Matching protection points | Reduction at this stage |
| --- | ---: | ---: |
| Four Protection IV pieces | 16 | 64% |
| Two Blast Protection IV pieces, against explosion-tagged damage | 16 | 64% |
| Three Blast Protection IV pieces, against explosion-tagged damage | 24, capped at 20 | 80% |
| Three Protection IV pieces and one Blast Protection IV piece, against explosion-tagged damage | 20 | 80% |
| That same mixed set, against another damage type covered by Protection | 12 | 48% |

These examples follow the bundled definitions and shared cap. A 10-point amount entering the enchantment stage becomes 3.6 with four Protection IV pieces, or 2 at the cap. **Do not add these percentages to an armor-reduction percentage.** The ordinary player path processes armor/toughness first, then eligible Resistance, then enchantment protection, then absorption before subtracting health. Each stage receives the remaining damage. See [Armor and damage reduction](../mechanics/Armor.md) for the armor calculation and [Combat](../mechanics/Combat.md#layer-defenses-without-adding-their-percentages) for the broader defensive sequence. [Stage order][stages] · [Absorption and health][player-damage]

## Damage types and bypasses

The specialized checks use the incoming **damage type's tags**, rather than an attack's name, appearance or the item that seems to have caused it. The bundled matching lists are:

| Protection type | Exact matching damage IDs, all in the `minecraft` namespace |
| --- | --- |
| Fire | `campfire`, `fireball`, `hot_floor`, `in_fire`, `lava`, `on_fire`, `unattributed_fireball` |
| Blast | `bad_respawn_point`, `explosion`, `fireworks`, `player_explosion` |
| Projectile | `arrow`, `bullet`, `fireball`, `mob_projectile`, `thrown`, `trident`, `unattributed_fireball`, `wind_charge`, `wither_skull` |

These lists identify the damage types accepted by the protection predicate; they do not establish which type every integrated weapon sends. Fireball damage appears in both the Fire and Projectile lists, so those enchantments on separate pieces can both contribute to the same qualifying hit, within the shared cap. [Fire tag][fire-tag] · [Explosion tag][blast-tag] · [Projectile tag][projectile-tag] · [Damage context][context] · [Predicate evaluation][condition] · [Tag matching][predicate]

**Armor bypass and enchantment bypass are different checks.** For example, Fall and Drowning damage bypass ordinary armor but remain eligible for general Protection in this snapshot. Conversely, these four enchantments do not reduce:

- **Starvation** (`starve`): its `bypasses_effects` tag skips the combined Resistance/enchantment stage
- **Sonic Boom** (`sonic_boom`): its `bypasses_enchantments` tag skips enchantment reduction
- **Out of World and Generic Kill** (`out_of_world`, `generic_kill`): all four definitions reject their `bypasses_invulnerability` tag

[Armor-bypass list][armor-bypass] · [Effect bypass][effects-bypass] · [Enchantment bypass][enchantment-bypass] · [Invulnerability bypass][invulnerability-bypass] · [Active stage gates][stages]

Protection enchantments do not themselves supply absorption hearts or the Fire Resistance status effect. Their damage reduction still leaves some damage at the ordinary cap. Other defenses and immunity checks can decide whether a hit reaches this calculation at all. [Enchantments' effects][protection] · [Fire Protection effects][fire_protection] · [Damage processing][player-damage]

## Fire duration and explosion knockback

**Fire Protection also changes the burning-time attribute.** Each level contributes −0.15 of the base value from each active worn piece. With the default base of 1 and no other modifiers, one Fire Protection IV piece gives roughly **0.4 times the requested ignition duration**. Contributions from different slots add; the attribute cannot fall below zero. The ignition caller rounds the scaled duration upward to a whole tick. This changes an ignition request, and equipping the armor does not directly shorten a fire timer already running: the underlying timer setter here only extends the current timer when the new request is longer. [Fire attribute effect][fire_protection] · [Slot-specific modifiers][attribute-slot-id] · [Attribute calculation][attribute-math] · [Default and bounds][fire-attribute] · [Clamping][attribute-clamp] · [Ignition scaling][ignite] · [Timer update][fire-timer]

**Blast Protection also adds 0.15 explosion-knockback resistance per level** from each active worn piece. With the default base of zero, one Blast Protection IV piece supplies roughly 0.6, leaving roughly 40% of the push calculated by the checked explosion routine. Contributions add up to the attribute's maximum of 1. This secondary attribute is separate from the enchantment damage cap and ordinary melee knockback resistance. The explosion routine deals its damage before reading the knockback-resistance attribute, so an armor piece that breaks from that damage can lose its attribute bonus before the push is calculated. [Blast attribute effect][blast_protection] · [Attribute bounds][fire-attribute] · [Attribute calculation][attribute-math] · [Explosion consumer][explosion-push] · [Break callback][break-callback]

## Supported equipment and obtaining the enchantments

All four share the same bundled supported-item tag: **Helmets, Chestplates, Leggings and Boots in Leather, Copper, Chainmail, Iron, Gold, Diamond and Netherite, plus the Turtle Shell helmet** (`minecraft:turtle_helmet`). This is 29 item types. Other equipment does not become eligible merely because it supplies armor: the reviewed tag does not include Centipede Leggings, Moose Headgear, Elytra, horse armor or Wolf Armor. [Armor tag][armor-tag] · [Head items][head-tag] · [Chest items][chest-tag] · [Leg items][leg-tag] · [Foot items][foot-tag]

The effects are assigned to armor slots. For ordinary player equipment, **wear the piece in its equipment slot**; carrying it in a hotbar or ordinary inventory slot does not activate these protection contributions. Stored enchantments on an Enchanted Book are not an equipped armor enchantment. [Slot assignment][protection] · [Slot groups][slots] · [Player armor slots][equipment-slots] · [Equipped component reader][iterator] · [Book component][book-component]

- **Enchant at a table:** all four are in the ordinary table pool, and none has a narrower primary-item list than its supported armor tag. The listed armor registrations enable enchanting. Use an eligible unenchanted piece, or a plain Book, and follow [Enchanting](Enchanting.md) for shelves, costs and rerolling. Offers are random; a level-30 requirement does not guarantee level IV or your chosen protection. [Table pool][table-tag] · [Pool contents][non-treasure] · [Armor registrations][armor-items] · [Turtle registration][turtle-item] · [Armor properties][armor-properties] · [Unenchanted-item gate][table-eligible] · [Eligibility][eligibility] · [Selection caller][table-pool] · [Offer selection][table-selection]
- **Apply or combine at an Anvil:** transfer a compatible [Enchanted Book](../items/EnchantedBook.md) onto supported armor, or combine matching equipment. Equal levels can advance by one up to IV; differing levels keep the higher, subject to the cap and compatibility rules. See [Anvil mechanics](../mechanics/AnvilMechanics.md) for payment, prior work and repair. [Combination checks][anvil]
- **Use the inventory browser:** ordinary category entries include a separate **level IV Enchanted Book for each of these four enchantments**. The category generator emits maximum-level books; its separate all-level entries are search-tab-only and are not the entries collected by this inventory panel. These ordinary category books can be requested in Survival or Creative through the [inventory item browser](../mechanics/InventoryBrowser.md), subject to its normal cursor, capacity and feature checks. This is a separate route from enchanting equipment at a table. [Category output][category-books] · [Book generation][book-levels] · [Tab visibility][tab-visibility] · [Browser list][browser-list] · [Click and cursor][browser-click] · [Empty-slot capacity][browser-capacity] · [Client request][browser-client] · [Server checks][browser-server] · [Server insertion][browser-insert]

## Retained broken armor in this snapshot

MattMC retains a fully damaged armor stack. Its armor/toughness attributes and the **Fire Protection burning-time and Blast Protection knockback attributes stop applying** when the equipment-break callback removes modifiers; ordinary equipment updates also exclude broken stacks from applying them. [Retained stack][broken-stack] · [Break removal][break-callback] · [Equipment update][equipped-attributes] · [Enchantment modifier dispatch][modifier-dispatch] · [Stack modifier dispatch][item-modifiers]

The **damage-protection contribution follows a different path**. The active equipped-enchantment iterator reads a nonempty stack's applied enchantments and matching slot without checking whether that stack is broken. Its damage-protection consumer likewise has no broken-stack gate. As a result, these four enchantments on retained broken armor can still contribute their matching damage-protection points while worn, subject to the same damage predicates and cap. This describes the checked implementation; it is not a claim that the armor's other defenses still work. See [Durability and repair](../mechanics/Durability.md) for repairing retained equipment. [Iterator and accumulator][iterator] · [Consumer][protection-consumer] · [Stage gate][stages] · [Shared cap][cap]

## Related pages

- [Combat status effects](../effects/CombatEffects.md)

- [Armor and damage reduction](../mechanics/Armor.md)
- [Enchanting](Enchanting.md)
- [Anvil mechanics](../mechanics/AnvilMechanics.md)
- [Defensive items](../mechanics/DefensiveItems.md)
- [Inventory item browser](../mechanics/InventoryBrowser.md)

## Sources and verification

Source-reviewed on **2026-10-02** at commit `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. This guide follows the four bundled enchantment definitions, recursive eligibility/exclusion and damage tags, active equipment/damage consumers, table/Anvil checks and ordinary browser-book route. These acquisition routes are not an exhaustive loot or trading inventory. No in-game combat, enchanting, inventory or repair tests were run. Modified item components and data packs can change the definitions and tags; the examples assume the bundled values and the stated conditions.

[protection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/protection.json
[fire_protection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/fire_protection.json
[blast_protection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/blast_protection.json
[projectile_protection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/projectile_protection.json
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/armor.json
[head-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/head_armor.json
[chest-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/chest_armor.json
[leg-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/leg_armor.json
[foot-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/foot_armor.json
[exclusive]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/armor.json
[table-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[blast-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/is_explosion.json
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json
[armor-bypass]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
[effects-bypass]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json
[enchantment-bypass]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json
[invulnerability-bypass]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json
[linear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/LevelBasedValue.java#L131-L147
[add]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/AddValue.java#L8-L20
[levels]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L140-L146
[eligibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L116-L130
[compatibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L180
[slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EquipmentSlotGroup.java#L14-L26
[equipment-slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EquipmentSlot.java#L13-L20
[iterator]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L127-L177
[protection-consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L199-L207
[context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L383-L392
[condition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/predicates/DamageSourceCondition.java#L25-L35
[predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/advancements/critereon/DamageSourcePredicate.java#L32-L45
[cap]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/damagesource/CombatRules.java#L32-L35
[stages]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1827
[player-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L789-L808
[equipped-attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2649-L2683
[break-callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3552
[broken-stack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
[fire-attribute]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L24-L31
[attribute-math]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/AttributeInstance.java#L150-L167
[attribute-clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/RangedAttribute.java#L30-L33
[attribute-slot-id]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/EnchantmentAttributeEffect.java#L31-L36
[modifier-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L393-L398
[item-modifiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L1016-L1020
[ignite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3690-L3693
[fire-timer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L597-L610
[explosion-push]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/ServerExplosion.java#L184-L198
[armor-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1357-L1406
[turtle-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1281-L1281
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L459-L467
[table-eligible]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[table-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L197
[table-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[anvil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L175-L223
[category-books]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1953-L1956
[book-levels]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2216-L2234
[book-component]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L121-L124
[tab-visibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTab.java#L227-L239
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1903
[browser-insert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2006-L2018
[browser-click]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L376-L390
[browser-capacity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L595-L630
