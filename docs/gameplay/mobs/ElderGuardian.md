# Elder Guardian

The **Elder Guardian** (`minecraft:elder_guardian`) is the large hostile resident of an [Ocean Monument](../structures/OceanMonument.md). Clear it before a major underwater mining job: its nearby Mining Fatigue III can reach through walls. Player-credited deaths provide a Wet Sponge, and a separate drop roll can provide a Tide Armor Trim Smithing Template. [Registration][registration] · [Fatigue][elder] · [Loot table][loot]

## Obtaining

A newly generated Monument includes **two wing rooms and one top penthouse**, each placing one Elder Guardian. These three placements use the structure-generation path and are separate from the ordinary Guardian spawn list. An already visited building may have fewer residents left. See [Ocean Monument](../structures/OceanMonument.md#finding-a-monument) for the active deep-ocean search route. [Room inclusion][building] · [Wing placements][wings] · [Penthouse placement][penthouse] · [Elder creation][elder-placement]

The constructor makes Elders persistent against ordinary distance despawning. **Peaceful still removes them** through the shared despawn path. The Monument's renewable monster list supplies Guardians, not replacement Elders; do not treat leaving the area or changing difficulty as an established way to replenish the three residents. [Persistence][elder] · [Peaceful removal][despawn] · [Monument spawn list][definition]

An Elder shares the registered water-spawn predicate with Guardians, but that predicate alone is not a natural spawn entry. Its [Elder Guardian Spawn Egg](../items/ElderGuardianSpawnEgg.md) is an ordinary category-listed item and is available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Survival as well as Creative. That item-access route is separate from Monument generation. [Placement registration][placement] · [Egg listing][eggs]

## Behavior

Elders have **80 health points (40 hearts)** and an attack-damage attribute of **8 points**. They inherit the [Guardian's target selection, beam, and defensive spikes](Guardian.md#behavior). A beam charge lasts **60 ticks after activation** rather than 80, or three seconds at normal tick speed, with the same short startup. The completed attack first requests **3 indirect-magic points**, increased to **5 on Hard**, before the inherited mob-attack call. Defenses, difficulty scaling, and recent-damage handling determine the actual health loss. [Elder attributes and duration][elder] · [Attribute registration][attributes] · [Shared attack goal][beam] · [Mob attack][mob-attack]

Break line of sight to cancel a charging beam. Unlike an ordinary Guardian, an Elder's already-running attack goal does **not** stop just because you move within three blocks. The inherited target selector still initially chooses targets beyond three blocks. Moving close also exposes a direct attacker to the shared spikes response. [Attack continuation and sight check][beam] · [Target selector][targets] · [Spikes][thorns]

### Mining Fatigue

Each Elder's active server AI checks for an effect pulse every **1,200 ticks**, about one minute at normal tick speed. Its timer is offset by its entity ID, so entering range does not start a fresh one-minute grace period. A pulse can apply **Mining Fatigue III for 6,000 ticks**, or five minutes. [Elder pulse][elder] · [Active AI caller][ai]

Eligible players must be in **Survival or Adventure**, not allied to that Elder, and at a **strict distance of less than 50 blocks** from it. The helper has **no line-of-sight test** and does not require either player or Elder to be underwater. Walls that interrupt the beam therefore do not stop the fatigue pulse. Creative and Spectator are excluded from this effect helper. [Effect eligibility][effect-helper] · [Game-mode definition][gametype]

A pulse applies when the player has no Mining Fatigue, has a weaker level, or has a finite Mining Fatigue effect with **less than 1,200 ticks remaining**. It does not blindly replace every current effect on every pulse. The affected players receive the Elder-effect screen event at the same time. [Refresh conditions][effect-helper] · [Duration comparison][effect-duration] · [Screen event][elder]

Mining Fatigue III multiplies the player's calculated mining speed by **0.0027** before the later block-break, submerged, and off-ground adjustments. This is a mining-speed factor, not a universal time to break a block. [Mining consumer][mining]

Use existing openings while clearing the residents. Killing an Elder does not itself remove an effect already on the player. Once no living Elder can reach you, wait for the effect to expire or drink [Milk](../items/MilkBucket.md) from a safe breathing position. Milk clears beneficial effects too, including Water Breathing; Conduit Power also does not cancel Mining Fatigue. The [Conduit guide](../blocks/Conduit.md) and [Brewing guide](../brewing/Brewing.md) own those preparation systems. [Milk clearing][milk] · [Effect duration][effect-duration] · [Mining calculation][mining]

## Drops

The loaded Elder table uses the following independent pools when normal mob loot is enabled. “Player credit” is the death context's recorded player condition; it is not simply a test of the final damage source. [Table][loot] · [Death-loot context][death] · [Player-credit predicate][credit] · [Monster loot gate][loot-gate]

| Pool | Result before Looting | Conditions |
| --- | --- | --- |
| Shards | **0–2 Prismarine Shards** | No player-credit condition in this pool |
| Common extra | **1 Cod (50%)**, **1 Prismarine Crystal (1/3)**, or nothing (1/6) | One weighted choice with weights 3, 2, and 1 |
| Wet Sponge | **1 Wet Sponge** | Requires player kill credit; no Looting increase |
| Rare fish | One fish-table roll on a **2.5%** chance | Requires player kill credit |
| Tide template | **1 Tide Armor Trim Smithing Template (20%)**, or nothing (80%) | No player-credit condition and no Looting modifier in this pool |

Looting increases the Shard and selected common-item quantities through the same functions as [Guardian drops](Guardian.md#drops): Looting III allows **0–5 Shards** and **1–4 of the selected Cod/Crystal item**. It raises the rare-fish chance to **3.5%, 4.5%, or 5.5%** at levels I–III, without changing the common pool's selection weights. The same burning/Fire Aspect smelting condition and fish subtable apply. These modifiers do **not** increase the Wet Sponge or Tide template result. [Elder table][loot] · [Count function][looting-count] · [Chance function][looting-chance]

Dry the Wet Sponge using the [Sponge guide](../blocks/Sponge.md); a wet drop is not ready to absorb more water. See the [Prismarine guide](../blocks/Prismarine.md#conduit-frames-and-other-uses) for duplicating a collected Tide template and [Smithing](../smithing/Smithing.md) for applying it. Three resident Elders provide three template chances, not a guaranteed template. Elders inherit the Guardian's **base experience reward of 10**, with the normal experience-release conditions. [Reward setting][xp] · [Experience release][death]

## Notes

- The entity is registered as `minecraft:elder_guardian`, in `MobCategory.MONSTER`, using the `ElderGuardian` class, with a base size of **1.9975 × 1.9975 blocks**. Its spawn egg is `minecraft:elder_guardian_spawn_egg`. [Registration][registration] · [Egg registration][egg-registration]
- The existing boss/mini-boss directory category describes the encounter. Its larger health pool and fatigue pulse do not establish a separate boss-health bar or a special respawn mechanic.
- The [Monument guide](../structures/OceanMonument.md) owns expedition planning and room navigation; [Guardian](Guardian.md) owns the shared attack and ordinary-spawn details.

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked room inclusion and active resident placement, entity/attribute registration, persistence versus Peaceful, inherited attacks, the server-AI effect caller and player/mode/range/refresh filters, mining and milk consumers, loaded death loot, and ordinary category-listed egg access. No in-game generation, fatigue, combat, drop, or drying test was run. Custom data packs, game rules, ticking, and prior exploration can change observations. [Default entity loot key][loot-key] · [Loot loading][loot-loading]

Related: [Ocean Monument](../structures/OceanMonument.md) · [Guardian](Guardian.md) · [Sponge](../blocks/Sponge.md) · [Mobs](Mobs.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L520-L529
[elder]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/ElderGuardian.java#L26-L78
[loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json
[building]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L167-L206
[wings]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1784-L1894
[penthouse]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1393-L1399
[elder-placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1534-L1545
[despawn]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L607-L632
[definition]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure/monument.json
[placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L165
[eggs]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1970-L2031
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[beam]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L345-L425
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1319
[targets]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L428-L442
[thorns]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L308-L322
[ai]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L635-L674
[effect-helper]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L47-L63
[gametype]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/GameType.java#L90-L92
[effect-duration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L176-L178
[mining]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L652
[milk]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L22-L24
[death]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1528
[credit]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[looting-count]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L68-L88
[looting-chance]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java#L41-L45
[xp]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L63-L69
[egg-registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1860-L1862
[loot-key]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
