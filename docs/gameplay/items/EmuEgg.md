# Emu Egg

Emu Egg is a throwable item registered as `minecraft:emu_egg`. It stacks to **16** and is produced periodically by living adult [Emus](../mobs/Emu.md).

## Obtaining

Adults lay an egg on a 6,000–11,999-tick timer, roughly 5–10 minutes while ticking at normal speed. Creative also lists the item. No nest-block placement is involved in this source-defined production path.

## Throwing and hatching chance

Use the item to throw it. The server creates an Emu Egg projectile and consumes one egg through the item-use path.

When the projectile hits:

- A **one-in-eight** check determines whether it produces any babies.
- On success, it normally creates one baby; a separate **one-in-thirty-two** check changes the count to four.
- Each baby starts at age -24,000 ticks, and the projectile is removed after the impact event.

A thrown egg is not guaranteed to hatch. Throw into a prepared enclosure rather than relying on it to place a tame adult. The babies are Emus; no owner-taming step is performed by the egg code.

## Cooking limitation

A [Boiled Emu Egg](BoiledEmuEgg.md) item exists, but no active recipe connecting it to this egg was found. Do not assume an upstream furnace or boiling recipe works here.

## Related pages

- [Emu](../mobs/Emu.md)
- [Emu Spawn Egg](EmuSpawnEgg.md), for direct Creative mob placement
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game spawning, breeding, combat, egg-throwing, or food test was run.

- [Registration and stack size](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1857-L1858)
- [Egg use](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/item/ItemEmuEgg.java)
- [Impact chances and babies](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityEmuEgg.java)
- [Periodic adult production](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityEmu.java#L193-L199)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
