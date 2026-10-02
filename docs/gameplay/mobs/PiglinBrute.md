# Piglin Brute

A **Piglin Brute** (`minecraft:piglin_brute`) is a dangerous Golden Axe-wielding resident of [Bastion Remnants](../structures/BastionRemnant.md). **Gold armor and Gold Ingots do not pacify it.** Identify Brutes before opening a route into a room, and keep a retreat available. [Identity][entity-ids] · [Equipment][brute-stats] · [Target selection][brute-target]

## Obtaining

Brutes are placed by connected Bastion mob templates. The templates named `melee_piglin` and `melee_piglin_always` explicitly store `minecraft:piglin_brute`, **50 health** and persistence. The ordinary Piglin pool can choose a Brute, and the melee pool also includes Brute pieces; their weights are choices for particular connectors, not a guaranteed Brute count for the building. [Ordinary resident pool][piglin-pool] · [Melee pool][melee-pool] · [Brute template][brute-template] · [Second Brute template][brute-always]

This is an active generation route: the connected [bridge start template][bridge-base] points to the resident pool, the [single-pool placement][pool-place] includes entities, and [entity placement][entity-place] runs their structure-spawn initialization. That initialization equips the Brute's Golden Axe. [Spawn initialization][brute-stats]

No bundled biome spawn-list entry for Piglin Brutes was found, and Bastions define no monster-spawn override. Removing a placed Brute therefore does not establish a renewable Brute spawn point. Ordinary biome monsters may still spawn in the area; see [Bastion residents](../structures/BastionRemnant.md#layouts-and-residents). [Bastion definition][bastion] · [Biome fallback][spawn-overrides]

For a placed encounter, use a [Piglin Brute Spawn Egg](../items/PiglinBruteSpawnEgg.md). It is category-listed, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can provide the egg in Survival as well as Creative. This is separate from natural generation. [Egg listing][category-eggs]

## Behavior

Brutes have **50 health points (25 hearts)** before modifiers, compared with an ordinary Piglin's 16 points. Their spawn initialization gives them a Golden Axe. This guide does not turn the base attack attribute into a fixed final damage value: equipment, difficulty and other modifiers must also be accounted for. [Registered attributes][attributes] · [Brute health and equipment][brute-stats] · [Piglin health][piglin-stats]

### Fighting and gold

- **They target attackable players without checking gold armor.** Their target priority is an existing anger target, a visible attackable player, then a nemesis. Wearing gold to approach an ordinary Piglin does not remove a Brute's player-target path. [Target selection][brute-target]
- **They pursue and attack in melee.** Their fight activity moves toward a target and runs a melee attack; their idle behavior includes returning to and strolling near a saved home position. Use distance and solid cover to prepare your approach, rather than following one blindly around a corner. [Combat activity][brute-ai] · [Home behavior][brute-home]
- **Retaliation can involve nearby Piglins.** A Brute hurt by a non-Piglin-family attacker uses the shared retaliation logic, which can alert nearby adults. Picking a fight with one can turn a quiet group hostile even if you wear gold. [Hurt response][brute-target] · [Shared retaliation][anger-share]
- **Brutes do not barter.** Their AI has no ordinary Piglin admiration/barter activity, and their pickup rule accepts only Golden Axes before applying the base pickup behavior. Throwing gold supplies into a Brute's path does not buy a safe opening. [AI activities][brute-ai] · [Pickup restriction][brute-pickup]

Ordinary Piglins' soul-fire avoidance is also absent from the Brute's checked activities. Do not treat a Soul Torch or a gold-armored companion's peaceful encounter as protection from a Brute. [Brute activities][brute-ai]

### Persistence and Peaceful difficulty

The bundled resident templates mark Brutes persistent, so ordinary distance-despawning is not the intended way to clear them. Persistence does not override Peaceful removal: Brutes are registered as disallowed in Peaceful, and the mob despawn check removes such entities before checking persistence. [Resident persistence][brute-template] · [Peaceful registration][entity-ids] · [Despawn ordering][despawn]

### Zombification

Brutes share the ordinary Piglin conversion check. With AI enabled and without entity-data immunity, more than **300 consecutive server AI ticks** in a dimension whose type is not Piglin-safe converts one to a [Zombified Piglin](ZombifiedPiglin.md), normally about 15 seconds at 20 ticks per second. Safe conditions reset the counter. The bundled Nether is safe; the Overworld, End and Primordial Caves are not. [Shared conversion][conversion] · [Brute AI dispatch][brute-pickup] · [Server AI caller][ai-dispatch] · [Nether][nether-type] · [Overworld][overworld-type] · [End][end-type] · [Primordial Caves][primordial-type]

This is the same dimension-type rule as [Piglin zombification](Piglin.md#zombification), not a promise that transport makes a dangerous Brute harmless before it converts.

## Drops

The bundled Brute entity loot table has **no item pools**. Its Golden Axe is carried equipment, not a guaranteed loot-table reward. [Entity loot][brute-loot] · [Spawn axe][brute-stats]

With mob loot enabled, its ordinary spawned axe uses the default equipment-drop path: base **8.5%** with recent player attribution, increased by **1 percentage point per player Looting level** in the bundled enchantment. Prevent-equipment-drop effects can block the drop, and ordinary damageable equipment receives randomized wear. Picked-up or specially preserved equipment can follow different drop chances. [Death dispatch][death-dispatch] · [Mob-loot rule][mob-loot-rule] · [Equipment conditions][equipment-drop] · [Base chance][drop-chances] · [Looting][looting]

The charged-Creeper Piglin Head branch matches the ordinary `minecraft:piglin` entity specifically; it is not a Brute head source. [Exact head selection][head-select]

## Notes

The mob is registered as `minecraft:piglin_brute`; its egg is [Piglin Brute Spawn Egg](../items/PiglinBruteSpawnEgg.md). This is a distinct entity from a gold-armored [Piglin](Piglin.md), with different targeting, pickup and bartering behavior.

Related: [Mobs](Mobs.md) · [Bastion Remnant](../structures/BastionRemnant.md) · [Nether](../dimensions/Nether.md) · [Golden Axe](../items/GoldenAxe.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked registration/attributes, connected NBT residents and initialization, bundled biome spawn lists and structure override, active AI, gold/pickup differences, persistence, Peaceful removal, conversion and equipment/death loot. No in-game structure generation, combat, transport, difficulty-change or drop test was run. No Brute count, respawn rate, exact combat damage or safe combat design is guaranteed; data packs, entity data and later source changes can differ.

[entity-ids]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L1030-L1048
[brute-stats]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinBrute.java#L63-L89
[brute-target]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinBruteAi.java#L132-L145
[piglin-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/mobs/piglin.json
[melee-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/mobs/piglin_melee.json
[brute-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/mobs/melee_piglin.nbt
[brute-always]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/mobs/melee_piglin_always.nbt
[bridge-base]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/bridge/starting_pieces/entrance_base.nbt
[pool-place]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L137-L181
[entity-place]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L522
[bastion]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure/bastion_remnant.json#L1-L13
[spawn-overrides]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[category-eggs]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2059-L2060
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L209-L210
[piglin-stats]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L185-L193
[brute-ai]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinBruteAi.java#L44-L88
[brute-home]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinBruteAi.java#L103-L126
[anger-share]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L585-L601
[brute-pickup]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinBrute.java#L106-L124
[despawn]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[conversion]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/AbstractPiglin.java#L77-L106
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[nether-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/the_nether.json
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/overworld.json
[end-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/the_end.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[brute-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/piglin_brute.json#L1-L4
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[mob-loot-rule]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L814-L838
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L13
[looting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/looting.json#L6-L26
[head-select]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json#L1-L22
