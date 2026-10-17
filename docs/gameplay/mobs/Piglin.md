# Piglin

A **Piglin** (`minecraft:piglin`) can trade random supplies for Gold Ingots, but it is also a sword- or crossbow-wielding threat. Wear a piece of gold armor before approaching an ordinary adult, avoid attacking its group, and leave guarded containers and gold alone until you are ready for a fight. A [Piglin Brute](PiglinBrute.md) follows different rules. [Identity][entity-ids] · [Combat][piglin-idle] · [Gold recognition][gold-armor]

## Obtaining

### Where to find Piglins

The bundled normal Nether's ambient Piglin entries are in **Nether Wastes** (group range 4–4, weight 15) and **Crimson Forest** (3–4, weight 5). These are spawn-selection values, not guaranteed visible groups or a spawning rate. [Normal preset][normal] · [Nether biome source][nether-biomes] · [Wastes entry][wastes] · [Crimson entry][crimson]

The active natural-spawn path checks the biome/structure list, a valid on-ground position, collision and liquid obstruction. Piglin's registered extra predicate rejects a **Nether Wart Block beneath its feet** and adds no darkness test. Lighting alone is therefore not a reliable way to prevent ordinary Piglin spawning. Server spawning settings, mob limits and player distance still matter. [Spawn-list selection][natural-list] · [Placement registration][spawn-placement] · [Ground checks][on-ground] · [Piglin predicate][piglin-stats] · [Natural checks][natural-spawn] · [Obstruction][mob-obstruction]

[Bastion Remnants](../structures/BastionRemnant.md) also place resident Piglins through their connected templates, including in Bastion-eligible biomes without an ambient Piglin entry. Those templates store sword or crossbow equipment and persistence; the structure-spawn path does not reroll the ordinary baby/weapon choice. This is separate from ambient spawning. [Resident pool][piglin-pool] · [Sword resident][sword-template] · [Crossbow resident][crossbow-template] · [Entity placement][entity-place] · [Spawn initialization][piglin-spawn]

For a placed encounter, use a [Piglin Spawn Egg](../items/PiglinSpawnEgg.md). The egg and Gold Ingot are ordinary category-listed items, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) provides a separate Creative insertion route; that listing is not a natural spawn or gold-production route. [Egg category][category-eggs] · [Gold category][category-gold]

## Behavior

Piglins have **16 health points (8 hearts)** before modifiers. Outside structure spawning, initialization has a 20% baby branch; adults otherwise receive either a Crossbow or Golden Sword, with equal weapon-selection probability. Each adult gold-armor slot is rolled separately at 10%. Actual equipment can subsequently change through item pickup. [Registered attributes][attributes] · [Health][piglin-stats] · [Spawn equipment][piglin-spawn] · [Weapon selection][piglin-weapon] · [Pickup][piglin-pickup]

### Gold armor and aggression

Wearing **any one** Golden Helmet, Chestplate, Leggings or Boots satisfies the bundled safe-armor check. Holding a Gold Ingot or gold tool does not satisfy that armor check. Gold suppresses the ordinary attack trigger for a player without safe armor; it does **not** clear an existing anger target. [Safe armor tag][gold-tag] · [Equipped-slot check][gold-armor] · [Player sensing][gold-target] · [Target priorities][anger-target]

- **Attacking Piglins can provoke the group.** Adults can retaliate and share their target; an attacked baby flees and can also alert nearby adults. Gold armor does not remove this retaliation path. [Damage callback][piglin-hurt] · [Response][anger-retaliation] · [Shared retaliation][anger-share]
- **Opening guarded containers can anger nearby idle Piglins that can see you.** Checked callers include Chests, Barrels, Ender Chests and Shulker Boxes. The nearby search extends 16 blocks around the player's bounding box. [Chest][chest-anger] · [Barrel][barrel-anger] · [Ender Chest][ender-anger] · [Shulker Box][shulker-anger] · [Anger search][anger-target]
- **Breaking a guarded block uses a separate anger check without that visibility requirement.** The bundled tag includes Gold Blocks, Raw Gold Blocks, gold ores, Gilded Blackstone and the listed container families, including Copper Chests. Do not assume a wall makes mining them safe. [Guarded tag][guarded] · [Break callback][break-anger] · [Anger search][anger-target]

