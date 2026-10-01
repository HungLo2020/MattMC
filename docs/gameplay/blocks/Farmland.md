# Farmland

Farmland supports wheat and several other crops. Its moisture state ranges from **0 to 7**, and nearby water or rain can keep it hydrated.

## Making farmland

Use a hoe on Dirt, Grass Block, or Dirt Path with air above it, without clicking the underside. The normal hoe path converts these blocks to Farmland and costs one durability.

Coarse Dirt first becomes ordinary Dirt. Rooted Dirt becomes Dirt and drops Hanging Roots. Those conversions do not all produce Farmland in a single use.

## Hydration

The moisture check searches up to **four blocks in each horizontal direction**, at farmland height and one block above, for water-tag fluid. Rain at the block above the farmland also hydrates it.

When hydrated, moisture rises to 7. Without water or rain, random ticks lower it one step at a time. At moisture 0, unmaintained farmland reverts to Dirt. A block above in the maintains-farmland tag, including Wheat, can keep dry farmland from reverting; it does not make that farmland hydrated.

For a compact plot, put water within that horizontal range and leave the crops room and light. A water source below the farmland's level is outside this particular search.

## Protecting the plot

A solid block placed above can invalidate farmland, with exceptions for fence gates and moving pistons. Falling entities can also trample it: the implementation checks fall distance, living-entity size, and the mob-griefing rule for non-player tramplers.

Use paths around fields and avoid jumping down onto crops. Hydrated farmland contributes more to crop growth than dry farmland, but it does not guarantee a fixed harvest time.

## Related pages

- [Wheat crop](Wheat.md)
- [Wheat Seeds](../items/WheatSeeds.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Hoe conversions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/HoeItem.java)
- [Moisture, water search, survival and trampling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/FarmBlock.java)
- [Maintaining crops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/maintains_farmland.json)
- [Growth-speed contribution](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/CropBlock.java)
