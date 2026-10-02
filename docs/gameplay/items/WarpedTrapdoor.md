# Warped Trapdoor

`minecraft:warped_trapdoor` places a **single-block hinged panel**. Its top/bottom setting chooses the panel's position within that block space. [Item binding][item] · [Block type][block]

## Obtaining

Use Warped Planks in the [shared trapdoor crafting layout](../blocks/WoodConstruction.md#crafting-construction-shapes); the recipe requires this exact plank material. [Recipe][recipe]

This ordinary [inventory-browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) entry is also available in Survival as well as Creative, subject to the browser's limits. [Category listing][listing]

## Usage

It opens by hand or redstone. The [Trapdoor guide](../blocks/WoodConstruction.md#trapdoors) explains placement and attachment, and [power rules](../blocks/WoodConstruction.md#waterlogging-and-power) explain later signal changes.

## Behavior

It can be waterlogged. Normal hand mining returns **one matching trapdoor**, with no Silk Touch requirement; explosion recovery is conditional. See [collection rules](../blocks/WoodConstruction.md#mining-and-drops). [Exact loot][loot]

## Notes

Warped Trapdoor is not a default furnace fuel. See the [material-specific fire and fuel rules](../blocks/WoodConstruction.md#fire-and-furnace-fuel).

Item binding, recipe, listing, and ordinary loot reviewed at `1d7e3e91f2a2694339f78b8673993e98d496ca5f` on 2026-10-02. Shared behavior remains in the linked family guide; no gameplay test.

[item]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/Items.java#L1101-L1101
[block]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/Blocks.java#L5658-L5667
[recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/warped_trapdoor.json
[listing]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L233
[loot]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/blocks/warped_trapdoor.json