Ordinary Piglins have avoidance behavior for tagged soul-fire blocks, Soul Torches, Soul Lanterns and lit Soul Campfires. This behavior belongs to their idle/celebration activities, so treat it as a behavior cue rather than a guaranteed barrier against an angry mob. [Repellent tag][repellent-tag] · [Lit-campfire check][repellent-check] · [Idle behavior][piglin-idle] · [Avoidance][repellent-behavior]

### Bartering

1. Choose an **ordinary adult Piglin**, not a baby or Brute
2. Interact while holding a **Gold Ingot**. An eligible adult consumes one ingot and admires it; it cannot already be admiring or have admiration disabled. Alternatively, let an eligible Piglin pick up a dropped ingot; ground pickup requires `mobGriefing` and its loot-pickup flag
3. Wait for admiration to finish, then collect the thrown result. The admiration memory lasts **119 game ticks**, about six seconds at 20 ticks per second. The result is thrown toward the nearest visible player, or a nearby position if none is visible; it is not placed directly in your inventory

[Interaction dispatch][piglin-interact] · [One-ingot offer and eligibility][barter-interact] · [Ground pickup][piglin-pickup] · [Pickup processing][barter-pickup] · [Timer][barter-timer] · [Core completion][barter-core] · [Completion callback][barter-finish] · [Adult response][barter-response] · [Loot and throwing][barter-throw]

**Do not hit a Piglin during the exchange.** A non-Piglin attacker interrupts its held-item processing without a barter response; a player hit also disables admiration for **400 ticks**, normally 20 seconds. A baby may take a dropped item but does not produce the adult barter response. Nuggets, Gold Blocks and other admired items are not barter currency. [Currency][barter-currency] · [Interrupted exchange][anger-retaliation] · [Adult/baby split][barter-response]

