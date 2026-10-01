# Shield

A Shield (`minecraft:shield`) blocks eligible incoming damage while actively held in use. It has **336 durability** in the bundled registration. Carrying it in a backpack slot does not protect you, and a raised Shield is not immunity to every hazard.

## Crafting and repair

Use **one Iron Ingot and six wooden-tool-material-tag items**. The pattern is Wood–Iron–Wood across the top, three Wood across the middle, and one Wood centered below. The wooden material tag currently delegates to the planks tag; integrated planks absent from that tag do not qualify by appearance alone.

The Shield's repairable component accepts the same wooden-tool-material tag. Use the [Anvil](../mechanics/AnvilMechanics.md) for material repair while keeping desired enchantments. The ordinary per-material repair amount is one quarter of maximum durability, rounded down: **84 durability** for the default Shield, limited by its missing durability.

## Raising and facing

Put the Shield in a usable hand, commonly the offhand, and hold the use action. Its blocking delay is **0.25 seconds, or 5 game ticks**. Raising it at the instant damage arrives does not skip that delay.

The default reduction covers damage sources within **90 degrees on either side of your horizontal facing**, a front-facing half-circle. Turn toward the source. The implementation calculates the source position relative to you; an attack behind you, or a source with no usable position, is not covered by this standard angle rule.

For a matching, non-bypassing hit, the checked reduction removes the incoming damage amount. This is the damage calculation, not a promise that every attack's separate status or world effect is cancelled.

## What gets through

- Damage in the bundled shield-bypass tag, including its inherited armor-bypass group
- Environmental examples explicitly listed there: Lava, Cactus, Campfire, Hot Floor, Lightning, and Falling Anvil damage
- Fall damage, Wither, and Ender Pearl damage through the inherited group
- An arrow with a positive piercing level, which has an explicit bypass check
- A hit arriving before the use delay or from outside the blocking angle

Blocking an ordinary projectile and surviving an environmental hazard are different checks. See [defensive item choices](../mechanics/DefensiveItems.md) before relying on one item for both.

## Durability and disabled blocking

The default blocked-hit durability rule uses the amount actually blocked:

- Below **3 damage points blocked:** no durability loss from this rule
- At least 3 points blocked: **floor(1 + blocked damage)** durability, before normal durability modifiers

A blocked direct, non-projectile hit by a living attacker can invoke its weapon's blocking-disable component. Ordinary axes are configured for **5 seconds**, and the default Shield scale keeps that as a **100-tick cooldown**. The player stops using the item when disabled. Do not assume continuous blocking will hold against axe attacks.

This checked path uses the weapon component rather than an old random axe-disable percentage. Changed item components can produce different durations.

## Banner decoration

The active special recipe accepts exactly one Banner and one Shield whose banner-pattern list is empty. It copies the Shield, then applies the Banner's pattern layers and base color. A Shield already carrying pattern layers fails this recipe's matching check.

Decoration does not change the default damage-reduction rule described here. Review the output before crafting with a Shield whose other components you want to preserve.

## Related pages

- [Defensive items](../mechanics/DefensiveItems.md)
- [Totem of Undying](TotemOfUndying.md)
- [Anvil mechanics](../mechanics/AnvilMechanics.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game blocking, combat, death-protection, decoration, or loot test was run. Item components, enchantments, tags, and game rules can change these values.

- [Shield recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/shield.json)
- [Wooden material tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json)
- [Shield properties and components](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Use initiation and axe properties](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java)
- [Blocking delay, angle, durability, and cooldown](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/BlocksAttacks.java)
- [Damage-source direction, piercing arrows, and active use](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Player blocking disable path](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Axe weapon component](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ToolMaterial.java)
- [Shield-bypassing damage](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json)
- [Inherited armor-bypassing damage group](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json)
- [Banner recipe implementation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/crafting/ShieldDecorationRecipe.java)
- [Registered decoration recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/shield_decoration.json)
