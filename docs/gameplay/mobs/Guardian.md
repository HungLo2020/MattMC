# Guardian

The **Guardian** (`minecraft:guardian`) is a hostile swimmer found through the [Ocean Monument](../structures/OceanMonument.md) spawn route. Its charging beam and defensive spikes reward the use of cover and controlled approaches. Its drops can supply [Prismarine Shards](../items/PrismarineShard.md) and [Prismarine Crystals](../items/PrismarineCrystals.md). [Registration][registration] · [Behavior][guardian] · [Loot table][loot]

## Obtaining

The bundled Monument replaces the **monster spawn list inside its full structure bounding box** with Guardian candidates, configured in groups of **2–4**. This is a candidate group size, not a guaranteed number per spawn attempt. The active natural-spawning lookup reads that override before the biome's ordinary list. See [Ocean Monument](../structures/OceanMonument.md#finding-a-monument) for the four eligible deep-ocean biomes and search routes. [Spawn data][definition] · [Override lookup][spawn-lookup] · [Natural-spawn caller][natural]

For a natural Guardian spawn, the checked paths require:

- Non-Peaceful difficulty
- Water at the candidate position and directly below it
- A position inside the world border, with no redstone-conducting block immediately above, and room for the entity without collision/obstruction
- A successful sky-exposure check: a position exposed to the sky through water passes the Guardian's random gate **1 time in 20**; a position hidden from that sky check bypasses this random gate

The predicate does not use the ordinary monster darkness test, so lighting the building is not a verified way to prevent Guardian spawning. Normal population limits, distance, eligible-position selection, and collision checks still apply. The 1-in-20 gate is **not** a measured spawn rate. [Registered placement][placement] · [Water placement type][water-placement] · [Guardian predicate][spawn-rules] · [Sky check][sky] · [Natural-spawn checks][natural-checks]

Defeating the [Elder Guardians](ElderGuardian.md) does not disable the ordinary Guardian override. Elders are separately placed structure residents; the normal Monument monster list contains only Guardians. [Spawn data][definition]

The [Guardian Spawn Egg](../items/GuardianSpawnEgg.md) is an ordinary category-listed item. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) intentionally provides listed ordinary items in Survival as well as Creative; this is separate from natural spawning. [Egg listing][eggs]

## Behavior

Guardians have **30 health points (15 hearts)** and an attack-damage attribute of **6 points**. Their target selector chooses eligible **players, Squid, and Axolotls** more than three blocks away; the normal targeting rules still decide whether a particular entity can be attacked. [Attributes and target goal][stats] · [Target selector][targets] · [Attribute registration][attributes]

### Beam attacks

A Guardian stops navigating while charging at its target. After a short **10-tick startup**, it activates the visible attack target and charges for **80 ticks**, or four seconds at the normal 20 ticks per second. **Breaking line of sight cancels the target**, so use a solid corner or wall when a beam begins. The ordinary Guardian also cannot continue this attack goal when the target closes to three blocks or less; that is not protection from its spikes or other nearby Guardians. [Attack goal][beam]

At the end of a successful charge, the code first requests **1 indirect-magic damage point**, increased to **3 on Hard**, and then calls the ordinary mob attack using the Guardian's attack attribute. These are two damage calls, not a promised sum of health lost: difficulty scaling, defenses, and the victim's recent-damage handling also apply. [Damage calls][beam] · [Mob attack consumer][mob-attack] · [Player difficulty scaling][player-damage] · [Recent-damage handling][damage]

### Spikes and movement

When the Guardian's movement state is off, a direct hit from a living attacker can trigger **2 base points of thorns damage** against that attacker, before difficulty and defense rules. The thorns response excludes thorns damage itself and the configured avoidance tag. It is a response to an incoming attack, not passive damage from touching the mob. Its spike animation retracts while swimming and extends while idle in water. [Thorns handler][thorns] · [Avoidance tag][avoid-thorns] · [Spike animation][spike-animation]

