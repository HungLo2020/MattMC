# Wind Charge

**Throw a Wind Charge to push nearby entities, launch yourself from a surface, or trigger supported blocks.** Each ordinary Survival throw spends one charge. The registered item is `minecraft:wind_charge`; a Breeze's own projectile and a Mace's Wind Burst enchantment are separate attacks. [Item registration][registration] · [Throwing][item-use] · [Player projectile][charge]

## Obtaining

Craft **one [Breeze Rod](BreezeRod.md) into four Wind Charges**. This is a shapeless recipe: the rod can occupy any single slot in the personal crafting grid or a Crafting Table. Breeze Rods come from [Breeze](../mobs/Breeze.md) loot on a player-credited kill. [Recipe][recipe] · [Shapeless matching][shapeless] · [Rod loot][breeze-loot]

Both normal and ominous [Vaults](../blocks/Vault.md#normal-versus-ominous-rewards) can also award charges. A normal common-table selection can produce **1–3 or 4–12**, depending on which entry is chosen; an ominous common-table selection produces **8–12**. An opening makes multiple loot selections, so these are counts for selected entries, not guaranteed totals per key. [Normal rewards][normal-reward] · [Normal common entries][normal-common] · [Ominous rewards][ominous-reward] · [Ominous common entry][ominous-common] · [Reward resolution][vault-use]

The item is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), whose insertion route is available in Creative. Browser insertion is separate from gathering rods and opening Vaults. [Category entries][creative]

## Usage

Use the held charge to throw it immediately in the aimed direction. There is no draw-and-release step. A successful use consumes **one charge** in ordinary Survival and starts a **10-tick item cooldown**, about half a second at 20 TPS. The cooldown applies to the Wind Charge item type, so switching to another stack does not bypass it. Creative's infinite-material ability preserves the stack. [Throw and consumption][item-use] · [Registered cooldown][registration] · [After-use processing][stack] · [Cooldown conversion][cooldown] · [Cooldown grouping][cooldown-group] · [Server use gate][server-use]

### Jumping and fall damage

Aim at a nearby surface below you to have the burst push you upward and away. The result depends on your position relative to the burst, obstructions, and explosion-knockback resistance; the source does not establish one guaranteed jump height. A block impact places the burst slightly out from the struck face. [Block impact][abstract-charge] · [Push calculation][explosion]

A player affected by this projectile's burst records their position for a **temporary fall-distance allowance**. When that allowance applies, the fall calculation uses no more than the drop from the recorded height to the landing height. Landing at or above that height contributes no positive fall distance through this path; landing below it can still hurt. It is not permanent immunity, and a later explosion can replace the recorded context. [Player explosion handling][server-player] · [Fall calculation and context reset][player]

This allowance is attached to the **player Wind Charge projectile type**. Do not assume that a Breeze's shot or the [Mace's Wind Burst enchantment](Mace.md#choosing-enchantments) gives the same protection. [Exact projectile-type check][server-player] · [Wind Burst explosion source][enchantment-explode]

### Dispensers

A [Dispenser](../blocks/DispenserAndDropper.md) has a registered Wind Charge projectile behavior and fires charges from its facing side. These are projectiles, not recoverable thrown item stacks. [Dispenser registration][dispenser] · [Dispenser projectile construction][item-use] · [Projectile impact removal][abstract-charge]

## Behavior

For the separate death-triggered status, see [Wind Charged](../effects/TriggeredEffects.md#wind-charged).

### Direct hits and the burst

A direct entity hit attempts **1 damage point** before defenses, then bursts. The burst itself is configured to **push without explosion damage**. Being pushed off a ledge or into a hazard can still cause harm, and the push can affect nearby players and animals as well as the intended target. [Direct hit][abstract-charge] · [Burst configuration][charge] · [Damage and knockback separation][calculator] · [Affected entities][explosion]

The burst uses trigger-style block interaction rather than ordinary blast destruction. Supported nearby reactions include:

- Toggling **Levers** and pressing currently unpowered **Buttons**
- Ringing **Bells**
- Toggling compatible, unpowered **Doors and Trapdoors**, including wooden and Copper types; Iron types are excluded by their material setting
- Extinguishing lit **Candles**

Each reaction still depends on the affected block reaching its handler and satisfying its own conditions. Redstone-powered doors are not freely overridden by the burst. [Trigger dispatch][explosion] · [Ordinary block behavior][block-behavior] · [Lever][lever] · [Button][button] · [Bell][bell] · [Door][door] · [Trapdoor][trapdoor] · [Material settings][block-types] · [Candles][candles]

### Charges are consumed, not repaired

The ordinary Wind Charge registration has no durability component. It is a consumable projectile item, so the Mace and Trident's retained-broken-item and repair behavior does not apply. The projectile is removed on impact and supplies no pickup item. [Registration][registration] · [Consumption][item-use] · [Impact and item representation][abstract-charge]

An ominous [Trial Spawner](../blocks/TrialSpawner.md) can select Wind Charges from its overhead-item table, but the item spawner launches projectile items **downward** instead of dropping them as collectible stacks. Treat that selection as an encounter hazard, not a guaranteed loose-charge reward. [Overhead selection][ominous-items] · [Active spawner creation][trial-state] · [Projectile branch][ominous-spawner]

## Notes

Related: [Mace](Mace.md) · [Breeze Rod](BreezeRod.md) · [Breeze](../mobs/Breeze.md) · [Vault](../blocks/Vault.md) · [Items](Items.md)

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. No in-game crafting, cooldown, jumping, damage, block-trigger, dispenser, or fall-protection test was run. The block reactions listed here are selected checked handlers, not a promise that every redstone or light-producing block reacts. Custom data and later code can change the bundled behavior.

[registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L2026
[item-use]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/WindChargeItem.java
[charge]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/crafting/wind_charge.json
[shapeless]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java
[breeze-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/breeze.json
[normal-reward]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json
[normal-common]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_common.json
[ominous-reward]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json
[ominous-common]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_common.json
[vault-use]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L334
[creative]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[stack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java
[cooldown]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/component/UseCooldown.java
[cooldown-group]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemCooldowns.java
[server-use]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L333
[abstract-charge]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java
[explosion]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/ServerExplosion.java
[server-player]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1272-L1278
[player]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/player/Player.java
[enchantment-explode]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/enchantment/effects/ExplodeEffect.java
[dispenser]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L70-L79
[calculator]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/SimpleExplosionDamageCalculator.java
[block-behavior]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L176-L211
[lever]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/LeverBlock.java#L79-L88
[button]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L97-L106
[bell]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/BellBlock.java#L206-L215
[door]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L110-L122
[trapdoor]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L102-L111
[block-types]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java
[candles]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/AbstractCandleBlock.java#L101-L110
[ominous-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/items_to_drop_when_ominous.json
[trial-state]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L146-L168
[ominous-spawner]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/OminousItemSpawner.java#L70-L108
