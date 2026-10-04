# Mossy Cobblestone

## Obtaining

Combine **1 [Cobblestone](Cobblestone.md) + 1 [Moss Block](MossBlock.md) → 1 Mossy Cobblestone**, or use **1 Cobblestone + 1 [Vine](Vines.md) → 1 Mossy Cobblestone**. Both recipes are shapeless and fit the personal crafting grid. Moss Carpet and Pale Moss Block do not replace the named Moss Block ingredient. The [Stone family's mossy recipes](../blocks/Stone.md#mossy-variants) also distinguish Mossy Cobblestone from Mossy Stone Bricks. [Moss recipe] · [Vine recipe]

Mine a placed block with an **unbroken pickaxe**, including a Wooden Pickaxe, to collect **1 Mossy Cobblestone**. Hands, unsuitable tools and already-broken pickaxes do not collect it. Silk Touch is unnecessary and Fortune does not increase the drop. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] · [Tool rules] · [Wood restrictions]

The [family mining rules](../blocks/Stone.md#mining-and-drops) cover the shared collection conditions. Normal item drops require block drops to be enabled; explosion recovery has a separate survival condition. [Block drops] · [Drop table]

## Usage

Use Mossy Cobblestone as a weathered full building block, or make its matching [stairs](MossyCobblestoneStairs.md), [slabs](MossyCobblestoneSlab.md) and [walls](MossyCobblestoneWall.md):

- At a Crafting Table, **6 blocks → 4 stairs**, **3 blocks → 6 slabs**, or **6 blocks → 6 walls**. The [family crafting layouts](../blocks/Stone.md#crafting-yields) show the stair pattern, slab row and two wall rows. [Stair recipe] · [Slab recipe] · [Wall recipe]
- At a Stonecutter, **1 block → 1 stair, 2 slabs or 1 wall**. Choose one output per operation. Cutting saves material for stairs and permits smaller batches for the other shapes; see the [family cutting choices](../blocks/Stone.md#stonecutting-choices). [Cut stairs] · [Cut slabs] · [Cut wall] · [Cutter consumption]

Make the mossy full block before shaping it; the shape recipes name Mossy Cobblestone as their ingredient. [Stair recipe] · [Slab recipe] · [Wall recipe]

## Behavior

This item places the ordinary full Mossy Cobblestone block. See the [Stone block guide](../blocks/Stone.md#block-properties) for its material properties; the separate shaped items use the [stair, slab and wall placement rules](../blocks/Stone.md#placing-shaped-blocks). Mining this full block returns the same ordinary item under the collection rules above. [Block registration] · [Drop table]

## Notes

* This item is the item form of the `minecraft:mossy_cobblestone` block. [Item registration] · [Block registration]

Find Mossy Cobblestone by displayed name in the combined [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). It has a catalog entry, but ordinary Survival browsing does not supply items: accepted item requests require the server's infinite-materials ability, normally Creative mode. [Catalog entry] · [Combined catalog] · [Creative admission]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The cited registrations, bundled recipes, loot, tool rules, placement and browser admission were checked. No in-game crafting, stonecutting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L462-L462
[Block registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Blocks.java#L1185-L1188
[Moss recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_moss_block.json#L1-L13
[Vine recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_vine.json#L1-L13
[Drop table]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/loot_table/blocks/mossy_cobblestone.json#L1-L21
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L29-L29
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L259-L259
[Stair recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_stairs.json#L1-L16
[Slab recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_slab.json#L1-L14
[Wall recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_wall.json#L1-L15
[Cut stairs]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_stairs_from_mossy_cobblestone_stonecutting.json#L1-L8
[Cut slabs]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_slab_from_mossy_cobblestone_stonecutting.json#L1-L8
[Cut wall]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_wall_from_mossy_cobblestone_stonecutting.json#L1-L8
[Cutter consumption]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L61-L68
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Tool rules]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[Wood restrictions]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[Block drops]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
