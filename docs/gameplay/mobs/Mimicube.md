# Mimicube

The **Mimicube** is an aggressive, hopping mob with **30 health points (15 hearts)**. It copies the headgear and held items of a recent attacker. Keep food out of your hands while fighting it: copied food can heal the Mimicube. Its copied weapons and armor have important MattMC limitations described below. [Attributes and goals][basics] · [Active attributes][attributes] · [Copying][copying] · [Eating][eating]

## Obtaining

Use the [Mimicube Spawn Egg](../items/MimicubeSpawnEgg.md) from the ordinary Spawn Eggs category in Creative for deliberate placement. The egg guide and [shared spawn-egg rules](../items/SpawnEggs.md) cover acquisition and placement. [Egg registration][egg] · [Creative entry][creative] · [Placement][egg-placement]

**No natural encounter is established in this snapshot.** The checked spawn-placement registrations, biome and structure spawn lists, bundled data and structure templates contain no Mimicube entry. An entity registration and a Creative egg do not establish an End or End City encounter, a Survival acquisition route, or a farm location. [Natural spawn selection][natural] · [Biome and structure lists][spawn-lists] · [Spawn placements][placements] · [Bundled data][data]

## Behavior

Mimicubes retaliate against living attackers and have separate goals for targeting players and villagers, with player targeting taking priority over villager targeting. They move by hopping and shorten the delay between jumps while they have a target. Keep them away from villagers when testing or building an enclosure. [Goals][basics] · [Movement][movement]

### Equipment copying

After damage records a living attacker, the Mimicube repeatedly copies that attacker's **head, main-hand and off-hand slots** during its server AI updates. It does not copy chest armor, leggings, boots or the rest of an inventory. The recent-attacker record normally clears after more than **100 ticks**, about **5 seconds at 20 ticks per second**, or when the attacker dies. Switching held items during that window can therefore change what it copies even without another hit. [Damage attribution][damage-attribution] · [Server AI dispatch][ai-dispatch] · [Copying][copying] · [Attacker memory][attacker-memory]

- Only nonempty attacker slots replace the corresponding Mimicube slot. Emptying your hand or removing your helmet does **not** clear a copy it already holds
- The original item stays with its owner. The copy includes the stack count and item data; this is not theft
- Damageable copies are immediately set to **maximum damage**, which MattMC treats as broken. Their ordinary equipment attribute bonuses are skipped, and broken weapons skip their normal damage-enchantment bonus. A copied diamond helmet or sword does not give its usual armor or attack bonus. [Copying][copying] · [Broken items][broken] · [Equipment attributes][equipment-attributes] · [Melee damage][melee-damage] · [Broken weapon enchantments][broken-enchantments]

### Fighting a Mimicube

With an ordinary main-hand item, it uses the shared melee attack. That attack requires range and line of sight; the separate five-tick attack animation routine in the Mimicube class is not the callback used by this melee goal. Do not rely on that animation as a warning before damage. [Melee goal][melee-goal] · [Active melee damage][melee-damage] · [Separate animation routine][old-melee]

A Bow, Crossbow or Trident in its main hand selects its ranged goal, but copying a weapon does **not** reproduce all of a player's weapon abilities. [Combat selection][combat]

- Bow and Crossbow shots use the mob-arrow routine, with a compatible held projectile or an ordinary Arrow fallback. The shot routine does not consume ammunition and passes no firing weapon to the arrow, so it does not reproduce the copied bow's or crossbow's usual weapon-enchantment behavior. [Arrow creation][projectiles] · [Projectile selection][ammo] · [Arrow helper][arrow-helper] · [Weapon data on arrows][arrow-weapon]
- Trident throwing creates a fresh ordinary Trident projectile, rather than throwing its copied stack. However, the goal chooses its item-use hand by checking specifically for a Bow in the main hand: a main-hand Trident with an **empty off hand cannot start that firing cycle**. Treat Trident use as incomplete integration. [Trident projectile][projectiles] · [Ranged item use][ranged-use] · [Hand selection][arrow-helper] · [Item-use gate][item-use]
- Repeated equipment copying removes and re-adds the combat goal. Stopping the ranged goal cancels item use and resets its aim timer, so the configured cooldowns are not a dependable firing rate while it is copying an attacker. [Equipment changes][equipment-change] · [Combat selection][combat] · [Goal removal][goal-removal] · [Ranged reset][ranged-reset]

Holding a copied Shield alone does **not** guarantee blocked damage. The Mimicube reports itself as blocking when either hand holds a Shield, but the active damage path instead requires an item actually being used, its blocking component and its use delay. There is no dedicated Shield-raising goal. [Shield check][shield] · [Goals][basics] · [Damage blocking][blocking] · [Active blocking item][blocking-item]

### Food and interactions

A wounded Mimicube eats food it already holds, giving the **off hand priority**. After **100 eating ticks** it consumes one item and heals **5 health points (2½ hearts)**, capped by its maximum health. This custom routine checks the food component and applies its own healing; it does not run the food's normal eating effects. [Eating][eating] · [Health cap][healing]

The two hands currently behave differently: off-hand eating resets the timer after each item, while main-hand eating leaves it at 100. If it still holds main-hand food and remains wounded, subsequent items can be consumed on following ticks. Ongoing copying can also replace a partly eaten stack with another copy. These are source-level quirks, not a tested feeding strategy. [Eating][eating] · [Repeated copying][copying]

