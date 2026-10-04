# Dark Oak Log

## Obtaining

Grow **four [Dark Oak Saplings](DarkOakSapling.md) in a 2 × 2 square** to produce a tree with Dark Oak Logs. A lone Dark Oak Sapling has no single-tree growth route. [Growth selection][growth] · [Growth callback][grow-callback] · [Trunk][trunk1]

Break a Dark Oak Log to collect **one Dark Oak Log**, even by hand; an axe mines it faster and Silk Touch is unnecessary. See [timber mining](../blocks/TreeLogsAndRoots.md#mining-and-placement) for the shared harvest rules. [Drop][drop]

## Usage

* Craft **one Dark Oak Log → four [Dark Oak Planks](DarkOakPlanks.md)**. The same plank recipe accepts this family's stripped log and both wood forms. [Plank recipe][planks] · [Accepted inputs][inputs]
* Put **four Dark Oak Logs in a 2 × 2 square → three [Dark Oak Wood](DarkOakWood.md)**, the bark-covered building form. This spends one of the four blocks; converting each log straight to planks preserves more material. [Wood recipe][wood]

[Tree logs and roots](../blocks/TreeLogsAndRoots.md#dark_oak-timber) compares this family's four timber forms; [Wood Construction](../blocks/WoodConstruction.md#planks-and-materials) continues from planks to building parts.

## Behavior

The log has bark sides and cut ends along its axis. Place against a top or bottom face for a vertical log, or against a side for a horizontal log. [Placement][placement] · [Axis models][axis-models]

Use an **unbroken axe** on the placed log to turn it into a **[Stripped Dark Oak Log](StrippedDarkOakLog.md)**, preserving its axis. Follow [stripping](../blocks/TreeLogsAndRoots.md#stripping) for axe wear and shield-interaction details. [Stripping map and use][stripping] · [Axis retention][strip-axis]

The placed log is flammable. As furnace fuel, one log supplies **300 burn ticks**; smelting one instead produces **one [Charcoal](Charcoal.md#making-charcoal)**. The shared [fire and fuel guide](../blocks/TreeLogsAndRoots.md#fire-and-fuel) explains these choices. [Fuel][fuel] · [Charcoal recipe][charcoal]

## Notes

* This item is the item form of the `minecraft:dark_oak_log` block. [Item registration][item]
* Available in the Creative Menu's Building Blocks and Natural Blocks tabs. [Building Blocks tab][creative-building] · [Natural Blocks tab][creative-natural]
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game test was run; data packs can change recipes, loot, and tags.

[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L37-L70
[grow-callback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L132-L198
[trunk1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/dark_oak.json
[drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_log.json
[planks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/dark_oak_planks.json
[inputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/dark_oak_logs.json
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/dark_oak_wood.json
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L57
[axis-models]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/blockstates/dark_oak_log.json
[stripping]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L100
[strip-axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L132
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[charcoal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/charcoal.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L227-L227
[creative-building]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L144
[creative-natural]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L841-L851
