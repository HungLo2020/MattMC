# Water and Lava

**Water** (`minecraft:water`) and **Lava** (`minecraft:lava`) have source, flowing and falling states within their block IDs. Their bucket items move sources; a waterfall from one source does not automatically become a column of collectible sources. [Fluid/block registration][blocks] · [State encoding][flowing] · [Source pickup][liquid]

## Moving fluids with buckets

Use the existing [Bucket guide](../items/Bucket.md), [Water Bucket](../items/WaterBucket.md), and [Lava Bucket](../items/LavaBucket.md) for collection and placement controls. An empty bucket collects an ordinary liquid block only when its block level is **0**, the source state. A flowing or falling liquid block does not satisfy that pickup branch. [Liquid pickup][liquid] · [Bucket targeting and interaction][bucket]

Water buckets can fill supported waterlogged blocks instead of replacing them. The block must implement the corresponding fluid-storage interface; visible gaps are not sufficient evidence that a block supports waterlogging. Waterlogging behavior belongs to the relevant [building guides](Blocks.md). The shared handler stores Water, not Lava. [Water storage][waterlogged] · [Bucket placement][bucket]

In an **ultrawarm dimension**, the normal Water Bucket placement path evaporates the water. This is a bucket-placement rule, not a claim that every water-containing block or command-created state is forbidden. The normal Nether is ultrawarm. Lava has no equivalent water-evaporation branch. [Bucket dimension check][bucket] · [Nether dimension settings][nether-type]

## Flow and timing

Fluid spreading tries downward movement and uses nearby downhill routes to select horizontal flow. Walls, block shapes, liquid-container behavior and other fluid states can prevent or redirect movement. These are fluid updates, not a fixed-size visual animation. [Spread and shape checks][flowing]

| Fluid and dimension setting | Base scheduled update delay | Level decrease per horizontal step | Flat-floor reach from one isolated source |
| --- | ---: | ---: | ---: |
| Water | 5 ticks | 1 | Up to 7 blocks |
| Lava, ordinary dimension | 30 ticks | 2 | Up to 3 blocks |
| Lava, ultrawarm dimension | 10 ticks | 1 | Up to 7 blocks |

The reach column follows the source amount of 8 and the level decrease on an unobstructed, level floor with no other sources or falling sections. It is not a total travel distance through stairs, shafts or a changing landscape. Falling fluid can continue down and establish new horizontal flow below. Lava can multiply its spread delay by **4** in a checked rising-level transition, so the base delay is not a guaranteed completion time. [Water constants][water] · [Lava constants and variable delay][lava] · [Flow-level calculation and downward behavior][flowing]

When spreading **actually replaces a block**, Water's callback requests that block's ordinary resource drops, while Lava's replacement callback produces its fizz effect without that same resource-drop request. This does not mean every block is replaceable: doors, signs, ladders and several other non-full blocks have explicit flow exclusions, and waterlogged containers follow their own placement method. [Water replacement][water] · [Lava replacement][lava] · [Fluid holding/replacement rules][flowing]

## Renewable source pools

The bundled defaults are **`waterSourceConversion = true`** and **`lavaSourceConversion = false`**. The shared conversion check needs:

1. At least **two horizontal neighboring sources of the same fluid**, with a passable connection to the cell
2. That fluid's source-conversion game rule enabled
3. A block below that is considered solid, or a source of the same fluid below

If those conditions hold during the update, the cell becomes a new source. The check is not based simply on the number of nearby buckets poured into an area. [Conversion algorithm][flowing] · [Water rule][water] · [Lava rule][lava] · [Bundled defaults][rules]

For an **untested, source-based Water setup**, dig a 2 × 2 pool one block deep over a solid floor and place Water in diagonally opposite corners. The remaining cells can become sources after updates. Taking one cell with a bucket leaves the two neighboring sources that can replenish it, while the rule remains enabled. Ordinary Lava does not replenish through this method with its default rule disabled. Server rules can change these results. [Source-neighbor and floor checks][flowing] · [Bucket pickup][liquid]

## Water, lava and building stone

Different contact directions produce different blocks. Use the canonical [water/lava stone conversions](Stone.md#water-and-lava) and [Basalt generation rules](BlackstoneAndBasalt.md#making-basalt-with-lava) before building a generator. Water beside or above lava can convert the lava position into Obsidian or Cobblestone depending on its source state; lava flowing downward into Water has a separate Stone conversion. Soul Soil and Blue Ice provide the separate Basalt route. These interactions can consume a source; a generator needs a deliberate layout. [Liquid contact checks][liquid] · [Downward lava callback][lava]

Lava Buckets also have a [Furnace fuel use](../items/LavaBucket.md); burning fuel in a furnace and placed-world fluid flow are separate systems.

## Light, fire and breathing

Placed Lava emits **light level 15**; Water has no registered light emission. Fluid surfaces do not provide ordinary solid floors for players. [Block properties][blocks] · [Liquid collision][liquid]

Water contact dispatches the extinguish effect. Lava contact clears freezing, applies ignition, and requests lava damage for entities that are not fire-immune. The base entity lava-damage request is **4 health points**, but defenses, immunities and damage timing affect actual health loss; it is not a guaranteed per-tick damage rate. [Water contact][water] · [Lava contact][lava] · [Active fluid-effect dispatch][fluid-state] [entity] · [Base lava damage][entity]

Lava's random fire attempts require the fire-ticking rule and the applicable away-from-player rule or nearby-player condition. They inspect nearby blocks' lava-ignition properties, so a block's furnace-fuel status or visual appearance alone is not a complete fire test. [Lava random ticks][lava] · [Fire rules][rules]

Living entities with their eyes in Water normally use their air supply unless underwater breathing, effects or player abilities exempt them. A **Bubble Column at the eye position** takes the air-recovery branch instead. That is why a working [Bubble Column](BubbleColumns.md) can provide underwater travel and breathing, while an ordinary flowing-water shaft cannot be treated as equivalent. [Breathing and eye-position check][living]

## Verification and related pages

Source-reviewed on **2026-10-02** at `e87cde38c872d30ae86139bbee181937603af769`, including fluid registration, active tick/spread rules, source conversion, bucket pickup, interaction dispatch and relevant entity effects. No in-game flow, pool, fire, damage or generator test was run. Server rules, dimensions, data and later implementations can change behavior.

[Blocks](Blocks.md) · [Bubble Columns](BubbleColumns.md) · [Farmland](Farmland.md) · [Kelp](Kelp.md) · [Bucket](../items/Bucket.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L285-L312
[flowing]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/material/FlowingFluid.java
[liquid]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/LiquidBlock.java
[water]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/material/WaterFluid.java
[lava]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/material/LavaFluid.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BucketItem.java
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[nether-type]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/dimension_type/the_nether.json
[rules]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/GameRules.java
[fluid-state]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/material/FluidState.java#L145-L147
[entity]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/Entity.java
[living]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
