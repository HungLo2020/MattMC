# Totem of Undying

A Totem of Undying (`minecraft:totem_of_undying`) is a single-use death-protection item. It stacks to one and must be in a **main hand or offhand** when the qualifying lethal damage is processed. A Totem elsewhere in the inventory is not included in that check.

## Obtaining

The bundled Evoker loot table has a separate pool that gives **one Totem**. That pool has no player-kill condition and no Looting count modifier. The table's Emerald pool has different conditions; do not transfer those to the Totem.

Ordinary entity-loot gates still apply, including the mob-loot game rule. This page verifies the drop table, not an Evoker location or raid-farm design.

## Activation

Hold the Totem before the dangerous hit. The lethal-damage protection code searches the hands for a death-protection component, consumes **one** matching item, sets health to **1 point (half a heart)**, and applies the Totem's effects.

The bundled effect sequence first **clears existing status effects**, then grants:

| Effect | Duration in game ticks | Approximate time at 20 ticks/second |
| --- | --- | --- |
| Regeneration II | 900 | 45 seconds |
| Absorption II | 100 | 5 seconds |
| Fire Resistance I | 800 | 40 seconds |

Both helpful and harmful existing effects are cleared by the first step. The health value is not restored to full immediately; regeneration happens afterward. Escape the source of danger while the temporary protection lasts.

## Limits

Damage tagged as bypassing invulnerability skips the Totem protection branch. The bundled tag contains **out_of_world** and **generic_kill** damage. It is not a reliable way to survive the void or a kill-style administrative effect.

One activation consumes one Totem; another lethal hit requires another eligible item in hand. The check does not automatically pull replacements from ordinary inventory slots. A Totem also does not teleport you, rebuild destroyed terrain, or establish a safe respawn point.

## Related pages

- [Defensive items](../mechanics/DefensiveItems.md)
- [Shield](Shield.md)
- [Status effects](../effects/Effects.md)
- [Regeneration](../effects/Regeneration.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game blocking, combat, death-protection, decoration, or loot test was run. Item components, enchantments, tags, and game rules can change these values.

- [Totem item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Lethal-hit hand search, consumption, and bypass](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Totem effect sequence](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/DeathProtection.java)
- [Evoker loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/evoker.json)
- [Invulnerability-bypass damage tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json)
