# Combat: timing attacks and choosing defenses

**Let melee attacks recharge, choose your movement for the hit you want, and match your defense to the incoming damage.** This guide covers the active ordinary player attack, Shield, Bow, and Crossbow paths. Equipment recipes and detailed values stay on their linked item and mechanics pages.

## Time your melee attacks

The attack-strength indicator, when enabled at the crosshair or hotbar, uses the player's recovering attack charge. Give it time to recover between deliberate hits. The server processes entity attacks through the player attack method; the following rules are not inferred from a weapon's animation. [Indicator][indicator] · [Hotbar indicator][hotbar-indicator] · [Server attack entry][attack-entry]

The normal full-charge interval is **20 divided by attack speed, in game ticks**. An ordinary unbroken material sword has 1.6 attack speed before other modifiers: a nominal **12.5-tick interval**, about 0.625 seconds at 20 TPS. The actual attack samples charge with a half-tick offset, so this is a timing reference, not a measured input or network delay. Different weapons and attribute modifiers can change the interval. [Charge calculation][charge] · [Default player attributes][player-attributes] · [Default speed][speed] · [Sword registrations][sword-items] · [Sword modifiers][sword-modifiers]

Hitting early reduces damage. Before item-specific bonuses and target defenses, the ordinary attack portion is multiplied by `0.2 + 0.8 × charge²`:

- Half charge gives **40%** of that portion
- Full charge gives **100%**

The separately calculated enchantment damage bonus scales with charge, too. An eligible entity attack resets the timer before checking whether the hit succeeds. Changing the main-hand item to a different item type also resets it; swapping weapons is not a shortcut to a charged hit. [Damage scaling and reset][attack-charge] · [Server enchantment calculation][enchanted-damage] · [Item changes][item-changes]

## Choose a critical, sprint hit, or sweep

All three special branches below require sampled charge **greater than 90%**. Waiting for full charge is the simple way to meet that threshold. [Attack conditions][attack-conditions]

- **Critical hit:** strike a living target while falling with positive fall distance. You must be off the ground, not climbing, not in Water, not blinded, not riding, and not sprinting. The attack portion is multiplied by 1.5 before the separate enchantment bonus is added. A jump alone does not establish a critical; the descending state and other conditions matter. [Critical conditions][attack-conditions] · [Blindness check][blindness]
- **Sprint hit:** a charged sprinting attack adds to knockback on a successful hit. The applied knockback branch slows horizontal movement and ends sprinting. Use it when pushing a target away matters; it does not also take the ordinary critical branch. [Sprint and knockback][attack-conditions]
- **Sword sweep:** stay grounded, avoid the critical and sprint-hit branches, and hold an unbroken item in the swords tag. Horizontal movement must be below the checked threshold of 2.5 times current movement speed. Standing still is a straightforward way to satisfy that movement test. A successful main hit then attempts the secondary hits. [Sweep conditions and hits][sweep] · [Bundled sword tag][sword-tag]

A sweep can catch nearby **non-allied living entities**, including animals that were not your selected target. It checks both the area around the main target and distance from you. Make space from bystanders before swinging. With full charge and no sweep or enchantment modifiers, the secondary hit starts at **1 damage point**, not your weapon's full main-hit damage. [Sweep selection and calculation][sweep] · [Default sweep ratio][sweep-ratio]

For axes' individual attack values and their blocking-disable role, use [Axes and Hoes](AxesAndHoes.md#material-and-combat-choices). An item's name alone does not grant ordinary sword sweep behavior.

## Layer defenses without adding their percentages

For ordinary player damage that reaches these stages, eligible item blocking happens first; the remaining damage then passes through armor, Resistance, and applicable protection enchantments. Other damage gates and target-specific rules still apply. [Blocking stage][blocking-stage] · [Player reduction order][player-reductions] · [Resistance and enchantments][effects-reductions]

