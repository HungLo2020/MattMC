# Ender Chest

An Ender Chest (`minecraft:ender_chest`) opens the current player's **personal 27-slot Ender inventory**. Every Ender Chest opened by that same player accesses the same stored items, including across dimensions in the same world/server. Another player opening the same block sees their own inventory. More Ender Chest blocks provide more access points, not more slots. [Opening the player-owned container][opening] · [Per-player inventory field][player-field] · [Personal container capacity][capacity]

## Crafting and harvesting

Use the existing [Ender Chest recipe on Eye of Ender](../items/EyeOfEnder.md#other-verified-recipes): **eight Obsidian surrounding one Eye of Ender → one Ender Chest**. It needs a Crafting Table. Ordinary Obsidian is the exact ingredient; Crying Obsidian is not accepted by this recipe. [Complete recipe][recipe]

| Ordinary Survival mining | Result |
| --- | --- |
| Tool meets the loot table's Silk Touch condition | 1 Ender Chest |
| No Silk Touch | 8 Obsidian; no Eye of Ender |

**This MattMC registration has no correct-tool requirement.** A pickaxe is the tagged faster tool, but a minimum pickaxe tier is not required for these drops: ordinary mining by hand can return the eight Obsidian. Silk Touch changes the loot branch; Fortune does not increase the eight-block result. Breaking without Silk Touch means spending another Eye of Ender to craft a replacement. Hardness is **22.5** and blast resistance is **600**; these values do not imply an unbreakable block. [Actual registration][properties] · [Pickaxe tag][pickaxe] · [Complete Silk Touch and Obsidian loot][loot] · [Correct-tool requirement check][tool-gate] · [Active Survival drop dispatch][mining]

Neither branch includes the player's stored items. Mining an Ender Chest destroys an access block while leaving the personal inventory stored with the player. Even if the last placed Ender Chest is removed, another one can provide access to those items. [Storage belongs to the player][player-storage] · [Lid block entity, without a block inventory][block-entity]

## Placement, water, and opening

Placement faces the chest opposite the player's **horizontal** direction; it does not have upward or downward facing. Adjacent Ender Chests do not combine into a double chest. The block's shape is 14/16 wide, 14/16 long, and 14/16 high, and it emits light level **7** regardless of whether the lid is open. [Shape, nonjoining behavior, and placement][shape-facing] · [Shape dimensions helper][dimensions] · [Constant light registration][light]

Leave the block immediately **above** it free of a block that counts as a redstone conductor. The opening handler refuses to show the menu when that test succeeds, even though the interaction is consumed. It does not query a sitting cat, unlike the ordinary [Chest](Chest.md#placement-and-access). The checked rule is the above-block conduction test, not an arbitrary scan of space in front of the lid. [Exact opening obstruction test][obstruction]

It can be **waterlogged** when placed in source Water or filled through the Water Bucket path. Stored water does not replace the personal inventory or add another opening restriction. An empty bucket can remove the stored source. The block's water state is independent of the player-owned contents. [Placement water check][water-place] · [Water state and updates][water-state] · [Water insertion and pickup][water-fill] · [Active Water Bucket insertion][bucket]

Opening records the particular chest as the active access block. The menu remains valid only while that block entity is present and the player passes its interaction-range check. Closing clears the active-block link; it does **not** clear the stored slots. The above-block obstruction is checked when opening, rather than in this ongoing menu-validity test. [Active chest, validity, and close][active] · [Chest block-entity validity][valid] · [Block identity and interaction range][range] · [Menu validity and close dispatch][menu]

## Player ownership and persistence

Ender inventory is saved in the player's **EnderItems** data, separately from the ordinary inventory. The block entity handles the lid and openers; it does not hold those item stacks. The normal player-data save/load path therefore preserves Ender contents across leaving and returning to the same saved world. This is not shared storage between different players or unrelated world saves. [Player save and load fields][save] · [Ender slot serialization][slots] · [Player save/load caller][save-call] · [Player data storage][player-file] · [Player data loading][load-file]

Dimension travel moves the same ServerPlayer to the destination level, retaining this container. It does not choose a separate Ender inventory for the Nether or the End. During respawn, the new player receives the old player's Ender container **outside the keepInventory condition**, so ordinary death does not erase or spill it. These statements describe the checked player lifecycle, not protection from world resets or external edits. [Cross-dimension player transfer][dimensions-transfer] · [Respawn creates and restores a player][respawn] · [Ender inventory restored independently of keepInventory][restore] · [Ordinary inventory death drops][death-inventory] · [Server death-loot caller][death-caller]

Breaking the block cannot reveal another player's stored contents. The normal removal handler only spills block entities that implement a container, and EnderChestBlockEntity is a lid controller rather than such a container. Each player's stored stacks remain in their own Ender inventory. For carrying items *inside a dropped block item*, use the separate [Shulker Box guide](ShulkerBox.md#breaking-and-carrying-contents). [Ender Chest block entity][ender-be] · [Block-entity removal caller][removal] · [Block-container spill condition][spill-gate]

## Hoppers, comparators, and job sites

**Hoppers cannot insert into or extract from the personal Ender inventory through an Ender Chest.** The hopper's block lookup requires an exposed container; this block entity supplies none. Opening the lid or changing the player's saved contents does not make that block into an automated container. [Hopper block-container lookup][hopper] · [Chest base class][base-block] · [Lid-only block entity][ender-type]

The block has **no inventory-fullness comparator output**. Its class does not enable the analog-output hook, and the block entity is not a container for the generic fullness helper. Use a [Barrel](Barrel.md#hoppers-and-comparator-output), [Chest](Chest.md), or another explicitly supported container when a circuit must measure stored contents. [Default analog capability][no-analog] · [Generic block-container fullness gate][fullness-gate]

No Ender Chest job-site registration appears in the checked point-of-interest table. The [Barrel](Barrel.md#fisherman-job-site-and-piglins), not the Ender Chest, is the registered Fisherman workstation. [Registered job sites][job-sites]

## Piglins nearby

Personal storage is still subject to Piglin interaction callbacks. Successfully reaching the Ender Chest opening path can anger eligible nearby idle [Piglins](../mobs/Piglin.md) that can see the player. Breaking the block invokes the guarded-block anger path without that opening visibility filter. A blocked lid returns before the opening anger callback. [Opening and obstruction order][open-anger] · [Guarded-by-Piglins tag][guarded] · [Breaking callback][break-anger] · [Nearby Piglin filters][piglins] · [Target eligibility][target]

## Related pages

- [Ender Chest item](../items/EnderChest.md), [Eye of Ender](../items/EyeOfEnder.md), and [Obsidian](../items/Obsidian.md)
- [Barrel](Barrel.md), [Chest](Chest.md), [Shulker Box](ShulkerBox.md), and [Copper Chests](CopperChests.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `60699a119c4728a7bcaf15196f3c839cfcfd69dc` on 2026-10-02. Registrations, complete producing recipes and loot tables, expanded ingredient/mining tags, and the active menu, persistence, removal, hopper, comparator, and Piglin paths were reviewed. No gameplay test of storage, relocation, obstruction, dimension travel, respawn, automation, or villager employment was run.

[opening]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L76-L95
[player-field]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L149-L154
[capacity]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/PlayerEnderChestContainer.java#L13-L26
[recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/ender_chest.json
[properties]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L2612-L2616
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/ender_chest.json
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[player-storage]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L1567-L1569
[block-entity]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/EnderChestBlockEntity.java#L13-L57
[shape-facing]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L41-L74
[dimensions]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Block.java#L175-L182
[light]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L2612-L2616
[obstruction]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L76-L95
[water-place]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L68-L74
[water-state]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L134-L159
[water-fill]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bucket]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/BucketItem.java#L126-L139
[active]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/PlayerEnderChestContainer.java#L21-L72
[valid]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/EnderChestBlockEntity.java#L86-L88
[range]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/Container.java#L87-L98
[menu]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/ChestMenu.java#L69-L103
[save]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L675-L700
[slots]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/PlayerEnderChestContainer.java#L29-L48
[save-call]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/players/PlayerList.java#L317-L328
[player-file]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/storage/PlayerDataStorage.java#L30-L49
[load-file]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/storage/PlayerDataStorage.java#L66-L85
[dimensions-transfer]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1072-L1127
[respawn]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L437
[restore]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1553-L1587
[death-inventory]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L587-L603
[death-caller]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayer.java#L900-L907
[ender-be]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/EnderChestBlockEntity.java#L13-L57
[removal]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[spill-gate]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[hopper]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L374-L388
[base-block]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/AbstractChestBlock.java#L13-L27
[ender-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/EnderChestBlockEntity.java#L13-L15
[no-analog]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L232-L234
[fullness-gate]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L747-L750
[job-sites]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L139
[open-anger]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/EnderChestBlock.java#L76-L95
[guarded]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[break-anger]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Block.java#L480-L486
[piglins]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533
[target]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L676-L687
