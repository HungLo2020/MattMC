# Beds

Beds provide sleeping and respawn-setting interactions where the dimension allows them. **Using a bed in a dimension whose bed support is disabled causes an explosion.** The bundled Nether and End dimension types disable beds; do not use one there as an ordinary sleeping spot.

## Making and placing a bed

For a [White Bed](../items/WhiteBed.md), place three White Wool across a row above three planks-tag items. The recipe produces one bed. This is a verified example; other colors have their own item IDs and recipes.

A bed occupies two horizontal block positions. Its placement checks that the second position can be replaced and is within the world border. Leave room around and above it for sleeping and a usable exit.

## Sleeping and respawn

In a suitable natural dimension, interacting with an in-range, unobstructed bed sets the player's respawn position before checking whether it is currently dark enough to sleep. Thus a daylight refusal to sleep can still follow a successful respawn-setting step.

Sleeping can be refused because the player is too far away, the bed is obstructed, it is too bright outside, or nearby monsters prevent rest. The ordinary non-Creative safety check examines a region extending 8 blocks horizontally and 5 vertically from the bed, and only counts monsters whose rest-prevention test applies.

An occupied bed normally reports that it is occupied. A sleeping villager can instead be made to leave through the bed interaction.

## Night skipping and weather

The server uses the required sleeping-player percentage and sufficiently deep sleeping to decide when to wake everyone. If daylight cycling is enabled, it advances to the next day boundary. If weather cycling is enabled and it is raining, it resets the weather cycle. These are game-rule-dependent effects, not a guarantee that a single player always skips the night on every server.

## Dimension danger

The bed block checks the dimension's `bed_works` property. When false, it removes the bed and creates a power-5 explosion with fire enabled. Do not infer bed safety merely from a dimension looking cave-like or familiar. A defined custom dimension type and an accessible playable dimension are also separate facts.

## Related pages

- [White Bed item](../items/WhiteBed.md)
- [Survival](../gamemodes/Survival.md)
- [Blocks](Blocks.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [White bed recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/white_bed.json)
- [Bed interaction and placement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java)
- [Player sleep/respawn checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1168-L1231)
- [Night skip and weather rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerLevel.java#L337-L350)
- [Nether bed setting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_nether.json)
- [End bed setting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_end.json)
- [Overworld bed setting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/overworld.json)
