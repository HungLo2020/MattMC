# Bottle of Komodo Spit

Bottle of Komodo Spit (`minecraft:komodo_spit_bottle`) is a separate registered item from loose [Komodo Spit](KomodoSpit.md). Its current registration sets a **one-item stack limit** and a **Glass Bottle crafting remainder**.

## Availability and use limits

With command permission, the registered item can be given by ID. No bundled recipe or active interaction converting loose spit into this bottle was established in the reviewed source. The adult Komodo's periodic production creates **loose spit**, not this bottle.

The bottle is registered as a plain item without a special consumable behavior or food component. A crafting remainder does not itself make an item drinkable or add a recipe. Do not assume that using it applies Poison or another potion effect.

See [Komodo Spit](KomodoSpit.md#uses-and-the-separate-bottle) for the verified distinction, and [Komodo Dragon](../mobs/KomodoDragon.md) for the living animal's production route and availability limits.

## Sources and verification

Source-reviewed on 2026-10-01 at `b81c01943c9f3254e713c365a1dd633392929cb2`; no item-use or recipe gameplay test. [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1767-L1768), [active aliases](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L63-L64), and [Komodo source evidence](KomodoSpit.md#sources-and-verification).

Related: [Komodo Spit](KomodoSpit.md) · [Items](Items.md)
