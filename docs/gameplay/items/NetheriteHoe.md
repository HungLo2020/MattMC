# Netherite Hoe

**Netherite Hoe** (`minecraft:netherite_hoe`) has **2,031 durability** and a **9.0 matching-block mining-speed value** before player modifiers. Its unbroken main-hand defaults give a normal player **1 attack damage** and **4.0 attack speed**. See [Axes and Hoes](../mechanics/AxesAndHoes.md#material-and-combat-choices) for how these attributes differ from actual hit damage. [Registration][registration] · [Material and attributes][materials] · [Player damage base][player-base] · [Speed base][speed-base]

## Obtaining

At a [Smithing Table](../blocks/SmithingTable.md), use **1 [Netherite Upgrade Template](SmithingTemplateNetheriteUpgrade.md) + 1 [Diamond Hoe](DiamondHoe.md) + 1 [Netherite Ingot](NetheriteIngot.md)** to receive **1 Netherite Hoe**. Taking the result consumes one of each input. The addition tag contains only Netherite Ingot in the checked data. [Recipe][recipe] · [Addition tag][repair-netherite] · [Consumption][smith-menu] · [Output count][smith-result]

The upgrade preserves saved changes such as damage, enchantments, and a custom name; it does not reset damage to zero. See [Smithing](../smithing/Smithing.md) for the canonical preservation rules. [Transform callback][smith-transform] · [Result application][smith-apply] · [Saved changes][stack-patch]

## Uses and upkeep

Use it for [hoe-tagged mining](../mechanics/AxesAndHoes.md#mining-targets-and-drops), [soil conversion](../mechanics/AxesAndHoes.md#tilling-with-a-hoe), and [MattMC crop-area harvesting](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting). Its material does not change the 3 × 3 harvest footprint. [Hoe callback][hoe] · [Crop callback][crop]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Netherite Ingot](NetheriteIngot.md). Its current family targets have no material-tier exclusions, but block loot conditions still apply. See the [shared mining explanation](../mechanics/AxesAndHoes.md#mining-targets-and-drops) before substituting it for another tool family. [Repair material][repair-netherite] · [Assigned properties][materials] · [Repair lookup][repair-check]

A fully damaged hoe is retained. It loses normal tool speed and cannot till, but the current block-side crop handler still accepts it for [ordinary area harvesting](../mechanics/AxesAndHoes.md#fully-damaged-hoes-still-reach-this-harvest-path). This verified exception does not make every broken-tool action work. [Use guard][use-guard] · [Retention and wear][broken] · [Crop condition][crop] · [Interaction order][server-use]

Its dropped item resists the checked fire-damage category, including lava. This follows the registration through the item-entity damage check. [Registration][registration] · [Resistance property][fire-property] · [Stack check][fire-stack] · [Category match][fire-component] · [Fire types][fire-tag] · [Dropped-item callback][fire-entity]

Related: [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Mining](../mechanics/Mining.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, tool-use, harvesting, or repair test was run. The creation recipe and shared callbacks are source-checked; this page does not inventory trades, chest loot, or every acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1346-L1348
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smithing/netherite_hoe_smithing.json
[repair-netherite]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[smith-menu]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L70-L93
[smith-result]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L23
[smith-transform]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L33
[smith-apply]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
[stack-patch]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[hoe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/HoeItem.java#L23-L88
[crop]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/CropBlock.java#L208-L250
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[server-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L387
[fire-property]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L402-L403
[fire-stack]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1103
[fire-component]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/DamageResistant.java#L12-L24
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[fire-entity]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L282
