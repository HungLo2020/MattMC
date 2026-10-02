# Mace

**Use a Mace for a heavy melee hit, then turn a controlled fall into a smash attack.** Its bonus depends on accumulated fall distance, so a missed target can leave you facing the fall you started. The registered item is `minecraft:mace`. [Registration][registration] · [Smash rules][mace]

## Obtaining

Craft a Mace from a [Heavy Core](HeavyCore.md#usage) and a [Breeze Rod](BreezeRod.md), following the linked core page's exact pattern. The [Heavy Core block guide](../blocks/HeavyCore.md) covers the ominous Vault route for the rare ingredient; a core in a Vault's preview is not a promised reward. [Mace recipe][recipe]

Mace is also an ordinary category-listed item in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), whose insertion route is available in Survival. That route is separate from collecting the crafting ingredients. [Category entry][creative]

## Usage

### Ordinary attacks and timing

Hold the Mace in the main hand and use **Attack** on a target. With ordinary player attributes and an unbroken, unmodified Mace, a fully charged basic melee attack starts at **6 damage points** before enchantments, critical hits, smash bonuses, and defenses. Its attack speed is approximately **0.6**, giving a nominal full-recharge interval of about **33⅓ game ticks**, or 1.67 seconds at 20 TPS. Early swings reduce the ordinary attack portion; [Combat](../mechanics/Combat.md#time-your-melee-attacks) explains that shared timing. [Mace modifiers][mace] · [Player damage and charge][player] · [Default attack speed][attributes]

### Landing a smash

A smash requires **more than 1.5 blocks of accumulated fall distance** and no Elytra fall-flying. Strike the target before landing. This condition is separate from the ordinary charged critical-hit rules: a smash can qualify without a fully charged attack, while an eligible critical can also multiply the attack after the Mace bonus is added. [Smash eligibility and bonus][mace] · [Attack calculation order][player]

The unenchanted smash adds damage in these bands:

| Part of accumulated fall distance | Added damage per block in that band |
| --- | ---: |
| First 3 blocks | 4 |
| Next 5 blocks, through 8 total | 2 |
| Beyond 8 blocks | 1 |

For example, a 5-block fall contributes **16 bonus damage points** before a possible critical multiplier and target defenses. These are damage-calculation inputs, not guaranteed health lost. [Bonus formula][mace]

A successful qualifying hit on a living target slows your vertical motion, resets the accumulated fall, and establishes the player's temporary fall-distance allowance at the impact position. It also pushes eligible nearby living entities away from the target within **3.5 blocks**; that surrounding effect is knockback, not another copy of the smash's damage. Knockback becomes stronger above 5 blocks of fall distance and is reduced by knockback resistance. Allies are filtered out, but this is not a blanket promise to spare every nearby animal. [Successful-hit effects and selection][mace] · [Successful-hit dispatch][player]

**A miss does not grant the landing allowance.** Even after a hit, dropping below the recorded impact height can still contribute fall damage. Plan a landing before jumping from a high ledge. [Impact handling][mace] · [Player fall calculation][player]

## Behavior

### Choosing enchantments

- **Density I–V:** adds **0.5 damage per enchantment level per block of accumulated fall distance** to an eligible smash. Density V adds 2.5 per fallen block, before any later critical multiplier and defenses. [Density][density] · [Application to the smash][mace]
- **Breach I–IV:** subtracts **0.15 per level** from the armor-reduction fraction calculated for the hit, clamped at zero. For example, a calculated 60% armor reduction becomes 45% with Breach I, or 0% with Breach IV. This does not remove separate Resistance or protection-enchantment reductions. [Breach][breach] · [Armor calculation][armor]
- **Wind Burst I–III:** a successful hit can create a wind launch when the attacker has at least 1.5 blocks of fall distance and is not flying. Its check includes both Elytra flight and ability-based flight. It has its own post-hit condition, rather than requiring the Mace's smash damage type. The launch can lead to ordinary fall damage: it does not receive the special allowance attached to a thrown [Wind Charge](WindCharge.md#jumping-and-fall-damage). [Wind Burst][wind-burst] · [Flying predicate][flying] · [Explosion effect][explode] · [Player explosion handling][server-player]

Density and Breach exclude each other, and share an exclusion group with Smite and Bane of Arthropods. Wind Burst is outside that group. The bundled ominous [Vault](../blocks/Vault.md#normal-versus-ominous-rewards) rare table can award a **Wind Burst I book**; the normal [Enchanting Table](../enchanting/Enchanting.md) pool excludes Wind Burst. [Damage exclusions][damage-exclusive] · [Wind Burst book][wind-book] · [Table pool][table-pool] · [Non-treasure list][non-treasure]

### Durability, repair, and a broken Mace

A Mace has **500 durability**. A successful ordinary weapon hit on a living target costs **1 durability**; using it to mine a block with nonzero destroy time costs **2**, before durability modifiers. Use a suitable mining tool instead. [Registered durability and wear][registration] · [Weapon-hit wear][stack] · [Tool settings][mace] · [Mining wear][item]

Repair it on an [Anvil](../mechanics/AnvilMechanics.md) with **Breeze Rods**, restoring up to **125 durability per rod**, or combine matching Maces using the methods in [Durability and repair](../mechanics/Durability.md#choose-a-repair-method). [Repair ingredient][registration] · [Material repair calculation][anvil]

**Mending** can also repair an equipped Mace from collected XP, including a retained broken one. Keep it in a hand while collecting orbs; a spare Mace in an ordinary inventory slot is not a candidate. [Mending][mending] · [Eligible items][durability-tag] · [Equipment selection][enchantment-helper] · [XP repair][xp]

MattMC retains a fully worn Mace as a **broken stack**. Its ordinary weapon attributes, smash bonus, hit hooks, and attacker post-hit enchantment effects stop working. Repair it before relying on it for a smash landing. [Retained stack and guarded hooks][stack] · [Attack guards][player] · [Equipment attributes][living] · [Post-hit guard][enchantment-helper]

There is a checked exception: **Breach is still read during the target's armor calculation even when the held Mace is broken**. That path does not test the broken state. This is a source-level behavior, not an in-game-tested reason to use broken equipment; it does not restore the Mace's damage attributes or smash. [Weapon lookup][damage-source] · [Armor path][armor] · [Enchantment iteration][enchantment-helper]

## Notes

- [Combat](../mechanics/Combat.md) covers shared attack timing, critical hits, and defenses
- [Wind Charge](WindCharge.md) covers the separate throwable movement item
- [Experience and Mending](../mechanics/Experience.md), [Enchanting](../enchanting/Enchanting.md), and [Items](Items.md) cover related equipment systems

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. No in-game crafting, damage, knockback, fall, enchantment, or repair test was run. Figures describe the bundled item and enchantment definitions; custom components, attributes, data packs, and target-specific rules can alter outcomes. The fall-distance allowance is not general fall immunity.

[registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L2034-L2045
[mace]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/MaceItem.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/crafting/mace.json
[creative]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[player]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/player/Player.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java
[density]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/density.json
[breach]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/breach.json
[armor]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/damagesource/CombatRules.java
[wind-burst]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/wind_burst.json
[flying]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/advancements/critereon/EntityFlagsPredicate.java
[explode]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/enchantment/effects/ExplodeEffect.java
[server-player]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1272-L1278
[damage-exclusive]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/damage.json
[wind-book]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_rare.json
[table-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[stack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java
[item]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Item.java
[anvil]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java
[living]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java
[enchantment-helper]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java
[damage-source]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/damagesource/DamageSource.java
[mending]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/mending.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[xp]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ExperienceOrb.java
