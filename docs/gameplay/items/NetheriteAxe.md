# Netherite Axe

**Netherite Axe** (`minecraft:netherite_axe`) has **2,031 durability** and a **9.0 matching-block mining-speed value** before player modifiers. Its unbroken main-hand defaults give a normal player **10 attack damage** and **1.0 attack speed**. See [Axes and Hoes](../mechanics/AxesAndHoes.md#material-and-combat-choices) for how these attributes differ from actual hit damage. [Registration][registration] · [Material and attributes][materials] · [Player damage base][player-base] · [Speed base][speed-base]

## Obtaining

At a [Smithing Table](../blocks/SmithingTable.md), use **1 [Netherite Upgrade Template](SmithingTemplateNetheriteUpgrade.md) + 1 [Diamond Axe](DiamondAxe.md) + 1 [Netherite Ingot](NetheriteIngot.md)** to receive **1 Netherite Axe**. Taking the result consumes one of each input. The addition tag contains only Netherite Ingot in the checked data. [Recipe][recipe] · [Addition tag][repair-netherite] · [Consumption][smith-menu] · [Output count][smith-result]

The upgrade preserves saved changes such as damage, enchantments, and a custom name; it does not reset damage to zero. See [Smithing](../smithing/Smithing.md) for the canonical preservation rules. [Transform callback][smith-transform] · [Result application][smith-apply] · [Saved changes][stack-patch]

## Uses and upkeep

Use it for [axe-tagged mining](../mechanics/AxesAndHoes.md#mining-targets-and-drops), [stripping supported wood](../mechanics/AxesAndHoes.md#stripping-wood-and-bamboo), and [scraping or unwaxing copper](../mechanics/AxesAndHoes.md#scraping-copper-and-removing-wax). Check the [offhand blocking-item rule](../mechanics/AxesAndHoes.md#when-axe-use-is-intercepted) if block conversion does not start. [Family properties][families] · [Axe callback][axe-use]

For [Anvil material repair](../mechanics/Durability.md#choose-a-repair-method), use [Netherite Ingot](NetheriteIngot.md). Its current family targets have no material-tier exclusions, but block loot conditions still apply. See the [shared mining explanation](../mechanics/AxesAndHoes.md#mining-targets-and-drops) before substituting it for another tool family. [Repair material][repair-netherite] · [Assigned properties][materials] · [Repair lookup][repair-check]

A fully damaged axe is retained for repair, but loses normal mining speed, correct-tool eligibility, and its guarded block-use conversions. See [Durability](../mechanics/Durability.md). [Retention][broken] · [Use and speed guards][use-guard] · [Drop guard][mine-guard]

Its dropped item resists the checked fire-damage category, including lava. This follows the registration through the item-entity damage check. [Registration][registration] · [Resistance property][fire-property] · [Stack check][fire-stack] · [Category match][fire-component] · [Fire types][fire-tag] · [Dropped-item callback][fire-entity]

Related: [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Mining](../mechanics/Mining.md) · [Durability and repair](../mechanics/Durability.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game crafting, combat, tool-use, harvesting, or repair test was run. The creation recipe and shared callbacks are source-checked; this page does not inventory trades, chest loot, or every acquisition route.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1343-L1345
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smithing/netherite_axe_smithing.json
[repair-netherite]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json
[smith-menu]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L70-L93
[smith-result]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L23
[smith-transform]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L33
[smith-apply]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
[stack-patch]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[families]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L439-L445
[axe-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L117
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[mine-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
[fire-property]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L402-L403
[fire-stack]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1103
[fire-component]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/DamageResistant.java#L12-L24
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[fire-entity]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L282
