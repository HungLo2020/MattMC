# Komodo Spit

Komodo Spit is a registered material, `minecraft:komodo_spit`, produced periodically by living adult [Komodo Dragons](../mobs/KomodoDragon.md). Its current item registration is a plain item; a special consumable or brewing effect is not established.

## Obtaining

An alive adult Komodo Dragon drops one spit item after a timer of **24,000–35,999 ticking game ticks**, then chooses a new timer in that range. At 20 ticks per second, this is about 20 to just under 30 minutes. The animal need not be tame, and the timer is saved. Babies do not count it down.

This proves a production path for an existing adult, not a natural Survival source for the animal. The [Komodo Dragon guide](../mobs/KomodoDragon.md#obtaining) explains the missing natural-spawn wiring. With command permission, the spit item can also be given by ID.

## Uses and the separate bottle

No crafting, cooking, or brewing use for loose Komodo Spit was found in the reviewed Java registrations and bundled recipes. Do not assume that its name establishes a poison ingredient or a drinkable effect.

[Bottle of Komodo Spit](BottleOfKomodoSpit.md), `minecraft:komodo_spit_bottle`, is a **separate registered item**. Its registration sets a one-item stack limit and a Glass Bottle crafting remainder, but no recipe or interaction converting loose spit into it was found. A crafting remainder alone does not make the bottle drinkable or supply a recipe.

## Related pages

- [Komodo Dragon](../mobs/KomodoDragon.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No gameplay test of production, collection, or item use was run.

- [Item and bottle registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1767-L1768)
- [Active item aliases](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L63-L64)
- [Adult production and saved timer](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKomodoDragon.java)
- [Bundled recipes](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe)
- [Brewing registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java)
