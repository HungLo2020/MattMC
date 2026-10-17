# Ambersol

Ambersol is a light-emitting block integrated from Alex's Caves. Place it above an open space to illuminate a vertical column beneath it. Its block and item ID is `minecraft:ambersol`.

## Obtaining

Ambersol is listed in the Creative inventory. To collect its **one Ambersol item** drop in Survival, use a **stone, copper, iron, diamond, or netherite pickaxe**. Wooden and golden pickaxes, other tools, and bare hands do not meet its harvesting requirement. A broken pickaxe cannot collect the drop. Silk Touch is not required.

A natural-generation location and a crafting recipe have not been established from MattMC's active implementation. Upstream Alex's Caves locations should not be treated as confirmed MattMC acquisition methods.

## Lighting and placement

- The block emits light level **15** and has a luminous surface.
- Placement fills air spaces directly below it with invisible Ambersol Light blocks, each emitting level 15 light.
- The scan follows one vertical column, starting immediately below the source. It continues through states that let skylight pass, replaces air only, and stops at the first obstruction. The world's minimum Y layer is excluded.
- A neighbor shape update at the Ambersol, a random tick, or a scheduled block tick runs the fill again. These callbacks do not establish a fixed refresh interval.

The column does not require open sky: the scan checks the blocks between the light and its Ambersol source. A light-block column is a lighting effect, not a solid support or climbable structure.

## Shaft setup and troubleshooting

Start with a dry, open shaft and place Ambersol directly above the spaces you want to light. Keep access to the side of the source so you can prompt a refresh after changing the shaft. For a wider room, check the resulting illumination in-game; the fill itself creates only a single vertical column.

| Situation | What the implementation does | Practical check |
| --- | --- | --- |
| An open shaft receives light only partway down | The scan stops at the first state that blocks skylight propagation. | Check the exact vertical line below the source for an obstruction, even if nearby space is clear. |
| Ordinary glass or Amber crosses the shaft | The scan passes through ordinary glass and Amber but leaves them in place; eligible air below can still receive lights. | Use either for a transparent crossing. Passing the scan does not turn the crossing block itself into an Ambersol Light. |
| Tinted glass, water, or lava crosses the shaft | Tinted glass and liquid blocks stop the scan. Water and lava are not replaced. | Keep the test shaft dry and remove the blocking layer before expecting lights below it. |
| Removing an obstruction leaves a gap | A new fill must reach the reopened space; an edit far below the source does not itself prove that the source received an update. | Place or remove a temporary block against the side of the Ambersol to prompt a source-neighbor shape update. Recheck the column afterward. |
| The lowest world layer has no helper light | The downward loop requires Y to be greater than the world's minimum Y. | Do not rely on the column to place a helper at minimum Y; use a separately placed light source if needed. |
| Light remains after removing the source or blocking the path | Each helper checks its connection when it receives a shape update. | Allow updates and inspect the result; immediate cleanup of the entire column is not verified. |

## Ambersol Light

`minecraft:ambersol_light` is the internal block used for the illuminated column. It is invisible, has no collision, and emits level **15** light. Its registration does not make it another collectible Ambersol item; use the ordinary Ambersol source to create the column.

A helper survives only when its upward scan through skylight-transmitting states reaches an Ambersol. Removing the source, or inserting an obstruction between it and a helper, makes that connection invalid. On a helper's shape update, an invalid helper becomes air. Some helper updates also request a source tick, but that is not a guarantee of an immediate, complete refill or removal in a running world.

The source creates the helper's dry default state. A registered `waterlogged` state exists, but it does not establish a working underwater shaft or a bucket interaction; those uses are not verified here.

## Block properties

| Property | Value |
| --- | --- |
| Hardness | 3 |
| Blast resistance | 10 |
| Emitted light | 15 |
| Sound set | Glass |

## Related pages

- [Ambersol item](../items/Ambersol.md)
- [Blocks](Blocks.md)

## Sources and verification

Harvesting configuration was source-reviewed on 2026-10-02 at [`ac7dece5`](https://github.com/HungLo2020/MattMC/tree/ac7dece5bec1557f2f073e83f1978f72f9e53e7b). The bundled tags require a stone-tier-or-better pickaxe, and the loot table specifies one Ambersol. Column behavior and native block definitions were reviewed on 2026-10-13 at `0d23141b`. No new game or native test run is claimed. In-game Survival breaking, neighbor-update propagation, rendered lighting, and full-column cleanup timing still need verification.

The existing column test explicitly delivers helper shape updates after removing the source; it does not simulate a world's update propagation. Waterlogged-helper propagation is not inferred from `AmbersolLightBlock`'s legacy three-argument skylight method: the active cached state calls the current one-argument method, whose default checks shape and fluid state.

- [Block registration](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L3999-L4000)
- [Column creation and refresh callbacks](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/alexscaves/server/block/AmbersolBlock.java#L22-L51)
- [Invisible light and support checks](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/alexscaves/server/block/AmbersolLightBlock.java)
- [Ordinary glass and Amber propagation](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/TransparentBlock.java), [Amber registration](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L3998), [tinted glass](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/TintedGlassBlock.java), and [liquids](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/LiquidBlock.java#L103-L106)
- [Native block definitions](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/catalog.rs#L1024-L1025), [physical properties](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/physics.rs), [light and fluid states](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/intrinsic/declarations.rs#L187-L188), [state defaults](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/templates.rs), and [sound material](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/material.rs#L81)
- [Default skylight callback](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L390-L392) and [cached-state initialization](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L540-L541)
- [Existing column test and its update boundary](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/test/misc/net/alexscaves/server/block/AmbersolLightBlockTest.java)
- [Block drops](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/resources/data/minecraft/loot_table/blocks/ambersol.json)
- [Creative inventory](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Mining tool tags](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json) and [required tool tier](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json)
