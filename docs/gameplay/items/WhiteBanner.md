# White Banner

White Banner (`minecraft:white_banner`) is the shared item for white standing and wall banners. It stacks to **16** and starts with a white base and no pattern layers. See [Banners](../blocks/Banners.md) for all colors and placed behavior. [Item registration][items] · [Paired item][banner-item]

## Obtaining

At a crafting table, put **six White Wool in two full rows** and **one Stick centered below** to make **one White Banner**. Hand mining collects a placed banner; an unbroken axe is faster. Its ordinary loot preserves its pattern layers and supported naming components without Silk Touch. [Recipe][recipe-white-banner] · [Loot][loot-white-banner] · [Tool tag][axe-tag] · [Properties][blocks]

## Usage

Place it on top of a solid support or against a solid side. Use a [Loom](../blocks/Loom.md) with Dye to add patterns. A 1–6-layer White Banner can be copied with one blank White Banner; the original is returned and the blank becomes the copy. [Standing][standing] · [Wall][wall] · [Duplication][duplicate]

The white base remains even when patterns cover it. A filled Map marker uses that white base and any custom name, not the detailed design. A Banner can also decorate a compatible Shield; see [map markers](../blocks/Banners.md#marking-a-map) and [Shield decoration](Shield.md#banner-decoration). [Marker data][map-banner] · [Shield recipe][shield]

## Behavior

Removing its support breaks the banner. Patterns survive the ordinary drop and placement round trip. Water Cauldron washing removes the last pattern while leaving the white base; follow the [Cauldron washing guide](../blocks/Cauldrons.md#washing-equipment-banners-and-shulker-boxes). Banners have no waterlogged state. [Support][standing] [wall] · [Saved patterns][banner-entity] · [Wash callback][washing]

The eight-layer Ominous Banner is a configured White Banner item, not a separate base-color block. Its full pattern list exceeds the ordinary duplication limit. [Ominous construction][ominous] · [Copy limit][duplicate]

## Sources and verification

Reviewed at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7` on 2026-10-02. Source and bundled-data review only; no in-game placement, weaving, duplication, washing, map-marker, shield-decoration, or loot test.

[items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L2149-L2228
[banner-item]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BannerItem.java
[recipe-white-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/white_banner.json
[loot-white-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/white_banner.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3360-L3743
[standing]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/BannerBlock.java
[wall]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/WallBannerBlock.java
[duplicate]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/BannerDuplicateRecipe.java
[map-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/saveddata/maps/MapBanner.java
[shield]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/ShieldDecorationRecipe.java
[banner-entity]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BannerBlockEntity.java
[washing]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[ominous]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/raid/Raid.java#L611-L629
