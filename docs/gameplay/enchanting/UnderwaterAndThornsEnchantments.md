# Aqua Affinity, Respiration and Thorns

Use **Aqua Affinity** to remove the normal submerged-mining penalty, **Respiration** to spend air more slowly, and **Thorns** to give qualifying attackers a chance of taking retaliatory damage. Aqua Affinity and Respiration work together on a helmet; Thorns can share that helmet through an Anvil. These three definitions do not exclude one another. [Default exclusion set][codec-defaults] · [Aqua Affinity][aqua] · [Respiration][respiration] · [Thorns][thorns] · [Compatibility][compatibility]

## Choosing equipment

| Enchantment and exact ID | Ordinary levels | Supported equipment | Active slot |
| --- | --- | --- | --- |
| Aqua Affinity, `minecraft:aqua_affinity` | I | The eight helmets listed below | Head |
| Respiration, `minecraft:respiration` | I–III | The same eight helmets | Head |
| Thorns, `minecraft:thorns` | I–III | The 29 armor items listed below | Any equipment slot, including either hand |

The helmet list is **Leather, Copper, Chainmail, Gold, Iron, Diamond, Netherite and Turtle Shell** (`minecraft:turtle_helmet`). Thorns also supports Chestplates, Leggings and Boots in those seven ordinary armor materials, for **29 supported item types**. These are resolved item-tag memberships; Elytra, animal armor and integrated special armor do not qualify just because they are wearable. [Head support][head-support] · [Helmets][heads] · [Armor support][armor-support] · [Chest support][chest-support] · [Leg support][leg-support] · [Foot support][foot-support] · [Chestplates][chests] · [Leggings][legs] · [Boots][feet]

**Wear the helmet** for Aqua Affinity and Respiration. Thorns has the broader `any` slot assignment: carrying a Thorns-enchanted armor piece in the selected main hand or offhand also makes it eligible for the victim-side effect. An unused hotbar slot or ordinary inventory slot does not. An Enchanted Book stores an enchantment for later application and does not activate it merely by being held. [Slot definitions][slots] · [Equipped iteration][iterator] · [Player hand mapping][player-equipment] · [Stored book component][book-storage]

## Aqua Affinity: underwater mining

The ordinary submerged-mining multiplier is **0.2**. Aqua Affinity I multiplies that attribute by **5**, making it **1.0** with the default base and no other modifiers. The mining calculation uses it when your **eyes are in water**. This restores that part of mining speed to its ordinary value. [Definition][aqua] · [Default attribute][attributes] · [Modifier calculation][attribute-math] · [Mining consumer][mining]

The separate **not-on-ground penalty still divides mining speed by five**. With otherwise identical conditions, floating underwater has a combined multiplier of 0.04 without Aqua Affinity and 0.2 with it; standing on the bottom gives 0.2 and 1.0 respectively. The enchantment does not replace the correct tool, change a block's drop requirements, remove Mining Fatigue or provide breathing. See [mining enchantments](MiningEnchantments.md) for tool speed and loot choices. [Active mining calculation and tool check][mining] · [Aqua Affinity's effect][aqua]

## Respiration: a slower, random air drain

Respiration adds its level to the oxygen-bonus attribute. With the default base of zero, each underwater air-decrease check has the following chance of actually spending one air unit:

| Level | Chance of spending air on each check | Average time to spend the same air supply |
| --- | ---: | ---: |
| None | 100% | 1× |
| I | 50% | 2× |
| II | About 33.3% | 3× |
| III | 25% | 4× |

These are **probabilities and averages**, not a fixed extra countdown or guaranteed safe dive duration. The formula is `1 / (oxygen bonus + 1)`. Respiration slows depletion; it does not increase the maximum air supply or refill it by itself. [Definition][respiration] · [Default attribute][attributes] · [Air-consumption and refill calculation][air-chance]

The active air-drain branch requires eyes in water, outside a Bubble Column, and no applicable underwater-breathing exemption. Once air reaches **−20**, the checked path resets it to zero and requests **2 drowning damage points**, or one heart before applicable defenses. Respiration continues to affect the air-decrease checks after the visible supply runs out, but does not make drowning harmless. [Air and drowning dispatch][air-use] · [Drowning threshold][drowning]

