# Tipped Arrow

A **Tipped Arrow** (`minecraft:tipped_arrow`) carries potion contents as ammunition for a [Bow](Bow.md), [Crossbow](Crossbow.md), or [Dispenser](../blocks/DispenserAndDropper.md). Its physical hit and its potion effects follow separate rules. Tipped Arrows stack up to **64 when their item components match**; different potion contents do not merge into one ordinary inventory stack. [Item properties][item] · [Default stack size][stack-size] · [Inventory matching][stack-match]

## Obtaining

At a Crafting Table, put **one [Lingering Potion](LingeringPotion.md) in the center** and **eight ordinary [Arrows](Arrow.md) around it**. The recipe produces **eight Tipped Arrows** carrying that potion's contents. [Loaded recipe][recipe-data] · [Recipe matching and output][recipe]

| Left | Center | Right |
| --- | --- | --- |
| Arrow | Arrow | Arrow |
| Arrow | Lingering Potion | Arrow |
| Arrow | Arrow | Arrow |

A drinkable or Splash Potion cannot replace the center bottle, and Tipped or Spectral Arrows cannot replace the outer ordinary Arrows. Taking the result consumes the eight arrows and the lingering bottle; **no empty Glass Bottle is returned**. The recipe also accepts an effect-free lingering bottle. Its result remains a **Tipped Arrow item with no effects**, without substituting Poison or changing it into an ordinary Arrow. [Exact ingredients][recipe] · [Ingredient consumption][craft-take] · [Crafting remainders][craft-remainder] · [Lingering item properties][lingering-item] · [Empty-effect bases][bases] · [Separate default-instance behavior][default-instance]

A **master Fletcher** may offer five Tipped Arrows for a base payment of **two Emeralds and five Arrows**. Its potion type is randomly selected from brewable types with effects, and the offer itself is not guaranteed on every Fletcher. The filter excludes [Luck](../effects/LuckAndUnluck.md#luck). Follow [Trading](../trading/Trading.md) for changing prices and stock. [Offer pool][trade-pool] · [Potion selection][trade-potion] · [Offer selection][trade-select] [trade-random]

With mob loot enabled, player-attributed kills of [Strays](../mobs/Stray.md#drops) can drop **Slowness Tipped Arrows**, while [Bogged](../mobs/Bogged.md#drops) can drop **Poison Tipped Arrows**. Follow those guides for drop rolls and Looting limits. These dropped items carry regular potion contents; the custom effects on the mobs' fired arrows are a separate combat behavior. [Stray drops][stray-loot] · [Bogged drops][bogged-loot] · [Mob-loot gate][mob-loot] · [Stray shots][stray-shot] · [Bogged shots][bogged-shot]

Enabled tipped variants are also listed in the [Inventory Browser](../mechanics/InventoryBrowser.md) for **Creative insertion**. Different catalog variants use this same item ID with different contents; seeing them in Survival does not supply the arrows. [Catalog entries][catalog] · [Variant construction][catalog-types]

## Usage

With a Bow or Crossbow in the main hand, put the desired Tipped Arrows in the **offhand** to give them priority over inventory ammunition. Use the weapon guides for drawing, loading, firing, and ammunition consumption. [Held-ammunition selection][held-ammo] · [Player inventory fallback][player-ammo]

When fired from a Bow, this ammunition can benefit from Power, Punch and Flame, but Infinity does not save it. See [Bow enchantments](../enchanting/BowEnchantments.md#infinity-which-ammunition-is-saved) for consumption and pickup limits. [Infinity condition][infinity]

A Dispenser shoots one selected Tipped Arrow, using its stored contents, and removes one from the loaded stack. As with weapon shots, hitting an entity does not guarantee that every stored effect will be accepted. [Dispenser registration][dispense-register] · [Projectile launch][dispense] · [Arrow factory][arrow-item] · [Effect application][arrow-effects]

## Behavior

The ordinary arrow damage is attempted **before** the potion effect callback. Only a hit that reaches that callback attempts to add the effects, and the target's effect-admission rules still apply. A Healing arrow can therefore injure or kill before healing; a Harming arrow is not a guaranteed fixed sum of physical and potion damage. See [instant-effect delivery](../effects/InstantEffects.md#drinking-splashing-clouds-and-arrows) for those cases. [Hit ordering][hit] · [Effect callback][arrow-effects]

For ordinary timed potion effects, the tipped item applies **one eighth of the stored duration**, keeping the effect's level. Positive finite durations are rounded down with a minimum of one tick. Crafting copies potion contents, **not the lingering bottle's duration multiplier**, so the result uses the arrow's own one-eighth scale. This scales time, not instant-effect strength. See [shared delivery rules](../effects/MovementEffects.md#delivery-changes-duration); custom ammunition and mob-created effects can differ. [Item scales][item] [lingering-item] · [Copied contents][recipe] · [Duration scaling][scale] · [Delivery][arrow-effects]

Recover eligible missed shots promptly. After **600 in-ground ticks**, normally **30 seconds at 20 TPS**, a lodged arrow whose stored potion contents are not completely empty changes its pickup item into an ordinary Arrow. This includes effect-free contents such as Water; completely empty contents do not enter this conversion. The timer counts time lodged in place and resets outside that in-ground state. Pickup eligibility still applies, so this does not make every fired arrow recoverable. [Potion loss][decay] · [In-ground timer][ground-timer] · [Pickup checks][pickup]

## Notes

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. No in-game crafting, trade, firing, inventory, pickup, or timing test was run. These are selected acquisition routes, not an exhaustive loot list. Components, enchantments, data packs, and recipient rules can change the reviewed defaults.

Related: [Arrow](Arrow.md) · [Bow](Bow.md) · [Crossbow](Crossbow.md) · [Brewing](../brewing/Brewing.md) · [Effects](../effects/Effects.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2246-L2250
[stack-size]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[stack-match]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Inventory.java#L95-L100
[default-instance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/TippedArrowItem.java#L14-L19
[recipe-data]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/tipped_arrow.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TippedArrowRecipe.java#L14-L46
[craft-take]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L108
[craft-remainder]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java#L17-L30
[lingering-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2251-L2255
[bases]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L11-L14
[trade-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L287-L295
[trade-potion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1527-L1538
[trade-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[trade-random]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[catalog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1673-L1678
[catalog-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[held-ammo]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L20-L40
[player-ammo]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1834-L1853
[infinity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/infinity.json
[dispense-register]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L66-L76
[dispense]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L42
[arrow-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ArrowItem.java#L17-L25
[arrow-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L415-L436
[scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L180-L187
[decay]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L73-L87
[ground-timer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L190-L208
[pickup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L598-L617
[stray-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/stray.json#L64-L106
[bogged-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/bogged.json#L64-L106
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[stray-shot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Stray.java#L59-L66
[bogged-shot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L109-L116
