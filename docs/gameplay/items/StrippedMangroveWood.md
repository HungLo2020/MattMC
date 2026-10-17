# Stripped Mangrove Wood

Stripped Mangrove Wood is the stripped form of [Mangrove Wood](MangroveWood.md), with the stripped-log side texture on **all six faces**. [Model][model] The [tree-timber guide](../blocks/TreeLogsAndRoots.md#mangrove-timber) compares it with the separate Mangrove Log and stripped-log forms.

## Obtaining

Craft **four [Stripped Mangrove Logs](StrippedMangroveLog.md) in a 2 × 2 square** to make **three Stripped Mangrove Wood**. This recipe uses the matching stripped logs, not ordinary logs or wood. [Recipe][recipe]

Alternatively, use an **unbroken axe on placed Mangrove Wood**, then mine the converted block. This keeps its axis and converts one block at a time; see [stripping controls](../blocks/TreeLogsAndRoots.md#stripping). An axe mines it faster, but ordinary Survival mining by hand or with any tool drops **one Stripped Mangrove Wood** without requiring Silk Touch. [Axe conversion][axe] · [Drops][loot]

## Usage

One Stripped Mangrove Wood crafts into **four [Mangrove Planks](MangrovePlanks.md)**. See [wood construction](../blocks/WoodConstruction.md#planks-and-materials) for the building materials those planks provide. [Plank recipe][planks]

It supplies **300 default furnace burn ticks** or can be smelted into **one [Charcoal](Charcoal.md#making-charcoal)**. See [fire and fuel](../blocks/TreeLogsAndRoots.md#fire-and-fuel) for the fuel tradeoffs.

## Behavior

The placed block keeps an axis: click a top or bottom face for a vertical pillar, or a side face for that horizontal axis. It is flammable and has **no further axe-stripping step**. The [mining and placement guide](../blocks/TreeLogsAndRoots.md#mining-and-placement) covers shared timber behavior.

## Notes

* This item is the item form of the `minecraft:stripped_mangrove_wood` block.
* Source checked at `78e8e0423084f010bb47e36132550619b37644c2`; recipes, tags, and loot can be changed by data packs. No in-game test was run.

[model]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/models/block/stripped_mangrove_wood.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/stripped_mangrove_wood.json
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L54
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/stripped_mangrove_wood.json
[planks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mangrove_planks.json
