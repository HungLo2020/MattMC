# Banners

Banners display a colored base and an ordered set of dyed pattern layers. Each of the **16 base colors** has standing and wall forms, giving **32 placed block IDs** and 16 shared inventory items. Use a [Loom](Loom.md) to add patterns; ordinary breaking preserves the banner's design and supported naming data. [Blocks][blocks] · [Items][items] · [Pattern data][banner-entity]

## Colors and crafted banners

All IDs below use the `minecraft:` namespace. One Banner item places either form of its color, and matching item stacks hold up to **16**. The wall form is not a separate inventory item. [Registration][items] · [Banner item][banner-item] · [Paired placement and item mapping][paired-item]

| Base color | Inventory / standing ID | Wall ID | Recipe and shared loot |
| --- | --- | --- | --- |
| [White](../items/WhiteBanner.md) | `white_banner` | `white_wall_banner` | [Recipe][recipe-white-banner] · [Loot][loot-white-banner] |
| [Orange](../items/OrangeBanner.md) | `orange_banner` | `orange_wall_banner` | [Recipe][recipe-orange-banner] · [Loot][loot-orange-banner] |
| [Magenta](../items/MagentaBanner.md) | `magenta_banner` | `magenta_wall_banner` | [Recipe][recipe-magenta-banner] · [Loot][loot-magenta-banner] |
| [Light Blue](../items/LightBlueBanner.md) | `light_blue_banner` | `light_blue_wall_banner` | [Recipe][recipe-light-blue-banner] · [Loot][loot-light-blue-banner] |
| [Yellow](../items/YellowBanner.md) | `yellow_banner` | `yellow_wall_banner` | [Recipe][recipe-yellow-banner] · [Loot][loot-yellow-banner] |
| [Lime](../items/LimeBanner.md) | `lime_banner` | `lime_wall_banner` | [Recipe][recipe-lime-banner] · [Loot][loot-lime-banner] |
| [Pink](../items/PinkBanner.md) | `pink_banner` | `pink_wall_banner` | [Recipe][recipe-pink-banner] · [Loot][loot-pink-banner] |
| [Gray](../items/GrayBanner.md) | `gray_banner` | `gray_wall_banner` | [Recipe][recipe-gray-banner] · [Loot][loot-gray-banner] |
| [Light Gray](../items/LightGrayBanner.md) | `light_gray_banner` | `light_gray_wall_banner` | [Recipe][recipe-light-gray-banner] · [Loot][loot-light-gray-banner] |
| [Cyan](../items/CyanBanner.md) | `cyan_banner` | `cyan_wall_banner` | [Recipe][recipe-cyan-banner] · [Loot][loot-cyan-banner] |
| [Purple](../items/PurpleBanner.md) | `purple_banner` | `purple_wall_banner` | [Recipe][recipe-purple-banner] · [Loot][loot-purple-banner] |
| [Blue](../items/BlueBanner.md) | `blue_banner` | `blue_wall_banner` | [Recipe][recipe-blue-banner] · [Loot][loot-blue-banner] |
| [Brown](../items/BrownBanner.md) | `brown_banner` | `brown_wall_banner` | [Recipe][recipe-brown-banner] · [Loot][loot-brown-banner] |
| [Green](../items/GreenBanner.md) | `green_banner` | `green_wall_banner` | [Recipe][recipe-green-banner] · [Loot][loot-green-banner] |
| [Red](../items/RedBanner.md) | `red_banner` | `red_wall_banner` | [Recipe][recipe-red-banner] · [Loot][loot-red-banner] |
| [Black](../items/BlackBanner.md) | `black_banner` | `black_wall_banner` | [Recipe][recipe-black-banner] · [Loot][loot-black-banner] |

At a crafting table, put **six Wool blocks of the same color in two full rows**, with **one Stick centered below**, to make **one Banner** of that base color. The recipes name the exact Wool item, so mixed colors are not interchangeable. See [Wool and Carpet](WoolAndCarpet.md) for making and obtaining colored Wool.

Choose the base color when crafting. A Loom adds a colored pattern layer; it does not replace the banner's underlying Wool color. The standing/wall ID stays tied to that base color even when its patterns cover most of the visible cloth. [Item color][banner-item] · [Loom result construction][loom-menu] · [Stored base color][banner-entity]

## Placement, support, and water

Use the Banner item on a solid block's top for the standing form, or against its side for the wall form. Standing banners choose among **16 horizontal rotations** from the placing player's view. Wall banners have four horizontal facings and require their support behind them. Both support tests use the neighboring block's `isSolid()` value, and losing the required support breaks the banner on that side's update. [Standing placement and support][standing] · [Wall placement and support][wall] · [Placement selection][paired-item]

