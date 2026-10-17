# Nether

The Nether (`minecraft:the_nether`) is reached through an obsidian portal. Its horizontal travel scale can shorten long journeys, but beds explode here and water buckets do not place water. Prepare a return route before exploring.

## Building and using a portal

See [Nether and Primordial Caves portals](../blocks/NetherPortals.md#nether-portal) for support checks, linking limits, entry cooldowns and server controls.

1. Build an upright rectangular frame of ordinary Obsidian. The open interior must be 2–21 blocks wide and 3–21 blocks tall, with a complete frame along its sides, top, and bottom.
2. Light the interior, for example with Flint and Steel. The verified Flint and Steel recipe combines one Iron Ingot and one Flint, without a shaped layout.
3. Enter the portal and remain inside for the server's configured portal delay. Player delay is controlled by game rules, with separate normal and Creative-delay settings.

The fire-based activation check permits ordinary Nether portals in the Overworld and Nether. Nether portals target the Nether when entered outside it, and the Overworld when entered from the Nether.

MattMC also lets a dropped Pitcher Pod convert a Nether portal into a different destination. See [Primordial Caves](Dimensions.md#primordial-caves-in-mattmc) before throwing pods near your travel portal.

## Linking portals and coordinates

For the bundled Overworld and Nether types, divide Overworld X and Z by **8** when planning a Nether portal; multiply Nether X and Z by **8** when planning an Overworld exit. For example, Overworld X=800, Z=−400 corresponds to a Nether search target of X=100, Z=−50.

Y is not divided or multiplied. These are search targets, not guaranteed arrival coordinates: travel clamps the target to the destination world border, looks for a nearby portal, and creates one if needed and possible. An existing portal or available building space can move the exit. Record both actual portal locations after the first crossing.

## Respawning safely

Use a Respawn Anchor rather than a bed:

- Craft an anchor with a row of three Glowstone between two rows of three Crying Obsidian: six Crying Obsidian and three Glowstone in total
- Charge it with Glowstone blocks, up to four charges
- Interact with the charged anchor to set your respawn point; leave usable space around it
- A successful ordinary death respawn spends one charge. Setting the point does not itself spend a charge

An empty, missing, or unusable anchor cannot provide an ordinary anchor respawn. The respawn code checks for a valid nearby standing position and falls back to the world's default spawn when the saved point cannot be used. Keep the anchor fuelled and avoid sealing its surroundings.

## Hazards to plan around

- **Beds explode when used.** Placing one does not make a safe Nether respawn point
- **Water buckets evaporate their water.** Do not count on placing a water source to extinguish yourself or stop a fall here
- **Lava spreads differently.** The Nether's ultra-warm setting reduces lava's base fluid tick delay from 30 to 10 ticks and reduces its flow drop-off, so leave extra room when opening a wall near lava

These rules follow the bundled Nether dimension type. The anchor recipe and interactions are verified here; this page does not verify the entire Survival supply chain for Obsidian, Crying Obsidian, or Glowstone.

## Related pages

- [Nether biomes](../biomes/NetherBiomes.md): compare all five exploration routes and their resources, candidates and structures

- [Dimensions](Dimensions.md)
- [Bastion Remnant](../structures/BastionRemnant.md)
- [Piglin](../mobs/Piglin.md) and [Piglin Brute](../mobs/PiglinBrute.md)
- [Beds](../blocks/Bed.md)
- [End](End.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game portal-building, linking, anchor-respawn, or fluid test was performed.

- [Nether dimension rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_nether.json) and [Overworld coordinate scale](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/overworld.json)
- [Obsidian frame and size checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L26-L179) and [fire activation dimensions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L156-L175)
- [Flint and Steel recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/flint_and_steel.json) and [ignition behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java)
- [Portal delay, destination, scaling, and exit selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L175-L247), [scale calculation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/dimension/DimensionType.java#L126-L130), and [portal search and creation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/portal/PortalForcer.java)
- [Anchor recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/respawn_anchor.json), [charging and setting spawn](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/RespawnAnchorBlock.java#L75-L170), and [respawn validity and fuel use](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayer.java#L993-L1058)
- [Death versus End-return respawn handling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1771-L1782), [spawn-block usage flag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L431), and [default-spawn fallback](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/portal/TeleportTransition.java#L48-L81)
- [Bed explosion](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java#L80-L122), [bucket evaporation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/BucketItem.java#L99-L139), and [lava's ultra-warm behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L150-L177)
