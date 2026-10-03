# Ravager

The **Ravager** (`minecraft:ravager`) is a large hostile raid beast with **100 health points (50 hearts)**. It attacks players, adult villagers and Iron Golems, and later raid waves can give it an illager rider. Treat it as a close-range threat that needs room to avoid, especially after a blocked attack. [Registration][ravager-registry] · [Health and targets][ravager-ai] · [Attribute registration][attributes]

## Obtaining

The verified encounter route is the [raid wave controller](../mechanics/Raid.md#waves-and-difficulty): its base composition includes one Ravager on wave 3, one on wave 5 and two on wave 7, subject to how many waves that difficulty uses. Bonus-wave rules can add more. Wave 5 Ravagers receive Pillager riders; from wave 7 onward the first receives an Evoker and later ones receive Vindicators. The rider and mount are separate raid members. [Base composition][raid-composition] · [Bonus additions][raid-counts] · [Mount/rider creation][raid-spawn]

The bundled biome spawn lists reviewed for this guide do not select Ravagers. Its registered ground-spawn predicate alone is not proof of ordinary biome spawning, and the [Pillager Outpost](../structures/PillagerOutpost.md) monster override selects Pillagers. [Outpost selection][outpost]

The listed [Ravager Spawn Egg](../items/RavagerSpawnEgg.md) is also available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. This creates a hostile mob and is separate from starting a raid or finding natural wildlife. [Ordinary egg listing][eggs-list] · [Browser assembly][browser-list] · [Client request][browser-client] · [Server insertion][browser-server] · [Egg use][egg-use]

## Behavior

A Ravager's base melee attack attribute is **12 damage points**, before the target's defense and applicable damage scaling. Its registered size is 1.95 blocks wide by 2.2 high, with a one-block step height and high knockback resistance. Avoid relying on ordinary knockback to keep it away from villagers. See [Combat](../mechanics/Combat.md) for shared defense rules rather than treating 12 as guaranteed health loss on every hit. [Attributes and goals][ravager-ai] · [Registered size][ravager-registry]

### Blocking, stun and roar

A successfully blocked non-projectile hit calls the attacker's blocking response. When a Ravager is not already roaring, that response has a **50% chance to stun it for 40 ticks**; otherwise it applies strong knockback to the blocker. Merely holding a Shield is not enough: the active blocking conditions must succeed. [Blocking caller][block-caller] · [Callback dispatch][block-dispatch] · [Ravager response][ravager-roar]

When the stun ends, it starts a 20-tick roar sequence. The damaging pulse occurs when that counter reaches 10. That leaves roughly half a second after the roar begins at 20 TPS, so use the stun to create distance rather than standing beside it waiting for another melee opening. [Stun-to-roar timing][ravager-collision]

The pulse checks living entities in the Ravager's bounding box expanded by **four blocks**, applies a base 6-point mob attack to non-illagers and applies knockback through the server/client paths. Other Ravagers are excluded. The checked pulse has **no line-of-sight test**, so a nearby wall is not a verified defense against it. This box is not an exact four-block circular radius from the mob's center. [Pulse and knockback][ravager-roar] · [Target filters][ravager-roar-target]

### Obstacles and raid persistence

With `mobGriefing` enabled, a horizontally colliding Ravager destroys nearby **LeavesBlock** blocks; if it destroys none and is grounded, it jumps. A leaf hedge is therefore a poor barrier. Disabling that rule does not disable its ordinary attacks or the damaging roar. [Leaf breaking and jump][ravager-collision] · [Roar rule use][ravager-roar]

The Ravager cannot be a raid captain and does not receive an additional buff from its own raid-buff method. Its strength comes from its base attributes, wave placement and possible rider. Raid membership adds persistence, so walking away is not proof that an assigned Ravager has disappeared. [Leader/buff overrides][ravager-leader] · [Raid persistence][raider-save]

## Drops

The bundled entity loot table contains **one Saddle**, with no player-kill condition or Looting multiplier in that table. Normal mob loot must still be enabled. Ravagers are absent from the Saddle's allowed-entity tag. This guide verifies the illager rider path, not a player riding interaction. [Saddle loot][ravager-loot] · [Mob-loot gate][light-rule] · [Runtime loot lookup][loot-dispatch] · [Saddle restriction][saddle-component] · [Allowed entities][saddle-entities]

Its base experience reward is 20, subject to the usual experience-drop eligibility. Follow [Experience](../mechanics/Experience.md) for collecting and using orbs. [Base reward][ravager-ai] · [Experience dispatch][loot-dispatch]

## Notes

The raid's shared progress bar tracks the group's health. This page does not claim a separate Ravager boss bar or an independent summoning/progression system. [Raid health bar][raid-health]

Related: [Raid](../mechanics/Raid.md) · [Pillager](Pillager.md) · [Saddle](../items/Saddle.md) · [Defensive items](../mechanics/DefensiveItems.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. Followed registered attributes, active raid spawning/riders, the actual blocking callback, stun/roar timing, obstacle behavior and runtime loot. No combat, shield-stun, escape, wall-defense, raid, saddle-drop or spawn-egg gameplay test was performed.

[ravager-registry]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L1125-L1132
[ravager-ai]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Ravager.java#L67-L106
[attributes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L211-L224
[raid-composition]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[raid-counts]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L716-L777
[raid-spawn]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L565
[outpost]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json
[eggs-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2061-L2072
[browser-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
[egg-use]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L103
[block-caller]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1275-L1298
[block-dispatch]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1341-L1346
[ravager-roar]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Ravager.java#L196-L251
[ravager-collision]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Ravager.java#L134-L184
[ravager-roar-target]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Ravager.java#L45-L51
[ravager-leader]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Ravager.java#L326-L333
[raider-save]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L182-L243
[ravager-loot]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/loot_table/entities/ravager.json
[light-rule]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Monster.java#L115-L132
[loot-dispatch]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1525
[saddle-component]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L99-L108
[saddle-entities]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json
[raid-health]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L568-L605
