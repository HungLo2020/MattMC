# Glass and Glass Panes

**Glass** makes full-block windows; **glass panes** make thin connected windows. Ordinary and all 16 stained colors let light through, but moving either form requires **Silk Touch** to recover the item. **Tinted Glass** is the separate light-blocking exception and can be collected without Silk Touch. [Ordinary registration][glass-block] · [Stained-glass registration][stained-register] · [Pane registration][pane-register] · [Tinted behavior][tinted] · [Glass loot][glass-loot] · [Tinted loot][tinted-loot]

## Forms and colors

The block and its item share the listed ID. Each linked item page gives its exact recipe and collection rule. [Item registrations][items]

| Color | Full glass block and item | Pane block and item |
| --- | --- | --- |
| Ordinary, uncolored | [`minecraft:glass`](../items/Glass.md) | [`minecraft:glass_pane`](../items/GlassPane.md) |
| White | [`minecraft:white_stained_glass`](../items/WhiteStainedGlass.md) | [`minecraft:white_stained_glass_pane`](../items/WhiteStainedGlassPane.md) |
| Orange | [`minecraft:orange_stained_glass`](../items/OrangeStainedGlass.md) | [`minecraft:orange_stained_glass_pane`](../items/OrangeStainedGlassPane.md) |
| Magenta | [`minecraft:magenta_stained_glass`](../items/MagentaStainedGlass.md) | [`minecraft:magenta_stained_glass_pane`](../items/MagentaStainedGlassPane.md) |
| Light Blue | [`minecraft:light_blue_stained_glass`](../items/LightBlueStainedGlass.md) | [`minecraft:light_blue_stained_glass_pane`](../items/LightBlueStainedGlassPane.md) |
| Yellow | [`minecraft:yellow_stained_glass`](../items/YellowStainedGlass.md) | [`minecraft:yellow_stained_glass_pane`](../items/YellowStainedGlassPane.md) |
| Lime | [`minecraft:lime_stained_glass`](../items/LimeStainedGlass.md) | [`minecraft:lime_stained_glass_pane`](../items/LimeStainedGlassPane.md) |
| Pink | [`minecraft:pink_stained_glass`](../items/PinkStainedGlass.md) | [`minecraft:pink_stained_glass_pane`](../items/PinkStainedGlassPane.md) |
| Gray | [`minecraft:gray_stained_glass`](../items/GrayStainedGlass.md) | [`minecraft:gray_stained_glass_pane`](../items/GrayStainedGlassPane.md) |
| Light Gray | [`minecraft:light_gray_stained_glass`](../items/LightGrayStainedGlass.md) | [`minecraft:light_gray_stained_glass_pane`](../items/LightGrayStainedGlassPane.md) |
| Cyan | [`minecraft:cyan_stained_glass`](../items/CyanStainedGlass.md) | [`minecraft:cyan_stained_glass_pane`](../items/CyanStainedGlassPane.md) |
| Purple | [`minecraft:purple_stained_glass`](../items/PurpleStainedGlass.md) | [`minecraft:purple_stained_glass_pane`](../items/PurpleStainedGlassPane.md) |
| Blue | [`minecraft:blue_stained_glass`](../items/BlueStainedGlass.md) | [`minecraft:blue_stained_glass_pane`](../items/BlueStainedGlassPane.md) |
| Brown | [`minecraft:brown_stained_glass`](../items/BrownStainedGlass.md) | [`minecraft:brown_stained_glass_pane`](../items/BrownStainedGlassPane.md) |
| Green | [`minecraft:green_stained_glass`](../items/GreenStainedGlass.md) | [`minecraft:green_stained_glass_pane`](../items/GreenStainedGlassPane.md) |
| Red | [`minecraft:red_stained_glass`](../items/RedStainedGlass.md) | [`minecraft:red_stained_glass_pane`](../items/RedStainedGlassPane.md) |
| Black | [`minecraft:black_stained_glass`](../items/BlackStainedGlass.md) | [`minecraft:black_stained_glass_pane`](../items/BlackStainedGlassPane.md) |

