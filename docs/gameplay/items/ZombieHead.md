# Zombie Head

**Zombie Head** (`minecraft:zombie_head`) is a collectible decoration and head-slot item. One item places either a standing head or its wall form. [Registration][items] · [Standing/wall pairing][pairing]

## Obtaining

A charged [Creeper](../mobs/Creeper.md) can produce **one Zombie Head** by killing an ordinary [Zombie](../mobs/Zombie.md), with mob loot enabled and that Creeper's special-head allowance still unused. The victim must be the exact Zombie type; Husks, Drowned, and Zombie Villagers are not substitutes. This special drop has no player-kill requirement or percentage roll. [Death dispatch][death] · [Killer callback][creeper] · [Mob-loot gate][mob-loot] · [Victim table][root] · [Reward][reward]

The allowance is shared across eligible head drops: **one explosion killing several eligible mobs does not give a special head for each victim**. See [charged-Creeper conditions](../blocks/HeadsAndSkulls.md#charged-creeper-conditions) before arranging a collection attempt. [One-output handling][creeper]

In Creative, search the combined [inventory item browser](../mechanics/InventoryBrowser.md) by name. Its catalog is also visible in Survival, but ordinary Survival requests are blocked before item insertion.

## Usage

Place it on a surface or wall for building, display, mapmaking, or themed decoration. Put a **standing Zombie Head directly above a [Note Block](../blocks/NoteBlock.md)** for the Zombie imitation sound, then play or power the Note Block. Use the standing form; the [sound guide](../blocks/HeadsAndSkulls.md#note-block-sounds) explains the wall-form limitation. [Placement][pairing] · [Instrument registration][blocks] · [Selection and playback trigger][note]

Put it in the inventory's **head equipment slot** to wear it. Its visibility factor is halved against an ordinary Zombie in the targeting calculation; this does not make the wearer invisible or immune to attack. Ordinary held use in the air does not quick-swap it onto the head. [Head-slot registration][items] · [Slot acceptance][slot] · [Equipment check][equipment] · [Visibility][visibility] · [Targeting caller][targeting] · [Held-use rule][use]

## Behavior

The same item places `minecraft:zombie_head` or `minecraft:zombie_wall_head`. Breaking either form normally returns **one Zombie Head**, even by hand; no Silk Touch is required. Its block loot preserves a supplied custom name. Block-drop game rules and loss of the loose item still matter. [Block registrations][blocks] · [Wall loot alias][wall-loot] · [Harvest check][harvest] · [Loot][loot] · [Drop dispatch][drops]

Follow [Heads and Skulls](../blocks/HeadsAndSkulls.md#placement-support-and-water) for rotation, support removal, and water behavior.

## Notes

This is the inventory item for the `minecraft:zombie_head` block and its wall form. The [Zombie-head guide](../blocks/HeadsAndSkulls.md#zombie-heads) owns the shared placed-block behavior.

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game charged-Creeper, equipment, visibility, placement, collection, sound, or inventory-browser test was run. Game rules, data packs, and supplied item data can change the result.

Related: [Heads and Skulls](../blocks/HeadsAndSkulls.md) · [Zombie](../mobs/Zombie.md) · [Creeper](../mobs/Creeper.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2078-L2087
[creeper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L170-L179
[death]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1436
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[root]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json
[pairing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L23-L52
[slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ArmorSlot.java#L32-L45
[equipment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3575-L3584
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L173-L188
[visibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L887-L914
[targeting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L79-L89
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2758-L2793
[note]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L54-L132
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L364-L419
[reward]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/charged_creeper/zombie.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/zombie_head.json
