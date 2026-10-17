# Loom

Loom (`minecraft:loom`) is the item for the Banner-pattern workstation and Shepherd job-site block. The [Loom guide](../blocks/Loom.md) explains its inputs, design selector, template sources, and normal six-layer limit. [Block behavior][loom-block] · [Shepherd job site][poi]

## Obtaining

Craft **two String above two plank-tag items** in a 2 × 2 grid to make **one Loom**. Mixed qualifying planks work; the current tag excludes Pewen Planks and Bamboo Mosaic. Hand mining returns the placed Loom, and an unbroken axe is faster. No Silk Touch is required. [Recipe][recipe-loom] · [Plank tag][planks] · [Properties][loom-registration] · [Axe tag][axe-tag] · [Loot][loot-loom]

## Usage

Place and open it, insert a Banner and a Dye, then choose a design. Taking one result uses one Banner and one Dye and returns a banner with the new layer appended. A template item unlocks its associated design and **is not consumed**. There are 32 ordinary designs and ten implemented template designs in the checked data. [Menu][loom-menu] · [Ordinary selector][basic-patterns] · [Templates][templates]

## Behavior

The Loom faces its placer and has hardness and blast resistance 2.5. Its inputs are temporary menu contents, returned when the menu closes; the block is not a storage container. Normal editing stops at six layers, while banner duplication has its own 1–6-layer rule. [Placement][loom-block] · [Properties][loom-registration] · [Menu lifetime and checks][loom-menu] · [Duplication][duplicate]

## Related pages

- [Loom](../blocks/Loom.md)
- [Banners](../blocks/Banners.md)
- [White Banner](WhiteBanner.md)
- [Field Masoned Banner Pattern](FieldMasonedBannerPattern.md)

## Sources and verification

Reviewed at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7` on 2026-10-02. Source and bundled-data review only; no in-game placement, weaving, duplication, washing, map-marker, shield-decoration, or loot test.

[loom-block]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/LoomBlock.java
[poi]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L131
[recipe-loom]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/loom.json
[planks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/planks.json
[loom-registration]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5306-L5310
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[loot-loom]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/loom.json
[loom-menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/LoomMenu.java
[basic-patterns]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/no_item_required.json
[templates]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L2387-L2424
[duplicate]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/BannerDuplicateRecipe.java
