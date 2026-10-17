# Loot and drops

**Check the source, its conditions, and collection separately when an expected item is missing.** Breaking a block, killing a mob, opening generated storage, and reeling in a catch supply different information to the loot system. A named reward is not necessarily guaranteed, and producing a dropped stack does not put it straight into your inventory.

## Choose the right route

| What you are doing | What matters first | Detailed guide |
| --- | --- | --- |
| Breaking a block normally | Whether that tool can harvest it, then the block's tool/state conditions and item-drop rule | [Mining](Mining.md), [Fortune and Silk Touch](../enchanting/MiningEnchantments.md), and the [block's page](../blocks/Blocks.md) |
| Killing a mob | The mob's death-loot gate, any recent player-credit requirement, the killing damage source, and that reward's conditions | [Mob pages](../mobs/Mobs.md) and [Looting](../enchanting/MeleeUtilityEnchantments.md#looting-check-the-reward-not-just-the-creature) |
| Accessing generated storage | Its assigned loot table and whether pending contents have already been generated | [Structures](../structures/Structures.md) and [Chest](../blocks/Chest.md) |
| Reeling in a fishing bite | The fishing context, including open-water eligibility and the rod/player luck values | [Fishing](Fishing.md) and [Luck and Unluck](../effects/LuckAndUnluck.md) |

The checked callers use their corresponding block, entity, chest, and fishing contexts. Ordinary block mining supplies the actual tool and block state; it does not use the mob-kill context. Fishing supplies the bobber and rod. Do not transfer a condition or bonus between routes just because both can produce the same item. [Block break and harvest gate][break] · [Block context][block-context] · [Block-state lookup][block-table] · [Mob context][mob-context] · [Chest context][container-roll] · [Fishing context][fishing]

## A listed drop is a possibility until its conditions pass

A loot table can contain several pools. Each eligible pool performs its own rolls, and a roll selects among eligible entries by weight. Failed conditions exclude a pool or entry; a successful selection can still produce a count of zero. A weight is a share among the choices that remain, not a guaranteed number of items or a percentage independent of those choices. [Pool conditions and selection][pool] · [Entry eligibility][entry]

For example, an eligible **[Blaze](../mobs/Blaze.md#drops-and-experience)** death can produce **zero Blaze Rods** with no Looting: its player-gated rod pool chooses a count from 0 to 1. Receiving no rod on one qualifying kill is consistent with that table. Conversely, merely knowing that the table contains a Blaze Rod does not establish a drop from a death without the required player credit. [Blaze table][blaze-table] · [Monster loot gate][monster-gate] · [Active death dispatch][death] · [Player condition][player-condition]

For **[Diamond Ore](../blocks/OreResources.md)**, the bundled block table chooses the ore itself with Silk Touch; the other branch produces Diamonds and applies Fortune. The normal break must first pass the correct-tool check. A valid loot-table branch cannot rescue a tool that never reaches the harvest callback. Follow the existing mining guides for tool materials and broken-tool restrictions. [Harvest gate][break] · [Diamond Ore table][diamond-table]

These are bundled defaults. The server loads loot from its selected data resources, and a missing lookup resolves to an empty table. A resource file in a repository or a recipe display alone is not proof that a particular server loaded it or that your action satisfied its conditions. See [World data packs](WorldDataPacks.md) when a world's rewards differ. [Resource loading][loader] · [Lookup fallback][lookup]

## Player credit and Looting are different checks

A player-gated pool tests for **recent player-damage credit**. The shared damage handler records a player for 100 game ticks and can also credit a tamed Wolf's owner; the death context only includes that player when the credit remains live and the player can be resolved. This is not the same question as who dealt the final damage. [Credit assignment][credit] · [Credit countdown][credit-tick] · [Death context][mob-context] · [Player condition][player-condition]

The common Looting count and enchanted-chance readers instead inspect the **living attacker in the killing damage source**, using the enchantment's active equipment slots at loot evaluation. Bundled Looting uses the main hand. A Wolf's owner receiving player credit does not make those readers inspect the owner's sword. Read [attacker context and player-kill credit](../enchanting/MeleeUtilityEnchantments.md#attacker-context-and-player-kill-credit) for fire and weapon implications; [equipped gear](../enchanting/MeleeUtilityEnchantments.md#equipped-gear-has-its-own-drop-chance) is another separate drop path. [Count reader][looting-count] · [Chance reader][looting-chance] · [Equipment lookup][enchantment-level] · [Looting definition][looting-definition]

## Generated containers: first access can matter

Some structure containers initially store **a loot-table assignment and seed**, rather than a finished inventory. The checked Desert Pyramid chest placement uses this route. When the pending table is unpacked, it fills the inventory and clears that assignment, so closing and reopening the same chest does not reroll its contents. This describes containers with pending loot; a chest you craft and fill is ordinary storage. [Pyramid placement][pyramid] · [Chest assignment][chest-assignment] · [One-time resolution][container-roll]

For an ordinary loot-bearing Chest:

- Menu creation can resolve its pending contents with the opening player's current luck
- An earlier inventory read or removal can resolve them with no player instead; Hopper extraction reads the source inventory
- Spectator access is rejected while the loot-table assignment is still pending

[Menu and inventory access][container-access] · [Hopper extraction][hopper]

**First access is broader than first opening.** Once generated, drinking Luck or changing equipment does not reroll the stored items. A supplied luck value only helps when the table actually uses it; [Luck and Unluck](../effects/LuckAndUnluck.md#where-player-luck-is-supplied) explains the checked bundle's limits. Breaking a Chest also has two distinct outcomes: the block's own item and its spilled inventory. Follow [Chest storage cautions](../blocks/Chest.md#storage-cautions) before relocating loot.

## Check the world's drop rules

These rule names are not interchangeable. All three below default to **true**, but a server can change them. Use [Game rules](GameRules.md) for permissions, queries, and editing. [Registered names and defaults][rules]

| Rule | Checked scope relevant here |
| --- | --- |
| `doTileDrops` | Gates item spawning through the ordinary block-drop helper and its block-experience helper |
| `doMobLoot` | Gates the shared ordinary mob death-loot path and the usual player-credited experience path; mob overrides and special paths can differ |
| `doEntityDrops` | Used by separate consumers such as vehicle item drops and Item Frame drops; it is not the shared mob-death toggle |

[Block item and XP gates][block-gates] · [Shared mob gate][mob-gate] · [Death and XP dispatch][death] · [Vehicle gate][vehicle] · [Item Frame gate][item-frame]

These gates describe the named production paths, not a command to remove items already lying on the ground. Player inventory loss has its own **keepInventory** rules in [Death and respawn](DeathAndRespawn.md#what-you-keep-and-lose).

## Collecting and keeping the result

If an item is visible but will not enter your inventory, move close enough for pickup and make room for its stack. Ordinary pickup requires a living, non-Spectator player, an expired pickup delay, any assigned owner restriction to match, and inventory acceptance. Blocks and the shared mob item-spawn helper apply a default **10-tick pickup delay**, about half a second at 20 TPS; other creation paths can differ. [Player contact][pickup-contact] · [Pickup checks][pickup] · [Block spawn][block-gates] · [Entity spawn][entity-spawn] · [Default delay][item-delay]

Nearby compatible drops can merge into one stack. The checked merge requires matching item/components and owner restriction, with enough stack capacity; fewer visible item entities need not mean fewer items. [Merge rules][merge]

Ordinary dropped stacks have a **6,000-tick age limit**, equivalent to five minutes only at a steady 20 TPS while they tick. Merging keeps the younger age, so it can postpone expiry. [Merge age][merge] The server limits entity ticking by its active ticking range and freeze state; being away for five wall-clock minutes is not a reliable expiry or recovery test. Items can also be destroyed by damage that applies to that item, so protect the collection area and retrieve valuable drops promptly. Special item ages/delays and item-specific damage resistance can change these defaults. [Server ticking gates][tick-gates] · [Item age][item-age] · [Damage handling][item-damage] · [Special age/delay controls][item-delay]

Item stacks and **experience orbs are separate rewards**. Block experience runs through its after-break path, mob experience has its own credit/gate checks, and fishing spawns orbs separately from the catch. An item count is not an experience total; collecting XP may also feed Mending before your level increases. See [Experience](Experience.md) and [Death and respawn](DeathAndRespawn.md#getting-back-to-your-items). [Block after-break][block-after] · [Mob experience][death] · [Fishing rewards][fishing]

## Sources and verification

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. The review followed resource loading, normal block harvest, active death-loot dispatch, generated Chest access, fishing retrieval, item ticking, and player pickup. This is a shared troubleshooting guide, not a complete reward catalogue or a gameplay test. Individual mob/block overrides, custom item components, data packs, and server rules can change a result; no observed loot distribution or guaranteed recovery is claimed.

Related: [Mechanics](Mechanics.md) · [Mining](Mining.md) · [Fishing](Fishing.md) · [Looting and Fire Aspect](../enchanting/MeleeUtilityEnchantments.md) · [Inventory controls](InventoryControls.md) · [Hopper](../blocks/Hopper.md)

[break]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L250-L303
[block-context]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Block.java#L342-L387
[block-table]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L269-L278
[mob-context]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1513-L1529
[container-roll]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[fishing]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java#L435-L469
[pool]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L104
[entry]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolEntryContainer.java#L16-L39
[blaze-table]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/loot_table/entities/blaze.json#L1-L41
[death]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1487
[monster-gate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[player-condition]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L23-L29
[diamond-table]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/loot_table/blocks/diamond_ore.json#L1-L52
[loader]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L39-L77
[lookup]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L121
[credit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1339
[credit-tick]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L464-L469
[looting-count]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L81
[looting-chance]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java#L36-L46
[enchantment-level]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L280-L292
[looting-definition]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/enchantment/looting.json#L1-L42
[pyramid]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L299-L309
[chest-assignment]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java#L447-L470
[container-access]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L49-L93
[hopper]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L218-L261
[rules]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/GameRules.java#L50-L65
[block-gates]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Block.java#L411-L423
[mob-gate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[vehicle]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L68-L76
[item-frame]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L219-L243
[pickup-contact]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/player/Player.java#L476-L498
[pickup]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L319-L336
[entity-spawn]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/Entity.java#L2070-L2079
[item-delay]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L388-L419
[merge]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L211-L250
[tick-gates]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L388-L418
[item-age]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L155-L176
[item-damage]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L287
[block-after]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/DropExperienceBlock.java#L30-L36