The cloth does not make an entity barrier: all banner registrations disable entity collision. However, they also force solid-block behavior for other checks. **Banners are not waterloggable**, and the checked flowing-fluid gate rejects occupying their block space because it considers them solid. Do not generalize their empty entity collision into water passing through them, or use ordinary flowing water as a banner-collection method. This distinction follows the registered properties and active fluid gate. [Properties][blocks] · [Solid/collision behavior][behaviour] · [Fluid admission][flowing] · [Standing properties][standing] · [Wall properties][wall]

A banner has **one pattern collection**, not independent editable front/back designs. The renderer applies that same base and layer list to the flag model. [Banner data][banner-entity] · [Renderer][renderer] · [Flag model][flag]

## Mining, drops, and pattern persistence

All 32 placed banners have hardness and blast resistance **1**. None requires a correct tool for ordinary Survival drops: hand mining works, while an unbroken axe has the usual mining-speed bonus through the banner tag. Silk Touch is not required and Fortune does not add banners. [Properties][blocks] · [Axe membership][axe-tag] [banner-tag] · [Tool rules][tool] · [Harvest gate][harvest] · [Active harvest][harvest-call]

Each banner normally drops **one item of its base color**. Wall registrations share the matching standing loot table. These tables explicitly copy pattern layers, custom/item names, tooltip display settings, and rarity from the block entity. Patterns and names therefore survive normal mining and support-loss drops, unlike sign writing. Normal drops remain subject to `doTileDrops`, and explosions use a survives-explosion condition. The color table links all 16 loot tables. [Wall loot mapping][wall-loot] · [Example copied components][loot-white-banner] · [Support/drop dispatch][block]

Placement applies the item's components to the new Banner block entity, which saves its patterns and custom name. All 32 forms are included in the Banner block entity's valid-state registration. This is the source-backed round trip for moving an ordinary patterned banner; it is not a promise that every arbitrary command-added component is preserved by every operation. [Placement restoration][block-item] · [Save/load and components][banner-entity] · [Valid blocks][entity-type]

## Patterns and duplication

