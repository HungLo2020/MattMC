# Diamond Axe

**Diamond Axe** (`minecraft:diamond_axe`) has **1,561 durability** and a **8.0 matching-block mining-speed value** before player modifiers. Its unbroken main-hand defaults give a normal player **9 attack damage** and **1.0 attack speed**. See [Axes and Hoes](../mechanics/AxesAndHoes.md#material-and-combat-choices) for how these attributes differ from actual hit damage. [Registration][registration] · [Material and attributes][materials] · [Player damage base][player-base] · [Speed base][speed-base]

## Obtaining

Use **3 Diamonds** and **2 [Sticks](Stick.md)** at a [Crafting Table](../blocks/CraftingTable.md) to make **1 Diamond Axe**. Place two head materials side by side in the top row, then a head material and Stick directly beneath them, with the second Stick below the first Stick. The mirrored pattern also works. [Recipe][recipe] · [Accepted material][repair-diamond] · [Pattern matching][pattern]

This tool is the base for a [Netherite Axe](NetheriteAxe.md#obtaining) upgrade.

## Uses and upkeep

Use it for [axe-tagged mining](../mechanics/AxesAndHoes.md#mining-targets-and-drops), [stripping supported wood](../mechanics/AxesAndHoes.md#stripping-wood-and-bamboo), and [scraping or unwaxing copper](../mechanics/AxesAndHoes.md#scraping-copper-and-removing-wax). Check the [offhand blocking-item rule](../mechanics/AxesAndHoes.md#when-axe-use-is-intercepted) if block conversion does not start. [Family properties][families] · [Axe callback][axe-use]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Diamond](Diamond.md). Its current family targets have no material-tier exclusions, but block loot conditions still apply. See the [shared mining explanation](../mechanics/AxesAndHoes.md#mining-targets-and-drops) before substituting it for another tool family. [Repair material][repair-diamond] · [Assigned properties][materials] · [Repair lookup][repair-check]

A fully damaged axe is retained for repair, but loses normal mining speed, correct-tool eligibility, and its guarded block-use conversions. See [Durability](../mechanics/Durability.md). [Retention][broken] · [Use and speed guards][use-guard] · [Drop guard][mine-guard]

Related: [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Mining](../mechanics/Mining.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, tool-use, harvesting, or repair test was run. The creation recipe and shared callbacks are source-checked; this page does not inventory trades, chest loot, or every acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1334
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/diamond_axe.json
[repair-diamond]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[families]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L439-L445
[axe-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L117
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[mine-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
