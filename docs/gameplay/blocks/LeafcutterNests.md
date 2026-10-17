# Leafcutter ant nests

**Leafcutter Anthill** and **Leafcutter Ant Chamber** are placeable blocks associated with the [Leafcutter Ant](../mobs/LeafcutterAnt.md). The anthill accepts a Pupa to spawn a baby ant, but the checked implementation does **not** provide a working colony-storage or fungus-production system. Building chambers around it does not complete that missing behavior. [Anthill interaction][anthill] · [Pupa use][pupa] · [Nest storage implementation][storage] · [Chamber implementation][chamber]

## Obtaining and recovering the blocks

Both blocks have real item forms, and both appear in the **Functional Blocks** category alongside [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md). They can therefore be requested through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. This insertion route supplies an item; it does not populate an anthill with ants or a queen. [Block items][items] · [Category identity][category] · [Category entries][listing] · [Initial nest data][storage]

No crafting recipe, natural nest-generation reference, or block loot table for either ID was found in the bundled data or generation code at the reviewed revision. Their default loot lookup therefore has no bundled table to return these items: **do not expect mining or Silk Touch to recover them**. Neither registration adds a correct-tool requirement; that is separate from the missing loot. [Bundled data][data] · [Generation code][worldgen] · [Registration][registration] · [Loot lookup][loot] · [Missing-table fallback][missing-loot]

The [biome guides](../biomes/Biomes.md) explain the distinction between registered content and actual generation entries. Neither an upstream jungle association nor the name of an imported biome establishes a naturally populated nest here.

## Leafcutter Anthill

`minecraft:leafcutter_anthill` is a full-block anthill with hardness **0.5** and a block entity. It has no facing, fungus level, waterlogged state, soil-only placement requirement, or gravity behavior in the checked class. Ordinary placement does not require a Chamber below it. [Registration][registration] · [Anthill class][anthill] · [Default support and shape][defaults]

### Using a Pupa

Use a **Leafcutter Ant Pupa on the anthill block itself**. The server creates a baby ant centered one block above it and consumes one Pupa unless the player is in Creative. This does not require an existing queen or a Chamber. The Pupa does not set the anthill's queen flag, store the new ant, or assign that ant a home. Keep clear space above the block for the spawned creature. [Pupa behavior][pupa] · [Initial ant home][ant-home]

### Queen release and nesting limits

An ordinary empty-hand use checks the anthill's saved `HasQueen` value. If it is true, the server attempts to spawn a queen one block above the anthill and clears that flag after creating her. A freshly placed anthill starts with the flag false, and the checked class has **no active way to install a queen or turn that flag on during ordinary play**. An anthill loaded with pre-existing queen data is a separate case. [Use action][anthill] · [Queen flag, release, and saving][storage]

Some ant movement remains implemented: ants with a carried leaf, or queens, can try to return to an already known anthill. However, the hive-search branch only reuses the ant's existing saved home instead of discovering nearby anthills, and the arrival call enters an empty storage method. The anthill's capacity and fire checks also always return false. Consequently, placing an anthill beside ants does not establish working nest discovery, ant storage, leaf deposit, smoke management, or colony growth. [Home loading][home-load] · [Return conditions and search][return-search] · [Arrival][return-arrival] · [Storage stubs][storage]

Adult worker ants can still forage leaves independently of successful nesting. Their goal selects leaf-tag blocks and records a carried leaf; the cutting path destroys the selected leaves without drops and restores them with approximately a 50% chance. Visible leaf carrying is therefore **not evidence that an anthill is producing fungus**. Protect decorative foliage when experimenting. [Foraging conditions][forage-start] · [Carried leaves and cutting][forage]

## Leafcutter Ant Chamber

`minecraft:leafcutter_ant_chamber` is a separate decorative block with hardness **0.3** and a **14/16-block-high** outline and collision shape. It uses the ordinary support rule, so it does not need an anthill, special soil, or another Chamber to remain placed. [Registration][registration] · [Chamber shape][chamber] · [Default support and collision][defaults]

Its complete class defines only its codec, constructor, and shape. It has **no fungus-growth stage, harvest action, ant inventory, queen interaction, block entity, or ticking production behavior**. Adding chambers does not increase anthill capacity, and no active anthill code places or consumes them. The green appearance and name do not establish a farmable food source. [Chamber implementation][chamber] · [Anthill implementation][anthill] · [Nest implementation][storage]

The [Anteater](../mobs/Anteater.md) raid goal is also simplified in this revision: its targets are Dirt and Coarse Dirt, rather than either of these nest blocks. Do not assume that the existence of that goal supplies missing anthill interactions. [Raid target][raid-target]

## Related pages

- [Leafcutter Ant](../mobs/LeafcutterAnt.md)
- [Leafcutter Anthill item](../items/LeafcutterAnthill.md)
- [Leafcutter Ant Chamber item](../items/LeafcutterAntChamber.md)
- [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md)
- [Tree leaves](TreeLeaves.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. Checked block/item/category registration, the nest and Pupa classes, ant foraging and return calls, and the Anteater raid target. Searched the bundled recipes, loot tables, tags, world-generation data, data generators, and generation classes for both nest IDs and references. No game, placement, harvest, colony, or world-generation test was run. Missing bundled routes and unfinished code are distinguished from the working inventory-browser route; data packs can change data-driven availability.

[registration]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5360-L5369
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2475-L2476
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1038
[listing]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1098-L1102
[anthill]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockLeafcutterAnthill.java#L18-L51
[chamber]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockLeafcutterAntChamber.java#L12-L29
[storage]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/tileentity/TileEntityLeafcutterAnthill.java#L12-L60
[pupa]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/item/ItemLeafcutterPupa.java#L18-L36
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L327
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L269-L277
[missing-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[data]: https://github.com/HungLo2020/MattMC/tree/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft
[worldgen]: https://github.com/HungLo2020/MattMC/tree/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen
[ant-home]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L88-L96
[home-load]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L481-L503
[return-search]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L634-L669
[return-arrival]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L684-L696
[forage-start]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/ai/LeafcutterAntAIForageLeaves.java#L25-L38
[forage]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/ai/LeafcutterAntAIForageLeaves.java#L123-L158
[raid-target]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L154-L158
