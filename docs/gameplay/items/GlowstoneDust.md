# Glowstone Dust

Glowstone Dust (`minecraft:glowstone_dust`) is a crafting and brewing ingredient with no block-placement action. The placeable [Glowstone block](Glowstone.md) is a separate item. [Registration][item] · [Item factory][factory] · [Use handler][use]

## Obtaining

Mine a Glowstone block without Silk Touch for **2–4 Dust before Fortune**. Fortune can raise the result, capped at four; Silk Touch selects the intact block instead. See [Glowstone harvesting](../blocks/LuminousBlocks.md#glowstone) and [finding Glowstone](../blocks/LuminousBlocks.md#finding-glowstone). [Loot][loot]

The ordinary [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can also provide this item in Creative, subject to its cursor, space, and feature checks. [Listing][listing]

## Usage

Craft four Dust in a 2 × 2 square into **one Glowstone block**. This conversion does not guarantee recovering all four Dust from a later harvest. [Recipe][recipe]

In a Brewing Stand, Dust changes Healing into **Healing II** and Swiftness into **Swiftness II**. Follow the [registered potion combinations](../brewing/Brewing.md#extending-strengthening-and-changing-potions); it does not strengthen every potion. [Brewing mappings][brew]

## Behavior

Adding Dust directly to Water Bottles produces **Thick Potion**, an effect-free base. Start with the required potion when seeking an enhanced effect. [Brewing mappings][brew] · [Thick definition][thick] · [Effect list][effect-list]

## Notes

Source-reviewed at `9363264a1615b1c23931f39e1189cda0cc088490` on 2026-10-02. Selected bundled routes are described here; data packs can change recipes, tags, and loot. No gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L1666-L1666
[factory]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L2792-L2797
[use]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Item.java#L164-L196
[listing]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1881
[loot]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/loot_table/blocks/glowstone.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/glowstone.json
[brew]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[thick]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L11-L14
[effect-list]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/alchemy/Potion.java#L24-L27
