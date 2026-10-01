# Oak Log

**Oak Log** (`minecraft:oak_log`) is the trunk material used for oak planks, oak wood, charcoal, and construction. Keep some logs unconverted if you plan to make charcoal. Placed-log mining, orientation, and axe stripping are covered by the [Oak block guide](../blocks/Oak.md). [Registration][item] · [Charcoal input tag][burning-logs]

## Obtaining

Harvest an oak trunk or grow a tree from an [Oak Sapling](OakSapling.md). The [Oak guide](../blocks/Oak.md#finding-and-harvesting-oak) covers verified natural generation, mining, leaf drops, and replanting. The raw-log loot table returns an oak log; stripping a placed log changes the block and its resulting drop to [Stripped Oak Log](StrippedOakLog.md). [Log loot][loot] · [Stripping behavior][axe]

## Crafting conversions

- Convert an accepted oak-family block into [Oak Planks](OakPlanks.md#crafting-oak-planks); that page holds the plank recipe and its accepted inputs
- Arrange **4 ordinary oak logs in a 2 × 2 square** to craft **3 [Oak Wood](OakWood.md)** blocks
- The parallel stripped recipe uses **4 stripped oak logs** in the same layout to make **3 [Stripped Oak Wood](StrippedOakWood.md)**

The two wood recipes name their exact log items, so mixing ordinary and stripped logs does not satisfy either recipe. Oak Wood and stripped forms still belong to the oak-log item tag used for plank crafting. [Oak Wood recipe][wood] · [Stripped Oak Wood recipe][stripped-wood] · [Oak-log tag][oak-tag]

## Charcoal and fuel

Oak logs are accepted by the bundled **logs-that-burn** charcoal recipe. Follow [Charcoal](Charcoal.md#making-charcoal) for its furnace input, processing time, and starting-fuel requirements. Oak planks are a different item and are not in that log tag. [Charcoal recipe][charcoal] · [Burnable log families][burning-logs]

An oak log supplies **300 default furnace burn ticks**, enough energy for **1.5 uninterrupted 200-tick recipes**. This is an energy amount, not a fractional item output. Converting a log into planks first increases its total direct fuel supply; see [Oak Planks](OakPlanks.md#fuel). [Fuel table][fuel] · [Server fuel initialization][server-fuel] · [Furnace fuel lookup][furnace]

Related: [Oak](../blocks/Oak.md) · [Oak Planks](OakPlanks.md) · [Oak Sapling](OakSapling.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game crafting, stripping, or furnace test was run. Data packs can change recipes and tags; uninterrupted processing is assumed for the fuel comparison.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L220
[burning-logs]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_log.json
[axe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/AxeItem.java
[wood]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/oak_wood.json
[stripped-wood]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/stripped_oak_wood.json
[oak-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/oak_logs.json
[charcoal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/smelting/charcoal.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[server-fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[furnace]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
