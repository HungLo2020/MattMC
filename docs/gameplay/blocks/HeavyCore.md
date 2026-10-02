# Heavy Core

A **Heavy Core** is the rare, placeable ingredient used to craft a [Mace](../items/Mace.md). Its exact block and item ID is `minecraft:heavy_core`. A placed core is a small, blast-resistant block that can hold water; despite its name, it does not fall when its support is removed. [Block registration][block] · [Item registration][item] · [Core class][core] · [Support default][shape]

## Obtaining a core

The verified loot route is a successful **ominous [Vault](Vault.md#ominous-vaults)** opening in a Trial Chamber. The generated ominous Vault template configures **one Ominous Trial Key** and the ominous reward table. The active block-use handler passes that configuration to key validation and reward selection. See the Vault guide for finding chambers, getting keys and each player's eligibility. [Ominous template][template] · [Reward-template pool][template-pool] · [Block-use dispatch][use] · [Key acceptance][key] · [Actual reward roll][roll]

Under the bundled tables, each eligible opening has a **7.5% chance to yield one Heavy Core**: the optional unique pool runs with probability **75%**, then makes one weighted selection in which Heavy Core has **weight 1 out of 10**. Its entry has no count function, so a successful selection creates one core. The common and rare reward rolls do not add extra cores. This is a per-opening chance, not a guarantee after a fixed number of keys. [Parent table][ominous] · [Unique table][unique] · [Common table][common-loot] · [Rare table][rare-loot] · [Selection and roll counts][pool] · [Default weight][weight] · [Single-item entry][one-item] [one-count]

The core is **ejected into the world** with the other rewards; collect it after unlocking. A core visible in the Vault's display is a separate preview roll and does not reserve that result. The normal Vault reward tables contain no Heavy Core. [Ejection][eject] · [Preview][preview] · [Normal tables][normal] [normal-common][] [normal-rare][] [normal-unique]

No bundled crafting recipe produces a Heavy Core. **Separate inventory route:** Heavy Core is an ordinary **Ingredients** category entry, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) provides an insertion route in Survival and Creative independently of Vault rewards. Its epic rarity does not make that category an operator-only entry. [Item rarity][item] · [Category][category] · [Entry][entry]

## Placement, water and shape

The core's collision/outline shape is **8 × 8 × 8 model units**, or **½ × ½ × ½ block**. It sits centrally on the bottom of its block space: `x=4…12`, `y=0…8`, `z=4…12` on the 16-unit block scale. It is not a full solid cube. [Shape][core] · [Column construction][column] · [Collision default][shape]

Its only defined block state is Boolean `waterlogged`, with no facing or axis. Placement into a source-Water cell sets that state; when waterlogged, it returns a water-source fluid state and schedules water updates. The shared waterlogging implementation also supports adding water and picking it back up with a Bucket. Removing the support below does not trigger falling or an attachment failure. [States and placement][core] · [Shared waterlogging][waterlogging] · [Support default][shape]

## Recovering a placed core

**Hand mining can recover one Heavy Core.** The registration has **no correct-tool drop requirement**. An unbroken pickaxe is the efficient tagged tool, but there is no required pickaxe tier for obtaining this block. Silk Touch is unnecessary, and Fortune does not multiply its one-core loot. [Registration][block] · [Active drop gate][gate] [harvest] · [Pickaxe tag][pickaxe] · [Pickaxe dispatch][pickaxe-dispatch] · [Tool speed][material] [speed][] [broken-speed][] · [Complete loot][loot]

The placed core has **hardness 10** and **blast resistance 1,200**. Hardness is not a mining duration, and blast resistance does not make the item drop unconditional: the complete loot table still has an explosion-survival condition. See [Mining](../mechanics/Mining.md) for shared tool and mining rules. [Properties][block] · [Loot][loot] · [Explosion condition][explosion]

## Crafting a Mace

Place **one Heavy Core directly above one [Breeze Rod](../items/BreezeRod.md)** to make **one Mace**. There are no other ingredients. The surrounding spaces in the resource's shaped pattern are trimmed, so the resulting one-column, two-row recipe fits the personal crafting grid. A Blaze Rod is not the specified ingredient. [Exact recipe][mace] · [Pattern decoding][pattern] · [Pattern trimming][trim] · [Crafting-input trimming][input]

Craft with the core's item form; recover a placed core first. The recipe consumes the core and rod, and no bundled recipe reverses a Mace into those ingredients. [Ingredient consumption][consume] Use the [Mace item page](../items/Mace.md) for the weapon; this guide owns the core's placement and recovery rules.

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. Checked the generated ominous Vault configuration and connected reward pool, active key/reward/ejection paths, all normal and ominous Vault reward tables, exact selection quantities, the full bundled recipe scan, core shape/state/waterlogging, harvest gate and complete block loot. The Vault guide owns the wider structure and encounter system. No in-game Vault opening, drop-rate trial, crafting, mining, explosion or waterlogging test was run. Custom loot/configuration and later changes can alter these rules.

[gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[shape]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[explosion]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/predicates/ExplosionCondition.java#L27-L35
[one-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/entries/LootItem.java#L33-L36
[material]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L6799-L6809
[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L173
[core]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/HeavyCoreBlock.java#L22-L80
[template]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt
[template-pool]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/reward/ominous_vault.json#L1-L16
[use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/VaultBlock.java#L44-L62
[key]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L273
[roll]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L321-L338
[ominous]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json#L1-L52
[unique]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_unique.json#L1-L35
[common-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_common.json#L1-L99
[rare-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_rare.json#L1-L124
[pool]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[weight]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[eject]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/vault/VaultState.java#L74-L94
[preview]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L284-L304
[normal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json#L1-L52
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1775-L1784
[entry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1843-L1847
[column]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L175-L183
[waterlogging]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L377-L385
[pickaxe-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Item.java#L435-L437
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/heavy_core.json#L1-L21
[mace]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/mace.json#L1-L16
[pattern]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L70-L99
[trim]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L103-L136
[input]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java#L32-L82
[consume]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L98
[one-count]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L248-L254
[normal-common]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_common.json#L1-L162
[normal-rare]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_rare.json#L1-L189
[normal-unique]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_unique.json#L1-L36
[harvest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[speed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L47
[broken-speed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
