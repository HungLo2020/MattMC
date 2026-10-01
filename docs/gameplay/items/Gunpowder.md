# Gunpowder

Gunpowder is a crafting and brewing ingredient registered as `minecraft:gunpowder`.

## Obtaining

The [Creeper](../mobs/Creeper.md) loot table defines a base **0–2 Gunpowder**, with a Looting count increase. That is a loot-table definition, not a promise that every creeper death or explosion yields gunpowder. See the mob guide for explosion behavior and safe interaction.

This page covers a verified source rather than an exhaustive list of all chest or mob loot.

## Uses

- Craft **one TNT** in the in-game crafting grid with Gunpowder in the four corners and center, and Sand or Red Sand in the four remaining cells. The recipe consumes five Gunpowder and four sand items.
- The current brewing container transformation combines a regular Potion with Gunpowder to make a Splash Potion. This changes the container type; it does not itself choose a potion effect.

These are selected source-verified uses.

## Related pages

- [Creeper](../mobs/Creeper.md)
- [TNT](TNT.md)
- [Splash Potion](SplashPotion.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game combat, crafting, brewing, or taming test was run.

- [Creeper loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/creeper.json)
- [TNT game recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/tnt.json)
- [Brewing transformation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L135-L138)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
