# Banner Pattern (Field Masoned)

Field Masoned Banner Pattern (`minecraft:field_masoned_banner_pattern`) unlocks the **bricks** design in a Loom. It is an implemented template in this revision, with a registered component, matching tag, and `minecraft:bricks` banner-pattern resource. It stacks to **one**. [Item registration][templates] · [Pattern tag][tag-field-masoned] · [Pattern resource][pattern-bricks]

## Obtaining

Combine **one Paper and one Bricks block** in any arrangement to craft **one Field Masoned Banner Pattern**. This two-ingredient shapeless recipe fits the inventory crafting grid. It uses the Bricks block, not the small Brick item. [Recipe][recipe-field-masoned-banner-pattern]

## Usage

Put a Banner, a Dye, and this template into a [Loom](../blocks/Loom.md#adding-a-pattern). The template's tag selects the brickwork design; the Dye supplies its color. Taking the result uses one Banner and one Dye, while the template remains for reuse. The normal six-layer editing limit still applies. [Menu lookup and result handling][loom-menu] · [Visible limit][loom-screen]

The design is not included in the 32 template-free options. For removing its most recent layer or copying a finished banner, use the [Banner guide](../blocks/Banners.md#patterns-and-duplication). [Ordinary selector][basic-patterns]

## Related pages

- [All Loom templates](../blocks/Loom.md#available-designs-and-templates)
- [Bricks](Bricks.md), [Paper](Paper.md)
- [White Banner](WhiteBanner.md)

## Sources and verification

Reviewed at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7` on 2026-10-02. Source and bundled-data review only; no in-game placement, weaving, duplication, washing, map-marker, shield-decoration, or loot test.

[templates]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L2387-L2424
[tag-field-masoned]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/field_masoned.json
[pattern-bricks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/banner_pattern/bricks.json
[recipe-field-masoned-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/field_masoned_banner_pattern.json
[loom-menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/LoomMenu.java
[loom-screen]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/gui/screens/inventory/LoomScreen.java
[basic-patterns]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/no_item_required.json
