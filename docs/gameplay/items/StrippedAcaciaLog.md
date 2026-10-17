# Stripped Acacia Log

Stripped Acacia Log (`minecraft:stripped_acacia_log`) is the stripped version of [Acacia Log](AcaciaLog.md), used for exposed timber and crafting. Its placed-block guide is [Acacia timber](../blocks/TreeLogsAndRoots.md#acacia-timber). [Registration][item]

## Obtaining

Use an **unbroken axe** on a placed Acacia Log, then mine the resulting Stripped Acacia Log to pick it up. The conversion keeps the log's axis; see [stripping](../blocks/TreeLogsAndRoots.md#stripping) for tool wear and interaction details. [Axe conversion][strip] · [Block change][strip-use] · [Axis retention][strip-axis] · [Unbroken-tool requirement][broken]

Normal Survival mining drops **one Stripped Acacia Log**. An unbroken axe is the efficient tool, but hand mining also works and Silk Touch is unnecessary. The [shared mining guide](../blocks/TreeLogsAndRoots.md#mining-and-placement) covers the harvest rules. [Drop][loot] · [Tool tag][axe-tag] · [Log properties][properties] · [Harvest check][harvest]

## Usage

- Craft **4 Stripped Acacia Logs in a 2 × 2 square** into **3 [Stripped Acacia Wood](StrippedAcaciaWood.md)** blocks. The recipe requires this exact stripped log. [Wood recipe][wood-recipe]
- Craft **1 Stripped Acacia Log** into **4 [Acacia Planks](AcaciaPlanks.md)** anywhere in a crafting grid. [Plank recipe][planks-recipe] · [Accepted inputs][planks-inputs]

See [crafting choices](../blocks/TreeLogsAndRoots.md#crafting-choices) for the other timber forms and [Wood Construction](../blocks/WoodConstruction.md#planks-and-materials) for plank-based building recipes.

## Behavior

Clicking a top or bottom face places a vertical log; clicking a side aligns it along that horizontal axis. Its two end faces have a different texture from the four stripped sides. Follow [mining and placement](../blocks/TreeLogsAndRoots.md#mining-and-placement) for the shared block rules. [Placement][axis] · [Axis models][state-models] · [End and side textures][end-side] · [Horizontal textures][horizontal-model]

This log is already stripped, so it has **no further axe-stripping conversion**. [Stripping map][strip]

## Notes

- This item places the `minecraft:stripped_acacia_log` block. [Block registration][block]
- Placed Stripped Acacia Log is **flammable**. The item supplies **300 default furnace burn ticks** and is accepted as an input for [Charcoal](Charcoal.md#making-charcoal). See [fire and fuel](../blocks/TreeLogsAndRoots.md#fire-and-fuel) for the shared rules and conversion tradeoffs. [Fire entries][fire] · [Fuel table][fuel] · [Burnable inputs][burnable-inputs] · [Charcoal recipe][charcoal]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game test was run. Data packs can change recipes, tags, and drops; resource packs can change appearance.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L238-L238
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L434-L436
[strip]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[strip-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L82
[strip-axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L132
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L365
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/stripped_acacia_log.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7162-L7168
[wood-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/stripped_acacia_wood.json
[planks-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/acacia_planks.json
[planks-inputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/acacia_logs.json
[axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L49-L56
[state-models]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/blockstates/stripped_acacia_log.json
[end-side]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/models/block/stripped_acacia_log.json
[horizontal-model]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/models/block/stripped_acacia_log_horizontal.json
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L373-L381
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[burnable-inputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[charcoal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/charcoal.json
