# Stone Hoe

**Stone Hoe** (`minecraft:stone_hoe`) has **131 durability** and a **4.0 matching-block mining-speed value** before player modifiers. Its unbroken main-hand defaults give a normal player **1 attack damage** and **2.0 attack speed**. See [Axes and Hoes](../mechanics/AxesAndHoes.md#material-and-combat-choices) for how these attributes differ from actual hit damage. [Registration][registration] · [Material and attributes][materials] · [Player damage base][player-base] · [Speed base][speed-base]

## Obtaining

Use **2 blocks chosen from Cobblestone, Blackstone, and Cobbled Deepslate** and **2 [Sticks](Stick.md)** at a [Crafting Table](../blocks/CraftingTable.md) to make **1 Stone Hoe**. Place two head materials side by side in the top row and the two Sticks vertically beneath one end. The mirrored pattern also works. [Recipe][recipe] · [Accepted material][repair-stone] · [Pattern matching][pattern]

The head slots can mix those three accepted blocks. Ordinary Stone is not in this ingredient tag. [Accepted blocks][repair-stone] · [Slot matching][pattern]

## Uses and upkeep

Use it for [hoe-tagged mining](../mechanics/AxesAndHoes.md#mining-targets-and-drops), [soil conversion](../mechanics/AxesAndHoes.md#tilling-with-a-hoe), and [MattMC crop-area harvesting](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting). Its material does not change the 3 × 3 harvest footprint. [Hoe callback][hoe] · [Crop callback][crop]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Cobblestone](Cobblestone.md), [Blackstone](Blackstone.md), or [Cobbled Deepslate](CobbledDeepslate.md). Its current family targets have no material-tier exclusions, but block loot conditions still apply. See the [shared mining explanation](../mechanics/AxesAndHoes.md#mining-targets-and-drops) before substituting it for another tool family. [Repair material][repair-stone] · [Assigned properties][materials] · [Repair lookup][repair-check]

A fully damaged hoe is retained. It loses normal tool speed and cannot till, but the current block-side crop handler still accepts it for [ordinary area harvesting](../mechanics/AxesAndHoes.md#fully-damaged-hoes-still-reach-this-harvest-path). This verified exception does not make every broken-tool action work. [Use guard][use-guard] · [Retention and wear][broken] · [Crop condition][crop] · [Interaction order][server-use]

Related: [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Mining](../mechanics/Mining.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, tool-use, harvesting, or repair test was run. The creation recipe and shared callbacks are source-checked; this page does not inventory trades, chest loot, or every acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1320
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_hoe.json
[repair-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[hoe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/HoeItem.java#L23-L88
[crop]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/CropBlock.java#L208-L250
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[server-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L387
