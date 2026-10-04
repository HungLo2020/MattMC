# Dinosaur Pottery Sherd

**Dinosaur Pottery Sherd** is registered as `minecraft:dinosaur_pottery_sherd` and is an accepted Decorated Pot crafting ingredient. Its current visual behavior has a source limitation described below. [Item registration][custom-items] · [Sherd tag][sherds]

## Obtaining

It can be requested through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. This source audit did not establish a Survival archaeology loot route for this item; an archaeology source should not be assumed from its name. [Creative listing][custom-creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

### Catalog name

For default item stacks with the reviewed bundled English resources, search the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) for `dinosaur_pottery_sherd`, including the underscores. The source-resolved display name is `item.minecraft.dinosaur_pottery_sherd`; this page's readable title is not a bundled translation. Custom item names, another language or resource-pack translations can change that text. This name/search guidance is source-derived, without a running-client check. [Default item names][catalog-name-init] · [Hover name][catalog-hover-name] · [Missing-key fallback][catalog-name-fallback] · [Name search][catalog-name-search] · [Bundled English][catalog-english]

## Usage

Use it in one of the four cross-shaped ingredient positions for a [Decorated Pot](../blocks/DecoratedPot.md#crafting-and-choosing-faces). The selected face retains this sherd's item identity. You may mix it with Bricks or other accepted sherds. [Recipe][recipe] · [Stored decorations][decorations]

## Behavior

The current item-to-pattern map has no Dinosaur entry, so the inspected pot renderer uses its **plain-face fallback**. This does not turn the stored ingredient into a Brick: shattering the pot returns this sherd for that face. See the [pattern comparison](../blocks/DecoratedPot.md#accepted-sherds-and-current-patterns). [Pattern mapping][patterns] · [Material lookup][material-lookup] · [Fallback][pattern-fallback] · [Ingredient drops][shatter]

## Notes

- Item ID: `minecraft:dinosaur_pottery_sherd`
- Source-reviewed on 2026-10-02 at `8d065c943710a8d4e3d609b0406dda95ed62cc63`; no archaeology acquisition, crafting or rendered-pattern gameplay test was run

Related: [Decorated Pot](DecoratedPot.md) · [Items](Items.md)

[custom-items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L2608-L2609
[sherds]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[custom-creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1929-L1930
[recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[decorations]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/PotDecorations.java#L25-L66
[patterns]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java
[material-lookup]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/Sheets.java#L200-L203
[pattern-fallback]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java#L112-L121
[shatter]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1929
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108

[catalog-name-init]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L122-L125
[catalog-hover-name]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L792-L817
[catalog-name-fallback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/locale/Language.java#L110-L114
[catalog-name-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L115-L132
[catalog-english]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json