The [Loom guide](Loom.md#adding-a-pattern) explains banner, Dye, and optional template inputs. Normal Loom editing adds up to **six pattern layers**, in order. Base color does not count as one of those six. The display renderer can process more stored layers, so a special banner is not necessarily limited to what a player can make through the ordinary Loom interface. [Loom checks][loom-menu] [loom-screen] · [Pattern collection][layers] · [Rendering limit][renderer]

To copy a design, put **one patterned Banner with 1–6 layers** and **one blank Banner of the same base color** into a crafting grid, in any arrangement. The special recipe produces one copy and returns the patterned original as a crafting remainder. The blank is consumed, so you finish with the original plus its copy. This works in the inventory's 2 × 2 crafting grid as well as a crafting table. [Recipe registration][recipe-banner-duplicate] [recipe-serializers] · [Matching, result, and original return][duplicate] · [Crafting remainder dispatch][result-slot]

Two blank banners, two patterned banners, different base colors, extra ingredients, or a banner with **more than six layers** fail the duplication recipe. The copy is made from the patterned item's components, so a custom name on the blank is not the source of the copy's name. [Duplication implementation][duplicate]

The bundled Ominous Banner builder is a concrete **eight-layer** example. It uses the White Banner item with additional pattern/name/rarity components, rather than a seventeenth banner block color. Its full eight-layer design cannot be copied by the ordinary duplication recipe. [Ominous Banner construction][ominous] · [Duplication limit][duplicate]

## Washing and shield decoration

Use a held patterned Banner on a Water Cauldron to remove its **last pattern layer**, one layer and one water level per successful wash. The base color remains. See [Cauldron washing](Cauldrons.md#washing-equipment-banners-and-shulker-boxes) for stack handling and the different Creative result behavior; that guide owns the complete washing rules. [Active banner wash][washing] · [Last-layer removal][layers]

To decorate a Shield, combine **one Banner and one Shield whose banner-pattern list is empty** in a crafting grid. The recipe copies the Shield and adds the banner's base color and pattern list. It consumes the Banner through the normal crafting path; duplicate your design first if you want to keep a matching banner. [Registered recipe][recipe-shield-decoration] [recipe-serializers] · [Decoration result][shield] · [Normal remainders][craft-remainders] · [Crafting consumption][result-slot]

The matching condition is an empty pattern list, not a test that the Shield has never had a base color. A Shield with existing pattern layers fails this recipe. Decoration does not add a new combat bonus; use the existing [Shield guide](../items/Shield.md#banner-decoration) for decoration and defensive behavior. [Matching conditions][shield]

## Marking a map

Use a **filled Map** on a placed banner to toggle its marker. The marker uses the banner's **base color** and optional custom name; its detailed pattern layers do not become a custom map icon. An [Anvil](../mechanics/AnvilMechanics.md)-named banner can therefore label a location. Both standing and wall forms belong to the banner tag checked by Map use. [Map item interaction][map] · [Marker identity, color, and name][map-banner] · [Banner tag][banner-tag]

The banner must be within the map's accepted bounds: its position relative to the map center, divided by `2^scale`, must lie between **−63 and +63 on both horizontal axes**. Adding a new marker is also subject to the map's shared **256 tracked-decoration** limit. This is not an allowance for 256 additional banners on top of other counted markers. Using the same map on the same unchanged banner again removes its marker. If its name/color has changed, the stored entry can instead be replaced with the new marker data. [Bounds, toggle, and limit][map-data]

Breaking a banner does not instantly delete its marker from every map. The map's terrain-update path checks banner columns it visits and removes markers whose recorded banner no longer matches. Locked maps skip that ordinary terrain update, so immediate cleanup should not be assumed. Marker records are saved with the map data. [Active update and lock checks][map] · [Banner recheck and saved data][map-data]

## A small matching design

For a source-based, **untested** example, craft two White Banners. In a Loom, add a red center stripe to one, take the result, then put it back with Black Dye and add a border. Both designs are in the ordinary selector and need no template. Combine that two-layer banner with the blank White Banner to make a matching copy while keeping the original. Place one as a marker, and use the other for a Shield if desired. Use a filled Map covering the placed banner to add its white base-color icon. [Loom](Loom.md) · [White Banner](../items/WhiteBanner.md) · [Shield](../items/Shield.md) · [Map](../items/Map.md)

## Sources and verification

Reviewed at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7` on 2026-10-02. Source and bundled-data review only; no in-game placement, weaving, duplication, washing, map-marker, shield-decoration, or loot test.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3360-L3743
[items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L2149-L2228
[banner-entity]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BannerBlockEntity.java
[banner-item]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BannerItem.java
[paired-item]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java
[recipe-white-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/white_banner.json
[loot-white-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/white_banner.json
[recipe-orange-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/orange_banner.json
[loot-orange-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/orange_banner.json
[recipe-magenta-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/magenta_banner.json
[loot-magenta-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/magenta_banner.json
[recipe-light-blue-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/light_blue_banner.json
[loot-light-blue-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/light_blue_banner.json
[recipe-yellow-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/yellow_banner.json
[loot-yellow-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/yellow_banner.json
[recipe-lime-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/lime_banner.json
[loot-lime-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/lime_banner.json
[recipe-pink-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/pink_banner.json
[loot-pink-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/pink_banner.json
[recipe-gray-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/gray_banner.json
[loot-gray-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/gray_banner.json
[recipe-light-gray-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/light_gray_banner.json
[loot-light-gray-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/light_gray_banner.json
[recipe-cyan-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/cyan_banner.json
[loot-cyan-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/cyan_banner.json
[recipe-purple-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/purple_banner.json
[loot-purple-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/purple_banner.json
[recipe-blue-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/blue_banner.json
[loot-blue-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/blue_banner.json
[recipe-brown-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/brown_banner.json
[loot-brown-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/brown_banner.json
[recipe-green-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/green_banner.json
[loot-green-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/green_banner.json
[recipe-red-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/red_banner.json
[loot-red-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/red_banner.json
[recipe-black-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/black_banner.json
[loot-black-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/black_banner.json
[loom-menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/LoomMenu.java
[standing]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/BannerBlock.java
[wall]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/WallBannerBlock.java
[behaviour]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[flowing]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L400-L426
[renderer]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/renderer/blockentity/BannerRenderer.java
[flag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/model/BannerFlagModel.java
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[banner-tag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/banners.json
[tool]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/ToolMaterial.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L282-L297
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[block]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Block.java
[block-item]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BlockItem.java
[entity-type]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L133-L168
[loom-screen]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/gui/screens/inventory/LoomScreen.java
[layers]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BannerPatternLayers.java
[recipe-banner-duplicate]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/banner_duplicate.json
[recipe-serializers]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L22-L27
[duplicate]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/BannerDuplicateRecipe.java
[result-slot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/ResultSlot.java
[ominous]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/raid/Raid.java#L611-L629
[washing]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[recipe-shield-decoration]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/shield_decoration.json
[shield]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/ShieldDecorationRecipe.java
[craft-remainders]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java
[map]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/MapItem.java
[map-banner]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/saveddata/maps/MapBanner.java
[map-data]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java