[Tinted Glass](../items/TintedGlass.md), `minecraft:tinted_glass`, is covered separately [below](#tinted-glass). It is not another dye color.

## Obtaining and color choices

Start with the [Glass smelting recipe](../items/Glass.md#obtaining): the bundled ingredient tag accepts ordinary Sand and Red Sand. Make [ordinary panes](../items/GlassPane.md#crafting) or choose a stained color in the table above. [Glass recipe][smelting] · [Sand tag][sand]

Stained glass does **not** use the [Wool and Carpet](WoolAndCarpet.md#obtaining-and-color-changes) recoloring rules. The checked recipes across all 16 glass colors use:

- Ordinary glass around a target-color dye for stained full blocks
- Matching-color stained full blocks in two full rows for stained panes
- Ordinary panes around a target-color dye for stained panes

Exact quantities and arrangements are on each color's item page. These bundled recipes do not accept already stained glass or panes as inputs for another color, and do not turn them back into ordinary glass. Choose the color before crafting a large batch. [Example block recipe][red-recipe] · [Matching pane recipe][red-pane-recipe] · [Ordinary-pane dye recipe][red-pane-dye]

Glass also has non-window uses: follow the existing [Glass Bottle recipe](../items/GlassBottle.md#crafting-and-filling-with-water) for bottles and [Tinted Glass recipe](../items/TintedGlass.md#crafting) for the light-blocking material.

## Mining and drops

| Material | Ordinary Survival collection |
| --- | --- |
| Ordinary Glass and Glass Panes | Silk Touch gives **one matching item**; without it, no item |
| All 16 stained blocks and panes | Silk Touch gives **one item of the same color and form**; without it, no item |
| Tinted Glass | **One Tinted Glass**, without needing Silk Touch |

The 34 ordinary/stained loot tables require **Silk Touch level 1 or higher** and have no fallback item or Fortune count bonus. Their registrations do not require a particular mining-tool tier, so the enchantment condition is the important collection restriction. A pane never drops the full block used to craft it. Individual item pages cite every color's actual loot table. [Ordinary block loot][glass-loot] · [Ordinary pane loot][pane-loot] · [Example stained block loot][red-loot] · [Example stained pane loot][red-pane-loot] · [Mining gate][mining-gate] · [Stained properties][stained-properties] · [Pane properties][pane-register]

Plan your window layout before placing a large batch if you do not yet have a Silk Touch tool. Glass's **0.3 registered hardness** makes it easy to break accidentally, but easy breaking does not bypass the loot condition. [Ordinary glass properties][glass-block] · [Stained properties][stained-properties] · [Pane properties][pane-register]

## Placement and pane connections

Use the item on a block face with an unobstructed target position. Full glass blocks retain a full collision box despite their transparency. Panes use a full-height central strip/post, **2/16 block wide**, with arms that change shape as neighbors connect. Neither full glass nor panes require continuing support underneath; removing a supporting or neighboring block does not itself make them fall or break. [Placement checks][placement] · [Default shape and survival][base-shape] · [Pane shape][pane] · [Connection shapes][cross]

Panes connect **horizontally** to other panes, including different colors, and to Iron Bars, walls, and suitable sturdy block faces. The sturdy-face route has exclusions such as leaves, pumpkins, melons, barriers, and shulker boxes. A pane without horizontal connections stands as a narrow central post. Connections update when the neighboring block changes; a connection is not a support requirement. [Connection rules and updates][pane] · [Connection exclusions][connection-exceptions] · [Stained-pane inheritance][stained-pane]

## Waterlogging

Ordinary and stained panes can contain water. Placing a pane in a **water source** sets its waterlogged state; a [Water Bucket](../items/WaterBucket.md) can also fill a pane through the normal liquid-container interaction. The bucket's usual dimension and interaction rules still apply, including water evaporation in ultrawarm dimensions. [Pane placement][pane] · [Bucket interaction][bucket]

Use an empty [Bucket](../items/Bucket.md) to remove the contained source water and leave the pane. A waterlogged pane exposes a source-water fluid state and schedules water updates; account for nearby water flow when building. Full glass blocks do not use this pane waterlogging behavior. [Water placement and pickup][waterlogged] · [Pane fluid state][cross] · [Full-block classes][transparent]

## Light and transparency

Ordinary and stained full glass blocks share the same light-transmitting behavior, regardless of dye color. **Black Stained Glass does not block light like Tinted Glass.** Dry panes also propagate skylight downward; waterlogged panes use the waterlogged light behavior instead. [Transparent block behavior][transparent] · [Stained-glass inheritance][stained] · [Pane skylight][cross]

For precision, ordinary/stained full blocks and dry panes report light blocking **0**, waterlogged panes report **1**, and Tinted Glass reports **15** and does not propagate skylight downward. These are source-defined block values, not a claim that nearby light never loses strength with distance: the light engine applies its own propagation rules. Stained glass and panes are registered in the translucent render layer, but this guide does not promise a particular shader's appearance. [Base light calculation][light-values] · [Block-state initialization][light-init] · [Propagation][light-engine] · [Tinted override][tinted] · [Stained render registration][render]

## Beacon beam colors

Place a stained-glass block or pane **in the beacon's vertical beam column** to contribute its color to the beam. Both stained forms implement the beam-color interface used by the active Beacon tick. Ordinary glass and ordinary panes let the beam continue without adding a dye color. [Stained glass][stained] · [Stained pane][stained-pane] · [Beacon ticker wiring][beacon-ticker] · [Beam scan][beacon]

The first stained color sets the colored beam section. Later differing colors are averaged with the current beam color, so the order of colors can change the result. Tinted Glass is not a beam-color block: its light-blocking value causes the beam scan to stop. [Beam scan and obstruction][beacon] · [Color averaging][average] · [Tinted behavior][tinted]

This section covers glass's effect on the beam. It does not replace the beacon's base, activation, and power requirements.

## Tinted Glass

[Tinted Glass](../items/TintedGlass.md) uses ordinary Glass and Amethyst Shards in its own recipe. It remains visually translucent but **blocks light**, and it **drops itself without Silk Touch**. Its loot includes an explosion-survival condition, so explosion recovery is not guaranteed. It also blocks a beacon beam, making it unsuitable as a beam-color filter. Use a stained color when you want colored glass that passes light; choose Tinted Glass when blocking light is the goal. [Recipe][tinted-recipe] · [Light behavior][tinted] · [Loot][tinted-loot] · [Render layer][tinted-render] · [Beam obstruction][beacon]

Related: [Glass](../items/Glass.md) · [Glass Pane](../items/GlassPane.md) · [Tinted Glass](../items/TintedGlass.md) · [Furnace](Furnace.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked all 35 block/item registrations and loot tables, all 48 stained-glass/pane recipes, ordinary-glass smelting, ordinary-pane crafting, Tinted Glass crafting, and the active placement, waterlogging, light, and Beacon callbacks. All bundled recipe JSON files were scanned for these outputs before describing the available color conversions. World-generation, trade, and structure acquisition routes are outside this guide. No in-game crafting, Silk Touch, placement, waterlogging, lighting, renderer, or beacon test was run. Data packs can change recipes, tags, and loot.

[glass-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L620-L632
[stained-register]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2122-L2137
[stained-properties]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L7183-L7198
[pane-register]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3031-L3110
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L718-L749
[tinted]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/TintedGlassBlock.java
[glass-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/glass.json
[pane-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/glass_pane.json
[tinted-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/tinted_glass.json
[smelting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/glass.json
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/smelts_to_glass.json
[red-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_stained_glass.json
[red-pane-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_stained_glass_pane.json
[red-pane-dye]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_stained_glass_pane_from_glass_pane.json
[red-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/red_stained_glass.json
[red-pane-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/red_stained_glass_pane.json
[mining-gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java#L47-L142
[base-shape]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[pane]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/IronBarsBlock.java
[cross]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java
[connection-exceptions]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Block.java#L243-L250
[stained-pane]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/StainedGlassPaneBlock.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BucketItem.java#L52-L132
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[transparent]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/TransparentBlock.java
[stained]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/StainedGlassBlock.java
[light-values]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L296-L301
[light-init]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L502-L524
[light-engine]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L50-L85
[render]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/renderer/ItemBlockRenderTypes.java#L335-L370
[beacon-ticker]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BeaconBlock.java#L42-L44
[beacon]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L127-L169
[average]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/util/ARGB.java#L101-L103
[tinted-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/tinted_glass.json
[tinted-render]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/renderer/ItemBlockRenderTypes.java#L375