For sustained work, plan an air source or [Water Breathing](../effects/WaterAndFireEffects.md) rather than treating Respiration as unlimited breathing. The two enchantments address separate problems: Respiration does not increase mining speed, and Aqua Affinity does not conserve air. [Their separate effects][respiration] · [Aqua Affinity][aqua]

## Thorns: retaliation and wear

Each eligible Thorns piece gets its own chance on a qualifying victim-side post-attack check: **15% at I, 30% at II, and 45% at III**. Raising the level raises the trigger chance. On a successful roll with an attacker available, the effect requests a random **floating-point damage value using 1.0–5.0 damage-point bounds**, roughly half a heart to two and a half hearts before the attacker's defenses and other damage rules. This is not a fixed integer damage amount that increases with level. [Definition][thorns] · [Chance test][roll] · [Target selection][post-target] · [Damage effect][damage-effect] · [Random-value calculation][random-float]

The effect targets the damage source's **attacker**, rather than automatically targeting its projectile. The checked ordinary player and mob melee paths and the Arrow hit path call this dispatcher after their successful-hit gate. Damage without an attacker cannot apply the retaliation effect. Do not assume every environmental hazard or custom weapon reaches this same post-attack path. [Victim dispatch][post-attack] · [Player melee][player-attack] · [Mob melee][mob-attack] · [Arrow hit][arrow-attack] · [Attacker selection][post-target]

Multiple equipped pieces are evaluated separately. More pieces mean more trigger opportunities, but their nominal damage requests are **not a promise of that much total health loss**: every retaliation goes through the target's damage processing. The checked dispatcher does not select one random piece for the entire set. [Equipment iteration][iterator] · [Victim branch][post-attack] · [Damage request][damage-effect]

A triggered effect also requests **2 durability damage to the specific Thorns piece that triggered**. This happens after the damage request without testing whether the attacker actually lost health. It is additional to any normal equipment wear caused by the incoming hit. The request uses ordinary durability processing, so [Unbreaking](DurabilityEnchantments.md) and the Creative/infinite-materials exemption can alter the actual wear; it is not an unconditional loss of two points. [Effect order][all-effects] · [Wear request][damage-item] · [Durability gates][broken]

## Obtaining and combining

- **Enchanting Table:** Aqua Affinity and Respiration can be offered on the supported unenchanted helmets. Thorns can be offered directly on the seven supported **Chestplates**, with levels I–II reachable in the bundled setup. Plain Books can roll all three from the ordinary table pool. Use [Enchanting](Enchanting.md) for shelves, lapis, level requirements and rerolling. [Pool][table-tag] · [Members][non-treasure] · [Table caller][table-pool] · [Eligibility and level selection][selection] · [Armor enchanting properties][armor-properties] · [Armor registrations][armor-items] · [Turtle Shell registration][helmet-item]
- **Thorns III:** ordinary table enchanting cannot reach its minimum selection strength of **50**. Even Gold, the most enchantable supported Chestplate at **25 enchantability**, reaches at most **49** from a level-30 offer; a plain Book reaches less. Combine two Thorns II copies at an Anvil or obtain a level III book through a route that selects that level. This limit follows the bundled costs and selection formula, rather than an in-game probability test. [Thorns costs][thorns] · [Armor enchantability][armor-materials] · [Book enchantability][book-item] · [Table cap][costs] · [Selection calculation][selection]
- **Anvil:** apply a compatible Enchanted Book to supported equipment, including Thorns on a Helmet, Leggings or Boots. Combining equal levels raises the result by one up to the ordinary maximum; different levels use the higher, capped at that maximum. See [Anvil mechanics](../mechanics/AnvilMechanics.md) for costs and repairs. [Support, compatibility and level checks][anvil]
- **Standard Librarian trades:** all three belong to the tradeable pool. With the experimental trade-rebalance feature **disabled**, Librarian book offers at Novice through Expert can randomly select them, including Respiration III or Thorns III. A particular villager is not guaranteed the desired book. The offer takes emeralds and one Book; inspect its actual price. This statement does not apply the standard pool to the experimental trade tables. [Tradeable pool][librarian-pool] · [Offer lists][librarian-offers] · [Random book and payment][librarian-book] · [Active feature switch][active-trades] · [Offer generation][offer-call]

