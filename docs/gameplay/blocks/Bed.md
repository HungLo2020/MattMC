# Beds

Beds provide sleeping and respawn-setting interactions where the dimension allows them. **Using a bed in a dimension whose bed support is disabled causes an explosion.** The bundled Nether and End dimension types disable beds; do not use one there as an ordinary sleeping spot.

## Making and placing a bed

For a [White Bed](../items/WhiteBed.md), place three [White Wool](../items/WhiteWool.md) across a row above three planks-tag items in a crafting table. The recipe produces one bed. All 16 colors follow this pattern: use three wool of the **same target color**, while the plank slots accept any items in `minecraft:planks`. Beds stack to **1**, so each spare needs its own inventory slot. [White recipe][bed-craft] · [Item limits][bed-items]

A bed occupies two horizontal block positions, with its head extending in the direction you face. Its placement checks that the second position can be replaced and is within the world border. Leave room around and above it for sleeping and a usable exit. Ordinary Survival placement consumes one bed item. [Placement][bed-place] · [Item consumption][bed-consume]

## Color variants

Follow the item guides below for each bed color.

| Bed item |
| --- |
| [Black Bed](../items/BlackBed.md) |
| [Blue Bed](../items/BlueBed.md) |
| [Brown Bed](../items/BrownBed.md) |
| [Cyan Bed](../items/CyanBed.md) |
| [Gray Bed](../items/GrayBed.md) |
| [Green Bed](../items/GreenBed.md) |
| [Light Blue Bed](../items/LightBlueBed.md) |
| [Light Gray Bed](../items/LightGrayBed.md) |
| [Lime Bed](../items/LimeBed.md) |
| [Magenta Bed](../items/MagentaBed.md) |
| [Orange Bed](../items/OrangeBed.md) |
| [Pink Bed](../items/PinkBed.md) |
| [Purple Bed](../items/PurpleBed.md) |
| [Red Bed](../items/RedBed.md) |
| [White Bed](../items/WhiteBed.md) |
| [Yellow Bed](../items/YellowBed.md) |

## Recovering and recoloring

Break either half of an intact bed in ordinary Survival, with block drops enabled, to recover **one bed of the same color**. The other half is removed too; only the head half supplies the bed item. No particular tool or Silk Touch is required. This is not a guarantee for explosions, because bed loot includes an explosion-survival condition. [Paired removal][bed-pair] · [Drop handling][bed-drop-pair] [bed-drop-level][] [bed-drop-rule][] · [White bed loot][bed-loot] · [Tool requirement][bed-tool] [bed-physics][]

To change the color, combine one bed with one [dye](../items/Dyes.md) of the target color in a shapeless recipe. Each of the 16 recipes accepts beds of the **other 15 colors**, including dyeing a colored bed white; an already-matching bed is excluded. Pick up a placed bed first. Recoloring produces one bed and does not change the sleeping or dimension rules. [White recoloring recipe][bed-dye]

## Sleeping and respawn

To select a bed as your return point, place it in a dimension that supports beds, such as the Overworld, then **interact with it while it is unoccupied**. Placing or carrying the item alone does not select it. Stand beside it and keep the spaces directly above both halves clear of suffocating blocks. Ordinary selection also requires a natural dimension. [Bed interaction][bed-use] · [Selection checks][bed-sleep]

In a suitable natural dimension, interacting with an in-range, unobstructed, unoccupied bed sets the player's respawn position **before both the daylight and nearby-monster checks**. Either refusal to sleep can therefore follow a successful respawn-setting step; you do not need to finish a night's sleep to select the point. A too-far-away or obstructed refusal happens before selection, so fix that problem and interact again. [Check order][bed-sleep]