For Gold Ingot supplies, see [ore processing](../blocks/OreResources.md#processing-raw-metal) and [Gold Block packing/unpacking](../blocks/ResourceStorageBlocks.md#gold-block). Their mining and recipe details remain on those guides.

The unchanged bundled barter table makes **one weighted selection**, with total weight **469**. Amounts below are the selected entry's output; no particular item is guaranteed after any fixed number of ingots. [Full barter table][barter-loot]

| Possible result | Amount | Weight |
| --- | --- | --- |
| Soul Speed enchanted book | 1 | 5 |
| Soul Speed Iron Boots | 1 | 8 |
| Fire Resistance potion | 1 | 8 |
| Splash Potion of Fire Resistance | 1 | 8 |
| Water Bottle | 1 | 10 |
| Iron Nuggets | 10–36 | 10 |
| Ender Pearls | 2–4 | 10 |
| [Dried Ghast](../blocks/DriedGhast.md) | 1 | 10 |
| String | 3–9 | 20 |
| Nether Quartz | 5–12 | 20 |
| Obsidian | 1 | 40 |
| Crying Obsidian | 1–3 | 40 |
| Fire Charge | 1 | 40 |
| Leather | 2–4 | 40 |
| Soul Sand | 2–8 | 40 |
| Nether Bricks (items) | 2–8 | 40 |
| Spectral Arrows | 6–12 | 40 |
| Gravel | 8–16 | 40 |
| Blackstone | 8–16 | 40 |

For example, Dried Ghast is **10/469, about 2.13% per completed barter** with this unchanged table. Its hydration, crafting and fossil-placement details remain in the [Dried Ghast guide](../blocks/DriedGhast.md). Bartering is neither a way to choose a result nor a guarantee of portal supplies. [Barter table][barter-loot]

### Zombification

Keep a bartering Piglin in a dimension whose type is **Piglin-safe**. The bundled Nether is safe; the Overworld, End and Primordial Caves types are not. An ordinary non-immune Piglin with AI enabled converts to a [Zombified Piglin](ZombifiedPiglin.md) after **more than 300 consecutive server AI ticks** in an unsafe dimension, about 15 seconds at 20 ticks per second. Returning to safe conditions resets this counter. Transport is therefore not a way to preserve an ordinary Piglin as an Overworld barter partner. [Conversion condition and reset][conversion] · [AI dispatch][ai-dispatch] · [Nether][nether-type] · [Overworld][overworld-type] · [End][end-type] · [Primordial Caves][primordial-type]

Conversion cancels ongoing admiration and releases the Piglin's stored inventory before conversion; it does not complete a barter. Commands or saved entity data can set conversion immunity, but this guide establishes no ordinary item that grants it. [Conversion items][conversion-items]

## Drops

The ordinary Piglin entity loot table has **no item pools**. Gold Ingots are barter input, not a guaranteed death drop. Its carried equipment and stored items are handled separately. [Entity loot][piglin-loot] · [Death dispatch][death-dispatch] · [Stored inventory release][piglin-inventory]

With mob loot enabled, default spawned equipment has a base **8.5%** drop chance per equipped slot when the death has recent player attribution. Player Looting adds **1 percentage point per level** in the bundled enchantment. Prevent-equipment-drop effects can block it, and ordinary damageable equipment is dropped with randomized wear. Picked-up or specially preserved equipment can use different chances; do not apply 8.5% to every item a Piglin carries. [Mob-loot rule][mob-loot-rule] · [Equipment conditions][equipment-drop] · [Base chance][drop-chances] · [Looting modifier][looting] · [Held-item preservation][piglin-pickup]

A separate charged-Creeper kill path can yield **1 Piglin Head**, if that powered Creeper is eligible to drop loot and has not already produced its skull drop. This is not ordinary Piglin loot and does not apply to Brutes. [Charged-Creeper callback][creeper-kill] · [Exact entity selection][head-select] · [Head result][head-loot]

## Notes

This page covers `minecraft:piglin`; [Piglin Brutes](PiglinBrute.md) and [Zombified Piglins](ZombifiedPiglin.md) are separate entities. Gold-armor protection, barter eligibility, item pickup and dimension conversion are separate checks; passing one does not make the others safe.

Related: [Mobs](Mobs.md) · [Bastion Remnant](../structures/BastionRemnant.md) · [Nether](../dimensions/Nether.md) · [Gold Ingot](../items/GoldIngot.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked entity/attribute registration, normal Nether spawn wiring, structure residents, AI dispatch, gold tags, guarded-block callers, completed/interrupted barter dispatch and loot, conversion, and death drops. No in-game spawn, barter, combat, transport, item-pickup or drop test was run. Probabilities and tick durations describe the checked code and bundled data, not measured rates; data packs and server settings can change results. [Piglin AI tick][piglin-tick]

[entity-ids]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/EntityType.java#L1030-L1048
[piglin-idle]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L152-L183
[gold-armor]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L638-L645
[normal]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L24-L35
[nether-biomes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L55-L69
[wastes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json#L104-L109
[crimson]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json#L98-L103
[natural-list]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L296-L325
[spawn-placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L137-L137
[on-ground]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L24-L41
[piglin-stats]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L185-L193
[natural-spawn]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L253-L287
[mob-obstruction]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L735-L740
[piglin-pool]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/template_pool/bastion/mobs/piglin.json
[sword-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/mobs/sword_piglin.nbt
[crossbow-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/bastion/mobs/crossbow_piglin.nbt
[entity-place]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L522
[piglin-spawn]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L195-L234
[category-eggs]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2059-L2060
[category-gold]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1798-L1798
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L209-L210
[piglin-weapon]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L317-L325
[piglin-pickup]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L388-L427
[gold-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/piglin_safe_armor.json#L1-L8
[gold-target]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L83-L111
[anger-target]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L497-L533
[piglin-hurt]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L364-L381
[anger-retaliation]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L552-L581
[anger-share]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L585-L601
[chest-anger]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L247-L258
[barrel-anger]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L40-L51
[ender-anger]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L77-L94
[shulker-anger]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/ShulkerBoxBlock.java#L67-L89
[guarded]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json#L1-L14
[break-anger]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487
[repellent-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/block/piglin_repellents.json#L1-L9
[repellent-check]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L118-L125
[repellent-behavior]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L283-L285
[piglin-interact]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L251-L261
[barter-interact]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L535-L550
[barter-pickup]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L327-L350
[barter-timer]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L793-L803
[barter-core]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L134-L149
[barter-finish]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/StopHoldingItemIfNoLongerAdmiring.java#L8-L20
[barter-response]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L399
[barter-throw]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L413-L445
[barter-currency]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L77-L83
[barter-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L1-L265
[conversion]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/AbstractPiglin.java#L77-L106
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[nether-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/the_nether.json
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/overworld.json
[end-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/the_end.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[conversion-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L310-L314
[piglin-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/piglin.json#L1-L4
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[piglin-inventory]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L155-L159
[mob-loot-rule]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L814-L838
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L13
[looting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/looting.json#L6-L26
[creeper-kill]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L170-L177
[head-select]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json#L1-L22
[head-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/charged_creeper/piglin.json#L1-L16
[piglin-tick]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java#L295-L303
