# Cobweb

Cobweb is the ordinary block item for `minecraft:cobweb`. The [Cobweb block guide](../blocks/Cobweb.md) owns its harvesting, slowing, fall, fluid and fire behavior. [Item registration][web-item] · [Block-item registration helper][item-helper]

## Obtaining

Use **Shears** to collect **1 Cobweb** from a placed web. An ordinary sword produces **1 String** instead; mining by hand does not pass the correct-tool gate. Silk Touch is a conditional loot branch, not a substitute for that gate. See [collecting Cobwebs](../blocks/Cobweb.md#collecting-cobwebs) and [verified placed sources](../blocks/Cobweb.md#finding-and-making-placed-cobwebs). [Loot][web-loot] · [Tool gate][tool-gate] · [Shears rule][shears] · [Sword rule][sword]

It is listed in **Natural Blocks** and can be requested through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. This is separate from harvesting; the bundled recipe data has no crafting recipe producing Cobweb. [Category][natural-category] · [Entry][web-category]

## Usage

Place it to create a Cobweb, or brew an **Awkward Potion with Cobweb** for a **Potion of Weaving**. [Slowing and collision](../blocks/Cobweb.md#slowing-collision-and-falling) · [Brewing use](../blocks/Cobweb.md#brewing-use) · [Brewing registration][brewing] · [Start-mix inputs][start-mix]

## Behavior

Placed webs slow many entities without acting as solid walls. Spiders, Cave Spiders, active player flight and the Wither have relevant exceptions. Flowing Water can replace the web and drop String. Use the block guide for the [movement exceptions](../blocks/Cobweb.md#slowing-collision-and-falling) and [Water/fire rules](../blocks/Cobweb.md#water-and-fire).

## Notes

- Item and block ID: `minecraft:cobweb`
- The [canonical guide's verification](../blocks/Cobweb.md#sources-and-verification) records the source review and runtime limits at `b823010659d7b5095ed021b1c99cf85627e2082a`

[web-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L287-L287
[item-helper]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2750-L2781
[web-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/cobweb.json#L1-L58
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[shears]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L44
[sword]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[natural-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L751-L759
[web-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1021-L1029
[brewing]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L128-L145
[start-mix]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L234
