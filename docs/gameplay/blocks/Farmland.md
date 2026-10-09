# Farmland

**Farmland** (`minecraft:farmland`) is the tilled soil for [Wheat](Wheat.md), [root crops](RootCrops.md), and several other crops. Keep water within reach, plant promptly, and avoid landing on the field. This page owns soil preparation, hydration and conversion back to Dirt; each crop guide owns its planting, light and harvest rules. [Soil behavior][farm] · [Maintaining plants][maintains]

## Making farmland

Use an unbroken hoe on **Dirt, Grass Block, or Dirt Path**, with air directly above and a top or side click. This converts the existing ground into **moisture-0 Farmland**; it does not produce an inventory item. A successful ordinary till requests **1 durability**. Coarse Dirt first becomes Dirt; Rooted Dirt becomes Dirt and drops Hanging Roots. Till the resulting Dirt again to make a field. For the exact Rooted Dirt exception, blocked inputs and tool controls, use [tilling with a hoe](../mechanics/AxesAndHoes.md#tilling-with-a-hoe). [Hoe conversions][hoe] · [Registered default][catalog] · [Default state][templates]

Clear the planting cell before tilling: vegetation is not air. A Farmland inventory item is a separate placement route, covered by the [Farmland item guide](../items/Farmland.md). If that item's intended placement has invalid solid cover above it, it selects Dirt instead. [Tilling condition][hoe] · [Placement fallback][placement]

## Mining and physical behavior

Ordinary mining gives **1 Dirt**, including by hand. An unbroken **shovel** is faster. Silk Touch does not recover Farmland, and Fortune does not increase the Dirt count; the loot table has only its Dirt entry and an explosion-survival condition. Breaking the soil is therefore not a way to move a prepared field intact. [Loot][loot] · [Shovel tag][shovel] · [Tool use][tool] · [Tool speeds][materials] · [Broken-tool speed][tool-speed] · [Drop gate][gate] · [Mining dispatch][mining]

Farmland has **hardness 0.6 and blast resistance 0.6**, and its collision surface is **15/16 of a block high** at every moisture value. It emits **no light** and has no waterlogged state: irrigation comes from nearby fluid, not water stored inside the soil. [Native properties][catalog] · [Physical profile][physics] · [Light and fluid][intrinsic] · [Default state][templates] · [Shape][farm] · [Collision dispatch][collision]

## Hydration

Farmland has **moisture 0–7**. On a random tick, it searches a square reaching **four blocks in each horizontal direction**, at the soil's height and **one block above**, for water-tag fluid. Diagonal positions count. Water below the soil is outside the search; there is no requirement for an unobstructed channel between water and soil. Flowing water and water inside a waterlogged block can qualify because the check reads fluid state. **Rain reaching the cell above** also hydrates it. [Moisture and search][farm] · [Water fluids][water-tag]

- With qualifying water or rain, the next moisture update raises the value straight to **7**
- Without either, an update lowers a positive value by **1**
- Once already at **0**, a later dry update changes the soil to Dirt unless a maintaining plant is directly above it

Drying is driven by random selections, so it has no fixed countdown in seconds. See [ticks and chunk activity](../mechanics/TicksAndChunkActivity.md#what-kind-of-tick-does-the-task-need) when a field stops updating. [Moisture update][farm] · [Live random-tick policy][policy] · [Tick dispatch][ticks]

The bundled maintenance tag contains **Wheat, Carrots, Potatoes, Beetroot, Torchflower Crop, mature Torchflower, Pitcher Crop, and both ordinary and attached Pumpkin/Melon Stems**. These preserve dry soil; they do not hydrate it. A plant merely being allowed on Farmland does not put it in this list. [Exact maintenance tag][maintains]

### A reliable watered plot

A **9 × 9 square with one central water cell** puts all **80 surrounding soil cells** within irrigation range, including its corners. Place the water at the same block height as the soil. This is a layout derived from the square search, not a measured yield recommendation; crops still need their own light and growth conditions. [Water bounds][farm]

In the bundled appearance, **only moisture 7 uses the darker wet model**. Values 1–6 already look dry, even though **any moisture above 0 gives the same hydrated-soil contribution to Wheat's growth calculation**. A dry-looking patch therefore need not have lost that bonus yet. Use [Wheat planting and growing](Wheat.md#planting-and-growing) for crop spacing, light and Bone Meal; irrigation alone does not set harvest time. [Models][models] · [Crop growth calculation][growth]

## Protecting the plot

A **solid block directly above** normally makes Farmland invalid, except for fence gates and moving pistons. An upper-neighbor update schedules a check **1 game tick later**; if the cover is still invalid then, the soil becomes Dirt. Rain, irrigation and a maintaining crop do not prevent this separate cover conversion. [Cover checks and scheduled conversion][farm]

**Landing can trample both wet and dry Farmland.** The server requires a living entity whose bounding-box width squared × height is **greater than 0.512**. Players qualify regardless of `mobGriefing`; other living entities require that rule to be enabled. With those conditions satisfied, the random test uses **fall distance − 0.5**: recorded distances of 0.5 or less cannot pass, 1.0 has a 50% chance, and 1.5 or more always passes. These are callback distances, not a guarantee for every apparent ledge height. [Landing conditions][trampling]

Use perimeter paths and level entrances so players and animals do not drop onto the beds. Crouching is not an exception in the landing check, and the normal fall-damage handling still runs. When the soil becomes Dirt, crops that require Farmland lose their support; irrigating the Dirt will not till it again. [Trampling and Dirt conversion][trampling] · [Crop support][crop-support] · [Support-loss update][vegetation]

## Related pages

- [Wheat crop](Wheat.md) and [Wheat Seeds](../items/WheatSeeds.md)
- [Root crops](RootCrops.md) and [Pumpkin and Melon](PumpkinAndMelon.md)
- [Farmland inventory item](../items/Farmland.md)
- [Soil, Sand, and Gravel](SoilSandAndGravel.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Active Java placement, ticking and harvesting paths were checked alongside the native state defaults, physical properties, tick policy, bundled tags, loot and appearance. [Native registration and property bridge][register] · [Native bridge][bridge]

No in-game tilling, mining, irrigation, growth, fall or timing test was run. Layout and landing examples are deductions from the checked source. Data packs, game rules, resource packs and later builds can change the described results.

[farm]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L34-L140
[maintains]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/tags/block/maintains_farmland.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/HoeItem.java#L24-L90
[catalog]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/catalog.rs#L234
[templates]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/templates.rs#L50
[placement]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L65-L77
[loot]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/loot_table/blocks/farmland.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[tool]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Item.java#L447-L449
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[materials]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L49
[gate]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L255-L297
[physics]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/physics.rs#L87-L92
[intrinsic]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/intrinsic/declarations.rs#L15
[collision]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L328-L330
[policy]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/policy.rs#L94
[ticks]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerLevel.java#L474-L511
[models]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/assets/minecraft/blockstates/farmland.json
[water-tag]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/tags/fluid/water.json
[growth]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/CropBlock.java#L107-L148
[crop-support]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/CropBlock.java#L53-L56
[trampling]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L109-L125
[vegetation]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L28-L49
[register]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/Blocks.java#L4997-L5001
[bridge]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/NativeBlockDefinitions.java#L44-L58