[Armor and damage reduction](Armor.md#why-armor-points-are-not-a-fixed-reduction-percentage) owns armor values and toughness examples. Armor's fraction depends on the hit size and toughness; a full armor bar does not promise a fixed percentage against every attack. [Armor calculation][armor-calculation]

**Resistance I reduces eligible damage at its stage by 20%; Resistance II reduces it by 40%.** These apply to the damage left at that stage, not to the original hit independently. For example, 10 points reaching Resistance I become 8 before later protection. [Resistance calculation][effects-reductions]

The defenses have separate exclusions. The bundled Fall damage type bypasses armor; Starvation bypasses the effects stage; Void and generic-kill damage bypass Resistance; Sonic Boom bypasses protection enchantments as well as armor. Do not infer that one defense's bypass means every other defense uses the same list. [Armor bypass][armor-bypass] · [Effects bypass][effects-bypass] · [Resistance bypass][resistance-bypass] · [Enchantment bypass][enchantment-bypass]

## Raise a Shield before the hit

A default [Shield](../items/Shield.md#raising-and-facing) needs **5 ticks of active use** before blocking and covers the front-facing half-circle. Turn toward the source and raise it early. The standard client consumes attack clicks while an item is being used, so release the Shield's use action before your melee counterattack. [Shield components][shield-components] · [Active-use delay][blocking-delay] · [Direction and piercing][blocking] · [Angle resolution][block-resolution] · [Use and attack controls][using-controls]

A raised Shield does not cover every source. The bundled bypass tag includes environmental damage and the armor-bypass group, and an arrow with positive Piercing has an explicit bypass. A qualifying blocked direct melee hit from an unbroken ordinary axe can disable blocking for **100 ticks**, about five seconds at normal speed. Reposition when disabled instead of assuming held use will keep protecting you. The [Shield guide](../items/Shield.md#durability-and-disabled-blocking) owns wear and cooldown details. [Bypass tag][shield-bypass] · [Piercing and melee dispatch][blocking] · [Axe setting][axe-disable] · [Weapon component][tool-weapon] · [Attacker weapon value][disable-value] · [Player disable callback][disable-callback] · [Cooldown application][disable-application]

## Treat arrows and rockets as different attacks

- **Bow:** hold use to draw, then release. The normal draw-strength cap is reached at **20 ticks**; a very short release can produce no shot. A full draw marks the arrow critical. That projectile flag gives randomized extra damage on impact, separate from the falling melee critical rule. [Bow registration][bow-registration] · [Bow release and draw][bow] · [Critical arrow creation][arrow-create] · [Arrow impact][arrow-impact]
- **Crossbow:** complete a load, then use again to fire. Loaded ammunition is stored on the item, so you can prepare it before approaching a target. The default charge duration is **25 ticks** before enchantment changes. See [Crossbow](../items/Crossbow.md#load-now-fire-later) for Quick Charge, ammunition selection, and wear. [Crossbow registration][crossbow-registration] · [Loading and use][crossbow-use] · [Completed-load callback][crossbow-load] · [Stored ammunition firing][crossbow-fire] · [Charge duration][crossbow-charge]
- **Arrows:** impact damage depends on speed, projectile base damage, enchantments, critical randomness, and the target's defenses. Neither the [Arrow](../items/Arrow.md) item nor a fully charged weapon guarantees one fixed health loss. [Arrow impact][arrow-impact]
- **Firework Rockets:** the Crossbow uses a separate rocket projectile. Its damaging explosion requires explosion data; a plain rocket does not acquire explosion damage just by being fired. Explosive rockets can affect nearby living entities, so leave space from yourself, animals, and other players. [Rocket creation][rocket-create] · [Rocket damage][rocket-damage]

## Equipment and further reading

- [Armor](Armor.md), [Shield](../items/Shield.md), and [defensive item choices](DefensiveItems.md)
- [Bow](../items/Bow.md), [Crossbow](../items/Crossbow.md), and [Arrow](../items/Arrow.md)
- [Durability and repair](Durability.md): MattMC's retained broken equipment and function guards
- [Enchanting](../enchanting/Enchanting.md) and [status effects](../effects/Effects.md)
- [Inventory item browser](InventoryBrowser.md): ordinary item requests are available in Survival as documented there
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. No in-game combat, timing, damage, blocking, projectile, or equipment test was run. Timings use game ticks and assume 20 TPS for seconds. Data packs, item components, enchantments, attributes, and target-specific handlers can change outcomes. This guide does not extend these rules to every integrated weapon, projectile, Mace bonus, or Trident ability.

[indicator]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/client/gui/Gui.java#L497-L513
[hotbar-indicator]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/client/gui/Gui.java#L658-L669
[attack-entry]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1743-L1752
[charge]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1723-L1735
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[sword-items]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L1306-L1336
[sword-modifiers]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L90
[attack-charge]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L983
[enchanted-damage]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2140-L2142
[item-changes]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L282-L289
[attack-conditions]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L989-L1043
[blindness]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1919-L1921
[sweep]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1015-L1064
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/item/swords.json#L1-L11
[sweep-ratio]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L90-L92
[blocking-stage]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1194
[player-reductions]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L788-L808
[effects-reductions]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1827
[armor-calculation]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/damagesource/CombatRules.java#L16-L34
[armor-bypass]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[effects-bypass]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json#L1-L5
[resistance-bypass]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json#L1-L6
[enchantment-bypass]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json#L1-L5
[shield-components]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L2256-L2277
[blocking-delay]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3319-L3332
[blocking]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1270-L1305
[using-controls]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/client/Minecraft.java#L2096-L2122
[shield-bypass]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json#L1-L15
[axe-disable]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[disable-value]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3665-L3673
[disable-callback]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L752-L760
[disable-application]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/component/BlocksAttacks.java#L87-L130
[bow]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/BowItem.java#L25-L75
[arrow-create]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L91-L98
[arrow-impact]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L377-L436
[crossbow-use]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CrossbowItem.java#L65-L111
[crossbow-fire]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CrossbowItem.java#L167-L183
[crossbow-charge]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CrossbowItem.java#L248-L250
[rocket-create]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CrossbowItem.java#L148-L159
[rocket-damage]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L212-L249
[block-resolution]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/component/BlocksAttacks.java#L133-L170
[tool-weapon]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[bow-registration]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L1289
[crossbow-registration]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L2373-L2377
[crossbow-load]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CrossbowItem.java#L195-L225
