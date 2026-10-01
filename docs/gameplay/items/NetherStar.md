# Nether Star

The **Nether Star** (`minecraft:nether_star`) is the [Wither](../mobs/Wither.md) reward used to craft a [Beacon](Beacon.md). It stacks to **64**, has **Rare** rarity, and displays an enchantment-style glint through an explicit visual component even without enchantments. [Registration][item] · [Default stack components][components] · [Item component inheritance][item-defaults] · [Glint handling][glint]

## Obtaining

Defeating a Wither normally produces **one Nether Star when mob loot is enabled**. The boss creates this item in its custom death-drop method; its bundled entity loot table contains no item pools. The fixed star creation has no player-kill condition and **Looting does not multiply it**. Player credit matters separately for experience. [Wither drop code][wither] · [Empty loot table][loot] · [Death-drop dispatch][death] · [Mob-loot rule][monster]

Use the [Wither guide](../mobs/Wither.md) for summoning materials, charge timing, and combat hazards. Removing the boss by switching to Peaceful uses a discard path rather than its normal death rewards. This is a verified acquisition route, not a survey of every custom reward or data-pack source. [Wither removal][wither]

## Collecting the drop safely

The **fresh star dropped by the Wither** receives an extended item-entity lifetime. Its age begins at **−6,000**, and ordinary despawning happens at age **6,000**, giving **12,000 ticking game ticks**, about **10 minutes at 20 TPS**. This is a ticking-entity timer, not a promise of ten wall-clock minutes under every server condition. [Extended lifetime and age handling][item-entity] · [Boss applying the lifetime][wither]

That extension belongs to the newly created dropped entity, not to every Nether Star stack forever. A normal new item entity starts at age 0, with the usual **6,000-tick** lifetime; do not assume a star you later drop from your inventory retains the boss drop's extended timer. [Default and extended ages][item-entity]

Dropped stars resist damage in the **explosion damage tag**, including ordinary explosions and fireworks. The component does **not** provide general invulnerability or fire/lava resistance. Collect the star before another hazard destroys it, and keep it clear of lava and fire. [Explosion-resistant component][item] · [Resistance matching][resistance] · [Item damage handling][item-entity] · [Explosion damage tag][explosion-tag] · [Fire damage tag][fire-tag]

## Crafting a Beacon

At a crafting table, combine **1 Nether Star**, **5 [Glass](Glass.md)**, and **3 [Obsidian](Obsidian.md)**:

```text
Glass     Glass        Glass
Glass     Nether Star  Glass
Obsidian  Obsidian      Obsidian
```

This shaped recipe produces **1 [Beacon](Beacon.md)** and consumes one star. It specifically uses glass blocks, not glass panes. This page verifies the crafting recipe; Beacon activation, mineral pyramids, and effect selection are separate placed-block mechanics. [Beacon recipe][recipe]

Related: [Wither](../mobs/Wither.md) · [Beacon](Beacon.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game drop, despawn, damage-resistance, or crafting test was run. Game rules, item components, entity age, merging, and data packs can alter particular items or outcomes.

[item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2098-L2104
[components]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[item-defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java#L354-L357
[glint]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L947-L950
[wither]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/wither.json
[death]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[item-entity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/item/ItemEntity.java
[resistance]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/DamageResistant.java
[explosion-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/is_explosion.json
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/beacon.json
