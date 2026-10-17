# Diamond Sword

**Diamond Sword** (`minecraft:diamond_sword`) has **1,561 durability**. Its unbroken main-hand defaults give a normal player **7 attack damage** and **1.6 attack speed**, before enchantments and other modifiers. See [Swords](../mechanics/Swords.md) for attack charge, criticals, sprint knockback, and sweeping. [Registration][registration] · [Material values][materials] · [Sword modifiers][sword-properties] · [Player damage base][player-base] · [Speed base][speed-base]

## Obtaining

This ordinary listed sword also has the separate [inventory-browser](../mechanics/InventoryBrowser.md) insertion route in Creative. [Category entries](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1584-L1591)

At a [Crafting Table](../blocks/CraftingTable.md), place **2 Diamonds** vertically above **1 [Stick](Stick.md)** in one three-slot column. This makes **1 Diamond Sword**. The three-row layout does not fit the inventory crafting grid. [Recipe][recipe] · [Accepted blade material][repair-diamond]

This is the base for the [Netherite Sword upgrade](NetheriteSword.md#obtaining).

<span id="usage"></span>
<span id="behavior"></span>

## Uses and upkeep

It uses the shared [sword attack branches](../mechanics/Swords.md#attack-charge-and-movement) and [fixed mining rules](../mechanics/Swords.md#cobwebs-and-other-mining-uses). In particular, an unbroken ordinary sword can collect String from Cobwebs and instantly cut the tagged Bamboo blocks. Its material does not change those mining speeds. [Sword properties][sword-properties] · [Sweep membership][sword-tag]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Diamond](Diamond.md). Ordinary wear retains a fully damaged stack, but it loses normal sword combat/mining behavior and cannot sweep. See [sword upkeep](../mechanics/Swords.md#enchanting-and-upkeep), including the checked Mending route. [Repair material][repair-diamond] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Retention][broken] · [Mining guards][mine-guard] · [Sweep guard][sweep]

Related: [Swords](../mechanics/Swords.md) · [Enchanting](../enchanting/Enchanting.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, mining, enchanting, or repair test was run. The creation recipe and shared behavior are source-checked; this page does not inventory trades, chest loot, or every acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1331
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[sword-properties]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L90
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/diamond_sword.json
[repair-diamond]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/swords.json
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[mine-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
[sweep]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1015-L1064
