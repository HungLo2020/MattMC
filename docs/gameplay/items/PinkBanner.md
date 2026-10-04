# Pink Banner

Pink Banner (`minecraft:pink_banner`) is the shared inventory item for the pink standing banner and `minecraft:pink_wall_banner` wall form. Matching stacks hold up to **16**. See [banner colors and item forms](../blocks/Banners.md#colors-and-crafted-banners). [Item registration][item] · [Standing form][standing] · [Wall form][wall]

## Obtaining

At a crafting table, fill the top two rows with **six [Pink Wool](PinkWool.md)** (`minecraft:pink_wool`), then put **one [Stick](Stick.md)** in the bottom-center slot. This makes **one blank Pink Banner**. Every Wool slot requires Pink Wool; other colors do not substitute. [Exact recipe][recipe]

Ordinary Survival mining collects a placed pink banner by hand, without Silk Touch. Either placed form returns the same item, with its existing design rather than a newly blank banner; see [collection and pattern retention](../blocks/Banners.md#mining-drops-and-pattern-persistence). [Harvest path][harvest] · [Loot][loot]

## Usage

Place it on top of a solid support for a standing banner, or against a solid support's side for the wall form. See [placement and support](../blocks/Banners.md#placement-support-and-water) for facing and support-loss behavior.

Use a [Loom](../blocks/Loom.md#adding-a-pattern) to add dyed pattern layers. This keeps the **pink base color**, even when the design covers it. [Pattern result][loom]

## Behavior

Normal mining and ordinary support loss preserve the banner's pattern layers and supported naming data. Its loot explicitly copies **patterns, custom name, item name, tooltip display, and rarity** from the block entity; placement reapplies the carried components. This does not promise retention of every arbitrary added component. [Loot][loot] · [Banner data][components] · [Placement][placement]

Ordinary item drops require `doTileDrops`; the loot also checks explosion survival. Follow the [shared drop rules](../blocks/Banners.md#mining-drops-and-pattern-persistence) when moving a decorated banner. [Drop dispatch][drop-rules] · [Loot][loot]

## Notes

- To [copy a design](../blocks/Banners.md#patterns-and-duplication), combine a Pink Banner with **1–6 pattern layers** and one **blank Pink Banner**. The original is returned; a different base color cannot be the blank. The copy takes its components from the patterned original. [Duplication][duplicate]
- The shared guide covers [Cauldron washing and Shield decoration](../blocks/Banners.md#washing-and-shield-decoration) and [marking a Map](../blocks/Banners.md#marking-a-map).

## Sources and verification

Reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Source and bundled-data review only; no in-game crafting, placement, pattern, or loot test.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2179-L2183
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pink_banner.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pink_banner.json
[standing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3432-L3443
[wall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3624-L3635
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L296
[components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BannerBlockEntity.java#L81-L103
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L103
[drop-rules]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L345-L415
[loom]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/LoomMenu.java#L261-L277
[duplicate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/BannerDuplicateRecipe.java#L17-L94
