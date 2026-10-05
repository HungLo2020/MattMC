# Ender Pearl

An Ender Pearl (`minecraft:ender_pearl`) is a throwable teleport item and an ingredient for an [Eye of Ender](EyeOfEnder.md). Pearls stack to **16**. Save some for crafting if you are preparing a stronghold expedition.

## Obtaining

Selected verified Survival sources are:

- **Enderman drops:** the bundled loot table gives a base 0–1 pearl, with a Looting count increase. A kill can give none
- **Adult Piglin bartering:** a completed barter using a Gold Ingot can select a 2–4-pearl reward. It is one possible weighted result, not a guaranteed exchange. The checked direct interaction requires an adult Piglin that can currently admire the offered ingot; babies are not this barter route
- **Stronghold corridor chests:** pearls are one weighted possibility in their loot table. See the [Stronghold guide](../structures/Stronghold.md) for locating and room differences

This is a selected source list, not an exhaustive inventory of trades, loot tables, or world-specific rewards.

## Throwing and cooldown

Use a pearl to throw it in your aim direction. Ordinary Survival use consumes one immediately; an unsuccessful later teleport does not refund that item.

The registered use cooldown is **20 ticks**, normally **1 second at 20 ticks per second**. The item-use path applies it after a successful throw, and the server rejects another use while the cooldown is active. It is shared by ordinary Ender Pearl stacks, so switching to another stack does not bypass it.

The thrown pearl teleports its eligible owner when it hits. The destination uses the projectile's position immediately before the collision, near the impact rather than a separately searched safe landing spot. Choose a broad, visible landing area and leave clearance around it. The successful player teleport resets accumulated fall distance, but landing hazards and the pearl's own damage still matter.

## Teleport damage

A successful player teleport attempts **5 damage points, or 2.5 hearts**, before applicable protection and immunity checks. Do not use that as a fixed final health loss:

- Ordinary armor protection does not reduce this damage: the bundled damage tag bypasses armor
- Pearl damage is also tagged as fall damage. The bundled [Feather Falling](../enchanting/FeatherFalling.md) enchantment can reduce it, and disabling the `fallDamage` game rule makes the player immune to this tagged damage
- Other applicable damage protection, absorption, or invulnerability can affect the result

Keep enough health for the teleport and for what is waiting at the destination. A successful hit on another entity calls a zero-damage projectile hit; the pearl is primarily a movement tool, not a direct-damage weapon.

## Conditions and risks

The impact must resolve an eligible owner. For a player in the same dimension, that means alive and not sleeping; the player's connection must also be accepting messages. Cross-dimension owner teleportation uses the portal-eligibility checks instead, which also reject dead or sleeping players. A pearl can therefore return an eligible owner to the pearl's dimension rather than being restricted to same-dimension travel.

The pearl itself has an additional restriction when leaving the End for the Overworld: a player owner must have seen the credits. This is not a guide to building a cross-dimension pearl system.

The player-teleport branch also has a **5% [Endermite](../mobs/Endermite.md#ender-pearl-spawns) spawn check** when the pearl's world allows monster spawning. Be ready for a mob as well as the destination terrain.

The default `enderPearlsVanishOnDeath` rule is true. Under that rule, an ordinary dead player's thrown pearls are discarded. Persistent pearl setups, logout/rejoin behavior, and chunk-loading arrangements are outside this page's verification; do not depend on one as an untested recovery plan.

## Crafting use

One pearl and one [Blaze Powder](BlazePowder.md) craft one Eye of Ender in a shapeless recipe. An eye has different behavior: it locates a stronghold or fills an End Portal Frame, rather than teleporting you when thrown.

## Related pages

- [Eye of Ender](EyeOfEnder.md)
- [Blaze Rod](BlazeRod.md)
- [Stronghold](../structures/Stronghold.md)
- [End](../dimensions/End.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game throwing, damage, barter, loot, or cross-dimension test was performed. The values describe active code and bundled data; item components, data packs, game rules, and server conditions can alter the experience.

- [Pearl registration, stack limit, and cooldown](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1761-L1762); [Throwing and consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/EnderpearlItem.java)
- [After-use component application](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L419); [Cooldown tick conversion](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/UseCooldown.java); [Shared cooldown groups](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemCooldowns.java); [Server cooldown gate](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L339)
- [Collision, teleport, damage, Endermites, death, and End-exit checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/ThrownEnderpearl.java); [Base portal eligibility](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L3065-L3067); [Sleeping restriction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3443-L3447); [Default death rule](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/GameRules.java#L206-L208)
- [Armor-bypass tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json); [Fall-damage tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/is_fall.json); [Feather Falling protection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/enchantment/feather_falling.json); [Game-rule immunity and player damage](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L750); [Armor, enchantment, effect, and absorption handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1846)
- [Enderman loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/enderman.json); [Entity registration and default loot-table wiring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java); [Runtime loot loading](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1528)
- [Piglin interaction dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L251-L263); [Adult barter eligibility and loot response](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java); [Barter reward table](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json); [Stronghold corridor loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/stronghold_corridor.json)
- [Eye recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/ender_eye.json)
