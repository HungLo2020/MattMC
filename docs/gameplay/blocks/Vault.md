# Vault

A **Vault** exchanges a matching Trial Key for a batch of loose rewards. Its eligibility record belongs to each placed Vault and tracks players separately, so another player can still use a Vault you have already opened. Normal and ominous Vaults use different keys and reward tables. They are reward blocks, with no storage menu to open. [Key interaction][vault] [use-key] · [Reward history][history]

## Finding Vaults

Find placed Vaults in **Trial Chambers**, underground structures available through the normal Overworld generation route when structure generation is enabled. The active structure set, biome eligibility, start pool, and jigsaw templates connect to both reward forms. This is a verified template route, not a claim that every chamber layout contains the same number or arrangement of Vaults. [Normal preset][normal-preset] · [Structure and set][structure] [structure-set] · [Eligible biomes][biome-tag] · [Active selection and placement][structure-filter] [biomes] [jigsaw] [placement]

The start pool selects corridor-end templates. In the bundled data, `end_1` connects directly to the ominous reward pool and `end_2` to the normal reward pool; those pools select the actual configured Vault templates. The Vault's reward configuration is therefore present in reachable structure data, rather than inferred from an unused loot table. [Start pool][start-pool] · [End templates][end-one] [end-two] · [Reward pools][normal-pool] [ominous-pool] · [Vault templates][normal-template] [ominous-template]

## Normal and ominous Vaults

There is **one registered block and inventory item**, `minecraft:vault`. Its block states are horizontal `facing`, Boolean `ominous`, and `vault_state` with `inactive`, `active`, `unlocking`, or `ejecting`. Both appearances use the same valid Vault block-entity type. [Registration][blocks] [items] [type] · [State definition][vault] [state]

### Normal Vaults

A normal generated Vault has `ominous=false`. Its template requires **one [Trial Key](../items/TrialKey.md)** and uses `minecraft:chests/trial_chambers/reward`. A fresh ordinary Vault item placed in Creative starts with the same normal key and reward defaults. [Normal template][normal-template] · [Default configuration][config] · [Fresh block entity][entity]

### Ominous Vaults

An ominous generated Vault has `ominous=true`. Its template requires **one [Ominous Trial Key](../items/OminousTrialKey.md)** and uses `minecraft:chests/trial_chambers/reward_ominous`. The keys are not interchangeable. [Ominous template][ominous-template] · [Exact key check][key-test]

The **block-entity configuration chooses the key and rewards**. The ominous block-state flag controls appearance and related effects; changing that flag alone does not replace the key or loot configuration. The Vault's use and ticking paths do not require Bad Omen or Trial Omen and do not transform a normal Vault when an affected player approaches. The separate [Trial Spawner](TrialSpawner.md) guide owns the ominous encounter and key-earning process. [Configuration][config] · [Key/reward lookup and visual effects][entity] · [Block use][vault]

## Getting keys

Neither key nor the Vault has a bundled crafting recipe. Both keys have Creative entries, and the following Survival routes are wired in the checked source:

| Key | Verified sources |
| --- | --- |
| Trial Key | A possible normal Trial Spawner completion reward; Trial Chamber entrance-chest loot; corridor Decorated Pot loot |
| Ominous Trial Key | A possible ominous Trial Spawner completion reward |

[Key registrations and Creative entries][items] [creative-keys] · [Spawner key tables][key-loot] [ominous-key-loot] · [Normal reward defaults][spawner-defaults] · [Ominous reward configuration][ominous-spawner] · [Active reward dispatch][spawner-reward] [spawner-eject] · [Entrance chest and pot loot][entrance-loot] [pot-loot]

The checked chamber templates actually assign the entrance and corridor-pot loot tables to those containers. [Decorated Pot](DecoratedPot.md) loot is resolved through its randomizable container and can be recovered when its contents spill. These sources can produce a Trial Key; finding a particular chest or pot does not guarantee one. See [Trial Spawner](TrialSpawner.md) for completion conditions and encounter rewards. [Entrance template][entrance-template] · [Pot template][pot-template] · [Connected corridor/decor pools][corridor-pool] [decor-pool] · [Pot loot access][pot] [container-loot] · [Container spill][container-spill]

## Activating and using a Vault

Approach a Vault you have not already used and wait for its active display. Its default activation range is **4 blocks**, expanding to **4.5 blocks** for retaining nearby eligible players once active. The check uses the player's block position and a strict distance comparison, and normally refreshes every **20 game ticks**, about one second. It includes Creative players, excludes spectators, and does not require line of sight for detection. It filters out players already in this Vault's rewarded-player record. [Default ranges][config] · [Detection and filtering][detector] [connected] · [State polling][state]

Use the active block while holding the matching key, **without Sneak/Crouch**. The block handler has no special clicked-face or keyhole requirement. It accepts only the configured item **with matching item components** and enough count. For the natural templates that is one ordinary, unmodified key. A renamed or otherwise modified key can fail the component check. [Block interaction][vault] · [Key comparison][key-test] [stack-match] · [Secondary-use routing][use-routing]

