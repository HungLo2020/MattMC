# White Bed

White Bed is the placeable item for a white [Bed](../blocks/Bed.md). Its ID is `minecraft:white_bed`. It stacks to **1**, so each spare bed needs its own inventory slot.

## Crafting and use

Place three [White Wool](WhiteWool.md) in one row above three items from the planks tag in a crafting table to craft one White Bed. All three wool must be white; the planks can be any items in `minecraft:planks`. Placement needs two horizontal block positions.

You can also combine one bed of any of the other 15 colors with one [White Dye](WhiteDye.md) in a shapeless recipe to make one White Bed. An already-white bed is excluded. To pick a placed White Bed back up, break either half of the intact bed in ordinary Survival with block drops enabled. It returns one White Bed; see [recovering and recoloring beds](../blocks/Bed.md#recovering-and-recoloring) for paired removal, tool, and explosion limits.

Use a placed bed for sleeping and respawn-setting where supported. **Do not try to sleep in the Nether or End:** their bundled dimension types disable beds, and the bed interaction explodes instead.

See the canonical bed guide for [respawn setup, refusals, and return space](../blocks/Bed.md#sleeping-and-respawn), including obstruction, nearby-monster checks, and daylight respawn setting, and for [multiplayer sleeping rules](../blocks/Bed.md#night-skipping-and-weather). A bed's color does not justify ignoring those conditions.

## Related pages

- [Beds: safe use and respawn](../blocks/Bed.md)
- [Crafting](../crafting/Crafting.md)
- [Items](Items.md)

## Sources and verification

Expanded crafting, recoloring, recovery, and stack-limit guidance was source-reviewed at `f86206767dadde696adfed4e04c5ee97cd0d0885` on 2026-10-10. No in-game crafting, recoloring, placement, sleeping, respawn, breaking, or explosion test was performed. The earlier review and its source links are retained below.

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/white_bed.json)
- [Bed block logic](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)

- [Current crafting recipe](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/recipe/crafting/white_bed.json) and [white recoloring recipe](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/recipe/crafting/dye_white_bed.json)
- [White bed loot](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/loot_table/blocks/white_bed.json), [bed item and stack limit](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Items.java#L1717-L1732), and [paired removal](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/BedBlock.java#L158-L196)
