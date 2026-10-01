# White Bed

White Bed is the placeable item for a white [Bed](../blocks/Bed.md). Its ID is `minecraft:white_bed`.

## Crafting and use

Place three White Wool in one row above three items from the planks tag to craft one White Bed. Placement needs two horizontal block positions.

Use a placed bed for sleeping and respawn-setting where supported. **Do not try to sleep in the Nether or End:** their bundled dimension types disable beds, and the bed interaction explodes instead.

See the canonical bed guide for obstruction, nearby-monster checks, daylight respawn setting, and multiplayer sleeping rules. A bed's color does not justify ignoring those conditions.

## Related pages

- [Beds: safe use and respawn](../blocks/Bed.md)
- [Crafting](../crafting/Crafting.md)
- [Items](Items.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/white_bed.json)
- [Bed block logic](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
