# Heartbreak Pottery Sherd

**Heartbreak Pottery Sherd** is a [Trail Ruins](../structures/TrailRuins.md) archaeology find used for the **heartbreak pattern** on a [Decorated Pot](../blocks/DecoratedPot.md). Its item-to-pattern mapping is present in MattMC. [Pattern map][patterns]

## Obtaining

Brush **[Suspicious Gravel](SuspiciousGravel.md) assigned the Trail Ruins rare table**. That table has one roll among 12 equally weighted entries, so the chance of one Heartbreak Pottery Sherd is **1/12, about 8.33%, per completed rare-table block**. The separate common table contains no Heartbreak Pottery Sherd. [Rare table][rare-loot] · [Common table][common-loot]

This is not the chance per ruin or per arbitrary Gravel block. Common and rare assignments initially look alike; follow [where rare finds can occur](../structures/TrailRuins.md#which-gravel-can-hold-rare-finds). Duplicate rewards are possible, and one site need not supply this sherd.

Keep the suspicious block **supported**, aim a serviceable [Brush](Brush.md#archaeology-basics) at it and hold use until its contents are released. Completion turns it into ordinary Gravel, ending that block's archaeology opportunity. Mining it, using Silk Touch or letting it fall does not recover the hidden find; see [suspicious-block handling](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel). [Brushing][brush-use] · [Completion][brush-finish]

The item is also listed in Creative. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) is visible in ordinary Survival, but its insertion requests are blocked in that mode. [Creative entry][creative]

## Usage

Use this sherd as one of the four face ingredients in a Decorated Pot recipe. For **one heartbreak face and three plain faces**, place the sherd in the bottom-center slot and a loose [Brick](Brick.md) in each of the top-center, middle-left and middle-right slots of a 3 × 3 crafting grid. Leave the center and corners empty. The sherd chooses the pot's front face. [Accepted ingredients][ingredients] · [Sherd tag][sherds] · [Recipe and face order][recipe]

Four copies make four heartbreak faces; mix sherds with mapped patterns to combine designs. See [crafting and choosing faces](../blocks/DecoratedPot.md#crafting-and-choosing-faces) for the shared layout and placement orientation. Using a sherd on an already placed pot inserts it into compatible storage rather than changing a decoration. [Pattern map][patterns] · [Placed-pot interaction][insert]

## Behavior

In ordinary Survival with block drops enabled, shattering a decorated pot returns **one Heartbreak Pottery Sherd for each heartbreak face** it contains. Use a pot-breaking tool such as a Pickaxe without Silk Touch. Breaking by hand, or with a tool carrying Silk Touch, instead keeps the pot and its decorations together. Stored contents are separate from face ingredients; see [pot recovery rules](../blocks/DecoratedPot.md#breaking-keep-the-pot-or-recover-ingredients). [Shattering and face drops][shatter] · [Pot loot][pot-loot]

## Notes

- This item is registered as `minecraft:heartbreak_pottery_sherd`. [Registration][items]
- Loot probabilities describe the unchanged bundled tables; custom data packs and world-specific structure overrides can alter acquisition
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game generation, excavation, loot-distribution, crafting, rendered-pattern or recovery test was run

Related: [Trail Ruins](../structures/TrailRuins.md) · [Brush](Brush.md) · [Decorated Pot item](DecoratedPot.md) · [Items](Items.md)

[rare-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_rare.json
[common-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_common.json
[brush-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BrushItem.java#L37-L98
[brush-finish]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L147
[ingredients]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/decorated_pot_ingredients.json
[sherds]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[patterns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java#L37-L71
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L95-L145
[shatter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/decorated_pot.json
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1905-L1930
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2585-L2612
