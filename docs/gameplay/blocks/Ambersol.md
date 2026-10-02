# Ambersol

Ambersol is a light-emitting block integrated from Alex's Caves. Place it above an open space to illuminate a vertical column beneath it. Its block and item ID is `minecraft:ambersol`.

## Obtaining

Ambersol is listed in the Creative inventory. To collect its **one Ambersol item** drop in Survival, use a **stone, copper, iron, diamond, or netherite pickaxe**. Wooden and golden pickaxes, other tools, and bare hands do not meet its harvesting requirement. A broken pickaxe cannot collect the drop. Silk Touch is not required.

A natural-generation location and a crafting recipe have not been established from MattMC's active implementation. Upstream Alex's Caves locations should not be treated as confirmed MattMC acquisition methods.

## Lighting and placement

- The block emits light level **15** and has a luminous surface.
- Placement fills air spaces directly below it with invisible Ambersol Light blocks, each emitting level 15 light.
- The scan continues downward through blocks that let skylight pass and stops at an obstruction or the world's lower limit. It replaces air only; it does not carve a shaft through solid blocks.
- Neighbor changes and ticks refresh the column. A light block checks that it still has an Ambersol above it through a skylight-transmitting column; an invalid light block is removed when its shape updates.

For a hanging light, keep the vertical space below the Ambersol clear. A light-block column is a lighting effect, not a solid support or climbable structure. Exact visual appearance and removal timing still need in-game verification.

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

Harvesting configuration source-reviewed on 2026-10-02. The bundled tags require a stone-tier-or-better pickaxe, and the loot table specifies one Ambersol. In-game Survival breaking and rendered light-column cleanup still need verification.

- [Block registration](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Column creation](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/java/net/alexscaves/server/block/AmbersolBlock.java)
- [Invisible light behavior](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/java/net/alexscaves/server/block/AmbersolLightBlock.java)
- [Block drops](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/resources/data/minecraft/loot_table/blocks/ambersol.json)
- [Creative inventory](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Mining tool tags](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Required tool tier](https://github.com/HungLo2020/MattMC/blob/fix/issue-779-ambersol-tool/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json)