## Retained broken equipment

MattMC retains fully damaged equipment as a broken stack. **Aqua Affinity and Respiration stop supplying their attributes when the helmet breaks**: the equipment-break callback removes its modifiers. Equipping an already-broken helmet also does not apply them, because the normal equipment-update path rejects broken stacks. Repair the helmet to restore its functionality; see [Durability and repair](../mechanics/Durability.md). [Retained stack][broken] · [Break-time removal][break-callback] · [Equipment updates][equipment-update] · [Enchantment modifier enumeration][modifiers] · [Stack modifiers][stack-modifiers]

**Thorns follows a different path in this snapshot.** The victim-side dispatcher and equipped-enchantment iterator do not reject a nonempty broken stack. A retained broken Thorns piece can therefore still roll and request retaliation while in an eligible equipment slot, including when re-equipped. Its extra durability request then has no effect because ordinary wear processing returns zero for an already-broken item. The attacker's separate broken-weapon guard does not disable this victim branch. This describes the reviewed implementation, not a general rule for all enchantments or a decision about future broken-item behavior. [Separate victim and attacker branches][post-attack] · [Iterator][iterator] · [Wear gate][broken]

## Related pages

- [Armor and damage reduction](../mechanics/Armor.md)
- [Protection enchantments](ProtectionEnchantments.md)
- [Equipment curses](EquipmentCurses.md)
- [Unbreaking and Mending](DurabilityEnchantments.md)
- [Enchanting](Enchanting.md)

## Sources and verification

Source-reviewed on **2026-10-04** at commit `78e8e0423084f010bb47e36132550619b37644c2`. The bundled enchantment registry resources, nested item tags, live attribute and post-attack consumers, equipment updates, table/Anvil rules and standard Librarian offer generation were traced. [Effect component registration][effect-components] · [Entity-effect registration][effect-types] · [World-loading caller][world-load] · [Registry wiring][registry] · [Resource and tag loading][resource-loading]

No in-game underwater mining, air-drain, combat, durability, enchanting or trading tests were run. Acquisition examples are selected routes, not an exhaustive loot inventory. The numerical examples assume the bundled definitions and stated conditions; data packs, custom components and later builds can change them.

[codec-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L60-L70
[aqua]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/aqua_affinity.json
[respiration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/respiration.json
[thorns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/thorns.json
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L180
[head-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/head_armor.json
[heads]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/head_armor.json
[armor-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/armor.json
[chest-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/chest_armor.json
[leg-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/leg_armor.json
[foot-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/foot_armor.json
[chests]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/chest_armor.json
[legs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/leg_armor.json
[feet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/foot_armor.json
[slots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EquipmentSlotGroup.java#L14-L25
[iterator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L127-L157
[player-equipment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/PlayerEquipment.java
[book-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2110-L2117
[attributes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L69-L89
[attribute-math]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/attributes/AttributeInstance.java#L150-L167
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656
[air-chance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L569-L582
[air-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[drowning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceCondition.java#L21-L24
[post-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L265-L293
[damage-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/effects/DamageEntity.java#L25-L29
[random-float]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/util/Mth.java#L643-L645
[post-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L212-L249
[player-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1030-L1102
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[arrow-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L421-L434
[all-effects]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/effects/AllOf.java#L31-L39
[damage-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/effects/ChangeItemDamage.java#L20-L27
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[table-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[table-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L197
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L459-L466
[armor-items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1357-L1406
[helmet-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1281-L1281
[armor-materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L8-L32
[book-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1598-L1598
[costs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L499-L514
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L119-L223
[librarian-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/tradeable.json
[librarian-offers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L338
[librarian-book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1325
[active-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L843
[offer-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[break-callback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3552
[equipment-update]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2649-L2687
[modifiers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L393-L399
[stack-modifiers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1016-L1019
[effect-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentEffectComponents.java#L43-L54
[effect-types]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/effects/EnchantmentEntityEffect.java#L18-L31
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L44
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L119-L130
[resource-loading]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L334
