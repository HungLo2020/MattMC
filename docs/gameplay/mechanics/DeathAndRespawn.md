# Death and respawn

Before travelling far, select a working respawn point and decide which supplies you can afford to carry. Ordinary death can scatter inventory items and remove most accumulated experience. Returning to the death location is a recovery attempt, not a guarantee that everything is still there. [Server death dispatch][death] · [Inventory drop rules][drops] · [Experience reward][xp]

## What you keep and lose

The **keepInventory** game rule defaults to **false**. For an ordinary non-Spectator death under that setting:

- The main inventory and equipped items are dropped into the world
- Items with the bundled **Curse of Vanishing** effect are removed before the remaining inventory is dropped
- The replacement player starts without the old experience level or progress
- The death's **base experience award is `min(level × 7, 100)` points**, not the full amount previously earned

At level 10, the base award is **70 points**; at level 30, it is **100 points**. Enchantment processing can modify an experience reward, so these examples describe the unmodified base calculation. Returning to collect the orbs does not restore the old level directly; [Experience](Experience.md) explains the increasing cost of levels and the possible Mending diversion. [Rule default][rules] · [Drops][drops] [inventory-drop][] [equipment-drop][] · [Curse definition][vanishing] · [Reward calculation][xp] [reward-processing][] · [Respawn state][restore]

With **keepInventory enabled**, the checked death path keeps ordinary inventory, including those cursed items, and respawn copies the player's experience level and progress. [Ender Chest contents](../blocks/EnderChest.md#player-ownership-and-persistence) persist independently of this rule. A Shulker Box being carried in ordinary inventory is still part of the carried inventory; see its [carrying guide](../blocks/ShulkerBox.md#breaking-and-carrying-contents). [Drop gate][drops] · [Inventory, experience and Ender storage restoration][restore]

## Getting back to your items

Dropped items are ordinary item entities. They can be destroyed by damage that applies to that item, and their ordinary age limit is **6,000 ticking steps**, equivalent to five minutes only at a steady 20 ticks per second while the entity is ticking. Time alone is not their only risk. Do not rely on a fixed five-minute real-world recovery window. [Item age][item-age] · [Damage and item-specific immunity][item-damage]

Ordinary death respawn restores maximum health and starts fresh food data: **20 hunger points and 5 saturation points**. It does not preserve the old active status effects. The [inventory item browser](InventoryBrowser.md#mode-and-permission-limits) does not supply replacements through ordinary Survival requests. Obtain replacements through their actual acquisition routes; Creative insertion is separate from retrieving the original dropped stacks. Bring replacement equipment and food appropriate to the return route; a full health bar does not remove the hazard that killed you. [Replacement player][replacement] · [Death-state restoration][restore] · [Fresh food defaults][food-defaults]

## Choosing where you return

The server checks the player's **one saved respawn setting**, including its dimension, before using the default-spawn fallback. Selecting a new point replaces that saved setting; it does not keep a history of older beds or anchors. [Saved setting][saved] · [Selection and validation][respawn]

- Use a [Bed](../blocks/Bed.md#sleeping-and-respawn) in a dimension that permits it. Bed use can set a point even when daylight prevents sleeping, but distance and obstruction still matter
- Use a charged [Respawn Anchor](../blocks/RespawnAnchor.md#charging-and-selecting-your-respawn-point) in an anchor-enabled dimension. Charging alone does not select it; a successful ordinary death respawn spends one charge
- Check the [dimension rules](../dimensions/Dimensions.md#travel-and-respawn-at-a-glance) before interacting. Beds and charged anchors can explode in the wrong dimension

Those linked block guides own the complete setup, placement, charge, and explosion rules. The actual respawn check still requires a usable standing position. A missing or obstructed ordinary saved block, or an unusable anchor, can send you to the server's **default spawn**. The failed-block path also clears that saved point on the replacement player, so repair the site and select it again. It does not search for an older bed. [Respawn checks][respawn] · [Default transition][fallback] · [Failed-point copying rule][replacement]

## Special cases

Returning from the End through the checked credits/return path uses a different restoration mode: it preserves player state and does not spend an ordinary anchor charge. A **Hardcore** death respawn switches the player to **Spectator**. Command-forced respawn positions also have different block requirements; this guide's ordinary bed/anchor instructions are not a promise about those overrides. [Death versus End-return and Hardcore][request] · [State modes][restore] · [Forced-position branch][respawn]

See [Collecting and keeping dropped items](LootAndDrops.md#collecting-and-keeping-the-result) and [Leaving an active area](TicksAndChunkActivity.md#what-happens-when-you-leave) for pickup, saved age and ticking distinctions.

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Active server death, inventory/equipment drops, player replacement, saved-point selection, fallback, and End-return dispatch were traced. No death, recovery, unloaded-chunk, respawn, Hardcore, or dimension gameplay test was run. World rules and custom enchantments/data can change the result; no runtime recovery guarantee is made.

Related: [Health](Health.md) · [Experience](Experience.md) · [Bed](../blocks/Bed.md) · [Respawn Anchor](../blocks/RespawnAnchor.md) · [Mechanics](Mechanics.md) · [Equipment curses](../enchanting/EquipmentCurses.md#vanishing-the-death-inventory-check)

[death]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L869-L927
[drops]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L587-L603
[inventory-drop]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Inventory.java#L443-L453
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/EntityEquipment.java#L62-L68
[xp]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1534-L1542
[reward-processing]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L587
[vanishing]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/enchantment/vanishing_curse.json
[rules]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/GameRules.java#L50-L52
[restore]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1553-L1588
[item-age]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L155-L176
[item-damage]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L287
[food-defaults]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/food/FoodData.java#L11-L17
[replacement]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L452
[saved]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1912-L1927
[respawn]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L993-L1059
[fallback]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/portal/TeleportTransition.java#L48-L81
[request]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1766-L1788
