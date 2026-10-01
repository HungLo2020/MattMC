# Shields and death protection

A Shield and a Totem of Undying protect against different stages of a dangerous encounter. This guide covers those two verified item systems, not every armor, damage, or enchantment rule.

## Choose and prepare the item

| Item | Required state | What it does | Main limit |
| --- | --- | --- | --- |
| [Shield](../items/Shield.md) | Actively used, past its 5-tick delay, facing an eligible damage source | Reduces the matching incoming damage | Bypass types, piercing arrows, rear hits, durability, and disable cooldowns |
| [Totem of Undying](../items/TotemOfUndying.md) | Held in either hand when qualifying lethal damage occurs | Consumes itself to prevent that death and apply recovery effects | One use; excluded damage types; no inventory auto-refill |

An offhand is one slot. If you replace a Shield with a Totem, plan how you will avoid or escape ordinary attacks rather than assuming the Totem is a reusable blocking item. If the Totem is stored away while the Shield is held, its protection is not active from that storage slot.

## Before a fight

- Raise a Shield early enough for its use delay and keep facing the source
- Treat axe attackers as a reason to reposition: the current ordinary axe path can disable blocking for 100 ticks
- Do not use Shield coverage as a substitute for avoiding Lava, falls, or the other shield-bypassing hazards
- Keep a Totem in hand before a potentially lethal hit; selecting it afterward cannot undo damage already processed
- After a Totem activates, move to safety and check your health and effects rather than expecting full health or permanent immunity

These rules describe source behavior, not a guarantee of surviving a specific combat setup. Latency, server state, data packs, item components, and separate attack effects can matter.

## Related pages

- [Shield details](../items/Shield.md)
- [Totem effects and exclusions](../items/TotemOfUndying.md)
- [Hunger and healing](Hunger.md)
- [Status effects](../effects/Effects.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game blocking, combat, death-protection, decoration, or loot test was run. Item components, enchantments, tags, and game rules can change these values.

- [Blocking and death-protection dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Player blocking-disable behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Shield and Totem components](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Totem effects](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/DeathProtection.java)
