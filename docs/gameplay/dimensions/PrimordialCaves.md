# Primordial Caves

Primordial Caves is a MattMC destination registered as `minecraft:primordial_caves`. It has active world-preset, server-startup, and portal wiring. Its existence does **not** establish that every upstream Alex's Caves creature, plant, or biome is naturally available.

## Entering: convert a Nether portal

1. Prepare a complete, active Nether portal. Keep a separate ordinary Nether route if you still need one.
2. Separate **one [Pitcher Pod](../items/PitcherPod.md)** from your inventory stack.
3. Drop that single pod as an item into the portal. The active block interaction replaces the connected Nether portal blocks with Primordial Caves portal blocks.
4. Enter the converted portal and wait for the configured portal delay.

**The code discards the entire dropped item entity, not just one item from its stack.** Do not throw a stack of pods. It also discards the item after calling conversion even if the shape check fails, so confirm the ordinary portal is complete before trying the conversion.

This changes that portal's destination; it is not merely an extra option on an unchanged Nether portal.

## Destination and return route

A Primordial Caves portal entered outside Primordial Caves targets this dimension. From within Primordial Caves it targets the **Overworld**, not whichever dimension you originally left.

Overworld and Primordial Caves both use coordinate scale 1, giving a **1:1 X/Z search target**. Y is not scaled. The destination logic finds a matching portal or attempts to create one within the world border; exact arrival coordinates can differ from the search target.

If you convert a portal while in the Nether, do not assume the return trip takes you back to that Nether location. Preserve or record another route before experimenting.

## Environment and respawn

The bundled dimension type has a ceiling, no skylight, and fixed time 18,000. Its configured vertical minimum is -64 with height 400; those data settings are not a promise of an unobstructed build volume.

Beds are permitted by its `bed_works` setting and the type is natural, but ordinary bed range, obstruction, rest, and exit checks still apply. Respawn anchors are not supported for setting spawn here and can explode when used for that purpose. See [Beds](../blocks/Bed.md) and [Dimensions](Dimensions.md) before establishing a base.

## What currently generates?

The bundled Normal preset chooses **Primordial Plains and Dry Midlands** for this destination. The server's missing-dimension fallback instead uses the Primordial Plains biome preset. These are distinct source paths; do not assume all world setups produce identical biome selection.

A scoped review of those active biome definitions found no positive natural-spawn wiring for the currently documented Grizzly Bear, Trilocaris, Subterranodon, or Emu. Primordial Plains references ordinary plains-tree selection rather than establishing Pewen placement. Those facts leave the individual pages' natural-availability notes unverified, rather than proving universal absence under every possible world/data pack.

## Related pages

- [Pitcher Pod acquisition and use](../items/PitcherPod.md)
- [Nether](Nether.md)
- [Dimensions](Dimensions.md)
- [Content guide](../ContentGuide.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game portal round trip, generation, or Sniffer-digging test was run. World presets and data packs can change the definitions.

- [Normal preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json)
- [Server fallback and world creation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L563)
- [Portal conversion and item discard](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172)
- [Portal destination and creation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java)
- [Dimension properties](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/primordial_caves.json)
- [Fallback biome selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L81-L93)
- [Primordial Plains](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json)
- [Dry Midlands](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json)
