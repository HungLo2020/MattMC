# Nautilus Shell

A **Nautilus Shell** (`minecraft:nautilus_shell`) is a crafting material for a [Conduit](Conduit.md). Its ordinary item registration has no food or potion-use component. [Item registration][items] · [Conduit recipe][conduit-recipe]

## Obtaining

Checked routes include:

- **Drowned equipment:** spawn finalization has a **3% chance** to put one in an empty offhand and mark it for guaranteed dropping. A shell-bearing Drowned drops that held shell with **doMobLoot** enabled; player kill credit is not required for this preserved equipment slot. Looting does not multiply it
- **Fishing treasure:** Nautilus Shell is one of six equally weighted entries in the treasure subtable. Treasure needs the current open-water condition; this is not a one-in-six chance on every cast. See [Fishing](../mechanics/Fishing.md#open-water-for-treasure) for pool eligibility and luck
- **Wandering Trader:** the active offer pool includes **one Shell for five Emeralds**, with five uses in that selected offer. Not every trader selects it

- **Inventory item browser:** the Shell is an ordinary category-listed item, so the [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Creative. This is separate from natural collection. [Shell listing][nautilus-shell-list]
- **Nautilus death loot:** an eligible adult player-credit kill has a **5% chance** to drop one Shell, rising to **6%, 7% and 8%** with Looting I, II and III. The current bundled biome tables do not naturally spawn Nautiluses, so this is not a verified natural farming route. A Zombie Nautilus's own loot table has no Shell. [Nautilus availability](../mobs/Nautilus.md#where-to-find-one) · [Nautilus loot][nautilus-shell-loot] · [Zombie Nautilus loot][zombie-nautilus-shell-loot]

[Drowned shell roll][drowned] · [Equipment drop gate][mob] [monster] · [Preserved drop chance][drop-chances] · [Fishing parent table][fishing-loot] · [Treasure table][treasure] · [Fishing loot dispatch][hook] · [Trader offer][trades] · [Active offer selection][trader]

Drowning an ordinary Zombie does not run the Drowned natural shell roll. See [Drowned equipment and conversion limits](../mobs/Drowned.md#equipment-and-conversion-limits) before choosing a collection route. These are selected acquisition sources, not a complete loot-table inventory. [Conversion path][zombie] [mob]

<span id="usage"></span>
<span id="behavior"></span>

## Crafting a Conduit

At a Crafting Table, place **one Heart of the Sea in the center** and surround it with **eight Nautilus Shells** to make **one Conduit**. Follow the [placed Conduit guide](../blocks/Conduit.md) for water, frame and effect requirements. The shell is an ingredient; carrying it does not provide a Conduit's underwater effects. [Recipe][conduit-recipe] · [Item properties][items]

## Related pages

- [Drowned](../mobs/Drowned.md), [Fishing](../mechanics/Fishing.md)
- [Heart of the Sea](HeartOfTheSea.md), [Conduit](Conduit.md), [Items](Items.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game combat, conversion, curing, loot, or crafting test was run. Data packs, entity state, difficulty, gamerules, and later builds can change these results.

[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[conduit-recipe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/conduit.json
[drowned]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Drowned.java
[mob]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Mob.java
[monster]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Monster.java
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/DropChances.java
[fishing-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json
[treasure]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/gameplay/fishing/treasure.json
[hook]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java
[trades]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[trader]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java
[zombie]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Zombie.java

Nautilus/browser additions source-reviewed on 2026-10-02 at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`; no in-game acquisition test was run.

[nautilus-shell-list]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1840-L1840
[nautilus-shell-loot]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/loot_table/entities/nautilus.json
[zombie-nautilus-shell-loot]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/loot_table/entities/zombie_nautilus.json
