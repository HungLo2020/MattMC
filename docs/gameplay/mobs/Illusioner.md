# Illusioner

The **Illusioner** (`minecraft:illusioner`) is an aggressive bow-wielding spellcaster with **32 health points (16 hearts)**. Its spells grant itself Invisibility and can inflict [Blindness](../effects/VisionEffects.md#blindness) on a target. Prepare for it in a deliberately created encounter; the checked built-in acquisition routes do not make it an ordinary mansion or raid-wave resident. [Entity binding][entity] · [Health and installed goals][goals] · [Attribute binding][attributes]

## Obtaining

There is **no dedicated Illusioner spawn egg** in the current item registry or ordinary inventory-browser egg list. The reviewed biome spawn tables, structure definitions and bundled structure templates do not select it. Its registered spawn-placement rule is a check for a spawning system to use, not a rule that adds it to a biome. Commands, custom data-driven content or another system must explicitly create `minecraft:illusioner`. [Item registry][items] · [Category list][categories] · [Bundled data][data] · [Natural selection][natural-selection] · [Placement registration][placement]

With command permission level 2, outside Peaceful, use:

```mcfunction
/summon minecraft:illusioner ~ ~ ~
```

This plain command runs spawn initialization, which supplies its **Bow** and enables its inherited ability to join a raid. **Adding an NBT argument, even `{}`, takes a different command branch that skips that initialization.** A custom-NBT summon therefore needs its equipment and other intended state specified separately; do not assume it receives the plain command's bow or raid eligibility. [Command arguments and permission][summon-args] · [Creation and initialization][summon-create] · [Bow setup][bow-setup] · [Raid eligibility setup][raid-setup]

### Mansions, patrols and raids

- [Woodland Mansion](../structures/WoodlandMansion.md) Mage markers create **Evokers**; Warrior markers create **Vindicators**. Neither creates an Illusioner. [Marker handler][mansion]
- The normal patrol spawner creates **Pillagers**. Inheriting patrol behavior does not put an Illusioner into those generated patrols. [Patrol creation][patrol]
- Built-in [raid waves](../mechanics/Raid.md#waves-and-difficulty) and Ravager riders do **not** spawn Illusioners. However, an already-created, eligible Illusioner can join a nearby raid through the shared raider checks. Its raid-buff callback adds no equipment upgrade. [Wave types][raid-types] · [Wave and rider creation][raid-spawn] · [Existing-raider join][raid-join] · [Eligibility check][raid-check] · [Empty buff callback][raid-buff]

## Behavior

Illusioners target eligible players, adult villagers, Wandering Traders and Iron Golems. They retaliate against qualifying attackers other than Raiders, and can alert nearby Illusioners without a target. They also have a goal to avoid Creakings within eight blocks. The shared illager check excludes baby villagers from attack eligibility. [Installed targets and avoidance][goals] · [Retaliation and same-class alerts][retaliation] · [Baby-villager exclusion][illager] · [Wandering Trader class][trader-class]

### Bow attacks

The ranged goal requires a held Bow. It approaches its target and can strafe after maintaining sight inside its **15-block combat radius**; that radius is a movement decision, not the maximum distance an arrow can travel. The goal draws for at least **20 ticks**, releases only with line of sight, then sets a **20-tick** wait before starting another draw. Casting, lost sight and movement can delay shots, so this is not one arrow every second. [Goal configuration][goals] · [Bow requirement][bow-goal] · [Movement and shooting][bow-tick]

Normal shots use ordinary Arrows when no supported ammunition is held. Higher difficulty reduces launch spread: the configured inaccuracy is **10 on Easy, 6 on Normal and 2 on Hard**. Arrow damage also depends on its difficulty-based random base damage, speed at impact and applicable defenses; it is not a single fixed damage number. [Ammunition fallback][ammunition] · [Shot creation][shoot] · [Mob arrow setup][arrow-setup] · [Base damage][arrow-base] · [Impact damage][arrow-hit]

### Invisibility and Blindness

Times here use **20 game ticks per second**. Both spells require a living target, no spell already being cast, and their own cooldown to have elapsed. Their shared casting goal stops navigation and tracks the target. [Spell eligibility and execution][spell-use] · [Casting movement][spell-move]

| Spell | Effect and additional condition | Cooldown set when casting starts |
| --- | --- | --- |
| Invisibility | **Invisibility I for 1,200 ticks / 60 seconds** on the Illusioner; it must not already have Invisibility | **340 ticks / 17 seconds** |
| Blindness | **Blindness I for 400 ticks / 20 seconds** on the target; effective local difficulty must be **greater than 2**, and the target must differ from the spell's last remembered target | **180 ticks / 9 seconds** |

[Invisibility spell][invisibility] · [Blindness spell][blindness] · [Local difficulty comparison][difficulty]

These cooldowns do not promise a spell every 17 or 9 seconds. Existing Invisibility blocks that spell, while the Blindness goal remembers its target when a cast starts. Waiting out the cooldown on that same target is not sufficient for another cast. Under the checked difficulty calculation, Easy cannot reach the Blindness threshold, Normal can qualify, and Hard exceeds it. “Hard only” is not the checked rule. Use [Vision effects](../effects/VisionEffects.md#blindness-sources) for the shared duration/source comparison and [clearing effects](../effects/VisionEffects.md#brewing-delivery-and-clearing). [Remembered target and cooldown][blindness] · [Local difficulty calculation][difficulty]

For a planned encounter, keep an escape route and solid cover against arrows, and bring [Milk](../items/MilkBucket.md) if you need to clear Blindness. Blindness restricts ordinary sprinting and falling melee criticals, so do not base a retreat on sprinting after the spell lands. Cover interrupts the bow's release check, but neither installed spell adds its own line-of-sight check once its target is retained. These precautions follow the source; they are not a tested arena layout. [Bow sight requirement][bow-tick] · [Spell gates][spell-use] · [Blindness action restrictions](../effects/VisionEffects.md#blindness)

## Keeping an Illusioner

A plain summon does **not** automatically make it persistent. Away from a raid and without another persistence condition, it remains subject to hostile-mob distance/idle despawning. A successfully applied, renamed [Name Tag](../items/NameTag.md) sets persistence; joining a raid also provides special persistence while that raid assignment remains. Patrol state can affect the distance checks. [Default persistence][mob-defaults] · [Despawn checks][despawn] · [Patrol distance rule][patrol-distance] · [Name Tag][name-tag] · [Raid persistence][raid-persistence] · [Saved raid assignment][raid-save]

**Peaceful still removes it, including a named or raid-assigned Illusioner.** The Peaceful check precedes the persistence checks, and the summon command rejects this entity in Peaceful. [Registration][entity] · [Removal order][despawn] · [Command rejection][summon-create]

## Drops

The default Illusioner loot table is **empty**. It supplies no Emeralds, Totem of Undying, Arrows or Ominous Bottle. Equipment drops are separate from that table. A banner-wearing Illusioner does not inherit the [Pillager captain's bottle](Pillager.md#captains-and-ominous-bottles) loot pool. [Default table binding][loot-binding] · [Illusioner table][loot] · [Pillager-only pool][pillager-loot] · [Death dispatch][death]

- **Bow:** its normally initialized Bow has an **8.5% base equipment-drop chance** when recent player-kill credit applies. The ordinary Looting effect adds one percentage point per level for an eligible player attacker, giving **11.5% with Looting III**. The drop receives random durability damage. [Default chance][drop-chance] · [Equipment drop][equipment-drop] · [Looting effect][looting]
- **Ominous Banner, if equipped by captain initialization:** the inherited initialization has a **6% captain roll** for spawn reasons other than Patrol, Event and Structure, including the plain command above. It equips a banner with preserved/guaranteed equipment-drop settings; that banner does not require recent player-kill credit. The mob-loot rule and ordinary equipment-drop exclusions still apply. [Captain and banner setup][captain] · [Preserved equipment][preserved] · [Drop conditions][equipment-drop]
- **Experience:** the base reward is **5 XP**, plus **1–3 per eligible item still equipped** when XP is calculated. In the ordinary bow-only setup, the source order yields **5 XP if the bow was dropped first**, or **6–8 if it remains equipped**; the preserved captain banner adds no XP. This follows the checked death order, not a measured drop sample. XP still requires its player-credit and mob-loot gates; modified equipment or enchantments can change the result. [Base reward][xp-base] · [Equipment bonus][xp-equipment] · [Drop order and XP gates][death] · [Reward modifiers][xp-modifiers]

Ordinary equipment and XP drops depend on `doMobLoot`. A player hit or qualifying tamed-Wolf hit supplies **100 ticks** of player-kill credit. Separately, the shared [Wither Rose](../items/WitherRose.md#obtaining) death route can plant or drop a rose when the credited killer is a Wither; that is not an Illusioner loot-table reward. [Loot rule][loot-rule] · [Kill credit][kill-credit] · [Wither death route][wither-rose]

## Notes

- This base MattMC/Minecraft entity is registered as `minecraft:illusioner`, uses the `Illusioner` class and `MobCategory.MONSTER`, and has a registered size of **0.6 × 1.95 blocks**. [Registration][entity]
- Its base attributes are **0.5 movement speed** and **18 follow range**, alongside 32 maximum health. Movement goals use separate speed multipliers, and ordinary spawn initialization can modify follow range; these values are not guaranteed travel speed or a fixed detection radius. [Attributes][goals] · [Spawn modifier][spawn-modifier]
- The registered class installs these goals on the server and the active living-mob AI path runs them. Spell status behavior does not establish a particular on-screen appearance or number of rendered illusion copies. [Goal installation][goal-install] · [Living AI caller][living-ai] · [Goal scheduler][ai-call]

Related: [Vision effects](../effects/VisionEffects.md) · [Evoker](Evoker.md) · [Vindicator](Vindicator.md) · [Raids](../mechanics/Raid.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Checked the entity/attribute bindings, active spawning and AI callers, complete default loot/equipment/experience path, persistence, source spawn selections and bundled data. No game, browser, spawning, raid, spell, combat, timing, persistence or drop test was run. This guide makes no claim about current rendered illusion-copy appearance. Custom data, commands and server settings can change the encounter.

[entity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L771-L779
[goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L60-L80
[attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L186
[items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java
[categories]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[data]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data
[natural-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L168
[summon-args]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L76
[summon-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/SummonCommand.java#L79-L108
[bow-setup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L82-L88
[raid-setup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raider.java#L262-L269
[mansion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1215-L1260
[patrol]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/PatrolSpawner.java#L71-L93
[raid-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[raid-spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L565
[raid-join]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raider.java#L86-L108
[raid-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raids.java#L105-L107
[raid-buff]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L168-L170
[retaliation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L33-L115
[illager]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/AbstractIllager.java#L26-L37
[trader-class]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L49
[bow-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java#L35-L46
[bow-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java#L69-L137
[ammunition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L139-L147
[shoot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L172-L186
[arrow-setup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ProjectileUtil.java#L160-L164
[arrow-base]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L673-L675
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L376-L388
[spell-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/SpellcasterIllager.java#L161-L207
[spell-move]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/SpellcasterIllager.java#L130-L158
[invisibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L248-L278
[blindness]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L197-L245
[difficulty]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/DifficultyInstance.java#L31-L60
[mob-defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L130-L152
[despawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L598-L631
[patrol-distance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/PatrollingMonster.java#L101-L104
[name-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L29
[raid-persistence]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raider.java#L235-L243
[loot-binding]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/illusioner.json#L1-L4
[pillager-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/pillager.json#L1-L43
[death]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[drop-chance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L14
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[looting]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/looting.json#L6-L28
[captain]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/PatrollingMonster.java#L60-L87
[preserved]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/DropChances.java#L42-L47
[xp-base]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L49-L57
[xp-equipment]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L289-L306
[xp-modifiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L594
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[kill-credit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1338
[wither-rose]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1430-L1463
[spawn-modifier]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1010-L1023
[goal-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L141-L153
[living-ai]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2774-L2785
[ai-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L634-L661
[raid-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raider.java#L182-L207