A successful Survival use consumes **one key**, resolves the reward batch, and immediately records your UUID as rewarded. Wrong keys, previously rewarded players, and an empty resolved reward list do not consume a key through this handler. Creative use preserves the key but still records the player and starts reward ejection. [Accepted/rejected key flow][use-key] · [Creative-aware consumption][consume]

An active display can be present because **another eligible player** is nearby. That does not make an already rewarded player eligible again. Leave the Vault ready for that player and look for another Vault for your next key. [Player filtering][connected] · [Per-player rejection][use-key]

## Unlocking and ejection

| State | What it does | Emitted light |
| --- | --- | ---: |
| `inactive` | Waits for an eligible nearby player; clears the displayed item on entry | 6 |
| `active` | Accepts a matching key and cycles the preview | 12 |
| `unlocking` | Holds the accepted reward batch during the opening delay | 12 |
| `ejecting` | Throws the reward stacks out above the block | 12 |

These light levels apply to both normal and ominous appearances. Key use is routed only while the block is `active`; it cannot accept another player's key midway through the same ejection sequence. [Light registration][blocks] · [States][state] · [Use-state gate][vault]

After acceptance, the Vault waits **14 game ticks** before entering `ejecting`, then another **20 game ticks** before the first stack. The first output is therefore about **1.7 seconds** after acceptance at 20 ticks per second, while actively ticking. Further stacks are ejected **20 ticks apart**. After the final stack it waits another **20 ticks**, then returns to active or inactive according to nearby eligible players. This short sequence does **not** reset the player reward history. [Unlocking delay][roll] · [Ejection timing and return state][state] · [Tick scheduling][tick] [level-tick] [chunk-tick]

The Vault ejects **item stacks into the world**, not directly into the key user's inventory. Its spawn routine does not assign a pickup owner, so other players can collect them too. Stay nearby with inventory space and give the block clear space above for collection. Your reward eligibility was already recorded at acceptance, not at pickup. [Upward ejection][state] [eject] · [Pickup rules][pickup] · [History update][use-key]

### The display is a preview

While active, the display cycles every **20 game ticks** by making a separate random display selection. Actual rewards are rolled when the key is accepted, with the player and key supplied to the loot context. **Seeing a Heavy Core or another desired item in the display does not reserve it for the next key.** During ejection the displayed stack follows the queued outputs instead. [Preview selection][display] · [Cycle timing][key-test] · [Actual reward roll][roll] · [Ejection display update][state]

## Normal versus ominous rewards

Both bundled reward tables perform **one initial weighted choice** between their rare and common tables, at weights **8:2**, followed by **one to three additional common-table rolls**. Each also has one possible unique-table roll: **25%** for normal, **75%** for ominous. Those percentages describe the optional unique roll, not the chance of every named unique item. [Normal table][normal-loot] · [Ominous table][ominous-loot] · [Roll/weight handling][loot-rolls] [loot-weights] [loot-chance]

| Reward group | Normal examples | Ominous examples |
| --- | --- | --- |
| Common | Arrows, Poison Arrows, Emeralds, Iron Ingots, Wind Charges, Honey Bottles, Diamonds, Ominous Bottles | Emeralds, Wind Charges, strong Slowness Arrows, Diamonds, Ominous Bottles |
| Rare | Shield, enchanted Bow/Crossbow, enchanted Iron or Diamond equipment, enchanted books, Golden Carrots | Emerald/Iron/Diamond Blocks, enchanted Crossbow and Diamond equipment, Golden Apple, enchanted books including **Wind Burst I** |
| Unique | Golden Apple, Bolt Armor Trim template, Guster Banner Pattern, Precipice music disc, Trident | Enchanted Golden Apple, Flow Armor Trim template, Flow Banner Pattern, Creator music disc, **Heavy Core** |

[Normal common][common] · [Normal rare][rare] · [Normal unique][unique] · [Ominous common][ominous-common] · [Ominous rare][ominous-rare] · [Ominous unique][ominous-unique] · [Book enchantment application][book]

For a useful example of the actual weighting, the ominous unique table gives **Heavy Core weight 1 out of a total 10**. Combined with the 75% unique-roll condition, that is a **7.5% Heavy Core chance per successful ominous Vault opening** under these bundled tables. Heavy Core is absent from the normal reward tables. This calculation comes from the checked resource weights; it is not a guarantee after a fixed number of keys. [Ominous parent table][ominous-loot] · [Unique weights][ominous-unique] · [Normal tables][normal-loot] [common] [rare] [unique]

Use existing item pages for the rewards themselves, including [Heavy Core](../items/HeavyCore.md), [Trident](../items/Trident.md), and [Golden Apple](../items/GoldenApple.md). [Banners](Banners.md) owns the Guster/Flow pattern uses. The Vault guide does not replace those item or crafting mechanics.

## Player history and persistence

