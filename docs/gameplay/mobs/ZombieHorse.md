# Zombie Horse

A **Zombie Horse** is a passive undead mount with **15 health points (7.5 hearts)**. A normal spawn egg creates an untamed animal whose ordinary interaction does not let you begin ride-to-tame attempts. An operator can create one already tame; then it accepts a Saddle and the shared Horse controls. It cannot breed or wear ordinary Horse Armor. [Registration][zombie-reg] · [Health][zombie-stats] · [Attribute registration][zombie-attributes] · [Species interaction][zombie-interact] · [Tame data][horse-save] · [Saddle gate][horse-saddle] · [Armor tag][armor]

## Obtaining

### Eggs and natural-spawn limits

The **[Zombie Horse Spawn Egg](../items/ZombieHorseSpawnEgg.md)** is the ordinary item route. It is available through the [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. Read the [spawn-egg guide](../items/SpawnEggs.md) for placement, supported babies and spawner use. Egg placement initializes a creature directly; item availability does not make it tame. [Egg registration][zombie-egg] · [Egg caller][egg-create] · [Initialization][type-create] · [Default tame flag][horse-flags]

There is **no Zombie Horse entry in any of the 68 bundled biome spawn definitions** reviewed here. Its registered placement predicate is not an active biome population by itself. The thunderstorm horse event specifically creates a **[Skeleton Horse](SkeletonHorse.md#lightning-traps)**, not a Zombie Horse. No ordinary natural or conversion route for Zombie Horses was found in the active source paths reviewed for this guide. [Placement registration][placement-zombie] · [Species predicate][zombie-stats] · [Natural candidate lookup][natural-candidates] · [Weather event's actual species][weather-trap]

### Creating a rideable adult

An **unmodified egg-created Zombie Horse is untamed**. Its species interaction returns without mounting or feeding until the tame flag is already set. Repeated empty-hand use, food or a Saddle does not unlock the normal Horse taming loop. The shared runaway/taming goal exists, but ordinary interaction does not put the player on this untamed species to start it. [Species gate][zombie-interact] · [Default tame state][horse-flags] · [Saddle eligibility][horse-saddle] · [Installed goals][horse-goals] · [Taming goal's mounted requirement][tame-goal]

A player with **permission level 2** can use:

```mcfunction
/summon minecraft:zombie_horse ~ ~ ~ {Tame:1b,Age:0}
```

This creates an already-tame adult through supported entity data. Add a Saddle separately. Command access is separate from the ordinary browser's item insertion; the example was checked against source, not executed in a game. [Registered command][summon-register] · [Syntax and permission][summon-syntax] · [NBT creation path][summon-create] · [Tame data reader][horse-save] · [Age data][age-save]

## Behavior

### Riding and equipment

For a **tame, unoccupied adult**, use a Saddle to fill its empty saddle slot, or Sneak/Crouch-interact to open the equipment inventory. Mount with an empty hand. A Saddle is required for player steering and the charged jump; follow [Horse riding controls](Horse.md#riding-and-equipment) for input and key bindings. The normal capacity is **one rider**. [Species interaction][zombie-interact] · [Mount/inventory interaction][horse-use] · [Saddle gate][horse-saddle] · [Allowed targets][saddles] · [Equipment component][equippable] · [Held-item dispatch][equip-dispatch] · [Equip action][equip-action] · [Control][horse-controller] · [Jump][horse-jump] · [Seat capacity][capacity]

Zombie Horses do **not** accept the bundled Horse Armor items: their allowed-entity tag contains only ordinary Horse. Their shared inventory also has **zero cargo columns**, so attaching a Chest is not a pack-animal route. Use [Donkeys](Donkey.md), [Mules](Mule.md) or [Llamas](Llama.md) for animal cargo. A Lead can move or secure a Zombie Horse, but held horse food does not lure it because this species omits that temptation goal. [Armor tag][armor] · [Cargo size][horse-cargo] · [Lead eligibility][lead] · [Shared Lead interaction][lead-action] · [Shared optional goals][horse-goals] · [Species goal override][zombie-interact]

### Water and daylight

A Zombie Horse is in the undead group and **can breathe underwater**, but it also appears in the **underwater-dismount tag**. When the rider's **eyes are submerged in Water outside a Bubble Column**, the passenger tick dismounts that rider. This differs from a Skeleton Horse, which is absent from that list. The Zombie Horse also omits the ordinary horse floating goal; choose a [boat](../mechanics/Transport.md) for routine surface travel. [Zombie membership][zombies] · [Undead expansion][undead] · [Breathing tag][underwater] · [Breathing lookup][breathing] · [Dismount list][dismounts] · [Tag lookup][dismount-tag-call] · [Actual rider check][drowning] · [Species goal override][zombie-interact]

The horse itself has no Zombie-style daylight-burning callback and does not have a hostile attack goal in its checked hierarchy. Its undead name does not make it a Zombie enemy. It still takes ordinary environmental and combat damage; falls can hurt both mount and rider. [Species class][zombie-class] · [Inherited goals][horse-goals] · [Inherited living tick][horse-regen] · [Sunburn predicate][sun-helper] · [Fall propagation][horse-fall]

### Healing, food and breeding

Allow time for the inherited random recovery: while alive, it has a **1-in-900 chance per eligible server AI tick to heal 1 point**, capped at its maximum. **Instant Damage heals it; Instant Health hurts it.** Poison and Regeneration are rejected by the undead effect tag. For throwable potions, see [Brewing](../brewing/Brewing.md#splash-and-lingering-forms); a Harming splash that helps this horse can hurt nearby players or living pets. [Recovery][horse-regen] · [Health cap][heal-cap] · [Inversion tag][inverted] · [Inversion lookup][potion-inversion] · [Registered effects][potion-registration] · [Instant effect][instant-effect] · [Splash caller][splash-caller] · [Rejected-effect tag][effect-tag] · [Effect check][effect-immunity]

The normal **Horse food-healing table does not run on an unoccupied Zombie Horse adult**. Once tame, its adult interaction generally mounts the player rather than feeding; while untamed, the species gate rejects that ordinary interaction. It also inherits `canMate = false`, so love particles from an occupied-animal food interaction do not produce foals. [Species gate][zombie-interact] · [Actual adult interaction][horse-use] · [Ordinary Horse's separate feeding caller][horse-real-food-caller] · [Generic occupied-animal food path][animal-feed] · [Mating rejection][horse-save]

The matching-egg helper **can** create a baby Zombie Horse without normal breeding. The baby starts untamed and matures after **24,000 ticking game ticks**, about **20 minutes** at normal speed. If a baby is explicitly made tame through entity data, its interaction can reach the generic food-growth path: tagged Horse food advances roughly **10% of remaining growth**, rounded to seconds. This is not the adult Horse table's fixed growth amount for each food. [Mob egg dispatch][egg-baby-caller] · [Egg helper][egg-baby] · [Species offspring factory][zombie-interact] · [Default state][horse-flags] · [Age progression][age] · [Baby interaction branch][horse-use] · [Generic growth feeding][animal-feed] · [Food predicate][horse-food-test] · [Food tag][food]

## Notes

- Entity ID: `minecraft:zombie_horse`; registered adult size **1.3964844×1.6 blocks** [Registration][zombie-reg]
- It does not use ordinary distance despawning. Tame state is saved, but it can still wander; park it with a Lead or enclosure [Animal retention][animal-persistence] · [Saved tame state][horse-save] · [Wandering goal][horse-goals]
- Normal spawn finalization varies jump strength while retaining the species' fixed base health and movement speed. No jump-height or travel-speed trial was run [Species attributes][zombie-stats] · [Finalization][horse-finalize] · [Jump generator][horse-jump-random]
- The adult base loot table gives **0–2 Rotten Flesh**, with a Looting count increase, when ordinary mob loot is enabled; babies skip it [Table][zombie-loot] · [Loot gate][loot-gate] · [Death dispatch][death] · [Table loading][loot-load]
- Recover the Saddle through its inventory or the eligible Shears action described on [Saddle](../items/Saddle.md#removing-a-saddle). Normally player-equipped gear is marked for guaranteed equipment drops, subject to the usual loot and equipment-prevention gates [Marking][equip-mark] · [Equip action][equip-action] · [Inventory insertion marking][menu-mark] · [Equipment death drops][equipment-drop] · [Loot gate][loot-gate]

## Related pages

- [Skeleton Horse](SkeletonHorse.md): lightning traps and underwater riding
- [Horse](Horse.md): shared riding controls
- [Saddle](../items/Saddle.md)
- [Spawn eggs](../items/SpawnEggs.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. Traced active creation and interaction callers, loaded tags, horse/animal inheritance, entity data, equipment and loot. The biome inventory covers the exact 68 active `data/<namespace>/worldgen/biome/*.json` definitions, excluding tags and legacy source trees. No game, command, riding, feeding, healing, trap, underwater or drop test was run. Timings assume a ticking server at 20 ticks per second; data packs, custom entity data and server settings can alter behavior.

[zombie-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1562-L1565
[zombie-stats]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/ZombieHorse.java#L36-L51
[zombie-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L274-L274
[zombie-interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/ZombieHorse.java#L68-L81
[horse-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L779-L802
[horse-saddle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L221-L235
[armor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json#L1-L5
[zombie-egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L2010-L2012
[egg-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L103
[type-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1731-L1775
[horse-flags]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L150-L185
[placement-zombie]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L159-L159
[natural-candidates]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L329
[weather-trap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L519-L549
[horse-goals]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L129-L148
[tame-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/RunAroundLikeCrazyGoal.java#L24-L71
[summon-register]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/commands/Commands.java#L237-L237
[summon-syntax]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L76
[summon-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/SummonCommand.java#L79-L107
[age-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[horse-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L635-L659
[saddles]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json#L1-L14
[equippable]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L99-L108
[equip-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L591-L604
[equip-action]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[horse-controller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L947-L951
[horse-jump]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L860-L880
[capacity]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2386-L2388
[horse-cargo]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L1033-L1035
[lead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[lead-action]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2162-L2174
[zombies]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/zombies.json#L1-L12
[undead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[underwater]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json#L1-L19
[breathing]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L386
[dismounts]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/dismounts_underwater.json#L1-L17
[dismount-tag-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2533-L2535
[drowning]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L440
[zombie-class]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/ZombieHorse.java#L26-L87
[horse-regen]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L524-L535
[sun-helper]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1326-L1336
[horse-fall]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L269-L283
[heal-cap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[inverted]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/inverted_healing_and_harm.json#L1-L5
[potion-inversion]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1027-L1029
[potion-registration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/effect/MobEffects.java#L50-L51
[instant-effect]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/effect/HealOrHarmMobEffect.java#L16-L40
[splash-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L68
[effect-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/ignores_poison_and_regen.json#L1-L5
[effect-immunity]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1012
[horse-real-food-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java#L157-L177
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L174
[egg-baby-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L158-L181
[age]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[horse-food-test]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L502-L505
[food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/horse_food.json#L1-L12
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[horse-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L1003-L1016
[horse-jump-random]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L928-L930
[zombie-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/zombie_horse.json#L1-L36
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L568
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[equip-mark]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L506-L518
[menu-mark]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L786-L799
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
