# Primordial decorative plants

Fiddlehead, Cycad, and Archaic Vine are integrated plant blocks with distinct placement and propagation rules. All three have registered block items and explicit Creative listings. A naturally generated starter supply was not established in the checked biome/configured-feature data or feature-building code; their prehistoric names do not guarantee encounters in [Primordial Caves](../dimensions/PrimordialCaves.md).

## Fiddlehead

[Fiddlehead](../items/Fiddlehead.md), `minecraft:fiddlehead`, is a small, non-colliding decorative plant. It accepts Grass Block, Moss Block, or another block with a sturdy full upper face. It is not limited to ordinary farmland or dirt-tag planting.

The class inherits the active BushBlock bone-meal behavior. When a neighboring horizontal air block at the same height has suitable support, bone meal places **one new Fiddlehead** in an eligible neighboring position. It does not transform the plant into a large fern. If all four horizontal neighbors are occupied or unsuitable, the spread target is invalid.

Its ordinary block loot returns one Fiddlehead, subject to explosion survival; no Shears requirement is present in that table. It breaks instantly under the registered properties.

## Cycad

[Cycad](../items/Cycad.md), `minecraft:cycad`, is a stackable plant. The bottom accepts ordinary vegetation ground: a dirt-tag block or Farmland. A Cycad block can also sit on another Cycad. The `top` state changes the appearance depending on whether another Cycad is directly above.

### Bone-meal growth

Use bone meal on the **top** of a column shorter than four blocks. The checked growth method:

1. Counts the connected Cycad blocks below the clicked top
2. Allows bone-meal targeting only while that height is less than four
3. Has a **50% success check**
4. On success, adds one Cycad above if that space is replaceable, turning the old top into a stem

A valid use can consume bone meal without visible growth, either because the chance fails or because the space above is blocked. Clear overhead space first. Four blocks is this bone-meal growth limit, not a separately enforced maximum for manual block placement.

Cycad does not register random ticking or implement spontaneous growth in this class. Waiting alone is not the verified propagation method. Its ordinary loot returns one Cycad per harvested block, subject to explosion survival; no Shears requirement is present in the table.

## Archaic Vine

[Archaic Vine](../items/ArchaicVine.md), `minecraft:archaic_vine`, hangs **downward**. Its top needs an existing connected Archaic Vine segment or a sturdy downward-facing surface above. The lowest tip is the head block; upper segments use the separate `minecraft:archaic_vine_plant` body state. Both yield the same item under their loot rules.

Removing support can cause a vine chain to break. Leave overhead support in place while collecting lower segments.

### Extending the vine

**Archaic Vines do not lengthen through natural random ticks.** Their class deliberately overrides that growth callback with an empty method.

Bone meal still uses the inherited growth implementation. With air below the tip, it extends downward by a variable number of blocks, stopping at an obstruction. Applying bone meal to a connected body segment locates the tip and delegates growth there. The new growth requires air, not simply any replaceable block; water is not a valid growth target in this implementation.

### Harvesting and climbing

For either head or body:

| Tool condition | Chance to drop one Archaic Vine |
| --- | --- |
| Shears or Silk Touch | 100% in the checked tool branch |
| Neither, no Fortune | 33% |
| Fortune I | 55% |
| Fortune II | 77% |
| Fortune III | 100% |

These are the explicit loot branches, not a guarantee against unrelated destruction or changed data packs. Ordinary harvest can lose the item, so use Shears when preserving a limited starter supply.

**Do not use Archaic Vine as a ladder.** Neither its head nor its body is in the bundled climbable tag, and the normal living-entity climbing check relies on that tag. Its lack of collision and vine appearance do not make it climbable.

## Related pages

- [Fiddlehead](../items/Fiddlehead.md)
- [Cycad](../items/Cycad.md)
- [Archaic Vine](../items/ArchaicVine.md)
- [Pewen family](Pewen.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game placement, harvesting, propagation, or climbing test was run. Natural starter sources remain unverified; data packs can change tags, recipes, loot, and generation.

- [Plant registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative availability](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Fiddlehead support](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/custom/FiddleheadBlock.java)
- [Inherited bush spreading](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BushBlock.java)
- [Neighbor selection for bone meal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BonemealableBlock.java)
- [Cycad stacking and growth](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/custom/CycadBlock.java)
- [Ordinary vegetation ground checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java)
- [Archaic Vine growth override](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/ArchaicVineBlock.java)
- [Vine support](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/GrowingPlantBlock.java)
- [Vine head bone-meal growth](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java)
- [Vine body and head conversion](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java)
- [Variable bone-meal length](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/NetherVines.java)
- [Bone-meal consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BoneMealItem.java)
- [Fiddlehead loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/fiddlehead.json)
- [Cycad loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/cycad.json)
- [Vine-head loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/archaic_vine.json)
- [Vine-body loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/archaic_vine_plant.json)
- [Climbable tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/climbable.json)
- [Climbing check](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