Treat a Vault as **one opening per player in ordinary play**, not a chest that refills for that player after a waiting period. The record belongs to this specific placed block entity; a different Vault has its own record. It is saved with the block's configuration, queued rewards, and state-resume time, so simply leaving the area or reloading the world does not clear it. [History lookup/storage][history] · [Save/load][saved]

There is one precise limit: the history retains **up to 128 rewarded UUIDs**. When a new entry takes the set above 128, the oldest retained entry is removed. This is a size limit, not a timed cooldown. The ordinary use path neither clears the whole history after ejection nor resets it because the Vault becomes inactive. [History insertion and eviction][history] · [End of ejection][state]

## Mining, placement, and limitations

The [Vault item](../items/Vault.md) is registered and available in Creative. There is no Survival crafting recipe or ordinary block-item drop. Vaults have **hardness 50**, so they are slow to break but are not registered as unbreakable. The bundled block loot table has **no reward pools**, including no Silk Touch branch: mining with Silk Touch or Fortune does not recover a Vault. Breaking it is not another way to open its reward table. [Registration][blocks] · [Creative entry][creative-vault] · [Mining rules][defaults] [mining] · [Empty block loot][vault-loot]

The registration has no correct-tool drop requirement, and the ordinary pickaxe/axe/shovel/hoe mining tags do not assign Vault an efficient tool. That does not create a drop where the block loot is empty. Leave a useful generated Vault in place. [Registration][blocks] · [Mining tags][pickaxe] [axe] [shovel] [hoe] · [Tool gate][gate] · [Loot][vault-loot]

A placed item faces toward its placer and starts inactive with `ominous=false`. The block has full-cube collision, no attachment-support requirement, and no waterlogged state. Pistons reject it because it has a block entity. It provides neither a normal inventory for Hoppers nor a redstone-powered opening handler; using the key is the active player interaction. [Placement and states][vault] · [Inherited shape/support][defaults] · [Piston rule][piston] · [Block-entity interface][entity] · [Hopper lookup][hopper]

For custom maps, configure the **key item and reward table together** with the desired appearance. Player detection ranges and a separate preview loot table can also be configured. These are block-entity settings, not a Survival menu or a property automatically supplied by the ordinary Vault item. The natural template configurations documented above are the verified player-facing defaults. [Configuration fields][config] · [Template configurations][normal-template] [ominous-template]

## Verification scope

Source-reviewed at MattMC commit `053cd852a8609f4002234ce0d445d3a345b551ae` on 2026-10-02. Registration, active use/tick dispatch, normal and ominous template configuration, structure-pool connections, all eight Vault reward tables, key-source loot, component checks, player history, saving, mining loot, and timing were inspected. No in-game key, reward, cooldown, multiplayer, or generation test was run. Custom loot/configuration can change the bundled behavior described here.

[vault]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/VaultBlock.java
[use-key]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L273
[history]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultServerData.java
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[structure]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json
[structure-set]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/structure_set/trial_chambers.json
[biome-tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json
[structure-filter]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[biomes]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java
[placement]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java
[start-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/chamber/end.json
[end-one]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/corridor/end_1.nbt
[end-two]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/corridor/end_2.nbt
[normal-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/reward/all.json
[ominous-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/reward/ominous_vault.json
[normal-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/reward/vault.nbt
[ominous-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt
[blocks]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L6787-L6798
[items]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2674-L2677
[type]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L256
[state]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultState.java
[config]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultConfig.java
[entity]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java
[key-test]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L332-L341
[creative-keys]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1951-L1952
[key-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/key.json
[ominous-key-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/ominous/trial_chamber/key.json
[spawner-defaults]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerConfig.java#L82-L95
[ominous-spawner]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/ominous.json
[spawner-reward]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L115-L130
[spawner-eject]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L236-L249
[entrance-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/entrance.json
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/pots/trial_chambers/corridor.json
[entrance-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/corridor/entrance_1.nbt
[pot-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/decor/undecorated_pot.nbt
[corridor-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/corridor.json
[decor-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/decor.json
[pot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java
[container-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/RandomizableContainer.java
[container-spill]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[detector]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/PlayerDetector.java#L23-L37
[connected]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultSharedData.java#L67-L77
[stack-match]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/ItemStack.java#L666-L677
[use-routing]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L400
[consume]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1086
[roll]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L306-L330
[tick]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L214-L244
[level-tick]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/Level.java#L440-L459
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L710-L783
[eject]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L29-L47
[pickup]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L320-L334
[display]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L284-L304
[normal-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json
[ominous-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json
[loot-rolls]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java
[loot-chance]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceCondition.java
[common]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_common.json
[rare]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_rare.json
[unique]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_unique.json
[ominous-common]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_common.json
[ominous-rare]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_rare.json
[ominous-unique]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_unique.json
[book]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/storage/loot/functions/SetEnchantmentsFunction.java#L60-L64
[saved]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L66-L80
[creative-vault]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1263
[defaults]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L355
[mining]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[vault-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/vault.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[axe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[piston]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L259
[hopper]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L360-L389