Guardians use water navigation and flop when on the ground outside water. They do not apply Mining Fatigue; that nearby pulse belongs to [Elder Guardians](ElderGuardian.md#mining-fatigue). [Movement][movement] · [Elder effect][fatigue]

## Drops

With normal mob loot enabled, the bundled table has these separate pools. [Guardian loot][loot] · [Death-loot dispatch][death] · [Monster loot gate][loot-gate]

| Pool | Result before Looting | Conditions |
| --- | --- | --- |
| Shards | **0–2 Prismarine Shards** | No player-credit condition in this pool |
| Common extra | **1 Cod (40%)**, **1 Prismarine Crystal (40%)**, or nothing (20%) | One weighted choice; no player-credit condition |
| Rare fish | One fish-table roll on a **2.5%** chance | Requires player kill credit; separate from the common extra |

Looting adds a random rounded amount from **0 up to its level** to the Shard count and to a selected common Cod/Crystal stack. With Looting III, the table can therefore produce **0–5 Shards** and **1–4 of the selected common item**. Looting changes those counts, not the 40%/40%/20% selection. The rare-fish chance becomes **3.5%, 4.5%, or 5.5%** with Looting I, II, or III. [Count function][looting-count] · [Chance function][looting-chance] · [Table][loot]

The rare fish roll selects Cod, Salmon, Tropical Fish, or Pufferfish with weights **60, 25, 2, and 13**. It does not require fishing or a Fishing Rod. When the Guardian is burning, or its direct attacker's main-hand item carries an enchantment in `minecraft:smelts_loot` (bundled: Fire Aspect), the Cod pool and rare fish pool attempt furnace smelting. Cod and Salmon have cooked results; an item without a matching smelting recipe remains unchanged. [Fish subtable][fish] · [Smelting enchantment tag][smelts] · [Smelting function][smelt-function] · [Cod recipe][cod-recipe] · [Salmon recipe][salmon-recipe]

“Player kill credit” refers to the death-loot context's recorded player, rather than a requirement that the final damage source literally be a player. The table checks that context for the rare fish roll. Guardians have a **base experience reward of 10**; actual experience release also follows player-credit and mob-loot rules. [Player-credit condition][credit] · [Death and experience][death] · [Reward setting][xp]

## Notes

- The entity is registered as `minecraft:guardian`, in `MobCategory.MONSTER`, using the `Guardian` class, with a base size of **0.85 × 0.85 blocks**. Its spawn egg is `minecraft:guardian_spawn_egg`. [Registration][registration] · [Egg registration][egg-registration]
- The entity registration excludes Peaceful. The shared despawn path removes it in Peaceful; its ordinary natural spawn predicate also rejects that difficulty. [Registration][registration] · [Despawn path][despawn] · [Spawn predicate][spawn-rules]
- The [Prismarine construction guide](../blocks/Prismarine.md) owns building recipes; [Sea Lanterns](../blocks/LuminousBlocks.md#sea-lantern) use the separate Crystal resource. Spawn eligibility and loot-table contents do not establish an in-game farm yield.

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked entity/attribute registration, the loaded Monument spawn override and natural-spawning caller, water/sky/collision gates, installed attack and target goals, active damage calls, loaded death tables, player credit, Looting, fish smelting, and category-listed egg access. No in-game spawning, beam, damage, loot, or farm test was run. Data packs and game rules can change the checked data. [Default entity loot key][loot-key] · [Loot loading][loot-loading]

Related: [Ocean Monument](../structures/OceanMonument.md) · [Elder Guardian](ElderGuardian.md) · [Ocean biomes](../biomes/Oceans.md) · [Mobs](Mobs.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L735-L744
[guardian]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java
[loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/guardian.json
[definition]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure/monument.json
[spawn-lookup]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[natural]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L99
[water-placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L14-L23
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L294-L305
[sky]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/LevelReader.java#L93-L112
[natural-checks]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L244-L287
[eggs]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1970-L2031
[stats]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L72-L92
[targets]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L428-L442
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[beam]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L345-L425
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1319
[player-damage]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L750
[damage]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1141-L1200
[thorns]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L308-L322
[avoid-thorns]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/damage_type/avoids_guardian_thorns.json
[spike-animation]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L208-L216
[movement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L252-L266
[fatigue]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/ElderGuardian.java#L63-L73
[death]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1528
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[looting-count]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L68-L88
[looting-chance]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java#L41-L45
[fish]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[smelts]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[smelt-function]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L31-L52
[cod-recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/smelting/cooked_cod.json
[salmon-recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/smelting/cooked_salmon.json
[credit]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[xp]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Guardian.java#L63-L69
[egg-registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1891
[despawn]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L607-L632
[loot-key]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
