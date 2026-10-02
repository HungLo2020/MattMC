# Pressure plates

Pressure plates sense eligible entities in the small region above them. Choose **Stone or Polished Blackstone** to ignore dropped items, a **wooden plate** for broader detection, or a **weighted plate** for an output that rises with the entity count. This guide covers all **17 registered plates**: 15 binary plates and the Gold/Light Weighted and Iron/Heavy Weighted pair. Pewen shares Cherry's detection but has separate water, tool, fuel, and piston differences below. [Binary detection and output][binary] · [Weighted detection and output][weighted] · [Plate item registrations][items] · [Pewen item registration][pewen-item]

## Crafting and collecting

Place **two matching listed ingredients side by side → one plate**. Every checked recipe fits the inventory's 2 × 2 grid and uses the current ingredient format, including Pewen's plate recipe. These are exact ingredients: Cobblestone does not replace Stone, ordinary Blackstone does not replace Polished Blackstone, and Bamboo Mosaic or raw Bamboo does not replace Bamboo Planks. Each recipe and loot table is linked in the complete matrix.

| Material and exact block/item ID | Two matching recipe ingredients | Detection / powered recheck | Evidence |
| --- | --- | --- | --- |
| <span id="stone-pressure-plate"></span>[Stone](../items/StonePressurePlate.md)<br>`minecraft:stone_pressure_plate` | Ordinary Stone | Living entities; 20 ticks | [Registration][reg-stone_pressure_plate] · [Recipe][recipe-stone_pressure_plate] · [Loot][loot-stone_pressure_plate] |
| <span id="polished-blackstone-pressure-plate"></span>[Polished Blackstone](../items/PolishedBlackstonePressurePlate.md)<br>`minecraft:polished_blackstone_pressure_plate` | Polished Blackstone | Living entities; 20 ticks | [Registration][reg-polished_blackstone_pressure_plate] · [Recipe][recipe-polished_blackstone_pressure_plate] · [Loot][loot-polished_blackstone_pressure_plate] |
| <span id="oak-pressure-plate"></span>[Oak](../items/OakPressurePlate.md)<br>`minecraft:oak_pressure_plate` | Oak Planks | All eligible entities; 20 ticks | [Registration][reg-oak_pressure_plate] · [Recipe][recipe-oak_pressure_plate] · [Loot][loot-oak_pressure_plate] |
| <span id="spruce-pressure-plate"></span>[Spruce](../items/SprucePressurePlate.md)<br>`minecraft:spruce_pressure_plate` | Spruce Planks | All eligible entities; 20 ticks | [Registration][reg-spruce_pressure_plate] · [Recipe][recipe-spruce_pressure_plate] · [Loot][loot-spruce_pressure_plate] |
| <span id="birch-pressure-plate"></span>[Birch](../items/BirchPressurePlate.md)<br>`minecraft:birch_pressure_plate` | Birch Planks | All eligible entities; 20 ticks | [Registration][reg-birch_pressure_plate] · [Recipe][recipe-birch_pressure_plate] · [Loot][loot-birch_pressure_plate] |
| <span id="jungle-pressure-plate"></span>[Jungle](../items/JunglePressurePlate.md)<br>`minecraft:jungle_pressure_plate` | Jungle Planks | All eligible entities; 20 ticks | [Registration][reg-jungle_pressure_plate] · [Recipe][recipe-jungle_pressure_plate] · [Loot][loot-jungle_pressure_plate] |
| <span id="acacia-pressure-plate"></span>[Acacia](../items/AcaciaPressurePlate.md)<br>`minecraft:acacia_pressure_plate` | Acacia Planks | All eligible entities; 20 ticks | [Registration][reg-acacia_pressure_plate] · [Recipe][recipe-acacia_pressure_plate] · [Loot][loot-acacia_pressure_plate] |
| <span id="cherry-pressure-plate"></span>[Cherry](../items/CherryPressurePlate.md)<br>`minecraft:cherry_pressure_plate` | Cherry Planks | All eligible entities; 20 ticks | [Registration][reg-cherry_pressure_plate] · [Recipe][recipe-cherry_pressure_plate] · [Loot][loot-cherry_pressure_plate] |
| <span id="dark-oak-pressure-plate"></span>[Dark Oak](../items/DarkOakPressurePlate.md)<br>`minecraft:dark_oak_pressure_plate` | Dark Oak Planks | All eligible entities; 20 ticks | [Registration][reg-dark_oak_pressure_plate] · [Recipe][recipe-dark_oak_pressure_plate] · [Loot][loot-dark_oak_pressure_plate] |
| <span id="pale-oak-pressure-plate"></span>[Pale Oak](../items/PaleOakPressurePlate.md)<br>`minecraft:pale_oak_pressure_plate` | Pale Oak Planks | All eligible entities; 20 ticks | [Registration][reg-pale_oak_pressure_plate] · [Recipe][recipe-pale_oak_pressure_plate] · [Loot][loot-pale_oak_pressure_plate] |
| <span id="mangrove-pressure-plate"></span>[Mangrove](../items/MangrovePressurePlate.md)<br>`minecraft:mangrove_pressure_plate` | Mangrove Planks | All eligible entities; 20 ticks | [Registration][reg-mangrove_pressure_plate] · [Recipe][recipe-mangrove_pressure_plate] · [Loot][loot-mangrove_pressure_plate] |
| <span id="bamboo-pressure-plate"></span>[Bamboo](../items/BambooPressurePlate.md)<br>`minecraft:bamboo_pressure_plate` | Bamboo Planks | All eligible entities; 20 ticks | [Registration][reg-bamboo_pressure_plate] · [Recipe][recipe-bamboo_pressure_plate] · [Loot][loot-bamboo_pressure_plate] |
| <span id="crimson-pressure-plate"></span>[Crimson](../items/CrimsonPressurePlate.md)<br>`minecraft:crimson_pressure_plate` | Crimson Planks | All eligible entities; 20 ticks | [Registration][reg-crimson_pressure_plate] · [Recipe][recipe-crimson_pressure_plate] · [Loot][loot-crimson_pressure_plate] |
| <span id="warped-pressure-plate"></span>[Warped](../items/WarpedPressurePlate.md)<br>`minecraft:warped_pressure_plate` | Warped Planks | All eligible entities; 20 ticks | [Registration][reg-warped_pressure_plate] · [Recipe][recipe-warped_pressure_plate] · [Loot][loot-warped_pressure_plate] |
| <span id="pewen-pressure-plate"></span>[Pewen](../items/PewenPressurePlate.md)<br>`minecraft:pewen_pressure_plate` | Pewen Planks; [Pewen recipe](Pewen.md#construction-and-recipes) | All eligible entities; 20 ticks | [Registration][reg-pewen_pressure_plate] · [Recipe][recipe-pewen_pressure_plate] · [Loot][loot-pewen_pressure_plate] |
| <span id="light-weighted-pressure-plate"></span>[Light Weighted](../items/LightWeightedPressurePlate.md)<br>`minecraft:light_weighted_pressure_plate` | Gold Ingot | All eligible entities; 10 ticks | [Registration][reg-light_weighted_pressure_plate] · [Recipe][recipe-light_weighted_pressure_plate] · [Loot][loot-light_weighted_pressure_plate] |
| <span id="heavy-weighted-pressure-plate"></span>[Heavy Weighted](../items/HeavyWeightedPressurePlate.md)<br>`minecraft:heavy_weighted_pressure_plate` | Iron Ingot | All eligible entities; 10 ticks | [Registration][reg-heavy_weighted_pressure_plate] · [Recipe][recipe-heavy_weighted_pressure_plate] · [Loot][loot-heavy_weighted_pressure_plate] |

All 17 plates have hardness and blast resistance **0.5**. Ordinary Survival mining drops **one matching plate even by hand**; their registrations have no correct-tool requirement. The four stone/metal plates are pickaxe-efficient, the 12 vanilla wooden plates are axe-efficient, and Pewen is absent from both mining tags. Silk Touch and Fortune do not change the checked self-drop tables. Their explosion-survival conditions still apply to explosion drops. [Pickaxe tag][pickaxe] · [Axe tag][axe] · [Wooden-plate tag][wood-plates] · [Strength assignment][strength] · [Mining-speed rules][tool-rule] · [Correct-tool check][toolgate] · [Survival mining dispatch][mine]

## Placement and output

A plate sits horizontally above a block whose upper face supplies **rigid or center support**. An ordinary full solid block is a simple choice. It cannot mount on a wall or ceiling, and losing valid support breaks it and normally drops the plate. Its no-collision setting lets entities pass through its space. [Support and support-loss behavior][support] · [Support tests][support-helper] · [Support-loss removal][break] · [Collision behavior][collision]

The detection box is centered on the block, **14/16 of a block wide and long and 4/16 high**, starting at the floor. It tests entity bounding-box overlap; an entity's center does not have to stand exactly in the plate's center. The visible selection shape is thinner: 1/16 high when released and 0.5/16 when pressed. [Detection and selection shapes][shapes] · [Column dimensions][shape-helper] · [Filtered detection query][entity-query] · [Entity bounding-box intersection][intersection]

A pressed plate supplies its signal to adjacent receivers and direct power into its supporting block. Use a short [Redstone Dust](RedstoneDust.md) connection or a receiver beside it. Weighted signals can be weak, so a long dust run may lose the signal before it reaches the receiver. Hand interaction does not press a plate; occupancy determines its output. [Neighbor updates and output][signal] · [Occupancy transitions][contact]

## What each plate detects

| Plate group | Eligible entities | Output |
| --- | --- | --- |
| Stone and Polished Blackstone | Living entities, including players and mobs | 15 with at least one qualifying entity; otherwise 0 |
| All 13 wooden plates, including Nether, Bamboo, and Pewen | Any eligible entity type, including dropped items and arrows | 15 with at least one qualifying entity; otherwise 0 |
| Light Weighted, gold | Any eligible entity type | One signal level per entity, capped at 15 |
| Heavy Weighted, iron | Any eligible entity type | One signal level per ten entities, rounded up, capped at 15 |

The two stone block-set types use living-entity sensitivity. Each wooden registration uses a block-set type with broad sensitivity; Pewen explicitly uses Cherry. Weighted plates query the broad entity class independently. [Stone sensitivity][stone-sensitivity] · [Wood sensitivity][wood-sensitivity] · [Binary class selection][binary-filter] · [Weighted class and rounding][weighted-filter]

Every plate excludes **spectators** and entities whose implementation ignores block triggers. Ordinary Armor Stands are living entities, so they qualify even for Stone; marker Armor Stands opt out. A class or mob name alone does not bypass these exclusions. [Common exclusions][filter] · [Armor Stand class][armor-class] · [Marker exception][armor-marker]

Weighted plates count **entities, not items inside a stack or physical mass**. One dropped stack of 64 Cobblestone counts as one item entity, just like a one-item stack. If dropped stacks merge, the entity count can fall even though the total items remain unchanged. The player standing on a weighted plate is another eligible entity, so step clear when measuring dropped items. [Entity-count query][count] · [Stack merging and entity removal][item-merge]

A Heavy plate gives 1 for 1–10 entities, 2 for 11–20, and reaches 15 at **141 or more**. Its internal count cap is 150; 150 is not the minimum for output 15. A Light plate reaches 15 at 15 entities. Both give 0 when empty. [Registered count caps][caps] · [Actual count formula][formula]

## Activation and release timing

An unpowered plate checks for occupants when an entity contacts its block region. Once powered, it schedules repeated occupancy checks:

| Plate group | Powered occupancy recheck | At 20 game ticks/second |
| --- | --- | --- |
| Stone, Polished Blackstone, and every wooden plate | 20 game ticks, or 10 redstone ticks | 1 second |
| Light and Heavy Weighted | 10 game ticks, or 5 redstone ticks | 0.5 seconds |

These are **recheck intervals, not fixed button pulses**. A plate stays on while eligible occupants remain. Departure and changes to a weighted count while already powered are noticed at a scheduled check; leaving does not start a new full-length timer. Tick-rate changes or lag alter elapsed real time. [Default interval][binary-time] · [Weighted interval][weighted-time] · [Contact, transitions, and rescheduling][checks] · [Active entity-contact dispatch][dispatch] · [Game-time scheduling][schedule] · [Scheduled tick dispatch][tick]

## Water, pistons, and fuel

**No listed plate waterlogs.** The 16 vanilla plates block incoming water from occupying their own cell through their explicit solid-state property, despite having no movement-blocking collision. Pewen Plate lacks that property: incoming water can replace it and run its item-drop path. This is a material exception to account for before using a pressure plate as a water barrier. [Default empty fluid state][fluid] · [Solid-state calculation][solid] · [Fluid motion test][motion] · [Pewen plate property copy][pewen-water] · [Copied Pewen Planks properties][pewen-planks] · [Copied property fields][copy] · [Fluid admission][water-admit] · [Fluid replacement][water-replace] · [Water drop callback][water-drop]

The 16 vanilla plates have the **destroy** piston reaction, so a piston pushing into them breaks them and runs their loot. Pewen Plate inherits the default **normal** reaction and can be moved by the piston path instead, subject to the ordinary piston limits and its support requirement. [Default reaction][piston-default] · [Piston eligibility][piston-check] · [Destroyed-block drops][piston-drop]

The default furnace fuel table gives **300 burn ticks per plate** to the ten ordinary Overworld wood/Bamboo variants. Crimson and Warped are excluded through the non-flammable-wood item tag. Pewen is missing from the wooden-pressure-plate fuel tag, and neither stone plate nor weighted metal plate has a fuel entry. Use [Furnace fuel planning](Furnace.md#fuel-planning) for cooking efficiency. [Fuel tag membership][fuel-plates] · [Nether fuel exclusions][nonflammable] · [Fuel entries and removal][fuel] · [Server fuel initialization][fuel-init]

## Small example: a floor indicator

This layout is source-derived and has **not been tested in game**.

1. Put an Oak Pressure Plate on a solid floor
2. Put a Redstone Lamp immediately beside it at the same block level, leaving another side accessible
3. Walk onto the plate, then step away. The Lamp should light, then turn off after the plate's empty recheck and the Lamp's separate four-game-tick off delay
4. Drop an item onto the plate and move clear. Oak should respond while the item remains; Stone and Polished Blackstone should ignore that dropped item

A weighted plate can also power the adjacent Lamp with one eligible entity, but the Lamp shows only on/off. Use a strength-sensitive circuit to distinguish counts. [Lamp response and off delay][lamp]

## Related pages

- [Stone item](../items/StonePressurePlate.md), [Oak item](../items/OakPressurePlate.md), and [Pewen item](../items/PewenPressurePlate.md)
- [Light Weighted item](../items/LightWeightedPressurePlate.md) and [Heavy Weighted item](../items/HeavyWeightedPressurePlate.md)
- [Tripwire](Tripwire.md), [Buttons](Buttons.md), and [Redstone basics](../redstone/Redstone.md)
- [Wood construction](WoodConstruction.md), [Pewen](Pewen.md), [Mining](../mechanics/Mining.md), and [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `be4ac3081f175ffb862938ab118b20e6457110d8` on 2026-10-02. Every listed registration, recipe, loot table, and relevant tag was checked against the active interaction, entity-contact, fluid, and scheduled-tick paths. No gameplay or circuit timing test was run.

[binary]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java#L28-L55
[weighted]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/WeightedPressurePlateBlock.java#L34-L68
[items]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/Items.java#L1052-L1067
[pewen-item]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/Items.java#L312-L313
[reg-stone_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1753-L1763
[recipe-stone_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/stone_pressure_plate.json
[loot-stone_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/stone_pressure_plate.json
[reg-polished_blackstone_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5848-L5858
[recipe-polished_blackstone_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_pressure_plate.json
[loot-polished_blackstone_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_pressure_plate.json
[reg-oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1769-L1780
[recipe-oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/oak_pressure_plate.json
[loot-oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/oak_pressure_plate.json
[reg-spruce_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1781-L1792
[recipe-spruce_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/spruce_pressure_plate.json
[loot-spruce_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/spruce_pressure_plate.json
[reg-birch_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1793-L1804
[recipe-birch_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/birch_pressure_plate.json
[loot-birch_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/birch_pressure_plate.json
[reg-jungle_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1805-L1816
[recipe-jungle_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/jungle_pressure_plate.json
[loot-jungle_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/jungle_pressure_plate.json
[reg-acacia_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1817-L1828
[recipe-acacia_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/acacia_pressure_plate.json
[loot-acacia_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/acacia_pressure_plate.json
[reg-cherry_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1829-L1840
[recipe-cherry_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/cherry_pressure_plate.json
[loot-cherry_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/cherry_pressure_plate.json
[reg-dark_oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1841-L1852
[recipe-dark_oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/dark_oak_pressure_plate.json
[loot-dark_oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_pressure_plate.json
[reg-pale_oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1853-L1864
[recipe-pale_oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/pale_oak_pressure_plate.json
[loot-pale_oak_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_pressure_plate.json
[reg-mangrove_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1865-L1876
[recipe-mangrove_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/mangrove_pressure_plate.json
[loot-mangrove_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/mangrove_pressure_plate.json
[reg-bamboo_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1877-L1888
[recipe-bamboo_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/bamboo_pressure_plate.json
[loot-bamboo_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/bamboo_pressure_plate.json
[reg-crimson_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5608-L5618
[recipe-crimson_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/crimson_pressure_plate.json
[loot-crimson_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/crimson_pressure_plate.json
[reg-warped_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5619-L5629
[recipe-warped_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/warped_pressure_plate.json
[loot-warped_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/warped_pressure_plate.json
[reg-pewen_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7057-L7061
[recipe-pewen_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/pewen_pressure_plate.json
[loot-pewen_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/pewen_pressure_plate.json
[reg-light_weighted_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2839-L2843
[recipe-light_weighted_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/light_weighted_pressure_plate.json
[loot-light_weighted_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/light_weighted_pressure_plate.json
[reg-heavy_weighted_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2844-L2848
[recipe-heavy_weighted_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/heavy_weighted_pressure_plate.json
[loot-heavy_weighted_pressure_plate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/heavy_weighted_pressure_plate.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[axe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[wood-plates]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/wooden_pressure_plates.json
[strength]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[tool-rule]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L45
[toolgate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mine]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[support]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L53-L73
[support-helper]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Block.java#L319-L328
[break]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Block.java#L213-L225
[collision]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L325-L330
[shapes]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L25-L41
[shape-helper]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Block.java#L175-L182
[entity-query]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L146-L148
[intersection]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/entity/EntitySection.java#L40-L53
[signal]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L126-L148
[contact]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L75-L117
[stone-sensitivity]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L83-L118
[wood-sensitivity]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L119-L217
[binary-filter]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java#L43-L50
[weighted-filter]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/WeightedPressurePlateBlock.java#L40-L49
[filter]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L146-L148
[armor-class]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L50-L50
[armor-marker]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L450-L453
[count]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L146-L148
[item-merge]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L228-L250
[caps]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2839-L2848
[formula]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/WeightedPressurePlateBlock.java#L40-L49
[binary-time]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L44-L46
[weighted-time]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/WeightedPressurePlateBlock.java#L61-L64
[checks]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L75-L117
[dispatch]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1205
[schedule]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/LevelAccessor.java#L35-L43
[tick]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L770
[fluid]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[solid]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L482-L497
[motion]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L534-L543
[pewen-water]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7057-L7061
[pewen-planks]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7010-L7017
[copy]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1081
[water-admit]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[water-replace]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[water-drop]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[piston-default]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1013-L1019
[piston-check]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L258
[piston-drop]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L287-L299
[fuel-plates]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/item/wooden_pressure_plates.json
[nonflammable]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/MinecraftServer.java#L340-L340
[lamp]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java#L36-L55
