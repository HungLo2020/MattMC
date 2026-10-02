# Netherite Sword

**Netherite Sword** (`minecraft:netherite_sword`) has **2,031 durability**. Its unbroken main-hand defaults give a normal player **8 attack damage** and **1.6 attack speed**, before enchantments and other modifiers. See [Swords](../mechanics/Swords.md) for attack charge, criticals, sprint knockback, and sweeping. [Registration][registration] · [Material values][materials] · [Sword modifiers][sword-properties] · [Player damage base][player-base] · [Speed base][speed-base]

## Obtaining

This ordinary listed sword also has the separate [inventory-browser](../mechanics/InventoryBrowser.md) insertion route in Survival and Creative. [Category entries](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1584-L1591)

At a [Smithing Table](../blocks/SmithingTable.md), combine **1 [Netherite Upgrade Template](SmithingTemplateNetheriteUpgrade.md) + 1 [Diamond Sword](DiamondSword.md) + 1 [Netherite Ingot](NetheriteIngot.md)** to receive **1 Netherite Sword**. Taking the result consumes one of each input. The addition tag contains only Netherite Ingot in the checked data. [Recipe][recipe] · [Addition tag][repair-netherite] · [Consumption][smith-menu] · [Output count][smith-result]

The upgrade preserves saved changes such as damage, a custom name, and enchantments. It does not reset damage to zero. See [Smithing](../smithing/Smithing.md) for the canonical component rules. [Transform callback][smith-transform] · [Result application][smith-apply] · [Component transfer][stack-patch]

<span id="usage"></span>
<span id="behavior"></span>

## Uses and upkeep

It uses the shared [sword attack branches](../mechanics/Swords.md#attack-charge-and-movement) and [fixed mining rules](../mechanics/Swords.md#cobwebs-and-other-mining-uses). In particular, an unbroken ordinary sword can collect String from Cobwebs and instantly cut the tagged Bamboo blocks. Its material does not change those mining speeds. [Sword properties][sword-properties] · [Sweep membership][sword-tag]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Netherite Ingot](NetheriteIngot.md). Ordinary wear retains a fully damaged stack, but it loses normal sword combat/mining behavior and cannot sweep. See [sword upkeep](../mechanics/Swords.md#enchanting-and-upkeep), including the checked Mending route. [Repair material][repair-netherite] · [Assigned repair component][materials] · [Repair lookup][repair-check] · [Retention][broken] · [Mining guards][mine-guard] · [Sweep guard][sweep]

The dropped sword resists the checked fire-damage category, including lava, through its registered damage-resistance property. [Registration][registration] · [Property][fire-property] · [Stack check][fire-stack] · [Category match][fire-component] · [Fire types][fire-tag] · [Dropped-item callback][fire-entity]

Related: [Swords](../mechanics/Swords.md) · [Enchanting](../enchanting/Enchanting.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, mining, enchanting, or repair test was run. The creation recipe and shared behavior are source-checked; this page does not inventory trades, chest loot, or every acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1336
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[sword-properties]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L90
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smithing/netherite_sword_smithing.json
[repair-netherite]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[smith-menu]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L70-L93
[smith-result]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L23
[smith-transform]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L33
[smith-apply]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
[stack-patch]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/swords.json
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[mine-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
[sweep]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1015-L1064
[fire-property]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L402-L403
[fire-stack]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1103
[fire-component]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/DamageResistant.java#L12-L24
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[fire-entity]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L282
