# Experience points, levels, and Mending

Experience **points** fill the progress bar; completing that bar increases your **level**. A level is not a fixed number of points. This matters when saving for enchantments, paying for repairs, or recovering experience after death. [Point collection and level conversion][levels]

For the on-screen choice between experience, locator and jump displays, see [Locator Bar](LocatorBar.md).

## How much is the next level?

The required points depend on your current level:

| Current level | Points for the next complete level |
| --- | --- |
| 0–14 | `7 + 2 × level` |
| 15–29 | `37 + 5 × (level − 15)` |
| 30 and above | `112 + 9 × (level − 30)` |

For example, an empty bar at level **0 needs 7 points**, at level **15 needs 37**, and at level **30 needs 112**. A partly filled bar needs only the remainder. These are point costs for the next level, not the total points needed from level zero. [Exact next-level calculation][costs]

## Collecting orbs

Ordinary experience orbs seek a nearby eligible player within **8 blocks**. Collection processes one stored orb count at a time with a **two-player-tick pickup delay**; visible nearby orbs need not all arrive in the bar immediately. Orbs can merge, so counting visible entities is not a reliable way to count points. Their ordinary lifetime is **6,000 ticking steps**, not an unconditional five-minute real-world guarantee. [Attraction and lifetime][orb-tick] · [Merging][orb-merge] · [Pickup][pickup] · [Pickup-delay countdown][delay]

Two checked routes illustrate why the source matters:

- A successful [fishing](Fishing.md) loot retrieval creates an orb worth **1–6 points per generated loot stack**. Reeling an empty cast or a hooked entity follows different branches
- Taking a [Grindstone](../blocks/Grindstone.md#experience-returned) result can award points calculated from the removed non-curse enchantments. It does not refund the exact original investment

Those are source-reviewed examples, not an exhaustive farming guide. Use each ore, mob, or cooking-device guide for its own reward conditions; a recipe XP field alone does not prove when a device pays it. [Fishing award][fishing] · [Grindstone award][grindstone]

## Why Mending can leave the bar unchanged

For equipment eligibility, acquisition, targeted repair and the odd-durability remainder, see [Unbreaking and Mending](../enchanting/DurabilityEnchantments.md).

Orb collection attempts **Mending repairs before adding leftover points to your experience bar**. The checked selection considers damaged eligible items in equipment slots, including the selected main-hand item and offhand, and chooses among matching candidates. A damaged Mending tool stored in another ordinary inventory slot is not a candidate until equipped. [Repair-before-XP path][pickup] · [Equipment selection][mending-selection] · [Selected main-hand mapping][player-equipment]

The bundled Mending effect supplies **two durability points of repair per experience point** before the remaining-damage cap and integer remainder calculation. For a simple example, a **5-point orb** can repair **10 damage** on the only eligible equipped item and leave **zero points** for your bar. If repair leaves points, the routine can try another eligible item before paying the final remainder to the player. Existing levels are not drained to do this. [Repair calculation][pickup] · [Bundled Mending effect][mending] · [Effect application][repair-effect]

For material repairs or repairs that should preserve selected enchantments, compare [Anvil operations](AnvilMechanics.md) and [Durability and repair](Durability.md). The [Enchanting guide](../enchanting/Enchanting.md#which-enchantments-can-appear) explains why rerolling the table will not produce Mending in the bundled pool; this page does not establish a new natural acquisition route for it.

## Spending and losing levels

An Enchanting Table's displayed offer requirement is the **level you must already have**. Its row then consumes **one, two, or three levels**, as well as the matching lapis amount, in ordinary Survival use. A level-30 bottom offer leaves a level-30 player at level 27. It does not spend thirty levels or a fixed three points. Follow the existing [required level versus levels spent](../enchanting/Enchanting.md#required-level-versus-levels-spent) guide for the complete setup. [Offer and payment][enchant-menu] · [Level deduction][level-payment]

The [Anvil](AnvilMechanics.md) also charges levels; the Grindstone awards points. On ordinary death without keepInventory, only a capped base point reward is generated and the old level is not retained. See [Death and respawn](DeathAndRespawn.md#what-you-keep-and-lose) before treating carried levels as recoverable savings.

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Point-to-level conversion, active orb pickup/repair, the bundled Mending effect, and table payment were checked. No orb, Mending, fishing, Grindstone, enchanting, or death gameplay test was run. Custom enchantment definitions, item components, and later builds can change these rules.

Related: [Enchanting](../enchanting/Enchanting.md) · [Anvil operations](AnvilMechanics.md) · [Death and respawn](DeathAndRespawn.md) · [Mechanics](Mechanics.md) · [Bottle o' Enchanting](../items/BottleOEnchanting.md)

[levels]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1435-L1456
[costs]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1488-L1494
[orb-tick]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L144-L170
[orb-merge]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L178-L229
[pickup]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L274-L311
[delay]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L246-L255
[fishing]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java#L435-L469
[grindstone]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L66-L103
[mending-selection]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L479-L497
[player-equipment]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/PlayerEquipment.java#L14-L22
[mending]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/enchantment/mending.json
[repair-effect]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L336-L340
[enchant-menu]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L134-L174
[level-payment]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1462-L1471
