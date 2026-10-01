# Limestone family

Limestone is a yellow-map-color building stone integrated from Alex's Caves. MattMC registers ordinary and smooth blocks, stairs, slabs, walls, a pillar, and a directional chiseled block. These are useful Creative building materials, but their Survival acquisition is not established by the reviewed implementation.

## Obtaining and mining

The family is listed in Creative inventory. No Limestone crafting, smelting, stonecutting, or world-generation route was found in the active recipe and world-generation sources reviewed here. Do not assume upstream cave locations or conversion recipes work in MattMC.

The base block requires a correct tool for drops, and the family copies its properties. None of these Limestone IDs was found in the active mining-tool tags. Their loot tables list items, but that does not bypass the tool check: a reliable Survival harvesting tool is **not verified**.

The slab loot entry specifies one slab and has no double-slab count condition. Do not assume a double slab returns two when broken. Recovery still depends on the tool and runtime conditions above.

## Building forms

| Form | Item page | Placement or behavior |
| --- | --- | --- |
| Limestone | [Limestone](../items/Limestone.md) | Full building block |
| Stairs, slab, wall | [Stairs](../items/LimestoneStairs.md), [Slab](../items/LimestoneSlab.md), [Wall](../items/LimestoneWall.md) | Standard corresponding block classes |
| Pillar | [Limestone Pillar](../items/LimestonePillar.md) | Axis-oriented pillar |
| Chiseled | [Chiseled Limestone](../items/ChiseledLimestone.md) | Faces opposite the player's nearest looking direction when placed |
| Smooth Limestone | [Smooth Limestone](../items/SmoothLimestone.md) | Full decorative block |
| Smooth stairs, slab, wall | [Stairs](../items/SmoothLimestoneStairs.md), [Slab](../items/SmoothLimestoneSlab.md), [Wall](../items/SmoothLimestoneWall.md) | Smooth-family shape variants |

Smooth Limestone currently has no special cave-painting interaction in its block class. Treat it as a decorative block rather than importing upstream painting mechanics.

## Registered properties

The base Limestone properties are **hardness 1.2**, **blast resistance 4.5**, dripstone-block sounds, and bass-drum note-block instrument. Related shapes inherit the base properties unless their shape class changes behavior. The IDs use the `minecraft` namespace; Chiseled Limestone is `minecraft:limestone_chiseled`, not `minecraft:chiseled_limestone`.

## Related pages

- [Stone](Stone.md)
- [Pewen family](Pewen.md)
- [Blocks](Blocks.md)

## Sources and verification

Reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source/data review only; no in-game placement, growth, harvesting, or crafting test.

- [Family registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L100-L125)
- [Smooth block implementation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/custom/SmoothLimestoneBlock.java)
- [Directional placement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/custom/DirectionalFacingBlock.java)
- [Limestone drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/limestone.json)
- [Slab drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/limestone_slab.json)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Creative inventory](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
