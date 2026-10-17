# Warden

The **Warden** (`minecraft:warden`) is a **blind, anger-driven hostile mob**. It investigates disturbances, sniffs out nearby targets, then roars and fights when sufficiently angry. Plan to avoid it or retreat: a wall can interrupt its approach without stopping its detection or sonic attack. [Active brain][brain] · [Smell targeting][sensor] · [Sonic attack][sonic]

## Obtaining

The checked encounter route is a **summoning-capable Sculk Shrieker** reaching warning level **4**, with difficulty above Peaceful and `doWardenSpawning` enabled. A valid spawn position is still required. This is a triggered spawn, not an ordinary biome monster roll. Follow [Shrieker warnings and summoning](../blocks/SculkShrieker.md#warning-levels-and-summoning) and [why spawning can fail](../blocks/SculkShrieker.md#why-a-fourth-warning-may-not-spawn-a-warden); find eligible Shriekers through [the sculk generation guide](../blocks/Sculk.md#finding-and-collecting-the-family). Ordinary item-placed Shriekers do not retain summoning capability. [Trigger gates][shriek-gates] · [Active spawn caller][shriek-spawn]

The [Warden Spawn Egg](../items/WardenSpawnEgg.md) is separately registered and listed in the ordinary Spawn Eggs category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can therefore provide it in **Creative**, subject to that browser's feature, cursor and capacity checks. Egg use creates the Warden through the spawn-item route and refuses Peaceful; it does not run the Shrieker warning sequence or the triggered emerging setup. This is a separate acquisition/spawning route, not a crafting recipe or natural spawn claim. [Egg registration][egg] · [Category entry][egg-tab] · [Egg use][egg-use] · [Triggered-only emergence][spawn-init]

## Behavior

The registered default Warden has:

- **500 health points (250 hearts)**, a standing size of **0.9 × 2.9 blocks**, and fire immunity
- **Movement-speed attribute 0.3**, with AI movement multipliers **0.5** while strolling, **0.7** while investigating and **1.2** while pursuing an attack target; these are implementation values, not blocks per second
- **Knockback resistance 1.0**, attack-knockback attribute **1.5**, and follow-range attribute **24**; follow range is not its vibration or sonic-attack radius

[Entity registration][registration] · [Active attribute registration][attributes-hook] · [Attribute values][attributes] · [Idle and investigation movement][movement] · [Fight behavior][fight]

### Vibrations, smell and touch

Its **16-block-radius** vibration listener accepts listed game events, including movement, eating, containers, block changes and projectile events. It is registered with the world and ticked by the Warden. A delivered vibration starts a **40-tick** listening cooldown; a Warden that is emerging, digging, dead or has disabled AI does not accept new vibrations through this listener. These are game events, not every sound you can hear. [Listener and reactions][vibrations] · [Listenable events][events] · [Listener registration][listener-hook] · [Server ticking][tick]

The shared vibration rules support [sneaking, Wool dampening and Wool-block occlusion](../blocks/WoolAndCarpet.md#vibrations), with [travel and selection details](../blocks/SculkSensors.md#detection-travel-and-cooldown) explained in the Sensor guide. Sneaking suppresses only the specified movement-related events; it does not silence every action. Those rules affect vibration reception, not smell or physical contact. The Warden does not inherit the Sensor-specific phase or adjacent-chunk delivery requirement. [Shared filtering][vibration-filter] · [Occlusion][occlusion] · [Warden listener][vibrations]

**Blindness does not make hiding or Invisibility reliable.** Its smell sensor selects nearby living entities without a visibility or Invisibility check, preferring an eligible player before other eligible mobs. At the end of a sniff it can investigate that entity's position; if the target is **less than 6 blocks horizontally and less than 20 blocks vertically** away, it also adds anger. Sniffing has a configured **84-tick** duration and a **100–200-tick** cooldown when started; it is not a continuous fixed-radius alarm. [Sensor selection][sensor] · [Nearby-entity collection][nearby] · [Sniff start][sniff-start] · [Sniff result][sniff] · [Duration][durations] · [Distance comparison][distance]

The Warden's target filter excludes Creative/Spectator players, allies, armor stands, other Wardens, invulnerable or dying entities, and entities outside its level or world border. Ordinary animals and other hostile mobs can qualify. Touching it through the collision/push callback can add anger even when movement vibrations are suppressed, with a **20-tick** touch cooldown. [Target filter][targets] · [Contact][touch]

### Anger and target choice

Anger is tracked per suspected entity. Its levels are **Calm below 40**, **Agitated at 40–79**, and **Angry at 80 or above**. Ordinary accepted vibrations attributed to a valid target, close-range sniffing and contact normally add **35**. The damage-response path adds **100** for an eligible attacker; a direct hit or sufficiently close attacker can also set an attack target immediately. Attacking it is not a safe way to distract it. Anger normally decreases by **1 every 20 game ticks while its AI runs**, rather than disappearing when you become quiet. [Thresholds][anger-levels] · [Anger additions][anger-add] · [Damage response][hurt] · [Sniff][sniff] · [Contact][touch] · [Decay][anger-decay] · [Tick cadence][ai-tick]

The suspect ordering prefers angry candidates, then players within the same anger tier, then higher anger. Raising a player's anger to Angry can interrupt an existing non-player target. The ordinary escalation selects a roar target, runs a configured **84-tick** roar that adds another **20 anger**, then assigns the attack target. Do not assume the mob it is currently chasing will keep its attention. [Ordering][anger-sort] · [Player priority][anger-add] · [Roar selection][roar-select] · [Roar][roar] · [Duration][durations]

Thrown-projectile distractions have a cost. For a detected projectile whose owner is **less than 30 blocks** away, the first event without recent-projectile memory adds **10 anger** toward that owner. Another while the **100-tick** memory is present uses the ordinary **35** increase and can redirect investigation to the owner's position. The memory is refreshed on receipt. An occasional distant landing may divert a Warden that is not yet angry; repeatedly throwing from nearby can expose you instead. [Projectile ownership on delivery][delivery] · [Projectile response][vibrations]

## Fighting and escaping

### Melee and shields

Its ordinary attack has base damage **30**. Against a player, before armor, effects or other mitigation, the checked difficulty handling gives **16 points on Easy**, **30 on Normal**, and **45 on Hard**: **8, 15 and 22.5 hearts**. The fight AI uses an **18-tick melee cooldown** and still requires its melee range and target-visibility conditions. These are attack settings, not guaranteed hit intervals. [Attributes][attributes] · [Melee damage caller][melee-damage] · [Melee damage type][melee-type] · [Damage scaling][damage-scaling] · [Player difficulty adjustment][difficulty] · [Fight registration][fight] · [Melee gates][melee]

Armor and applicable protective effects/enchantments can reduce ordinary melee damage. A correctly raised and facing ordinary Shield can block a melee hit, but the Warden disables successful blocking for **5 seconds (100 ticks)**. Do not depend on holding the shield continuously. [Armor and magic mitigation][mitigation] · [Shield definition][shield] · [Blocking and contact][blocking] · [Warden disable duration][shield-disable] · [Player disable callback][player-block] · [Cooldown conversion][block-cooldown]

### Sonic boom

The sonic attack can reach a valid target **less than 15 blocks horizontally and less than 20 blocks vertically** from the Warden. These are separate horizontal and vertical limits, not a 15-block sphere. The attack has **no wall/line-of-sight test** and directly damages its selected target; ordinary cover or a short pillar is not a reliable defense. The firing step checks the target and range again. [Range, firing and direct damage][sonic] · [Distance comparison][distance]

Its base damage **10** becomes **6/10/15 points** against players on **Easy/Normal/Hard**: **3/5/7.5 hearts**. Sonic damage bypasses **armor, Shields and protective enchantments**. **Resistance still applies** in the checked mitigation path, and the attack's push is reduced by the target's knockback resistance. This is different from the Warden's melee attack. [Sonic damage][sonic] · [Sonic scaling][sonic-type] · [Difficulty adjustment][difficulty] · [Armor bypass][bypass-armor] · [Shield bypass inheritance][bypass-shield] · [Enchantments bypass][bypass-enchantments] · [Effect-bypass tag][bypass-effects] · [Resistance-bypass tag][bypass-resistance] · [Mitigation order][mitigation]

Selecting an attack target sets a **200-tick sonic cooldown**; a melee attempt replaces it with **40 ticks**. Once sonic behavior starts, it reserves a configured **60-tick** attack period, waits **34 ticks** before its firing step, and sets another **40-tick** cooldown when it stops. These gates depend on the current target and behavior, so they do not establish a fixed firing schedule. [Target assignment][hurt] · [Melee reset][melee-reset] · [Sonic timing][sonic]

### Practical avoidance

- **Plan the return route before approaching Shriekers.** Reduce avoidable movement and block/container events; use the [Wool and sneaking rules](../blocks/WoolAndCarpet.md#vibrations) without assuming they suppress smell or contact
- **Keep your distance and avoid touching or attacking it.** Standing still nearby can still end in a sniff or collision; repeatedly using projectiles can raise anger toward you
- **Leave room to retreat beyond sonic range.** Breaking line of sight alone does not stop the sonic hit; armor that helps against melee does not mitigate it
- **Stop feeding new disturbances while withdrawing.** Quiet gives anger and the burrow timer a chance to run down, but pursuit, smell, other entities and persistence can keep the encounter active

These tactics follow the detection, attack and timer rules above; they are not a tested escape route, containment design or guarantee of safety.

## Darkness, emerging and burrowing

See [Vision effects](../effects/VisionEffects.md#darkness) for Darkness visibility, the Darkness Pulsing setting, and the HUD-icon distinction.

While its server AI runs, the Warden checks every **120 ticks** to apply **260 ticks of Darkness** to eligible non-allied Survival/Adventure players **less than 20 blocks** away. The helper refreshes only when its effect-duration/amplifier conditions allow. Darkness does not require the Warden to be angry at that player. This aura is distinct from [a Shrieker's wider Darkness response](../blocks/SculkShrieker.md#warning-levels-and-summoning). [AI caller][ai-tick] · [Darkness effect][darkness] · [Player filtering][effect-players] · [Survival/Adventure predicate][survival]

A triggered Warden begins **emerging**, with a configured **134-tick** emergence. Ordinary damage cannot hurt it while emerging or digging unless the damage type bypasses invulnerability; it also ignores explosions in those poses. This is not an opportunity for ordinary free hits. [Spawn initialization][spawn-init] · [Duration][durations] · [Emerging behavior][emerging] · [Damage gate][invulnerability] · [Explosion handling][explosions]

Spawning gives it a **1,200-tick burrow cooldown**, approximately **60 seconds at 20 TPS**. Anger increases, eligible investigations and fighting can refresh the existing cooldown. When the timer is absent, burrowing also requires no roar target, attack target or walk target and suitable footing: ground, water or lava. On land it uses a configured **100-tick** digging behavior before discarding itself; without ground contact, the water/lava branch discards it without that ground-dig sequence. It does not require a Sculk floor. [Initial timer][spawn-init] · [Activity gates and timer refresh][dig-ai] · [Investigation refresh][disturbance] · [Anger refresh][anger-add] · [Dig conditions and removal][dig]

The Warden opts out of ordinary distance-based despawning. A used Name Tag sets persistence, and persistence refreshes its existing burrow cooldown; do not assume waiting one minute removes a named Warden or cancels a dig already underway. Switching to Peaceful discards it even when persistent. Discarding is not normal death and is not a loot-collection route. [Distance persistence][distance-persistence] · [Name Tag][name-tag] · [Persistence ticking][tick] · [Peaceful removal][despawn] · [Death loot path][death]

## Drops

With **mob loot enabled**, ordinary death uses the registered entity loot table and drops **one Sculk Catalyst**. Its pool has no Looting multiplier. A qualifying player-attributed kill has a **base reward of 5 XP**, subject to ordinary XP eligibility; custom equipment or enchantment processing can affect the final reward. The small default reward gives little reason to fight it merely for materials: see [Catalyst acquisition](../blocks/Sculk.md#finding-and-collecting-the-family) for other checked routes. [Loot-table wiring][loot-wiring] · [Catalyst drop][loot] · [Mob-loot gate][loot-gate] · [Base XP assignment][constructor] · [XP calculation][xp] · [Reward processing][xp-process] · [Death/XP eligibility][death]

## Notes

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked active entity/attribute registration, brain and listener wiring, Shrieker/egg spawn paths, targeting and anger, combat callers and protection tags, Darkness, persistence/removal and loot/XP. Timings are game ticks; approximate seconds assume 20 TPS. This page covers ordinary bundled behavior, not every command, data pack, modified attribute, custom item or server setting. No in-game summon, fight, damage, escape, persistence or drop test was run.

Related: [Mobs](Mobs.md) · [Sculk family](../blocks/Sculk.md) · [Sensors](../blocks/SculkSensors.md) · [Shriekers](../blocks/SculkShrieker.md) · [Inventory browser](../mechanics/InventoryBrowser.md)

[brain]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/WardenAi.java#L95-L185
[sensor]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/sensing/WardenEntitySensor.java#L16-L40
[sonic]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/SonicBoom.java#L19-L95
[shriek-gates]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L100-L130
[shriek-spawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L140-L167
[egg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2000-L2000
[egg-tab]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2110-L2120
[egg-use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L101
[spawn-init]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L467-L480
[registration]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/EntityType.java#L1483-L1492
[attributes-hook]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L261-L270
[attributes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L175-L183
[movement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/WardenAi.java#L135-L157
[fight]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/WardenAi.java#L169-L185
[vibrations]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L558-L627
[events]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/game_event/warden_can_listen.json#L1-L59
[listener-hook]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L373-L378
[tick]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L238-L245
[vibration-filter]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L354-L403
[occlusion]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L200-L255
[nearby]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/sensing/NearestLivingEntitySensor.java#L15-L25
[sniff-start]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/TryToSniff.java#L12-L33
[sniff]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/Sniffing.java#L13-L63
[durations]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/WardenAi.java#L45-L55
[distance]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L432-L441
[targets]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L380-L391
[touch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L516-L525
[anger-levels]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/AngerLevel.java#L8-L49
[anger-add]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L420-L453
[hurt]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L482-L503
[anger-decay]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/AngerManagement.java#L75-L118
[ai-tick]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L276-L293
[anger-sort]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/AngerManagement.java#L163-L200
[roar-select]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/SetRoarTarget.java#L11-L30
[roar]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/Roar.java#L17-L65
[delivery]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L319-L334
[melee-damage]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[melee-type]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/damage_type/mob_attack.json#L1-L5
[damage-scaling]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/damagesource/DamageSource.java#L95-L101
[difficulty]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L721-L750
[melee]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/MeleeAttack.java#L18-L45
[mitigation]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1780-L1827
[shield]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2256-L2277
[blocking]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1273-L1307
[shield-disable]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L165-L168
[player-block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L752-L761
[block-cooldown]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/BlocksAttacks.java#L87-L126
[sonic-type]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/damage_type/sonic_boom.json#L1-L5
[bypass-armor]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[bypass-shield]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json#L1-L15
[bypass-enchantments]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json#L1-L5
[bypass-effects]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json#L1-L5
[bypass-resistance]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json#L1-L6
[melee-reset]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L216-L222
[darkness]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L393-L396
[effect-players]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L47-L62
[survival]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameType.java#L90-L92
[emerging]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/Emerging.java#L12-L40
[invulnerability]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L151-L158
[explosions]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L358-L361
[dig-ai]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/WardenAi.java#L85-L132
[disturbance]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/WardenAi.java#L191-L215
[dig]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/behavior/warden/Digging.java#L13-L40
[distance-persistence]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L462-L465
[name-tag]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L33
[despawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Mob.java#L602-L630
[death]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[loot-wiring]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/EntityType.java#L2055-L2066
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/entities/warden.json#L1-L16
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[constructor]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L113-L126
[xp]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Mob.java#L289-L307
[xp-process]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L591