Sleeping can be refused because the player is too far away, the bed is obstructed, it is too bright outside, or nearby monsters prevent rest. The sleep-distance check accepts either half when the player is within 3 blocks on each horizontal axis and 2 vertically from that half's bottom center. The obstruction check tests the blocks directly above both halves for suffocation; it is not the full standing-space check used on respawn. The ordinary non-Creative safety check examines a region extending 8 blocks horizontally and 5 vertically from the bed, and only counts monsters whose rest-prevention test applies. [Distance and obstruction][bed-range] · [Suffocation check][bed-suffocation] · [Monster check][bed-sleep]

An occupied bed normally reports that it is occupied. A sleeping villager can instead be made to leave through the bed interaction. That interaction ends without selecting the bed for you; use it again once it is free. [Occupied-bed handling][bed-use]

### Keep a usable return space

Selecting the bed does not prove that you can respawn there later. Actual bed respawn searches for a standing position near the bed, checking floor support, room for the player's body, blocks that forbid spawning inside them, and the world border. Keep usable floor and headroom around the bed when building walls, low ceilings, or bunk beds. Keep hazards away too: the search can retry without its dangerous-block filter if safer candidates fail. [Respawn validation][bed-respawn] · [Standing-position search][bed-stand] · [Floor and clearance checks][bed-clearance]

After moving a bed, interact with it at the new location to select it again. See [Death and respawn: choosing where you return](../mechanics/DeathAndRespawn.md#choosing-where-you-return) for the single saved-point setting, default-spawn fallback, and why a failed point must be selected again after repair.

## Night skipping and weather

The server checks sleepers separately in each dimension and wakes the sleeping players in that level when its percentage and deep-sleep thresholds pass. In the Overworld, daylight cycling permits a jump to the next day boundary; weather cycling separately permits a reset when the level counts as raining. See [Time, weather, and sleep](../mechanics/TimeWeatherAndSleep.md#coordinate-multiplayer-sleep) for the counts and dimension limits.

## Dimension danger

The bed block checks the dimension's `bed_works` property. When false, it removes the bed and creates a power-5 explosion with fire enabled. Do not infer bed safety merely from a dimension looking cave-like or familiar. A defined custom dimension type and an accessible playable dimension are also separate facts.

## Related pages

- [White Bed item](../items/WhiteBed.md)
- [Survival](../gamemodes/Survival.md)
- [Blocks](Blocks.md)

## Sources and verification

Expanded acquisition, recovery, recoloring, selection order, and return-space guidance was source-reviewed at `f86206767dadde696adfed4e04c5ee97cd0d0885` on 2026-10-10. The active interaction and respawn paths, all 16 crafting/recoloring/loot definitions, and native bed properties were checked. No in-game crafting, recoloring, placement, sleeping, respawn, breaking, or explosion test was performed. The earlier review and its source links are retained below.

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [White bed recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/white_bed.json)
- [Bed interaction and placement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java)
- [Player sleep/respawn checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1168-L1231)
- [Night skip and weather rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerLevel.java#L337-L350)
- [Nether bed setting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_nether.json)
- [End bed setting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_end.json)
- [Overworld bed setting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/overworld.json)

[bed-craft]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/recipe/crafting/white_bed.json
[bed-dye]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/recipe/crafting/dye_white_bed.json
[bed-loot]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/loot_table/blocks/white_bed.json
[bed-items]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Items.java#L1717-L1732
[bed-place]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/BedBlock.java#L199-L208
[bed-consume]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[bed-pair]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/BedBlock.java#L158-L196
[bed-drop-pair]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/Block.java#L216-L227
[bed-drop-level]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/Level.java#L263-L283
[bed-drop-rule]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/Block.java#L420-L424
[bed-tool]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656
[bed-physics]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/content/block/definitions/physics.rs#L205-L210
[bed-use]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/BedBlock.java#L83-L132
[bed-sleep]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1168-L1209
[bed-range]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1220-L1231
[bed-suffocation]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/player/Player.java#L1333-L1335
[bed-respawn]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1027-L1058
[bed-stand]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/block/BedBlock.java#L226-L283
[bed-clearance]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/vehicle/DismountHelper.java#L79-L108
