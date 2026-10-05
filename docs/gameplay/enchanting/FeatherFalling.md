# Feather Falling

**Feather Falling** (`minecraft:feather_falling`) reduces qualifying fall damage while you wear enchanted Boots. Its ordinary levels are **I–IV**. It is useful for drops and [Ender Pearl teleports](../items/EnderPearl.md#teleport-damage), but even level IV does not make every landing safe. [Definition][definition] · [Level bounds][levels] · [Equipped effects][equipped]

## Boots and damage types

The bundled supported items are **Leather, Copper, Chainmail, Golden, Iron, Diamond and Netherite Boots**. Wear the Boots in their equipment slot; carrying them in the inventory does not activate the protection. Other footwear does not become eligible just because it looks like Boots. See [Armor](../mechanics/Armor.md) for material defenses and repairs. [Supported-item tag][support] · [Seven Boots][boots] · [Equipment properties][armor-properties] · [Equipped effects][equipped]

Feather Falling checks the incoming damage type's `is_fall` tag. The bundled list has exactly three entries:

| Damage type | Exact ID |
| --- | --- |
| Fall | `minecraft:fall` |
| Ender Pearl | `minecraft:ender_pearl` |
| Stalagmite | `minecraft:stalagmite` |

The tag does not cover every collision or hazard encountered while falling. The enchantment also rejects damage tagged `bypasses_invulnerability`, and the shared reduction stage is skipped by `bypasses_effects` or `bypasses_enchantments`. Other immunity checks can prevent damage before this stage; for players, disabling the `fallDamage` game rule makes damage carrying `is_fall` harmless independently of these Boots. [Fall tag][fall-tag] · [Enchantment predicate][definition] · [Stage gates][stages] · [Player game-rule gate][player-immunity]

## Reduction and Protection stacking

Each Feather Falling level adds **3 enchantment protection points** against qualifying damage. These are separate from the armor bar's points.

| Feather Falling level | Protection points | Reduction at the enchantment stage |
| --- | ---: | ---: |
| I | 3 | 12% |
| II | 6 | 24% |
| III | 9 | 36% |
| IV | 12 | 48% |

These percentages follow the additive level formula and shared calculation: `damage entering this stage × (1 − min(total points, 20) / 25)`. The server sums matching equipped contributions and caps the total at **20 points**, giving at most **80% reduction at this stage**. [Definition][definition] · [Level formula][linear] · [Additive effect][add] · [Accumulation][equipped] · [Conditional application][consumer] · [Cap and calculation][cap]

**Feather Falling and Protection can share the same Boots.** Feather Falling is outside the exclusive group used by [the four protection choices](ProtectionEnchantments.md#choosing-an-enchantment). For qualifying damage, Feather Falling IV plus Protection IV on two worn armor pieces supplies `12 + 4 + 4 = 20` points; one of those two pieces can be the Boots themselves. Adding more matching points cannot raise the shared cap. [Feather Falling definition][definition] · [Protection contribution][protection] · [Exclusive group][exclusive] · [Compatibility check][compatibility]

For example, **10 damage points entering this stage** become 5.2 with Feather Falling IV alone, or 2 at the combined cap. This is not a fixed final health loss or a guaranteed survivable height: other damage stages, immunity, absorption and your [current health](../mechanics/Health.md) still matter. See [Combat's defensive sequence](../mechanics/Combat.md#layer-defenses-without-adding-their-percentages). [Stage order][stages] · [Player health calculation][player-damage]

## Getting and applying it

- **Enchanting Table:** all seven ordinary Boots and a plain Book can be candidates for Feather Falling. Use an eligible unenchanted item and follow [Enchanting](Enchanting.md) for shelves, lapis, levels and changing offers. Table-pool membership does not guarantee a roll: item enchantability, adjusted power, level cost ranges, random selection and compatibility still apply. [Table pool][table-tag] · [Pool membership][non-treasure] · [Boot registrations][boot-items] · [Armor enchantability][armor-properties] · [Book registration][book-item] · [Unenchanted-item gate][table-eligible] · [Active menu][table-menu] · [Pool selection][table-caller] · [Eligible results][table-selection] · [Primary-item rule][primary]
- **Anvil:** apply a Feather Falling [Enchanted Book](../items/EnchantedBook.md) to eligible Boots, or combine matching Boots or books. Equal levels can advance one level, up to IV; differing levels retain the higher, subject to the ordinary cap, compatibility and payment rules. Use [Anvil operations](../mechanics/AnvilMechanics.md) for costs and prior work. [Anvil inputs and combination][anvil]
- **Librarian:** with Trade Rebalance disabled, Feather Falling I–IV is a possible ordinary book offer. The book entry and its enchantment are selected, not guaranteed. Follow [Librarian trades](../trading/LibrarianTrades.md) for stock, prices and the separate optional Trade Rebalance rules. [Ordinary book entries][trade-entries] · [Active table choice][trade-switch] · [Offer selection][trade-selection] · [Tradeable pool][trade-tag] · [Nested membership][non-treasure] · [Book and level selection][trade-book]

These are selected acquisition routes, not an exhaustive loot or trading inventory.

## Retained broken Boots

MattMC retains fully damaged Boots. In this source snapshot, the equipped-enchantment iterator and damage-protection consumer do **not** reject a retained broken stack, so its Feather Falling can still contribute while worn, subject to the same damage checks and cap. This effect-specific behavior does not mean the Boots retain all their other defenses. Repair them using [Durability and repair](../mechanics/Durability.md); the broader retained-equipment policy remains under [issue #800](https://github.com/HungLo2020/MattMC/issues/800), and this guide does not describe a fix or an in-game reproduction. [Retained stack][broken-stack] · [Equipped reader][equipped] · [Protection consumer][consumer]

## Sources and verification

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. Checked the active definition, complete support and fall tags, equipped damage consumer, shared cap, and selected table, Anvil and ordinary Librarian routes. The percentages are calculated from those sources. No game, damage, enchanting, trading or repair test was run. Data packs, item components and later builds can change these rules.

[definition]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/enchantment/feather_falling.json#L1-L49
[levels]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L140-L146
[equipped]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L178
[support]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/item/enchantable/foot_armor.json#L1-L5
[boots]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/item/foot_armor.json#L1-L11
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Item.java#L459-L467
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/damage_type/is_fall.json#L1-L7
[stages]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1827
[player-immunity]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L718
[linear]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/LevelBasedValue.java#L131-L143
[add]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/effects/AddValue.java#L8-L16
[consumer]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L199-L207
[cap]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/damagesource/CombatRules.java#L32-L35
[protection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/enchantment/protection.json#L1-L46
[exclusive]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/armor.json#L1-L8
[compatibility]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L162
[player-damage]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/player/Player.java#L788-L805
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json#L1-L7
[table-tag]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json#L1-L5
[boot-items]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L1357-L1406
[book-item]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L1598-L1598
[table-eligible]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[table-menu]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L85-L119
[table-caller]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L197
[table-selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[primary]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L130
[anvil]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L127-L223
[trade-entries]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L338
[trade-switch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[trade-tag]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/enchantment/tradeable.json#L1-L9
[trade-book]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1325
[broken-stack]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
