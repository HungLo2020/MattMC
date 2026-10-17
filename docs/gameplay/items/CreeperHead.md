# Creeper Head

**Creeper Head** (`minecraft:creeper_head`) is a collectible decoration and head-slot item. It also crafts the [Creeper Banner Pattern](CreeperBannerPattern.md). One item supplies its standing and wall forms. [Registration][items] · [Pattern recipe][recipe] · [Pairing][pairing]

## Obtaining

A charged [Creeper](../mobs/Creeper.md) must kill **another Creeper** to produce one Creeper Head. Mob loot must be enabled, and the killer must not already have produced a special head. Its own explosion is not this victim-drop route. The special table has no player-kill requirement or percentage roll. [Death dispatch][death] · [Killer callback][creeper] · [Mob-loot gate][mob-loot] · [Exact victim][root] · [Reward][reward] · [Self-explosion path][explosion]

That allowance is shared across eligible heads: **an explosion killing several eligible mobs does not award one special head per victim**. Follow [charged-Creeper conditions](../blocks/HeadsAndSkulls.md#charged-creeper-conditions). [One-output handling][creeper]

In Creative, search the combined [inventory item browser](../mechanics/InventoryBrowser.md) by name. Its catalog is also visible in Survival, but ordinary Survival requests are blocked before item insertion.

## Usage

Place it as a standing or wall decoration. A **standing Creeper Head directly above a [Note Block](../blocks/NoteBlock.md)** selects the Creeper imitation sound when the Note Block is played or powered. Use the standing form; see the [sound guide and wall-form limitation](../blocks/HeadsAndSkulls.md#note-block-sounds). [Placement][pairing] · [Instrument registration][blocks] · [Selection and playback trigger][note]

Wear it in the inventory's **head equipment slot**. It halves the visibility factor used against a Creeper in the targeting calculation, without granting invisibility or immunity. Ordinary held use in the air does not quick-swap it onto your head. [Equipment registration][items] · [Slot acceptance][slot] · [Equipment check][equipment] · [Visibility][visibility] · [Targeting caller][targeting] · [Held-use rule][use]

Combine **one Creeper Head and one [Paper](Paper.md)**, shapeless, to craft **one Creeper Banner Pattern**. Crafting consumes the head, so reserve a separate one for wearing or display. [Recipe][recipe] · [Ingredient consumption][crafting]

## Behavior

The item places `minecraft:creeper_head` or `minecraft:creeper_wall_head`. Breaking either form normally returns **one Creeper Head**, including by hand; no Silk Touch is required. Its block loot preserves a supplied custom name. Block-drop game rules and later loss of the loose item still matter. [Block registrations][blocks] · [Wall loot alias][wall-loot] · [Harvest check][harvest] · [Loot][loot] · [Drop dispatch][drops]

The canonical [placement guide](../blocks/HeadsAndSkulls.md#placement-support-and-water) covers rotation, support removal, and water behavior.

## Notes

This is the inventory item for the `minecraft:creeper_head` block and its wall form. See [Creeper heads](../blocks/HeadsAndSkulls.md#creeper-heads) for the family overview.

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game charged-Creeper, equipment, visibility, placement, collection, sound, crafting, or inventory-browser test was run. Game rules, data packs, and supplied item data can change the result.

Related: [Heads and Skulls](../blocks/HeadsAndSkulls.md) · [Creeper](../mobs/Creeper.md) · [Creeper Banner Pattern](CreeperBannerPattern.md) · [Items](Items.md)

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
[reward]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/charged_creeper/creeper.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/creeper_head.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/creeper_banner_pattern.json
[crafting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L112
[explosion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L231-L240