There is no implemented hand-feeding, taming, breeding or riding interaction. Dropping food or equipment beside a normally spawned Mimicube does not supply it: ordinary item pickup is disabled by default, and this mob does not enable it. [Mob class and goals][basics] · [Shared interaction][interaction] · [Pickup default][pickup-default] · [Pickup gate][pickup-gate]

### Keeping one

Use a named [Name Tag](../items/NameTag.md) to prevent ordinary distance-based despawning. Merely copying or holding equipment does not set that persistence flag. Its equipment is saved by the shared entity data system. [Name Tag][name-tag] · [Despawn rules][despawn] · [Copying][copying] · [Equipment saving][equipment-saving]

Switching to Peaceful does **not** automatically remove this registered mob: its registration leaves the shared “allowed in Peaceful” flag enabled. Normal player targeting is blocked in Peaceful, but this is not a removal command or a guarantee that villagers are safe. [Registration][entity] · [Registration defaults][entity-defaults] · [Despawn rules][despawn] · [Targeting rules][targeting]

## Notes

### Drops

No `minecraft:entities/mimicube` death-loot table is supplied in the checked bundled data, so no species-specific material drop is established. The default table name resolves to `data/minecraft/loot_table/entities/mimicube.json`; missing tables resolve to empty loot. [Default table name][entity-defaults] · [Bundled data][data] · [Loot loading][loot-loading] · [Missing-table fallback][loot-fallback]

**Copied equipment can still drop through the separate shared equipment path.** For an ordinary Mimicube, each occupied equipment slot starts with an **8.5%** drop chance when recent-player-credit and `doMobLoot` conditions apply. Player Looting adds **1 percentage point per level**; equipment with an effect preventing its drop is excluded. Damageable drops have their damage randomized, so a useful remaining durability is not guaranteed. [Death dispatch][death-dispatch] · [Mob-loot rule][mob-loot] · [Equipment drops][equipment-drops] · [Default chances][drop-chances] · [Looting modifier][looting] · [Modifier dispatch][looting-dispatch]

The Mimicube's old zero-drop helper is not called by that active equipment-drop path. Do not use upstream “copied equipment never drops” advice for this source snapshot. This is source-reviewed behavior; no item-duplication or loot farm was tested. [Unused helper][old-drops] · [Equipment drops][equipment-drops]

### Rendering limitation

Mimicube uses the Citadel model path tracked in [rendering issue #803](https://github.com/HungLo2020/MattMC/issues/803). When its visible ordinary body is selected by the active Rust whole-frame renderer with a readable texture and a finite transform, the inspected model path yields an empty body mesh and throws an error. This is a conditional source finding; no Mimicube crash or invisibility was reproduced in game. [Renderer registration][renderer-registration] · [Mimicube renderer][renderer] · [Mimicube model][model] · [Shared proxy][proxy] · [Model admission][render-admission] · [Extraction guard][render-guard]

Registered as `minecraft:mimicube`, using `MobCategory.MONSTER`, with a normal size of **1.02 × 1.02 blocks**. Its entity class is `EntityMimicube` and its spawn egg is `minecraft:mimicube_spawn_egg`. It comes from bundled Alex's Mobs content integrated into MattMC. [Registration][entity] · [Egg registration][egg]

Related: [Mimicube Spawn Egg](../items/MimicubeSpawnEgg.md) · [Spawn eggs](../items/SpawnEggs.md) · [Name Tag](../items/NameTag.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active registration and attributes, spawn/data wiring, shared AI and combat dispatch, item and damage guards, food, interactions, persistence, loot and the model path. No in-game spawn, combat, copying, feeding, drop, save/reload or rendering test was run. Server data packs, item components and later builds can change these results.

[basics]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L42-L91
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L227
[copying]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L195-L217
[eating]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L254-L299
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1945
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2076
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L84
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[spawn-lists]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L77-L178
[movement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L335-L393
[damage-attribution]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1316-L1321
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L633-L675
[attacker-memory]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L474-L481
[broken]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L433-L451
[equipment-attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2648-L2685
[melee-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1320
[broken-enchantments]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L180-L187
[melee-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L145
[old-melee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L79-L82
[combat]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L93-L111
[projectiles]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L113-L150
[ammo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Monster.java#L139-L147
[arrow-helper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/ProjectileUtil.java#L156-L164
[arrow-weapon]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L84-L114
[ranged-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/MimiCubeAIRangedAttack.java#L129-L144
[item-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3195-L3205
[equipment-change]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L152-L177
[goal-removal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/GoalSelector.java#L36-L44
[ranged-reset]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/MimiCubeAIRangedAttack.java#L67-L73
[shield]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L189-L193
[blocking]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1270-L1307
[blocking-item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3314-L3332
[healing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1139
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[pickup-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L129-L134
[pickup-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L439-L448
[name-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L31
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L631
[equipment-saving]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L750-L757
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1146-L1148
[entity-defaults]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2050-L2074
[targeting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L917-L926
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L53-L70
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L16
[looting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/looting.json#L7-L26
[looting-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L342-L382
[old-drops]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicube.java#L323-L325
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L255
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderMimicube.java#L13-L20
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelMimicube.java#L1-L28
[proxy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L55
[render-admission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L5965-L5975
[render-guard]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
